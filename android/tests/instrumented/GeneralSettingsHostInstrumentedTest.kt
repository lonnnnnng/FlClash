package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ServiceConfig
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class GeneralSettingsHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualGeneralPersistsPortsAndRecentsFlagAcrossRecreationAndKeepsProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val original = entry.generalSettingsStore().read()
        val before = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val files = before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_general"))
            compose.onNodeWithTag("tools_item_general").performClick()
            compose.onNodeWithTag("general_hidden").performClick()
            compose.waitUntil(15_000) { manager.generalSettings.value.hidden != original.hidden }
            val tasks = compose.activity.getSystemService(android.app.ActivityManager::class.java)
            compose.waitUntil(10_000) { tasks.appTasks.firstOrNull { it.taskInfo?.id == compose.activity.taskId }?.taskInfo?.baseIntent?.flags?.let {
                (it and android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS != 0) == !original.hidden
            } == true }
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("general_ports"))
            compose.onNodeWithTag("general_ports").performClick()
            val port = if (original.mixedPort == 18890) 18891 else 18890
            compose.onNodeWithTag("general_port_0").performTextReplacement(port.toString())
            compose.onNodeWithTag("general_ports_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("general_ports_editor").fetchSemanticsNodes().isEmpty() }
            assertEquals(port, entry.generalSettingsStore().read().mixedPort)
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("page_general").assertExists()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("general_ports"))
            compose.onNodeWithTag("general_ports").assertTextContains(port.toString())
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            capture("general_actual_inbound")
        } finally {
            manager.updateGeneralSettings(entry.generalSettingsStore().read(), original).getOrThrow()
            assertEquals(original, entry.generalSettingsStore().read())
        }
    }

    @Test fun runningPortsAuthenticationNotificationAndLogPreferencesReachSystemWithoutRewritingProfiles() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashGeneralAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertNull(android.net.VpnService.prepare(context))
        val repository = entry.profileRepository()
        val before = entry.database().profiles().getAll()
        val selected = repository.currentProfileId.value
        val profile = before.single { it.id == selected }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(profile.url.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashAuthorizedSourceSha256"), hash)
        val original = entry.generalSettingsStore().read()
        val originalVpn = entry.vpnSettingsStore().read()
        val files = before.associate { row -> row.id to repository.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        try {
            manager.setVpnEnabled(true).getOrThrow(); manager.startProxy().getOrThrow()
            val start = manager.runningSinceElapsedMillis.value
            val next = original.copy(mixedPort = if (original.mixedPort == 18890) 18891 else 18890, authentication = true,
                username = "acceptance", password = "local-only", openLogs = true, showNotificationStopAction = false, onlyStatisticsProxy = true).prepared()
            manager.updateGeneralSettings(original, next).getOrThrow()
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertEquals(start, manager.runningSinceElapsedMillis.value)
            assertEquals(next.mixedPort, ServiceConfig.vpnOptions?.port)
            assertEquals(false, ServiceConfig.vpnOptions?.systemProxy)
            assertFalse(ServiceConfig.notificationParams.value.showStopAction)
            assertTrue(ServiceConfig.notificationParams.value.onlyStatisticsProxy)
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            var actual = false
            repeat(50) {
                if (notifications.activeNotifications.any { it.packageName == context.packageName && it.notification.actions.isNullOrEmpty() }) actual = true
                if (!actual) kotlinx.coroutines.delay(100)
            }
            assertTrue("系统前台通知应移除停止动作", actual)
            val config = com.google.gson.JsonParser.parseString(com.lonnnnnng.flclash.service.YamlConfigCodec.toJson(manager.previewConfig(checkNotNull(selected)).content)).asJsonObject
            assertEquals(next.mixedPort, config.get("mixed-port").asInt)
            assertEquals("acceptance:local-only", config.getAsJsonArray("authentication").single().asString)
            manager.getStats().getOrThrow()
            assertEquals(before, entry.database().profiles().getAll()); assertEquals(selected, repository.currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to repository.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
        } finally {
            manager.stop().getOrThrow()
            manager.updateGeneralSettings(entry.generalSettingsStore().read(), original).getOrThrow()
            manager.setVpnEnabled(originalVpn).getOrThrow()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
