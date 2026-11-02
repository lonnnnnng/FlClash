package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.service.CoreRuntimeStatus

private data class PreviewContext(
    val profile: Profile?,
    val script: Script?,
    val currentId: Long?,
    val runtime: CoreRuntimeStatus,
    val destination: String,
)

@Composable
internal fun RuntimeConfigPreviewHost(
    state: RuntimeConfigPreviewUiState,
    profile: Profile?,
    boundScript: Script?,
    currentProfileId: Long?,
    runtimeState: CoreRuntimeStatus,
    destination: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val context = PreviewContext(profile, boundScript, currentProfileId, runtimeState, destination)
    val openedContext = remember(state.profileId) { context }
    LaunchedEffect(state.profileId, context) {
        // 作者 long：预览属于所点卡片和打开时的页面；目标被删改、热切配置或离开页面后不继续展示旧快照。
        if (state.profileId != null && (profile?.id != state.profileId || openedContext != context)) onClose()
    }
    val owner = LocalLifecycleOwner.current
    val closeLatest by rememberUpdatedState(onClose)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            // 作者 long：配置正文可能包含节点凭据，退到后台或宿主销毁即丢弃，不在返回时自动恢复。
            if (event == Lifecycle.Event.ON_STOP) closeLatest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); closeLatest() }
    }
    RuntimeConfigPreviewSheet(state, onRetry, onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuntimeConfigPreviewSheet(
    state: RuntimeConfigPreviewUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    if (state.profileId == null) return
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("runtime_config_preview_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("运行配置", style = MaterialTheme.typography.titleLarge)
                    state.preview?.let { Text(it.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                TextButton(onClick = onRetry, enabled = !state.isLoading, modifier = Modifier.testTag("runtime_config_preview_retry")) {
                    Text(if (state.error != null) "重试" else "刷新")
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("runtime_config_preview_close")) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭运行配置")
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("runtime_config_preview_loading"))
                }
                state.error != null -> Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("runtime_config_preview_error"))
                    Text("请确认这份配置及绑定脚本可用后重试。", style = MaterialTheme.typography.bodyMedium)
                }
                state.preview != null -> {
                    Text(
                        if (state.preview.isApplied) "当前核心加载的配置，只读。模式和节点以当前选择为准。"
                        else "由这份配置生成的预览，只读；尚未经过核心校验。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp).testTag("runtime_config_preview_origin"),
                    )
                    val lines = remember(state.preview.content) { state.preview.content.lines() }
                    // 作者 long：只为可视行布局，正文保留选择能力但不提供编辑或自动导出，避免改动原始配置。
                    SelectionContainer(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize().testTag("runtime_config_preview_content"), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(lines) { line ->
                                Text(line.ifEmpty { " " }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
