package com.lonnnnnng.flclash.data

import android.content.Context
import android.util.Size
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/** 作者 long：相机只服务于当前可见扫码页；退出后只解绑本会话的用例，避免影响其他相机使用者。 */
@MainThread
internal class ProfileQrCamera(
    context: Context,
    owner: LifecycleOwner,
    previewView: PreviewView,
    private val onCamera: (Camera) -> Unit,
    private val onCodes: (List<String>) -> Unit,
    private val onError: () -> Unit,
) : AutoCloseable {
    private val executor = ContextCompat.getMainExecutor(context)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )
    private val preview = Preview.Builder().build()
    private val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
        ).build())
        .build()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var closed = false
    private var processing = false

    init {
        preview.setSurfaceProvider(previewView.surfaceProvider)
        analyzeFrames()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // 作者 long：权限弹窗、离页和相册可能早于相机初始化完成，迟到结果不能重新占用镜头。
            if (!closed) {
                runCatching {
                    val ready = future.get()
                    provider = ready
                    check(ready.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    ready.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onSuccess { camera = it; onCamera(it) }
                    .onFailure { close(); onError() }
            }
        }, executor)
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeFrames() {
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (closed || media == null) {
                proxy.close()
            } else {
                processing = true
                // 作者 long：ML Kit 异步读取原始帧，必须等任务结束才归还 ImageProxy，否则预览会卡住或读到已关闭图像。
                runCatching {
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                }.onSuccess { task ->
                    task.addOnSuccessListener(executor) { codes ->
                        if (!closed) onCodes(codes.mapNotNull { it.rawValue })
                    }.addOnFailureListener(executor) {
                        if (!closed) { close(); onError() }
                    }.addOnCompleteListener(executor) {
                        proxy.close()
                        processing = false
                        if (closed) scanner.close()
                    }
                }.onFailure {
                    proxy.close()
                    processing = false
                    close()
                    onError()
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        camera?.cameraControl?.enableTorch(false)
        analysis.clearAnalyzer()
        provider?.unbind(preview, analysis)
        if (!processing) scanner.close()
    }
}
