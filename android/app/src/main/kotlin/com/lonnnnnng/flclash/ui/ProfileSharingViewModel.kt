package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lonnnnnng.flclash.data.ClipboardGateway
import com.lonnnnnng.flclash.data.ProfileDocumentWriter
import com.lonnnnnng.flclash.data.ProfileExport
import com.lonnnnnng.flclash.data.ProfileExportReader
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface ProfileQrEncoder {
    suspend fun encode(url: String): BitMatrix
}

class SubscriptionQrEncoder @Inject constructor() : ProfileQrEncoder {
    override suspend fun encode(url: String): BitMatrix = withContext(Dispatchers.Default) {
        QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 1, 1, mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ))
    }
}

data class ProfileQrUiState(
    val profileId: Long,
    val label: String,
    val matrix: BitMatrix? = null,
    val error: String? = null,
)

data class ProfileExportRequest(val token: Long, val fileName: String, val launched: Boolean = false)

data class ProfileSharingUiState(
    val qr: ProfileQrUiState? = null,
    val exportRequest: ProfileExportRequest? = null,
    val exporting: Boolean = false,
)

@HiltViewModel
class ProfileSharingViewModel @Inject constructor(
    private val profiles: ProfileStore,
    private val reader: ProfileExportReader,
    private val writer: ProfileDocumentWriter,
    private val clipboard: ClipboardGateway,
    private val qrEncoder: ProfileQrEncoder,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileSharingUiState())
    val state = mutableState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = mutableMessages.asSharedFlow()
    private var qrJob: Job? = null
    private var qrGeneration = 0L
    private var exportToken = 0L
    private var pendingExport: ProfileExport? = null

    fun copyLink(profileId: Long) {
        try {
            clipboard.writeSensitiveText(subscription(profileId).url)
            mutableMessages.tryEmit("订阅链接已复制")
        } catch (failure: Exception) {
            mutableMessages.tryEmit(safeMessage(failure, "复制订阅链接失败，请重试"))
        }
    }

    fun showQr(profileId: Long) {
        closeQr()
        val profile = try { subscription(profileId) } catch (failure: Exception) {
            mutableMessages.tryEmit(safeMessage(failure, "无法读取订阅链接"))
            return
        }
        val generation = qrGeneration
        val loading = ProfileQrUiState(profile.id, profile.realLabel)
        mutableState.value = mutableState.value.copy(qr = loading)
        qrJob = viewModelScope.launch {
            val result = try {
                loading.copy(matrix = qrEncoder.encode(profile.url))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loading.copy(error = "无法生成二维码，链接可能过长，请复制订阅链接")
            }
            // 作者 long：编码可能晚于关闭或后台回调结束，旧结果不能重新显示含订阅凭据的二维码。
            if (generation == qrGeneration) mutableState.value = mutableState.value.copy(qr = result)
        }
    }

    fun closeQr() {
        qrGeneration++
        qrJob?.cancel()
        qrJob = null
        mutableState.value = mutableState.value.copy(qr = null)
    }

    fun beginExport(profileId: Long) {
        if (state.value.exporting) return
        mutableState.value = mutableState.value.copy(exporting = true)
        viewModelScope.launch {
            try {
                val profile = profiles.profiles.value.firstOrNull { it.id == profileId } ?: error("配置不存在")
                require(profile.type != ProfileType.CUSTOM) { "自定义配置不支持文件导出" }
                // 作者 long：冻结所点卡片的缓存，保存窗口期间切换当前配置不会改变这次导出的内容。
                pendingExport = reader.readExport(profileId)
                mutableState.value = mutableState.value.copy(
                    exportRequest = ProfileExportRequest(++exportToken, checkNotNull(pendingExport).fileName),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                clearExport()
                mutableMessages.emit(safeMessage(failure, "读取配置文件失败，请重试"))
            }
        }
    }

    fun claimExportRequest(token: Long): String? {
        val request = state.value.exportRequest ?: return null
        if (request.token != token || request.launched) return null
        // 作者 long：ViewModel 跨 Activity 重建保存已打开标记，防止系统保存窗口被重复启动。
        mutableState.value = mutableState.value.copy(exportRequest = request.copy(launched = true))
        return request.fileName
    }

    fun exportLaunchFailed() {
        clearExport()
        mutableMessages.tryEmit("无法打开保存窗口，请重试")
    }

    fun finishExport(destination: String?) {
        val snapshot = pendingExport ?: return
        if (state.value.exportRequest?.launched != true) return
        pendingExport = null
        mutableState.value = mutableState.value.copy(exportRequest = null)
        if (destination == null) {
            clearExport()
            return
        }
        viewModelScope.launch {
            try {
                writer.write(destination, snapshot.bytes)
                mutableMessages.emit("配置已导出")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 作者 long：系统或 Provider 的异常可能含 URI、路径或配置正文，只向页面返回受控提示。
                mutableMessages.emit("保存配置文件失败，请重试")
            } finally {
                clearExport()
            }
        }
    }

    private fun subscription(profileId: Long) =
        (profiles.profiles.value.firstOrNull { it.id == profileId } ?: error("配置不存在")).also {
            require(it.type == ProfileType.URL && it.url.isNotBlank()) { "此配置没有订阅链接" }
        }

    private fun clearExport() {
        pendingExport = null
        mutableState.value = mutableState.value.copy(exportRequest = null, exporting = false)
    }

    private fun safeMessage(failure: Exception, fallback: String) = failure.message?.takeIf {
        it in setOf("配置不存在", "此配置没有订阅链接", "自定义配置不支持文件导出",
            "配置文件不存在", "配置文件为空", "配置超过 8 MiB，暂不支持导出")
    } ?: fallback
}
