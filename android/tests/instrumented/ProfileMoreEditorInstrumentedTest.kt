package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
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

/** 作者 long：一次走完整页草稿流程，确认子页和 YAML 不会在父页保存前写入配置。 */
class ProfileMoreEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var dark by mutableStateOf(false)
    private var section by mutableStateOf("ntp")
    private var state by mutableStateOf(ProfileMoreEditorUiState(Profile.custom(920, "更多设置")))
    private var savedNtp: ProfileNtpOverrides? = null
    private var savedSniffer: ProfileSnifferOverrides? = null
    private var exits = 0

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
        compose.runOnIdle { assertNull(savedNtp) }
        compose.onNodeWithTag("profile_more_editor_save").performClick()
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
        compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("8000-9000", substring = true)
        compose.onNodeWithTag("more_protocol_destination_default").performScrollTo().performClick()
        compose.onNodeWithTag("more_value_confirm").performClick()
        compose.onNodeWithTag("profile_more_editor_save").performClick()
        compose.runOnIdle {
            val saved = checkNotNull(savedSniffer)
            assertEquals(false, saved.enable); assertEquals(emptyList<String>(), saved.skipDomain)
            assertEquals(listOf("geoip:cn", "rule-set:local"), saved.skipSrcAddress)
            assertNull(saved.sniff?.get("HTTP")?.overrideDestination)
            assertEquals(listOf("80", "8000-9000"), saved.sniff?.get("HTTP")?.ports)
        }
        compose.onNodeWithTag("more_remove_sniff.HTTP").performScrollTo().performClick()
        compose.onNodeWithTag("profile_more_editor_save").performClick()
        compose.runOnIdle { assertNull(savedSniffer?.sniff) }
        yaml("sniff: {}")
        waitGone("more_yaml_confirm")
        compose.onNodeWithTag("profile_more_protocols_cleared").assertIsDisplayed()
        compose.onNodeWithTag("profile_more_editor_save").performClick()
        compose.runOnIdle { assertEquals(emptyMap<String, Any>(), savedSniffer?.sniff) }
        compose.onNodeWithText("恢复协议继承").performClick()
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
        compose.onNodeWithText("放弃").performClick()
        compose.onNodeWithTag("more_edit_port").assertTextContains("123", substring = true)
        compose.onNodeWithTag("profile_more_editor_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("profile_more_reset").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("more_field_port").assertExists()
        compose.onNodeWithTag("profile_more_editor_back").performClick()
        compose.onNodeWithText("放弃").performClick()
        compose.runOnIdle { assertEquals(1, exits); assertNull(savedNtp); assertNull(savedSniffer) }
    }

    @Test fun morePagesRenderEmptyAddFieldsProtocolYamlAndErrorsAcrossThemes() {
        render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; section = "ntp"; state = state.copy(profile = Profile.custom(if (dark) 922 else 921, "更多设置")) }
            capture("more_ntp_empty_$theme")
            compose.onNodeWithTag("profile_more_add").performClick()
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
            add("enable", "sniff.HTTP")
            compose.onNodeWithTag("more_open_sniff.HTTP").performScrollTo().performClick()
            capture("more_sniffer_protocol_$theme")
            compose.onNodeWithTag("more_value_back").performClick()
            compose.onNodeWithTag("profile_more_yaml").performClick()
            capture("more_sniffer_yaml_$theme")
            compose.onNodeWithTag("more_yaml_back").performClick()
        }
    }

    private fun render() {
        assertPortraitDefaultFontConfiguration()
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileMoreEditor(state, section, "", { exits++ }) { ntp, sniffer -> savedNtp = ntp; savedSniffer = sniffer }
        } }
    }
    private fun add(vararg keys: String) {
        compose.onNodeWithTag("profile_more_add").performClick()
        keys.forEach { compose.onNodeWithTag("more_add_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("profile_more_add_done").performClick()
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
