package com.lonnnnnng.flclash.data

import androidx.room.withTransaction
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
import kotlinx.coroutines.withContext

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
)

/**
 * 作者 long：备份只携带 Kotlin 应用可以重新生成的用户数据，不把 Mihomo 临时目录、诊断历史或
 * “进程重建后自动恢复核心”的运行意图写进归档，避免恢复备份时意外启动旧 VPN 状态。
 */
class BackupRepository(
    private val database: KClashDatabase,
    private val filesRoot: File,
    private val stagingRoot: File = File(filesRoot.parentFile ?: filesRoot, "kclash-backup-staging"),
) {
    private val gson = Gson()
    private val profileDao = database.profiles()
    private val scriptDao = database.scripts()
    private val preferenceDao = database.appPreferences()

    suspend fun createArchive(scope: BackupRestoreScope = BackupRestoreScope.ALL_DATA): ByteArray =
        withContext(Dispatchers.IO) {
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
            val manifest = BackupManifest(
                version = CURRENT_VERSION,
                createdAtMillis = System.currentTimeMillis(),
                includesScripts = scope == BackupRestoreScope.ALL_DATA,
                includesCurrentProfile = scope == BackupRestoreScope.ALL_DATA,
            )
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    putText(zip, MANIFEST_ENTRY, gson.toJson(manifest))
                    putText(zip, PROFILES_ENTRY, gson.toJson(profiles))
                    if (manifest.includesScripts) {
                        putText(zip, SCRIPTS_ENTRY, gson.toJson(scripts))
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
        }

    suspend fun restore(
        archive: ByteArray,
        scope: BackupRestoreScope,
        strategy: BackupRestoreStrategy,
    ) = withContext(Dispatchers.IO) {
        require(archive.isNotEmpty()) { "备份文件为空" }
        val parsed = parseArchive(archive)
        require(scope != BackupRestoreScope.ALL_DATA || parsed.manifest.includesScripts) {
            "备份不包含脚本和全部数据"
        }
        val staged = File(stagingRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        val profilesToRestore = parsed.profiles.map { profile ->
            if (scope == BackupRestoreScope.PROFILES_ONLY) profile.copy(scriptId = null) else profile
        }
        val rollback = File(staged, "rollback").apply { mkdirs() }
        try {
            stageIncomingFiles(parsed, scope, staged)
            val incomingFiles = incomingFileNames(parsed, scope)
            val snapshotNames = snapshotNames(strategy, scope, incomingFiles)
            snapshotExistingFiles(snapshotNames, rollback)
            try {
                copyStagedFiles(incomingFiles, staged)
                database.withTransaction {
                    if (strategy == BackupRestoreStrategy.OVERRIDE) {
                        profileDao.deleteAll()
                        if (scope == BackupRestoreScope.ALL_DATA) scriptDao.deleteAll()
                    }
                    profilesToRestore.forEach { profileDao.insert(it) }
                    if (scope == BackupRestoreScope.ALL_DATA) {
                        parsed.scripts.forEach { scriptDao.insert(it) }
                        parsed.currentProfileId?.let { currentId ->
                            preferenceDao.put(
                                AppPreferenceEntity(
                                    key = CURRENT_PROFILE_KEY,
                                    value = currentId.toString(),
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                }
                if (strategy == BackupRestoreStrategy.OVERRIDE) {
                    deleteOrphanFiles(scope, incomingFiles)
                }
            } catch (error: Throwable) {
                restoreSnapshots(snapshotNames, rollback)
                throw error
            }
        } finally {
            staged.deleteRecursively()
        }
    }

    private fun parseArchive(bytes: ByteArray): ParsedBackup {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                validateEntryPath(entry.name)
                require(!entry.isDirectory) { "备份包含目录条目" }
                require(entries.put(entry.name, readLimited(input)) == null) {
                    "备份包含重复条目：${entry.name}"
                }
            }
        }
        val manifest = entries[MANIFEST_ENTRY]?.let { json ->
            runCatching { gson.fromJson(json.toString(Charsets.UTF_8), BackupManifest::class.java) }
                .getOrElse { throw IllegalArgumentException("备份清单无效", it) }
        } ?: throw IllegalArgumentException("备份缺少清单")
        require(manifest.version == CURRENT_VERSION) {
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
        return ParsedBackup(manifest, profiles, scripts, currentProfileId, entries)
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
            source.copyTo(target, overwrite = true)
        }
    }

    private fun restoreSnapshots(names: Set<String>, rollback: File) {
        names.forEach { name ->
            val snapshot = File(rollback, name)
            val target = resolveAppFile(name)
            if (snapshot.isFile) {
                target.parentFile?.mkdirs()
                snapshot.copyTo(target, overwrite = true)
            } else {
                target.delete()
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
                if (relative !in incomingFiles) file.delete()
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
        else -> throw IllegalArgumentException("备份文件路径不受支持")
    }

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
        const val CURRENT_VERSION = 1
        const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L
        const val CURRENT_PROFILE_KEY = "current_profile_id"
        const val MANIFEST_ENTRY = "manifest.json"
        const val PROFILES_ENTRY = "profiles.json"
        const val SCRIPTS_ENTRY = "scripts.json"
        const val CURRENT_PROFILE_ENTRY = "current_profile_id.txt"
        const val PROFILES_DIRECTORY = "profiles"
        const val SCRIPTS_DIRECTORY = "scripts"

        fun profileEntry(id: Long): String = "$PROFILES_DIRECTORY/$id.yaml"
        fun scriptEntry(id: Long): String = "$SCRIPTS_DIRECTORY/$id.js"
    }
}
