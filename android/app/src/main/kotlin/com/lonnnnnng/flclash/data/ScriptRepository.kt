package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.net.URL
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
}

class ScriptRepository(
    context: Context,
    private val database: KClashDatabase,
    private val scriptClient: ScriptClient = HttpScriptClient(),
    private val filesRoot: File = context.applicationContext.filesDir,
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

    override suspend fun create(label: String, content: String): Script = withContext(Dispatchers.IO) { fileLock.withLock {
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
    } }

    override suspend fun importFile(fileName: String, bytes: ByteArray): Script = withContext(Dispatchers.IO) { fileLock.withLock {
        val content = decodeScriptBytes(bytes)
        val rows = scriptDao.getAll()
        // 作者 long：文件名只用作显示名称，导入同名文件分配后缀，不能覆盖已绑定配置的原脚本。
        val stem = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { "脚本" }
        val label = uniqueScriptLabel(stem, rows.map { it.label })
        val script = Script(newId(), label, lastUpdateAt = System.currentTimeMillis(), orderIndex = rows.size)
        createContent(script, content)
        script
    } }

    override suspend fun createRemote(label: String, url: String): Script = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        val downloaded = scriptClient.download(normalizedUrl)
        val normalizedContent = decodeScriptBytes(downloaded.bytes)
        fileLock.withLock {
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

    override suspend fun update(id: Long, label: String, content: String): Script =
        withContext(Dispatchers.IO) { fileLock.withLock {
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
        } }

    override suspend fun updateIfUnchanged(expected: Script, label: String, content: String): Script =
        withContext(Dispatchers.IO) { fileLock.withLock {
            val current = requireNotNull(scriptDao.getById(expected.id)) { "脚本已删除" }.toDomain()
            check(current.label == expected.label && current.url == expected.url && current.lastUpdateAt == expected.lastUpdateAt) { "脚本已变化，请重新打开后保存" }
            val normalized = normalizeLabel(label)
            require(scriptDao.getAll().none { it.id != current.id && it.label == normalized }) { "脚本名称已经存在" }
            val updated = current.copy(label = normalized, lastUpdateAt = maxOf(System.currentTimeMillis(), (current.lastUpdateAt ?: 0) + 1))
            commitContent(current, updated, normalizeContent(content))
            updated
        } }

    override suspend fun updateRemote(expected: Script, url: String): Script = withContext(Dispatchers.IO) {
        require(expected.url.isNotBlank()) { "本地脚本没有远程地址" }
        val source = normalizeUrl(url)
        val content = decodeScriptBytes(scriptClient.download(source).bytes)
        fileLock.withLock {
            val latest = requireNotNull(scriptDao.getById(expected.id)) { "脚本已删除" }.toDomain()
            // 作者 long：先下载新地址，再同时提交地址和正文；失败或期间编辑时保留旧地址和旧文件。
            check(latest.url == expected.url && latest.label == expected.label && latest.lastUpdateAt == expected.lastUpdateAt) { "脚本已变化，请重新修改地址" }
            require(scriptDao.getAll().none { it.id != latest.id && it.url == source }) { "该远程脚本已经添加" }
            val updated = latest.copy(url = source, lastUpdateAt = maxOf(System.currentTimeMillis(), (latest.lastUpdateAt ?: 0) + 1))
            commitContent(latest, updated, content)
            updated
        }
    }

    override suspend fun sync(id: Long): Script = withContext(Dispatchers.IO) {
        val current = requireNotNull(scriptDao.getById(id)) { "脚本不存在" }.toDomain()
        val url = current.url.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("本地脚本没有远程地址")
        val downloaded = scriptClient.download(url)
        val normalizedContent = decodeScriptBytes(downloaded.bytes)
        fileLock.withLock {
            val latest = requireNotNull(scriptDao.getById(id)) { "脚本已删除" }.toDomain()
            // 作者 long：下载在锁外执行，提交时复核源和版本，不能覆盖期间编辑的脚本或让删除的脚本复活。
            check(latest.url == current.url && latest.lastUpdateAt == current.lastUpdateAt) { "脚本已变化，请重新同步" }
            val updated = latest.copy(lastUpdateAt = maxOf(System.currentTimeMillis(), (latest.lastUpdateAt ?: 0L) + 1))
            commitContent(latest, updated, normalizedContent)
            updated
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { fileLock.withLock {
        val current = scriptDao.getById(id) ?: return@withLock
        database.withTransaction {
            // 作者 long：删除脚本时同步清掉 Profile 的绑定，避免未来执行链读取到已经不存在的正文文件。
            database.profiles().clearScriptBinding(id)
            scriptDao.delete(current)
            scriptFile(id).delete()
            Unit
        }
    } }

    override suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) { fileLock.withLock {
        database.withTransaction {
            val existingIds = scriptDao.getAll().map(ScriptEntity::id)
            require(ids.size == existingIds.size && ids.toSet() == existingIds.toSet()) { "脚本列表已变化，请重新排序" }
            ids.forEachIndexed { index, id -> scriptDao.updateOrder(id, index) }
        }
    } }

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
        val original = file.takeIf(File::isFile)?.readText(Charsets.UTF_8)
        try {
            database.withTransaction {
                check(scriptDao.getById(previous.id) == previous.toEntity()) { "脚本已变化，请重试" }
                replaceFileAtomically(previous.id, content)
                scriptDao.update(updated.toEntity())
            }
        } catch (failure: Exception) {
            if (original != null) replaceFileAtomically(previous.id, original)
            else if (file.exists()) check(file.delete()) { "脚本回滚失败" }
            throw failure
        }
    }

    private fun replaceFileAtomically(id: Long, content: String) {
        val target = scriptFile(id)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}-${UUID.randomUUID()}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.outputStream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
            if (!temporary.delete()) throw IOException("无法清理脚本临时文件")
        }
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
