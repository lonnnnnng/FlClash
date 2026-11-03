package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.UUID
import java.security.MessageDigest
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ScriptStore {
    val scripts: StateFlow<List<Script>>

    suspend fun create(label: String, content: String): Script

    suspend fun createRemote(label: String, url: String): Script

    suspend fun readContent(id: Long): String

    suspend fun update(id: Long, label: String, content: String): Script

    suspend fun sync(id: Long): Script

    suspend fun delete(id: Long)

    suspend fun reorder(ids: List<Long>)

    suspend fun importFile(fileName: String, bytes: ByteArray): Script

    suspend fun updateRemote(expected: Script, url: String): Script

    suspend fun updateIfUnchanged(expected: Script, label: String, content: String): Script

    suspend fun readDocument(id: Long): ScriptDocument = ScriptDocument(
        scripts.value.first { it.id == id }, readContent(id))

    suspend fun updateDocument(expected: ScriptDocument, label: String, content: String): Script =
        updateIfUnchanged(expected.script, label, content)
}

class ScriptRepository(
    context: Context,
    private val database: KClashDatabase,
    private val scriptClient: ScriptClient = HttpScriptClient(),
    private val filesRoot: File = context.applicationContext.filesDir,
    private val runtime: (() -> CoreRuntimeStatusProvider)? = null,
) : ScriptStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scriptDao = database.scripts()
    private val fileLock = Mutex()
    private val mutableScripts = MutableStateFlow<List<Script>>(emptyList())

    override val scripts: StateFlow<List<Script>> = mutableScripts.asStateFlow()

    init {
        scope.launch {
            scriptDao.observeAll().collectLatest { rows ->
                mutableScripts.value = rows.map(ScriptEntity::toDomain)
            }
        }
    }

    override suspend fun create(label: String, content: String): Script = writeScript(null) {
        val normalizedLabel = normalizeLabel(label)
        val normalizedContent = normalizeContent(content)
        val rows = scriptDao.getAll()
        require(rows.none { it.label == normalizedLabel }) { "脚本名称已经存在" }
        val script = Script(
            id = newId(),
            label = normalizedLabel,
            lastUpdateAt = System.currentTimeMillis(),
            orderIndex = rows.size,
        )
        createContent(script, normalizedContent)
        script
    }

    override suspend fun importFile(fileName: String, bytes: ByteArray): Script = writeScript(null) {
        val content = decodeScriptBytes(bytes)
        val rows = scriptDao.getAll()
        // 作者 long：文件名只用作显示名称，导入同名文件分配后缀，不能覆盖已绑定配置的原脚本。
        val stem = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { "脚本" }
        val label = uniqueScriptLabel(stem, rows.map { it.label })
        val script = Script(newId(), label, lastUpdateAt = System.currentTimeMillis(), orderIndex = rows.size)
        createContent(script, content)
        script
    }

    override suspend fun createRemote(label: String, url: String): Script = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        val downloaded = scriptClient.download(normalizedUrl)
        val normalizedContent = decodeScriptBytes(downloaded.bytes)
        writeScript(null) {
        val rows = scriptDao.getAll()
        require(rows.none { it.url == normalizedUrl }) { "该远程脚本已经添加" }
        val normalizedLabel = if (label.isBlank()) uniqueScriptLabel(downloaded.suggestedLabel ?: suggestedLabel(normalizedUrl), rows.map { it.label }) else normalizeLabel(label)
        require(rows.none { it.label == normalizedLabel }) { "脚本名称已经存在" }
        val script = Script(
            id = newId(),
            label = normalizedLabel,
            url = normalizedUrl,
            lastUpdateAt = System.currentTimeMillis(),
            orderIndex = rows.size,
        )
        createContent(script, normalizedContent)
        script
        }
    }

    override suspend fun readContent(id: Long): String = withContext(Dispatchers.IO) { fileLock.withLock {
        requireNotNull(scriptDao.getById(id)) { "脚本不存在" }
        check(scriptFile(id).isFile) { "脚本文件不存在" }
        decodeScriptBytes(scriptFile(id).readBytes())
    } }

    override suspend fun readDocument(id: Long): ScriptDocument = withContext(Dispatchers.IO) { fileLock.withLock {
        val script = requireNotNull(scriptDao.getById(id)) { "脚本不存在" }.toDomain()
        val file = scriptFile(id)
        check(file.isFile) { "脚本文件不存在" }
        val bytes = file.readBytes()
        ScriptDocument(script, decodeScriptBytes(bytes), digest(bytes), file.lastModified())
    } }

    override suspend fun update(id: Long, label: String, content: String): Script =
        writeScript(id) {
            val normalizedLabel = normalizeLabel(label)
            val normalizedContent = normalizeContent(content)
            val current = requireNotNull(scriptDao.getById(id)) { "脚本不存在" }.toDomain()
            require(scriptDao.getAll().none { it.id != id && it.label == normalizedLabel }) {
                "脚本名称已经存在"
            }
            val updated = current.copy(
                label = normalizedLabel,
                lastUpdateAt = maxOf(System.currentTimeMillis(), (current.lastUpdateAt ?: 0L) + 1),
            )
            commitContent(current, updated, normalizedContent)
            updated
        }

    override suspend fun updateIfUnchanged(expected: Script, label: String, content: String): Script =
        writeScript(expected.id) {
            val current = requireNotNull(scriptDao.getById(expected.id)) { "脚本已删除" }.toDomain()
            check(current.label == expected.label && current.url == expected.url && current.lastUpdateAt == expected.lastUpdateAt) { "脚本已变化，请重新打开后保存" }
            val normalized = normalizeLabel(label)
            require(scriptDao.getAll().none { it.id != current.id && it.label == normalized }) { "脚本名称已经存在" }
            val updated = current.copy(label = normalized, lastUpdateAt = maxOf(System.currentTimeMillis(), (current.lastUpdateAt ?: 0) + 1))
            commitContent(current, updated, normalizeContent(content))
            updated
        }

    override suspend fun updateDocument(expected: ScriptDocument, label: String, content: String): Script = writeScript(expected.script.id) {
        val current = requireNotNull(scriptDao.getById(expected.script.id)) { "脚本已删除" }.toDomain()
        checkVersion(current, expected.script)
        checkDocument(expected)
        val normalized = normalizeLabel(label)
        require(scriptDao.getAll().none { it.id != current.id && it.label == normalized }) { "脚本名称已经存在" }
        val updated = current.copy(label = normalized, lastUpdateAt = maxOf(System.currentTimeMillis(), (current.lastUpdateAt ?: 0) + 1))
        commitContent(current, updated, normalizeContent(content))
        updated
    }

    override suspend fun updateRemote(expected: Script, url: String): Script = withContext(Dispatchers.IO) {
        require(expected.url.isNotBlank()) { "本地脚本没有远程地址" }
        val original = readDocument(expected.id)
        checkVersion(original.script, expected)
        val source = normalizeUrl(url)
        val content = decodeScriptBytes(scriptClient.download(source).bytes)
        writeScript(expected.id) {
            val latest = requireNotNull(scriptDao.getById(expected.id)) { "脚本已删除" }.toDomain()
            // 作者 long：先下载新地址，再同时提交地址和正文；失败或期间编辑时保留旧地址和旧文件。
            check(latest.url == expected.url && latest.label == expected.label && latest.lastUpdateAt == expected.lastUpdateAt) { "脚本已变化，请重新修改地址" }
            checkDocument(original)
            require(scriptDao.getAll().none { it.id != latest.id && it.url == source }) { "该远程脚本已经添加" }
            val updated = latest.copy(url = source, lastUpdateAt = maxOf(System.currentTimeMillis(), (latest.lastUpdateAt ?: 0) + 1))
            commitContent(latest, updated, content)
            updated
        }
    }

    override suspend fun sync(id: Long): Script = withContext(Dispatchers.IO) {
        val original = readDocument(id)
        val current = original.script
        val url = current.url.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("本地脚本没有远程地址")
        val downloaded = scriptClient.download(url)
        val normalizedContent = decodeScriptBytes(downloaded.bytes)
        writeScript(id) {
            val latest = requireNotNull(scriptDao.getById(id)) { "脚本已删除" }.toDomain()
            // 作者 long：下载在锁外执行，提交时复核源和版本，不能覆盖期间编辑的脚本或让删除的脚本复活。
            check(latest.url == current.url && latest.lastUpdateAt == current.lastUpdateAt) { "脚本已变化，请重新同步" }
            checkDocument(original)
            val updated = latest.copy(lastUpdateAt = maxOf(System.currentTimeMillis(), (latest.lastUpdateAt ?: 0L) + 1))
            commitContent(latest, updated, normalizedContent)
            updated
        }
    }

    override suspend fun delete(id: Long) = writeScript(id) {
        val current = scriptDao.getById(id) ?: return@writeScript
        val file = scriptFile(id)
        val original = file.takeIf(File::isFile)?.readBytes()
        val modified = file.lastModified()
        try { database.withTransaction {
            // 作者 long：删除脚本时同步清掉 Profile 的绑定，避免未来执行链读取到已经不存在的正文文件。
            database.profiles().clearScriptBinding(id)
            scriptDao.delete(current)
            check(!file.exists() || file.delete()) { "删除脚本文件失败" }
            Unit
        } } catch (failure: Exception) {
            restoreFile(id, original, modified, failure)
            throw failure
        }
    }

    override suspend fun reorder(ids: List<Long>) = writeScript(null) {
        database.withTransaction {
            val existingIds = scriptDao.getAll().map(ScriptEntity::id)
            require(ids.size == existingIds.size && ids.toSet() == existingIds.toSet()) { "脚本列表已变化，请重新排序" }
            ids.forEachIndexed { index, id -> scriptDao.updateOrder(id, index) }
        }
    }

    private suspend fun <T> writeScript(id: Long?, write: suspend () -> T): T = withContext(Dispatchers.IO) {
        // 作者 long：所有入口（含后台同步）遵守生命周期锁→目录锁，下载留在锁外，提交时再检查真实启停状态。
        val commit: suspend () -> T = { fileLock.withLock { withContext(NonCancellable) { write() } } }
        val manager = runtime?.invoke()
        if (manager != null) manager.withScriptWrite(id?.let { scriptFile(it).path }, commit) else commit()
    }

    private fun checkVersion(current: Script, expected: Script) {
        check(current.label == expected.label && current.url == expected.url && current.lastUpdateAt == expected.lastUpdateAt) { "脚本已变化，请重新打开后保存" }
    }

    private fun checkDocument(expected: ScriptDocument) {
        val file = scriptFile(expected.script.id)
        check(file.isFile && file.lastModified() == expected.modified && digest(file.readBytes()) == expected.digest) { "脚本正文已变化，请重新打开后保存" }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun scriptFile(id: Long): File = File(scriptsDirectory, "$id.js")

    private val scriptsDirectory: File
        get() = File(filesRoot, SCRIPTS_DIRECTORY)

    private suspend fun createContent(script: Script, content: String) = withContext(NonCancellable) {
        // 作者 long：受理后的正文与元数据必须成对写完，离开页面不能留下只有文件的半次导入。
        try {
            replaceFileAtomically(script.id, content)
            scriptDao.insert(script.toEntity())
        } catch (failure: Exception) {
            scriptFile(script.id).delete()
            throw failure
        }
    }

    private suspend fun commitContent(previous: Script, updated: Script, content: String) = withContext(NonCancellable) {
        val file = scriptFile(previous.id)
        val original = file.takeIf(File::isFile)?.readBytes()
        val modified = file.lastModified()
        try {
            database.withTransaction {
                check(scriptDao.getById(previous.id) == previous.toEntity()) { "脚本已变化，请重试" }
                replaceFileAtomically(previous.id, content)
                scriptDao.update(updated.toEntity())
            }
        } catch (failure: Exception) {
            restoreFile(previous.id, original, modified, failure)
            throw failure
        }
    }

    private fun replaceFileAtomically(id: Long, content: String) {
        replaceBytesAtomically(id, content.toByteArray(Charsets.UTF_8))
    }

    private fun replaceBytesAtomically(id: Long, bytes: ByteArray) {
        val target = scriptFile(id)
        check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) { "无法创建脚本目录" }
        val modified = target.lastModified()
        val temporary = File(target.parentFile, ".${target.name}-${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            // 作者 long：同长度编辑也必须改变文件版本，避免核心沿用旧缓存；rename失败不能直接覆盖目标而留下半份正文。
            check(temporary.setLastModified(maxOf(System.currentTimeMillis(), modified + 1))) { "无法登记脚本文件版本" }
            if (!temporary.renameTo(target)) throw IOException("无法原子替换脚本文件")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun restoreFile(id: Long, original: ByteArray?, modified: Long, failure: Exception) {
        try {
            val file = scriptFile(id)
            if (original != null) {
                if (!file.isFile || !file.readBytes().contentEquals(original)) replaceBytesAtomically(id, original)
                check(file.setLastModified(modified)) { "恢复脚本时间失败" }
            } else check(!file.exists() || file.delete()) { "脚本回滚失败" }
        } catch (rollback: Exception) { failure.addSuppressed(rollback) }
    }

    private fun normalizeLabel(label: String): String = label.trim().also {
        require(it.isNotEmpty()) { "脚本名称不能为空" }
    }

    private fun normalizeContent(content: String): String = content.also {
        require(it.isNotEmpty()) { "脚本内容不能为空" }
        require(it.isNotBlank() && '\u0000' !in it) { "脚本内容必须是非空文本" }
        require(it.toByteArray(Charsets.UTF_8).size <= SCRIPT_MAX_BYTES) { "脚本文件超过 2 MiB 限制" }
    }

    private fun normalizeUrl(url: String): String = url.trim().also {
        require(it.isNotEmpty()) { "脚本地址不能为空" }
        val parsed = runCatching { URL(it) }.getOrElse {
            throw IllegalArgumentException("脚本地址格式无效", it)
        }
        require(parsed.protocol == "http" || parsed.protocol == "https") {
            "仅支持 HTTP/HTTPS 脚本地址"
        }
    }

    private fun suggestedLabel(url: String): String =
        runCatching { URL(url).path.substringAfterLast('/').substringBeforeLast('.') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "远程脚本"

    private fun newId(): Long = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    private companion object {
        const val SCRIPTS_DIRECTORY = "scripts"
    }
}
