package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.OnDemandNetworkState
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnDemandSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun authorizationValidationFailedDraftAndBothThemesUseWholePages() {
        assertPortraitDefaultFontConfiguration()
        var names by mutableStateOf(listOf("Home", "Office"))
        var version by mutableStateOf(0L)
        var error by mutableStateOf<String?>(null)
        var dark by mutableStateOf(false)
        var reject = true
        var requests = 0
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileEditorPage({}, { Text("按需连接") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) { OnDemandSettingsPanel(false, {}, ssids = names,
                network = OnDemandNetworkState(locationEnabled = false), onRequestLocation = { requests++ },
                savedVersion = version, error = error, onSave = { expected, next ->
                    assertEquals(names, expected)
                    if (reject) error = "保存失败，输入已保留" else { names = next; error = null; version++ }
                }) }
        }) } }
        compose.onNodeWithTag("on_demand_location_authorize").performClick()
        assertEquals(1, requests)
        capture("on_demand_light_list")
        compose.onNodeWithTag("on_demand_add").performScrollTo().performClick()
        compose.onNodeWithTag("on_demand_input").performTextReplacement("Home")
        compose.onNodeWithTag("on_demand_save").performClick()
        compose.onNodeWithTag("on_demand_editor_error").assertTextContains("重复", substring = true)
        compose.onNodeWithTag("on_demand_input").performTextReplacement("😀".repeat(33))
        compose.onNodeWithTag("on_demand_save").performClick()
        compose.onNodeWithTag("on_demand_editor_error").assertTextContains("32", substring = true)
        compose.onNodeWithTag("on_demand_input").performTextReplacement(" Home ")
        compose.onNodeWithTag("on_demand_save").performClick()
        compose.onNodeWithTag("on_demand_editor_error").assertTextContains("输入已保留", substring = true)
        compose.onNodeWithTag("on_demand_save").assertIsDisplayed()
        capture("on_demand_light_failed_editor")
        compose.runOnIdle { reject = false; dark = true }
        compose.onNodeWithTag("on_demand_save").performClick()
        compose.onNodeWithTag("on_demand_input").assertDoesNotExist()
        assertEquals(" Home ", names.last())
        compose.onNodeWithTag("on_demand_sort").performScrollTo().performClick()
        capture("on_demand_dark_order")
        compose.onNodeWithTag("on_demand_down_Home").performClick()
        compose.onNodeWithTag("on_demand_order_list").assertDoesNotExist()
        assertEquals(listOf("Office", "Home", " Home "), names)
        compose.onNodeWithTag("on_demand_select").performScrollTo().performClick()
        compose.onNodeWithTag("on_demand_select_all").performClick()
        compose.onNodeWithTag("on_demand_delete").performClick()
        compose.onNodeWithTag("on_demand_delete_confirm").performClick()
        assertTrue(names.isEmpty())
        capture("on_demand_dark_empty")
    }

    @Test fun restoredEditorKeepsOriginalSnapshotAndSaveCompletionClosesCurrentComposition() {
        assertPortraitDefaultFontConfiguration()
        val restorer = StateRestorationTester(compose)
        var names by mutableStateOf(listOf("Original"))
        var version by mutableStateOf(0L)
        var busy by mutableStateOf(false)
        var accepted: List<String>? = null
        var submissions = 0
        restorer.setContent { KClashTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { OnDemandSettingsPanel(true, {}, ssids = names, busy = busy,
                savedVersion = version, onSave = { expected, next ->
                    assertEquals(listOf("Original"), expected)
                    accepted = next; submissions++; busy = true
                }) }
        } }
        compose.onNodeWithTag("on_demand_ssid_0").performScrollTo().performClick()
        compose.onNodeWithTag("on_demand_input").performTextReplacement(" Changed ")
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("on_demand_input").assertTextContains(" Changed ")
        compose.onNodeWithTag("on_demand_save").performClick()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("on_demand_save").assertIsNotEnabled()
        compose.runOnIdle { names = requireNotNull(accepted); version++; busy = false }
        compose.onNodeWithTag("on_demand_input").assertDoesNotExist()
        assertEquals(1, submissions)
        compose.onNodeWithText(" Changed ").assertExists()
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
