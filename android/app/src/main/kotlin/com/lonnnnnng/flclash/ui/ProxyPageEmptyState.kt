package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 作者 long：加载、无数据和搜索无结果分别显示状态与恢复操作，避免空白页面让用户误以为卡住。 */
@Composable
internal fun ProxyPageEmptyState(title: String, description: String?, tag: String, loading: Boolean = false,
    searching: Boolean = false, actionLabel: String? = null,
    actionEnabled: Boolean = true, actionTag: String = "${tag}_action", onAction: () -> Unit = {}) {
    BoxWithConstraints(Modifier.widthIn(max = 320.dp)) {
        // 作者 long：键盘展开后优先留下状态文字和恢复操作；缩小装饰而不缩小文字，极矮视口仍可滚动。
        val illustrationSize = (maxHeight - 232.dp).coerceIn(64.dp, 160.dp)
        Column(Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
            } else {
                ProfileEmptyIllustration(if (searching) AppGlyphs.search else AppGlyphs.proxies,
                    if (searching) ProfileIllustrationPaths.flower else ProfileIllustrationPaths.sunny, illustrationSize)
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, Modifier, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            if (actionLabel != null) {
                Spacer(Modifier.height(24.dp))
                FilledTonalButton(onAction, enabled = actionEnabled, modifier = Modifier.testTag(actionTag)) { Text(actionLabel) }
            }
        }
    }
}
