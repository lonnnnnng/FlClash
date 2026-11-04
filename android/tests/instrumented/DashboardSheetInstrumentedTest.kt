package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import com.lonnnnnng.flclash.data.Profile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 作者 long：真实Dialog内验证短内容重新测量、长正文与停靠衔接，不能用直接调用关闭回调替代平台返回和拖动。 */
class DashboardSheetInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun shortContentRefitsAfterGrowthAndShrinkInBothThemes() {
        var rows by mutableIntStateOf(1)
        var dark by mutableStateOf(false)
        var open by mutableStateOf(true)
        var closed = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            if (open) DashboardModalSheet("详情验收", "fit_sheet", "fit_close", onDismiss = { open = false; closed++ }) {
                repeat(rows) { Text("第 ${it + 1} 项详情", Modifier.fillMaxWidth().testTag("fit_row_$it")) }
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; rows = 1 }
            compose.onNodeWithTag("fit_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "适应内容"))
            val short = compose.onNodeWithTag("fit_sheet").fetchSemanticsNode().boundsInRoot.height
            val viewport = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot.height
            assertTrue(short < viewport * 9f / 16f)
            captureSheetWindow("dashboard_sheet_short_$theme")
            compose.runOnIdle { rows = 80 }
            compose.onNodeWithTag("fit_sheet").performSemanticsAction(SemanticsActions.Expand) { it() }
            val expanded = compose.onNodeWithTag("fit_sheet").fetchSemanticsNode().boundsInRoot.height
            assertEquals(viewport * 0.9f, expanded, 2f)
            compose.onNodeWithTag("fit_row_79").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("fit_close").assertIsDisplayed()
            captureSheetWindow("dashboard_sheet_long_$theme")
            compose.runOnIdle { rows = 1 }
            compose.onNodeWithTag("fit_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "适应内容"))
            assertEquals(short, compose.onNodeWithTag("fit_sheet").fetchSemanticsNode().boundsInRoot.height, 2f)
        }
        pressBack()
        compose.onNodeWithTag("fit_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun longBodyMiddleDragKeepsSheetAndFixedActionsReachable() {
        var open by mutableStateOf(true)
        compose.setContent { KClashTheme {
            if (open) DashboardModalSheet("长详情", "long_sheet", "long_close", onDismiss = { open = false }) {
                repeat(120) { Text("第 ${it + 1} 项", Modifier.fillMaxWidth().testTag("long_row_$it")) }
            }
        } }
        compose.onNodeWithTag("long_sheet").performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNodeWithTag("long_row_80").performScrollTo()
        val before = compose.onNodeWithTag("long_sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("dashboard_sheet_scroll").performTouchInput { swipeDown() }
        compose.onNodeWithTag("long_sheet").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("long_sheet").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("long_close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("long_sheet").assertDoesNotExist()
    }

    @Test fun longProfilePickerKeepsDetentsAndSelectsLastRowOnce() {
        val profiles = (1L..100L).map { Profile.custom(it, "第 $it 份配置 · 长名称与列表选择验收") }
        var current by mutableLongStateOf(1L)
        var selections = 0
        compose.setContent { KClashTheme {
            Column(Modifier.fillMaxSize()) {
                DashboardProfileCard(profiles.first { it.id == current }, profiles, current,
                    onShowProfiles = {}, onAddProfile = {}, onSelectProfile = { current = it; selections++ })
            }
        } }
        compose.onNodeWithTag("dashboard_profile_switch").performClick()
        compose.onNodeWithTag("dashboard_profile_picker").performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(99)
        compose.onNodeWithTag("dashboard_profile_option_100").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_profile_picker_close").assertIsDisplayed()
        captureSheetWindow("dashboard_sheet_profile_long")
        compose.onNodeWithTag("dashboard_profile_option_100").performClick()
        compose.onNodeWithTag("dashboard_profile_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(100L, current); assertEquals(1, selections) }
    }
}

class DashboardSheetHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<DashboardTestActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun serviceSystemBackReturnsFromQualityAndManagementBeforeClosing() {
        compose.activityRule.scenario.onActivity { it.showServiceExit() }
        compose.onNodeWithTag("dashboard_service_card").performClick()
        compose.onNodeWithTag("service_exit_google").performClick()
        compose.activityRule.scenario.onActivity { it.finishServiceQuality(true) }
        compose.onNodeWithTag("service_detail_sheet").performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNodeWithTag("ip_quality_hide").performScrollTo()
        compose.onNodeWithTag("service_manage_back").assertIsDisplayed()
        captureSheetWindow("dashboard_sheet_service_quality")
        pressBack()
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
        compose.onNodeWithTag("service_manage").performClick()
        compose.onNodeWithTag("service_order_list").performScrollToIndex(8)
        compose.onNodeWithTag("service_manage_back").assertIsDisplayed()
        captureSheetWindow("dashboard_sheet_service_manage")
        pressBack()
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("service_detail_sheet").assertDoesNotExist()
    }

    @Test fun profilePickerFitsTwoRowsAndNativeBackPreservesSelection() {
        compose.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        for (dark in listOf(true, false)) {
            compose.activityRule.scenario.onActivity { it.setDarkTheme(dark) }
            compose.onNodeWithTag("dashboard_profile_switch").performScrollTo().performClick()
            compose.onNodeWithTag("dashboard_profile_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "适应内容"))
            compose.onNodeWithTag("dashboard_profile_option_101").assertIsSelected()
            compose.onNodeWithTag("dashboard_profile_option_102").assertIsDisplayed()
            captureSheetWindow("dashboard_sheet_profile_picker_$dark")
            pressBack()
            compose.onNodeWithTag("dashboard_profile_picker").assertDoesNotExist()
        }
        compose.onNodeWithTag("dashboard_profile_switch").performClick()
        compose.onNodeWithTag("dashboard_profile_option_101").assertIsSelected()
        compose.onNodeWithTag("dashboard_profile_option_102").performClick()
        compose.onNodeWithTag("dashboard_profile_picker").assertDoesNotExist()
        compose.onNodeWithText("固定自定义配置").assertIsDisplayed()
    }
}

private fun captureSheetWindow(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    android.os.SystemClock.sleep(150)
    val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val output = ByteArrayOutputStream()
    screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
    screen.recycle()
    recordVisualPng(name, output.toByteArray())
}
