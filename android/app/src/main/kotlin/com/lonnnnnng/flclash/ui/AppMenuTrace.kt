package com.lonnnnnng.flclash.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import com.lonnnnnng.flclash.BuildConfig

/** 作者 long：标记实际测量和固有尺寸查询，诊断包才执行；切片是包容墙钟，不能与子切片叠加或冒充CPU。 */
internal inline fun <T> menuTrace(name: String, block: () -> T): T {
    if (!BuildConfig.MENU_TRACE) return block()
    android.os.Trace.beginSection(name)
    return try { block() } finally { android.os.Trace.endSection() }
}

private val menuTraceSequence = java.util.concurrent.atomic.AtomicInteger()

/** 作者 long：协程跨帧不能用线程同步slice圈定；仅诊断R8包记录完整窗口阶段，并在反转/取消时成对结束。 */
internal suspend fun <T> menuWindowTrace(name: String, block: suspend () -> T): T {
    if (!BuildConfig.MENU_TRACE || android.os.Build.VERSION.SDK_INT < 29) return block()
    val cookie = menuTraceSequence.incrementAndGet()
    android.os.Trace.beginAsyncSection(name, cookie)
    return try { block() } finally { android.os.Trace.endAsyncSection(name, cookie) }
}

internal fun Modifier.menuTraceMeasure(name: String): Modifier =
    if (BuildConfig.MENU_TRACE) then(MenuTraceMeasure(name)) else this

private data class MenuTraceMeasure(val name: String) : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: androidx.compose.ui.unit.Constraints): MeasureResult =
        menuTrace("kclash/$name/measure") {
            val child=measurable.measure(constraints)
            layout(child.width,child.height) { menuTrace("kclash/$name/place") { child.place(0,0) } }
        }
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable,height:Int) =
        menuTrace("kclash/$name/maxIntrinsicWidth") { measurable.maxIntrinsicWidth(height) }
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable,height:Int) =
        menuTrace("kclash/$name/minIntrinsicWidth") { measurable.minIntrinsicWidth(height) }
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable,width:Int) =
        menuTrace("kclash/$name/maxIntrinsicHeight") { measurable.maxIntrinsicHeight(width) }
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable,width:Int) =
        menuTrace("kclash/$name/minIntrinsicHeight") { measurable.minIntrinsicHeight(width) }
}
