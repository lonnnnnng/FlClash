package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：用长标签和真实删除触点检查剩余宽度，避免固定标题比例把右侧值压成难以编辑的窄缝。 */
class ProviderOptionsFormGeometryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun leadingActionLeavesLongLabelAndEditableValueSeparateAcrossThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var value by mutableStateOf("45000")
        var removed = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                ProfileFormSection(Modifier.width(320.dp), contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    ProfileFormRow("很长的健康检查超时时间标签", Modifier.testTag("form_row"), leading = {
                        IconButton({ removed++ }, Modifier.testTag("form_remove")) { AppGlyphIcon(AppGlyphs.delete) }
                    }) {
                        androidx.compose.foundation.text.BasicTextField(value, { value = it }, singleLine = true,
                            modifier = Modifier.weight(1f).testTag("form_value"))
                    }
                }
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val label = compose.onNodeWithText("很长的健康检查超时时间标签").fetchSemanticsNode().boundsInRoot
            val input = compose.onNodeWithTag("form_value").fetchSemanticsNode().boundsInRoot
            val remove = compose.onNodeWithTag("form_remove").fetchSemanticsNode().boundsInRoot
            assertTrue("标题必须在独立删除触点之后", label.left >= remove.right - 1f)
            assertTrue("右侧输入至少保留与标签相同宽度", input.width >= label.width - 1f)
            assertTrue("标签和值不能重叠", input.left > label.right)
            compose.onNodeWithTag("form_value").performTextReplacement("1234567890")
            compose.onNodeWithTag("form_remove").performClick()
            compose.runOnIdle { assertEquals("1234567890", value) }
        }
        compose.runOnIdle { assertEquals(2, removed) }
    }
}
