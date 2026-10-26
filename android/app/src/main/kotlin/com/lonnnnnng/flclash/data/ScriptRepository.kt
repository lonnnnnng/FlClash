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

interface ScriptStore {
    val scripts: StateFlow<List<Script>>

    suspend fun create(label: String, content: String): Script

    suspend fun readContent(id: Long): String

    suspend fun update(id: Long, label: String, content: String): Script

    suspend fun delete(id: Long)

    suspend fun reorder(ids: List<Long>)
}

class ScriptRepository(
    context: Context,
    private val database: KClashDatabase,
) : ScriptStore {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scriptDao = database.scripts()
    private val mutableScripts = MutableStateFlow<List<Script>>(emptyList())

    override val scripts: StateFlow<List<Script>> = mutableScripts.asStateFlow()

    init {
        scope.launch {
            scriptDao.observeAll().collectLatest { rows ->
                mutableScripts.value = rows.map(ScriptEntity::toDomain)
            }
        }
    }

    override suspend fun create(label: String, content: String): Script = withContext(Dispatchers.IO) {
        val normalizedLabel = normalizeLabel(label)
        val normalizedContent = normalizeContent(content)
        require(mutableScripts.value.none { it.label == normalizedLabel }) { "脚本名称已经存在" }
        val script = Script(
            id = newId(),
            label = normalizedLabel,
            lastUpdateAt = System.currentTimeMillis(),
            orderIndex = mutableScripts.value.size,
        )
        replaceFileAtomically(script.id, normalizedContent)
        runCatching { scriptDao.insert(script.toEntity()) }
            .onFailure {
                scriptFile(script.id).delete()
                throw it
            }
        script
    }

    override suspend fun readContent(id: Long): String = withContext(Dispatchers.IO) {
        requireNotNull(scriptDao.getById(id)) { "脚本不存在" }
        scriptFile(id).takeIf(File::isFile)?.readText(Charsets.UTF_8).orEmpty()
    }

    override suspend fun update(id: Long, label: String, content: String): Script =
        withContext(Dispatchers.IO) {
            val normalizedLabel = normalizeLabel(label)
            val normalizedContent = normalizeContent(content)
            val current = requireNotNull(scriptDao.getById(id)) { "脚本不存在" }.toDomain()
            require(mutableScripts.value.none { it.id != id && it.label == normalizedLabel }) {
                "脚本名称已经存在"
            }
            replaceFileAtomically(id, normalizedContent)
            val updated = current.copy(
                label = normalizedLabel,
                lastUpdateAt = System.currentTimeMillis(),
            )
            scriptDao.update(updated.toEntity())
            updated
        }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val current = scriptDao.getById(id) ?: return@withContext
        database.withTransaction {
            scriptDao.delete(current)
            scriptFile(id).delete()
            Unit
        }
    }

    override suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existingIds = scriptDao.getAll().map(ScriptEntity::id)
            val requested = ids.filter { it in existingIds }.distinct()
            val orderedIds = requested + existingIds.filterNot { it in requested }
            orderedIds.forEachIndexed { index, id -> scriptDao.updateOrder(id, index) }
        }
    }

    fun scriptFile(id: Long): File = File(scriptsDirectory, "$id.js")

    private val scriptsDirectory: File
        get() = File(appContext.filesDir, SCRIPTS_DIRECTORY)

    private fun replaceFileAtomically(id: Long, content: String) {
        val target = scriptFile(id)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.outputStream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
            if (!temporary.delete()) throw IOException("无法清理脚本临时文件")
        }
    }

    private fun normalizeLabel(label: String): String = label.trim().also {
        require(it.isNotEmpty()) { "脚本名称不能为空" }
    }

    private fun normalizeContent(content: String): String = content.trim().also {
        require(it.isNotEmpty()) { "脚本内容不能为空" }
    }

    private fun newId(): Long = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    private companion object {
        const val SCRIPTS_DIRECTORY = "scripts"
    }
}
