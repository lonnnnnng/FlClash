package com.lonnnnnng.flclash.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.data.DeepLinkProfileImport
import com.lonnnnnng.flclash.data.DeepLinkProfileParser
import com.lonnnnnng.flclash.data.ProfileQrCamera
import com.lonnnnnng.flclash.data.QrCodeImageImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 作者 long：镜头与权限只属于扫码窗口；二维码确认后复用订阅表单，不形成新的配置写入链路。 */
@Composable
internal fun ProfileQrScannerPage(onClose: () -> Unit, onImport: (DeepLinkProfileImport) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val importCallback by rememberUpdatedState(onImport)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var ready by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeSession by remember { mutableStateOf<ProfileQrCamera?>(null) }
    var pendingImport by remember { mutableStateOf<DeepLinkProfileImport?>(null) }
    var torch by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }

    fun accept(values: List<String>) {
        if (leaving || disposed || pendingImport != null) return
        val result = DeepLinkProfileParser.fromQrCodes(values)
        if (result == null) {
            if (values.isNotEmpty()) hint = "二维码中没有可导入的配置链接"
        } else {
            // 作者 long：连续帧和相册返回只能交付一次，后续帧不能重复打开表单。
            pendingImport = result
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picking = false
        if (uri != null && !disposed && !leaving) {
            decoding = true
            scope.launch {
                try {
                    val values = withContext(Dispatchers.IO) { QrCodeImageImporter.decode(context, uri) }
                    if (values.isEmpty()) hint = "图片中没有可识别的二维码" else accept(values)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 作者 long：解析异常可能携带原始 URI 或内容，只展示固定提示，避免泄漏订阅信息。
                    hint = "无法识别这张图片，请换一张清晰的二维码图片"
                } finally { decoding = false }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
        if (!granted && !requested) { requested = true; permission.launch(Manifest.permission.CAMERA) }
    }
    LaunchedEffect(hint) { if (hint != null) { delay(2500); hint = null } }
    LaunchedEffect(pendingImport, foreground) {
        // 作者 long：相册识别或最后一帧可能在后台才完成，只在窗口恢复前台后交付确认表单。
        pendingImport?.let { result -> if (foreground && !leaving && !disposed) { leaving = true; importCallback(result) } }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            // 作者 long：ON_STOP 可能先于下一帧重组，主动释放镜头，不能只等待 DisposableEffect 随界面刷新解绑。
            if (!foreground) { activeSession?.close(); camera = null; torch = false }
            else retry++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { disposed = true; owner.lifecycle.removeObserver(observer) }
    }
    val scanning = ready && foreground && granted && !picking && !decoding && !leaving && pendingImport == null && !cameraError
    DisposableEffect(preview, scanning, retry, owner) {
        val view = preview
        val session = if (scanning && view != null) ProfileQrCamera(context, owner, view,
            onCamera = { camera = it },
            onCodes = { if (foreground && !picking && !decoding) accept(it) },
            onError = { camera = null; cameraError = true },
        ) else null
        activeSession = session
        onDispose { session?.close(); if (activeSession === session) activeSession = null; camera = null; torch = false }
    }
    val close = { leaving = true; onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val bars = window?.let { WindowCompat.getInsetsController(it, view) }
            bars?.isAppearanceLightStatusBars = false
            bars?.isAppearanceLightNavigationBars = false
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("profile_qr_scanner")) {
            if (granted && !cameraError) AndroidView(
                factory = { PreviewView(it).apply {
                    // 作者 long：COMPATIBLE 使用 TextureView，确保全屏 Dialog 上的取景蒙层和按钮正确叠加。
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    preview = this
                } },
                modifier = Modifier.fillMaxSize().testTag("profile_qr_preview"),
            )
            ProfileQrScannerOverlay(
                cameraReady = camera != null,
                denied = !granted,
                cameraError = cameraError,
                decoding = decoding,
                hint = hint,
                hasFlash = camera?.cameraInfo?.hasFlashUnit() == true,
                torch = torch,
                onClose = close,
                onAlbum = {
                    if (!picking && !decoding && !leaving) {
                        picking = true
                        try { album.launch("image/*") } catch (_: Exception) { picking = false; hint = "无法打开相册" }
                    }
                },
                onRetry = {
                    cameraError = false
                    retry++
                    if (!granted) { requested = true; permission.launch(Manifest.permission.CAMERA) }
                },
                onSettings = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        .onFailure { hint = "无法打开设置，请在系统设置中允许相机权限" }
                },
                onTorch = {
                    val enabled = !torch
                    camera?.cameraControl?.enableTorch(enabled)?.let { task ->
                        task.addListener({ runCatching { task.get() }.onSuccess { if (camera != null) torch = enabled }
                            .onFailure { hint = "无法切换手电筒，请重试" } }, ContextCompat.getMainExecutor(context))
                    }
                },
            )
        }
    }
}

@Composable
internal fun ProfileQrScannerOverlay(
    cameraReady: Boolean,
    denied: Boolean,
    cameraError: Boolean,
    decoding: Boolean,
    hint: String?,
    hasFlash: Boolean,
    torch: Boolean,
    onClose: () -> Unit,
    onAlbum: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onTorch: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val side = minOf(size.width * .67f, size.height * .48f, 400.dp.toPx())
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2 - 32.dp.toPx()
        val dim = Color.Black.copy(alpha = .6f)
        drawRect(dim, size = Size(size.width, top))
        drawRect(dim, Offset(0f, top + side), Size(size.width, size.height - top - side))
        drawRect(dim, Offset(0f, top), Size(left, side))
        drawRect(dim, Offset(left + side, top), Size(left, side))
        if (!denied && !cameraError) drawRoundRect(primary, Offset(left, top), Size(side, side), CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx()))
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(onClose, Modifier.testTag("profile_qr_close")) { Icon(Icons.Outlined.Close, "关闭扫码") }
            FilledTonalIconButton(onAlbum, Modifier.testTag("profile_qr_album"), enabled = !decoding) { Icon(Icons.Outlined.PhotoLibrary, "从相册选择") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (denied || cameraError) Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .8f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (denied) "允许相机权限后即可扫描配置二维码" else "相机不可用，请重试或从相册选择", color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("profile_qr_error"))
                Button(onRetry, Modifier.testTag("profile_qr_retry")) { Text(if (denied) "允许使用相机" else "重试") }
                if (denied) TextButton(onSettings, Modifier.testTag("profile_qr_settings")) { Text("前往设置") }
            } else if (!cameraReady || decoding) CircularProgressIndicator(Modifier.testTag("profile_qr_loading"))
        }
        Text(hint ?: if (decoding) "正在识别图片…" else "将配置二维码放入框内，识别后确认导入", color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("profile_qr_hint"))
        FilledTonalIconButton(onTorch, Modifier.size(64.dp).testTag("profile_qr_torch"), enabled = hasFlash && cameraReady && !decoding, shape = CircleShape) {
            Icon(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, if (torch) "关闭手电筒" else "打开手电筒", Modifier.size(30.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}
