package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** 作者 long：查找面板收回时保留正文和查询拥有者，布局连续让出空间；离场内容不能再接收替换或保存触点。 */
@Composable
internal fun SearchOptionsTransition(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible,
        enter = expandVertically(tween(300, easing = SheetPageEasing), expandFrom = Alignment.Top) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(220, easing = SheetPageEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120))) {
        Column(Modifier.fillMaxWidth().pointerInput(visible) {
            if (!visible) awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }, content = content)
    }
}
