package com.lonnnnnng.flclash.data

import androidx.room.withTransaction
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BackupRestoreScope {
    PROFILES_ONLY,
    ALL_DATA,
}

enum class BackupRestoreStrategy {
    COMPATIBLE,
    OVERRIDE,
}

private data class BackupManifest(
    val version: Int,
    val createdAtMillis: Long,
    val includesScripts: Boolean,
    val includesCurrentProfile: Boolean,
)

private data class ParsedBackup(
    val manifest: BackupManifest,
    val profiles: List<ProfileEntity>,
    val scripts: List<ScriptEntity>,
    val currentProfileId: Long?,
    val files: Map<String, ByteArray>,
    val globalRules: List<ExtensionRule> = emptyList(),
    val resources: List<RuleResource> = emptyList(),
    val localProxies: List<LocalProxy> = emptyList(),
    val resourcePreferences: ResourcePreferences? = null,
    val applicationSettings: ApplicationSettingsBackup? = null,
)

/**
 * 作者 long：备份只携带 Kotlin 应用可以重新生成的用户数据，不把 Mihomo 临时目录、诊断历史或
 * “进程重建后自动恢复核心”的运行意图写进归档，避免恢复备份时意外启动旧 VPN 状态。
 */
class BackupRepository(
    private val database: KClashDatabase,
    private val filesRoot: File,
    private val stagingRoot: File = File(filesRoot.parentFile ?: filesRoot, "kclash-backup-staging"),
    private val geoSettingsStore: com.lonnnnnng.flclash.service.GeoResourceSettingsStore? = null,
    private val filterValidator: ProxyGroupFilterValidator? = null,
    private val applicationSettingsStore: ApplicationSettingsBackupStore = ApplicationSettingsBackupStore(),
    private val runtimeManager: com.lonnnnnng.flclash.service.CoreRuntimeManager? = null,
) {
    private val operationLock = Mutex()
    private val gson = Gson()
    private val profileDao = database.profiles()
    private val scriptDao = database.scripts()
    private val preferenceDao = database.appPreferences()

    suspend fun createArchive(scope: BackupRestoreScope = BackupRestoreScope.ALL_DATA): ByteArray =
        operationLock.withLock { withContext(Dispatchers.IO) {
            val profiles = profileDao.getAll().map { entity ->
                if (scope == BackupRestoreScope.PROFILES_ONLY) {
                    // 作者 long：仅恢复配置文件时不携带脚本绑定，避免归档不含脚本却留下悬挂 scriptId。
                    entity.copy(scriptId = null)
                } else {
                    entity
                }
            }
            val scripts = if (scope == BackupRestoreScope.ALL_DATA) scriptDao.getAll() else emptyList()
            val currentProfileId = if (scope == BackupRestoreScope.ALL_DATA) {
                preferenceDao.get(CURRENT_PROFILE_KEY)?.toLongOrNull()
            } else {
                null
            }
            val globalRules = if (scope == BackupRestoreScope.ALL_DATA) ExtensionRules.decode(preferenceDao.get(ExtensionRules.PREFERENCE_KEY)) else emptyList()
            val allResources = RuleResources.decode(preferenceDao.get(RULE_RESOURCES_KEY))
            val resources = if (scope == BackupRestoreScope.ALL_DATA) allResources else {
                val ids = profiles.flatMap { profile ->
                    Regex("kclash-resource:([a-zA-Z0-9-]{1,80})").findAll(profileFile(profile.id).readText()).map { it.groupValues[1] }.toList()
                }.toSet()
                allResources.filter { it.id in ids }
            }
            val allLocalProxies = LocalProxies.decode(preferenceDao.get(LocalProxies.KEY))
            // 作者 long：全量备份原样携带目录；仅配置且确有全局节点时才解析依赖，旧正文仍可先备份再修复。
            val localProxies = if (scope == BackupRestoreScope.ALL_DATA || allLocalProxies.isEmpty()) allLocalProxies else {
                val referencedProxies = profiles.filter { it.type == ProfileType.CUSTOM }.flatMap { LocalProxies.referenced(profileFile(it.id).readText()) }.toSet()
                allLocalProxies.filter { it.name in referencedProxies }
            }
            val manifest = BackupManifest(
                version = CURRENT_VERSION,
                createdAtMillis = System.currentTimeMillis(),
                includesScripts = scope == BackupRestoreScope.ALL_DATA,
                includesCurrentProfile = scope == BackupRestoreScope.ALL_DATA && currentProfileId != null,
            )
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    putText(zip, MANIFEST_ENTRY, gson.toJson(manifest))
                    putText(zip, PROFILES_ENTRY, gson.toJson(profiles))
                    putText(zip, LOCAL_PROXIES_ENTRY, LocalProxies.encode(localProxies))
                    putText(zip, RESOURCE_METADATA_ENTRY, RuleResources.encode(resources))
                    resources.forEach { resource -> resourceFileNames(resource).forEach { putFile(zip, resourceEntry(it), resourceFile(it)) } }
                    if (manifest.includesScripts) {
                        putText(zip, APPLICATION_SETTINGS_ENTRY, ApplicationSettingsBackupCodec.encode(applicationSettingsStore.read()))
                        putText(zip, RESOURCE_PREFERENCES_ENTRY, ResourcePreferencesCodec.encode(ResourcePreferences(
                            IconSetCodec.decode(preferenceDao.get(ICON_SETS_KEY)),
                            FilterPresets.decode(preferenceDao.get(FILTER_PRESETS_KEY)),
                            geoSettingsStore?.read() ?: com.lonnnnnng.flclash.service.GeoResourceSettings(),
                        )))
                        putText(zip, SCRIPTS_ENTRY, gson.toJson(scripts))
                        putText(zip, GLOBAL_RULES_ENTRY, ExtensionRules.toJson(globalRules).toString())
                        currentProfileId?.let { putText(zip, CURRENT_PROFILE_ENTRY, it.toString()) }
                    }
                    profiles.sortedBy { it.id }.forEach { profile ->
                        putFile(zip, profileEntry(profile.id), profileFile(profile.id))
                    }
                    scripts.sortedBy { it.id }.forEach { script ->
                        putFile(zip, scriptEntry(script.id), scriptFile(script.id))
                    }
                }
                output.toByteArray()
            }
        } }

    suspend fun restore(
        archive: ByteArray,
        scope: BackupRestoreScope,
        strategy: BackupRestoreStrategy,
    ) = operationLock.withLock {
        if (runtimeManager == null) restoreStopped(archive, scope, strategy)
        else runtimeManager.withStoppedDataRestore { restoreStopped(archive, scope, strategy) }
    }

    private suspend fun restoreStopped(archive: ByteArray, scope: BackupRestoreScope, strategy: BackupRestoreStrategy) = com.lonnnnnng.flclash.common.auditedStateIo("backup.restore") withContext@ {
        require(archive.isNotEmpty()) { "备份文件为空" }
        val read = parseArchive(archive)
        read.resourcePreferences?.filters?.takeIf { it.isNotEmpty() }?.let { presets ->
            // 作者 long：归档的结构和核心正则语法都在写文件前检查，恢复失败不能留下半套全局目录。
            filterValidator?.let { validator ->
                val errors = validator.validate(presets.map { it.regex })
                require(errors.size == presets.size && errors.all(String::isEmpty)) { "备份包含无效过滤表达式" }
            }
        }
        val previousLocal = LocalProxies.decode(preferenceDao.get(LocalProxies.KEY))
        val existingLocal = if (scope == BackupRestoreScope.ALL_DATA && strategy == BackupRestoreStrategy.OVERRIDE) emptyList() else previousLocal
        // 作者 long：同 ID 的节点在备份后可能已改名；兼容恢复为旧名分配新 ID，保护归档外配置仍使用的现有名称。
        val incomingLocal = read.localProxies.map { row ->
            if (existingLocal.any { it.id == row.id && it.name != row.name }) row.copy(id = UUID.randomUUID().toString()) else row
        }
        val incomingIds = incomingLocal.map { it.id }.toSet()
        val mergedLocal = existingLocal.filterNot { it.id in incomingIds }.toMutableList()
        var restored = read
        // 作者 long：旧归档没有全局节点，不需要解析正文；损坏或旧格式配置仍应能原样恢复后再修复。
        val retainedProfiles = if (strategy == BackupRestoreStrategy.COMPATIBLE) profileDao.getAll().filter { old -> read.profiles.none { it.id == old.id } } else emptyList()
        val groupNames = if (incomingLocal.isEmpty()) emptySet() else (
            read.profiles.filter { it.type == ProfileType.CUSTOM }
                .flatMap { entity -> ProxyGroupDrafts.read(read.files.getValue(profileEntry(entity.id)).toString(Charsets.UTF_8)).map { it.name } } +
                retainedProfiles.filter { it.type == ProfileType.CUSTOM }.flatMap { ProxyGroupDrafts.read(profileFile(it.id).readText()).map { it.name } }
            ).toSet()
        incomingLocal.forEach { row ->
            var name = row.name; var suffix = 2
            while (name in LocalProxies.reserved || name in groupNames || mergedLocal.any { it.name == name } || name != row.name && read.localProxies.any { it.name == name }) name = "${row.name} (${suffix++})"
            if (name != row.name) {
                val files = restored.files.toMutableMap()
                val profiles = restored.profiles.map { entity ->
                    if (entity.type != ProfileType.CUSTOM) entity else {
                        files[profileEntry(entity.id)] = LocalProxyReferences.rename(files.getValue(profileEntry(entity.id)).toString(Charsets.UTF_8), row.name, name).toByteArray()
                        LocalProxyReferences.metadata(entity.toDomain(), row.name, name).toEntity()
                    }
                }
                restored = restored.copy(profiles = profiles, files = files)
            }
            mergedLocal += LocalProxies.withValue(row, "name", com.google.gson.JsonPrimitive(name))
        }
        LocalProxies.validate(mergedLocal)
        val parsed = restored
        require(scope != BackupRestoreScope.ALL_DATA || parsed.manifest.includesScripts) {
            "备份不包含脚本和全部数据"
        }
        val staged = File(stagingRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        val profilesToRestore = parsed.profiles.map { profile ->
            if (scope == BackupRestoreScope.PROFILES_ONLY) profile.copy(scriptId = null) else profile
        }
        val rollback = File(staged, "rollback").apply { mkdirs() }
        val previousGeo = geoSettingsStore?.read()
        val previousSettings = applicationSettingsStore.read()
        var settingsWriteAttempted = false
        var geoWriteAttempted = false
        var databaseCommitted = false
        try {
            stageIncomingFiles(parsed, scope, staged)
            val incomingFiles = incomingFileNames(parsed, scope)
            val snapshotNames = snapshotNames(strategy, scope, incomingFiles)
            snapshotExistingFiles(snapshotNames, rollback)
            try {
                copyStagedFiles(incomingFiles, staged)
                withContext(NonCancellable) { database.withTransaction {
                    if (strategy == BackupRestoreStrategy.OVERRIDE) {
                        profileDao.deleteAll()
                        if (scope == BackupRestoreScope.ALL_DATA) scriptDao.deleteAll()
                    }
                    check(LocalProxies.decode(preferenceDao.get(LocalProxies.KEY)) == previousLocal) { "本地代理已变化，请重试恢复" }
                    profilesToRestore.forEach { profileDao.insert(it) }
                    preferenceDao.put(AppPreferenceEntity(LocalProxies.KEY, LocalProxies.encode(mergedLocal), System.currentTimeMillis()))
                    // 作者 long：仅配置备份也携带被引用资源；稳定 ID 保护配置引用，重名只调整全局列表显示名。
                    val oldResources = if (scope == BackupRestoreScope.ALL_DATA && strategy == BackupRestoreStrategy.OVERRIDE) emptyList() else RuleResources.decode(preferenceDao.get(RULE_RESOURCES_KEY))
                    val incomingIds = parsed.resources.map { it.id }.toSet()
                    val mergedResources = oldResources.filterNot { it.id in incomingIds }.toMutableList()
                    parsed.resources.forEach { resource -> mergedResources += resource.copy(label = RuleResources.uniqueLabel(resource.label, mergedResources)) }
                    preferenceDao.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(mergedResources), System.currentTimeMillis()))
                    if (scope == BackupRestoreScope.ALL_DATA) {
                        parsed.resourcePreferences?.let { incoming ->
                            val settings = if (strategy == BackupRestoreStrategy.OVERRIDE) incoming else ResourcePreferencesCodec.merge(
                                ResourcePreferences(IconSetCodec.decode(preferenceDao.get(ICON_SETS_KEY)), FilterPresets.decode(preferenceDao.get(FILTER_PRESETS_KEY)), previousGeo ?: incoming.geo), incoming)
                            preferenceDao.put(AppPreferenceEntity(ICON_SETS_KEY, IconSetCodec.encode(settings.icons), System.currentTimeMillis()))
                            preferenceDao.put(AppPreferenceEntity(FILTER_PRESETS_KEY, FilterPresets.encode(settings.filters), System.currentTimeMillis()))
                        }
                        parsed.scripts.forEach { scriptDao.insert(it) }
                        // 作者 long：规则 ID 随完整备份保留，兼容恢复只合并同 ID，避免各配置的禁用集合错位。
                        val global = if (strategy == BackupRestoreStrategy.OVERRIDE) parsed.globalRules else {
                            val incomingIds = parsed.globalRules.map { it.id }.toSet()
                            ExtensionRules.decode(preferenceDao.get(ExtensionRules.PREFERENCE_KEY)).filterNot { it.id in incomingIds } + parsed.globalRules
                        }
                        ExtensionRules.validate(global)
                        preferenceDao.put(AppPreferenceEntity(ExtensionRules.PREFERENCE_KEY, ExtensionRules.toJson(global).toString(), System.currentTimeMillis()))
                        if (strategy == BackupRestoreStrategy.OVERRIDE && parsed.currentProfileId == null) {
                            // 作者 long：归档没有当前选择时，全量覆盖也应恢复空选择，不能残留指向已删除配置的旧 ID。
                            preferenceDao.delete(CURRENT_PROFILE_KEY)
                        }
                        parsed.currentProfileId?.let { currentId ->
                            preferenceDao.put(
                                AppPreferenceEntity(
                                    key = CURRENT_PROFILE_KEY,
                                    value = currentId.toString(),
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                        parsed.resourcePreferences?.let { incoming ->
                            // 作者 long：SharedPreferences 不属于 Room；最后写入并在事务失败时补偿，不在此处获取核心启停锁。
                            check(geoSettingsStore?.read() == previousGeo) { "Geo 设置已变化，请重试恢复" }
                            geoWriteAttempted = geoSettingsStore != null
                            geoSettingsStore?.write(incoming.geo)
                        }
                        parsed.applicationSettings?.let { incoming ->
                            check(applicationSettingsStore.read() == previousSettings) { "应用设置已变化，请重试恢复" }
                            settingsWriteAttempted = true
                            applicationSettingsStore.write(incoming)
                        }
                    }
                }; databaseCommitted = true }
                if (strategy == BackupRestoreStrategy.OVERRIDE) {
                    deleteOrphanFiles(scope, incomingFiles)
                }
            } catch (error: Throwable) {
                if (!databaseCommitted) withContext(NonCancellable) {
                    runCatching { restoreSnapshots(snapshotNames, rollback) }.exceptionOrNull()?.let(error::addSuppressed)
                    if (geoWriteAttempted && previousGeo != null) runCatching { geoSettingsStore.write(previousGeo) }.exceptionOrNull()?.let(error::addSuppressed)
                    if (settingsWriteAttempted) runCatching { applicationSettingsStore.compensate(previousSettings) }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            }
        } finally {
            staged.deleteRecursively()
        }
    }

    private fun parseArchive(bytes: ByteArray): ParsedBackup {
        require(bytes.size <= 64 * 1024 * 1024) { "备份文件超过 64 MiB 限制" }
        val entries = linkedMapOf<String, ByteArray>()
        var unpackedSize = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                validateEntryPath(entry.name)
                require(!entry.isDirectory) { "备份包含目录条目" }
                val body = readLimited(input)
                unpackedSize += body.size
                require(unpackedSize <= 128L * 1024 * 1024 && entries.size < 4096) { "备份解压后超过容量限制" }
                require(entries.put(entry.name, body) == null) {
                    "备份包含重复条目：${entry.name}"
                }
            }
        }
        val manifest = entries[MANIFEST_ENTRY]?.let { json ->
            runCatching { gson.fromJson(json.toString(Charsets.UTF_8), BackupManifest::class.java) }
                .getOrElse { throw IllegalArgumentException("备份清单无效", it) }
        } ?: throw IllegalArgumentException("备份缺少清单")
        require(manifest.version in 1..CURRENT_VERSION) {
            if (manifest.version > CURRENT_VERSION) "备份版本过高" else "备份版本过旧"
        }
        val profiles = decodeList<ProfileEntity>(entries[PROFILES_ENTRY], "Profiles")
        val scripts = if (manifest.includesScripts) {
            decodeList<ScriptEntity>(entries[SCRIPTS_ENTRY], "Scripts")
        } else {
            emptyList()
        }
        val currentProfileId = if (manifest.includesCurrentProfile) {
            entries[CURRENT_PROFILE_ENTRY]?.toString(Charsets.UTF_8)?.trim()?.toLongOrNull()
                ?: throw IllegalArgumentException("备份当前配置选择无效")
        } else {
            null
        }
        validateRows(profiles, scripts, entries, manifest)
        val resources = RuleResources.decode(entries[RESOURCE_METADATA_ENTRY]?.toString(Charsets.UTF_8))
        require(manifest.version < 3 || entries.containsKey(RESOURCE_METADATA_ENTRY)) { "备份缺少资源目录" }
        resources.forEach { resource -> resourceFileNames(resource).forEach { require(entries.containsKey(resourceEntry(it))) { "备份缺少规则资源正文或 MRS 缓存" } } }
        require(manifest.version < 4 || entries.containsKey(LOCAL_PROXIES_ENTRY)) { "备份缺少本地代理目录" }
        val localProxies = LocalProxies.decode(entries[LOCAL_PROXIES_ENTRY]?.toString(Charsets.UTF_8))
        LocalProxies.validate(localProxies)
        require(manifest.version < 5 || !manifest.includesScripts || entries.containsKey(RESOURCE_PREFERENCES_ENTRY)) { "备份缺少资源设置" }
        val resourcePreferences = entries[RESOURCE_PREFERENCES_ENTRY]?.toString(Charsets.UTF_8)?.let(ResourcePreferencesCodec::decode)
        require(manifest.version < 6 || !manifest.includesScripts || entries.containsKey(APPLICATION_SETTINGS_ENTRY)) { "备份缺少应用设置" }
        val applicationSettings = entries[APPLICATION_SETTINGS_ENTRY]?.toString(Charsets.UTF_8)?.let(ApplicationSettingsBackupCodec::decode)
        return ParsedBackup(manifest, profiles, scripts, currentProfileId, entries,
            ExtensionRules.decode(entries[GLOBAL_RULES_ENTRY]?.toString(Charsets.UTF_8)), resources, localProxies, resourcePreferences, applicationSettings)
    }

    private fun validateRows(
        profiles: List<ProfileEntity>,
        scripts: List<ScriptEntity>,
        entries: Map<String, ByteArray>,
        manifest: BackupManifest,
    ) {
        require(profiles.map(ProfileEntity::id).distinct().size == profiles.size) { "备份包含重复 Profile" }
        require(scripts.map(ScriptEntity::id).distinct().size == scripts.size) { "备份包含重复脚本" }
        profiles.forEach { profile ->
            require(entries.containsKey(profileEntry(profile.id))) { "备份缺少 Profile 文件：${profile.id}" }
            if (manifest.includesScripts && profile.scriptId != null) {
                require(scripts.any { it.id == profile.scriptId }) { "Profile 脚本绑定不存在：${profile.id}" }
            }
        }
        scripts.forEach { script ->
            require(entries.containsKey(scriptEntry(script.id))) { "备份缺少脚本正文：${script.id}" }
        }
    }

    private fun stageIncomingFiles(parsed: ParsedBackup, scope: BackupRestoreScope, staged: File) {
        parsed.resources.forEach { resource -> resourceFileNames(resource).forEach { writeStaged(staged, resourceEntry(it), parsed.files.getValue(resourceEntry(it))) } }
        parsed.profiles.forEach { profile ->
            writeStaged(staged, profileEntry(profile.id), parsed.files.getValue(profileEntry(profile.id)))
        }
        if (scope == BackupRestoreScope.ALL_DATA) {
            parsed.scripts.forEach { script ->
                writeStaged(staged, scriptEntry(script.id), parsed.files.getValue(scriptEntry(script.id)))
            }
        }
    }

    private fun incomingFileNames(parsed: ParsedBackup, scope: BackupRestoreScope): Set<String> =
        buildSet {
            parsed.resources.forEach { resource -> resourceFileNames(resource).forEach { add(resourceEntry(it)) } }
            parsed.profiles.forEach { add(profileEntry(it.id)) }
            if (scope == BackupRestoreScope.ALL_DATA) parsed.scripts.forEach { add(scriptEntry(it.id)) }
        }

    private fun snapshotNames(
        strategy: BackupRestoreStrategy,
        scope: BackupRestoreScope,
        incomingFiles: Set<String>,
    ): Set<String> {
        if (strategy != BackupRestoreStrategy.OVERRIDE) return incomingFiles
        val names = incomingFiles.toMutableSet()
        if (scope == BackupRestoreScope.PROFILES_ONLY) {
            profileDirectory().listFiles()
                ?.filter { it.extension == "yaml" }
                ?.forEach { file -> file.nameWithoutExtension.toLongOrNull()?.let { names += profileEntry(it) } }
        } else {
            profileDirectory().listFiles()?.filter { it.extension == "yaml" }?.forEach { file ->
                file.nameWithoutExtension.toLongOrNull()?.let { names += profileEntry(it) }
            }
            scriptDirectory().listFiles()?.filter { it.extension == "js" }?.forEach { file ->
                file.nameWithoutExtension.toLongOrNull()?.let { names += scriptEntry(it) }
            }
        }
        return names
    }

    private fun snapshotExistingFiles(names: Set<String>, rollback: File) {
        names.forEach { name ->
            val target = resolveAppFile(name)
            if (target.isFile) {
                val backup = File(rollback, name)
                backup.parentFile?.mkdirs()
                target.copyTo(backup, overwrite = true)
            }
        }
    }

    private fun copyStagedFiles(names: Set<String>, staged: File) {
        names.forEach { name ->
            val source = File(staged, name)
            require(source.isFile) { "备份暂存文件缺失：$name" }
            val target = resolveAppFile(name)
            target.parentFile?.mkdirs()
            com.lonnnnnng.flclash.common.StateWriteAudit.fileMutation(target, "backup.restore.copy") { source.copyTo(target, overwrite = true) }
        }
    }

    private fun restoreSnapshots(names: Set<String>, rollback: File) {
        names.forEach { name ->
            val snapshot = File(rollback, name)
            val target = resolveAppFile(name)
            if (snapshot.isFile) {
                target.parentFile?.mkdirs()
                com.lonnnnnng.flclash.common.StateWriteAudit.fileMutation(target, "backup.restore.rollback") { snapshot.copyTo(target, overwrite = true) }
            } else {
                com.lonnnnnng.flclash.common.StateWriteAudit.fileMutation(target, "backup.restore.rollbackNew") { target.delete() }
            }
        }
    }

    private fun deleteOrphanFiles(scope: BackupRestoreScope, incomingFiles: Set<String>) {
        val directories = if (scope == BackupRestoreScope.ALL_DATA) {
            listOf(profileDirectory(), scriptDirectory())
        } else {
            listOf(profileDirectory())
        }
        directories.forEach { directory ->
            directory.listFiles()?.filter { it.extension == "yaml" || it.extension == "js" }?.forEach { file ->
                val numeric = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
                val relative = if (file.extension == "yaml") profileEntry(numeric) else scriptEntry(numeric)
                if (relative !in incomingFiles) com.lonnnnnng.flclash.common.StateWriteAudit.fileMutation(file, "backup.restore.orphan") { file.delete() }
            }
        }
    }

    private fun writeStaged(staged: File, name: String, bytes: ByteArray) {
        val target = File(staged, name)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun resolveAppFile(name: String): File = when {
        name.startsWith("$PROFILES_DIRECTORY/") -> File(profileDirectory(), name.substringAfter('/'))
        name.startsWith("$SCRIPTS_DIRECTORY/") -> File(scriptDirectory(), name.substringAfter('/'))
        name.startsWith("rule_resources/") -> resourceFile(name.substringAfter('/'))
        else -> throw IllegalArgumentException("备份文件路径不受支持")
    }

    private fun resourceFileNames(resource: RuleResource): List<String> = if (resource.compiled) listOf(resource.fileName, resource.coreFileName) else listOf(resource.fileName)

    private fun resourceFile(name: String): File = File(filesRoot, "mihomo/providers/rules/$name")

    private fun profileFile(id: Long): File = File(profileDirectory(), "$id.yaml")

    private fun scriptFile(id: Long): File = File(scriptDirectory(), "$id.js")

    private fun profileDirectory(): File = File(filesRoot, PROFILES_DIRECTORY)

    private fun scriptDirectory(): File = File(filesRoot, SCRIPTS_DIRECTORY)

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        require(file.isFile) { "应用数据文件缺失：$name" }
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun validateEntryPath(name: String) {
        require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\')) {
            "备份包含非法路径"
        }
        val segments = name.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." }) { "备份包含路径穿越" }
        require(
            name == MANIFEST_ENTRY ||
                name == PROFILES_ENTRY ||
                name == SCRIPTS_ENTRY ||
                name == CURRENT_PROFILE_ENTRY ||
                name == GLOBAL_RULES_ENTRY ||
                name == RESOURCE_METADATA_ENTRY ||
                name == LOCAL_PROXIES_ENTRY ||
                name == RESOURCE_PREFERENCES_ENTRY ||
                name == APPLICATION_SETTINGS_ENTRY ||
                name.matches(Regex("rule_resources/[a-zA-Z0-9-]{1,80}\\.rules(?:\\.mrs)?")) ||
                name.matches(Regex("(?:profiles|scripts)/[0-9]+\\.(?:yaml|js)")),
        ) { "备份包含未知条目" }
    }

    private fun readLimited(input: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_ENTRY_BYTES) { "备份条目过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private inline fun <reified T> decodeList(bytes: ByteArray?, label: String): List<T> {
        require(bytes != null) { "备份缺少$label 数据" }
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(bytes.toString(Charsets.UTF_8), type).orEmpty() }
            .getOrElse { throw IllegalArgumentException("备份$label 数据无效", it) }
    }

    private companion object {
        const val CURRENT_VERSION = 6
        const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L
        const val CURRENT_PROFILE_KEY = "current_profile_id"
        const val MANIFEST_ENTRY = "manifest.json"
        const val PROFILES_ENTRY = "profiles.json"
        const val SCRIPTS_ENTRY = "scripts.json"
        const val LOCAL_PROXIES_ENTRY = "local_proxies.json"
        const val RESOURCE_PREFERENCES_ENTRY = "resource_preferences.json"
        const val APPLICATION_SETTINGS_ENTRY = "application_settings.json"
        const val RESOURCE_METADATA_ENTRY = "rule_resources.json"
        fun resourceEntry(name: String): String = "rule_resources/$name"
        const val GLOBAL_RULES_ENTRY = "global_extension_rules.json"
        const val CURRENT_PROFILE_ENTRY = "current_profile_id.txt"
        const val PROFILES_DIRECTORY = "profiles"
        const val SCRIPTS_DIRECTORY = "scripts"

        fun profileEntry(id: Long): String = "$PROFILES_DIRECTORY/$id.yaml"
        fun scriptEntry(id: Long): String = "$SCRIPTS_DIRECTORY/$id.js"
    }
}

/** 作者 long：系统文件提供方可能没有声明长度，边读边限量，避免恢复前就把超大归档全部读进内存。 */
internal fun readBackupArchive(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= 64L * 1024 * 1024) { "备份文件超过 64 MiB 限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
