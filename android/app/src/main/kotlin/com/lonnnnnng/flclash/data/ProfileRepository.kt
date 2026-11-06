package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import com.lonnnnnng.flclash.service.ActiveProfile
import com.lonnnnnng.flclash.service.ActiveProfileProvider
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import com.lonnnnnng.flclash.service.ProfileExtensionOverrides
import com.lonnnnnng.flclash.service.StandardProfileExtension
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
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

private val immediateProfileStoreLoaded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

interface ProfileStore {
    val profiles: StateFlow<List<Profile>>
    val currentProfileId: StateFlow<Long?>
    // 作者 long：内存数据源已经就绪；正式仓库须在首次Room读取与当前选择校正后发布，空列表才能表示确实没有配置。
    val profilesLoaded: StateFlow<Boolean> get() = immediateProfileStoreLoaded

    suspend fun selectProfile(id: Long?)
    suspend fun addRemoteProfile(url: String, label: String = "", ageSecretKey: String = ""): Profile
    suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String = ""): Profile
    suspend fun importFileProfile(label: String, bytes: ByteArray, ageSecretKey: String, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "导入已取消或核心状态已变化" }
        return addFileProfile(label, bytes, ageSecretKey)
    }
    suspend fun localProxies(): List<LocalProxy> = emptyList()
    suspend fun recentGroupIcons(): List<String> = emptyList()
    suspend fun recordGroupIcon(icon: String, remove: Boolean) {}
    suspend fun addCustomProfile(label: String): Profile
    suspend fun readLocalProfileContent(id: Long): String
    suspend fun readProfileSourceContent(id: Long): String = readLocalProfileContent(id)
    suspend fun readEditableProfileDocument(id: Long): ProfileDocumentSnapshot {
        val profile = extensionProfile(id) ?: error("配置不存在")
        val content = readProfileSourceContent(id)
        return ProfileDocumentSnapshot(profile, content, profileDocumentDigest(content.toByteArray()), 0)
    }
    suspend fun saveProfileDocument(expected: ProfileDocumentSnapshot, settings: ProfileDocumentSettings,
        replacement: ByteArray?, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "编辑会话已变化" }
        check(readProfileSourceContent(expected.profile.id) == expected.content) { "配置正文已变化，请重新打开编辑器" }
        if (expected.profile.type == ProfileType.FILE) return updateFileProfile(expected.profile.id, settings.ageSecretKey, replacement, settings.label, canWrite)
        check(replacement == null) { "此数据源不支持远程正文替换" }
        return updateRemoteProfileSettings(expected.profile.id, settings.label, settings.url, settings.autoUpdate, settings.intervalMillis, settings.ageSecretKey)
    }
    suspend fun updateLocalProfileContent(id: Long, content: String): Profile
    suspend fun saveCustomDocument(id: Long, content: String, expectedContent: String, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "编辑会话已变化" }
        check(readLocalProfileContent(id) == expectedContent) { "配置正文已变化，请重新打开编辑器" }
        return updateLocalProfileContent(id, content)
    }
    suspend fun saveCustomRules(id: Long, rules: String, expectedContent: String, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
        require(readLocalProfileContent(id) == expectedContent) { "配置正文已变化，请重新打开规则页" }
        return updateLocalProfileContent(id, ProfileRulesEditor.replaceRules(expectedContent, rules))
    }
    suspend fun saveCustomProxyGroups(id: Long, save: ProxyGroupsSave, expectedContent: String, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
        require(readLocalProfileContent(id) == expectedContent) { "配置正文已变化，请重新打开策略组" }
        return updateLocalProfileContent(id, ProxyGroupDrafts.apply(expectedContent, save))
    }
    suspend fun saveCustomProviders(id: Long, sections: ProfileProvidersEditor.Sections, overrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>, expectedContent: String, expectedOverrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>? = null, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
        require(readLocalProfileContent(id) == expectedContent) { "配置正文已变化，请重新打开资源页" }
        val baseline = extensionProfile(id) ?: error("配置不存在")
        check(expectedOverrides == null || ProfileProviderOverrides.fromJsonString(baseline.overridesJson).proxyProviders == expectedOverrides) { "Provider 选项已变化，请重新打开资源页" }
        updateLocalProfileContent(id, ProfileProvidersEditor.replace(expectedContent, sections))
        val latest = extensionProfile(id) ?: error("配置不存在")
        return updateProfileOverrides(id, ProfileProviderOverrides.fromJsonString(latest.overridesJson).copy(proxyProviders = overrides).toJsonString())
    }
    suspend fun downloadCustomImport(url: String): ByteArray = error("此数据源不支持 URL 导入")
    suspend fun decodeCustomImport(bytes: ByteArray, ageSecretKey: String): CustomProfileImportContent = customProfileImportContent(PlainProfileContentDecoder.decode(bytes, ageSecretKey))
    suspend fun replaceCustomProfile(id: Long, content: String, expected: Profile, expectedContent: String, canWrite: () -> Boolean): Profile =
        error("此数据源不支持自定义配置替换")
    suspend fun updateFileProfile(
        id: Long,
        ageSecretKey: String,
        replacement: ByteArray? = null,
        label: String? = null,
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
    suspend fun patchProfileOverrides(id: Long, canWrite: () -> Boolean, patch: (ProfileProviderOverrides) -> ProfileProviderOverrides): Profile {
        check(canWrite()) { "编辑会话已变化" }
        val current = extensionProfile(id) ?: error("配置不存在")
        return updateProfileOverrides(id, patch(ProfileProviderOverrides.fromJsonString(current.overridesJson)).toJsonString())
    }
    suspend fun saveMoreOptions(id: Long, patch: ProfileMorePatch, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "更多设置编辑状态已变化" }
        val current = extensionProfile(id) ?: error("配置不存在")
        return updateProfileOverrides(id, patch.applyTo(current.overridesJson))
    }
    suspend fun saveProviderOptions(id: Long, overrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>, expectedOverrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>? = null, canWrite: () -> Boolean): Profile {
        check(canWrite()) { "资源编辑状态已变化" }
        val current = extensionProfile(id) ?: error("配置不存在")
        check(expectedOverrides == null || ProfileProviderOverrides.fromJsonString(current.overridesJson).proxyProviders == expectedOverrides) { "Provider 选项已变化，请重新打开资源页" }
        return updateProfileOverrides(id, ProfileProviderOverrides.fromJsonString(current.overridesJson).copy(proxyProviders = overrides).toJsonString())
    }
    suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile
    suspend fun updateProfileProxySelection(id: Long, groupName: String, proxyName: String?): Profile {
        val current = profiles.value.first { it.id == id }
        val selected = decodeSelectedMap(current.selectedMapJson).toMutableMap().apply {
            if (proxyName == null) remove(groupName) else put(groupName, proxyName)
        }
        return updateProfileSelectedMap(id, selected)
    }
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
    suspend fun extensionProfile(id: Long): Profile? = profiles.value.firstOrNull { it.id == id }
    suspend fun globalExtensionRules(): List<ExtensionRule> = emptyList()
    suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) { error("此数据源不支持全局附加规则") }
    suspend fun compareAndSaveGlobalExtensionRules(expected: List<ExtensionRule>, rules: List<ExtensionRule>) {
        check(globalExtensionRules() == expected) { "全局规则已变化，请重新读取后保存" }
        saveGlobalExtensionRules(rules)
    }
    suspend fun saveExtension(id: Long, mode: String, scriptId: Long?, matchTarget: String?, extension: ProfileExtensionOverrides): Profile =
        error("此数据源不支持扩展设置")
    suspend fun updateRemoteProfile(id: Long): Profile
    suspend fun deleteProfile(id: Long)
    suspend fun proxyResourceUsers(id: Long): List<String> = emptyList()
    suspend fun proxyResourceUsage(): Map<Long, List<String>> = emptyMap()
    suspend fun reorder(ids: List<Long>)
}

