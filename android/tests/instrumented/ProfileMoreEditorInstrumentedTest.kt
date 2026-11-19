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
        val headerHeight = compose.onNodeWithTag("more_add_header_true").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("more_add_sniff.HTTP").performClick()
        compose.mainClock.advanceTimeBy(80)
        val leavingHeaderHeight = compose.onNodeWithTag("more_add_header_true").fetchSemanticsNode().boundsInRoot.height
        assertTrue("分组标题须随最后条目连续收起", leavingHeaderHeight > 0f && leavingHeaderHeight < headerHeight)
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
        compose.runOnIdle { section = "ntp" }
        compose.onNodeWithTag("profile_more_empty_add").performClick()
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
        compose.onNodeWithTag("more_value_back").performClick(); compose.onNodeWithText("放弃").performClick()
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performClick().performTextReplacement("port: 0")
        val yamlWindow = awaitVisibleIme()
        compose.onNodeWithTag("more_yaml_confirm").performClick()
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
        compose.onNodeWithTag("more_yaml_error").assertIsDisplayed()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("enable: false\nskip-domain: []\nskip-src-address: ['geoip:cn', 'rule-set:local']\nsniff:\n  http:\n    ports: [80, 8000-9000]\n    override-destination: false")
        compose.onNodeWithTag("more_yaml_confirm").performClick()
        waitGone("more_yaml_confirm")
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
        waitGone("more_yaml_confirm")
        compose.runOnIdle { assertNull(savedSniffer?.sniff) }
        compose.onNodeWithTag("profile_more_empty").assertIsDisplayed()
    }

    @Test fun returningFromFieldYamlAndParentProtectsUnsavedDrafts() {
        render(); add("port")
        compose.onNodeWithTag("more_open_port").performClick()
        compose.onNodeWithTag("more_port_value").performTextReplacement("124")
        compose.onNodeWithTag("more_value_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("more_port_value").assertTextContains("124")
        compose.onNodeWithTag("more_value_back").performClick()
        compose.onNodeWithText("放弃").performClick()
        compose.onNodeWithTag("more_edit_port").assertTextContains("123", substring = true)
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement("port: 999")
        compose.onNodeWithTag("more_yaml_back").performClick()
        waitGone("more_yaml_confirm")
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
        compose.onNodeWithTag("more_list_quick_confirm").performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_destination").assertTextContains("已关闭", substring = true)
        compose.runOnIdle { assertEquals(listOf("8080", "8000-9000"), savedSniffer?.sniff?.get("HTTP")?.ports) }
        compose.onNodeWithTag("more_protocol_ports").performClick()
        openQuickPorts()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextClearance()
        compose.onNodeWithTag("more_list_quick_confirm").performClick()
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
            compose.onNodeWithTag("more_value_back").performClick(); compose.onNodeWithText("放弃").performClick()
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
        compose.onNodeWithTag("more_list_quick_confirm").performClick()
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
        compose.onNodeWithTag("more_list_quick_confirm").performClick()
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
        compose.onNodeWithTag("more_force-domain_value").performTextReplacement("example.test")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.onNodeWithText("放弃快速编辑？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("more_list_quick_confirm").performClick()
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
        compose.onNodeWithTag("more_yaml_confirm").performClick()
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
