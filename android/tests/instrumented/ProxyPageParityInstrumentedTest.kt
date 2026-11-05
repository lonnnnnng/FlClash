package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** 作者 long：同一轮覆盖代理完整页面的布局和操作，固定状态不访问订阅或启动真实核心。 */
class ProxyPageParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previousOptions: ProxyPageOptions

    @Before fun setup() {
        assertPortraitDefaultFontConfiguration()
        previousOptions = ProxyPageOptionsStore.load(compose.activity)
        ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
    }

    @After fun restore() { ProxyPageOptionsStore.save(compose.activity, previousOptions) }

    @Test fun pagerDragCancelAndDistantTabTapKeepContinuousFramesAndSettledSelection() {
        val names = (0..12).map { "分组$it" }
        val groups = names.map { ProxyGroupInfo(it, "Selector", "node-a", listOf("node-a", "node-b"), false) }
        var current by mutableStateOf(names.first())
        val settled = mutableListOf<String>()
        compose.setContent { KClashTheme {
            ProxyGroupsCard(state = ProxyGroupsUiState(groups = groups), runtimeState = CoreRuntimeStatus.STOPPED,
                currentGroupName = current, onSelectGroup = { current = it; settled += it },
                onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("proxy_node_分组0_node-a").assertIsDisplayed()
        val pager = compose.onNodeWithTag("proxy_groups_pager")
        compose.mainClock.autoAdvance = false
        pager.performTouchInput { down(Offset(width * .85f, height * .5f)); moveTo(Offset(width * .6f, height * .5f), 160) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { org.junit.Assert.assertEquals("分组0", current) }
        capture("proxy_pager_partial_drag")
        pager.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("proxy_group_tab_分组0").assertIsSelected()
        compose.runOnIdle { org.junit.Assert.assertEquals(listOf("分组0"), settled.distinct()) }

        pager.performTouchInput { swipeLeft(durationMillis = 400) }
        compose.onNodeWithTag("proxy_group_tab_分组1").assertIsSelected()
        compose.runOnIdle { org.junit.Assert.assertEquals("分组1", current) }
        compose.onNodeWithTag("proxy_group_more").performClick()
        compose.onNodeWithTag("proxy_group_menu_分组10").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("proxy_group_menu_分组10").performClick()
        compose.mainClock.advanceTimeBy(64)
        val targetMembers = compose.onAllNodesWithTag("proxy_group_members_分组10")
        var elapsedMillis = 0
        while (targetMembers.fetchSemanticsNodes().isEmpty() && elapsedMillis < 500) {
            // 作者 long：Pager 只预组合邻近页，远端目标页进入可视范围后才有成员节点可用于采集运动帧。
            compose.mainClock.advanceTimeBy(16)
            elapsedMillis += 16
        }
        check(targetMembers.fetchSemanticsNodes().isNotEmpty()) {
            "目标组进入 Pager 可视范围后应创建成员页面，等待=${elapsedMillis}ms"
        }
        val positions = mutableListOf<Float>()
        repeat(4) { frame ->
            compose.mainClock.advanceTimeBy(48)
            val member = targetMembers.onFirst().fetchSemanticsNode()
            positions += member.layoutInfo.coordinates.positionInRoot().x
            capture("proxy_pager_tap_frame_$frame")
        }
        org.junit.Assert.assertTrue("目标页必须持续向左收敛", positions.zipWithNext().all { (a, b) -> b <= a } && positions.first() > positions.last())
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("proxy_group_tab_分组10").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("proxy_node_分组10_node-a").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals("分组10", current) }
        val strip = compose.onNodeWithTag("proxy_group_tabs").fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithTag("proxy_group_tab_分组10").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals("中间组应居中显示", strip.center.x, tab.center.x, 3f)
        capture("proxy_pager_tab_centered")
    }

    @Test fun tabAndListAllCardSizesRenderBothThemesAndKeepDelayIndependent() {
        var dark by mutableStateOf(false)
        var expanded by mutableStateOf(setOf("节点选择"))
        var selected by mutableStateOf("香港节点")
        var tested by mutableStateOf(0)
        val group = ProxyGroupInfo("节点选择", "Selector", selected,
            listOf("香港节点", "日本节点", "这是一个很长的节点名称用来检查两行省略", "自动选择"), false)
        val nested = ProxyGroupInfo("自动选择", "URLTest", "香港节点", listOf("香港节点"), false)
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                val state = ProxyGroupsUiState(groups = listOf(group.copy(now = selected), nested),
                    unfoldSet = expanded, proxyTypes = mapOf("香港节点" to "Trojan", "日本节点" to "Vless"),
                    delayResults = mapOf(
                        ProxyDelayKey(group.name, "香港节点", CoreBridge.DEFAULT_TEST_URL) to ProxyDelayUiState(value = 100),
                        ProxyDelayKey(group.name, "日本节点", CoreBridge.DEFAULT_TEST_URL) to ProxyDelayUiState(value = 600),
                    ))
                ProxyGroupsCard(modifier = Modifier.fillMaxSize(), state = state,
                    runtimeState = CoreRuntimeStatus.STOPPED, currentGroupName = group.name,
                    onRefresh = {}, onToggleGroup = { name -> expanded = if (name in expanded) expanded - name else expanded + name },
                    onSelect = { _, node -> selected = node }, onTestDelay = { _, _ -> tested++ }, onTestGroupDelay = {})
            }
        }
        // 作者 long：对可见测速目标执行真实触摸，确保压缩高度后仍不会触发父卡片选择。
        compose.onNodeWithTag("proxy_node_delay_节点选择_日本节点", true).performTouchInput { click() }
        compose.runOnIdle { check(tested == 1 && selected == "香港节点") }
        for (layout in listOf("TAB", "LIST")) {
            if (layout == "LIST") option("display_mode", "proxy_display_mode_LIST")
            for (size in ProxyNodeCardSize.entries) {
                option("card_size", "proxy_card_size_option_" + size.name)
                for (theme in listOf("light", "dark")) {
                    compose.runOnIdle { dark = theme == "dark" }
                    val tag = if (layout == "TAB") "proxy_node_节点选择_日本节点" else "proxy_list_node_节点选择_日本节点"
                    compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(when (size) {
                        ProxyNodeCardSize.COMPACT -> 66.dp
                        ProxyNodeCardSize.STANDARD -> 86.dp
                        ProxyNodeCardSize.LOOSE -> 105.dp
                    })
                    val currentTag = if (layout == "TAB") {
                        "proxy_node_节点选择_香港节点"
                    } else {
                        "proxy_list_node_节点选择_香港节点"
                    }
                    compose.onNodeWithTag(currentTag).assertProxyInUse(compose)
                    compose.onNodeWithTag(tag).assertProxyNotInUse(compose)
                    val longName = "这是一个很长的节点名称用来检查两行省略"
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNode(hasText(longName) and hasAnyAncestor(hasTestTag(
                        (if (layout == "TAB") "proxy_node_节点选择_" else "proxy_list_node_节点选择_") + longName)), true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    check(layouts.single().lineCount == if (size == ProxyNodeCardSize.COMPACT) 1 else 2) {
                        "layout=" + layout + " size=" + size + " lineCount=" + layouts.single().lineCount + " bounds=" + layouts.single().size
                    }
                    capture("proxy_page_" + layout.lowercase() + "_" + size.name.lowercase() + "_" + theme)
                }
            }
        }
        // 作者 long：List 节点卡片与 Tab 使用同一套当前节点状态，点选后徽标必须唯一地迁移到新节点。
        val currentHongKong = compose.onNodeWithTag("proxy_list_node_节点选择_香港节点")
        val currentJapan = compose.onNodeWithTag("proxy_list_node_节点选择_日本节点")
        currentHongKong.assertProxyInUse(compose)
        currentJapan.assertProxyNotInUse(compose)
        capture("proxy_list_current_hongkong_dark")
        currentJapan.performClick()
        compose.runOnIdle { check(selected == "日本节点") }
        currentHongKong.assertProxyNotInUse(compose)
        currentJapan.assertProxyInUse(compose)
        capture("proxy_list_current_japan_dark")
        compose.runOnIdle { dark = false }
        capture("proxy_list_current_japan_light")
        compose.onNodeWithTag("proxy_list_group_header_节点选择").performClick()
        compose.onNodeWithTag("proxy_list_group_locate_节点选择").assertDoesNotExist()
        compose.onNodeWithTag("proxy_list_group_batch_节点选择").assertDoesNotExist()
        capture("proxy_page_list_collapsed_dark")
        compose.onNodeWithTag("proxy_list_group_header_节点选择").performClick()
        compose.onNodeWithTag("proxy_list_group_locate_节点选择").assertIsDisplayed()
        compose.onNodeWithTag("proxy_list_group_batch_节点选择").assertIsDisplayed()
    }

    @Test fun delayFocusRingAndKeyboardActivationStayIndependentFromNodeSelection() {
        var selected by mutableStateOf("香港节点")
        var tested by mutableStateOf(0)
        lateinit var inputMode: InputModeManager
        val group = ProxyGroupInfo("键盘焦点", "Selector", selected, listOf("香港节点", "日本节点"), false)
        compose.setContent {
            KClashTheme {
                inputMode = LocalInputModeManager.current
                ProxyGroupsCard(
                    state = ProxyGroupsUiState(
                        groups = listOf(group),
                        unfoldSet = setOf(group.name),
                        proxyTypes = mapOf("香港节点" to "Trojan", "日本节点" to "Vless"),
                    ),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    currentGroupName = group.name,
                    onRefresh = {},
                    onToggleGroup = {},
                    onSelect = { _, name -> selected = name },
                    onTestDelay = { _, _ -> tested++ },
                    onTestGroupDelay = {},
                )
            }
        }
        val card = compose.onNodeWithTag("proxy_node_键盘焦点_日本节点")
        val delay = compose.onNodeWithTag("proxy_node_delay_键盘焦点_日本节点", useUnmergedTree = true)
        val before = card.captureToImage().toPixelMap()
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        val delayBounds = delay.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        delay.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        delay.assertIsFocused()
        val after = card.captureToImage().toPixelMap()
        val left = ((delayBounds.left - cardBounds.left) - 3f).roundToInt().coerceAtLeast(0)
        val top = ((delayBounds.top - cardBounds.top) - 3f).roundToInt().coerceAtLeast(0)
        val right = ((delayBounds.right - cardBounds.left) + 3f).roundToInt().coerceAtMost(before.width)
        val bottom = ((delayBounds.bottom - cardBounds.top) + 3f).roundToInt().coerceAtMost(before.height)
        var changedPixels = 0
        for (y in top until bottom) for (x in left until right) {
            if (before[x, y] != after[x, y]) changedPixels++
        }
        check(changedPixels > 0) { "测速触点获得焦点后应出现局部焦点环，changedPixels=$changedPixels" }
        // 作者 long：显式采集聚焦前后卡片，方便回看局部焦点环而不把选中态误当焦点态。
        capture("proxy_delay_focus_settled")
        delay.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        compose.runOnIdle {
            assertEquals(1, tested)
            // 作者 long：测速触点的键盘激活只能启动测速，不能冒泡为代理节点选择。
            assertEquals("香港节点", selected)
        }
    }

    @Test fun delayStatusUsesFadeTransitionWithoutChangingNodeSelection() {
        var selected by mutableStateOf("香港节点")
        var delayState by mutableStateOf<ProxyDelayUiState?>(null)
        val group = ProxyGroupInfo("测速动效", "Selector", selected, listOf("香港节点", "日本节点"), false)
        compose.setContent {
            KClashTheme {
                ProxyGroupsCard(
                    state = ProxyGroupsUiState(
                        groups = listOf(group),
                        unfoldSet = setOf(group.name),
                        delayResults = delayState?.let {
                            mapOf(ProxyDelayKey(group.name, "日本节点", CoreBridge.DEFAULT_TEST_URL) to it)
                        } ?: emptyMap(),
                    ),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    currentGroupName = group.name,
                    onRefresh = {},
                    onToggleGroup = {},
                    onSelect = { _, name -> selected = name },
                    onTestDelay = { _, _ -> },
                    onTestGroupDelay = {},
                )
            }
        }
        val delay = compose.onNodeWithTag("proxy_node_delay_测速动效_日本节点", useUnmergedTree = true)
        delay.assertIsDisplayed()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = delay.captureToImage().asAndroidBitmap()
        compose.runOnIdle { delayState = ProxyDelayUiState(isTesting = true) }
        compose.mainClock.advanceTimeBy(60)
        val middle = delay.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(300)
        val settled = delay.captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = true
        check(!before.sameAs(middle)) { "测速状态切换应产生淡入淡出中间帧" }
        check(!middle.sameAs(settled)) { "测速状态切换的中间帧不能直接等于终帧" }
        compose.runOnIdle { assertEquals("香港节点", selected) }
    }

    @Test fun overflowingGroupsCanBePickedAndSearchClearsWithoutLosingSelection() {
        val names = (0..12).map { "分组" + it }
        var current by mutableStateOf(names.first())
        val groups = names.map { ProxyGroupInfo(it, "Selector", "node-a", listOf("node-a", "node-b"), false) }
        compose.setContent { KClashTheme {
            ProxyGroupsCard(state = ProxyGroupsUiState(groups = groups), runtimeState = CoreRuntimeStatus.STOPPED,
                currentGroupName = current, onSelectGroup = { current = it },
                onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("proxy_group_more").performClick()
        compose.onNodeWithTag("proxy_group_menu_分组12").performScrollTo().performClick()
        compose.onNodeWithTag("proxy_group_tab_分组12").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        compose.onNodeWithTag("proxy_node_分组12_node-a").assertDoesNotExist()
        compose.onNodeWithTag("proxy_tools").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_batch_分组12").assertIsDisplayed()
        compose.onNodeWithTag("proxy_search_clear").performClick()
        compose.onNodeWithTag("proxy_node_分组12_node-a").assertIsDisplayed().assertProxyInUse(compose)
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_tab_分组12").assertIsSelected()
    }

    @Test fun listGroupHeaderKeepsRaisedLayerWhileLongContentScrolls() {
        val group = ProxyGroupInfo(
            "长列表",
            "Selector",
            "节点-00",
            (0..24).map { "节点-${it.toString().padStart(2, '0')}" },
            false,
        )
        var expanded by mutableStateOf(emptySet<String>())
        compose.setContent { KClashTheme {
            ProxyGroupsCard(
                modifier = Modifier.fillMaxSize(),
                state = ProxyGroupsUiState(groups = listOf(group), unfoldSet = expanded),
                runtimeState = CoreRuntimeStatus.STOPPED,
                currentGroupName = group.name,
                onRefresh = {},
                onToggleGroup = { name -> expanded = if (name in expanded) expanded - name else expanded + name },
                onSelect = { _, _ -> },
                onTestDelay = { _, _ -> },
                onTestGroupDelay = {},
            )
        } }
        option("display_mode", "proxy_display_mode_LIST")
        // 作者 long：先让显示模式切换完成首帧，再冻结时钟采集展开中间帧，避免点击时语义树尚未建立。
        compose.waitForIdle()
        compose.onNodeWithTag("proxy_list_group_header_长列表").assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("proxy_list_group_header_长列表").performClick()
        // 作者 long：展开状态先经过一帧布局，LazyColumn 才会创建首行；之后再采集真实动画中间态。
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("proxy_list_node_长列表_节点-00").assertExists()
        // 作者 long：展开首帧和稳定帧必须不同，证明组头圆角与节点行没有瞬时跳变。
        val expandingFrame = capturePixels("proxy_list_group_expand_mid")
        compose.mainClock.advanceTimeBy(900)
        val expandedFrame = capturePixels("proxy_list_group_expand_settled")
        check(!expandingFrame.contentEquals(expandedFrame)) { "List 组展开不能在首帧直接完成" }
        compose.mainClock.autoAdvance = true
        val list = compose.onNodeWithTag("proxy_groups_list")
        val header = compose.onNodeWithTag("proxy_list_group_header_长列表")
        val restingListTop = list.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
        val restingHeaderTop = header.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
        capture("proxy_list_group_header_resting")
        // 作者 long：stickyHeader 到达视口边界前仍会随内容移动；要多滚一段并确认后续滚动不再改变组头位置。
        val restingOffset = restingHeaderTop - restingListTop
        var pinnedOffset: Float? = null
        var latestOffset = restingOffset
        var latestListTop = restingListTop
        var latestHeaderTop = restingHeaderTop
        for (attempt in 0 until 6) {
            list.performTouchInput {
                swipeUp(startY = height * 0.85f, endY = height * 0.15f, durationMillis = 500)
            }
            compose.waitForIdle()
            val firstListTop = list.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
            val firstHeaderTop = header.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
            val firstOffset = firstHeaderTop - firstListTop
            latestOffset = firstOffset
            latestListTop = firstListTop
            latestHeaderTop = firstHeaderTop
            if (firstOffset < restingOffset - 1f) {
                list.performTouchInput {
                    swipeUp(startY = height * 0.85f, endY = height * 0.15f, durationMillis = 500)
                }
                compose.waitForIdle()
                val nextListTop = list.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
                val nextHeaderTop = header.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
                val nextOffset = nextHeaderTop - nextListTop
                latestOffset = nextOffset
                latestListTop = nextListTop
                latestHeaderTop = nextHeaderTop
                if (kotlin.math.abs(nextOffset - firstOffset) <= 1f) {
                    pinnedOffset = nextOffset
                    break
                }
            }
        }
        header.assertIsDisplayed()
        check(pinnedOffset != null) {
            "长列表滚动后组头应进入并保持 pinned 状态，restingOffset=$restingOffset latestOffset=$latestOffset, " +
                "listTop=$restingListTop->$latestListTop headerTop=$restingHeaderTop->$latestHeaderTop"
        }
        capture("proxy_list_group_header_lifted")
        compose.mainClock.autoAdvance = false
        header.performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("proxy_list_node_长列表_节点-00").assertDoesNotExist()
    }

    private fun option(menu: String, value: String) {
        compose.onNodeWithTag("proxy_tools").performClick()
        compose.onNodeWithTag("proxy_tools_" + menu).performClick()
        compose.onNodeWithTag(value).performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("proxy_groups_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        recordVisualPng(name, png)
    }

    private fun capturePixels(name: String): ByteArray {
        val bitmap = compose.onNodeWithTag("proxy_groups_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        recordVisualPng(name, png)
        return png
    }
}
