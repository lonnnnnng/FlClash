package com.lonnnnnng.flclash.ui

import android.Manifest
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.GeoResourceType
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实工具页验证资源设置浮框的错误恢复、保存/重置回执和关闭后重开，结束恢复原设置且不启动核心。 */
class ResourceInputMotionInstrumentedTest {
    @get:Rule(order = 0) val permission = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realToolsResourceInputsValidateSaveResetCancelAndReopenContinuously() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        assertTrue("该流程需要实际安装的独立motion宿主", compose.activity.packageName.endsWith(".motion"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val runtime = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val originalSettings = runtime.geoSettings.value
        val resourcePrefs = compose.activity.getSharedPreferences("resource_settings", android.content.Context.MODE_PRIVATE)
        val originalKeys = resourcePrefs.all.toMap()

        fun capture(name: String) {
            compose.waitForIdle()
            android.os.SystemClock.sleep(250)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            recordVisualPng("resource_input_$name", ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray())
        }

        fun waitForDialogExit() {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("resource_setting_dialog").fetchSemanticsNodes().isEmpty()
            }
        }

        fun waitForIme(visible: Boolean) {
            compose.waitUntil(5_000) {
                var current = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                    current = view?.let(ViewCompat::getRootWindowInsets)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                current == visible
            }
        }

        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_resources").performClick()
            compose.onNodeWithTag("resources_page").assertIsDisplayed()
            capture("page_initial")
            if (!runtime.geoSettings.value.autoUpdate) {
                compose.onNodeWithTag("resources_auto_update").performClick()
                compose.waitUntil(10_000) { runtime.geoSettings.value.autoUpdate }
            }

            compose.onNodeWithText("资源更新周期").performClick()
            compose.onNodeWithTag("resource_setting_input").assertTextEquals(runtime.geoSettings.value.intervalHours.toString())
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("87601")
            waitForIme(true)
            compose.onNodeWithTag("resource_setting_input").performImeAction()
            waitForIme(false)
            compose.onNodeWithTag("resource_setting_error").assertIsDisplayed()
            capture("interval_invalid")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("48")
            waitForIme(true)
            capture("interval_ime")
            compose.onNodeWithTag("resource_setting_input").performImeAction()
            waitForIme(false)
            waitForDialogExit()
            compose.waitUntil(10_000) { runtime.geoSettings.value.intervalHours == 48 }
            compose.onNodeWithText("资源更新周期").performClick()
            compose.onNodeWithTag("resource_setting_input").assertTextEquals("48")
            capture("interval_saved_reopened")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("72")
            compose.onNodeWithTag("resource_setting_cancel").performClick()
            waitForDialogExit()
            compose.onNodeWithText("资源更新周期").performClick()
            compose.onNodeWithTag("resource_setting_input").assertTextEquals("48")
            compose.onNodeWithTag("resource_setting_cancel").performClick()
            waitForDialogExit()

            compose.onNodeWithTag("resource_GEO:MMDB").performClick()
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("file:///bad")
            waitForIme(true)
            capture("geo_url_ime")
            compose.onNodeWithTag("resource_setting_save").performClick()
            waitForIme(false)
            compose.onNodeWithTag("resource_setting_error").assertIsDisplayed()
            capture("geo_url_invalid")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("https://example.org/geoip.dat")
            compose.onNodeWithTag("resource_setting_input").performImeAction()
            waitForDialogExit()
            compose.waitUntil(10_000) {
                runtime.geoSettings.value.urls[GeoResourceType.MMDB] == "https://example.org/geoip.dat"
            }
            compose.onNodeWithTag("resource_GEO:MMDB").performClick()
            compose.onNodeWithTag("resource_setting_input").assertRawInputEquals("https://example.org/geoip.dat")
            capture("geo_url_saved_reopened")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("https://cancel.example/geoip.dat")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            androidx.test.espresso.Espresso.pressBack()
            waitForDialogExit()
            compose.onNodeWithTag("resource_GEO:MMDB").performClick()
            compose.onNodeWithTag("resource_setting_input").assertRawInputEquals("https://example.org/geoip.dat")
            compose.onNodeWithTag("resource_setting_reset").performClick()
            waitForDialogExit()
            compose.waitUntil(10_000) {
                runtime.geoSettings.value.urls[GeoResourceType.MMDB] == GeoResourceType.MMDB.defaultUrl
            }
            compose.onNodeWithTag("resource_GEO:MMDB").performClick()
            compose.onNodeWithTag("resource_setting_input").assertRawInputEquals(GeoResourceType.MMDB.defaultUrl)
            capture("geo_url_reset_reopened")
            compose.onNodeWithTag("resource_setting_cancel").performClick()
            waitForDialogExit()

            compose.onNodeWithTag("resources_back").performClick()
            compose.onNodeWithTag("tools_item_resources").assertIsDisplayed()
            capture("resources_returned")
        } finally {
            runtime.setGeoSettings(originalSettings)
            // 作者 long：恢复语义之外还恢复原键集合，不能为视觉验收留下此前不存在的默认配置。
            val editor = resourcePrefs.edit()
            for (key in listOf("auto_update", "interval_hours") + GeoResourceType.entries.map { it.name }) if (key !in originalKeys) editor.remove(key)
            check(editor.commit()); assertEquals(originalKeys, resourcePrefs.all)
            assertEquals(originalSettings, runtime.geoSettings.value)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        }
    }
}
