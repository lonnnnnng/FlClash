package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import com.lonnnnnng.flclash.service.ActiveProfile
import com.lonnnnnng.flclash.service.ActiveProfileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ProfileStore {
    val profiles: StateFlow<List<Profile>>
    val currentProfileId: StateFlow<Long?>

    suspend fun selectProfile(id: Long?)
    suspend fun addRemoteProfile(url: String, label: String = ""): Profile
    suspend fun addFileProfile(label: String, bytes: ByteArray): Profile
    suspend fun addCustomProfile(label: String): Profile
    suspend fun readLocalProfileContent(id: Long): String
    suspend fun updateLocalProfileContent(id: Long, content: String): Profile
    suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?: error("配置不存在")
    suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile
    suspend fun refreshCurrentProfile() = Unit
    suspend fun updateProfileAutoUpdate(
        id: Long,
        enabled: Boolean,
        intervalMillis: Long = Profile.DEFAULT_UPDATE_INTERVAL_MILLIS,
    ): Profile = profiles.value.firstOrNull { it.id == id }
        ?.copy(autoUpdate = enabled, autoUpdateIntervalMillis = intervalMillis)
        ?: error("配置不存在")
    suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?: error("配置不存在")
    suspend fun updateRemoteProfile(id: Long): Profile
    suspend fun deleteProfile(id: Long)
    suspend fun reorder(ids: List<Long>)
}

