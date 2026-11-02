package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.core.CoreRouteHit
import com.lonnnnnng.flclash.core.CoreRouteCheck
import com.lonnnnnng.flclash.core.CoreServiceCheck
import com.lonnnnnng.flclash.core.ServiceTarget
import com.lonnnnnng.flclash.core.ServiceStatus
import java.io.File
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreBridgeException
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.CoreIpResponse
import com.lonnnnnng.flclash.core.CoreRouteStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import com.lonnnnnng.flclash.service.models.VpnOptions
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoreRuntimeManagerTest {
    private fun cachedService(target: ServiceTarget = ServiceTarget.GITHUB) = CoreServiceCheck(target,
        ServiceStatus.AVAILABLE, checkedAt = 123L, stamp = CoreRouteStamp(1, 1),
        hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit")))

    private fun providerFixture(vehicle: String = "HTTP") = com.lonnnnnng.flclash.core.CoreExternalProvider("fixture", "Proxy", vehicle, 3, null)

    @Test fun providerDetailsAndUpdateUseLoadedProfileAndInvalidateRoutesWithoutStarting() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(8, "明细", source.path), temporaryFolder.newFolder(), bridge, controller)
        assertTrue(runCatching { runtime.profileProviders(8) }.isFailure)
        runtime.getProxyGroups().getOrThrow()
        bridge.externalRead = { listOf(providerFixture()) }
        assertEquals(listOf(providerFixture()), runtime.profileProviders(8))
        val revision = runtime.serviceCacheRevision.value
        runtime.updateProfileProvider(8, "fixture")
        assertEquals(1, bridge.externalUpdates)
        assertTrue(runtime.serviceCacheRevision.value > revision)
        assertTrue(controller.events.isEmpty()); assertEquals("{}", source.readText())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
    }

    @Test fun providerUpdateRefusesWrongProfileMissingAndFileProvidersWithoutCallingUpdate() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val runtime = manager(ActiveProfile(8, "明细", source.path), temporaryFolder.newFolder(), bridge, FakeServiceController())
        runtime.getProxyGroups().getOrThrow()
        bridge.externalRead = { listOf(providerFixture("File")) }
        assertTrue(runCatching { runtime.updateProfileProvider(9, "fixture") }.isFailure)
        assertEquals("Provider 已不存在，请刷新列表", runCatching { runtime.updateProfileProvider(8, "missing") }.exceptionOrNull()?.message)
        assertEquals("此 Provider 来自本地文件，无需在线更新", runCatching { runtime.updateProfileProvider(8, "fixture") }.exceptionOrNull()?.message)
        assertEquals(0, bridge.externalUpdates)
    }

    @Test fun providerUpdateDoesNotBlockStopRejectsDuplicatesAndDiscardsLateResult() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(8, "明细", source.path), temporaryFolder.newFolder(), bridge, controller)
        runtime.startProxy().getOrThrow()
        bridge.externalRead = { listOf(providerFixture()) }
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        bridge.externalUpdate = { started.complete(Unit); release.await() }
        val request = async { runCatching { runtime.updateProfileProvider(8, "fixture") } }
        started.await()
        assertEquals("已有 Provider 正在更新，请稍后重试", runCatching { runtime.updateProfileProvider(8, "fixture") }.exceptionOrNull()?.message)
        runtime.stop().getOrThrow()
        val revision = runtime.serviceCacheRevision.value
        release.complete(Unit)
        assertTrue(request.await().isFailure)
        assertEquals(revision, runtime.serviceCacheRevision.value)
        assertEquals(1, bridge.externalUpdates)
    }

    @Test fun providerUpdateMapsCoreErrorsWithoutSecretsAndReleasesGateForRetry() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val runtime = manager(ActiveProfile(8, "明细", source.path), temporaryFolder.newFolder(), bridge, FakeServiceController())
        runtime.getProxyGroups().getOrThrow(); bridge.externalRead = { listOf(providerFixture()) }
        bridge.externalUpdate = { throw CoreBridgeException("request_bad_response", "fixture-secret") }
        val failure = runCatching { runtime.updateProfileProvider(8, "fixture") }.exceptionOrNull()
        assertEquals("更新服务返回异常，请稍后重试", failure?.message)
        assertFalse(generateSequence(failure) { it.cause }.any { it is CoreBridgeException || it.toString().contains("fixture-secret") })
        bridge.externalUpdate = {}
        runtime.updateProfileProvider(8, "fixture")
        assertEquals(2, bridge.externalUpdates)
    }

    @Test fun statisticsReadsAppliedScriptOutputWithoutRerunningOrStartingService() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val script = temporaryFolder.newFile().apply { writeText("main") }
        var scriptCalls = 0
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(8, "统计", source.path, scriptPath = script.path), temporaryFolder.newFolder(), bridge, controller,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String {
                    scriptCalls++
                    return """{"proxy-groups":[{},{}],"proxies":[{}],"rules":["MATCH,DIRECT"]}"""
                }
            })
        runtime.getProxyGroups().getOrThrow()
        assertEquals(1, scriptCalls)
        bridge.providerRead = { com.lonnnnnng.flclash.core.CoreProviderCounts(5, 2) }
        assertEquals(ProfileStatistics(8, 2, 6, 1, 2), runtime.profileStatistics(8))
        assertEquals(1, scriptCalls); assertTrue(controller.events.isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertEquals("{}", source.readText())
    }

    @Test fun statisticsRefusesUnloadedWrongProfileAndChangingInput() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val runtime = manager(ActiveProfile(8, "统计", source.path), temporaryFolder.newFolder(), bridge, FakeServiceController())
        assertTrue(runCatching { runtime.profileStatistics(8) }.isFailure)
        assertTrue(bridge.calls.isEmpty())
        runtime.getProxyGroups().getOrThrow()
        assertTrue(runCatching { runtime.profileStatistics(9) }.isFailure)
        bridge.providerRead = { source.appendText("# changed\n"); com.lonnnnnng.flclash.core.CoreProviderCounts(0, 0) }
        assertTrue(runCatching { runtime.profileStatistics(8) }.isFailure)
    }

    @Test fun statisticsWaitingForProviderDoesNotBlockStopAndCannotReturnOldCounts() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("{}") }
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(8, "统计", source.path), temporaryFolder.newFolder(), bridge, controller)
        runtime.startProxy().getOrThrow()
        val pending = CompletableDeferred<com.lonnnnnng.flclash.core.CoreProviderCounts>()
        val entered = CompletableDeferred<Unit>()
        bridge.providerRead = { entered.complete(Unit); pending.await() }
        val request = async { runCatching { runtime.profileStatistics(8) } }
        entered.await()
        controller.onStop = { pending.complete(com.lonnnnnng.flclash.core.CoreProviderCounts(0, 0)) }
        runtime.stop().getOrThrow()
        assertTrue(request.await().isFailure)
    }

    @Test fun routeReuseKeepsOnlyHeldTargetsAndOriginalDetectionTime() = runTest {
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val runtime = manager(null, temporaryFolder.newFolder(), bridge, controller)
        bridge.ipStamp = CoreRouteStamp(1, 2)
        bridge.routeRead = { CoreRouteCheck(bridge.ipStamp, listOf(false, true)) }
        val source = listOf(cachedService(ServiceTarget.GOOGLE), cachedService())
        val held = runtime.reuseServiceChecks(source, bridge.ipStamp, runtime.serviceCacheRevision.value)
        assertEquals(listOf(source[1].copy(stamp = bridge.ipStamp)), held)
        assertEquals(2, bridge.routeCalls.single().size)
        assertTrue(controller.events.isEmpty())
    }

    @Test fun routeReuseDropsFailuresEmptyHitsAndForeignEpochBeforeCoreCall() = runTest {
        val bridge = FakeBridge(); val runtime = manager(null, temporaryFolder.newFolder(), bridge, FakeServiceController())
        val item = cachedService()
        val invalid = listOf(item.copy(hits = emptyList()), item.copy(status = ServiceStatus.TIMEOUT),
            item.copy(stamp = CoreRouteStamp(2, 1)), item.copy(hits = listOf(CoreRouteHit("", "", ""))))
        assertTrue(runtime.reuseServiceChecks(invalid, bridge.ipStamp, runtime.serviceCacheRevision.value).isEmpty())
        assertTrue(bridge.routeCalls.isEmpty())
    }

    @Test fun routeReuseRejectsNetworkChangeAndCoreStampRaceAndMissingHeldRows() = runTest {
        val bridge = FakeBridge(); val runtime = manager(null, temporaryFolder.newFolder(), bridge, FakeServiceController())
        val source = listOf(cachedService())
        val revision = runtime.serviceCacheRevision.value
        runtime.networkChanged()
        assertTrue(runCatching { runtime.reuseServiceChecks(source, bridge.ipStamp, revision) }.exceptionOrNull() is ServiceRouteChangedException)
        assertTrue(bridge.routeCalls.isEmpty())
        bridge.routeRead = { CoreRouteCheck(CoreRouteStamp(1, 2), listOf(true)) }
        assertTrue(runCatching { runtime.reuseServiceChecks(source, bridge.ipStamp, runtime.serviceCacheRevision.value) }.exceptionOrNull() is ServiceRouteChangedException)
        bridge.routeRead = { CoreRouteCheck(bridge.ipStamp, emptyList()) }
        assertTrue(runCatching { runtime.reuseServiceChecks(source, bridge.ipStamp, runtime.serviceCacheRevision.value) }.isFailure)
        bridge.routeRead = { runtime.networkChanged(); CoreRouteCheck(bridge.ipStamp, listOf(true)) }
        assertTrue(runCatching { runtime.reuseServiceChecks(source, bridge.ipStamp, runtime.serviceCacheRevision.value) }.exceptionOrNull() is ServiceRouteChangedException)
    }

    @Test fun routeValidationDoesNotBlockStopAndCannotLandAfterStop() = runTest {
        val bridge = FakeBridge(); val controller = FakeServiceController()
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val runtime = manager(ActiveProfile(1, "核验", source.path), temporaryFolder.newFolder(), bridge, controller)
        runtime.startProxy().getOrThrow()
        val pending = CompletableDeferred<CoreRouteCheck>()
        bridge.routeRead = { pending.await() }
        val lookup = async { runCatching { runtime.reuseServiceChecks(listOf(cachedService()), bridge.ipStamp, runtime.serviceCacheRevision.value) } }
        runCurrent()
        controller.onStop = { pending.complete(CoreRouteCheck(bridge.ipStamp, listOf(true))) }
        runtime.stop().getOrThrow()
        assertTrue(lookup.await().exceptionOrNull() is ServiceRouteChangedException)
    }

    @Test fun nodeAndModeChangesAllowValidationButConfigReloadAndStopRequireNewDetection() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val runtime = manager(ActiveProfile(1, "缓存", source.path), temporaryFolder.newFolder(), FakeBridge(), FakeServiceController())
        runtime.startProxy().getOrThrow()
        val revision = runtime.serviceCacheRevision.value
        runtime.changeProxy("Proxy", "node-a").getOrThrow()
        runtime.setOutboundMode(CoreOutboundMode.DIRECT).getOrThrow()
        assertEquals(revision, runtime.serviceCacheRevision.value)
        runtime.stop().getOrThrow()
        assertTrue(runtime.serviceCacheRevision.value > revision)
        val stopped = runtime.serviceCacheRevision.value
        source.appendText("# changed\n")
        runtime.getProxyGroups().getOrThrow()
        assertTrue(runtime.serviceCacheRevision.value > stopped)
        runtime.startProxy().getOrThrow()
        assertTrue(runtime.serviceCacheRevision.value > stopped)
    }

    @Test fun serviceExitRequiresCurrentStampAndDirectWhileStopped() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val runtime = manager(null, temporaryFolder.newFolder(), bridge, controller)
        bridge.ipRead = { CoreIpResponse("", "", "", 200, bridge.ipStamp) }
        runtime.queryServiceExit("DIRECT", bridge.ipStamp, listOf("https://fixture.invalid"))
        assertEquals("DIRECT", bridge.exitProxy)
        assertTrue(controller.events.isEmpty())
        assertTrue(runCatching { runtime.queryServiceExit("fixture-node", bridge.ipStamp, listOf("https://fixture.invalid")) }.exceptionOrNull() is ServiceRouteChangedException)
        assertTrue(runCatching { runtime.queryServiceExit("DIRECT", CoreRouteStamp(9, 9), listOf("https://fixture.invalid")) }.exceptionOrNull() is ServiceRouteChangedException)
        bridge.ipRead = { runtime.networkChanged(); CoreIpResponse("", "", "", 200, bridge.ipStamp) }
        assertTrue(runCatching { runtime.queryServiceExit("DIRECT", bridge.ipStamp, listOf("https://fixture.invalid")) }.exceptionOrNull() is ServiceRouteChangedException)
    }

    @Test fun serviceExitTargetsDetectedNodeAndDoesNotBlockStopping() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(1, "出口", source.path), temporaryFolder.newFolder(), bridge, controller)
        runtime.startProxy().getOrThrow()
        val pending = CompletableDeferred<CoreIpResponse>()
        bridge.ipRead = { pending.await() }
        val lookup = async { runCatching { runtime.queryServiceExit("fixture-node", bridge.ipStamp, listOf("https://fixture.invalid")) } }
        runCurrent()
        assertEquals("fixture-node", bridge.exitProxy)
        controller.onStop = { pending.complete(CoreIpResponse("", "", "", 200, bridge.ipStamp)) }
        runtime.stop().getOrThrow()
        val failure = lookup.await().exceptionOrNull()
        assertTrue(failure.toString(), failure is ServiceRouteChangedException)
    }

    @Test fun serviceChecksUseDirectWithoutProfileAndDoNotStartPlatformServices() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val runtime = manager(null, temporaryFolder.newFolder(), bridge, controller)
        val target = com.lonnnnnng.flclash.core.ServiceTarget.GOOGLE
        assertEquals(target, runtime.checkServices(listOf(target)).single().target)
        assertEquals(true, bridge.serviceDirect)
        assertEquals(listOf("initialize"), bridge.calls)
        assertTrue(controller.events.isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertTrue(runCatching { runtime.checkServices(emptyList()) }.isFailure)
        assertTrue(runCatching { runtime.checkServices(listOf(target, target)) }.isFailure)
    }

    @Test fun serviceCheckDoesNotHoldLifecycleLockAndRejectsAnswerAfterStop() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val runtime = manager(ActiveProfile(1, "服务检测", source.path), temporaryFolder.newFolder(), bridge, controller)
        runtime.startProxy().getOrThrow()
        val pending = CompletableDeferred<Unit>()
        bridge.serviceRead = { pending.await(); it.map { target -> serviceResult(target, bridge.ipStamp) } }
        val check = async { runCatching { runtime.checkServices(listOf(com.lonnnnnng.flclash.core.ServiceTarget.GITHUB)) } }
        runCurrent(); assertEquals(false, bridge.serviceDirect)
        runtime.stop().getOrThrow()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        pending.complete(Unit)
        assertTrue(check.await().isFailure)
    }

    @Test fun serviceCheckRejectsIncompleteAndStaleResults() = runTest {
        val bridge = FakeBridge()
        val runtime = manager(null, temporaryFolder.newFolder(), bridge, FakeServiceController())
        val targets = listOf(com.lonnnnnng.flclash.core.ServiceTarget.GOOGLE)
        bridge.serviceRead = { emptyList() }
        assertTrue(runCatching { runtime.checkServices(targets) }.isFailure)
        bridge.serviceRead = { it.map { target -> serviceResult(target, CoreRouteStamp(9, 9)) } }
        assertTrue(runCatching { runtime.checkServices(targets) }.isFailure)
        bridge.serviceRead = { runtime.networkChanged(); it.map { target -> serviceResult(target, bridge.ipStamp) } }
        assertTrue(runCatching { runtime.checkServices(targets) }.isFailure)
    }

    private fun serviceResult(target: com.lonnnnnng.flclash.core.ServiceTarget, stamp: CoreRouteStamp) =
        com.lonnnnnng.flclash.core.CoreServiceCheck(target, com.lonnnnnng.flclash.core.ServiceStatus.AVAILABLE, checkedAt = 1L, stamp = stamp)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class FakeVpnStore(var enabled: Boolean = true, var fail: Boolean = false) : VpnSettingsStore {
        override fun read() = enabled
        override fun write(enabled: Boolean) { check(!fail); this.enabled = enabled }
    }

    @Test fun vpnPreferenceWithoutProfilePersistsWithoutStartingCoreAndFailureKeepsState() = runTest {
        val store = FakeVpnStore()
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(null, temporaryFolder.newFolder(), bridge, controller, vpnSettingsStore = store)
        manager.setVpnEnabled(false).getOrThrow()
        assertFalse(store.enabled)
        assertFalse(manager.vpnSettings.value.enabled)
        assertFalse(manager.vpnSettings.value.restartRequired)
        assertTrue(bridge.calls.isEmpty())
        assertTrue(controller.events.isEmpty())
        val rebuilt = manager(null, temporaryFolder.newFolder(), bridge, controller, vpnSettingsStore = store)
        assertFalse(rebuilt.vpnSettings.value.enabled)
        store.fail = true
        assertTrue(rebuilt.setVpnEnabled(true).isFailure)
        assertFalse(rebuilt.vpnSettings.value.enabled)
    }

    @Test fun vpnChangesStayPendingUntilExplicitRestartAndCanBeReverted() = runTest {
        val store = FakeVpnStore(false)
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7891") }
        val original = source.readText()
        val controller = FakeServiceController()
        val manager = manager(ActiveProfile(90, "VPN", source.path), temporaryFolder.newFolder(),
            FakeBridge(), controller, vpnSettingsStore = store)
        manager.startProxy().getOrThrow()
        assertFalse(controller.receivedOptions!!.enable)
        assertEquals(false, manager.vpnSettings.value.appliedEnabled)
        val events = controller.events.toList()
        manager.setVpnEnabled(true).getOrThrow()
        assertTrue(manager.vpnSettings.value.restartRequired)
        assertEquals(events, controller.events)
        manager.setVpnEnabled(false).getOrThrow()
        assertFalse(manager.vpnSettings.value.restartRequired)
        manager.setVpnEnabled(true).getOrThrow()
        manager.restartForVpnSettings().getOrThrow()
        assertTrue(controller.receivedOptions!!.enable)
        assertFalse(manager.vpnSettings.value.restartRequired)
        assertEquals(listOf("service-start", "service-stop", "service-start"), controller.events)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        manager.setVpnEnabled(false).getOrThrow()
        manager.restartForVpnSettings().getOrThrow()
        assertFalse(controller.receivedOptions!!.enable)
        manager.stop().getOrThrow()
        assertNull(manager.vpnSettings.value.appliedEnabled)
        assertFalse(manager.vpnSettings.value.restartRequired)
        assertEquals(original, source.readText())
    }

    @Test fun vpnRestartFailureClearsAppliedStateAndDoesNotPretendConnected() = runTest {
        val store = FakeVpnStore()
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7891") }
        val controller = FakeServiceController()
        val manager = manager(ActiveProfile(91, "VPN失败", source.path), temporaryFolder.newFolder(),
            FakeBridge(), controller, vpnSettingsStore = store)
        manager.startProxy().getOrThrow()
        manager.setVpnEnabled(false).getOrThrow()
        controller.failNextStart = true
        assertTrue(manager.restartForVpnSettings().isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertNull(manager.vpnSettings.value.appliedEnabled)
        assertNull(manager.runningSinceElapsedMillis.value)
        assertFalse(controller.started)
    }

    @Test fun memoryReadAndReleaseDoNotLoadProfileOrStartServices() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(null, temporaryFolder.newFolder(), bridge, controller)
        assertEquals(100L, manager.memoryStats().rss)
        assertEquals(100L, manager.releaseMemory().rss)
        assertEquals(listOf("initialize", "memory", "memory-release", "memory"), bridge.calls)
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertFalse(controller.started)
    }

    @Test fun lanPreferenceWithoutProfileDoesNotInitializeCoreAndCanBeCleared() = runTest {
        val store = FakeLanStore()
        val bridge = FakeBridge()
        val manager = manager(null, temporaryFolder.newFolder(), bridge, FakeServiceController(), lanSharingStore = store)
        manager.updateLanSharing(true)
        manager.refreshLanSharing()
        assertEquals(true, store.value)
        assertTrue(manager.lanSharing.value.config.allowLan)
        assertFalse(manager.lanSharing.value.hasConfig)
        manager.updateLanSharing(null)
        assertEquals(null, store.value)
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun lanSettingPatchesOnlyStagedInputSurvivesReloadAndMatchesPreview() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\nallow-lan: false\ncustom-field: keep\n") }
        val original = source.readText()
        val profile = ActiveProfile(73, "内网验收", source.path)
        val home = temporaryFolder.newFolder()
        val store = FakeLanStore()
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(profile, home, bridge, controller, lanSharingStore = store)
        manager.refreshLanSharing()
        manager.updateLanSharing(true)
        assertTrue(manager.lanSharing.value.config.allowLan)
        assertEquals(7891, manager.lanSharing.value.config.mixedPort)
        assertEquals(true, store.value)
        assertEquals(original, source.readText())
        assertTrue(File(home, "config.yaml").readText().contains("allow-lan: true"))
        assertTrue(manager.previewConfig(73).content.contains("allow-lan: true"))
        assertFalse(controller.started)
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rebuilt = manager(profile, home, FakeBridge(), controller, lanSharingStore = store)
        rebuilt.refreshLanSharing()
        assertTrue(rebuilt.lanSharing.value.config.allowLan)
        rebuilt.updateLanSharing(null)
        assertFalse(rebuilt.lanSharing.value.config.allowLan)
        assertEquals(null, store.value)
        assertEquals(original, source.readText())
    }

    @Test fun runningLanUpdateAndPartialFailurePreserveSessionAndRollbackEverything() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\nallow-lan: false\n") }
        val home = temporaryFolder.newFolder()
        val store = FakeLanStore()
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(ActiveProfile(74, "内网事务", source.path), home, bridge, controller, lanSharingStore = store)
        manager.startProxy().getOrThrow()
        val services = controller.events.toList()
        val before = File(home, "config.yaml").readText()
        bridge.failLanUpdate = true
        assertTrue(runCatching { manager.updateLanSharing(true) }.isFailure)
        assertEquals(before, File(home, "config.yaml").readText())
        assertFalse(bridge.lan.allowLan)
        assertEquals(null, store.value)
        assertEquals(services, controller.events)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        manager.updateLanSharing(true)
        assertTrue(bridge.lan.allowLan)
        assertEquals(services, controller.events)
        assertEquals(1, bridge.calls.count { it == "setup" })
    }

    @Test fun lanPersistenceFailureRestoresCoreAndDoesNotRerunBoundScript() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\nallow-lan: false\n") }
        val script = temporaryFolder.newFile().apply { writeText("fixture") }
        var evaluations = 0
        val store = FakeLanStore()
        val bridge = FakeBridge()
        val manager = manager(ActiveProfile(75, "内网脚本", source.path, scriptPath = script.path),
            temporaryFolder.newFolder(), bridge, FakeServiceController(), lanSharingStore = store,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String { evaluations++; return configJson }
            })
        manager.refreshLanSharing()
        store.failNext = true
        assertTrue(runCatching { manager.updateLanSharing(true) }.isFailure)
        assertFalse(bridge.lan.allowLan)
        assertEquals(null, store.value)
        assertEquals(1, evaluations)
        manager.updateLanSharing(true)
        assertEquals(1, evaluations)
    }

    @Test fun lanOverrideFollowsHotProfileSwitchAndRestartWithoutChangingOriginals() = runTest {
        val a = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\nallow-lan: false\n") }
        val b = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\nallow-lan: true\n") }
        val originals = listOf(a.readText(), b.readText())
        val profiles = listOf(ActiveProfile(76, "A", a.path), ActiveProfile(77, "B", b.path))
        var selected = 76L
        val store = FakeLanStore(false)
        val bridge = FakeBridge()
        val services = FakeServiceController()
        val manager = CoreRuntimeManager(CoreRuntimePaths(temporaryFolder.newFolder()),
            object : ActiveProfileProvider { override suspend fun activeProfile() = profiles.single { it.id == selected } },
            bridge, services, DefaultVpnOptionsProvider(), FakeRuntimeResumeStore(),
            elapsedRealtimeMillis = { 0L }, lanSharingStore = store)
        manager.startProxy().getOrThrow()
        manager.selectProfile(77) { selected = checkNotNull(it) }
        manager.refreshLanSharing()
        assertFalse(manager.lanSharing.value.config.allowLan)
        assertEquals(7891, manager.lanSharing.value.config.mixedPort)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(1, services.events.count { it == "service-start" })
        manager.stop().getOrThrow()
        manager.startProxy().getOrThrow()
        manager.refreshLanSharing()
        assertFalse(manager.lanSharing.value.config.allowLan)
        assertEquals(originals, listOf(a.readText(), b.readText()))
    }

    private class FakeLanStore(var value: Boolean? = null) : LanSharingStore {
        var failNext = false
        override fun read() = value
        override fun write(value: Boolean?) {
            if (failNext) { failNext = false; error("保存失败") }
            this.value = value
        }
    }

    @Before
    fun clearDiagnosticHistory() {
        RuntimeDiagnosticHistory.clear()
    }

    @Test fun memoryReleasePreservesAnExistingRunningSession() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(ActiveProfile(46, "内存验收", source.path), temporaryFolder.newFolder(), bridge, controller)
        manager.startProxy().getOrThrow()
        val callsBefore = bridge.calls.toList()
        val servicesBefore = controller.events.toList()
        manager.memoryStats()
        manager.releaseMemory()
        // 作者 long：运行态回收只能读取/回收内存，不能重新应用配置、重建监听或启停现有服务。
        assertEquals(callsBefore + listOf("memory", "memory-release", "memory"), bridge.calls)
        assertEquals(servicesBefore, controller.events)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
    }

    @Test
    fun `stopped valid profile loads nodes selects and tests without a service`() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(ActiveProfile(45, "未启动", source.path), temporaryFolder.newFolder(), bridge, controller)
        manager.getProxyGroups().getOrThrow()
        manager.changeProxy("Proxy", "node-b").getOrThrow()
        manager.testDelay("node-b", "https://example.com", 1000).getOrThrow()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertFalse(controller.started)
        assertTrue(bridge.calls.contains("setup"))
        assertFalse(bridge.calls.contains("listener-start"))
    }

    @Test
    fun `profile hot switch preserves running state and rolls back invalid input`() = runTest {
        val sourceA = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val sourceB = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\n") }
        val profiles = listOf(ActiveProfile(51, "A", sourceA.path), ActiveProfile(52, "B", sourceB.path))
        var selected = 51L
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val home = temporaryFolder.newFolder()
        val manager = CoreRuntimeManager(
            CoreRuntimePaths(home), object : ActiveProfileProvider {
                override suspend fun activeProfile() = profiles.single { it.id == selected }
            }, bridge, controller, DefaultVpnOptionsProvider(), FakeRuntimeResumeStore(), elapsedRealtimeMillis = { 0L },
        )
        manager.startProxy().getOrThrow()
        manager.selectProfile(52) { selected = checkNotNull(it) }
        assertEquals(52L, selected)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(7891, com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(File(home, "config.yaml").readText())).asJsonObject["mixed-port"].asInt)
        assertEquals(1, controller.events.count { it == "service-start" })
        sourceA.delete()
        assertTrue(runCatching { manager.selectProfile(51) { selected = checkNotNull(it) } }.isFailure)
        assertEquals(52L, selected)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(7891, com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(File(home, "config.yaml").readText())).asJsonObject["mixed-port"].asInt)
    }

    @Test fun hotSwitchRebuildsChangedVpnPortAndRestoresItOnServiceFailure() = runTest {
        val a = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val b = temporaryFolder.newFile().apply { writeText("mixed-port: 17890\n") }
        val profiles = listOf(ActiveProfile(1, "A", a.path), ActiveProfile(2, "B", b.path))
        var selected = 1L
        val home = temporaryFolder.newFolder()
        val services = FakeServiceController()
        val manager = CoreRuntimeManager(CoreRuntimePaths(home), object : ActiveProfileProvider {
            override suspend fun activeProfile() = profiles.single { it.id == selected }
        }, FakeBridge(), services, DefaultVpnOptionsProvider(appliedConfigFile = File(home, "config.yaml")),
            FakeRuntimeResumeStore(), elapsedRealtimeMillis = { 0L })
        manager.startProxy().getOrThrow()
        assertEquals(7890, services.receivedOptions?.port)
        manager.selectProfile(2) { selected = checkNotNull(it) }
        assertEquals(17890, services.receivedOptions?.port)
        assertEquals(listOf("service-start", "service-stop", "service-start"), services.events)
        services.failNextStart = true
        assertTrue(runCatching { manager.selectProfile(1) { selected = checkNotNull(it) } }.isFailure)
        assertEquals(2L, selected)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(17890, services.receivedOptions?.port)
        assertTrue(services.started)
        assertEquals(17890, com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(File(home, "config.yaml").readText())).asJsonObject["mixed-port"].asInt)
        manager.stop().getOrThrow()
    }

    @Test
    fun `stopped preview uses startup transforms without touching source staged file or core`() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val script = temporaryFolder.newFile().apply { writeText("function main(c) { return c }") }
        val home = temporaryFolder.newFolder()
        val staged = File(home, "config.yaml").apply { writeText("previous input") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        var scriptCalls = 0
        val manager = manager(
            ActiveProfile(41, "预览", source.path, scriptPath = script.path,
                overridesJson = ProfileProviderOverrides(mapOf("cloud" to ProxyProviderOverride(timeout = 2000))).toJsonString()),
            home, bridge, controller, profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String {
                    scriptCalls++
                    return "{\"mixed-port\":7891,\"proxy-providers\":{\"cloud\":{\"type\":\"http\"}}}"
                }
            },
        )
        val preview = manager.previewConfig(41)
        assertFalse(preview.isApplied)
        assertTrue(preview.content.contains("mixed-port: 7891"))
        assertTrue(preview.content.contains("timeout: 2000"))
        assertEquals("mixed-port: 7890\n", source.readText())
        assertEquals("previous input", staged.readText())
        assertTrue(bridge.calls.isEmpty())
        assertFalse(controller.started)
        manager.startProxy().getOrThrow()
        assertEquals(preview.content, staged.readText())
        val applied = manager.previewConfig(41)
        assertTrue(applied.isApplied)
        assertEquals(preview.content, applied.content)
        assertEquals(2, scriptCalls)
    }

    @Test
    fun `preview rejects missing profile missing file and excessive input`() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val manager = manager(ActiveProfile(42, "边界", source.path), temporaryFolder.newFolder(), FakeBridge(), FakeServiceController())
        assertEquals("配置不存在", runCatching { manager.previewConfig(43) }.exceptionOrNull()?.message)
        source.writeBytes(ByteArray(2 * 1024 * 1024 + 1))
        assertEquals("配置超过 2 MiB，暂不支持预览", runCatching { manager.previewConfig(42) }.exceptionOrNull()?.message)
        assertTrue(source.delete())
        assertEquals("配置文件不存在", runCatching { manager.previewConfig(42) }.exceptionOrNull()?.message)
    }

    @Test
    fun `pending preview does not block stop and rejects a changed session`() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val script = temporaryFolder.newFile().apply { writeText("function main(c) { return c }") }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val manager = manager(ActiveProfile(44, "并发", source.path, scriptPath = script.path),
            temporaryFolder.newFolder(), FakeBridge(), FakeServiceController(),
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String {
                    entered.complete(Unit)
                    release.await()
                    return configJson
                }
            })
        val pending = async { runCatching { manager.previewConfig(44) } }
        entered.await()
        manager.stop().getOrThrow()
        assertFalse(pending.isCompleted)
        release.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
    }

    @Test
    fun `noncurrent preview renders own transforms while stopped and running without selecting or applying it`() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val targetSource = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\n") }
        val script = temporaryFolder.newFile().apply { writeText("function main(c) { return c }") }
        val current = ActiveProfile(45, "当前配置", source.path)
        val target = ActiveProfile(46, "另一份配置", targetSource.path, scriptPath = script.path,
            overridesJson = ProfileProviderOverrides(mapOf("cloud" to ProxyProviderOverride(timeout = 2100))).toJsonString())
        val provider = object : ActiveProfileProvider {
            override suspend fun activeProfile() = current
            override suspend fun profileById(id: Long) = listOf(current, target).firstOrNull { it.id == id }
        }
        val home = temporaryFolder.newFolder()
        val staged = File(home, "config.yaml").apply { writeText("previous input") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        var scriptCalls = 0
        val manager = manager(current, home, bridge, controller, profileProvider = provider,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String {
                    scriptCalls++
                    assertTrue(configJson.contains("7891"))
                    return "{\"mixed-port\":7892,\"proxy-providers\":{\"cloud\":{\"type\":\"http\"}}}"
                }
            })
        val stopped = manager.previewConfig(target.id)
        assertEquals(target.id, stopped.profileId)
        assertFalse(stopped.isApplied)
        assertTrue(stopped.content.contains("mixed-port: 7892"))
        assertTrue(stopped.content.contains("timeout: 2100"))
        assertEquals("previous input", staged.readText())
        assertTrue(bridge.calls.isEmpty())
        assertFalse(controller.started)
        manager.startProxy().getOrThrow()
        val appliedInput = staged.readText()
        val calls = bridge.calls.toList()
        val running = manager.previewConfig(target.id)
        assertEquals(stopped.content, running.content)
        assertFalse(running.isApplied)
        assertEquals(appliedInput, staged.readText())
        assertEquals(calls, bridge.calls)
        assertEquals(current.id, provider.activeProfileId())
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertTrue(manager.previewConfig(current.id).isApplied)
        assertEquals(2, scriptCalls)
        assertEquals("mixed-port: 7891\n", targetSource.readText())
    }

    @Test
    fun `valid target preview does not resolve broken current script or invoke core`() = runTest {
        val target = ActiveProfile(48, "有效目标", temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }.path)
        val provider = object : ActiveProfileProvider {
            override suspend fun activeProfile(): ActiveProfile = error("绑定脚本不存在")
            override suspend fun activeProfileId() = 47L
            override suspend fun profileById(id: Long) = target.takeIf { it.id == id }
        }
        val bridge = FakeBridge()
        val manager = manager(null, temporaryFolder.newFolder(), bridge, FakeServiceController(), profileProvider = provider)
        assertEquals(target.id, manager.previewConfig(target.id).profileId)
        assertTrue(bridge.calls.isEmpty())
    }

    @Test
    fun `noncurrent preview rejects target edits deletion same timestamp file replacements and LAN changes`() = runTest {
        for (change in listOf("metadata", "deleted", "source", "script", "lan", "selection")) {
            val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
            val targetSource = temporaryFolder.newFile().apply { writeText("mixed-port: 7891\n") }
            val script = temporaryFolder.newFile().apply { writeText("function main(c) { return c }") }
            val current = ActiveProfile(49, "当前配置", source.path)
            val target = ActiveProfile(50, "目标配置", targetSource.path, scriptPath = script.path)
            val profiles = java.util.concurrent.ConcurrentHashMap(mapOf(current.id to current, target.id to target))
            val selected = java.util.concurrent.atomic.AtomicReference(current.id)
            val provider = object : ActiveProfileProvider {
                override suspend fun activeProfile() = profiles[selected.get()]
                override suspend fun profileById(id: Long) = profiles[id]
            }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val lan = object : LanSharingStore {
                var enabled = false
                override fun read(): Boolean = enabled
                override fun write(value: Boolean?) { enabled = value ?: false }
            }
            val bridge = FakeBridge()
            val manager = manager(current, temporaryFolder.newFolder(), bridge, FakeServiceController(),
                profileProvider = provider, lanSharingStore = lan,
                profileScriptEvaluator = object : ProfileScriptEvaluator {
                    override suspend fun evaluate(script: String, configJson: String): String {
                        entered.complete(Unit)
                        release.await()
                        return configJson
                    }
                })
            val pending = async { runCatching { manager.previewConfig(target.id) } }
            entered.await()
            when (change) {
                "metadata" -> profiles[target.id] = target.copy(overridesJson = "{\"dns\":{\"enable\":true}}")
                "deleted" -> profiles.remove(target.id)
                "source" -> {
                    val modified = targetSource.lastModified()
                    targetSource.writeText("mixed-port: 7892\n")
                    assertTrue(targetSource.setLastModified(modified))
                }
                "script" -> {
                    val modified = script.lastModified()
                    script.writeText("function main(d) { return d }")
                    assertTrue(script.setLastModified(modified))
                }
                "lan" -> lan.enabled = true
                "selection" -> selected.set(target.id)
            }
            release.complete(Unit)
            assertEquals(change, "配置或核心状态已变化，请重试", pending.await().exceptionOrNull()?.message)
            assertTrue(bridge.calls.isEmpty())
        }
    }

    @Test
    fun `slow noncurrent preview does not block a hot switch and cannot return for the old session`() = runTest {
        val first = ActiveProfile(54, "原配置", temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }.path)
        val target = ActiveProfile(55, "预览目标", temporaryFolder.newFile().apply { writeText("mixed-port: 7891\n") }.path,
            scriptPath = temporaryFolder.newFile().apply { writeText("function main(c) { return c }") }.path)
        val next = ActiveProfile(56, "切换目标", temporaryFolder.newFile().apply { writeText("mixed-port: 7892\n") }.path)
        val profiles = listOf(first, target, next).associateBy { it.id }
        val selected = java.util.concurrent.atomic.AtomicReference(first.id)
        val provider = object : ActiveProfileProvider {
            override suspend fun activeProfile() = profiles[selected.get()]
            override suspend fun profileById(id: Long) = profiles[id]
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val home = temporaryFolder.newFolder()
        val manager = manager(first, home, FakeBridge(), FakeServiceController(), profileProvider = provider,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String {
                    entered.complete(Unit); release.await(); return configJson
                }
            })
        manager.startProxy().getOrThrow()
        val pending = async { runCatching { manager.previewConfig(target.id) } }
        entered.await()
        manager.selectProfile(next.id) { selected.set(it) }
        assertFalse(pending.isCompleted)
        assertEquals(next.id, selected.get())
        assertEquals(7892, com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(File(home, "config.yaml").readText())).asJsonObject["mixed-port"].asInt)
        release.complete(Unit)
        assertEquals("配置或核心状态已变化，请重试", pending.await().exceptionOrNull()?.message)
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
    }

    @Test
    fun `valid profile is staged and applied before listener starts`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply {
            writeText(
                """
                    mixed-port: 7890
                    proxy-providers:
                      cloud:
                        type: http
                        url: https://example.com/cloud
                """.trimIndent() + "\n",
            )
        }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(
                id = 7,
                label = "测试配置",
                filePath = source.path,
                selectedMap = mapOf("GLOBAL" to "node-a"),
                overridesJson = ProfileProviderOverrides(
                    mapOf("cloud" to ProxyProviderOverride(timeout = 2_000)),
                ).toJsonString(),
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()

        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(
            """
                mixed-port: 7890
                proxy-providers:
                  cloud:
                    type: http
                    url: https://example.com/cloud

                    health-check:
                      enable: true
                      timeout: 2000
                dns:
                  enable: true
                  enhanced-mode: fake-ip
                  nameserver:
                    - https://doh.pub/dns-query
                    - https://dns.alidns.com/dns-query
            """.trimIndent() + "\n",
            File(homeDir, "config.yaml").readText(),
        )
        assertEquals(listOf("initialize", "validate", "setup", "mode-read", "log-start", "listener-start"), bridge.calls)
        assertEquals(mapOf("GLOBAL" to "node-a"), bridge.setupSelectedMap)
        assertEquals(listOf("service-start"), controller.events)
        assertTrue(controller.started)
        assertEquals("mips", controller.receivedOptions?.stack)
        assertTrue(resumeStore.shouldResume)
    }

    @Test
    fun `core preparation runs before native initialization`() = runTest {
        val source = temporaryFolder.newFile("prepare-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        var prepared = false
        val manager = manager(
            profile = ActiveProfile(24, "离线核心资源", source.path),
            homeDir = temporaryFolder.newFolder("prepared-core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            prepareCore = { prepared = true },
        )

        manager.startProxy().getOrThrow()

        assertTrue(prepared)
    }

    @Test
    fun `dialer overrides are included in staged config before validation`() = runTest {
        val source = temporaryFolder.newFile("dialer-profile.yaml").apply {
            writeText(
                """
                    mixed-port: 7890
                    proxies:
                      - name: node-a
                        type: vmess
                      - name: node-b
                        type: trojan
                    proxy-groups:
                      - name: Relay
                        type: select
                        proxies:
                          - node-b
                """.trimIndent() + "\n",
            )
        }
        val homeDir = temporaryFolder.newFolder("dialer-core")
        val bridge = FakeBridge()
        val manager = manager(
            profile = ActiveProfile(
                id = 8,
                label = "Dialers 配置",
                filePath = source.path,
                overridesJson = ProfileProviderOverrides(
                    dialers = mapOf("node-a" to "Relay"),
                ).toJsonString(),
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = FakeServiceController(),
        )

        manager.startProxy().getOrThrow()

        val staged = File(homeDir, "config.yaml").readText()
        assertTrue(staged.contains("dialer-proxy: Relay"))
        assertEquals(listOf("initialize", "validate", "setup", "mode-read", "log-start", "listener-start"), bridge.calls)
    }

    @Test
    fun `bound profile script transforms staged config before provider overrides`() = runTest {
        val source = temporaryFolder.newFile("profile-script.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val script = temporaryFolder.newFile("profile.js").apply {
            writeText("function main(config) { return config }")
        }
        val homeDir = temporaryFolder.newFolder("script-core")
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(
                id = 21,
                label = "脚本配置",
                filePath = source.path,
                scriptPath = script.path,
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String =
                    "{\"mixed-port\":7891,\"proxy-providers\":{}}"
            },
        )

        manager.startProxy().getOrThrow()

        assertTrue(File(homeDir, "config.yaml").readText().contains("mixed-port: 7891"))
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
    }

    @Test
    fun `script failure prevents core setup and service start`() = runTest {
        val source = temporaryFolder.newFile("script-failure.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val script = temporaryFolder.newFile("broken.js").apply {
            writeText("function main(config) { throw new Error('broken') }")
        }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(
                id = 22,
                label = "失败脚本",
                filePath = source.path,
                scriptPath = script.path,
            ),
            homeDir = temporaryFolder.newFolder("failure-core"),
            bridge = bridge,
            controller = controller,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String =
                    error("脚本执行失败")
            },
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertFalse(controller.started)
        assertEquals(listOf("initialize"), bridge.calls)
    }

    @Test
    fun `invalid profile never starts service or reports running`() = runTest {
        val source = temporaryFolder.newFile("invalid.yaml").apply { writeText("broken") }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge(validationError = IllegalArgumentException("配置格式错误"))
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(8, "错误配置", source.path),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertFalse(controller.started)
        assertFalse(bridge.listenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `core failure diagnostics keep code and redact raw core message`() = runTest {
        val source = temporaryFolder.newFile("invalid-with-detail.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val diagnosticStore = FakeRuntimeDiagnosticStore()
        val bridge = FakeBridge(
            validationError = CoreBridgeException(
                code = "invalid_config",
                message = "line 7: token=secret is not allowed",
            ),
        )
        val manager = manager(
            profile = ActiveProfile(23, "错误配置详情", source.path),
            homeDir = temporaryFolder.newFolder("detail-core"),
            bridge = bridge,
            controller = FakeServiceController(),
            runtimeDiagnosticStore = diagnosticStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(
            "line 7: token=secret is not allowed",
            result.exceptionOrNull()?.message,
        )
        assertEquals(
            "invalid_config: line 7: [REDACTED_SECRET] is not allowed",
            diagnosticStore.events.single().detail,
        )
        assertEquals(listOf("initialize", "validate"), bridge.calls)
    }

    @Test
    fun `missing profile fails before core initialization`() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(emptyList<String>(), bridge.calls)
        assertFalse(controller.started)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `proxy switch failure is returned without hiding the core error`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge(proxyChangeError = IllegalStateException("节点不可用"))
        val manager = manager(
            profile = ActiveProfile(9, "可用配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.changeProxy("Proxy", "香港-01")

        assertTrue(result.isFailure)
        assertEquals("节点不可用", result.exceptionOrNull()?.message)
        assertEquals(0, bridge.proxyGroupsReads)
    }

    @Test
    fun `delay test fails while core is stopped`() = runTest {
        val bridge = FakeBridge()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )

        val result = manager.testDelay("香港-01", "https://example.com", 8_000)

        assertTrue(result.isFailure)
        assertEquals("请先添加可用配置", result.exceptionOrNull()?.message)
    }

    @Test
    fun `delay bridge failure is returned without fabricating a result`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge(delayError = IllegalStateException("测速失败"))
        val manager = manager(
            profile = ActiveProfile(10, "可用配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.testDelay("香港-01", "https://example.com", 8_000)

        assertTrue(result.isFailure)
        assertEquals("测速失败", result.exceptionOrNull()?.message)
    }

    @Test
    fun `batch delay keeps successful values and individual failures`() = runTest {
        val source = temporaryFolder.newFile("batch-delay-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val bridge = FakeBridge(delayErrors = setOf("node-b"))
        val manager = manager(
            profile = ActiveProfile(11, "批量测速", source.path),
            homeDir = temporaryFolder.newFolder("batch-delay-core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val results = manager.testDelayBatch(
            requests = listOf(
                ProxyDelayRequest("node-a", "https://one"),
                ProxyDelayRequest("node-b", "https://one"),
            ),
            timeoutMs = CoreBridge.DEFAULT_DELAY_TIMEOUT_MS,
            maxConcurrent = 2,
        ).getOrThrow()

        assertEquals(123, results[0].result?.value)
        assertEquals("测速失败", results[1].error?.message)
    }

    @Test
    fun `batch delay propagates cancellation instead of returning failure`() = runTest {
        val source = temporaryFolder.newFile("batch-delay-cancel-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val manager = manager(
            profile = ActiveProfile(12, "批量测速取消", source.path),
            homeDir = temporaryFolder.newFolder("batch-delay-cancel-core"),
            bridge = FakeBridge(delayError = CancellationException("已取消")),
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val error = assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.testDelayBatch(
                    requests = listOf(ProxyDelayRequest("node-a", "https://one")),
                    timeoutMs = CoreBridge.DEFAULT_DELAY_TIMEOUT_MS,
                    maxConcurrent = 32,
                ).getOrThrow()
            }
        }
        assertEquals("已取消", error.message)
    }

    @Test
    fun `probe result keeps the core route attribution`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val manager = manager(
            profile = ActiveProfile(14, "诊断配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.probe("https://example.com").getOrThrow()

        assertEquals(listOf("GLOBAL", "FR-1-01"), result?.chains)
        assertEquals("DOMAIN-SUFFIX", result?.rule)
    }

    @Test
    fun `probe fails while core is stopped`() = runTest {
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )

        val result = manager.probe("https://example.com")

        assertTrue(result.isFailure)
        assertEquals("请先启动核心", result.exceptionOrNull()?.message)
    }

    @Test
    fun `clear dns cache delegates to the core and reports success`() = runTest {
        val bridge = FakeBridge()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )

        val result = manager.clearDnsCache()

        assertTrue(result.isSuccess)
        assertEquals(listOf("clear-dns-cache"), bridge.calls)
    }

    @Test
    fun `clear dns cache turns a negative core acknowledgement into failure`() = runTest {
        val bridge = FakeBridge(dnsCacheClearResult = false)
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )

        val result = manager.clearDnsCache()

        assertTrue(result.isFailure)
        assertEquals("清除 DNS 缓存失败", result.exceptionOrNull()?.message)
    }

    @Test
    fun `runtime stats are read from the core only while running`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val manager = manager(
            profile = ActiveProfile(12, "统计配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )

        assertTrue(manager.getStats().isFailure)
        manager.startProxy().getOrThrow()

        assertEquals(
            CoreRuntimeStats(
                traffic = CoreTraffic(12, 34),
                totalTraffic = CoreTraffic(120, 340),
                connectionCount = 2,
            ),
            manager.getStats().getOrThrow(),
        )
        assertTrue(manager.getConnections().isSuccess)
    }

    @Test
    fun `uptime belongs to a successful core session and restarts from zero`() = runTest {
        val source = temporaryFolder.newFile("uptime.yaml").apply { writeText("mixed-port: 7890\n") }
        var now = 5_000L
        val manager = manager(
            profile = ActiveProfile(40, "会话计时", source.path),
            homeDir = temporaryFolder.newFolder("uptime-core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            elapsedRealtimeMillis = { now },
        )
        assertEquals(null, manager.uptimeMillis())
        manager.startProxy().getOrThrow()
        now += 3_661_000L
        assertEquals(3_661_000L, manager.uptimeMillis())
        manager.startProxy().getOrThrow()
        assertEquals(5_000L, manager.runningSinceElapsedMillis.value)
        manager.stop().getOrThrow()
        assertEquals(null, manager.uptimeMillis())
        assertEquals(null, manager.runningSinceElapsedMillis.value)
        manager.startProxy().getOrThrow()
        assertEquals(0L, manager.uptimeMillis())
        now += 1_000L
        assertEquals(1_000L, manager.uptimeMillis())
        manager.handleVpnRevoked().getOrThrow()
        assertEquals(null, manager.uptimeMillis())
        assertEquals(null, manager.runningSinceElapsedMillis.value)
    }

    @Test
    fun `cancelled stats propagate instead of becoming a failed sample`() = runTest {
        val source = temporaryFolder.newFile("cancel-stats.yaml").apply { writeText("mixed-port: 7890\n") }
        val manager = manager(
            profile = ActiveProfile(41, "取消采样", source.path),
            homeDir = temporaryFolder.newFolder("cancel-stats-core"),
            bridge = FakeBridge(trafficError = CancellationException("离开前台")),
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()
        try {
            manager.getStats()
            throw AssertionError("取消采样必须传播取消异常")
        } catch (_: CancellationException) {
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        }
    }

    @Test
    fun `stopped mode choice is persisted and applied after profile setup`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mode: rule\n") }
        val bridge = FakeBridge()
        val store = FakeOutboundModeStore()
        val manager = manager(
            profile = ActiveProfile(33, "模式配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
            outboundModeStore = store,
        )

        manager.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow()
        manager.startProxy().getOrThrow()

        assertEquals(CoreOutboundMode.GLOBAL, manager.outboundMode.value)
        assertEquals(CoreOutboundMode.GLOBAL, store.mode)
        assertTrue(bridge.calls.indexOf("setup") < bridge.calls.indexOf("mode-global"))
        assertTrue(bridge.calls.none { it == "mode-read" })
    }

    @Test
    fun `first startup reads mode from the staged profile`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mode: direct\n") }
        val bridge = FakeBridge(configuredMode = CoreOutboundMode.DIRECT)
        val manager = manager(
            profile = ActiveProfile(34, "直连配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )

        manager.startProxy().getOrThrow()

        assertEquals(CoreOutboundMode.DIRECT, manager.outboundMode.value)
        assertTrue(bridge.calls.contains("mode-read"))
        assertTrue(bridge.calls.none { it.startsWith("mode-") && it != "mode-read" })
    }

    @Test
    fun `core event listener follows runtime lifecycle`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(13, "事件配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()
        assertTrue(bridge.eventListenerStarted)
        assertTrue(bridge.logListenerStarted)

        manager.stop().getOrThrow()
        assertFalse(bridge.eventListenerStarted)
        assertFalse(bridge.logListenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `core lifecycle records start stop and restart evidence`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val store = FakeRuntimeDiagnosticStore()
        val manager = manager(
            profile = ActiveProfile(18, "生命周期配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            runtimeDiagnosticStore = store,
        )

        manager.startProxy().getOrThrow()
        manager.stop().getOrThrow()
        manager.startProxy().getOrThrow()

        assertEquals(
            listOf(
                RuntimeDiagnosticKind.CORE_STARTED,
                RuntimeDiagnosticKind.CORE_STOPPED,
                RuntimeDiagnosticKind.CORE_RESTARTED,
            ),
            RuntimeDiagnosticHistory.events.value.map { it.kind },
        )
        assertEquals(
            RuntimeDiagnosticHistory.events.value.map { it.kind },
            store.events.map { it.kind },
        )
    }

    @Test
    fun `restored running history marks the next automatic start as restart`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val store = FakeRuntimeDiagnosticStore(
            mutableListOf(
                RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_STARTED, "旧进程启动", 1L),
            ),
        )
        val manager = manager(
            profile = ActiveProfile(19, "重建配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            runtimeDiagnosticStore = store,
        )

        manager.restoreDiagnosticHistory(markNextStartAsRestart = true)
        manager.startProxy().getOrThrow()

        assertEquals(
            listOf(RuntimeDiagnosticKind.CORE_STARTED, RuntimeDiagnosticKind.CORE_RESTARTED),
            RuntimeDiagnosticHistory.events.value.map { it.kind },
        )
    }

    @Test
    fun `vpn start failure never reports running and cleans the service`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController(
            startError = IllegalStateException("VPN 权限未授予"),
        )
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(11, "VPN 失败", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
        assertEquals(null, manager.runningSinceElapsedMillis.value)
        assertEquals(null, manager.uptimeMillis())
        assertFalse(bridge.listenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `vpn revoke stops services and marks a running runtime failed`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(15, "撤销配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()

        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
        assertFalse(bridge.listenerStarted)
        assertFalse(bridge.eventListenerStarted)
        assertFalse(bridge.logListenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `repeated vpn revoke is idempotent`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(16, "重复撤销", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = controller,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()

        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
    }

    @Test
    fun `runtime can restart after vpn revoke`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(17, "撤销后重启", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()
        manager.startProxy().getOrThrow()

        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(2, controller.events.count { it == "service-start" })
        assertTrue(bridge.listenerStarted)
        assertTrue(bridge.eventListenerStarted)
        assertTrue(bridge.logListenerStarted)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun ipWaitDoesNotHoldStopLockAndOldSessionResponseIsRejected() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val pending = CompletableDeferred<CoreIpResponse>()
        bridge.ipRead = { pending.await() }
        val manager = manager(ActiveProfile(1, "出口会话", source.path), temporaryFolder.newFolder(), bridge, FakeServiceController())
        manager.startProxy().getOrThrow()
        val lookup = async { runCatching { manager.queryOutboundIp(listOf("https://fixture.invalid")) } }
        runCurrent()
        manager.stop().getOrThrow()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        manager.startProxy().getOrThrow()
        pending.complete(CoreIpResponse("", "", "", 200, bridge.ipStamp))
        assertTrue(lookup.await().isFailure)
        manager.stop().getOrThrow()
    }

    @Test
    fun ipAnswerRequiresMatchingCoreRouteAndLocalRevision() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val manager = manager(ActiveProfile(1, "出口版本", source.path), temporaryFolder.newFolder(), bridge, FakeServiceController())
        manager.startProxy().getOrThrow()
        val valid = CoreIpResponse("", "", "", 200, bridge.ipStamp)
        bridge.ipRead = { valid }
        assertEquals(valid, manager.queryIpMetadata("https://fixture.invalid"))
        bridge.ipStamp = CoreRouteStamp(1, 2)
        assertTrue(runCatching { manager.queryIpMetadata("https://fixture.invalid") }.isFailure)
        bridge.ipRead = { manager.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow(); valid }
        assertTrue(runCatching { manager.queryIpMetadata("https://fixture.invalid") }.isFailure)
        manager.stop().getOrThrow()
    }

    @Test fun extensionApplyUsesSameProfileWithoutStartingAndGuardsStaleTarget() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("rules: ['MATCH,DIRECT']\n") }
        var profile = ActiveProfile(71, "扩展", source.path)
        val bridge = FakeBridge(); val controller = FakeServiceController(); val home = temporaryFolder.newFolder()
        val runtime = manager(profile, home, bridge, controller, profileProvider = object : ActiveProfileProvider {
            override suspend fun activeProfile() = profile
        })
        assertFalse(runtime.profileInputsApplied(71))
        runtime.applyCurrentProfile(71)
        assertTrue(runtime.profileInputsApplied(71)); assertTrue(controller.events.isEmpty()); assertFalse(bridge.listenerStarted)
        profile = profile.copy(standardExtension = StandardProfileExtension(listOf(ExtensionRule("x", "DOMAIN,example.test,MATCH")), "Main"))
        assertFalse(runtime.profileInputsApplied(71))
        runtime.applyCurrentProfile(71)
        assertTrue(File(home, "config.yaml").readText().contains("DOMAIN,example.test,Main"))
        assertEquals("rules: ['MATCH,DIRECT']\n", source.readText())
        assertTrue(runCatching { runtime.applyCurrentProfile(72) }.isFailure)
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value); assertTrue(controller.events.isEmpty())
    }

    @Test fun backgroundProxyRefreshKeepsDeferredExtensionUntilExplicitApply() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("rules: ['MATCH,DIRECT']\n") }
        var profile = ActiveProfile(71, "扩展", source.path, deferExtensionApplication = true)
        val bridge = FakeBridge(); val controller = FakeServiceController(); val home = temporaryFolder.newFolder()
        val runtime = manager(profile, home, bridge, controller, lanSharingStore = FakeLanStore(), profileProvider = object : ActiveProfileProvider {
            override suspend fun activeProfile() = profile
        })
        runtime.getProxyGroups().getOrThrow()
        val applied = File(home, "config.yaml").readText()
        profile = profile.copy(standardExtension = StandardProfileExtension(listOf(ExtensionRule("x", "DOMAIN,example.test,DIRECT"))))
        runtime.getProxyGroups().getOrThrow()
        assertEquals(applied, File(home, "config.yaml").readText())
        assertFalse(runtime.profileInputsApplied(71))
        runtime.updateLanSharing(true)
        runtime.getProxyGroups().getOrThrow()
        assertFalse(runtime.profileInputsApplied(71))
        assertFalse(File(home, "config.yaml").readText().contains("DOMAIN,example.test,DIRECT"))
        assertTrue(runCatching { runtime.updateLanSharing(null) }.isFailure)
        runtime.applyCurrentProfile(71)
        assertTrue(runtime.profileInputsApplied(71))
        assertTrue(File(home, "config.yaml").readText().contains("DOMAIN,example.test,DIRECT"))
        // 作者 long：普通 DNS 覆盖变化仍走已有自动加载，延期的只是扩展输入，不能冻结其他设置。
        profile = profile.copy(overridesJson = ProfileProviderOverrides(dns = ProfileDnsOverrides(ipv6 = true)).toJsonString())
        runtime.getProxyGroups().getOrThrow()
        assertTrue(runtime.profileInputsApplied(71)); assertTrue(controller.events.isEmpty())
    }

    @Test fun extensionFailureRestoresRunningConfigAndKeepsNewInputsUnapplied() = runTest {
        val source = temporaryFolder.newFile().apply { writeText("rules: ['MATCH,DIRECT']\n") }
        var profile = ActiveProfile(71, "扩展", source.path)
        val bridge = FakeBridge(); val controller = FakeServiceController(); val home = temporaryFolder.newFolder()
        val runtime = manager(profile, home, bridge, controller, profileProvider = object : ActiveProfileProvider {
            override suspend fun activeProfile() = profile
        })
        runtime.startProxy().getOrThrow()
        val previous = File(home, "config.yaml").readText()
        profile = profile.copy(standardExtension = StandardProfileExtension(listOf(ExtensionRule("x", "invalid-rule"))))
        assertTrue(runCatching { runtime.applyCurrentProfile(71) }.isFailure)
        assertEquals(previous, File(home, "config.yaml").readText())
        assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
        assertFalse(runtime.profileInputsApplied(71)); assertTrue(bridge.listenerStarted)
        profile = profile.copy(standardExtension = StandardProfileExtension(listOf(ExtensionRule("x", "DOMAIN,example.test,DIRECT"))))
        runtime.applyCurrentProfile(71)
        assertTrue(runtime.profileInputsApplied(71)); assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
    }

    private fun manager(
        profile: ActiveProfile?,
        homeDir: File,
        bridge: FakeBridge,
        controller: FakeServiceController,
        runtimeResumeStore: FakeRuntimeResumeStore = FakeRuntimeResumeStore(),
        runtimeDiagnosticStore: RuntimeDiagnosticStore = NoopRuntimeDiagnosticStore,
        profileScriptEvaluator: ProfileScriptEvaluator = MissingProfileScriptEvaluator,
        prepareCore: suspend () -> Unit = {},
        outboundModeStore: OutboundModeStore = NoopOutboundModeStore,
        elapsedRealtimeMillis: () -> Long = { 0L },
        lanSharingStore: LanSharingStore = NoopLanSharingStore,
        vpnSettingsStore: VpnSettingsStore = DefaultVpnSettingsStore,
        profileProvider: ActiveProfileProvider? = null,
    ): CoreRuntimeManager {
        return CoreRuntimeManager(
            paths = CoreRuntimePaths(homeDir, prepare = prepareCore),
            profileProvider = profileProvider ?: object : ActiveProfileProvider {
                override suspend fun activeProfile(): ActiveProfile? = profile
            },
            bridge = bridge,
            serviceController = controller,
            vpnOptionsProvider = DefaultVpnOptionsProvider(vpnSettingsStore = vpnSettingsStore),
            runtimeResumeStore = runtimeResumeStore,
            runtimeDiagnosticStore = runtimeDiagnosticStore,
            profileScriptEvaluator = profileScriptEvaluator,
            outboundModeStore = outboundModeStore,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            lanSharingStore = lanSharingStore,
            vpnSettingsStore = vpnSettingsStore,
        )
    }

    private class FakeOutboundModeStore(var mode: CoreOutboundMode? = null) : OutboundModeStore {
        override fun read(): CoreOutboundMode? = mode

        override fun write(mode: CoreOutboundMode) {
            this.mode = mode
        }
    }

    private class FakeRuntimeResumeStore : RuntimeResumeStore {
        var shouldResume = false

        override suspend fun shouldResume(): Boolean = shouldResume

        override suspend fun setShouldResume(value: Boolean) {
            shouldResume = value
        }
    }

    private class FakeRuntimeDiagnosticStore(
        var events: MutableList<RuntimeDiagnosticEvent> = mutableListOf(),
    ) : RuntimeDiagnosticStore {
        override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> = events.takeLast(limit)

        override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) {
            events = (events + event).takeLast(limit).toMutableList()
        }

        override suspend fun clear() {
            events.clear()
        }
    }

    private class FakeBridge(
        private val validationError: Throwable? = null,
        private val proxyChangeError: Throwable? = null,
        private val delayError: Throwable? = null,
        private val delayErrors: Set<String> = emptySet(),
        private val dnsCacheClearResult: Boolean = true,
        private val configuredMode: CoreOutboundMode = CoreOutboundMode.RULE,
        private val trafficError: Throwable? = null,
    ) : CoreRuntimeBridge {
        var lan = com.lonnnnnng.flclash.core.CoreLanConfig(false, 0)
        var failLanUpdate = false
        private var coreHome: File? = null
        private fun lanFromFile(path: String): com.lonnnnnng.flclash.core.CoreLanConfig {
            val json = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(File(path).readText())).asJsonObject
            return com.lonnnnnng.flclash.core.CoreLanConfig(json.get("allow-lan")?.asBoolean ?: false,
                json.get("mixed-port")?.asInt ?: 0)
        }
        override suspend fun getLanConfig(path: String?): com.lonnnnnng.flclash.core.CoreLanConfig {
            calls += "lan-read"
            return path?.let(::lanFromFile) ?: lan
        }
        override suspend fun updateAllowLan(value: Boolean) {
            calls += "lan-update"
            lan = lan.copy(allowLan = value)
            if (failLanUpdate) { failLanUpdate = false; error("更新后失败") }
        }
        override suspend fun getMemoryStats(): com.lonnnnnng.flclash.core.CoreMemoryStats {
            calls += "memory"
            return com.lonnnnnng.flclash.core.CoreMemoryStats(100, 20, 10, 5, 5)
        }
        override suspend fun releaseMemory() { calls += "memory-release" }
        var ipStamp = CoreRouteStamp(1, 1)
        var exitProxy: String? = null
        override suspend fun outboundIpForProxy(urls: List<String>, timeoutMs: Long, proxyName: String): CoreIpResponse {
            exitProxy = proxyName
            assertEquals(6_000L, timeoutMs)
            return ipRead()
        }
        var providerRead: suspend () -> com.lonnnnnng.flclash.core.CoreProviderCounts = { com.lonnnnnng.flclash.core.CoreProviderCounts(0, 0) }
        override suspend fun providerCounts() = providerRead()
        var externalRead: suspend () -> List<com.lonnnnnng.flclash.core.CoreExternalProvider> = { emptyList() }
        var externalUpdate: suspend () -> Unit = {}
        var externalUpdates = 0
        override suspend fun externalProviders() = externalRead()
        override suspend fun updateExternalProvider(name: String, stamp: CoreRouteStamp) { assertEquals(ipStamp, stamp); externalUpdates++; externalUpdate() }

        val routeCalls = mutableListOf<List<List<CoreRouteHit>>>()
        var routeRead: suspend () -> CoreRouteCheck = { CoreRouteCheck(ipStamp, routeCalls.last().map { true }) }
        override suspend fun checkRoutes(routes: List<List<CoreRouteHit>>): CoreRouteCheck {
            routeCalls += routes
            return routeRead()
        }
        var serviceDirect: Boolean? = null
        var serviceRead: suspend (List<com.lonnnnnng.flclash.core.ServiceTarget>) -> List<com.lonnnnnng.flclash.core.CoreServiceCheck> = { targets ->
            targets.map { com.lonnnnnng.flclash.core.CoreServiceCheck(it, com.lonnnnnng.flclash.core.ServiceStatus.AVAILABLE, checkedAt = 1L, stamp = ipStamp) }
        }
        override suspend fun checkServices(targets: List<com.lonnnnnng.flclash.core.ServiceTarget>, direct: Boolean, timeoutMs: Long): List<com.lonnnnnng.flclash.core.CoreServiceCheck> {
            serviceDirect = direct
            check(timeoutMs == 6_000L)
            return serviceRead(targets)
        }
        var ipRead: suspend () -> CoreIpResponse = { error("未设置出口夹具") }
        override suspend fun ipRouteStamp() = ipStamp
        override suspend fun outboundIp(urls: List<String>, timeoutMs: Long) = ipRead()
        override suspend fun ipMetadata(url: String, timeoutMs: Long) = ipRead()
        override val events = emptyFlow<CoreEvent>()
        private val mutableEventFlowStats = MutableStateFlow(CoreEventFlowStats())
        override val eventFlowStats = mutableEventFlowStats.asStateFlow()
        val calls = mutableListOf<String>()
        var listenerStarted = false
        var eventListenerStarted = false
        var logListenerStarted = false
        var proxyGroupsReads = 0
        var setupSelectedMap: Map<String, String> = emptyMap()

        override suspend fun initialize(homeDir: String, sdkVersion: Int) {
            calls += "initialize"
            coreHome = File(homeDir)
        }

        override suspend fun validateConfig(path: String) {
            calls += "validate"
            validationError?.let { throw it }
        }

        override suspend fun setupConfig(selectedMap: Map<String, String>) {
            calls += "setup"
            runCatching { lanFromFile(File(coreHome, "config.yaml").path) }.getOrNull()?.let { lan = it }
            setupSelectedMap = selectedMap
        }

        override suspend fun getConfiguredMode(path: String): CoreOutboundMode {
            calls += "mode-read"
            return configuredMode
        }

        override suspend fun updateMode(mode: CoreOutboundMode) {
            calls += "mode-${mode.wireValue}"
        }

        override suspend fun getProxyGroups(): ProxyGroupsSnapshot {
            proxyGroupsReads++
            return ProxyGroupsSnapshot(emptyList())
        }

        override suspend fun changeProxy(groupName: String, proxyName: String): Boolean {
            proxyChangeError?.let { throw it }
            return true
        }

        override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay? {
            delayError?.let { throw it }
            if (proxyName in delayErrors) throw IllegalStateException("测速失败")
            return ProxyDelay(testUrl, proxyName, 123)
        }

        override suspend fun probe(url: String, proxyName: String, timeoutMs: Long): CoreProbeResult =
            CoreProbeResult(
                statusCode = 200,
                delay = 123,
                url = url,
                chains = listOf("GLOBAL", "FR-1-01"),
                rule = "DOMAIN-SUFFIX",
                rulePayload = "example.com",
                error = "",
                message = "",
                coreEpoch = 1,
                routeVersion = 2,
            )

        override suspend fun getTraffic(): CoreTraffic {
            trafficError?.let { throw it }
            return CoreTraffic(up = 12, down = 34)
        }

        override suspend fun getTotalTraffic(): CoreTraffic = CoreTraffic(up = 120, down = 340)

        override suspend fun getConnectionCount(): Int = 2

        override suspend fun getConnections(): List<CoreConnection> = emptyList()

        override suspend fun clearDnsCache(): Boolean {
            calls += "clear-dns-cache"
            return dnsCacheClearResult
        }

        override fun startEventListener() {
            eventListenerStarted = true
        }

        override fun stopEventListener() {
            eventListenerStarted = false
        }

        override suspend fun startLog() {
            calls += "log-start"
            logListenerStarted = true
        }

        override suspend fun stopLog() {
            calls += "log-stop"
            logListenerStarted = false
        }

        override fun startListener() {
            calls += "listener-start"
            listenerStarted = true
        }

        override fun stopListener() {
            calls += "listener-stop"
            listenerStarted = false
        }
    }

    private class FakeServiceController(
        private val startError: Throwable? = null,
    ) : ProxyServiceController {
        val events = mutableListOf<String>()
        var started = false
        var failNextStart = false
        var receivedOptions: VpnOptions? = null
        var onStop: () -> Unit = {}

        override suspend fun start(options: VpnOptions) {
            events += "service-start"
            receivedOptions = options
            startError?.let { throw it }
            if (failNextStart) { failNextStart = false; error("模拟服务启动失败") }
            started = true
            ServiceConfig.updateVpnState(VpnRuntimeState.CONNECTED)
        }

        override fun stop() {
            onStop()
            events += "service-stop"
            started = false
        }
    }
}
