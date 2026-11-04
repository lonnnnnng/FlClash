package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput

/** 作者 long：真实按住后跨一行拖动，只有松手才提交，避免排序回归绕过正式手势链路。 */
internal fun SemanticsNodeInteraction.performOrderTouchMove(direction: Int) {
    require(direction == -1 || direction == 1)
    val height = fetchSemanticsNode().boundsInRoot.height
    performTouchInput {
        down(center)
        advanceEventTime(800)
        moveBy(Offset(0f, direction * height), delayMillis = 200)
        up()
    }
}
