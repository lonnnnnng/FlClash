package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class ProfileSyncStatus { WAITING, RUNNING, SUCCEEDED, FAILED }
data class ProfileSyncItem(val id: Long, val label: String, val status: ProfileSyncStatus = ProfileSyncStatus.WAITING)
data class ProfileSyncUiState(val running: Boolean = false, val items: List<ProfileSyncItem> = emptyList(), val applyError: String? = null) {
    fun withStatus(id: Long, status: ProfileSyncStatus) = copy(items = items.map { if (it.id == id) it.copy(status = status) else it })
}

/** 作者 long：批次结果由VM持有，页面重建不丢失败项；应用失败单列，避免用户反复下载已经保存的配置。 */
@Composable
internal fun ProfileSyncResults(state: ProfileSyncUiState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    if (state.items.isEmpty() && state.applyError == null) return
    val finished = state.items.count { it.status == ProfileSyncStatus.SUCCEEDED || it.status == ProfileSyncStatus.FAILED }
    val failed = state.items.count { it.status == ProfileSyncStatus.FAILED }
    AlertDialog(onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text(if (state.items.isEmpty()) "应用配置" else if (state.running) "正在同步配置" else "同步结果") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("profile_sync_results"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.items.isNotEmpty()) Text(if (state.running) "已完成 $finished / ${state.items.size}" else "成功 ${state.items.size - failed}，失败 $failed")
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.items.forEach { row ->
                    Text("${row.label}：${when (row.status) {
                        ProfileSyncStatus.WAITING -> "等待中"
                        ProfileSyncStatus.RUNNING -> "正在下载"
                        ProfileSyncStatus.SUCCEEDED -> "已保存"
                        ProfileSyncStatus.FAILED -> "同步失败，请检查网络、地址或解密密钥"
                    }}", color = if (row.status == ProfileSyncStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("profile_sync_result_${row.id}"))
                }
                state.applyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.running, modifier = Modifier.testTag("profile_sync_done")) { Text("完成") } },
        dismissButton = { if (failed > 0 || state.applyError != null) TextButton(onClick = onRetry, enabled = !state.running,
            modifier = Modifier.testTag("profile_sync_retry")) { Text(if (failed > 0) "重试失败项" else "重试应用") } },
    )
}