class ProfileRepository(
    context: Context,
    private val database: KClashDatabase,
    private val subscriptionClient: SubscriptionClient,
    private val configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
) : ActiveProfileProvider, ProfileStore, RemoteProfileUpdater {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profileDao = database.profiles()
    private val preferenceDao = database.appPreferences()
    private val mutableProfiles = MutableStateFlow<List<Profile>>(emptyList())
    private val mutableCurrentProfileId = MutableStateFlow<Long?>(null)

    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = mutableCurrentProfileId.asStateFlow()

    init {
        scope.launch {
            mutableCurrentProfileId.value = preferenceDao.get(CURRENT_PROFILE_KEY)?.toLongOrNull()
            profileDao.observeAll().collectLatest { rows ->
                val next = rows.map(ProfileEntity::toDomain)
                mutableProfiles.value = next
                if (mutableCurrentProfileId.value !in next.map(Profile::id)) {
                    val fallback = next.firstOrNull()?.id
                    mutableCurrentProfileId.value = fallback
                    persistCurrentProfile(fallback)
                }
            }
        }
    }

    override suspend fun selectProfile(id: Long?) {
        val valid = id?.takeIf { candidate -> profileDao.getById(candidate) != null }
        mutableCurrentProfileId.value = valid
        persistCurrentProfile(valid)
    }

    override suspend fun refreshCurrentProfile() = withContext(Dispatchers.IO) {
        val restored = preferenceDao.get(CURRENT_PROFILE_KEY)?.toLongOrNull()
        val valid = restored?.takeIf { profileDao.getById(it) != null }
            ?: profileDao.getAll().firstOrNull()?.id
        mutableCurrentProfileId.value = valid
        persistCurrentProfile(valid)
    }

    override suspend fun addRemoteProfile(url: String, label: String): Profile = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "订阅链接不能为空" }
        require(mutableProfiles.value.none { it.url == normalizedUrl }) { "该订阅已经添加" }
        val id = newId()
        val downloaded = subscriptionClient.download(normalizedUrl)
        val profile = Profile.remote(
            id = id,
            url = normalizedUrl,
            label = label.trim().ifEmpty { downloaded.suggestedLabel.orEmpty() },
        ).copy(
            lastUpdateAt = System.currentTimeMillis(),
            subscriptionInfo = downloaded.subscriptionInfo.takeIf { it != SubscriptionInfo() },
            orderIndex = mutableProfiles.value.size,
        )
        replaceFileAtomically(id, downloaded.bytes)
        runCatching { profileDao.insert(profile.toEntity()) }
            .onFailure {
                profileFile(id).delete()
                throw it
            }
        ensureCurrentProfile(profile.id)
        profile
    }

    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "配置文件为空" }
        val id = newId()
        val profile = Profile(
            id = id,
            type = ProfileType.FILE,
            label = label.trim(),
            url = "",
            ageSecretKey = "",
            lastUpdateAt = System.currentTimeMillis(),
            autoUpdate = false,
            autoUpdateIntervalMillis = Profile.DEFAULT_UPDATE_INTERVAL_MILLIS,
            subscriptionInfo = null,
            orderIndex = mutableProfiles.value.size,
        )
        replaceFileAtomically(id, bytes)
        runCatching { profileDao.insert(profile.toEntity()) }
            .onFailure {
                profileFile(id).delete()
                throw it
            }
        ensureCurrentProfile(profile.id)
        profile
    }

    override suspend fun addCustomProfile(label: String): Profile = withContext(Dispatchers.IO) {
        val profile = Profile.custom(newId(), label.trim().ifEmpty { "未命名配置" }).copy(
            orderIndex = mutableProfiles.value.size,
        )
        profileFile(profile.id).apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
        profileDao.insert(profile.toEntity())
        ensureCurrentProfile(profile.id)
        profile
    }

    override suspend fun readLocalProfileContent(id: Long): String = withContext(Dispatchers.IO) {
        val profile = requireNotNull(profileDao.getById(id)) { "配置不存在" }
        require(profile.type != ProfileType.URL) { "远程订阅不能直接编辑" }
        profileFile(id).takeIf(File::isFile)?.readText(Charsets.UTF_8).orEmpty()
    }

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = withContext(Dispatchers.IO) {
        require(content.isNotBlank()) { "配置内容不能为空" }
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type != ProfileType.URL) { "远程订阅不能直接编辑" }
        replaceFileAtomically(id, content.toByteArray(Charsets.UTF_8))
        val updated = current.copy(lastUpdateAt = System.currentTimeMillis())
        profileDao.update(updated.toEntity())
        updated
    }

    override suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile =
        withContext(Dispatchers.IO) {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            val updated = current.copy(overridesJson = overridesJson)
            profileDao.update(updated.toEntity())
            updated
        }

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile =
        withContext(Dispatchers.IO) {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            val updated = current.copy(selectedMapJson = encodeSelectedMap(selectedMap))
            profileDao.update(updated.toEntity())
            updated
        }

    override suspend fun updateProfileAutoUpdate(
        id: Long,
        enabled: Boolean,
        intervalMillis: Long,
    ): Profile = withContext(Dispatchers.IO) {
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type == ProfileType.URL) { "只有订阅配置支持自动更新" }
        val updated = current.copy(
            autoUpdate = enabled,
            autoUpdateIntervalMillis = intervalMillis.coerceAtLeast(MIN_AUTO_UPDATE_INTERVAL_MILLIS),
        )
        profileDao.update(updated.toEntity())
        updated
    }

    override suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 作者 long：校验脚本存在与写入 Profile 必须原子完成，避免脚本删除并发时留下悬挂绑定。
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                if (scriptId != null) {
                    requireNotNull(database.scripts().getById(scriptId)) { "脚本不存在" }
                }
                val updated = current.copy(scriptId = scriptId)
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun updateRemoteProfile(id: Long): Profile = withContext(Dispatchers.IO) {
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type == ProfileType.URL) { "只有订阅配置支持更新" }
        try {
            val downloaded = subscriptionClient.download(current.url)
            // 作者 long：下载内容必须先通过 YAML 根对象校验，之后才允许替换旧文件，
            // 这样网络返回 HTML、空内容或损坏配置时不会破坏上一份可运行订阅。
            configValidator.validate(downloaded.bytes)
            val latest = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            replaceFileAtomically(id, downloaded.bytes)
            val updated = latest.copy(
                label = latest.label.ifBlank { downloaded.suggestedLabel.orEmpty() },
                lastUpdateAt = System.currentTimeMillis(),
                lastUpdateError = null,
                subscriptionInfo = downloaded.subscriptionInfo.takeIf { it != SubscriptionInfo() },
            )
            profileDao.update(updated.toEntity())
            updated
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            recordRemoteUpdateFailure(id, error)
            throw error
        }
    }

    override suspend fun updateDueProfiles(nowMillis: Long): AutoUpdateResult = withContext(Dispatchers.IO) {
        val due = profileDao.getAll()
            .map(ProfileEntity::toDomain)
            .filter { profile ->
                profile.type == ProfileType.URL &&
                    profile.autoUpdate &&
                    isAutoUpdateDue(profile, nowMillis)
            }
        var succeeded = 0
        val failures = buildList {
            due.forEach { profile ->
                try {
                    updateRemoteProfile(profile.id)
                    succeeded++
                } catch (error: CancellationException) {
                    // 作者 long：Worker 被系统停止时必须把取消继续向上传递，不能把生命周期取消伪装成订阅失败。
                    throw error
                } catch (error: Throwable) {
                    add(
                        AutoUpdateFailure(
                            profileId = profile.id,
                            label = profile.realLabel,
                            message = error.message ?: "订阅更新失败",
                        ),
                    )
                }
            }
        }
        AutoUpdateResult(
            attempted = due.size,
            succeeded = succeeded,
            failures = failures,
        )
    }

    override suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
        val profile = profileDao.getById(id) ?: return@withContext
        database.withTransaction {
            profileDao.delete(profile)
            profileFile(id).delete()
            if (mutableCurrentProfileId.value == id) {
                val fallback = profileDao.getAll().firstOrNull()?.id
                mutableCurrentProfileId.value = fallback
                persistCurrentProfile(fallback)
            }
        }
    }

    override suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existingIds = profileDao.getAll().map(ProfileEntity::id)
            val requested = ids
                .filter { it in existingIds }
                .distinct()
            val orderedIds = requested + existingIds.filterNot { it in requested }
            orderedIds.forEachIndexed { index, id -> profileDao.updateOrder(id, index) }
        }
    }

    fun profileFile(id: Long): File = File(File(appContext.filesDir, PROFILES_DIRECTORY), "$id.yaml")

    private fun scriptFile(id: Long): File = File(File(appContext.filesDir, SCRIPTS_DIRECTORY), "$id.js")

    override suspend fun activeProfile(): ActiveProfile = withContext(Dispatchers.IO) {
        val id = mutableCurrentProfileId.value
        val profile = id?.let { profileDao.getById(it)?.toDomain() }
            ?: profileDao.getAll().firstOrNull()?.toDomain()
        requireNotNull(profile) { "没有可用配置" }
        ActiveProfile(
            id = profile.id,
            label = profile.realLabel,
            filePath = profileFile(profile.id).path,
            selectedMap = decodeSelectedMap(profile.selectedMapJson),
            overridesJson = profile.overridesJson,
            scriptPath = profile.scriptId?.let { scriptId ->
                requireNotNull(database.scripts().getById(scriptId)) { "绑定脚本不存在" }
                scriptFile(scriptId).also { file ->
                    require(file.isFile) { "绑定脚本正文不存在" }
                }.path
            },
        )
    }

    private suspend fun ensureCurrentProfile(id: Long) {
        if (mutableCurrentProfileId.value == null) selectProfile(id)
    }

    private suspend fun persistCurrentProfile(id: Long?) {
        if (id == null) {
            preferenceDao.delete(CURRENT_PROFILE_KEY)
        } else {
            preferenceDao.put(
                AppPreferenceEntity(
                    key = CURRENT_PROFILE_KEY,
                    value = id.toString(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun replaceFileAtomically(id: Long, bytes: ByteArray) {
        val target = profileFile(id)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            target.outputStream().use { it.write(bytes) }
            if (!temporary.delete()) {
                throw IOException("无法清理配置临时文件")
            }
        }
    }

    private suspend fun recordRemoteUpdateFailure(id: Long, error: Throwable) {
        runCatching {
            val latest = profileDao.getById(id)?.toDomain() ?: return@runCatching
            val message = error.message.orEmpty().trim().take(MAX_UPDATE_ERROR_LENGTH)
                .ifBlank { "订阅更新失败" }
            profileDao.update(latest.copy(lastUpdateError = message).toEntity())
        }
    }

    private fun newId(): Long = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    private companion object {
        const val CURRENT_PROFILE_KEY = "current_profile_id"
        const val PROFILES_DIRECTORY = "profiles"
        const val SCRIPTS_DIRECTORY = "scripts"
        const val MIN_AUTO_UPDATE_INTERVAL_MILLIS = 15 * 60 * 1_000L
        const val MAX_UPDATE_ERROR_LENGTH = 240
    }
}
