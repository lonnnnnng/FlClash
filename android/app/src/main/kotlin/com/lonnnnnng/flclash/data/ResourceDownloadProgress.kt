package com.lonnnnnng.flclash.data

data class ResourceDownloadProgress(val downloadedBytes: Long = 0, val totalBytes: Long? = null, val attempt: Int = 1) {
    val fraction: Float? get() = totalBytes?.takeIf { it > 0 && it >= downloadedBytes }
        ?.let { (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

/** 作者 long：按实际读取字节展示进度，每200ms最多刷新一次，结束/异常时补末值，避免高速下载驱动逐块重组。 */
internal class ResourceProgressReporter(
    totalBytes: Long?,
    private val callback: (ResourceDownloadProgress) -> Unit,
    private val clock: () -> Long = System::nanoTime,
) {
    private val total = totalBytes?.takeIf { it > 0 }
    private var lastAt = clock()
    private var bytes = 0L
    init { callback(ResourceDownloadProgress(totalBytes = total)) }
    fun read(count: Int) {
        if (count <= 0) return
        bytes += count
        val now = clock()
        if (now - lastAt >= 200_000_000L) { lastAt = now; emit() }
    }
    fun finish() = emit()
    private fun emit() = callback(ResourceDownloadProgress(bytes, total?.takeIf { it >= bytes }))
}
