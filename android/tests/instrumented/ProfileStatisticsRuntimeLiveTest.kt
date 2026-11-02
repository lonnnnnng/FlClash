package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式首页只复用已有两份配置，核对统计随核心和当前配置变化，结束恢复选择与停止态。 */
class ProfileStatisticsRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appliedProfileStatisticsFollowStoppedRunningAndHotSwitchWithoutChangingFiles() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashStatisticsAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager(); val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.size >= 2 }
        val originalId = checkNotNull(repository.currentProfileId.value)
        assertTrue(repository.profiles.value.first { it.id == originalId }.url == args.getString("kclashStatisticsAuthorizedSource"))
        val otherId = repository.profiles.value.first { it.id != originalId }.id
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } }
        val context = compose.activity.applicationContext; val layout = DashboardLayoutStore.load(context)
        val vpn = entry.vpnSettingsStore().read()
        lateinit var model: ProfileStatisticsViewModel
        var phase = "prepare"
        fun open() { compose.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick() }
        fun assertStatistics(id: Long): ProfileStatistics {
            compose.waitUntil(20_000) { model.state.value.counts?.profileId == id }
            val expected = runBlocking(Dispatchers.IO) {
                var answer: ProfileStatistics? = null
                repeat(3) {
                    if (answer == null) {
                        try { answer = manager.profileStatistics(id) } catch (_: IllegalStateException) { delay(1_000L) }
                    }
                }
                checkNotNull(answer) { "配置统计未稳定" }
            }
            compose.waitUntil(15_000) { model.state.value.counts == expected }
            listOf(expected.groups, expected.proxies, expected.rules, expected.providers).forEachIndexed { index, value ->
                compose.onNodeWithTag("profile_statistics_value_" + index, useUnmergedTree = true).assertTextEquals(value.toString())
            }
            compose.waitUntil(15_000) { model.providers.value.profileId == id && model.providers.value.providers != null }
            val providers = checkNotNull(model.providers.value.providers)
            assertEquals(expected.providers, providers.size.toLong())
            // 作者 long：配置加载会异步填充资源，应比较同一轮当前快照，不能等待页面回到已经过时的中间值。
            compose.waitUntil(20_000) {
                val current = runBlocking(Dispatchers.IO) { runCatching { manager.profileProviders(id) }.getOrNull() }
                current != null && model.providers.value.providers == current
            }
            val firstHttp = providers.indexOfFirst { it.canUpdate }
            val visibleTag = when {
                providers.isEmpty() -> "profile_providers_empty"
                firstHttp >= 0 -> "profile_provider_update_" + firstHttp
                else -> "profile_provider_row_0"
            }
            // 作者 long：启停后弹层内容会从加载占位重新测量；数据一致后再等原生布局稳定，确认滚动确实能到达首行。
            compose.waitForIdle()
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag(visibleTag).performScrollTo()
                    compose.waitForIdle()
                    runCatching { compose.onNodeWithTag(visibleTag).assertIsDisplayed() }.getOrElse {
                        compose.onNodeWithTag("dashboard_profile_detail_sheet").performTouchInput {
                            swipeUp(startY = height * 0.75f, endY = height * 0.25f)
                        }
                        compose.waitForIdle()
                        compose.onNodeWithTag(visibleTag).assertIsDisplayed()
                    }
                }.isSuccess
            }
            val missing = runBlocking(Dispatchers.IO) { runCatching { manager.updateProfileProvider(id, "kclash-missing-provider-acceptance") }.exceptionOrNull() }
            assertTrue(missing is ProviderOperationException)
            assertEquals("Provider 已不存在，请刷新列表", missing?.message)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_providers", "rows=" + providers.size + " missing-update-refused") })
            return expected
        }
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.PROFILES)))
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { model = androidx.lifecycle.ViewModelProvider(it)[ProfileStatisticsViewModel::class.java] }
            runBlocking(Dispatchers.IO) { manager.getProxyGroups().getOrThrow() }
            phase = "stopped"
            open(); val stopped = assertStatistics(originalId)
            assertTrue(stopped.proxies > 0); assertTrue(stopped.groups > 0)
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            val updateIndex = model.providers.value.providers.orEmpty().indexOfFirst { it.canUpdate }
            if (updateIndex >= 0) {
                phase = "stopped-http-update"
                compose.onNodeWithTag("profile_provider_update_" + updateIndex).performScrollTo().performClick()
                compose.waitUntil(60_000) { model.providers.value.updateMessage != null && model.providers.value.updatingName == null }
                assertFalse("HTTP provider update failed", model.providers.value.updateFailed)
                assertStatistics(originalId)
                assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
                instrumentation.sendStatus(2, Bundle().apply { putString("kclash_providers_update", "existing-http-resource-updated-while-stopped") })
            }
            phase = "background"
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(5_000) { model.state.value.counts == null }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertStatistics(originalId)
            phase = "running"
            runBlocking(Dispatchers.IO) { manager.setVpnEnabled(false).getOrThrow(); manager.startProxy().getOrThrow() }
            assertStatistics(originalId)
            val session = manager.runningSinceElapsedMillis.value
            compose.onNodeWithTag("dashboard_profile_detail_open_profiles").performScrollTo().performClick()
            compose.onNodeWithTag("nav_dashboard").performClick()
            phase = "hot-switch-existing"
            runBlocking(Dispatchers.IO) { manager.selectProfile(otherId) { repository.selectProfile(it) } }
            compose.waitUntil(10_000) { repository.currentProfileId.value == otherId }
            open(); assertStatistics(otherId)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value); assertEquals(session, manager.runningSinceElapsedMillis.value)
            compose.onNodeWithTag("dashboard_profile_detail_open_profiles").performScrollTo().performClick()
            compose.onNodeWithTag("nav_dashboard").performClick()
            phase = "restore-profile"
            runBlocking(Dispatchers.IO) { manager.selectProfile(originalId) { repository.selectProfile(it) } }
            open(); assertStatistics(originalId)
            phase = "stop"
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            assertStatistics(originalId)
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet()); assertEquals(originalId, repository.currentProfileId.value)
            metadata.forEach { (id, value) -> repository.profileFile(id).let { f -> assertEquals(value, Triple(f.name, f.length(), f.lastModified())) } }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_profile_statistics", "stopped-running-background-hot-switch-existing-stopped-ui-counts-match profiles-preserved") })
        } catch (failure: Throwable) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val bytes = java.io.ByteArrayOutputStream()
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_png_name", "provider_layout_failure")
                putString("kclash_png_base64", android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
            })
            listOf("profile_provider_row_0", "profile_providers", "dashboard_profile_detail_sheet").forEach { tag ->
                val bounds = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.toString() }.getOrElse { "missing" }
                instrumentation.sendStatus(2, Bundle().apply { putString("kclash_provider_geometry", tag + "=" + bounds) })
            }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_profile_statistics_stage", phase) })
            throw failure
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                if (repository.currentProfileId.value != originalId) manager.selectProfile(originalId) { repository.selectProfile(it) }
                manager.setVpnEnabled(vpn).getOrThrow()
            }
            DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.activityRule.scenario.recreate()
        }
    }
}
