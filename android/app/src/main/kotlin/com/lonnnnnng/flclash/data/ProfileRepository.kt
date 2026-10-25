package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileRepository(
    context: Context,
    private val database: KClashDatabase,
    private val subscriptionClient: SubscriptionClient,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profileDao = database.profiles()
    private val preferenceDao = database.appPreferences()
    private val mutableProfiles = MutableStateFlow<List<Profile>>(emptyList())
    private val mutableCurrentProfileId = MutableStateFlow<Long?>(null)

    val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    val currentProfileId: StateFlow<Long?> = mutableCurrentProfileId.asStateFlow()

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

    suspend fun selectProfile(id: Long?) {
        val valid = id?.takeIf { candidate -> profileDao.getById(candidate) != null }
        mutableCurrentProfileId.value = valid
        persistCurrentProfile(valid)
    }

    suspend fun addRemoteProfile(url: String, label: String = ""): Profile = withContext(Dispatchers.IO) {
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

    suspend fun addFileProfile(label: String, bytes: ByteArray): Profile = withContext(Dispatchers.IO) {
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

    suspend fun addCustomProfile(label: String): Profile = withContext(Dispatchers.IO) {
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

    suspend fun updateRemoteProfile(id: Long): Profile = withContext(Dispatchers.IO) {
        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
        require(current.type == ProfileType.URL) { "只有订阅配置支持更新" }
        val downloaded = subscriptionClient.download(current.url)
        val latest = requireNotNull(profileDao.getById(id)).toDomain()
        replaceFileAtomically(id, downloaded.bytes)
        val updated = latest.copy(
            label = latest.label.ifBlank { downloaded.suggestedLabel.orEmpty() },
            lastUpdateAt = System.currentTimeMillis(),
            subscriptionInfo = downloaded.subscriptionInfo.takeIf { it != SubscriptionInfo() },
        )
        profileDao.update(updated.toEntity())
        updated
    }

    suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
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

    suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            ids.forEachIndexed { index, id -> profileDao.updateOrder(id, index) }
        }
    }

    fun profileFile(id: Long): File = File(File(appContext.filesDir, PROFILES_DIRECTORY), "$id.yaml")

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

    private fun newId(): Long = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    private companion object {
        const val CURRENT_PROFILE_KEY = "current_profile_id"
        const val PROFILES_DIRECTORY = "profiles"
    }
}
