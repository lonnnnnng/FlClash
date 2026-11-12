package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.GeneralSettings
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GeneralSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsAndLanSavesExposeBusyFeedback() {
        var settings by mutableStateOf(GeneralSettings())
        var saving by mutableStateOf(false)
        var lanBusy by mutableStateOf(false)
        compose.setContent { KClashTheme { ProfileEditorPage({}, { Text("常规") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GeneralSettingsPanel({}, settings, lanBusy = lanBusy, saving = saving,
                    onSave = { next, done -> saving = true; settings = next; done(true); saving = false })
            }
        }) } }
        compose.runOnIdle { saving = true }
        compose.onNodeWithTag("general_settings_saving").assertIsDisplayed()
        compose.runOnIdle { saving = false; lanBusy = true }
        compose.onNodeWithTag("general_settings_saving").assertIsDisplayed()
    }

    @Test fun generalSectionsCredentialsConnectionsHostsAndThemesSaveTogether() {
        assertPortraitDefaultFontConfiguration()
        var value by mutableStateOf(GeneralSettings())
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileEditorPage({}, { Text("常规") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) { GeneralSettingsPanel({}, value, onSave = { next, done -> value = next.prepared(); done(true) }) }
        }) } }
        capture("general_light_startup")
        compose.onNodeWithTag("general_notification_stop").performScrollTo().performClick()
        compose.onNodeWithTag("general_authentication").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(value.authentication); assertEquals(8, value.username.length); assertEquals(16, value.password.length); dark = true }
        compose.onNodeWithTag("general_username").performScrollTo().performClick()
        compose.onNodeWithTag("general_text_input").performTextReplacement("a:lice")
        compose.onNodeWithTag("general_text_save").performClick()
        compose.runOnIdle { assertEquals("alice", value.username) }
        compose.onNodeWithTag("general_controller").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(32, value.controllerSecret.length) }
        capture("general_dark_inbound")
        compose.onNodeWithTag("general_timeout").performScrollTo().performClick()
        compose.onNodeWithTag("general_option_1000").performClick()
        compose.onNodeWithTag("general_only_proxy").performScrollTo().performClick()
        compose.onNodeWithTag("general_system_dns").performScrollTo().performClick()
        compose.onNodeWithTag("general_log_level").performScrollTo().performClick()
        compose.onNodeWithTag("general_option_info").performClick()
        compose.onNodeWithTag("general_logs").performScrollTo().performClick()
        capture("general_dark_core_logs")
        compose.runOnIdle { assertEquals(1000, value.delayTestTimeout); assertTrue(value.onlyStatisticsProxy); assertTrue(value.appendSystemDns); assertEquals("info", value.logLevel); assertTrue(value.openLogs) }
        compose.onNodeWithTag("general_hosts").performScrollTo().performClick()
        compose.onNodeWithTag("general_host_add").performClick()
        compose.onNodeWithTag("general_host_name").performTextInput("fixture.invalid")
        compose.onNodeWithTag("general_host_value").performTextInput("192.0.2.1 2001:db8::1")
        compose.onNodeWithTag("general_host_save").performClick()
        compose.onNodeWithText("fixture.invalid").assertIsDisplayed()
        capture("general_hosts_dark")
        compose.onNodeWithTag("general_hosts_back").performClick()
        compose.runOnIdle { assertEquals("192.0.2.1 2001:db8::1", value.hosts["fixture.invalid"]) }
    }

    @Test fun portValidationAndSaveFailureKeepDraftAndUserAgentUsesIndependentPage() {
        assertPortraitDefaultFontConfiguration()
        var value by mutableStateOf(GeneralSettings())
        var fail = true
        compose.setContent { KClashTheme(darkTheme = false) { ProfileEditorPage({}, { Text("常规") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) { GeneralSettingsPanel({}, value, onSave = { next, done -> if (!fail) value = next.prepared(); done(!fail) }) }
        }) } }
        compose.onNodeWithTag("general_ports").performScrollTo().performClick()
        compose.onNodeWithTag("general_port_1").performTextReplacement("7890")
        compose.onNodeWithTag("general_ports_save").performClick()
        compose.onNodeWithTag("general_editor_error").assertTextContains("不能重复", substring = true)
        compose.onNodeWithTag("general_port_1").performTextReplacement("18890")
        compose.onNodeWithTag("general_ports_save").performClick()
        compose.onNodeWithTag("general_editor_error").assertTextContains("输入已保留", substring = true)
        // 作者 long：输入失败时键盘仍打开，保存必须实际可见，不能仅通过语义树点击被键盘遮住的按钮。
        compose.onNodeWithTag("general_ports_save").assertIsDisplayed()
        capture("general_ports_failure")
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("general_ports_save").performClick()
        compose.onNodeWithTag("general_ports_editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(18890, value.port) }
        compose.onNodeWithTag("general_user_agent").performScrollTo().performClick()
        compose.onNodeWithTag("general_ua_add").performClick()
        compose.onNodeWithTag("general_text_input").performTextInput("KClash-Fixture/1")
        compose.onNodeWithTag("general_text_save").performClick()
        compose.onNodeWithText("KClash-Fixture/1").performClick()
        compose.runOnIdle { assertEquals("KClash-Fixture/1", value.userAgent) }
        capture("general_user_agents_light")
        compose.onNodeWithTag("general_ua_default").performClick()
        compose.runOnIdle { assertNull(value.userAgent) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
