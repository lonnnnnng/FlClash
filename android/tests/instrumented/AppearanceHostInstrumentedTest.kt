package com.lonnnnnng.flclash.ui

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.common.AppText
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayOutputStream

/** 作者 long：真实入口保存显示偏好并重建，核对配置与核心保持；只对独立motion包操作。 */
class AppearanceHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun languageThemePersistenceAndFourTabsKeepProfileAndRuntime() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        check(context.packageName.endsWith(".motion"))
        val store = compose.activity.appearanceSettings
        val before = store.read()
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val profiles = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        try {
            store.write(AppearanceSettings(AppLanguage.CHINESE, AppTheme.LIGHT))
            compose.waitUntil(5_000) { !UiLanguage.english }
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_language").performClick()
            compose.onNodeWithTag("language_picker").assertIsDisplayed()
            capture("tools_language_zh")
            compose.onNodeWithTag("appearance_language_english").performClick()
            compose.waitUntil(10_000) { store.read().language == AppLanguage.ENGLISH && UiLanguage.english }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("language_picker").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Network activity").assertIsDisplayed()
            compose.onNodeWithText("Language").assertIsDisplayed()
            compose.onNodeWithTag("tools_item_theme").performClick()
            compose.onNodeWithTag("theme_picker").assertIsDisplayed()
            capture("tools_theme_en")
            compose.onNodeWithTag("appearance_theme_dark").performClick()
            compose.waitUntil(5_000) { store.read().theme == AppTheme.DARK && compose.onAllNodesWithTag("theme_picker").fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { assertFalse(WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("tools_item_observations").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(AppearanceSettings(AppLanguage.ENGLISH, AppTheme.DARK), store.read())
            capture("tools_en_dark")
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_general"))
            compose.onNodeWithTag("tools_item_general").performClick()
            capture("tools_general_en")
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_network").performClick()
            capture("tools_network_en")
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("advanced_item_dns").performClick()
            compose.onNodeWithTag("page_global_dns").assertIsDisplayed()
            capture("tools_global_dns_en")
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_observations"))
            compose.onNodeWithTag("tools_item_observations").performClick()
            for (tab in listOf("requests", "connections", "dns", "logs")) {
                compose.onNodeWithTag("observation_tab_$tab").performClick()
                compose.onNodeWithTag("observation_search_toggle").performClick()
                compose.onNodeWithTag("observation_search_field").performTextInput("no-results")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                capture("tools_en_${tab}")
                compose.onNodeWithTag("observation_search_toggle").performClick()
            }
            compose.onNodeWithTag("observation_list_back").performClick()
            compose.onNodeWithTag("tools_item_language").performClick()
            compose.onNodeWithTag("appearance_language_chinese").performClick()
            compose.waitUntil(10_000) { !UiLanguage.english && compose.onAllNodesWithTag("language_picker").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("网络活动").assertIsDisplayed()
            compose.onNodeWithTag("tools_item_theme").performClick()
            compose.onNodeWithTag("appearance_theme_light").performClick()
            compose.waitUntil(5_000) { store.read().theme == AppTheme.LIGHT && compose.onAllNodesWithTag("theme_picker").fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { assertTrue(WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
            capture("tools_zh_light")
            compose.onNodeWithTag("tools_item_theme").performClick()
            compose.onNodeWithTag("appearance_theme_system").performClick()
            compose.onNodeWithTag("tools_item_language").performClick()
            compose.onNodeWithTag("appearance_language_system").performClick()
            compose.waitUntil(10_000) { store.read() == AppearanceSettings() }
            assertEquals(profiles, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally { store.write(before) }
    }

    @Test fun translatedMessagesPreserveParametersAndAllCatalogEntriesResolve() {
        val original = AppText.useEnglish
        try {
            AppText.useEnglish = true
            assertEquals("Settings", AppText.text("设置"))
            assertEquals("Delete 用户{0}\\\$?", AppText.text("删除 {0}？", "用户{0}\\\$"))
            assertEquals("Port must be between 1 and 65535", AppText.text("端口必须在 1–65535 之间"))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val en = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) })
            val zh = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) })
            val fields = com.lonnnnnng.flclash.common.R.string::class.java.fields.filter { it.name.startsWith("text_") }
            assertTrue(fields.size > 2100)
            for (field in fields) {
                val id = field.getInt(null)
                val english = en.getString(id); val chinese = zh.getString(id)
                assertTrue(field.name, english.isNotBlank() || chinese.isBlank())
                assertEquals(field.name, Regex("\\{\\d+\\}").findAll(chinese).map { it.value }.sorted().toList(),
                    Regex("\\{\\d+\\}").findAll(english).map { it.value }.sorted().toList())
                if (field.name != "text_${java.security.MessageDigest.getInstance("SHA-256").digest("中文".toByteArray()).joinToString("") { "%02x".format(it) }.take(12)}") {
                    // 作者 long：英语资源不得误留整条中文；明确保留的内部标识不属于显示资源覆盖。
                    assertFalse(field.name, english.any { it in '\u4e00'..'\u9fff' })
                }
            }
        } finally { AppText.useEnglish = original }
    }
    @Test fun languageSwitchKeepsUnsubmittedTextDraft() = runBlocking {
        val store = compose.activity.appearanceSettings; val before = store.read()
        try {
            store.write(AppearanceSettings(AppLanguage.CHINESE, AppTheme.LIGHT))
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_general"))
            compose.onNodeWithTag("tools_item_general").performClick()
            compose.onNodeWithTag("general_user_agent").performClick()
            compose.onNodeWithTag("general_ua_add").performClick()
            compose.onNodeWithTag("general_text_input").performTextReplacement("用户草稿{0}User-Agent")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            store.write(AppearanceSettings(AppLanguage.ENGLISH, AppTheme.DARK))
            compose.waitUntil(10_000) { UiLanguage.english }
            compose.onNodeWithTag("general_text_input").assertTextEquals("用户草稿{0}User-Agent")
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            capture("tools_draft_en_dark")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("general_text_input").assertTextEquals("用户草稿{0}User-Agent")
            store.write(AppearanceSettings(AppLanguage.CHINESE, AppTheme.LIGHT))
            compose.waitUntil(10_000) { !UiLanguage.english }
            compose.onNodeWithText("取消").assertIsDisplayed()
            compose.onNodeWithTag("general_text_input").assertTextEquals("用户草稿{0}User-Agent")
            compose.onNodeWithText("取消").performClick()
            assertFalse(compose.activity.appearanceSettings.read().language == AppLanguage.ENGLISH)
        } finally { store.write(before) }
    }

    @Test fun coldPrepareAppearance() = runBlocking {
        check(compose.activity.packageName.endsWith(".motion"))
        val store = compose.activity.appearanceSettings
        check(store.read() == AppearanceSettings())
        store.write(AppearanceSettings(AppLanguage.ENGLISH, AppTheme.DARK))
        compose.waitUntil(10_000) { UiLanguage.english }
        val bundle = android.os.Bundle().apply { putString("appearance_prepare_pid", android.os.Process.myPid().toString()) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }
    @Test fun coldVerifyAppearanceAndRestore() = runBlocking {
        check(compose.activity.packageName.endsWith(".motion"))
        val store = compose.activity.appearanceSettings
        try {
            assertEquals(AppearanceSettings(AppLanguage.ENGLISH, AppTheme.DARK), store.read())
            compose.waitUntil(10_000) { UiLanguage.english }
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithText("Network activity").assertIsDisplayed()
            compose.runOnIdle { assertFalse(WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
            capture("tools_cold_en_dark")
            val bundle = android.os.Bundle().apply { putString("appearance_verify_pid", android.os.Process.myPid().toString()) }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
        } finally { store.write(AppearanceSettings()) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(700)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val bytes = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        recordVisualPng(name, bytes)
    }
}
