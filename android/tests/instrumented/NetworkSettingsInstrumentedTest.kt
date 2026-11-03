package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.NetworkSettings
import com.lonnnnnng.flclash.service.VpnSettingsState
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NetworkSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun networkSectionsSaveOptionsAndRenderBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var settings by mutableStateOf(NetworkSettings())
        var dark by mutableStateOf(false)
        var saves = 0
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileEditorPage({}, { Text("网络") }, {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                NetworkSettingsPanel(settings, VpnSettingsState(), false, {}, {}, { next, done -> settings = next; saves++; done(true) })
            }
        }) } }
        capture("network_light_top")
        compose.onNodeWithTag("network_ipv6").performScrollTo().performClick()
        compose.onNodeWithTag("network_dns_hijacking").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settings.ipv6); assertTrue(settings.dnsHijacking) }
        compose.onNodeWithTag("network_stack").performScrollTo().performClick()
        compose.onNodeWithTag("network_option_system").performClick()
        compose.onNodeWithTag("network_congestion").assertDoesNotExist()
        compose.onNodeWithTag("network_stack").performScrollTo().performClick()
        compose.onNodeWithTag("network_option_mips").performClick()
        compose.onNodeWithTag("network_congestion").performScrollTo().performClick()
        compose.onNodeWithTag("network_option_bbr3").performClick()
        compose.onNodeWithTag("network_routes").performScrollTo().performClick()
        compose.onNodeWithTag("network_editor_input").performTextReplacement("192.0.2.0/24\n2001:db8::/32")
        compose.onNodeWithTag("network_editor_save").performClick()
        compose.runOnIdle { assertEquals(2, settings.routeAddress.size); assertEquals("bbr3", settings.congestionController); assertEquals(6, saves) }
        compose.onNodeWithTag("network_excluded").performScrollTo()
        capture("network_light_tun")
        compose.runOnIdle { dark = true }
        capture("network_dark_tun")
        compose.onNodeWithTag("network_vpn").performScrollTo().assertIsDisplayed()
        capture("network_dark_top")
    }

    @Test fun invalidInputAndSaveFailureKeepDraftUntilSuccessfulRetry() {
        assertPortraitDefaultFontConfiguration()
        var settings by mutableStateOf(NetworkSettings())
        var fail = true
        var submissions = 0
        compose.setContent { KClashTheme { ProfileEditorPage({}, { Text("网络") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NetworkSettingsPanel(settings, VpnSettingsState(), false, {}, {}, { next, done ->
                    submissions++; if (!fail) settings = next; done(!fail)
                })
            }
        }) } }
        compose.onNodeWithTag("network_mtu").performScrollTo().performClick()
        compose.onNodeWithTag("network_editor_input").performTextReplacement("1200")
        compose.onNodeWithTag("network_editor_save").performClick()
        compose.onNodeWithTag("network_editor_error").assertTextContains("1280", substring = true)
        compose.runOnIdle { assertEquals(0, submissions) }
        compose.onNodeWithTag("network_editor_input").performTextReplacement("1400")
        compose.onNodeWithTag("network_editor_save").performClick()
        compose.onNodeWithTag("network_editor_error").assertTextContains("保存失败", substring = true)
        compose.onNodeWithTag("network_editor_input").assertTextContains("1400")
        capture("network_save_failure")
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("network_editor_save").performClick()
        compose.onNodeWithTag("network_editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1400, settings.mtu); assertEquals(2, submissions) }
        compose.onNodeWithTag("network_excluded").performScrollTo().performClick()
        compose.onNodeWithTag("network_editor_input").performTextReplacement("example.com/24")
        compose.onNodeWithTag("network_editor_save").performClick()
        compose.onNodeWithTag("network_editor_error").assertTextContains("数字 IPv4", substring = true)
        compose.runOnIdle { assertEquals(2, submissions) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
