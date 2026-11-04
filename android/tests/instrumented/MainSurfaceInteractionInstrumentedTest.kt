package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 作者 long：视觉反馈必须保留真实手势、语义和焦点边界，截图通过不能替代这些交互断言。 */
class MainSurfaceInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Before fun checkDeviceConfiguration() = assertPortraitDefaultFontConfiguration()

    @Test fun superellipseContoursMatchNativeFlutterIncludingAsymmetricAndZeroCorners() {
        val reference = javaClass.getResourceAsStream("/superellipse-flutter-reference.json")!!.bufferedReader().use { it.readText() }
        val cases = JsonParser.parseString(reference).asJsonObject.getAsJsonArray("cases")
        cases.forEachIndexed { index, item ->
            val data = item.asJsonObject; val c = data.getAsJsonArray("case").map { it.asFloat }
            val width = c[0].toInt(); val height = c[1].toInt()
            val shape = AppSuperellipseShape(c[2].dp, c[3].dp, c[4].dp, c[5].dp)
            val outline = shape.createOutline(Size(c[0], c[1]), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawPath(outline.path.asAndroidPath(), Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = false })
            val spans = data.getAsJsonArray("spans")
            repeat(height) { y ->
                val actual = (0 until width).filter { bitmap.getPixel(it, y) != 0 }
                val expected = spans[y].asJsonArray
                val first = actual.firstOrNull() ?: -1; val last = actual.lastOrNull() ?: -1
                // 作者 long：允许不同平台栅格器在边界处取整1像素；不能放宽到掩盖普通圆角或错误四角的差异。
                assertTrue("case=$index row=$y first=$first expected=${expected[0]}", abs(first - expected[0].asInt) <= 1)
                assertTrue("case=$index row=$y last=$last expected=${expected[1]}", abs(last - expected[1].asInt) <= 1)
            }
            val rtl = shape.createOutline(Size(c[0], c[1]), LayoutDirection.Rtl, Density(1f)) as Outline.Generic
            val mirrored = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(mirrored).drawPath(rtl.path.asAndroidPath(), Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = false })
            repeat(height) { y ->
                val actual = (0 until width).filter { mirrored.getPixel(it, y) != 0 }
                val expected = spans[y].asJsonArray
                val first = actual.firstOrNull() ?: -1; val last = actual.lastOrNull() ?: -1
                val expectedFirst = if (expected[1].asInt < 0) -1 else width - 1 - expected[1].asInt
                val expectedLast = if (expected[0].asInt < 0) -1 else width - 1 - expected[0].asInt
                assertTrue("RTL case=$index row=$y first=$first expected=$expectedFirst", abs(first - expectedFirst) <= 1)
                assertTrue("RTL case=$index row=$y last=$last expected=$expectedLast", abs(last - expectedLast) <= 1)
            }
            bitmap.recycle(); mirrored.recycle()
        }
    }

    @Test fun emojiInitialKeepsFullGraphemeAndRepairsBrokenSurrogate() {
        listOf("🇭🇰", "👩🏽‍💻", "e\u0301").forEach { initial -> assertEquals(initial, groupInitial("  $initial group")) }
        assertEquals("?", groupInitial("  "))
        val invalid = groupInitial("\uD83D broken")
        assertTrue(invalid.none { Character.isSurrogate(it) })
        compose.setContent { KClashTheme { Text(groupInitial("🇭🇰 Group"), Modifier.testTag("emoji_initial")) } }
        compose.onNodeWithText("🇭🇰").assertIsDisplayed()

    }

    @Test fun consumedDownKeepsElasticPressUntilReleaseAndDoesNotMoveHitBounds() {
        lateinit var press: ElasticPressState
        var clicks = 0
        compose.setContent { KClashTheme {
            press = rememberElasticPressState()
            Box(Modifier.size(120.dp, 70.dp).appElasticPress(state = press)
                .selectable(false, onClick = { clicks++ }).testTag("elastic_target"))
        } }
        val node = compose.onNodeWithTag("elastic_target")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(1f, press.lift.target, 0f); assertTrue(press.lift.value > .5f); assertEquals(0, clicks) }
        assertEquals(bounds, node.fetchSemanticsNode().boundsInRoot)
        recordNode("elastic_held", node)
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1400)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(0f, press.lift.value, .001f) }
    }

    @Test fun dockDragCommitsOnceCancelKeepsSelectionAndSemanticsStillActivate() {
        var selected by mutableStateOf(MainDestination.DASHBOARD); val calls = mutableListOf<MainDestination>()
        val destinations = listOf(MainDestination.DASHBOARD, MainDestination.PROFILES, MainDestination.TOOLS)
        compose.setContent { KClashTheme { MainNavigationDock(selected, destinations) { calls += it; selected = it } } }
        val dock = compose.onNodeWithTag("main_navigation_dock")
        dock.performTouchInput { down(Offset(width * .5f, height * .5f)); moveTo(Offset(width * .82f, height * .5f), 250) }
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        recordNode("dock_drag_held", dock)
        dock.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(MainDestination.TOOLS), calls) }
        dock.performTouchInput { down(Offset(width * .15f, height * .5f)); moveTo(center, 100); cancel() }
        compose.runOnIdle { assertEquals(1, calls.size); assertEquals(MainDestination.TOOLS, selected) }
        compose.onNodeWithTag("nav_profiles").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(2, calls.size) }
        // 作者 long：额外命中区由父节点受理，视觉Bar底部外4dp仍可选择；入口静态语义矩形不因按压增长。
        val profileBounds = compose.onNodeWithTag("nav_profiles").fetchSemanticsNode().boundsInRoot
        dock.performTouchInput { click(Offset(width * .15f, height + 4.dp.toPx())) }
        compose.runOnIdle { assertEquals(MainDestination.DASHBOARD, selected); assertEquals(3, calls.size) }
        assertEquals(profileBounds, compose.onNodeWithTag("nav_profiles").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("nav_dashboard").performTouchInput { click() }
        compose.runOnIdle { assertEquals(4, calls.size); assertEquals(MainDestination.DASHBOARD, calls.last()) }
    }

    @Test fun focusRingFollowsDescendantRatherThanSelectedState() {
        val requester = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent { KClashTheme {
            inputMode = LocalInputModeManager.current
            Box(Modifier.size(150.dp, 80.dp).appFocusRing(AppSuperellipseShape(20.dp)).testTag("focus_panel")) {
                Button({}, Modifier.padding(20.dp).focusRequester(requester).testTag("focus_button")) { Text("操作") }
            }
        } }
        val panel = compose.onNodeWithTag("focus_panel")
        val before = panel.captureToImage().toPixelMap()
        val oldMode = inputMode.inputMode
        try {
            compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); requester.requestFocus() }
            compose.onNodeWithTag("focus_button").assertIsFocused()
            val after = panel.captureToImage().toPixelMap()
            assertNotEquals(before[before.width / 2, 1], after[after.width / 2, 1])
            recordNode("focus_descendant", panel)
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(oldMode == InputMode.Touch)
            compose.waitUntil(5_000) { inputMode.inputMode == oldMode }
        }
    }

    @Test fun proxyRefreshDisablesExistingTabNodesAndShowsProgress() = checkBusyNodes(ProxyDisplayMode.TAB)
    @Test fun proxyRefreshDisablesExistingListNodesAndShowsProgress() = checkBusyNodes(ProxyDisplayMode.LIST)

    private fun recordNode(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val png = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng("surface_$name", png)
    }

    private fun checkBusyNodes(mode: ProxyDisplayMode) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val old = ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = mode))
            val group = ProxyGroupInfo("Shared", "Selector", "a", listOf("a", "b"), false)
            var selections = 0; var tests = 0
            compose.setContent { KClashTheme {
                ProxyGroupsCard(state = ProxyGroupsUiState(profileId = 7, groups = listOf(group), unfoldSet = setOf("Shared"), isLoading = true), runtimeState = CoreRuntimeStatus.STOPPED,
                    currentProfileId = 7, currentGroupName = "Shared", onRefresh = {}, onSelect = { _, _ -> selections++ },
                    onTestDelay = { _, _ -> tests++ }, onTestGroupDelay = {})
            } }
            compose.onNodeWithTag("proxy_groups_refreshing").assertIsDisplayed()
            val prefix = if (mode == ProxyDisplayMode.LIST) "proxy_list_node" else "proxy_node"
            compose.onNodeWithTag("${prefix}_Shared_a").assertIsNotEnabled()
            compose.onNodeWithTag("${prefix}_delay_Shared_a").assertIsNotEnabled()
            assertEquals(0, selections); assertEquals(0, tests)
        } finally { ProxyPageOptionsStore.save(context, old) }
    }
}
