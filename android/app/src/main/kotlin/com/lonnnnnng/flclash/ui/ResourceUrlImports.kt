package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class UrlImportStatus(val label: String) {
    QUEUED("等待导入"), RUNNING("正在导入"), IMPORTED("已导入"), SKIPPED("已存在，已跳过"), FAILED("导入失败"), CANCELLED("已取消"),
}
data class UrlImportRow(val input: NamedResourceUrl, val status: UrlImportStatus = UrlImportStatus.QUEUED)
data class UrlImportState(val rows: List<UrlImportRow> = emptyList(), val running: Boolean = false, val error: String? = null)

/** 作者 long：规则和图标共用批次状态，最多同时导入四项；重试只处理失败/取消项，成功资源不回滚。 */
class ResourceUrlImportController(private val scope: CoroutineScope, private val import: suspend (NamedResourceUrl) -> ResourceImportResult) {
    private val mutable = MutableStateFlow(UrlImportState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun start(text: String, label: String) {
        if (mutable.value.running) return
        val inputs = try { NamedResourceUrls.parse(text, label) } catch (_: Exception) {
            mutable.update { it.copy(error = "请输入有效的 HTTP/HTTPS URL，每行一个，最多 128 项；名称最多 128 字符") }; return
        }
        mutable.value = UrlImportState(inputs.map(::UrlImportRow))
        run(inputs.map { it.url }.toSet())
    }
    fun retry() {
        if (mutable.value.running) return
        run(mutable.value.rows.filter { it.status in setOf(UrlImportStatus.FAILED, UrlImportStatus.CANCELLED) }.map { it.input.url }.toSet())
    }
    fun cancel() { job?.cancel() }
    fun reset() { if (!mutable.value.running) mutable.value = UrlImportState() }
    private fun update(url: String, status: UrlImportStatus) { mutable.update { current -> current.copy(rows = current.rows.map { if (it.input.url == url) it.copy(status = status) else it }) } }
    private fun run(urls: Set<String>) {
        if (urls.isEmpty()) return
        val targets = mutable.value.rows.filter { it.input.url in urls }
        mutable.update { it.copy(running = true, error = null, rows = it.rows.map { row -> if (row.input.url in urls) row.copy(status = UrlImportStatus.QUEUED) else row }) }
        job = scope.launch {
            val permits = Semaphore(4)
            try { coroutineScope {
                targets.forEach { row -> launch {
                    permits.withPermit {
                        update(row.input.url, UrlImportStatus.RUNNING)
                        try {
                            val result = withContext(Dispatchers.IO) { import(row.input) }
                            update(row.input.url, if (result == ResourceImportResult.SKIPPED) UrlImportStatus.SKIPPED else UrlImportStatus.IMPORTED)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { update(row.input.url, UrlImportStatus.FAILED) }
                    }
                } }
            } } finally {
                mutable.update { it.copy(running = false, rows = it.rows.map { row ->
                    if (row.status in setOf(UrlImportStatus.QUEUED, UrlImportStatus.RUNNING)) row.copy(status = UrlImportStatus.CANCELLED) else row
                }) }
            }
        }
    }
}

@Composable
internal fun ResourceUrlImportPage(title: String, state: UrlImportState, onDismiss: () -> Unit,
    onStart: (String, String) -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, onReset: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (state.running || (state.rows.isEmpty() && (text.isNotBlank() || label.isNotBlank()))) confirmClose = true else onDismiss() }
    val single = text.trim().split(Regex("\\s+")).count(String::isNotEmpty) <= 1
    ProfileEditorPage(onDismissRequest = close, title = { Text(title) }, backTag = "resource_urls_back", confirmButton = {
        when {
            state.running -> IconButton(onClick = onCancel, modifier = Modifier.testTag("resource_urls_cancel")) { Icon(Icons.Outlined.Close, "取消剩余导入") }
            state.rows.isEmpty() -> TextButton(onClick = { onStart(text, label) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("resource_urls_start")) { Text("导入") }
            else -> TextButton(onClick = onDismiss) { Text("完成") }
        }
    }, text = {
        Column(Modifier.fillMaxSize().imePadding().testTag("resource_urls_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.rows.isEmpty()) {
                OutlinedTextField(text, { text = it }, minLines = 6, maxLines = 12, label = { Text("URL · 每行一个") }, modifier = Modifier.fillMaxWidth().testTag("resource_urls_input"))
                if (single) OutlinedTextField(label, { label = it }, singleLine = true, label = { Text("名称（可选）") }, modifier = Modifier.fillMaxWidth().testTag("resource_urls_label"))
                Text("已存在的 URL 会跳过。多个链接按文件名命名，重名时自动添加序号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val complete = state.rows.count { it.status !in setOf(UrlImportStatus.QUEUED, UrlImportStatus.RUNNING) }
                if (state.running) LinearProgressIndicator(progress = { complete.toFloat() / state.rows.size }, modifier = Modifier.fillMaxWidth())
                Text("$complete / ${state.rows.size} · 已导入 ${state.rows.count { it.status == UrlImportStatus.IMPORTED }} · 已跳过 ${state.rows.count { it.status == UrlImportStatus.SKIPPED }}")
                Text("失败或取消不会移除已经导入的资源。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f)) { itemsIndexed(state.rows, key = { _, row -> row.input.url }) { index, row ->
                    // 作者 long：结果页只显示名称和序号，不把订阅参数或 Token 暴露在错误摘要及截图中。
                    ListItem(headlineContent = { Text(row.input.suggestedName("资源 ${index + 1}")) }, supportingContent = { Text(row.status.label, color = if (row.status == UrlImportStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) })
                } }
                if (!state.running) {
                    if (state.rows.any { it.status in setOf(UrlImportStatus.FAILED, UrlImportStatus.CANCELLED) }) TextButton(onClick = onRetry, modifier = Modifier.testTag("resource_urls_retry")) { Text("重试失败和取消项") }
                    TextButton(onClick = onReset) { Text("继续导入") }
                }
            }
        }
    })
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text(if (state.running) "取消剩余导入？" else "放弃输入？") }, text = { Text(if (state.running) "已导入的资源会保留。" else "尚未导入的链接将丢失。") },
        confirmButton = { TextButton(onClick = { if (state.running) onCancel(); confirmClose = false; onDismiss() }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("继续") } })
}
