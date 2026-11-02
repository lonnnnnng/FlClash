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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ProfileStore {
    val profiles: StateFlow<List<Profile>>
    val currentProfileId: StateFlow<Long?>

    suspend fun selectProfile(id: Long?)
    suspend fun addRemoteProfile(url: String, label: String = "", ageSecretKey: String = ""): Profile
    suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String = ""): Profile
    suspend fun addCustomProfile(label: String): Profile
    suspend fun readLocalProfileContent(id: Long): String
    suspend fun updateLocalProfileContent(id: Long, content: String): Profile
    suspend fun updateFileProfile(
        id: Long,
        ageSecretKey: String,
        replacement: ByteArray? = null,
        canWrite: () -> Boolean = { true },
    ): Profile = error("此数据源不支持本地文件设置")
    suspend fun updateProfileLabel(id: Long, label: String): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?.copy(label = label.trim())
            ?: error("配置不存在")
    suspend fun updateRemoteProfileSettings(
        id: Long,
        label: String,
        url: String,
        autoUpdate: Boolean,
        intervalMillis: Long,
        ageSecretKey: String = profiles.value.firstOrNull { it.id == id }?.ageSecretKey.orEmpty(),
    ): Profile = profiles.value.firstOrNull { it.id == id }
        ?.copy(
            label = label.trim(),
            url = url.trim(),
            autoUpdate = autoUpdate,
            autoUpdateIntervalMillis = intervalMillis,
            ageSecretKey = ageSecretKey.trim(),
        )
        ?: error("配置不存在")
    suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?: error("配置不存在")
    suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile
    suspend fun updateProfileCurrentGroupName(id: Long, groupName: String): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?.copy(currentGroupName = groupName)
            ?: error("配置不存在")
    suspend fun updateProfileGroupViewState(id: Long, groupName: String, unfoldSet: Set<String>): Profile =
        profiles.value.firstOrNull { it.id == id }
            ?.copy(currentGroupName = groupName, unfoldSetJson = encodeUnfoldSet(unfoldSet))
            ?: error("配置不存在")
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
    private val contentDecoder: ProfileContentDecoder = PlainProfileContentDecoder,
) : ActiveProfileProvider, ProfileStore, RemoteProfileUpdater, ProfileExportReader {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profileDao = database.profiles()
    private val preferenceDao = database.appPreferences()
    private val mutableProfiles = MutableStateFlow<List<Profile>>(emptyList())
    private val mutableCurrentProfileId = MutableStateFlow<Long?>(null)
    // 作者 long：锁覆盖文件替换、Room 提交和失败恢复，后续保存不能在旧正文回滚完成前写入同一路径。
    private val fileMutationMutex = Mutex()

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

    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "订阅链接不能为空" }
        require(mutableProfiles.value.none { it.url == normalizedUrl }) { "该订阅已经添加" }
        val id = newId()
        val downloaded = subscriptionClient.download(normalizedUrl)
        val plaintext = contentDecoder.decode(downloaded.bytes, ageSecretKey)
        configValidator.validate(plaintext)
        val profile = Profile.remote(
            id = id,
            url = normalizedUrl,
            label = label.trim().ifEmpty { downloaded.suggestedLabel.orEmpty() },
        ).copy(
            lastUpdateAt = System.currentTimeMillis(),
            subscriptionInfo = downloaded.subscriptionInfo.takeIf { it != SubscriptionInfo() },
            orderIndex = mutableProfiles.value.size,
            ageSecretKey = ageSecretKey.trim(),
        )
        replaceFileAtomically(id, plaintext)
        runCatching { profileDao.insert(profile.toEntity()) }
            .onFailure {
                profileFile(id).delete()
                throw it
            }
        ensureCurrentProfile(profile.id)
        profile
    }

    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "配置文件为空" }
        // 作者 long：外部文件必须先通过订阅更新共用的 YAML 解析校验，避免无效内容先写入文件或 Room 并成为当前配置。
        val plaintext = contentDecoder.decode(bytes, ageSecretKey)
        configValidator.validate(plaintext)
        val id = newId()
        val profile = Profile(
            id = id,
            type = ProfileType.FILE,
            label = label.trim(),
            url = "",
            ageSecretKey = ageSecretKey.trim(),
            lastUpdateAt = System.currentTimeMillis(),
            autoUpdate = false,
            autoUpdateIntervalMillis = Profile.DEFAULT_UPDATE_INTERVAL_MILLIS,
            subscriptionInfo = null,
            orderIndex = mutableProfiles.value.size,
        )
        replaceFileAtomically(id, plaintext)
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

    override suspend fun readExport(profileId: Long): ProfileExport = withContext(Dispatchers.IO) {
        val profile = requireNotNull(profileDao.getById(profileId)) { "配置不存在" }.toDomain()
        require(profile.type != ProfileType.CUSTOM) { "自定义配置不支持文件导出" }
        // 作者 long：菜单可以作用于非当前配置，按卡片 ID 读取原缓存，不切换当前选择或下载订阅。
        readProfileExport(profileFile(profileId), profile.realLabel)
    }

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = withContext(Dispatchers.IO) {
        require(content.isNotBlank()) { "配置内容不能为空" }
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type != ProfileType.URL) { "远程订阅不能直接编辑" }
        if (current.type == ProfileType.FILE) return@withContext updateFileProfile(id, current.ageSecretKey, content.toByteArray(Charsets.UTF_8))
        replaceFileAtomically(id, content.toByteArray(Charsets.UTF_8))
        val updated = current.copy(lastUpdateAt = System.currentTimeMillis())
        profileDao.update(updated.toEntity())
        updated
    }

    override suspend fun updateFileProfile(
        id: Long,
        ageSecretKey: String,
        replacement: ByteArray?,
        canWrite: () -> Boolean,
    ): Profile = withContext(Dispatchers.IO) {
        // 作者 long：文件恢复发生在 Room 事务退出之后，整笔保存都要串行，避免旧事务回滚覆盖另一笔已保存的正文。
        fileMutationMutex.withLock {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(current.type == ProfileType.FILE) { "只有本地文件配置支持替换文件" }
            val key = ageSecretKey.trim()
            val original = if (replacement != null) snapshotLocalProfileFile(profileFile(id)) else null
            val plaintext = replacement?.let { bytes ->
                require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
                contentDecoder.decode(bytes, key).also { decoded ->
                    require(decoded.isNotEmpty() && decoded.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
                    try { configValidator.validate(decoded) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { throw IllegalArgumentException("配置不是有效的 YAML，请检查文件内容") }
                }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // 作者 long：校验之后的文件/Room 写入共同完成或回滚，协程取消不能把文件和密钥拆成一半保存。
            withContext(kotlinx.coroutines.NonCancellable) {
                suspend fun persist(commit: () -> Unit): Profile = database.withTransaction {
                    check(canWrite()) { "核心运行中，请先停止核心后再修改配置" }
                    val latest = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                    require(latest == current) { "配置已变化，请重新打开编辑器" }
                    val updated = latest.copy(
                        ageSecretKey = key,
                        // 作者 long：只改解密密钥属于元数据调整，不下载、不重写缓存，也不伪造正文更新时间。
                        lastUpdateAt = if (plaintext == null) latest.lastUpdateAt else System.currentTimeMillis(),
                    )
                    profileDao.update(updated.toEntity())
                    check(canWrite()) { "核心运行中，请先停止核心后再修改配置" }
                    commit()
                    updated
                }
                if (plaintext == null) persist {} else replaceLocalProfileFile(profileFile(id), plaintext, ::persist, expected = checkNotNull(original))
            }
        }
    }

    override suspend fun updateProfileLabel(id: Long, label: String): Profile = withContext(Dispatchers.IO) {
        val normalized = label.trim()
        require(normalized.isNotEmpty()) { "配置名称不能为空" }
        require(normalized.length <= MAX_PROFILE_LABEL_LENGTH) { "配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符" }
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        val updated = current.copy(label = normalized)
        // 作者 long：名称只属于 Room 元数据，独立更新不会重写 YAML 或触发订阅下载，避免改名影响正在使用的配置内容。
        profileDao.update(updated.toEntity())
        updated
    }

    override suspend fun updateRemoteProfileSettings(
        id: Long,
        label: String,
        url: String,
        autoUpdate: Boolean,
        intervalMillis: Long,
        ageSecretKey: String,
    ): Profile = withContext(Dispatchers.IO) {
        val normalizedLabel = label.trim()
        require(normalizedLabel.length <= MAX_PROFILE_LABEL_LENGTH) {
            "配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符"
        }
        val normalizedUrl = normalizeSubscriptionUrl(url)
        require(mutableProfiles.value.none { it.id != id && it.url == normalizedUrl }) { "该订阅已经添加" }
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type == ProfileType.URL) { "只有远程订阅支持编辑订阅信息" }
        val updated = current.copy(
            label = normalizedLabel,
            url = normalizedUrl,
            autoUpdate = autoUpdate,
            autoUpdateIntervalMillis = intervalMillis.coerceAtLeast(MIN_AUTO_UPDATE_INTERVAL_MILLIS),
            ageSecretKey = ageSecretKey.trim(),
        )
        // 作者 long：沿用原版先保存元数据；地址或密钥变化后再下载，失败时保留旧缓存并允许修正密钥后重试。
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

    override suspend fun updateProfileGroupViewState(
        id: Long,
        groupName: String,
        unfoldSet: Set<String>,
    ): Profile =
        withContext(Dispatchers.IO) {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            // 作者 long：当前组和折叠状态由同一次 Room 更新保存，避免并发写整行时互相覆盖。
            val updated = current.copy(
                currentGroupName = groupName,
                unfoldSetJson = encodeUnfoldSet(unfoldSet),
            )
            profileDao.update(updated.toEntity())
            updated
        }

    override suspend fun updateProfileCurrentGroupName(id: Long, groupName: String): Profile =
        withContext(Dispatchers.IO) {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            // 作者 long：组页记忆按 Profile 隔离，切回同一配置时可恢复上次查看的组，不改动核心 YAML。
            val updated = current.copy(currentGroupName = groupName)
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
            val plaintext = contentDecoder.decode(downloaded.bytes, current.ageSecretKey)
            configValidator.validate(plaintext)
            val latest = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            // 作者 long：下载期间用户可能改了地址或密钥，旧请求不能把过期来源的明文覆盖到新设置下。
            require(latest.url == current.url && latest.ageSecretKey == current.ageSecretKey) { "订阅设置已变化，请重新更新" }
            replaceFileAtomically(id, plaintext)
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
        // 作者 long：删除与本地文件保存共用锁，避免失败保存恢复旧文件时把已删除的配置正文重新留在磁盘。
        fileMutationMutex.withLock {
            val profile = profileDao.getById(id) ?: return@withLock
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
        toActiveProfile(profile)
    }

    override suspend fun profileById(id: Long): ActiveProfile? = withContext(Dispatchers.IO) {
        // 作者 long：预览未选中的卡片时只读它自己的元数据；缺失就返回空，不影响当前配置偏好。
        profileDao.getById(id)?.toDomain()?.let { toActiveProfile(it) }
    }

    override suspend fun activeProfileId(): Long? = withContext(Dispatchers.IO) {
        // 作者 long：只核对当前 ID，不解析另一份配置的绑定脚本，损坏的当前配置不能挡住有效目标的预览。
        mutableCurrentProfileId.value?.let { profileDao.getById(it)?.id }
            ?: profileDao.getAll().firstOrNull()?.id
    }

    private suspend fun toActiveProfile(profile: Profile): ActiveProfile =
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
        const val MAX_PROFILE_LABEL_LENGTH = 64
    }
}

internal fun normalizeSubscriptionUrl(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "订阅链接不能为空" }
    val uri = runCatching { java.net.URI(normalized) }
        .getOrElse { throw IllegalArgumentException("订阅链接格式无效", it) }
    require(
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank(),
    ) { "请输入有效的 HTTP/HTTPS 订阅链接" }
    return normalized
}
