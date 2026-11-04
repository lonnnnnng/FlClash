package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** 作者 long：拖动只在越过手势阈值后认领；单纯长按后抬手仍保留菜单，取消和切换配置不能触发最后悬停项。 */
@Stable
internal class AppMenuCarry {
    var position by mutableStateOf<Offset?>(null)
    var dragging by mutableStateOf(false)
    var release by mutableIntStateOf(0)
    private var origin: Offset? = null
    var armed by mutableStateOf(false)
    fun start(point: Offset) { origin = point; position = point; dragging = false; armed = true }
    fun move(point: Offset, slop: Float) {
        if (!armed) return
        if ((point - (origin ?: point)).getDistance() > slop) dragging = true
        position = point
    }
    fun finish() { if (armed && dragging) release++; armed = false }
    fun cancel() { armed = false; dragging = false; position = null }
}

internal data class AppMenuCarriedItem(val bounds: Rect, val enabled: Boolean, val submenu: Boolean, val action: () -> Unit)
internal class AppMenuCarryRegistry {
    val items = mutableStateMapOf<Any, AppMenuCarriedItem>()
    var hovered by mutableStateOf<Any?>(null)
}
internal val LocalAppMenuCarry = staticCompositionLocalOf<AppMenuCarryRegistry?> { null }