class ProfileRepository(
    context: Context,
    private val database: KClashDatabase,
    private val subscriptionClient: SubscriptionClient,
    private val configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
    private val contentDecoder: ProfileContentDecoder = PlainProfileContentDecoder,
    private val resources: RuleResourceRepository? = null,
    private val automaticCommitter: RemoteProfileCommitter? = null,
    observationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ActiveProfileProvider, ProfileStore, RemoteProfileUpdater, ProfileExportReader {
    private val appContext = context.applicationContext
    // 作者 long：观察生命周期由仓库拥有者管理；隔离数据库关闭前先取消并等待观察结束，不能让迟到读取拖垮后续任务。
    private val scope = observationScope
    private val profileDao = database.profiles()
    private val preferenceDao = database.appPreferences()
    private val mutableProfiles = MutableStateFlow<List<Profile>>(emptyList())
    private val mutableCurrentProfileId = MutableStateFlow<Long?>(null)
    private val mutableProfilesLoaded = MutableStateFlow(false)
    // 作者 long：锁覆盖文件替换、Room 提交和失败恢复，后续保存不能在旧正文回滚完成前写入同一路径。
    private val fileMutationMutex = Mutex()
    private val automaticUpdateMutex = Mutex()

    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = mutableCurrentProfileId.asStateFlow()
    override val profilesLoaded: StateFlow<Boolean> = mutableProfilesLoaded.asStateFlow()

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
                mutableProfilesLoaded.value = true
            }
        }
    }

    private val iconHistoryMutex = Mutex()

    override suspend fun recentGroupIcons(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            com.google.gson.JsonParser.parseString(preferenceDao.get("group_icon_history") ?: "[]").asJsonArray
                .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.map { it.asString }
        }.getOrDefault(emptyList())
    }

    override suspend fun recordGroupIcon(icon: String, remove: Boolean) = withContext(Dispatchers.IO) {
        if (icon.isBlank() || icon.length > 256 * 1024) return@withContext
        iconHistoryMutex.withLock {
            val previous = recentGroupIcons().filterNot { it == icon }
            val candidates = if (remove) previous else listOf(icon) + previous
            // 作者 long：最近图标只存有界记录，避免本地 data 图片把单行偏好撑过 SQLite CursorWindow。
            var size = 0
            val bounded = candidates.take(24).takeWhile { size += it.length; size <= 512 * 1024 }
            preferenceDao.put(AppPreferenceEntity("group_icon_history", ProxyGroupDrafts.array(bounded).toString(), System.currentTimeMillis()))
        }
    }

    val localProxyChanges = preferenceDao.observe(LocalProxies.KEY)
    override suspend fun localProxies(): List<LocalProxy> = LocalProxies.decode(preferenceDao.get(LocalProxies.KEY))

    suspend fun localProxyUsers(names: Set<String>): List<String> = withContext(Dispatchers.IO) {
        profileDao.getAll().filter { it.type == ProfileType.CUSTOM }.mapNotNull {
            if (LocalProxies.referenced(profileFile(it.id).readText()).any(names::contains)) it.toDomain().realLabel else null
        }
    }

    suspend fun saveLocalProxies(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>? = null) = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            check(localProxies() == expected) { "本地代理已变化，请重新打开编辑器" }
            val custom = profileDao.getAll().filter { it.type == ProfileType.CUSTOM }
            val sources = custom.associate { it.id to profileFile(it.id).readText() }
            val groupNames = sources.values.flatMap { ProxyGroupDrafts.read(it).map { group -> group.name } }.toSet()
            LocalProxies.validate(next, groupNames)
            val changes = if (rename == null) emptyList() else custom.mapNotNull { entity ->
                val source = sources.getValue(entity.id)
                // 作者 long：同名组拥有自己的引用空间；与上游一样，不能把组引用误改成全局节点名。
                if (ProxyGroupDrafts.read(source).any { it.name == rename.first || it.name == rename.second } || rename.first in ProxyGroupDrafts.proxyNames(source)) null else {
                    val updated = LocalProxyReferences.rename(source, rename.first, rename.second)
                    require(updated == source || rename.second !in ProxyGroupDrafts.proxyNames(source)) { "新名称与配置内节点重名，请使用其他名称" }
                    if (ProxyGroupDrafts.root(updated) == ProxyGroupDrafts.root(source) && LocalProxyReferences.metadata(entity.toDomain(), rename.first, rename.second) == entity.toDomain()) null else {
                        val snapshot = snapshotLocalProfileFile(profileFile(entity.id))
                        check(snapshot.bytes?.toString(Charsets.UTF_8) == source) { "配置正文已变化，请重试" }
                        Triple(entity, updated, snapshot)
                    }
                }
            }
            // 作者 long：多份配置的引用和全局目录共同提交；任意文件或数据库失败，沿嵌套提交逐层恢复原正文与时间。
            withContext(kotlinx.coroutines.NonCancellable) {
                val commits = mutableListOf<() -> Unit>()
                suspend fun commitAt(index: Int) {
                    if (index < changes.size) {
                        val (entity, content, snapshot) = changes[index]
                        replaceLocalProfileFile(profileFile(entity.id), content.toByteArray(), { commit ->
                            commits += commit
                            commitAt(index + 1)
                        }, expected = snapshot)
                    } else database.withTransaction {
                        check(localProxies() == expected && profileDao.getAll().filter { it.type == ProfileType.CUSTOM } == custom) { "配置已变化，请重试" }
                        changes.forEach { (entity, _, _) ->
                            val updated = LocalProxyReferences.metadata(entity.toDomain(), rename!!.first, rename.second)
                                .copy(lastUpdateAt = System.currentTimeMillis())
                            profileDao.update(updated.toEntity())
                        }
                        preferenceDao.put(AppPreferenceEntity(LocalProxies.KEY, LocalProxies.encode(next), System.currentTimeMillis()))
                        commits.forEach { it() }
                    }
                }
                commitAt(0)
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

    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile =
        importFileProfile(label, bytes, ageSecretKey) { true }

    override suspend fun importFileProfile(label: String, bytes: ByteArray, ageSecretKey: String, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
        val plaintext = contentDecoder.decode(bytes, ageSecretKey)
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PROFILE_FILE_BYTES) { "解密后的配置为空或超过 8 MiB" }
        configValidator.validate(plaintext)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        fileMutationMutex.withLock {
            check(canWrite()) { "导入已取消或核心状态已变化" }
            val profile = Profile(
                id = newId(), type = ProfileType.FILE, label = label.trim(), url = "", ageSecretKey = ageSecretKey.trim(),
                lastUpdateAt = System.currentTimeMillis(), autoUpdate = false,
                autoUpdateIntervalMillis = Profile.DEFAULT_UPDATE_INTERVAL_MILLIS, subscriptionInfo = null,
                orderIndex = profileDao.getAll().size,
            )
            // 作者 long：解密和校验期间不创建 Profile；确认写入后让文件与 Room 一起完成或回滚，取消不能留下半份导入。
            withContext(kotlinx.coroutines.NonCancellable) {
                replaceLocalProfileFile(profileFile(profile.id), plaintext, { commit ->
                    database.withTransaction {
                        check(canWrite()) { "导入已取消或核心状态已变化" }
                        profileDao.insert(profile.toEntity())
                        commit()
                        check(canWrite()) { "导入已取消或核心状态已变化" }
                        profile
                    }
                }, expected = LocalProfileFileSnapshot(null, 0L))
                ensureCurrentProfile(profile.id)
                profile
            }
        }
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

    override suspend fun readProfileSourceContent(id: Long): String = withContext(Dispatchers.IO) {
        requireNotNull(profileDao.getById(id)) { "配置不存在" }
        // 作者 long：扩展页读取目标配置自己的缓存，远程配置只读，不能借此改写订阅正文。
        val file = profileFile(id)
        require(file.isFile && file.length() <= MAX_PROFILE_FILE_BYTES) { "配置缓存不存在或超过 8 MiB" }
        file.readText(Charsets.UTF_8)
    }

    override suspend fun readEditableProfileDocument(id: Long): ProfileDocumentSnapshot = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            val profile = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(profile.type != ProfileType.CUSTOM) { "请从自定义配置分区编辑" }
            val snapshot = snapshotLocalProfileFile(profileFile(id))
            val bytes = requireNotNull(snapshot.bytes) { "配置缓存不存在" }
            ProfileDocumentSnapshot(profile, profileDocumentText(bytes), profileDocumentDigest(bytes), snapshot.modified)
        }
    }

    override suspend fun saveProfileDocument(expected: ProfileDocumentSnapshot, settings: ProfileDocumentSettings,
        replacement: ByteArray?, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        val before = expected.profile
        require(before.type == ProfileType.FILE || before.type == ProfileType.URL)
        val label = settings.label.trim()
        require(label.isNotEmpty() && label.length <= MAX_PROFILE_LABEL_LENGTH) { "配置名称不能为空，最多 64 个字符" }
        val url = if (before.type == ProfileType.URL) normalizeSubscriptionUrl(settings.url) else before.url
        val key = settings.ageSecretKey.trim()
        val plaintext = replacement?.let { bytes ->
            require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
            val decoded = contentDecoder.decode(bytes, key)
            profileDocumentText(decoded)
            try { configValidator.validate(decoded) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw IllegalArgumentException("配置不是有效的 YAML，请检查文件内容") }
            decoded
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        fileMutationMutex.withLock {
            val file = profileFile(before.id)
            val original = snapshotLocalProfileFile(file)
            check(original.bytes != null && original.modified == expected.modified && profileDocumentDigest(original.bytes) == expected.digest) {
                "配置正文已变化，请重新打开编辑器"
            }
            withContext(kotlinx.coroutines.NonCancellable) {
                suspend fun persist(commit: () -> Unit) = database.withTransaction {
                    check(canWrite()) { "编辑会话已变化，请重试" }
                    val latest = requireNotNull(profileDao.getById(before.id)) { "配置不存在" }.toDomain()
                    // 作者 long：仅比较表单所属字段，运行中选节点/组页记忆/覆盖设置保留最新值；外部改源、改名和同步必须重开。
                    check(latest.type == before.type && latest.documentSettings() == before.documentSettings() && latest.lastUpdateAt == before.lastUpdateAt) {
                        "配置设置已变化，请重新打开编辑器"
                    }
                    if (latest.type == ProfileType.URL) require(profileDao.getAll().none { it.id != latest.id && it.url == url }) { "该订阅已经添加" }
                    val next = latest.copy(label = label, url = url, ageSecretKey = key,
                        autoUpdate = if (latest.type == ProfileType.URL) settings.autoUpdate else latest.autoUpdate,
                        autoUpdateIntervalMillis = if (latest.type == ProfileType.URL) settings.intervalMillis.coerceAtLeast(MIN_AUTO_UPDATE_INTERVAL_MILLIS) else latest.autoUpdateIntervalMillis,
                        lastUpdateAt = if (plaintext != null) System.currentTimeMillis() else latest.lastUpdateAt,
                        lastUpdateError = if (plaintext != null) null else latest.lastUpdateError)
                    commit()
                    profileDao.update(next.toEntity())
                    check(canWrite()) { "编辑会话已变化，请重试" }
                    next
                }
                // 作者 long：元数据与新正文一起提交；Room失败由同一文件工具恢复原字节和mtime，取消也不能只写一半。
                if (plaintext == null) persist {} else replaceLocalProfileFile(file, plaintext, ::persist, expected = original)
            }
        }
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

    override suspend fun saveCustomRules(id: Long, rules: String, expectedContent: String, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            val content = ProfileRulesEditor.replaceRules(expectedContent, rules)
            configValidator.validate(content.toByteArray(Charsets.UTF_8))
            val file = profileFile(id)
            val original = snapshotLocalProfileFile(file)
            require(original.bytes?.toString(Charsets.UTF_8) == expectedContent) { "配置正文已变化，请重新打开规则页" }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // 作者 long：每次规则操作只提交该分区；事务内读取最新元数据，正文过期或运行状态变化时回滚文件与 Room。
            withContext(kotlinx.coroutines.NonCancellable) {
                replaceLocalProfileFile(file, content.toByteArray(Charsets.UTF_8), { commit ->
                    database.withTransaction {
                        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                        require(current.type == ProfileType.CUSTOM) { "只有自定义配置支持规则编辑" }
                        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                        val updated = current.copy(lastUpdateAt = System.currentTimeMillis())
                        profileDao.update(updated.toEntity())
                        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                        commit()
                        updated
                    }
                }, expected = original)
            }
        }
    }

    override suspend fun saveCustomDocument(id: Long, content: String, expectedContent: String, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        profileDocumentText(bytes)
        configValidator.validate(bytes)
        fileMutationMutex.withLock {
            val file = profileFile(id)
            val original = snapshotLocalProfileFile(file)
            check(original.bytes?.toString(Charsets.UTF_8) == expectedContent) { "配置正文已变化，请重新打开编辑器" }
            // 作者 long：完整正文与分区保存使用同一文件锁及Room补偿，不能把旧父草稿覆盖到新分区或新节点选择上。
            withContext(kotlinx.coroutines.NonCancellable) {
                replaceLocalProfileFile(file, bytes, { commit ->
                    database.withTransaction {
                        val latest = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                        require(latest.type == ProfileType.CUSTOM) { "只有自定义配置支持分区编辑" }
                        check(canWrite()) { "编辑会话已变化" }
                        val next = latest.copy(lastUpdateAt = System.currentTimeMillis())
                        commit()
                        profileDao.update(next.toEntity())
                        check(canWrite()) { "编辑会话已变化" }
                        next
                    }
                }, expected = original)
            }
        }
    }

    override suspend fun saveCustomProxyGroups(id: Long, save: ProxyGroupsSave, expectedContent: String, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            save.providerProfiles.values.distinct().forEach { resourceId ->
                require(profileDao.getById(resourceId)?.type?.let { it != ProfileType.CUSTOM } == true) { "代理资源已删除，请重新选择" }
            }
            val candidates = LocalProxies.candidates(expectedContent, localProxies())
            ProxyGroupDrafts.validate(save.groups, candidates)
            val content = ProxyGroupDrafts.apply(expectedContent, save, localProxies().map { it.name })
            configValidator.validate(content.toByteArray(Charsets.UTF_8))
            val file = profileFile(id)
            val original = snapshotLocalProfileFile(file)
            require(original.bytes?.toString(Charsets.UTF_8) == expectedContent) { "配置正文已变化，请重新打开策略组" }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // 作者 long：策略组与名称引用共同提交；沿用最新元数据，保留脚本绑定、DNS 覆盖和用户配置选择。
            withContext(kotlinx.coroutines.NonCancellable) {
                replaceLocalProfileFile(file, content.toByteArray(Charsets.UTF_8), { commit ->
                    database.withTransaction {
                        val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                        require(current.type == ProfileType.CUSTOM) { "只有自定义配置支持策略组编辑" }
                        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                        val updated = ProxyGroupDrafts.updateProfileReferences(current, save).copy(lastUpdateAt = System.currentTimeMillis())
                        profileDao.update(updated.toEntity())
                        check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                        commit()
                        updated
                    }
                }, expected = original)
            }
        }
    }

    override suspend fun saveCustomProviders(id: Long, sections: ProfileProvidersEditor.Sections, overrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>, expectedContent: String, expectedOverrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>?, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            // 作者 long：只保存选项覆盖时正文应逐字节保持，避免一次字段确认无意义地重排资源定义。
            val content = if (sections == ProfileProvidersEditor.extract(expectedContent)) expectedContent else ProfileProvidersEditor.replace(expectedContent, sections)
            configValidator.validate(content.toByteArray())
            val file = profileFile(id)
            val original = snapshotLocalProfileFile(file)
            require(original.bytes?.toString(Charsets.UTF_8) == expectedContent) { "配置正文已变化，请重新打开资源页" }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // 作者 long：资源引用和字段覆盖一次提交；事务读取最新元数据，不能覆盖另一个分区刚保存的 DNS 或脚本。
            withContext(kotlinx.coroutines.NonCancellable) {
                suspend fun updateMetadata(commit: () -> Unit): Profile = database.withTransaction {
                    val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                    require(current.type == ProfileType.CUSTOM) { "只有自定义配置支持资源绑定" }
                    check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                    val latestOverrides = ProfileProviderOverrides.fromJsonString(current.overridesJson)
                    // 作者 long：正文未变也可能有另一个窗口修改同一Provider，必须在事务内比较本分区，其他分区仍按最新值合并。
                    check(expectedOverrides == null || latestOverrides.proxyProviders == expectedOverrides) { "Provider 选项已变化，请重新打开资源页" }
                    val merged = latestOverrides.copy(proxyProviders = overrides)
                    val updated = current.copy(overridesJson = merged.toJsonString(), lastUpdateAt = System.currentTimeMillis())
                    profileDao.update(updated.toEntity())
                    check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                    commit()
                    check(canWrite()) { "核心状态已变化，请停止核心后重新编辑" }
                    updated
                }
                // 作者 long：仅字段确认不触碰源文件与 mtime；资源定义变更才进入原子文件/Room 共同提交。
                if (content == expectedContent) updateMetadata {} else {
                    replaceLocalProfileFile(file, content.toByteArray(), { commit -> updateMetadata(commit) }, expected = original)
                }
            }
        }
    }

    override suspend fun downloadCustomImport(url: String): ByteArray = withContext(Dispatchers.IO) {
        subscriptionClient.download(normalizeSubscriptionUrl(url)).bytes
    }

    override suspend fun decodeCustomImport(bytes: ByteArray, ageSecretKey: String): CustomProfileImportContent = withContext(Dispatchers.IO) {
        customProfileImportContent(contentDecoder.decode(bytes, ageSecretKey))
    }

    override suspend fun replaceCustomProfile(id: Long, content: String, expected: Profile, expectedContent: String, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_PROFILE_FILE_BYTES && content.isNotBlank()) { "配置为空或超过 8 MiB" }
        configValidator.validate(content.toByteArray(Charsets.UTF_8))
        check(canWrite()) { "核心运行中，请先停止核心后再修改配置" }
        val localBefore = localProxies()
        val groupNames = profileDao.getAll().filter { it.type == ProfileType.CUSTOM && it.id != id }
            .flatMap { ProxyGroupDrafts.read(profileFile(it.id).readText()).map { group -> group.name } }.toSet() + ProxyGroupDrafts.read(content).map { it.name }
        val localImport = planLocalProxyImport(content, localBefore, groupNames)
        LocalProxies.validate(localImport.after, groupNames)
        val proxyImport = prepareProxyImport(localImport.source)
        val createdFiles = mutableListOf<File>()
        // 作者 long：下载和核心编译不能持有配置文件锁，否则核心读取配置时会与编译初始化形成反向等待。
        val commit: suspend (String, suspend () -> Unit) -> Profile = { mappedContent, persistResources ->
            fileMutationMutex.withLock {
                val file = profileFile(id)
                val original = snapshotLocalProfileFile(file)
                require(original.bytes?.toString(Charsets.UTF_8) == expectedContent) { "配置正文已变化或缺失，请重新导入" }
                // 作者 long：导入下载期间其他配置可能改了策略组名称，提交前复核所有名称空间，避免新全局节点遮蔽策略组。
                val currentGroups = profileDao.getAll().filter { it.type == ProfileType.CUSTOM && it.id != id }
                    .flatMap { ProxyGroupDrafts.read(profileFile(it.id).readText()).map { group -> group.name } } + ProxyGroupDrafts.read(mappedContent).map { it.name }
                LocalProxies.validate(localImport.after, currentGroups.toSet())
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // 作者 long：导入同时替换正文、全局资源、覆盖与旧节点选择，文件和 Room 共同提交或回滚。
                withContext(kotlinx.coroutines.NonCancellable) {
                    replaceLocalProfileFile(file, mappedContent.toByteArray(Charsets.UTF_8), { commitFile ->
                        database.withTransaction {
                            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                            require(current.type == ProfileType.CUSTOM && current == expected) { "配置已变化，请重新导入" }
                            check(canWrite()) { "核心运行中，请先停止核心后再修改配置" }
                            require(profileDao.getAll() == proxyImport.before) { "配置资源已变化，请重新确认导入" }
                            proxyImport.added.forEach { (resource, bytes) ->
                                val target = profileFile(resource.id)
                                require(!target.exists()) { "代理资源文件已存在，请重新导入" }
                                createdFiles += target
                                replaceFileAtomically(resource.id, bytes)
                                profileDao.insert(resource.toEntity())
                            }
                            check(localProxies() == localImport.before) { "本地代理已变化，请重新确认导入" }
                            preferenceDao.put(AppPreferenceEntity(LocalProxies.KEY, LocalProxies.encode(localImport.after), System.currentTimeMillis()))
                            persistResources()
                            val updated = current.copy(overridesJson = ProfileProviderOverrides(dialers = localImport.dialers.takeIf { it.isNotEmpty() }).toJsonString(),
                                selectedMapJson = "{}", currentGroupName = "", unfoldSetJson = "[]", lastUpdateAt = System.currentTimeMillis())
                            profileDao.update(updated.toEntity())
                            check(canWrite()) { "核心运行中，请先停止核心后再修改配置" }
                            commitFile()
                            check(canWrite()) { "导入已取消或核心状态已变化" }
                            updated
                        }
                    }, expected = original)
                }
            }
        }
        var committed = false
        try {
            (resources?.importResources(proxyImport.content, commit) ?: commit(proxyImport.content) {}).also { committed = true }
        } finally {
            // 作者 long：Room 回滚后只回收本次新分配的代理文件，已有订阅和当前配置始终保留。
            if (!committed) createdFiles.forEach { it.delete() }
        }
    }

    private data class ProxyImportPlan(val content: String, val before: List<ProfileEntity>, val added: List<Pair<Profile, ByteArray>>)

    private suspend fun prepareProxyImport(content: String): ProxyImportPlan {
        val before = profileDao.getAll()
        val root = ProxyGroupDrafts.root(content)
        val definitions = root.get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return ProxyImportPlan(content, before, emptyList())
        if (definitions.size() == 0) return ProxyImportPlan(content, before, emptyList())
        val profiles = before.map { it.toDomain() }.toMutableList()
        val held = profiles.flatMap { listOf(it.label, it.realLabel) }.toMutableSet()
        val names = linkedMapOf<String, String>()
        val mapped = com.google.gson.JsonObject()
        val added = mutableListOf<Pair<Profile, ByteArray>>()
        for ((name, definition) in definitions.entrySet()) {
            if (ProfileProxyResources.idOf(definition) != null) { mapped.add(name, definition.deepCopy()); continue }
            require(definition.isJsonObject) { "代理资源定义无效" }
            val fields = definition.asJsonObject
            val type = fields.get("type")?.asString
            val url = if (type == "http") normalizeSubscriptionUrl(fields.get("url")?.asString.orEmpty()) else ""
            val key = if (url.isNotEmpty()) fields.get("age-secret-key")?.asString.orEmpty().trim() else ""
            val existing = profiles.firstOrNull { it.type != ProfileType.CUSTOM && url.isNotEmpty() && it.url == url && (key.isEmpty() || it.ageSecretKey == key) }
            val resource = existing ?: run {
                val label = ProfileProxyResources.uniqueLabel(name, held)
                val downloaded = if (url.isNotEmpty()) subscriptionClient.download(url) else null
                val bytes = if (downloaded != null) contentDecoder.decode(downloaded.bytes, key) else {
                    require(type == "inline" && fields.get("payload")?.isJsonArray == true) { "本地代理资源缺少节点正文，请先导入该资源后重新绑定" }
                    com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(com.google.gson.JsonObject().apply {
                        add("proxies", fields.get("payload").deepCopy())
                    }.toString()).toByteArray()
                }
                require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "代理资源为空或超过 8 MiB" }
                configValidator.validate(bytes)
                val resourceId = generateSequence { newId() }.first { candidate -> candidate > 0 && profiles.none { it.id == candidate } && !profileFile(candidate).exists() }
                val profile = Profile.remote(resourceId, url, label).copy(
                    type = if (url.isEmpty()) ProfileType.FILE else ProfileType.URL,
                    ageSecretKey = key, autoUpdate = url.isNotEmpty(), lastUpdateAt = System.currentTimeMillis(),
                    subscriptionInfo = downloaded?.subscriptionInfo, orderIndex = profiles.size,
                )
                profiles += profile; held += label; added += profile to bytes
                profile
            }
            val alias = mapped.entrySet().firstOrNull { ProfileProxyResources.idOf(it.value) == resource.id }?.key
                ?: ProfileProxyResources.uniqueLabel(resource.realLabel, mapped.keySet() + definitions.entrySet().filter { ProfileProxyResources.idOf(it.value) != null }.map { it.key })
            names[name] = alias
            // 作者 long：来源字段转为稳定绑定，其余核心选项留在配置内，两个配置引用同一订阅仍可独立过滤和测速。
            val options = fields.deepCopy()
            listOf("url", "payload", "age-secret-key", "path", "interval").forEach(options::remove)
            ProfileProxyResources.reference(resource.id).entrySet().forEach { options.add(it.key, it.value) }
            mapped.add(alias, options)
        }
        root.add("proxy-providers", mapped)
        ProfileProxyResources.renameUses(root, names)
        return ProxyImportPlan(com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(root.toString()), before, added)
    }

    override suspend fun updateFileProfile(
        id: Long,
        ageSecretKey: String,
        replacement: ByteArray?,
        label: String?,
        canWrite: () -> Boolean,
    ): Profile = withContext(Dispatchers.IO) {
        // 作者 long：文件恢复发生在 Room 事务退出之后，整笔保存都要串行，避免旧事务回滚覆盖另一笔已保存的正文。
        fileMutationMutex.withLock {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(current.type == ProfileType.FILE) { "只有本地文件配置支持替换文件" }
            val key = ageSecretKey.trim()
            val normalizedLabel = label?.trim() ?: current.label
            if (label != null) {
                require(normalizedLabel.isNotEmpty()) { "配置名称不能为空" }
                require(normalizedLabel.length <= MAX_PROFILE_LABEL_LENGTH) { "配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符" }
            }
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
                        // 作者 long：名称、密钥和替换文件共用同一事务，文件失败时不能只留下新名称。
                        label = normalizedLabel,
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
        database.withTransaction {
            val normalized = label.trim()
            require(normalized.isNotEmpty()) { "配置名称不能为空" }
            require(normalized.length <= MAX_PROFILE_LABEL_LENGTH) { "配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符" }
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            val updated = current.copy(label = normalized)
            // 作者 long：名称只属于 Room 元数据，独立更新不会重写 YAML 或触发订阅下载，避免改名影响正在使用的配置内容。
            profileDao.update(updated.toEntity())
            updated
        }
    }

    override suspend fun updateRemoteProfileSettings(
        id: Long,
        label: String,
        url: String,
        autoUpdate: Boolean,
        intervalMillis: Long,
        ageSecretKey: String,
    ): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            val normalizedLabel = label.trim()
            require(normalizedLabel.length <= MAX_PROFILE_LABEL_LENGTH) {
                "配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符"
            }
            val normalizedUrl = normalizeSubscriptionUrl(url)
            require(profileDao.getAll().none { it.id != id && it.url == normalizedUrl }) { "该订阅已经添加" }
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
    }

    override suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                val updated = current.copy(overridesJson = overridesJson)
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun saveMoreOptions(id: Long, patch: ProfileMorePatch, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            // 作者 long：事务内重读最新整行，仅提交本次差异；启停或会话变更必须回滚，且不重写原YAML文件。
            check(canWrite()) { "更多设置编辑状态已变化，请停止核心后重试" }
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(current.type == ProfileType.CUSTOM) { "请从自定义配置编辑更多设置" }
            val updated = current.copy(overridesJson = patch.applyTo(current.overridesJson))
            profileDao.update(updated.toEntity())
            check(canWrite()) { "更多设置编辑状态已变化，请停止核心后重试" }
            updated
        }
    }

    override suspend fun patchProfileOverrides(id: Long, canWrite: () -> Boolean, patch: (ProfileProviderOverrides) -> ProfileProviderOverrides): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            // 作者 long：DNS/链式代理在事务内仅合并本分区，排队保存时不能把其他分区的新值换回旧快照。
            check(canWrite()) { "编辑会话已变化" }
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            val updated = current.copy(overridesJson = patch(ProfileProviderOverrides.fromJsonString(current.overridesJson)).toJsonString())
            profileDao.update(updated.toEntity())
            check(canWrite()) { "编辑会话已变化" }
            updated
        }
    }

    override suspend fun saveProviderOptions(id: Long, overrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>, expectedOverrides: Map<String, com.lonnnnnng.flclash.service.ProxyProviderOverride>?, canWrite: () -> Boolean): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            // 作者 long：订阅/文件配置仅更新本分区，事务内读取最新 DNS/脚本等元数据，并拒绝关闭或启停后的迟到提交。
            check(canWrite()) { "资源编辑状态已变化" }
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            val latest = ProfileProviderOverrides.fromJsonString(current.overridesJson)
            check(expectedOverrides == null || latest.proxyProviders == expectedOverrides) { "Provider 选项已变化，请重新打开资源页" }
            val updated = current.copy(overridesJson = latest.copy(proxyProviders = overrides).toJsonString())
            profileDao.update(updated.toEntity())
            check(canWrite()) { "资源编辑状态已变化" }
            updated
        }
    }

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                val updated = current.copy(selectedMapJson = encodeSelectedMap(selectedMap))
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun updateProfileProxySelection(id: Long, groupName: String, proxyName: String?): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            // 作者 long：一次节点点击只修改自己的组，保留其他入口刚保存的选择和配置元数据。
            val selected = decodeSelectedMap(current.selectedMapJson).toMutableMap().apply {
                if (proxyName == null) remove(groupName) else put(groupName, proxyName)
            }
            val updated = current.copy(selectedMapJson = encodeSelectedMap(selected))
            profileDao.update(updated.toEntity())
            updated
        }
    }

    override suspend fun updateProfileGroupViewState(
        id: Long,
        groupName: String,
        unfoldSet: Set<String>,
    ): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                // 作者 long：当前组和折叠状态由同一次 Room 更新保存，避免并发写整行时互相覆盖。
                val updated = current.copy(
                    currentGroupName = groupName,
                    unfoldSetJson = encodeUnfoldSet(unfoldSet),
                )
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun updateProfileCurrentGroupName(id: Long, groupName: String): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                // 作者 long：组页记忆按 Profile 隔离，切回同一配置时可恢复上次查看的组，不改动核心 YAML。
                val updated = current.copy(currentGroupName = groupName)
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun updateProfileAutoUpdate(
        id: Long,
        enabled: Boolean,
        intervalMillis: Long,
    ): Profile = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(current.type == ProfileType.URL) { "只有订阅配置支持自动更新" }
            val updated = current.copy(
                autoUpdate = enabled,
                autoUpdateIntervalMillis = intervalMillis.coerceAtLeast(MIN_AUTO_UPDATE_INTERVAL_MILLIS),
            )
            profileDao.update(updated.toEntity())
            updated
        }
    }

    override suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 作者 long：校验脚本存在与写入 Profile 必须原子完成，避免脚本删除并发时留下悬挂绑定。
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                if (scriptId != null) {
                    requireNotNull(database.scripts().getById(scriptId)) { "脚本不存在" }
                }
                val updated = current.copy(scriptId = scriptId,
                    extendType = if (scriptId != null && current.type != ProfileType.CUSTOM) "script" else current.extendType)
                profileDao.update(updated.toEntity())
                updated
            }
        }

    override suspend fun extensionProfile(id: Long): Profile? = withContext(Dispatchers.IO) { profileDao.getById(id)?.toDomain() }

    override suspend fun globalExtensionRules(): List<ExtensionRule> =
        ExtensionRules.decode(preferenceDao.get(ExtensionRules.PREFERENCE_KEY))

    override suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) = withContext(Dispatchers.IO) {
        ExtensionRules.validate(rules)
        preferenceDao.put(AppPreferenceEntity(ExtensionRules.PREFERENCE_KEY, ExtensionRules.toJson(rules).toString(), System.currentTimeMillis()))
    }

    override suspend fun compareAndSaveGlobalExtensionRules(expected: List<ExtensionRule>, rules: List<ExtensionRule>) = withContext(Dispatchers.IO) {
        ExtensionRules.validate(rules)
        // 作者 long：全局管理页和配置扩展页共享目录；事务内复核完整旧快照，拒绝迟到页面覆盖新的规则或排序。
        database.withTransaction {
            check(globalExtensionRules() == expected) { "全局规则已变化，请重新读取后保存" }
            saveGlobalExtensionRules(rules)
        }
    }

    override suspend fun saveExtension(id: Long, mode: String, scriptId: Long?, matchTarget: String?, extension: ProfileExtensionOverrides): Profile =
        withContext(Dispatchers.IO) {
            require(mode in setOf("standard", "script")) { "未知扩展模式" }
            ExtensionRules.validate(extension.rules)
            require(matchTarget == null || matchTarget.isNotBlank() && ',' !in matchTarget && '\n' !in matchTarget) { "默认目标无效" }
            database.withTransaction {
                val current = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                require(current.type != ProfileType.CUSTOM) { "自定义配置请直接编辑规则" }
                if (scriptId != null) requireNotNull(database.scripts().getById(scriptId)) { "脚本不存在" }
                // 作者 long：扩展只改自己的元数据，合并事务内最新 DNS/Provider 等覆盖，运行期间也不会重写订阅正文。
                val overrides = ProfileProviderOverrides.fromJsonString(current.overridesJson).copy(extension = extension.copy(configured = true))
                current.copy(extendType = mode, scriptId = scriptId, matchTarget = matchTarget, overridesJson = overrides.toJsonString()).also {
                    profileDao.update(it.toEntity())
                }
            }
        }

    override suspend fun updateRemoteProfile(id: Long): Profile = updateRemoteProfile(id, automatic = false)

    private suspend fun updateRemoteProfile(id: Long, automatic: Boolean): Profile = withContext(Dispatchers.IO) {
        val (current, original) = fileMutationMutex.withLock {
            val row = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
            require(row.type == ProfileType.URL) { "只有订阅配置支持更新" }
            row to snapshotLocalProfileFile(profileFile(id))
        }
        try {
            // 作者 long：下载不占目录锁；完成后重新核验来源和最新整行，运行中切节点/改名不能被旧请求覆盖。
            val downloaded = subscriptionClient.download(current.url)
            val plaintext = contentDecoder.decode(downloaded.bytes, current.ageSecretKey)
            require(plaintext.isNotEmpty() && plaintext.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
            configValidator.validate(plaintext)
            val commit: suspend () -> Profile = { fileMutationMutex.withLock {
                withContext(kotlinx.coroutines.NonCancellable) {
                    replaceLocalProfileFile(profileFile(id), plaintext, persist = { commit ->
                        database.withTransaction {
                            val latest = requireNotNull(profileDao.getById(id)) { "配置不存在" }.toDomain()
                            require(latest.type == ProfileType.URL && latest.url == current.url && latest.ageSecretKey == current.ageSecretKey) { "订阅设置已变化，请重新更新" }
                            val updated = latest.copy(
                                label = latest.label.ifBlank { downloaded.suggestedLabel.orEmpty() },
                                lastUpdateAt = System.currentTimeMillis(), lastUpdateError = null,
                                subscriptionInfo = downloaded.subscriptionInfo.takeIf { it != SubscriptionInfo() },
                            )
                            // 作者 long：先替换文件再写Room；数据库拒绝时既有文件工具会恢复原字节和mtime。
                            commit()
                            profileDao.update(updated.toEntity())
                            if (automatic && automaticCommitter != null) {
                                // 作者 long：待应用标记与正文元数据一起提交；进程在核心应用前退出，下一次只重试应用而不重复下载。
                                preferenceDao.put(AppPreferenceEntity(automaticApplyKey(id), automaticApplyVersion(updated, plaintext), System.currentTimeMillis()))
                            }
                            updated
                        }
                    }, expected = original)
                }
            } }
            val result = if (automatic && automaticCommitter != null) automaticCommitter.commit(id, commit) else commit()
            if (automatic && automaticCommitter != null) preferenceDao.delete(automaticApplyKey(id))
            result
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException) throw error
            recordRemoteUpdateFailure(id, current.url, current.ageSecretKey, current.lastUpdateAt)
            throw error
        }
    }

    override suspend fun updateDueProfiles(nowMillis: Long): AutoUpdateResult = withContext(Dispatchers.IO) {
        automaticUpdateMutex.withLock {
            val failures = mutableListOf<AutoUpdateFailure>()
            val applyFailures = mutableListOf<AutoUpdateFailure>()
            val downloaded = mutableListOf<Long>()
            val applied = mutableListOf<Long>()
            val pending = if (automaticCommitter == null) emptyList() else preferenceDao.getByPrefix(AUTOMATIC_APPLY_PREFIX)
            val pendingIds = mutableSetOf<Long>()
            for (marker in pending) {
                val id = marker.key.removePrefix(AUTOMATIC_APPLY_PREFIX).toLongOrNull() ?: continue
                val profile = profileDao.getById(id)?.toDomain()
                if (profile == null) { preferenceDao.delete(automaticApplyKey(id)); continue }
                // 作者 long：改源、换密钥或另一次同步后，旧正文的待应用任务失效，不能永久挡住修正来源的下载。
                val version = runCatching { automaticApplyVersion(profile, profileFile(id).readBytes()) }.getOrNull()
                if (marker.value != version) { preferenceDao.delete(marker.key); continue }
                pendingIds += id
                try {
                    automaticCommitter!!.commit(id) { requireNotNull(profileDao.getById(id)) { "配置已删除" }.toDomain() }
                    preferenceDao.delete(automaticApplyKey(id))
                    applied += id
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { applyFailures += AutoUpdateFailure(id, profile.realLabel, "订阅已保存，当前配置应用失败，将重试应用") }
            }
            val candidates = profileDao.getAll().map(ProfileEntity::toDomain).filter { it.type == ProfileType.URL && it.autoUpdate }
            val checks = candidates.map { AutoUpdateCheck(it.id, it.lastUpdateAt, it.autoUpdateIntervalMillis.coerceAtLeast(MIN_AUTO_UPDATE_INTERVAL_MILLIS), isAutoUpdateDue(it, nowMillis)) }
            // 作者 long：已保存但应用失败的资源先走上面的恢复链，本轮不能再次联网覆盖仍待应用的正文。
            val due = candidates.filter { it.id !in pendingIds && isAutoUpdateDue(it, nowMillis) }
            for (profile in due) {
                try {
                    updateRemoteProfile(profile.id, automatic = true)
                    downloaded += profile.id
                    if (automaticCommitter != null) applied += profile.id
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException) {
                    downloaded += profile.id
                    applyFailures += AutoUpdateFailure(profile.id, profile.realLabel, "订阅已保存，当前配置应用失败，将重试应用")
                } catch (_: Exception) {
                    // 作者 long：下载错误可能包含带凭据的地址，通知只给出可重试阶段，具体来源不进入日志。
                    failures += AutoUpdateFailure(profile.id, profile.realLabel, "订阅下载或保存失败，将重试更新")
                }
            }
            AutoUpdateResult(due.size, downloaded.size, failures, applyFailures, downloaded, applied, checks)
        }
    }

    override suspend fun referencesProfile(profileId: Long, resourceId: Long): Boolean = withContext(Dispatchers.IO) {
        val row = profileDao.getById(profileId) ?: return@withContext false
        if (row.type != ProfileType.CUSTOM) return@withContext false
        ProfileProxyResources.referencesProfile(profileFile(profileId).readText(), resourceId)
    }

    private fun automaticApplyKey(id: Long) = "$AUTOMATIC_APPLY_PREFIX$id"

    private fun automaticApplyVersion(profile: Profile, bytes: ByteArray): String = profileDocumentDigest(
        "${profile.url}\u0000${profile.ageSecretKey}\u0000${profile.lastUpdateAt}\u0000${profileDocumentDigest(bytes)}".toByteArray())

    /** 作者 long：需要冻结自动更新的批处理先等待已受理下载结束，避免只改开关却仍有旧请求提交正文。 */
    suspend fun <T> withAutomaticUpdateBarrier(action: suspend () -> T): T = automaticUpdateMutex.withLock { action() }

    override suspend fun proxyResourceUsers(id: Long): List<String> = proxyResourceUsage()[id].orEmpty()

    override suspend fun proxyResourceUsage(): Map<Long, List<String>> = withContext(Dispatchers.IO) {
        val usage = linkedMapOf<Long, MutableList<String>>()
        // 作者 long：配置列表一次扫描引用关系，不能为每张订阅卡重复解析全部自定义配置。
        profileDao.getAll().filter { it.type == ProfileType.CUSTOM }.forEach { row ->
            val ids = runCatching {
                val root = ProxyGroupDrafts.root(profileFile(row.id).readText())
                val used = ProfileProxyResources.referenced(root)
                root.getAsJsonObject("proxy-providers")?.entrySet()?.filter { it.key in used }
                    ?.mapNotNull { ProfileProxyResources.idOf(it.value) }?.toSet().orEmpty()
            }.getOrDefault(emptySet())
            ids.forEach { id -> usage.getOrPut(id) { mutableListOf() }.add(row.toDomain().realLabel) }
        }
        usage
    }

    override suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
        // 作者 long：删除与本地文件保存共用锁，避免失败保存恢复旧文件时把已删除的配置正文重新留在磁盘。
        fileMutationMutex.withLock {
            val profile = profileDao.getById(id) ?: return@withLock
            var fallback = mutableCurrentProfileId.value
            database.withTransaction {
                profileDao.delete(profile)
                if (mutableCurrentProfileId.value == id) {
                    fallback = profileDao.getAll().firstOrNull()?.id
                    persistCurrentProfile(fallback)
                }
            }
            // 作者 long：先提交Room再清理正文；事务失败必须保留旧源和当前选择，供管理器恢复原运行配置。
            mutableCurrentProfileId.value = fallback
            profileFile(id).delete()
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

    private suspend fun toActiveProfile(profile: Profile): ActiveProfile {
        val extension = ProfileProviderOverrides.fromJsonString(profile.overridesJson).extension
        // 作者 long：旧绑定在引入模式前始终执行，未明确选择新模式的配置保留旧行为；CUSTOM 的既有绑定继续兼容。
        val useScript = profile.type == ProfileType.CUSTOM || profile.extendType == "script" || !extension.configured && profile.scriptId != null
        val standard = profile.type != ProfileType.CUSTOM && !useScript
        val sourcePath = if (profile.type == ProfileType.CUSTOM) {
            fileMutationMutex.withLock {
                val source = profileFile(profile.id).readText()
                val resolved = LocalProxies.project(resolveProxyResources(resources?.resolve(source) ?: source), localProxies())
                if (resolved == source) profileFile(profile.id).path else {
                    // 作者 long：投影按内容变动才替换，后台刷新不能因每次读取重写 mtime 而重复加载核心。
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(resolved.toByteArray()).joinToString("") { "%02x".format(it) }
                    val projection = File(appContext.filesDir, "resolved-profiles/${profile.id}-$digest.yaml")
                    if (!projection.isFile || projection.readText() != resolved) {
                        projection.parentFile?.mkdirs()
                        val temporary = File(projection.parentFile, ".${profile.id}.tmp")
                        temporary.writeText(resolved)
                        check(temporary.renameTo(projection)) { "无法写入资源投影配置" }
                    }
                    projection.path
                }
            }
        } else profileFile(profile.id).path
        return ActiveProfile(
            id = profile.id,
            label = profile.realLabel,
            filePath = sourcePath,
            selectedMap = decodeSelectedMap(profile.selectedMapJson),
            overridesJson = profile.overridesJson,
            scriptPath = profile.scriptId?.takeIf { useScript }?.let { scriptId ->
                requireNotNull(database.scripts().getById(scriptId)) { "绑定脚本不存在" }
                scriptFile(scriptId).also { file ->
                    require(file.isFile) { "绑定脚本正文不存在" }
                }.path
            },
            deferExtensionApplication = profile.type != ProfileType.CUSTOM,
            usesGlobalDns = profile.type != ProfileType.CUSTOM,
            standardExtension = if (standard) StandardProfileExtension(extension.rules + globalExtensionRules().filterNot { it.id in extension.disabledGlobalRuleIds }, profile.matchTarget) else null,
        )
    }

    private suspend fun resolveProxyResources(source: String): String {
        if (!source.contains(PROFILE_RESOURCE_PREFIX)) return source
        val root = ProxyGroupDrafts.root(source)
        val used = ProfileProxyResources.referenced(root)
        val providers = root.getAsJsonObject("proxy-providers") ?: return source
        for ((alias, definition) in providers.entrySet().toList()) {
            if (!ProfileProxyResources.isReference(definition)) continue
            // 作者 long：include-all-providers 只包含配置显式引用的资源，未使用绑定不能引发额外加载或缺失错误。
            if (alias !in used) { providers.remove(alias); continue }
            val resourceId = requireNotNull(ProfileProxyResources.idOf(definition)) { "代理资源标识无效：$alias，请重新选择资源" }
            val resource = profileDao.getById(resourceId)
            require(resource != null && resource.type != ProfileType.CUSTOM) { "代理资源缺失：$alias，请重新选择资源" }
            val file = profileFile(resourceId)
            require(file.isFile && file.length() in 1..MAX_PROFILE_FILE_BYTES.toLong()) { "代理资源缓存缺失，请同步或重新导入" }
            val bytes = file.readBytes()
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val snapshot = File(appContext.filesDir, "mihomo/providers/profiles/$resourceId-$digest.yaml")
            // 作者 long：Mihomo 读取整个订阅缓存，和 Flutter 一致，不执行该资源配置绑定的脚本或 DNS 扩展。
            if (!snapshot.isFile) {
                snapshot.parentFile?.mkdirs()
                val temporary = File(snapshot.parentFile, ".$resourceId.tmp")
                temporary.writeBytes(bytes)
                check(temporary.renameTo(snapshot)) { "无法写入代理资源投影" }
            }
            definition.asJsonObject.addProperty("path", snapshot.path)
        }
        return com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(root.toString())
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

    private suspend fun recordRemoteUpdateFailure(id: Long, sourceUrl: String, sourceKey: String, updatedAt: Long?) {
        runCatching {
            database.withTransaction {
                val latest = profileDao.getById(id)?.toDomain() ?: return@withTransaction
                // 作者 long：旧来源的失败不污染用户刚改好的订阅，错误文本不持久化URL/核心原文。
                if (latest.url == sourceUrl && latest.ageSecretKey == sourceKey && latest.lastUpdateAt == updatedAt) {
                    profileDao.update(latest.copy(lastUpdateError = "订阅更新失败，请检查网络、订阅地址或解密密钥").toEntity())
                }
            }
        }
    }

    private fun newId(): Long = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    private companion object {
        const val AUTOMATIC_APPLY_PREFIX = "subscription-pending-apply:"
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
