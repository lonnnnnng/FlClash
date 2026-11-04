package com.lonnnnnng.flclash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ProfileSharingHost(viewModel: ProfileSharingViewModel, onMessage: suspend (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")) {
        viewModel.finishExport(it?.toString())
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, owner) {
        // 作者 long：缓存读取完成时页面可能已退到后台，保存窗口只在前台发起，回到页面后继续待处理请求。
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.map { it.exportRequest?.token }.distinctUntilChanged().collect { token ->
                token?.let { viewModel.claimExportRequest(it) }?.let { fileName ->
                    try { launcher.launch(fileName) } catch (_: Exception) { viewModel.exportLaunchFailed() }
                }
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { onMessage(it) } }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            // 作者 long：进入后台清掉二维码；系统保存窗口也会触发后台，但导出快照必须保留到结果返回。
            if (event == Lifecycle.Event.ON_STOP) viewModel.closeQr()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.closeQr() }
    }
    state.qr?.let { qr ->
        ProfileQrDialog(qr, onCopy = { viewModel.copyLink(qr.profileId) }, onClose = viewModel::closeQr)
    }
}

@Composable
internal fun ProfileQrDialog(state: ProfileQrUiState, onCopy: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("profile_qr_dialog"),
        onDismissRequest = onClose,
        title = { Text(state.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.matrix?.let { matrix ->
                    Canvas(Modifier.size(280.dp).testTag("profile_qr_image")) {
                        // 作者 long：深浅主题均保持黑码白底，并以整数像素绘制模块和四格静区，避免缩放模糊影响扫码。
                        drawRect(Color.White)
                        val scale = (minOf(size.width, size.height) / matrix.width).toInt().coerceAtLeast(1)
                        val left = ((size.width - matrix.width * scale) / 2).toInt()
                        val top = ((size.height - matrix.height * scale) / 2).toInt()
                        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                            if (matrix[x, y]) drawRect(Color.Black,
                                Offset((left + x * scale).toFloat(), (top + y * scale).toFloat()),
                                Size(scale.toFloat(), scale.toFloat()))
                        }
                    }
                } ?: if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                    Text("正在生成二维码…")
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("profile_qr_close")) { Text("关闭") } },
        dismissButton = { TextButton(onClick = onCopy) { Text("复制链接") } },
    )
}
