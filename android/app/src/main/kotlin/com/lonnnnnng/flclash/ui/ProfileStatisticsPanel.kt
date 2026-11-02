package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileStatisticsPanel(state: ProfileStatisticsUiState, onRetry: () -> Unit) {
    val counts = state.counts
    val items = listOf("代理组" to counts?.groups, "节点" to counts?.proxies,
        "规则" to counts?.rules, "Providers" to counts?.providers)
    Column(Modifier.fillMaxWidth().testTag("profile_statistics"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { column, (label, count) ->
                    Surface(Modifier.weight(1f).semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            // 作者 long：加载和失败均不显示伪造的零；真实空配置的零值仍明确可见。
                            Text(count?.toString() ?: if (state.isLoading) "…" else "—", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.testTag("profile_statistics_value_" + (row * 2 + column)))
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (state.error != null) {
            Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("profile_statistics_error"))
            TextButton(onClick = onRetry, enabled = !state.isLoading, modifier = Modifier.testTag("profile_statistics_retry")) { Text("重试") }
        }
    }
}
