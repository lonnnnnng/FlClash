package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.core.CoreMemoryStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardMemoryCard(state: MemoryUiState, onRelease: () -> Unit) {
    var detail by rememberSaveable { mutableStateOf(false) }
    DashboardSurface(modifier = Modifier.testTag("dashboard_memory_card"), onClick = { detail = true }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Memory, contentDescription = null)
            Text("内存信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
        Spacer(Modifier.height(12.dp))
        Text(state.snapshot?.rss?.let(::formatBytes) ?: if (state.isLoading) "读取中…" else "—",
            style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("dashboard_memory_total"))
        state.snapshot?.let { memory ->
            Spacer(Modifier.height(14.dp))
            val fraction = if (memory.rss > 0L) (memory.runtimeTotal.toDouble() / memory.rss).toFloat().coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("应用 " + formatBytes(memory.appResidentBytes()), style = MaterialTheme.typography.bodyMedium)
                Text("核心 " + formatBytes(memory.runtimeTotal), style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (detail) DashboardModalSheet(title = "内存信息", sheetTag = "memory_detail_sheet", closeTag = "memory_detail_close",
        onDismiss = { detail = false }, actions = {
            // 作者 long：回收属于详情主操作，固定在标题栏，失败提示变长或正文滚动后仍能立即重试。
            IconButton(onClick = onRelease, enabled = !state.isReleasing, modifier = Modifier.testTag("memory_release")) {
                if (state.isReleasing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.DeleteSweep, null)
            }
        }) {
            Text(state.snapshot?.rss?.let(::formatBytes) ?: "—", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("memory_detail_total"))
            Text("进程驻留内存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.snapshot?.let { memory -> MemoryRows(memory) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("memory_detail_error")) }
            state.feedback?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("memory_detail_feedback")) }
    }
}

@Composable
private fun MemoryRows(memory: CoreMemoryStats) {
    DashboardDetailSection("应用（与核心共享进程）") {
        MemoryRow("应用（不含核心运行时）", memory.appResidentBytes())
    }
    DashboardDetailSection("核心运行时") {
        MemoryRow("堆使用中", memory.heapInuse)
        MemoryRow("堆保留空闲", memory.heapIdle)
        MemoryRow("栈", memory.stackInuse)
        MemoryRow("运行时其他", memory.runtimeOther)
    }
    // 作者 long：Go 统计与 OS 驻留量采用不同口径，避免把分项误当可严格相加的系统总量。
    Text("核心与应用共享进程。总量按进程驻留内存统计，核心分项按 Go 运行时统计。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MemoryRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium)
    }
}
