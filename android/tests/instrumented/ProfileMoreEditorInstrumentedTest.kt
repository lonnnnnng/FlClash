package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("8000-9000", substring = true)
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
        compose.onNodeWithTag("more_protocol_ports").assertTextContains("默认端口", substring = true)
        compose.onNodeWithTag("more_protocol_destination").performClick()
        compose.runOnIdle { reject = true }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        compose.onNodeWithTag("more_protocol_error").assertTextContains("保存失败，请重试")
        compose.runOnIdle { assertNull(savedSniffer?.sniff?.get("HTTP")?.overrideDestination); reject = false }
        compose.onNodeWithTag("more_protocol_destination_false").performClick()
        waitGone("more_protocol_destination_false")
        compose.onNodeWithTag("more_protocol_ports").performClick()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextReplacement("8080\n8000-9000")
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_destination").assertTextContains("禁用", substring = true)
        compose.runOnIdle { assertEquals(listOf("8080", "8000-9000"), savedSniffer?.sniff?.get("HTTP")?.ports) }
        compose.onNodeWithTag("more_protocol_ports").performClick()
        compose.onNodeWithTag("sniff_HTTP_ports").performTextClearance()
        compose.onNodeWithTag("more_value_confirm").performClick()
        waitGone("more_value_confirm")
        compose.onNodeWithTag("more_protocol_ports").assertTextContains("默认端口", substring = true)
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
