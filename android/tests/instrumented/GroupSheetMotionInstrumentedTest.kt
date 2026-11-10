package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：从真实触点验证浮动命中区域、拖回取消/完成和自然高度，不修改系统字号或方向。 */
class GroupSheetMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun floatingShortFormGrowsAndShrinksWithinFlutterDetents() {
        assertPortraitDefaultFontConfiguration()
        var rows by mutableIntStateOf(1)
        var density = 1f
        compose.setContent { KClashTheme {
            density = LocalDensity.current.density
            val scroll = rememberScrollState()
            ProfileEditorSheet("root", scroll, false, {}, {}, {}) {
                val report = LocalEmbeddedProfilePage.current!!.reportBodyHeight
                ProfileEditorPage({}, { Text("内容适配") }, text = {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).onSizeChanged { report(it.height) }) {
                        repeat(rows) { Text("内容 $it", Modifier.height(56.dp)) }
                    }
                })
            }
        } }
        val viewport = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
        fun sheet() = compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot
        assertEquals(8f * density, sheet().left - viewport.left, 2f)
        assertEquals(8f * density, viewport.bottom - sheet().bottom, 2f)
        capture("form_floating_short")
        val short = sheet().height
        compose.runOnIdle { rows = 30 }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        assertEquals(viewport.width, sheet().width, 2f)
        assertEquals(viewport.height * .9f, sheet().height, 2f)
        capture("form_expanded_long")
        compose.runOnIdle { rows = 1 }
        assertEquals(short, sheet().height, 2f)
        assertEquals(8f * density, sheet().left - viewport.left, 2f)
        capture("form_shrunk_short")
        compose.runOnIdle { rows = 30 }
        assertEquals(viewport.height * .9f, sheet().height, 2f)
        assertEquals(viewport.width, sheet().width, 2f)
    }

    @Test fun horizontalBackCancelsOrCompletesOnceAndYamlRemainsGuarded() {
        assertPortraitDefaultFontConfiguration()
        var page by mutableStateOf("members")
        var calls = 0
        var rootClicks = 0
        compose.setContent { KClashTheme {
            val scroll = rememberScrollState()
            ProfileEditorSheet(page, scroll, false, {}, { page = "root"; calls++ }, {}) { shown ->
                ProfileEditorPage({ page = "root"; calls++ }, { Text(shown) }, text = {
                    Column(Modifier.fillMaxSize().testTag("drag_body_$shown")) {
                        Text("空白区域拖动")
                        if (shown == "root") androidx.compose.material3.Button({ rootClicks++ }, Modifier.testTag("root_after_drag")) { Text("继续编辑") }
                    }
                })
            }
        } }
        fun swipe(fraction: Float) {
            compose.onNodeWithTag("drag_body_$page").performTouchInput {
                down(Offset(width * .05f, height * .5f)); moveTo(Offset(width * fraction, height * .5f), delayMillis = 600)
                advanceEventTime(250); up()
            }
        }
        swipe(.28f)
        compose.onNodeWithTag("drag_body_members").assertIsDisplayed(); compose.runOnIdle { assertEquals(0, calls) }
        swipe(.85f)
        compose.onNodeWithTag("drag_body_root").assertIsDisplayed()
        compose.onNodeWithTag("root_after_drag").performClick()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(1, rootClicks); page = "options" }
        compose.onNodeWithTag("drag_body_options").performTouchInput {
            down(Offset(width * .05f, height * .5f)); moveTo(Offset(width * .8f, height * .5f), delayMillis = 500); cancel()
        }
        compose.onNodeWithTag("drag_body_options").assertIsDisplayed(); compose.runOnIdle { assertEquals(1, calls); page = "yaml" }
        swipe(.9f)
        compose.onNodeWithTag("drag_body_yaml").assertIsDisplayed(); compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun backgroundInterruptsDragWithoutLateNavigation() {
        assertPortraitDefaultFontConfiguration()
        var calls = 0
        compose.setContent { KClashTheme {
            val scroll = rememberScrollState()
            ProfileEditorSheet("members", scroll, false, {}, { calls++ }, {}) {
                ProfileEditorPage({ calls++ }, { Text("添加节点") }, text = { Text("拖回区域", Modifier.fillMaxSize().testTag("background_drag")) })
            }
        } }
        compose.onNodeWithTag("background_drag").performTouchInput {
            down(Offset(width * .05f, height * .5f)); moveTo(Offset(width * .8f, height * .5f), delayMillis = 500)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("background_drag").performTouchInput { cancel() }
        compose.onNodeWithTag("background_drag").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        val image = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
