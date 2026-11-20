package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：一次走完整页草稿流程，验证逐项保存、有效YAML返回保存和失败草稿保护。 */
class ProfileMoreEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var dark by mutableStateOf(false)
    private var section by mutableStateOf("ntp")
    private var state by mutableStateOf(ProfileMoreEditorUiState(Profile.custom(920, "更多设置")))
    private var savedNtp: ProfileNtpOverrides? = null
    private var savedSniffer: ProfileSnifferOverrides? = null
    private var exits = 0
    private var reject = false
    private var holdSave = false
    private var pendingSave: Pair<ProfileNtpOverrides, ProfileSnifferOverrides>? = null
    private var saveCalls = 0

    @Test fun successfulAdditionsCollapseIndependentlyAndFailureKeepsCandidate() {
        render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.runOnIdle { holdSave = true }
        fun height(key: String) = compose.onNodeWithTag("more_add_transition_$key").fetchSemanticsNode().boundsInRoot.height
        val fullHeight = height("server")
        compose.onNodeWithTag("more_add_server").performClick()
        compose.onNodeWithTag("more_add_server").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saveCalls); completeHeldSave(false) }
        compose.onNodeWithTag("more_add_server").assertIsEnabled()
        assertEquals(fullHeight, height("server"), 1f)
        compose.onNodeWithTag("more_add_server").performClick()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { completeHeldSave(true) }
        compose.mainClock.advanceTimeBy(112)
        val firstHeight = height("server")
        assertTrue("成功回执后条目应逐渐收起", firstHeight > 0f && firstHeight < fullHeight)
        compose.onNodeWithTag("more_add_server").assertIsNotEnabled()
        compose.onNodeWithTag("more_add_port").performClick()
        compose.runOnIdle { completeHeldSave(true) }
        compose.mainClock.advanceTimeBy(80)
        assertTrue("第二次添加不能重置第一条的退场进度", height("server") < firstHeight)
        assertTrue("后添加的条目应保留独立时间轴", height("port") > height("server"))
        captureEntries("more_add_two_leaving")
        compose.mainClock.advanceTimeBy(144)
        compose.onNodeWithTag("more_add_transition_server").assertDoesNotExist()
        compose.onNodeWithTag("more_add_transition_port").assertExists()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag("more_add_transition_port").assertDoesNotExist()
        compose.onNodeWithTag("more_add_interval").assertIsEnabled()
        compose.onNodeWithTag("profile_more_add_done").assertExists()
        compose.runOnIdle { assertEquals(3, saveCalls); assertEquals(123, savedNtp?.port) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun changingSearchDuringExitDoesNotRetainAnEmptyProtocolGroup() {
        section = "sniffer"; render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("HTTP")
        awaitVisibleIme()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val headerHeight = compose.onNodeWithTag("more_add_header_true").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("more_add_sniff.HTTP").performClick()
        compose.runOnIdle { assertEquals(1, saveCalls); assertNotNull(savedSniffer?.sniff?.get("HTTP")) }
        // 作者 long：先让保存回执触发重组和动画首帧，再测80ms；不能把异步排程尚未开始误判为标题不收起。
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        val leavingHeaderHeight = compose.onNodeWithTag("more_add_header_true").fetchSemanticsNode().boundsInRoot.height
        assertTrue("分组标题须随最后条目连续收起：$headerHeight → $leavingHeaderHeight", leavingHeaderHeight > 0f && leavingHeaderHeight < headerHeight)
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("TLS")
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("more_add_sniff.TLS").assertIsDisplayed()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("HTTP")
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("profile_more_add_empty").assertIsDisplayed()
        compose.onNodeWithTag("more_add_header_true").assertDoesNotExist()
        captureEntries("more_add_query_after_exit")
        compose.runOnIdle { assertEquals(1, saveCalls); assertNotNull(savedSniffer?.sniff?.get("HTTP")) }
        compose.mainClock.autoAdvance = true
    }

    private fun captureEntries(name: String) {
        // 作者 long：冻结测试时钟后直接采集列表，不能调用终态截图助手推进或等待掉过渡帧。
        val bitmap = compose.onNodeWithTag("profile_more_available_fields").captureToImage().asAndroidBitmap()
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }

    @Test fun pendingDestinationIgnoresBackAndOutsideThenKeepsFailedChoiceForRetry() {
        section = "sniffer"; render(); add("sniff.HTTP")
        compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_protocol_destination").performClick()
        compose.runOnIdle { holdSave = true }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        compose.onNodeWithTag("more_protocol_destination_false").assertIsNotEnabled()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        val before = compose.onNodeWithTag("app_popup_menu").captureToImage().asAndroidBitmap()
        val calls = saveCalls
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("app_popup_outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, 5f)) }
        compose.mainClock.advanceTimeBy(180)
        val during = compose.onNodeWithTag("app_popup_menu").captureToImage().asAndroidBitmap()
        listOf("before" to before, "during" to during).forEach { (phase, bitmap) ->
            recordVisualPng("more_pending_destination_$phase", ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray())
        }
        assertEquals(before.width, during.width); assertEquals(before.height, during.height)
        // 作者 long：被点击的第三行涟漪由Android独立时钟推进；比较未点击的上半部仍须逐像素一致，回源缩放/模糊不能被容差放过。
        val upperBefore = android.graphics.Bitmap.createBitmap(before, 0, 0, before.width, before.height / 2)
        val upperDuring = android.graphics.Bitmap.createBitmap(during, 0, 0, during.width, during.height / 2)
        assertTrue("保存中返回/外点不得先启动回源", upperBefore.sameAs(upperDuring))
        compose.runOnIdle { assertEquals(calls, saveCalls); completeHeldSave(false) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("more_protocol_error").assertIsDisplayed()
        compose.runOnIdle { holdSave = false }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        waitGone("more_protocol_destination_false")
        compose.runOnIdle { assertEquals(false, savedSniffer?.sniff?.get("HTTP")?.overrideDestination) }
    }

    @Test fun lastAdditionWaitsForAsyncReceiptAndFailedSaveCanRetry() {
        render(); add(*ntpFields.dropLast(1).map { it.key }.toTypedArray())
        compose.onNodeWithTag("profile_more_add").performClick()
        compose.runOnIdle { holdSave = true }
        compose.onNodeWithTag("more_add_write-to-system").performClick()
        compose.onNodeWithTag("profile_more_add_done").assertIsNotEnabled()
        compose.onNodeWithTag("more_add_write-to-system").assertIsNotEnabled()
        compose.runOnIdle { assertNull(savedNtp?.writeToSystem); completeHeldSave(false) }
        compose.onNodeWithTag("more_add_write-to-system").assertIsEnabled().performClick()
        compose.onNodeWithTag("profile_more_add_done").assertExists()
        compose.runOnIdle { completeHeldSave(true) }
        waitGone("profile_more_add_done")
        compose.runOnIdle { assertEquals(false, savedNtp?.writeToSystem) }
    }

    private fun completeHeldSave(success: Boolean) {
        val (ntp, sniffer) = checkNotNull(pendingSave); pendingSave = null
        if (success) {
            savedNtp = ntp; savedSniffer = sniffer
            state = state.copy(ntp = ntp, sniffer = sniffer, isSaving = false, revision = state.revision + 1, error = null)
        } else state = state.copy(isSaving = false, error = "保存失败，请重试")
    }

    @Test fun addSearchKeepsQueryAndLastSuccessfulChoiceClosesOnlyAfterSave() {
        section = "sniffer"; render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("not-a-field")
        compose.onNodeWithTag("profile_more_add_empty").assertIsDisplayed()
        capture("more_sniffer_search_empty")
        compose.onNodeWithTag("profile_more_add_search_clear").performClick()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("HTTP")
        compose.onNodeWithTag("more_add_sniff.HTTP").performClick()
        compose.onNodeWithTag("profile_more_add_empty").assertIsDisplayed()
        compose.onNodeWithTag("profile_more_add_search").assertTextContains("HTTP")
        compose.onNodeWithTag("profile_more_add_search_clear").performClick()
        compose.onNodeWithTag("profile_more_add_done").performClick()
        // 作者 long：测试宿主直接切分区前等旧Sheet完成关闭，避免其迟到onDismiss关闭新NTP面板。
        waitGone("profile_more_add_done")
        compose.runOnIdle { section = "ntp" }
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_add_enable").fetchSemanticsNodes().isNotEmpty() }
        ntpFields.dropLast(1).forEach { compose.onNodeWithTag("more_add_${it.key}").performScrollTo().performClick() }
        compose.runOnIdle { reject = true }
        compose.onNodeWithTag("more_add_write-to-system").performScrollTo().performClick()
        compose.onNodeWithTag("profile_more_add_done").assertIsDisplayed()
        compose.onNode(hasText("保存失败，请重试") and !hasTestTag("profile_more_editor_error")).assertIsDisplayed()
        compose.runOnIdle { reject = false }
        compose.onNodeWithTag("more_add_write-to-system").performClick()
        waitGone("profile_more_add_done")
        compose.runOnIdle { assertEquals(false, savedNtp?.writeToSystem) }
    }

    @Test fun addDockCancelKeepsSheetAndDraftWithoutSaving() {
        section = "sniffer"; render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("profile_more_add_search").performClick().performTextReplacement("HTTP")
        val candidate = compose.onNodeWithTag("more_add_sniff.HTTP").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val dock = compose.onNodeWithTag("profile_more_add_search_dock").fetchSemanticsNode().boundsInWindow
        assertTrue("候选须位于下沿搜索上方", candidate.bottom <= dock.top)
        capture("more_dock_query")
        compose.onNodeWithTag("profile_more_add_search_cancel").performClick()
        compose.onNodeWithTag("profile_more_add_done").assertIsDisplayed()
        assertEquals("", compose.onNodeWithTag("profile_more_add_search").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        compose.runOnIdle { assertEquals(0, saveCalls); assertEquals(0, exits) }
        capture("more_dock_cancel")
    }

    @Test fun vis055NtpShortAddPanelLight() = ntpShortAddPanel(false)
    @Test fun vis055NtpShortAddPanelDark() = ntpShortAddPanel(true)

    private fun ntpShortAddPanel(darkTheme: Boolean) {
        dark = darkTheme
        section = "ntp"
        render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("profile_more_add_search").assertDoesNotExist()
        compose.onNodeWithTag("more_add_header_true").assertDoesNotExist()
        ntpFields.forEach { compose.onNodeWithTag("more_add_${it.key}").assertExists() }
        capture("vis055_ntp_${darkTheme}_six")
        fun sheetHeight() = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.height
        val viewportHeight = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInWindow.height
        compose.onNodeWithTag("connection_detail_handle").performClick()
        compose.waitForIdle()
        assertEquals("真实Flutter NTP列表上滑仍展开至90%", viewportHeight * .9f, sheetHeight(), 4f)
        capture("vis055_ntp_${darkTheme}_expanded")
        listOf("server", "port", "enable").forEach {
            compose.onNodeWithTag("more_add_$it").performScrollTo().performClick()
            compose.onNodeWithTag("more_add_$it").assertDoesNotExist()
        }
        val shortHeight = sheetHeight()
        assertEquals("添加条目后保持原展开意图", viewportHeight * .9f, shortHeight, 4f)
        compose.onNodeWithTag("connection_detail_handle").performTouchInput {
            swipe(center, center.copy(y = center.y - 500f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertEquals("展开后的剩余候选不改变停靠", shortHeight, sheetHeight(), 4f)
        capture("vis055_ntp_${darkTheme}_short")
        compose.onNodeWithTag("profile_more_add_done").performClick()
        compose.onNodeWithTag("profile_more_add_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals("time.apple.com", savedNtp?.server); assertEquals(3, saveCalls); assertEquals(0, exits) }
        compose.onNodeWithTag("profile_more_add").performClick()
        listOf("interval", "dialer-proxy").forEach { compose.onNodeWithTag("more_add_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("more_add_write-to-system").assertIsDisplayed()
        capture("vis055_ntp_${darkTheme}_last")
        compose.onNodeWithTag("more_add_write-to-system").performClick()
        compose.onNodeWithTag("profile_more_add_sheet").assertDoesNotExist()
        compose.onNodeWithTag("profile_more_add").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(6, saveCalls); assertEquals(0, exits) }
        capture("vis055_ntp_${darkTheme}_complete")
    }

    @Test fun vis054SnifferAddPanelGroupsSearchAndSelectsProtocol() {
        section = "sniffer"
        dark = true
        state = ProfileMoreEditorUiState(Profile.custom(54054, "Sniffer 参考夹具"))
        render()

        compose.onNodeWithTag("profile_more_empty").assertExists()
        capture("vis054_sniffer_empty")

        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("more_add_header_false").assertExists()
        compose.onNodeWithTag("more_add_header_true").assertExists()
        compose.onNodeWithTag("more_add_enable").assertIsDisplayed()
        compose.onNodeWithTag("more_add_sniff.HTTP").assertExists()
        compose.onNodeWithTag("more_add_sniff.TLS").assertExists()
        capture("vis054_sniffer_add_all")
        compose.onNodeWithTag("more_add_sniff.HTTP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more_add_sniff.TLS").performScrollTo().assertIsDisplayed()
        capture("vis054_sniffer_add_protocols")
        val partialSheetTop = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.top

        compose.onNodeWithTag("profile_more_add_search").performClick()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("HTTP")
        val imeWindow = awaitVisibleIme()
        val imeSheetTop = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.top
        assertTrue("键盘出现后添加面板应上移并露出候选", imeSheetTop < partialSheetTop)
        compose.onNodeWithTag("more_add_header_false").assertDoesNotExist()
        compose.onNodeWithTag("more_add_header_true").assertIsDisplayed()
        compose.onNodeWithTag("more_add_sniff.HTTP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more_add_sniff.TLS").assertDoesNotExist()
        compose.onNodeWithTag("more_add_sniff.QUIC").assertDoesNotExist()
        capture("vis054_sniffer_search_http")

        compose.onNodeWithTag("profile_more_add_search_clear").performClick()
        capture("vis054_sniffer_search_cleared_ime")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        awaitHiddenIme(imeWindow)
        capture("vis054_sniffer_search_cleared_ime_hidden")
        val restoredSheetTop = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.top
        assertEquals("键盘收起后应返回原停靠位置", partialSheetTop, restoredSheetTop, 40f)
        compose.onNodeWithTag("more_add_header_false").performScrollTo().assertIsDisplayed()
        capture("vis054_sniffer_search_cleared_options")
        compose.onNodeWithTag("more_add_header_true").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more_add_sniff.TLS").performScrollTo().assertIsDisplayed()
        capture("vis054_sniffer_search_cleared_protocols")

        compose.onNodeWithTag("more_add_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_add_sniff.HTTP").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, saveCalls)
            assertNotNull(savedSniffer?.sniff?.get("HTTP"))
        }
        capture("vis054_sniffer_http_added")
        compose.onNodeWithTag("profile_more_add_done").performClick()
        compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_protocol_ports").assertIsDisplayed()
        compose.onNodeWithTag("more_protocol_destination").assertIsDisplayed()
        capture("vis054_sniffer_http_page")
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun fieldAndYamlValidationDismissTheirOwnImeAndKeepFailedDraft() {
        render(); add("port")
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performClick().performTextReplacement("0")
        val valueWindow = awaitVisibleIme()
        compose.onNodeWithTag("more_value_confirm").performClick()
        awaitHiddenIme(valueWindow)
        compose.onNodeWithTag("more_value_error").assertIsDisplayed()
        compose.onNodeWithTag("more_port_value").assertTextContains("0")
        capture("more_value_error_ime_hidden")
        compose.onNodeWithTag("more_value_back").performClick(); waitGone("more_value_dialog")
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performClick().performTextReplacement("port: 0")
        val yamlWindow = awaitVisibleIme()
        compose.onNodeWithTag("more_yaml_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        awaitHiddenIme(yamlWindow)
        compose.onNodeWithTag("more_yaml_error").assertIsDisplayed()
        compose.onNodeWithTag("more_yaml_content").assertTextContains("port: 0")
        capture("more_yaml_error_ime_hidden")
        compose.runOnIdle { assertEquals(123, savedNtp?.port) }
    }

    private fun ime(): Pair<android.os.IBinder, Boolean>? {
        var state: Pair<android.os.IBinder, Boolean>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(androidx.core.view.ViewCompat::getRootWindowInsets)
            if (view != null && insets != null) state = view.windowToken to insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        return state
    }

    private fun awaitVisibleIme(): android.os.IBinder {
        compose.waitUntil(5_000) { ime()?.second == true }
        return checkNotNull(ime()).first
    }

    private fun awaitHiddenIme(token: android.os.IBinder) {
        // 作者 long：只接受刚才实际显示键盘的同一Dialog回报隐藏，切窗或空Insets不能当成功。
        compose.waitUntil(5_000) { ime()?.let { it.first == token && !it.second } == true }
    }

    @Test fun ntpAddsDefaultsValidatesEditsAndSavesOnlySelectedFields() {
        render()
        compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
        add("enable", "server", "port", "interval", "dialer-proxy", "write-to-system")
        compose.onNodeWithTag("more_enable_value").performClick()
        compose.onNodeWithTag("more_open_port").performScrollTo().performClick()
        compose.onNodeWithTag("more_port_value").performTextReplacement("65536")
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("more_value_error").assertTextContains("端口必须", substring = true)
        compose.onNodeWithTag("more_port_value").performTextReplacement("124")
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("more_remove_interval").performScrollTo().performClick()
        compose.onNodeWithTag("profile_more_editor_save").assertDoesNotExist()
        compose.runOnIdle {
            val saved = checkNotNull(savedNtp)
            assertEquals(true, saved.enable); assertEquals(124, saved.port)
            assertEquals("time.apple.com", saved.server); assertNull(saved.interval)
            assertEquals("", saved.dialerProxy); assertEquals(false, saved.writeToSystem)
        }
    }

    @Test fun snifferYamlValidationProtocolInheritanceAndExplicitEmptyStayDistinct() {
        section = "sniffer"; render()
        yaml("sniffer: {enable: true}")
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("more_yaml_error").assertIsDisplayed()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("enable: false\nskip-domain: []\nskip-src-address: ['geoip:cn', 'rule-set:local']\nsniff:\n  http:\n    ports: [80, 8000-9000]\n    override-destination: false")
        compose.onNodeWithTag("more_yaml_back").performClick()
        waitGone("more_yaml_back")
        compose.onNodeWithTag("more_open_sniff.HTTP").performScrollTo().performClick()
        compose.onNodeWithTag("more_protocol_ports").performClick()
        compose.onNodeWithText("8000-9000").assertIsDisplayed()
        compose.onNodeWithTag("more_value_back").performClick()
        compose.onNodeWithTag("more_protocol_destination").performClick()
        compose.onNodeWithTag("more_protocol_destination_default").performClick()
        waitGone("more_protocol_destination_default")
        compose.onNodeWithTag("more_protocol_back").performClick()
        compose.runOnIdle {
            val saved = checkNotNull(savedSniffer)
            assertEquals(false, saved.enable); assertNull(saved.skipDomain)
            assertEquals(listOf("geoip:cn", "rule-set:local"), saved.skipSrcAddress)
            assertNull(saved.sniff?.get("HTTP")?.overrideDestination)
            assertEquals(listOf("80", "8000-9000"), saved.sniff?.get("HTTP")?.ports)
        }
        compose.onNodeWithTag("more_remove_sniff.HTTP").performScrollTo().performClick()
        compose.runOnIdle { assertNull(savedSniffer?.sniff) }
        yaml("sniff: {}")
        waitGone("more_yaml_back")
        compose.runOnIdle { assertNull(savedSniffer?.sniff) }
        compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
    }

    @Test fun returningFromFieldYamlAndParentProtectsUnsavedDrafts() {
        render(); add("port")
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performTextReplacement("124")
        compose.onNodeWithTag("more_value_back").performClick()
        waitGone("more_value_dialog")
        compose.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()
        compose.onNodeWithTag("more_edit_port").assertTextContains("123", substring = true)
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("port: 999")
        compose.onNodeWithTag("more_yaml_back").performClick()
        waitGone("more_yaml_back")
        compose.onNodeWithTag("more_edit_port").assertTextContains("999", substring = true)
        compose.runOnIdle { assertEquals(999, savedNtp?.port) }
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("port: 0")
        compose.onNodeWithTag("more_yaml_back").performClick()
        compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("more_yaml_content").assertTextContains("port: 0")
        compose.onNodeWithTag("more_yaml_back").performClick()
        compose.onNodeWithText("放弃", substring = false).performClick()
        compose.onNodeWithTag("profile_more_reset").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("more_field_port").assertExists()
        compose.onNodeWithTag("profile_more_editor_back").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, exits); assertEquals(999, savedNtp?.port) }
    }

    @Test fun failedFieldSaveRetainsInputAndRetriesWithoutParentSave() {
        render(); add("port")
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performTextReplacement("124")
        compose.runOnIdle { reject = true }
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("more_value_error").assertTextContains("保存失败，请重试")
        compose.onNodeWithTag("more_port_value").assertTextContains("124")
        compose.runOnIdle { assertEquals(123, savedNtp?.port); reject = false }
        capture("more_field_save_failure")
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.runOnIdle { assertEquals(124, savedNtp?.port) }
        compose.onNodeWithTag("profile_more_editor_save").assertDoesNotExist()
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performTextClearance()
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
    }

    @Test fun protocolPortsAndDestinationSaveIndependentlyAndEmptyPortsKeepProtocol() {
        section = "sniffer"; render(); add("sniff.HTTP")
        compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_protocol_ports").assertTextContains("默认", substring = true)
        compose.onNodeWithTag("more_protocol_destination").performClick()
        compose.runOnIdle { reject = true }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        compose.onNodeWithTag("more_protocol_error").assertTextContains("保存失败，请重试")
        compose.runOnIdle { assertNull(savedSniffer?.sniff?.get("HTTP")?.overrideDestination); reject = false }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        waitGone("more_protocol_destination_false")
        compose.onNodeWithTag("more_protocol_ports").performClick()
        openQuickPorts()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("8080\n8000-9000")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_destination").assertTextContains("已关闭", substring = true)
        compose.runOnIdle { assertEquals(listOf("8080", "8000-9000"), savedSniffer?.sniff?.get("HTTP")?.ports) }
        compose.onNodeWithTag("more_protocol_ports").performClick()
        openQuickPorts()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextClearance()
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_ports").assertTextContains("默认", substring = true)
        compose.runOnIdle {
            assertEquals(emptyList<String>(), savedSniffer?.sniff?.get("HTTP")?.ports)
            assertEquals(false, savedSniffer?.sniff?.get("HTTP")?.overrideDestination)
        }
    }

    @Test fun morePagesRenderEmptyAddFieldsProtocolYamlAndErrorsAcrossThemes() {
        render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; section = "ntp"; state = ProfileMoreEditorUiState(Profile.custom(if (dark) 922 else 921, "更多设置")) }
            capture("more_ntp_empty_$theme")
            compose.onNodeWithTag("profile_more_empty_add").performClick()
            capture("more_ntp_add_$theme")
            for (key in listOf("enable", "server", "port")) compose.onNodeWithTag("more_add_$key").performScrollTo().performClick()
            compose.onNodeWithTag("profile_more_add_done").performClick()
            capture("more_ntp_fields_$theme")
            compose.onNodeWithTag("more_open_port").performClick()
            compose.onNodeWithTag("more_port_value").performTextReplacement("0")
            compose.onNodeWithTag("more_value_confirm").performClick()
            capture("more_ntp_error_$theme")
            compose.onNodeWithTag("more_value_back").performClick(); waitGone("more_value_dialog")
            compose.runOnIdle { section = "sniffer" }
            capture("more_sniffer_empty_$theme")
            compose.onNodeWithTag("profile_more_empty_add").performClick()
            capture("more_sniffer_add_$theme")
            compose.onNodeWithTag("profile_more_add_done").performClick()
            add("enable", "sniff.HTTP")
            compose.onNodeWithTag("more_open_sniff.HTTP").performScrollTo().performClick()
            capture("more_sniffer_protocol_$theme")
            compose.onNodeWithTag("more_protocol_destination").performClick()
            capture("more_sniffer_destination_$theme")
            compose.onNodeWithTag("more_protocol_destination_default").performClick()
            waitGone("more_protocol_destination_default")
            compose.onNodeWithTag("more_protocol_ports").performClick()
            capture("more_sniffer_ports_$theme")
            compose.onNodeWithTag("more_value_back").performClick()
            compose.onNodeWithTag("more_protocol_back").performClick()
            compose.onNodeWithTag("profile_more_yaml").performClick()
            capture("more_sniffer_yaml_$theme")
            compose.onNodeWithTag("more_yaml_back").performClick()
        }
    }

    @Test fun emptyStateAndConfiguredRowsCrossfadeWithoutLeavingOutgoingActionsEnabled() {
        section = "sniffer"
        state = ProfileMoreEditorUiState(
            Profile.custom(923, "更多设置"),
            sniffer = ProfileSnifferOverrides(enable = false),
        )
        render()
        try {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("more_remove_enable").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("more_field_enable").assertExists()
            compose.onNodeWithTag("profile_more_empty").assertExists()
            compose.onNodeWithTag("more_remove_enable").assertIsNotEnabled()
            compose.onNodeWithTag("profile_more_reset").assertIsNotEnabled()
            capture("vis043_sniffer_exit_80ms")
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
            compose.onNodeWithTag("more_field_enable").assertDoesNotExist()
            capture("vis043_sniffer_empty")

            compose.runOnIdle { state = state.copy(sniffer = ProfileSnifferOverrides(enable = false)) }
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("profile_more_empty").assertExists()
            compose.onNodeWithTag("more_field_enable").assertExists()
            compose.onNodeWithTag("profile_more_empty_add").assertIsNotEnabled()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("profile_more_empty").assertDoesNotExist()
            compose.onNodeWithTag("more_field_enable").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun openQuickPorts() {
        compose.onNodeWithTag("more_list_add_menu").performClick()
        compose.onNodeWithTag("more_list_quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_list_quick_back").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun vis056SnifferListDraftValidationReorderAndReceipt() {
        section = "sniffer"; dark = true; render(); add("sniff.HTTP")
        compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_protocol_ports").performClick()
        compose.onNodeWithText("暂无数据").assertIsDisplayed()
        capture("vis056_ports_empty")
        fun addPort(value: String) {
            compose.onNodeWithTag("more_list_add_menu").performClick()
            compose.onNodeWithTag("more_list_add").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_list_entry_input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("more_list_entry_input").performClick().performTextReplacement(value)
            compose.onNodeWithTag("more_list_entry_confirm").performClick()
        }
        addPort("0")
        compose.onNodeWithTag("more_list_input_error").assertIsDisplayed()
        compose.onNodeWithTag("more_list_entry_input").performTextReplacement("8080")
        compose.onNodeWithTag("more_list_entry_confirm").performClick()
        addPort("8000-9000")
        compose.runOnIdle { assertEquals(1, saveCalls) }
        capture("vis056_ports_draft")
        val first = compose.onNodeWithTag("more_list_order_0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("more_list_order_1").fetchSemanticsNode().boundsInRoot
        val listTop = compose.onNodeWithTag("more_list_order_list").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("more_list_order_list").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(center.x, first.center.y - listTop))
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, second.height * 1.2f), delayMillis = 300)
            up()
        }
        assertTrue("拖动后第二项应排到第一项之前",
            compose.onNodeWithText("8000-9000").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithText("8080").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("more_value_back").performClick()
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("more_list_select_8080").performClick()
        compose.onNodeWithTag("more_list_delete").performClick()
        compose.onNodeWithTag("more_list_action_confirm").performClick()
        compose.onNodeWithText("8080").assertDoesNotExist()
        compose.onNodeWithTag("more_list_reset").performClick()
        compose.onNodeWithTag("more_list_action_confirm").performClick()
        compose.onNodeWithText("暂无数据").assertIsDisplayed()
        openQuickPorts()
        compose.onNodeWithTag("sniff_HTTP_ports").performClick().performTextReplacement("8080\n8000-9000")
        awaitVisibleIme()
        capture("vis056_ports_quick_ime")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.runOnIdle { holdSave = true }
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("more_value_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("more_value_back").performClick()
        compose.onNodeWithTag("more_list_saving").assertIsDisplayed()
        compose.runOnIdle { completeHeldSave(false) }
        compose.onNodeWithTag("more_value_error").assertIsDisplayed()
        capture("vis056_ports_save_error")
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.runOnIdle { completeHeldSave(true) }
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_ports").assertTextContains("2 项", substring = true)
        compose.runOnIdle { assertEquals(listOf("8080", "8000-9000"), savedSniffer?.sniff?.get("HTTP")?.ports) }
        capture("vis056_ports_saved")
    }

    @Test fun vis056ListReceiptAfterStateRestorationClosesSavedDraft() {
        val restore = StateRestorationTester(compose)
        var initial by mutableStateOf(com.google.gson.JsonParser.parseString("{}").asJsonObject)
        var saving by mutableStateOf(false)
        var open by mutableStateOf(true)
        var pending: com.google.gson.JsonObject? = null
        var calls = 0
        restore.setContent { KClashTheme {
            if (open) MoreListEditor(snifferFields.first { it.key == "sniff.HTTP" }, initial,
                com.google.gson.JsonParser.parseString("{\"sniff\":{\"HTTP\":{}}}").asJsonObject,
                saving, null, { open = false }) { pending = it?.asJsonObject; calls++; saving = true }
        } }
        openQuickPorts()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("8080")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("more_list_saving").assertIsDisplayed()
        compose.runOnIdle { initial = checkNotNull(pending); saving = false }
        compose.waitUntil(5_000) { !open }
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun vis056DomainListDraftCancelAndClearRestoreInheritance() {
        section = "sniffer"; render()
        compose.onNodeWithTag("profile_more_empty_add").performClick()
        compose.onNodeWithTag("profile_more_add_search").performTextReplacement("force-domain")
        compose.onNodeWithTag("more_add_force-domain").performClick()
        compose.onNodeWithTag("profile_more_add_done").performClick()
        compose.onNodeWithTag("more_open_force-domain").performClick()
        compose.onNodeWithTag("more_list_add_menu").performClick()
        compose.onNodeWithTag("more_list_quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_force-domain_value").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("more_force-domain_value").performTextReplacement("example.test")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), savedSniffer?.forceDomain) }
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.runOnIdle { assertEquals(listOf("example.test"), savedSniffer?.forceDomain) }
        compose.onNodeWithTag("more_open_force-domain").performClick()
        compose.onNodeWithTag("more_list_select_example.test").performClick()
        compose.onNodeWithTag("more_list_delete").performClick()
        compose.onNodeWithTag("more_list_action_confirm").performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
        compose.runOnIdle { assertNull(savedSniffer?.forceDomain) }
    }

    @Test fun vis056DestinationReceiptAfterStateRestorationClosesMenu() {
        val restore = StateRestorationTester(compose)
        section = "sniffer"
        state = state.copy(sniffer = ProfileSnifferOverrides(sniff = mapOf("HTTP" to com.lonnnnnng.flclash.service.ProfileSniffProtocolOverrides())))
        restore.setContent { KClashTheme {
            ProfileMoreEditor(state, "sniffer", "", {}, { ntp, sniffer ->
                pendingSave = ntp to sniffer; state = state.copy(isSaving = true); saveCalls++
            })
        } }
        compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
        compose.onNodeWithTag("more_protocol_destination").performClick()
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("more_protocol_destination_false").assertIsNotEnabled()
        compose.runOnIdle { completeHeldSave(true) }
        waitGone("more_protocol_destination_false")
        compose.onNodeWithTag("more_protocol_destination").assertTextContains("已关闭", substring = true)
        compose.runOnIdle { assertEquals(1, saveCalls) }
    }

    @Test fun vis057EntryToolbarAndQuickEditorAcrossThemes() {
        section = "sniffer"; render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; state = ProfileMoreEditorUiState(Profile.custom(957, "列表编辑")) }
            add("sniff.HTTP")
            compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
            compose.onNodeWithTag("more_protocol_ports").performClick()
            compose.onNodeWithTag("more_list_add_menu").performClick()
            capture("vis057_add_menu_$theme")
            compose.onNodeWithTag("more_list_add").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_list_entry_input").fetchSemanticsNodes().isNotEmpty() }
            capture("vis057_entry_$theme")
            compose.onNodeWithTag("more_list_entry_confirm").performClick()
            compose.onNodeWithTag("more_list_input_error").assertTextEquals("值不能为空")
            compose.onNodeWithTag("more_list_entry_input").performClick().performTextReplacement("8080\n9000")
            compose.onNodeWithTag("more_list_input_error").assertTextEquals("值只能是一项")
            compose.onNodeWithTag("more_list_entry_input").performTextReplacement("8080")
            awaitVisibleIme(); capture("vis057_entry_ime_$theme")
            compose.onNodeWithTag("more_list_entry_confirm").performClick()
            compose.onNodeWithTag("more_list_add_menu").performClick()
            compose.onNodeWithTag("more_list_add").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_list_entry_input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("more_list_entry_input").performTextReplacement("8080")
            compose.onNodeWithTag("more_list_entry_confirm").performClick()
            compose.onNodeWithTag("more_list_input_error").assertTextEquals("值当前已存在")
            compose.onNodeWithTag("more_list_entry_input").performTextReplacement("8000-9000")
            compose.onNodeWithTag("more_list_entry_confirm").performClick()
            capture("vis057_list_$theme")
            compose.onNodeWithTag("more_list_select_8080").performClick()
            capture("vis057_selection_$theme")
            compose.onNodeWithTag("more_value_back").performClick()
            openQuickPorts()
            capture("vis057_quick_$theme")
            compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("443\n8443")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            capture("vis057_quick_menu_$theme")
            compose.onNodeWithTag("more_list_quick_undo").performClick()
        waitGone("more_list_quick_undo")
            compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("8080\n8000-9000")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_redo").performClick()
        waitGone("more_list_quick_redo")
            compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("443\n8443")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_sizes").performClick()
            compose.onNodeWithTag("more_list_quick_size_20").performClick()
            waitGone("more_list_quick_size_20")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_wrap").performClick()
        waitGone("more_list_quick_wrap")
            compose.onNodeWithTag("profile_top_search_open").performClick()
            compose.onNodeWithTag("profile_top_search_input").performTextReplacement("8443")
            compose.onNodeWithTag("sniff_HTTP_ports").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, androidx.compose.ui.text.TextRange(4, 8)))
            capture("vis057_quick_search_$theme")
            compose.onNodeWithTag("profile_top_search_input").performTextReplacement("not-found")
            compose.onNodeWithTag("more_list_quick_no_match").assertIsDisplayed()
            compose.onNodeWithTag("profile_top_search_close").performClick()
            compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("0")
            compose.onNodeWithTag("more_list_quick_back").performClick()
            compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
            compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("0")
            compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("443\n8443")
            compose.onNodeWithTag("more_list_quick_back").performClick()
            compose.runOnIdle { assertEquals(emptyList<String>(), savedSniffer?.sniff?.get("HTTP")?.ports.orEmpty()) }
            compose.onNodeWithTag("more_value_confirm").performClick()
            waitGone("more_value_confirm")
            compose.runOnIdle { assertEquals(listOf("443", "8443"), savedSniffer?.sniff?.get("HTTP")?.ports) }
            compose.onNodeWithTag("more_protocol_back").performClick()
        }
    }

    @Test fun vis057QuickDraftHistoryAndQuerySurviveRestoration() {
        val restore = StateRestorationTester(compose)
        var text by androidx.compose.runtime.mutableStateOf("8080")
        var applied = ""
        restore.setContent { KClashTheme {
            MoreListQuickEditor("端口", text, "quick_text", { text = it }, {}, { applied = it })
        } }
        compose.onNodeWithTag("quick_text").performTextReplacement("443\n8443")
        compose.onNodeWithTag("profile_top_search_open").performClick()
        compose.onNodeWithTag("profile_top_search_input").performTextReplacement("8443")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile_top_search_input").assertTextContains("8443")
        compose.onNodeWithTag("quick_text").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, androidx.compose.ui.text.TextRange(4, 8)))
        compose.onNodeWithTag("profile_top_search_close").performClick()
        compose.onNodeWithTag("more_list_quick_menu").performClick()
        compose.onNodeWithTag("more_list_quick_undo").performClick()
        waitGone("more_list_quick_undo")
        compose.onNodeWithTag("quick_text").assertTextContains("8080")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.runOnIdle { assertEquals("", applied) }
    }

    @Test fun vis057LongUnwrappedSearchScrollsToMatchAndBackAppliesDraft() {
        var text by mutableStateOf("8080")
        var applied = ""
        compose.setContent { KClashTheme { MoreListQuickEditor("列表", text, "quick_long", { text = it }, {}, { applied = it }) } }
        compose.onNodeWithTag("quick_long").performTextReplacement("prefix-".repeat(70) + "needle")
        compose.onNodeWithTag("more_list_quick_menu").performClick()
        compose.onNodeWithTag("more_list_quick_wrap").performClick()
        waitGone("more_list_quick_wrap")
        compose.onNodeWithTag("profile_top_search_open").performClick()
        compose.onNodeWithTag("profile_top_search_input").performTextReplacement("needle")
        compose.waitForIdle()
        val range = compose.onNodeWithTag("quick_long").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("末尾查找结果应滚入横向视口", range.value() > 0f)
        capture("vis057_long_search")
        compose.onNodeWithTag("profile_top_search_close").performClick()
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.runOnIdle { assertEquals(text, applied) }
    }

    @Test fun vis058NestedQuickMenuBackOutsideAndDelayedAction() {
        var content by mutableStateOf("8080\n8000-9000")
        var changes = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            MoreListQuickEditor("端口", content, "vis058_content", { content = it; changes++ }, {}, {})
        } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_undo").assertIsNotEnabled()
            capture("vis058_menu_root_$theme")
            compose.onNodeWithTag("more_list_quick_sizes").performClick()
            capture("vis058_menu_size_$theme")
            compose.onNodeWithTag("more_list_quick_size_18").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitGone("more_list_quick_size_18")
            compose.onNodeWithTag("more_list_quick_sizes").assertIsDisplayed()
            compose.onNodeWithTag("more_list_quick_sizes").performClick()
            compose.onNodeWithTag("more_list_quick_size_back").performClick()
            waitGone("more_list_quick_size_18")
            compose.onNodeWithTag("more_list_quick_sizes").performClick()
            compose.onNodeWithTag("more_list_quick_size_18").performClick()
            waitGone("more_list_quick_size_18")
            capture("vis058_editor_large_$theme")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_sizes").assertIsDisplayed()
            compose.onNodeWithTag("more_list_quick_sizes").performClick()
            // 作者 long：外点从任意子层关闭全部，不应用当前字号候选，也不能把下层正文变成编辑动作。
            val ins = InstrumentationRegistry.getInstrumentation()
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, now + 60, action, 20f, 1200f, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            waitGone("more_list_quick_size_18")
            compose.runOnIdle { assertEquals(0, changes) }
        }
        compose.onNodeWithTag("vis058_content").performTextReplacement("443")
        compose.onNodeWithTag("more_list_quick_menu").performClick()
        compose.onNodeWithTag("more_list_quick_undo").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("more_list_quick_undo").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { assertEquals("443", content); assertEquals(1, changes) }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle { assertEquals("8080\n8000-9000", content); assertEquals(2, changes) }
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("more_list_quick_menu").performClick()
        compose.onNodeWithTag("more_list_quick_redo").assertIsEnabled()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("more_list_quick_redo").performClick()
            compose.mainClock.advanceTimeBy(64)
            // 作者 long：受控模拟回源期间输入法迟到提交，旧重做不得覆盖新正文或读取已经清空的历史栈。
            compose.runOnIdle { content = "8443" }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle { assertEquals("8443", content); assertEquals(2, changes) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun vis058SearchEmptyAndEntryScrimAcrossThemes() {
        section = "sniffer"; render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; state = ProfileMoreEditorUiState(Profile.custom(958, "搜索空态")) }
            compose.onNodeWithTag("profile_more_empty_add").performClick()
            capture("vis058_empty_before_$theme")
            val sheetTop = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.top
            compose.onNodeWithTag("profile_more_add_search").performTextReplacement("not-a-field")
            val token = awaitVisibleIme(); capture("vis058_empty_ime_$theme")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            awaitHiddenIme(token)
            compose.waitForIdle()
            android.os.SystemClock.sleep(900)
            val restoredTop = compose.onNodeWithTag("profile_more_add_sheet").fetchSemanticsNode().boundsInWindow.top
            capture("vis058_empty_$theme")
            assertEquals("键盘隐藏后空态面板应恢复：$sheetTop → $restoredTop", sheetTop, restoredTop, 40f)
            compose.onNodeWithTag("profile_more_add_empty").assertIsDisplayed()
            compose.onNodeWithTag("profile_more_add_search_clear").performClick()
            compose.onNodeWithTag("more_add_sniff.HTTP").performScrollTo().performClick()
            compose.onNodeWithTag("profile_more_add_done").performClick()
            waitGone("profile_more_add_done")
            compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
            compose.onNodeWithTag("more_protocol_ports").performClick()
            compose.onNodeWithTag("more_list_add_menu").performClick()
            compose.onNodeWithTag("more_list_add").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("more_list_entry_dialog").fetchSemanticsNodes().isNotEmpty() }
            var amount = -1f
            compose.waitUntil(5_000) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                    amount = (view?.layoutParams as? android.view.WindowManager.LayoutParams)?.dimAmount ?: -1f
                }
                amount >= 0f
            }
            assertEquals(if (dark) .48f else .20f, amount, .001f)
            capture("vis058_entry_$theme")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitGone("more_list_entry_dialog")
            compose.onNodeWithTag("more_value_back").performClick()
            compose.onNodeWithTag("more_protocol_back").performClick()
        }
    }

    @Test fun vis059YamlToolbarErrorsReceiptAndRestore() {
        val restore = StateRestorationTester(compose)
        section = "sniffer"
        state = ProfileMoreEditorUiState(Profile.custom(959, "YAML验收"), sniffer = ProfileSnifferOverrides(enable = true))
        restore.setContent { KClashTheme(darkTheme = dark) {
            ProfileMoreEditor(state, section, "", { exits++ }) { ntp, sniffer ->
                pendingSave = ntp to sniffer; saveCalls++; state = state.copy(isSaving = true, error = null)
            }
        } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            compose.onNodeWithTag("profile_more_yaml").performClick()
            capture("vis059_yaml_$theme")
            compose.onNodeWithTag("more_yaml_content").performTextReplacement("sniffer: {enable: true}")
            compose.onNodeWithTag("more_yaml_back").performClick()
            compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
            compose.onNodeWithTag("more_yaml_error").assertIsDisplayed()
            compose.runOnIdle { assertEquals(if (theme == "light") 0 else 2, saveCalls) }
            compose.onNodeWithTag("more_yaml_content").performTextReplacement("enable: false\nsniff:\n  HTTP:\n    ports: [8080, 8000-9000]\n    override-destination: false")
            compose.onNodeWithTag("profile_top_search_open").performClick()
            compose.onNodeWithTag("profile_top_search_input").performTextReplacement("8080")
            capture("vis059_yaml_search_$theme")
            compose.onNodeWithTag("profile_top_search_close").performClick()
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_undo").performClick()
            waitGone("more_list_quick_undo")
            compose.onNodeWithTag("more_yaml_content").assertTextContains("sniffer: {enable: true}")
            compose.onNodeWithTag("more_list_quick_menu").performClick()
            compose.onNodeWithTag("more_list_quick_redo").performClick()
            waitGone("more_list_quick_redo")
            compose.onNodeWithTag("more_yaml_back").performClick()
            compose.waitUntil(5_000) { state.isSaving }
            compose.onNodeWithTag("more_yaml_content").assertIsNotEnabled()
            compose.onNodeWithTag("more_yaml_back").performClick()
            compose.runOnIdle { assertEquals(if (theme == "light") 1 else 3, saveCalls) }
            compose.runOnIdle { completeHeldSave(false) }
            compose.onNodeWithTag("more_yaml_error").assertIsDisplayed()
            compose.onNodeWithTag("more_yaml_content").assertIsEnabled()
            capture("vis059_yaml_failure_$theme")
            compose.onNodeWithTag("more_yaml_back").performClick()
            compose.waitUntil(5_000) { state.isSaving }
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("more_yaml_saving").assertIsDisplayed()
            compose.runOnIdle { completeHeldSave(true) }
            waitGone("more_yaml_back")
            compose.runOnIdle {
                assertEquals(if (theme == "light") 2 else 4, saveCalls)
                assertEquals(false, savedSniffer?.enable)
                assertEquals(listOf("8080", "8000-9000"), savedSniffer?.sniff?.get("HTTP")?.ports)
                // 作者 long：下一主题仍有真实变更，避免重复同值直接退出绕过保存失败与恢复检查。
                if (theme == "light") state = state.copy(sniffer = ProfileSnifferOverrides(enable = true), revision = state.revision + 1)
            }
        }
    }

    @Test fun vis059ProtocolPickerGeometryAndNullableTargetAcrossThemes() {
        section = "sniffer"; render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; state = ProfileMoreEditorUiState(Profile.custom(9591, "目标选择"),
                sniffer = ProfileSnifferOverrides(sniff = mapOf("HTTP" to com.lonnnnnng.flclash.service.ProfileSniffProtocolOverrides(ports = listOf("8080"))))) }
            compose.onNodeWithTag("more_open_sniff.HTTP").performClick()
            capture("vis059_protocol_$theme")
            compose.onNodeWithTag("more_protocol_destination").performClick()
            val a = compose.onNodeWithTag("more_protocol_destination_default").fetchSemanticsNode().boundsInWindow
            val b = compose.onNodeWithTag("more_protocol_destination_true").fetchSemanticsNode().boundsInWindow
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            assertEquals(44f * density, a.height, 2f)
            assertEquals(a.bottom, b.top, 2f)
            capture("vis059_target_default_$theme")
            compose.runOnIdle { holdSave = true }
            compose.onNodeWithTag("more_protocol_destination_true").performClick()
            compose.onNodeWithTag("more_protocol_destination_true").assertIsNotEnabled()
            compose.runOnIdle { completeHeldSave(false) }
            compose.onNodeWithTag("more_protocol_error").assertIsDisplayed()
            compose.onNodeWithTag("more_protocol_destination_true").performClick()
            compose.runOnIdle { completeHeldSave(true); holdSave = false }
            waitGone("more_protocol_destination_true")
            compose.onNodeWithTag("more_protocol_destination").assertTextContains("已开启", substring = true)
            compose.onNodeWithTag("more_protocol_destination").performClick()
            capture("vis059_target_enabled_$theme")
            compose.onNodeWithTag("more_protocol_destination_default").performClick()
            waitGone("more_protocol_destination_default")
            compose.runOnIdle { assertNull(savedSniffer?.sniff?.get("HTTP")?.overrideDestination); assertEquals(listOf("8080"), savedSniffer?.sniff?.get("HTTP")?.ports) }
            compose.onNodeWithTag("more_protocol_back").performClick()
        }
    }

    @Test fun vis059YamlUnacceptedAndRepeatedRejectedSavesRemainEditable() {
        section = "sniffer"
        state = ProfileMoreEditorUiState(Profile.custom(9592, "YAML拒绝"), sniffer = ProfileSnifferOverrides(enable = true))
        var calls = 0
        compose.setContent { KClashTheme {
            ProfileMoreEditor(state, section, "", {}) { _, _ ->
                calls++
                if (calls > 1) state = state.copy(error = "当前暂不可修改，请重试")
            }
        } }
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("enable: false")
        repeat(3) { attempt ->
            compose.onNodeWithTag("more_yaml_back").performClick()
            compose.waitUntil(5_000) { calls == attempt + 1 && compose.onAllNodesWithTag("more_yaml_saving").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("more_yaml_content").assertIsEnabled()
        }
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("enable: true\n")
        compose.onNodeWithTag("more_yaml_back").performClick()
        waitGone("more_yaml_back")
        compose.runOnIdle { assertEquals(3, calls) }
    }

    @Test fun vis060ScalarDialogFieldsThemesValidationAndCancel() {
        state = state.copy(ntp = ProfileNtpOverrides(server = "time.apple.com", port = 123, interval = 30, dialerProxy = ""))
        render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            for (key in listOf("server", "port", "interval", "dialer-proxy")) {
                compose.onNodeWithTag("more_open_$key").performClick()
                compose.onNodeWithTag("more_value_dialog").assertIsDisplayed()
                compose.onNodeWithTag("more_${key}_value").assertIsNotFocused()
                val rect = compose.onNodeWithTag("more_value_dialog").fetchSemanticsNode().boundsInWindow
                val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
                assertEquals(minOf(348f * metrics.density, metrics.widthPixels - 80f * metrics.density), rect.width, 2f)
                capture("vis060_${key.replace("-", "_")}_$theme")
                val invalid = when (key) { "server" -> "https://bad.test"; "port" -> "0"; "interval" -> "2147483648"; else -> "Proxy Test" }
                compose.onNodeWithTag("more_${key}_value").performTextReplacement(invalid)
                if (key != "dialer-proxy") compose.onNodeWithTag("more_value_error").assertIsDisplayed()
                if (key == "port") capture("vis060_error_$theme")
                compose.onNodeWithTag("more_value_back").performClick(); waitGone("more_value_dialog")
                compose.runOnIdle { assertEquals(0, saveCalls) }
            }
            // 作者 long：原空拨号代理确认不删除入口；主动清空原非空服务器才恢复继承。
            compose.onNodeWithTag("more_open_dialer-proxy").performClick()
            compose.onNodeWithTag("more_value_confirm").performClick(); waitGone("more_value_dialog")
            compose.onNodeWithTag("more_field_dialer-proxy").assertExists()
            compose.runOnIdle { assertEquals(0, saveCalls) }
            compose.onNodeWithTag("more_open_server").performClick()
            compose.onNodeWithTag("more_server_value").performTextReplacement("a".repeat(520))
            compose.onNodeWithTag("more_server_value").assertTextEquals("a".repeat(512))
            compose.onNodeWithTag("more_value_outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, 5f)) }
            waitGone("more_value_dialog")
            compose.onNodeWithTag("more_open_port").performClick()
            // 作者 long：前一输入窗口销毁后键盘和焦点仍在交接；只在新弹框已接焦点且IME隐藏时验证返回取消。
            compose.waitUntil(5_000) { ime()?.second == false }
            android.os.SystemClock.sleep(500)
            capture("vis060_before_back_$theme")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitGone("more_value_dialog")
        }
    }

    @Test fun vis060ScalarSaveRestoreFailureAndClear() {
        val restore = StateRestorationTester(compose)
        state = state.copy(ntp = ProfileNtpOverrides(server = "time.apple.com", port = 123, interval = 30))
        restore.setContent { KClashTheme(darkTheme = true) {
            ProfileMoreEditor(state, "ntp", "", {}) { ntp, sniffer ->
                saveCalls++; pendingSave = ntp to sniffer; state = state.copy(isSaving = true, error = null)
            }
        } }
        compose.onNodeWithTag("more_open_server").performClick()
        compose.onNodeWithTag("more_server_value").performTextReplacement("time.example.com")
        restore.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNodeWithTag("more_server_value").assertTextContains("time.example.com")
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.runOnIdle { assertEquals(1, saveCalls); assertTrue(state.isSaving) }
        compose.onNodeWithTag("more_value_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("more_value_back").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("more_value_outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, 5f)) }
        compose.onNodeWithTag("more_value_dialog").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saveCalls); completeHeldSave(false) }
        compose.onNodeWithTag("more_server_value").assertTextContains("time.example.com")
        compose.onNodeWithTag("more_value_error").assertIsDisplayed(); capture("vis060_save_failure")
        compose.onNodeWithTag("more_value_confirm").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("more_value_saving").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, saveCalls); completeHeldSave(true) }
        waitGone("more_value_dialog")
        compose.onNodeWithTag("more_edit_server").assertTextContains("time.example.com", substring = true)
        compose.onNodeWithTag("more_open_interval").performClick()
        compose.onNodeWithTag("more_interval_value").performTextReplacement("60")
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.runOnIdle { completeHeldSave(true); assertEquals(60, savedNtp?.interval) }
        waitGone("more_value_dialog")
        compose.onNodeWithTag("more_open_server").performClick()
        compose.onNodeWithTag("more_server_value").performTextClearance()
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.runOnIdle { completeHeldSave(true); assertNull(savedNtp?.server); assertEquals(123, savedNtp?.port) }
        waitGone("more_value_dialog")
        compose.onNodeWithTag("more_field_server").assertDoesNotExist()
    }

    @Test fun vis060NormalizedNumbersAndClearedFieldCanBeReadded() {
        state = state.copy(ntp = ProfileNtpOverrides(server = "time.apple.com", port = 123, interval = 30))
        render()
        for ((key, text) in listOf("port" to "0123", "interval" to "030")) {
            compose.onNodeWithTag("more_open_$key").performClick()
            compose.onNodeWithTag("more_${key}_value").performTextReplacement(text)
            compose.onNodeWithTag("more_value_confirm").performClick(); waitGone("more_value_dialog")
            compose.runOnIdle { assertEquals(0, saveCalls) }
        }
        compose.onNodeWithTag("more_open_server").performClick()
        compose.onNodeWithTag("more_server_value").performTextClearance()
        compose.onNodeWithTag("more_value_confirm").performClick(); waitGone("more_value_dialog")
        compose.onNodeWithTag("more_field_server").assertDoesNotExist()
        add("server")
        compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
        compose.onNodeWithTag("more_edit_server").assertTextContains("time.apple.com", substring = true)
        compose.runOnIdle { assertEquals(2, saveCalls) }
    }

    @Test fun vis060ScalarUnacceptedAndRepeatedRejectionRemainEditable() {
        state = state.copy(ntp = ProfileNtpOverrides(port = 123))
        compose.setContent { KClashTheme {
            ProfileMoreEditor(state, "ntp", "", {}) { _, _ ->
                saveCalls++
                if (saveCalls > 1) state = state.copy(error = "当前暂不可修改，请重试")
            }
        } }
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performTextReplacement("124")
        repeat(3) { attempt ->
            compose.onNodeWithTag("more_value_confirm").performClick()
            compose.runOnIdle { assertEquals(attempt + 1, saveCalls) }
            compose.onNodeWithTag("more_port_value").assertIsEnabled()
        }
        compose.onNodeWithTag("more_port_value").performTextReplacement("123")
        compose.onNodeWithTag("more_value_confirm").performClick(); waitGone("more_value_dialog")
        compose.runOnIdle { assertEquals(3, saveCalls) }
    }

    @Test fun vis061DialogEntryEarlyCancelAndStableFadeAcrossThemes() {
        state = state.copy(ntp = ProfileNtpOverrides(port = 123)); render()
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val density = metrics.density
        val stableWidth = minOf(348f * density, metrics.widthPixels - 80f * density)
        fun width() = compose.onNodeWithTag("more_value_dialog").fetchSemanticsNode().boundsInWindow.width
        fun frame(name: String) {
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
        }
        fun backdrop(): Int {
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            return android.graphics.Color.red(bitmap.getPixel(bitmap.width / 2, bitmap.height * 7 / 8)).also { bitmap.recycle() }
        }
        try {
            for (theme in listOf("light", "dark")) {
                compose.runOnIdle { dark = theme == "dark" }
                compose.waitForIdle(); android.os.SystemClock.sleep(600)
                val parentRed = backdrop()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag("more_open_port").performClick()
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
                val entering = width()
                assertTrue("入场应仍大于稳定宽度", entering > stableWidth + 5f)
                frame("vis061_entry_96_$theme")
                compose.onNodeWithTag("more_value_back").performClick()
                val held = width()
                compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
                compose.onNodeWithTag("more_value_confirm").assertIsNotEnabled()
                assertEquals("提前退出保持当时缩放", held, width(), 2f)
                frame("vis061_early_exit_$theme")
                compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
                compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
                compose.runOnIdle { assertEquals(0, saveCalls) }
                compose.mainClock.autoAdvance = true
                compose.onNodeWithTag("more_open_port").performClick()
                capture("vis061_settled_$theme")
                val settled = width(); assertEquals(stableWidth, settled, 2f)
                val expectedRed = parentRed * if (theme == "dark") .52f else .8f
                assertEquals("仅应用本弹框遮罩，不能叠加系统默认dim", expectedRed, backdrop().toFloat(), 5f)
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag("more_value_back").performClick()
                compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
                compose.onNodeWithTag("more_value_dialog").assertExists()
                assertEquals(settled,width(),2f); frame("vis061_exit_96_$theme")
                compose.onNodeWithTag("more_value_outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(5f,5f)) }
                compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
                compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
                compose.runOnIdle { assertEquals(0,saveCalls) }
                compose.mainClock.autoAdvance = true
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun vis061ClearSaveRetainsWindowUntilFadeAndClosingRestoreDoesNotReplay() {
        var registry by mutableStateOf(androidx.compose.runtime.saveable.SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        var disposals = 0
        state = state.copy(ntp = ProfileNtpOverrides(server = "time.apple.com", port = 123))
        compose.setContent { if (mounted) CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides registry) {
          DisposableEffect(Unit) { onDispose { disposals++ } }
          KClashTheme(darkTheme = true) {
            ProfileMoreEditor(state,"ntp","",{}) { ntp,sniffer ->
                saveCalls++; pendingSave=ntp to sniffer; state=state.copy(isSaving=true,error=null)
            }
          }
        } }
        compose.onNodeWithTag("more_open_server").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNodeWithTag("more_server_value").performTextClearance()
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.waitUntil(5_000) { state.isSaving }
        try {
            compose.mainClock.autoAdvance=false
            compose.runOnIdle { completeHeldSave(true) }
            compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
            compose.onNodeWithTag("more_value_dialog").assertExists()
            compose.onNodeWithTag("more_value_confirm").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1,saveCalls); assertNull(savedNtp?.server) }
            val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            recordVisualPng("vis061_clear_fading",ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
            var savedState: Map<String, List<Any?>> = emptyMap()
            compose.runOnIdle { savedState = registry.performSave(); mounted = false }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.runOnIdle { assertEquals("冻结时钟也必须真正销毁旧子树", 1, disposals)
                registry = androidx.compose.runtime.saveable.SaveableStateRegistry(savedState) { true }; mounted = true }
            compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1,saveCalls) }
        } finally { compose.mainClock.autoAdvance=true }
        compose.onNodeWithTag("profile_more_add").performClick()
        compose.onNodeWithTag("more_add_server").performClick()
        compose.runOnIdle { completeHeldSave(true) }
        compose.onNodeWithTag("profile_more_add_done").performClick()
        waitGone("profile_more_add_done")
        compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
    }

    @Test fun vis061NewProfileDoesNotInheritClosingDialog() {
        state = state.copy(ntp = ProfileNtpOverrides(port = 123)); render()
        compose.onNodeWithTag("more_open_port").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        try {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("more_value_back").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { state = ProfileMoreEditorUiState(Profile.custom(9612,"新配置"),ntp=ProfileNtpOverrides(port=456)) }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onNodeWithTag("more_value_dialog").assertDoesNotExist()
            compose.onNodeWithTag("more_open_port").performClick()
            compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            compose.onNodeWithTag("more_port_value").assertTextContains("456")
            compose.onNodeWithTag("more_value_confirm").assertIsEnabled()
            compose.runOnIdle { assertEquals(0,saveCalls) }
        } finally { compose.mainClock.autoAdvance=true }
        compose.onNodeWithTag("more_value_back").performClick(); waitGone("more_value_dialog")
    }

    private fun render() {
        assertPortraitDefaultFontConfiguration()
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileMoreEditor(state, section, "", { exits++ }) { ntp, sniffer ->
                saveCalls++
                if (holdSave) { pendingSave = ntp to sniffer; state = state.copy(isSaving = true, error = null) }
                else if (reject) state = state.copy(error = "保存失败，请重试")
                else { savedNtp = ntp; savedSniffer = sniffer; state = state.copy(ntp = ntp, sniffer = sniffer, revision = state.revision + 1, error = null) }
            }
        } }
    }
    private fun add(vararg keys: String) {
        val tag = if (compose.onAllNodesWithTag("profile_more_empty_add").fetchSemanticsNodes().isNotEmpty()) "profile_more_empty_add" else "profile_more_add"
        compose.onNodeWithTag(tag).performClick()
        keys.forEach { compose.onNodeWithTag("more_add_$it").performScrollTo().performClick() }
        if (compose.onAllNodesWithTag("profile_more_add_done").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("profile_more_add_done").performClick()
    }
    private fun yaml(content: String) {
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement(content)
        compose.onNodeWithTag("more_yaml_back").performClick()
    }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
    }
}
