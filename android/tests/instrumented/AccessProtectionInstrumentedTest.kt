package com.lonnnnnng.flclash.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.data.RoomAccessControlSettingsStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：独立motion包使用真实PackageManager、主页面VM、系统剪贴板及磁盘Room；原设置在finally逐行恢复。 */
class AccessProtectionInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: AccessControlViewModel
    private fun waitReady() {
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[AccessControlViewModel::class.java] }
        compose.waitUntil(20_000) { !model.state.value.isLoading && model.state.value.settingsReady }
        assertNull(model.state.value.appsError); assertFalse(model.state.value.appsPermissionRequired)
    }
    private fun open() {
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_access"))
        compose.onNodeWithTag("tools_item_access").performClick(); waitReady()
    }
    private fun fixture(block: (RoomAccessControlSettingsStore) -> Unit) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("com.lonnnnnng.flclash.motion", compose.activity.packageName)
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val dao = entry.database().appPreferences()
        val previous = runBlocking { dao.getByPrefix("vpn_access_control").singleOrNull() }
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        val oldClip = clipboard.primaryClip
        try { open(); block(RoomAccessControlSettingsStore(dao)) }
        finally {
            runBlocking { if (previous == null) dao.delete("vpn_access_control") else dao.put(previous) }
            compose.runOnIdle { model.reload() }
            waitReady()
            if (oldClip != null) clipboard.setPrimaryClip(oldClip) else clipboard.clearPrimaryClip()
            assertEquals(previous, runBlocking { dao.getByPrefix("vpn_access_control").singleOrNull() })
        }
    }

    @Test fun nonLauncherAppCanBeSavedAndReadAfterActivityRecreation() = fixture { store ->
        val manager = compose.activity.packageManager
        val launchers = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
        val candidate = model.state.value.apps.firstOrNull { it.hasInternetPermission && it.packageName !in launchers }
            ?: error("当前设备没有无Launcher联网包，不能冒充全包枚举已验")
        compose.runOnIdle {
            model.setEnabled(true); model.setMode(AccessControlMode.ACCEPT_SELECTED)
            model.setFilterSystemApps(false); model.setFilterNonInternetApps(false)
            if (candidate.packageName !in model.state.value.settings.acceptList) model.togglePackage(candidate.packageName)
        }
        compose.onNodeWithTag("access_control_search_toggle").performClick()
        compose.onNodeWithTag("access_control_search").performTextReplacement(candidate.packageName)
        compose.onNodeWithTag("access_control_app_list").performScrollToNode(hasTestTag("access_control_app_${candidate.packageName}"))
        compose.onNodeWithTag("access_control_app_${candidate.packageName}").assertIsOn()
        capture("access_nonlauncher_selected")
        var callbacks = 0
        compose.runOnIdle { model.save { callbacks++ }; model.save { callbacks++ } }
        compose.waitUntil(10_000) { !model.state.value.isSaving }
        assertEquals(1, callbacks)
        val saved = runBlocking { store.current() }
        assertTrue(candidate.packageName in saved.acceptList)
        compose.activityRule.scenario.recreate(); waitReady()
        assertEquals(saved, model.state.value.settings)
        assertEquals(saved, runBlocking { store.current() })
        capture("access_saved_recreated")
    }

    @Test fun invalidSystemClipboardKeepsSelectionAndRefreshKeepsUnsavedDraft() = fixture { store ->
        val baseline = runBlocking { store.current() }
        val candidate = model.state.value.apps.first { !it.isSystem && it.hasInternetPermission }.packageName
        compose.runOnIdle { model.setEnabled(true); model.setMode(AccessControlMode.REJECT_SELECTED); model.togglePackage(candidate) }
        val draft = model.state.value.settings
        assertNotEquals(baseline, draft)
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("KClash test", " \nnot a package\nandroid\n1invalid.app"))
        compose.onNodeWithTag("access_control_actions").performScrollTo().performClick()
        compose.onNodeWithTag("access_control_clipboard_import").performClick()
        compose.onNodeWithTag("access_control_error").performScrollTo().assertTextContains("没有有效包名", substring = true)
        assertEquals(draft, model.state.value.settings); assertEquals(baseline, runBlocking { store.current() })
        compose.runOnIdle { model.setSearchQuery(candidate); model.refreshApps() }; waitReady()
        assertEquals(draft, model.state.value.settings); assertEquals(candidate, model.state.value.searchQuery)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED); waitReady()
        assertEquals(draft, model.state.value.settings); assertEquals(candidate, model.state.value.searchQuery)
        capture("access_invalid_clipboard_preserved")
    }

    @Test fun realSmartSelectionKeepsHiddenPackagesAndIgnoresSearch() = fixture { store ->
        val baseline = runBlocking { store.current() }
        val system = model.state.value.apps.first { it.isSystem }.packageName
        val offline = model.state.value.apps.first { !it.hasInternetPermission }.packageName
        val missing = "org.kclash.acceptance.not_installed"
        assertFalse(model.state.value.apps.any { it.packageName == missing })
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        compose.runOnIdle { model.setEnabled(true); model.setMode(AccessControlMode.ACCEPT_SELECTED) }
        clipboard.setPrimaryClip(ClipData.newPlainText("KClash test", "$system\n$offline\n$missing"))
        compose.runOnIdle { model.importFromClipboard(); model.setMode(AccessControlMode.REJECT_SELECTED); model.importFromClipboard()
            model.setFilterSystemApps(true); model.setFilterNonInternetApps(true); model.setSearchQuery("no_matches_expected") }
        val before = model.state.value
        assertTrue(before.visibleApps.isEmpty())
        val eligible = before.apps.filter { !it.isSystem && it.hasInternetPermission }
        assertTrue(eligible.isNotEmpty())
        compose.runOnIdle { model.smartSelectApps() }
        compose.waitUntil(20_000) { !model.state.value.isSmartSelecting }
        val after = model.state.value
        assertNull(after.error)
        val expected = buildSmartSelection(before.apps, before.apps.map { it.packageName }.filter(ChinaPackageMatcher::isChinaPackage).toSet(), before.settings)
        assertEquals(expected.acceptList, after.settings.acceptList); assertEquals(expected.rejectList, after.settings.rejectList)
        assertTrue(system in after.settings.acceptList && missing in after.settings.acceptList)
        assertTrue(system in after.settings.rejectList && missing in after.settings.rejectList)
        assertTrue(offline in after.settings.acceptList && offline in after.settings.rejectList)
        eligible.forEach { app ->
            if (ChinaPackageMatcher.isChinaPackage(app.packageName)) {
                assertTrue(app.packageName in after.settings.rejectList); assertFalse(app.packageName in after.settings.acceptList)
            } else {
                assertTrue(app.packageName in after.settings.acceptList); assertFalse(app.packageName in after.settings.rejectList)
            }
        }
        val count = after.selectedCount
        compose.runOnIdle { model.setSearchQuery(""); model.setFilterSystemApps(false) }
        assertEquals(count, model.state.value.selectedCount); assertEquals(baseline, runBlocking { store.current() })
        capture("access_smart_hidden_preserved")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
