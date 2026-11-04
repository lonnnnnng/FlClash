package com.lonnnnnng.flclash.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule

/** 作者 long：从真实语义树取得读屏动作，再在UI线程执行，与单尾柄的触摸拖动分开覆盖。 */
internal fun SemanticsNodeInteraction.performOrderAccessibilityAction(compose: ComposeTestRule, prefix: String) {
    val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label.startsWith(prefix) }
    compose.runOnIdle { check(action.action()) }
}
