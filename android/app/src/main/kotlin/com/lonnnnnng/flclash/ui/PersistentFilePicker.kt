package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.ProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileFileDocument
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class FilePickerRequest(
    val token: String = UUID.randomUUID().toString(),
    val owner: String,
    val phase: String = WAITING,
    val name: String? = null,
    val bytes: ByteArray? = null,
    val error: String? = null,
) {
    fun validate() {
        require(UUID.fromString(token).toString() == token && owner.isNotBlank() && owner.length <= 256)
        require(phase in setOf(WAITING, READING, RESULT, DONE, FAILED))
        require(bytes == null || bytes.size <= 8 * 1024 * 1024)
        if (phase == RESULT) require(name != null && bytes != null)
    }
    override fun toString() = "FilePickerRequest(phase=$phase, [REDACTED])"
    companion object {
        const val WAITING = "waiting"
        const val READING = "reading"
        const val RESULT = "result"
        const val DONE = "done"
        const val FAILED = "failed"
    }
}
internal data class FilePickerState(
    val ready: Boolean = false, val request: FilePickerRequest? = null,
    val processing: Boolean = false, val error: String? = null, val recoveryFailed: Boolean = false,
) {
    val busy: Boolean get() = processing || request?.phase in setOf(FilePickerRequest.WAITING, FilePickerRequest.READING)
}

/** 作者 long：请求身份先写密文再启动系统页面；回调先认领再读取，关闭、重复和旧窗口回调都不能重复交付。 */
internal class PersistentFilePicker(
    private val scope: CoroutineScope,
    private val store: ProfileDraftStore,
    private val failureMessage: String = "无法读取所选文件，请重新选择",
) {
    private val lock = Mutex()
    private val gson = Gson()
    private val restored = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow(FilePickerState())
    val state = mutable.asStateFlow()
    private var reading: Job? = null

    init { restore() }
    private fun restore() {
        mutable.value = mutable.value.copy(ready = false, recoveryFailed = false)
        scope.launch { lock.withLock {
            try {
                val saved = store.read()?.let { gson.fromJson(it.decodeToString(), FilePickerRequest::class.java) }
                saved?.validate()
                val next = if (saved?.phase == FilePickerRequest.READING)
                    saved.copy(phase = FilePickerRequest.FAILED, error = "上次读取已中断，请重新选择文件") else saved
                if (next !== saved) store.write(gson.toJson(next).toByteArray())
                mutable.value = FilePickerState(ready = true, request = next, error = next?.error)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = FilePickerState(recoveryFailed = true, error = "文件选择记录暂时无法恢复，原记录已保留，请重试") }
            finally { if (!restored.isCompleted) restored.complete(Unit) }
        } }
    }
    fun retryRecovery() { if (state.value.recoveryFailed) restore() }
    suspend fun awaitReady(): Boolean { restored.await(); return state.value.ready && !state.value.recoveryFailed }

    suspend fun begin(owner: String): String? {
        if (!awaitReady()) return null
        return lock.withLock {
            val current = state.value
            if (current.processing || current.request?.phase in setOf(FilePickerRequest.WAITING, FilePickerRequest.READING, FilePickerRequest.RESULT)) return@withLock null
            val request = FilePickerRequest(owner = owner)
            try { persist(request); request.token }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "无法暂存文件选择，请重试"); null }
        }
    }
    suspend fun reconcile(launcherToken: String?): Boolean {
        if (!awaitReady()) return false
        val pending = state.value.request ?: return false
        if (pending.phase != FilePickerRequest.WAITING) return false
        if (pending.token == launcherToken) return true
        // 作者 long：桌面重新打开没有对应系统launcher，磁盘里的孤立等待必须结束，不能锁死入口或接受未知回调。
        cancel(pending.token)
        return false
    }
    suspend fun cancel(token: String? = state.value.request?.token): Boolean {
        if (!awaitReady() || state.value.processing) return false
        val current = state.value.request
        if (current == null) return true
        if (token != current.token) return false
        reading?.cancel()
        return lock.withLock {
            if (state.value.processing || state.value.request?.token != token) return@withLock false
            try { persist(null); true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "无法取消文件选择，请重试"); false }
        }
    }
    fun receive(token: String?, uri: String?, load: suspend (String) -> ProfileFileDocument) {
        scope.launch {
            if (!awaitReady() || token == null) return@launch
            val accepted = lock.withLock {
                val pending = state.value.request ?: return@withLock false
                if (pending.token != token || pending.phase != FilePickerRequest.WAITING) return@withLock false
                try {
                    persist(if (uri == null) null else pending.copy(phase = FilePickerRequest.READING))
                    uri != null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = state.value.copy(error = "文件选择未能受理，请重试"); false }
            }
            if (!accepted) return@launch
            reading = currentCoroutineContext()[Job]
            try {
                EditorCommitProbe.checkpoint(EditorCommitWindow.FILE_PICKER_READING)
                val file = load(checkNotNull(uri))
                lock.withLock {
                    val pending = state.value.request
                    if (pending?.token != token || pending.phase != FilePickerRequest.READING) return@withLock
                    // 作者 long：结果先加密落盘；预览、图库提交或父组交付另行确认，不把读取成功当作业务已保存。
                    persist(pending.copy(phase = FilePickerRequest.RESULT, name = file.name, bytes = file.bytes.copyOf(), error = null))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { lock.withLock {
                state.value.request?.takeIf { it.token == token }?.let { request ->
                    try { persist(request.copy(phase = FilePickerRequest.FAILED, bytes = null, error = failureMessage)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutable.value = state.value.copy(request = request.copy(phase = FilePickerRequest.FAILED), error = failureMessage) }
                }
            } }
            finally { if (reading == currentCoroutineContext()[Job]) reading = null }
        }
    }

    suspend fun complete(token: String, accept: suspend (FilePickerRequest) -> Unit, afterAck: suspend () -> Unit = {}): Boolean {
        if (!awaitReady()) return false
        return lock.withLock {
            val request = state.value.request ?: return@withLock false
            if (request.token != token || request.phase !in setOf(FilePickerRequest.RESULT, FilePickerRequest.DONE)) return@withLock false
            mutable.value = state.value.copy(processing = true, error = null)
            try {
                withContext(NonCancellable) {
                    if (request.phase == FilePickerRequest.RESULT) {
                        accept(request)
                        // 作者 long：DONE先于清业务回执；任一清理窗口死亡都只继续清理，不能重放已受理的数据交付。
                        persist(request.copy(phase = FilePickerRequest.DONE, bytes = null, name = null))
                    }
                    afterAck()
                    persist(null)
                }
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "文件已读取，但处理尚未完成，请重试"); false }
            finally { mutable.value = state.value.copy(processing = false) }
        }
    }
    private suspend fun persist(request: FilePickerRequest?) {
        request?.validate()
        store.write(request?.let { gson.toJson(it).toByteArray() })
        mutable.value = state.value.copy(request = request, error = request?.error)
    }
}
