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
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.data.ServiceSettings
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实检测只复用已授权配置，恢复设置与停止态，不创建或改写 Profile 正文。 */
class ServiceStatusRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun serviceRouteCacheReusesUnchangedExitAndRechecksChangedExitAndNetwork() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashServiceAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager(); val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val currentId = repository.currentProfileId.value
        assertTrue(repository.profiles.value.first { it.id == currentId }.url == args.getString("kclashServiceAuthorizedSource"))
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } }
        val store = entry.serviceSettingsStore(); val original = store.read(); val vpn = entry.vpnSettingsStore().read()
        val mode = manager.outboundMode.value
        val context = compose.activity.applicationContext; val layout = DashboardLayoutStore.load(context)
        lateinit var model: ServiceStatusViewModel
        var globalOriginal: String? = null
        var phase = "prepare"
        try {
            store.write(ServiceSettings(disabled = ServiceTarget.entries.toSet() - ServiceTarget.GITHUB))
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.SERVICE_STATUS)))
            compose.activityRule.scenario.onActivity { model = androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java]; model.reloadSettings() }
            compose.waitUntil(5_000) { model.state.value.settings.enabled == listOf(ServiceTarget.GITHUB) }
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val selected = runBlocking(Dispatchers.IO) {
                manager.setVpnEnabled(false).getOrThrow(); manager.startProxy().getOrThrow()
                manager.setOutboundMode(CoreOutboundMode.DIRECT).getOrThrow()
                val groups = manager.getProxyGroups().getOrThrow().groups
                val global = groups.first { it.name == "GLOBAL" }; globalOriginal = global.now
                val groupNames = groups.map { it.name }.toSet()
                val node = global.all.filter { it !in groupNames && it !in listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS") }.take(12)
                    .firstOrNull { (manager.testDelay(it, CoreBridge.DEFAULT_TEST_URL, 4_000L).getOrNull()?.value ?: -1) > 0 }
                checkNotNull(node) { "没有可用的真实代理节点" }
                manager.changeProxy("GLOBAL", "DIRECT").getOrThrow()
                node
            }
            val session = manager.runningSinceElapsedMillis.value
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_service_card"))
            phase = "initial-direct"
            compose.waitUntil(60_000) { model.state.value.entries[ServiceTarget.GITHUB]?.result?.let {
                it.status == ServiceStatus.AVAILABLE && it.chains.firstOrNull() == "DIRECT" && it.hits.isNotEmpty()
            } == true }
            val baseline = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            val hardRevision = manager.serviceCacheRevision.value
            phase = "unrelated-global-pick"
            runBlocking(Dispatchers.IO) { manager.changeProxy("GLOBAL", selected).getOrThrow() }
            val moved = runBlocking(Dispatchers.IO) { manager.ipRouteStamp() }
            assertNotEquals(baseline.stamp, moved)
            assertEquals(hardRevision, manager.serviceCacheRevision.value)
            // 作者 long：DIRECT 模式下修改 GLOBAL 选择不改变 GitHub 出口；原时间与完整结果保留证明未重新外网检测。
            val verified = runBlocking(Dispatchers.IO) {
                manager.reuseServiceChecks(listOf(baseline), manager.ipRouteStamp(), hardRevision)
            }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_service_cache_verify", "held=" + verified.size + " baseline=" + baseline.stamp + " immediate=" + moved) })
            compose.waitUntil(20_000) { model.state.value.entries[ServiceTarget.GITHUB]?.result?.let {
                it.stamp.epoch == baseline.stamp.epoch && it.stamp.version > baseline.stamp.version
            } == true }
            val reused = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            assertEquals(baseline.copy(stamp = reused.stamp), reused)
            phase = "manual-refresh"
            compose.runOnIdle { model.check(listOf(ServiceTarget.GITHUB)) }
            compose.waitUntil(60_000) { (model.state.value.entries[ServiceTarget.GITHUB]?.result?.checkedAt ?: 0L) > baseline.checkedAt }
            val manual = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            phase = "network-invalidates"
            manager.networkChanged()
            compose.waitUntil(60_000) { (model.state.value.entries[ServiceTarget.GITHUB]?.result?.checkedAt ?: 0L) > manual.checkedAt }
            val network = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            assertTrue(manager.serviceCacheRevision.value > hardRevision)
            phase = "changed-exit"
            runBlocking(Dispatchers.IO) { manager.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow() }
            compose.waitUntil(60_000) { model.state.value.entries[ServiceTarget.GITHUB]?.result?.let {
                it.checkedAt > network.checkedAt && selected in it.chains && it.status == ServiceStatus.AVAILABLE
            } == true }
            assertEquals(session, manager.runningSinceElapsedMillis.value)
            phase = "stop-invalidates"
            val routed = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            compose.waitUntil(60_000) { model.state.value.entries[ServiceTarget.GITHUB]?.result?.let {
                it.checkedAt > routed.checkedAt && it.chains.firstOrNull() == "DIRECT"
            } == true }
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet()); assertEquals(currentId, repository.currentProfileId.value)
            metadata.forEach { (id, value) -> repository.profileFile(id).let { file -> assertEquals(value, Triple(file.name, file.length(), file.lastModified())) } }
            assertFalse(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_service_cache", "real-hits unchanged-exit-reused-original-time manual-network-changed-exit-stop-rechecked session-and-profiles-preserved") })
        } catch (failure: Throwable) {
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_service_cache_stage", phase)
                putString("kclash_service_cache_state", "entry=" + model.state.value.entries[ServiceTarget.GITHUB]?.result?.stamp +
                    " status=" + model.state.value.entries[ServiceTarget.GITHUB]?.result?.status +
                    " loading=" + model.state.value.entries[ServiceTarget.GITHUB]?.loading +
                    " error=" + model.state.value.error + " hard=" + manager.serviceCacheRevision.value +
                    " current=" + runBlocking(Dispatchers.IO) { manager.ipRouteStamp() })
            })
            throw failure
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                globalOriginal?.let { manager.changeProxy("GLOBAL", it).getOrThrow() }
                manager.setOutboundMode(mode).getOrThrow(); manager.setVpnEnabled(vpn).getOrThrow()
            }
            store.write(original); DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.activityRule.scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java].reloadSettings() }
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun serviceOrderDragPersistsAndKeepsDisabledTargetsWithoutChangingProfiles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kclashServiceAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val currentId = repository.currentProfileId.value
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } }
        val context = compose.activity.applicationContext
        val layout = DashboardLayoutStore.load(context)
        val store = entry.serviceSettingsStore(); val original = store.read()
        lateinit var model: ServiceStatusViewModel
        try {
            val disabled = ServiceTarget.entries.toSet() - ServiceTarget.GOOGLE
            store.write(ServiceSettings(disabled = disabled))
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.SERVICE_STATUS)))
            compose.activityRule.scenario.onActivity { model = androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java]; model.reloadSettings() }
            compose.waitUntil(5_000) { !model.state.value.settingsLoading && model.state.value.settings.disabled == disabled }
            compose.activityRule.scenario.recreate()
            openServices()
            compose.onNodeWithTag("service_manage").performClick()
            compose.onNodeWithTag("service_toggle_google").assertIsNotEnabled()
            val list = compose.onNodeWithTag("service_order_list")
            val bounds = list.fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("service_order_google").fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithTag("service_order_github").fetchSemanticsNode().boundsInRoot
            // 作者 long：正式页面整行长按排序，独立回读偏好证明松手提交经过 ViewModel 和真实存储。
            list.performTouchInput {
                down(androidx.compose.ui.geometry.Offset(90f, first.center.y - bounds.top))
                advanceEventTime(800)
                moveBy(androidx.compose.ui.geometry.Offset(0f, next.center.y - first.center.y), delayMillis = 200)
                up()
            }
            compose.waitUntil(5_000) { !model.state.value.saving && store.read().order.first() == ServiceTarget.GITHUB }
            val saved = store.read()
            assertEquals(disabled, saved.disabled); assertEquals(listOf(ServiceTarget.GOOGLE), saved.enabled)
            compose.onNodeWithTag("service_detail_close").performClick()
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java].reloadSettings() }
            compose.waitUntil(5_000) { model.state.value.settings == saved }
            openServices(); compose.onNodeWithTag("service_manage").performClick()
            compose.onNodeWithTag("service_order_list").performScrollToIndex(0)
            compose.onNodeWithTag("service_order_github").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "第 1 项"))
            compose.onNodeWithTag("service_toggle_google").assertIsNotEnabled()
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet()); assertEquals(currentId, repository.currentProfileId.value)
            metadata.forEach { (id, value) -> repository.profileFile(id).let { f -> assertEquals(value, Triple(f.name, f.length(), f.lastModified())) } }
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("kclash_service_order", "drag-persisted-rebuilt-disabled-preserved-last-enabled-locked-profiles-preserved") })
        } finally {
            store.write(original); DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java].reloadSettings() }
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun serviceExitIpAndQualityFollowDirectAndRealProxyWithoutChangingProfiles() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashServiceAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager(); val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val currentId = repository.currentProfileId.value
        assertTrue(repository.profiles.value.first { it.id == currentId }.url == args.getString("kclashServiceAuthorizedSource"))
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } }
        val settings = entry.serviceSettingsStore().read(); val vpn = entry.vpnSettingsStore().read()
        val mode = manager.outboundMode.value
        val context = compose.activity.applicationContext; val layout = DashboardLayoutStore.load(context)
        lateinit var model: ServiceStatusViewModel
        var globalOriginal: String? = null
        var phase = "direct"
        try {
            entry.serviceSettingsStore().write(ServiceSettings(disabled = ServiceTarget.entries.toSet() - ServiceTarget.GITHUB))
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.SERVICE_STATUS)))
            compose.activityRule.scenario.onActivity { model = androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java]; model.reloadSettings() }
            compose.waitUntil(5_000) { model.state.value.settings.enabled == listOf(ServiceTarget.GITHUB) }
            compose.activityRule.scenario.recreate()
            openServices()
            compose.waitUntil(50_000) { model.state.value.exits["DIRECT"]?.info != null }
            val directIp = checkNotNull(model.state.value.exits["DIRECT"]?.info).ip
            assertTrue(com.lonnnnnng.flclash.data.isPublicIpLiteral(directIp))
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertFalse(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
            compose.onNodeWithTag("service_exit_github").performClick()
            compose.waitUntil(35_000) { !model.state.value.exits.getValue("DIRECT").qualityLoading }
            val quality = model.state.value.exits.getValue("DIRECT")
            assertTrue(quality.quality != null || quality.qualityFailures.isNotEmpty())
            quality.quality?.let { assertEquals(directIp, it.ip) }
            compose.onNodeWithTag("ip_quality_address").performClick()
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(directIp, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            compose.onNodeWithTag("ip_quality_hide").performClick()
            compose.onNodeWithText("*****").assertExists()
            compose.onNodeWithTag("service_manage_back").performClick()
            compose.onNodeWithTag("service_detail_close").performClick()
            phase = "routed"
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val selected = runBlocking(Dispatchers.IO) {
                manager.setVpnEnabled(false).getOrThrow(); manager.startProxy().getOrThrow()
                val groups = manager.getProxyGroups().getOrThrow().groups
                val global = groups.first { it.name == "GLOBAL" }; globalOriginal = global.now
                val groupNames = groups.map { it.name }.toSet()
                val node = global.all.filter { it !in groupNames && it !in listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS") }.take(12)
                    .firstOrNull { (manager.testDelay(it, CoreBridge.DEFAULT_TEST_URL, 4_000L).getOrNull()?.value ?: -1) > 0 }
                checkNotNull(node) { "没有可用的真实代理节点" }
                manager.changeProxy("GLOBAL", node).getOrThrow(); manager.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow()
                node
            }
            val session = manager.runningSinceElapsedMillis.value
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            openServices()
            compose.waitUntil(60_000) {
                val result = model.state.value.entries[ServiceTarget.GITHUB]?.result
                result?.chains?.contains(selected) == true && model.state.value.exits[result.chains.first()]?.info != null
            }
            val check = checkNotNull(model.state.value.entries[ServiceTarget.GITHUB]?.result)
            val node = check.chains.first()
            val routedIp = checkNotNull(model.state.value.exits[node]?.info).ip
            assertTrue(com.lonnnnnng.flclash.data.isPublicIpLiteral(routedIp))
            assertEquals(session, manager.runningSinceElapsedMillis.value)
            phase = "routed-independent"
            val independent = runBlocking(Dispatchers.IO) {
                var answer: com.lonnnnnng.flclash.data.OutboundIpInfo? = null
                repeat(3) {
                    if (answer == null) {
                        delay(1_500L)
                        try { answer = entry.serviceExitStore().outbound(node, manager.ipRouteStamp()) }
                        catch (_: ServiceRouteChangedException) {
                            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_service_exit_retry", "independent-route-changed") })
                        }
                    }
                }
                checkNotNull(answer) { "独立出口复查未取得稳定结果" }
            }
            assertTrue(com.lonnnnnng.flclash.data.isPublicIpLiteral(independent.ip))
            compose.waitUntil(50_000) { model.state.value.exits[node]?.info != null }
            val qualityIp = checkNotNull(model.state.value.exits[node]?.info).ip
            compose.onNodeWithTag("service_exit_github").performClick()
            compose.waitUntil(35_000) { model.state.value.exits[node]?.qualityLoading == false }
            val routedQuality = checkNotNull(model.state.value.exits[node])
            routedQuality.quality?.let { assertEquals(qualityIp, it.ip) }
            assertTrue(routedQuality.quality != null || routedQuality.qualityFailures.isNotEmpty() || routedQuality.qualityError != null)
            phase = "stopped"
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            compose.waitUntil(5_000) { model.state.value.qualityNode == null && node !in model.state.value.exits }
            compose.waitUntil(50_000) { model.state.value.exits["DIRECT"]?.info != null }
            assertFalse(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet())
            assertEquals(currentId, repository.currentProfileId.value)
            metadata.forEach { (id, stamp) -> repository.profileFile(id).let { assertEquals(stamp, Triple(it.name, it.length(), it.lastModified())) } }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_service_exit",
                "direct-ip-real-routed-detected-proxy-ip-real-independent-query=true changed=${directIp != routedIp} quality-direct=${quality.quality?.source?.name ?: "failed"} quality-routed=${routedQuality.quality?.source?.name ?: "failed"} stopped-invalidated clipboard-hide-return profiles-preserved") })
        } catch (failure: Throwable) {
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_service_exit_stage", phase) })
            throw failure
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                globalOriginal?.let { manager.changeProxy("GLOBAL", it).getOrThrow() }
                manager.setOutboundMode(mode).getOrThrow(); manager.setVpnEnabled(vpn).getOrThrow()
            }
            entry.serviceSettingsStore().write(settings); DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.activityRule.scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java].reloadSettings() }
            compose.activityRule.scenario.recreate()
        }
    }

    private fun openServices() {
        compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_service_card"))
        compose.onNodeWithTag("dashboard_service_card").performClick()
    }

    @Test fun realDirectAndRoutedChecksAndCardSettingsPreserveProfiles() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashServiceAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val currentId = repository.currentProfileId.value
        val profile = repository.profiles.value.first { it.id == currentId }
        assertTrue("只复用授权来源", profile.url == args.getString("kclashServiceAuthorizedSource"))
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { file -> file.name to (file.length() to file.lastModified()) } }
        val store = entry.serviceSettingsStore()
        val original = store.read()
        val vpn = entry.vpnSettingsStore().read()
        val context = compose.activity.applicationContext
        val layout = DashboardLayoutStore.load(context)
        val targets = listOf(ServiceTarget.GOOGLE, ServiceTarget.GITHUB)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND)))
            compose.activityRule.scenario.recreate()
            val direct = stableCheck(manager, targets, "direct")
            assertEquals(targets, direct.map { it.target })
            assertTrue(direct.all { it.checkedAt > 0 && it.stamp == direct.first().stamp })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertFalse(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
            store.write(ServiceSettings(disabled = ServiceTarget.entries.toSet() - targets.toSet()))
            // 作者 long：Activity 重建保留 ViewModel；验收直接预置偏好后必须显式重读，避免旧内存状态覆盖夹具。
            lateinit var serviceModel: ServiceStatusViewModel
            compose.activityRule.scenario.onActivity {
                serviceModel = androidx.lifecycle.ViewModelProvider(it)[ServiceStatusViewModel::class.java]
                serviceModel.reloadSettings()
            }
            compose.waitUntil(5_000) { serviceModel.state.value.settings.enabled == targets }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_add_service_status"))
            compose.onNodeWithTag("dashboard_add_service_status").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_service_card"))
            compose.onNodeWithTag("dashboard_service_card").performClick()
            compose.waitUntil(45_000) {
                val text = compose.onAllNodesWithTag("service_result_github").fetchSemanticsNodes().firstOrNull()
                    ?.config?.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }?.joinToString()
                text != null && !text.contains("待检测") && !text.contains("检测中")
            }
            compose.onNodeWithTag("service_manage").performClick()
            compose.onNodeWithTag("service_toggle_google").performClick().assertIsOff()
            compose.waitUntil(5_000) { ServiceTarget.GOOGLE in store.read().disabled }
            compose.onNodeWithTag("service_up_github").performClick()
            compose.waitUntil(5_000) { store.read().order.first() == ServiceTarget.GITHUB }
            compose.onNodeWithTag("service_detail_close").performClick()
            compose.activityRule.scenario.recreate()
            assertEquals(ServiceTarget.GITHUB, store.read().order.first())
            assertEquals(listOf(ServiceTarget.GITHUB), store.read().enabled)
            // 作者 long：后台页面已取消自动探测，独立调用用来核对运行态核心协议，避免同名探测互相取消。
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking(Dispatchers.IO) { manager.setVpnEnabled(false).getOrThrow(); manager.startProxy().getOrThrow() }
            val session = manager.runningSinceElapsedMillis.value
            val routed = stableCheck(manager, targets, "routed")
            assertEquals(targets, routed.map { it.target })
            assertTrue(routed.all { it.checkedAt >= direct.first().checkedAt && it.stamp == routed.first().stamp })
            assertEquals(session, manager.runningSinceElapsedMillis.value)
            assertTrue(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            val stopped = stableCheck(manager, listOf(ServiceTarget.GITHUB), "stopped").single()
            assertTrue(stopped.checkedAt >= routed.first().checkedAt)
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet())
            assertEquals(currentId, repository.currentProfileId.value)
            metadata.forEach { (id, value) -> repository.profileFile(id).let { assertEquals(value, it.name to (it.length() to it.lastModified())) } }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_service_acceptance", "direct=${direct.joinToString { it.target.id + ":" + it.status.wire }} routed=${routed.joinToString { it.target.id + ":" + it.status.wire }} stopped=${stopped.status.wire}; settings-rebuild-profiles-preserved")
            })
        } finally {
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow(); manager.setVpnEnabled(vpn).getOrThrow() }
            store.write(original)
            DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.activityRule.scenario.recreate()
        }
    }

    private fun stableCheck(manager: CoreRuntimeManager, targets: List<ServiceTarget>, phase: String): List<CoreServiceCheck> = runBlocking(Dispatchers.IO) {
        repeat(3) { attempt ->
            delay(1_500L)
            try { return@runBlocking manager.checkServices(targets) }
            catch (error: ServiceRouteChangedException) {
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_service_retry", "$phase route-changed attempt=${attempt + 1}")
                })
                if (attempt == 2) throw error
            }
        }
        error("路由未稳定")
    }
}
