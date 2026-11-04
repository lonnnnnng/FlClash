package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式页面与真实管理器一起验收，只操作原1.yaml副本；原配置和脚本逐项核对并恢复。 */
class HomeProxyMainFlowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recoverExplicitInterruptedFixtureWithoutChangingFormalProfiles() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("kclashRecoverHomeProxyFixture") == "true")
        val ids = requireNotNull(args.getString("kclashRecoveryProfileIds")).split(',').map(String::toLong).toSet()
        require(ids.size == 2)
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val manager = e.runtimeManager()
        val all = e.database().profiles().getAll()
        val temporary = all.filter { it.id in ids }
        check(temporary.size == ids.size && temporary.all { it.type == ProfileType.FILE && it.label == "mainflow-${it.id}" })
        val bytes = readAuthorizedProxyProfile()
        temporary.forEach { assertArrayEquals(bytes, repo.profileFile(it.id).readBytes()) }
        val formal = all.filterNot { it.id in ids }; check(formal.size == 2)
        val files = formal.associate { it.id to signature(repo.profileFile(it.id)) }
        val metadata = formal.sortedBy { it.id }.joinToString("\n") {
            repo.profileFile(it.id).let { file -> "${file.name}\t${file.length()}\t${file.lastModified()}" }
        }
        assertEquals(compose.activity.cacheDir.resolve("dashboard-ownership-profile-metadata.txt").readText(), metadata)
        val selected = formal.single { it.toDomain().realLabel == requireNotNull(args.getString("kclashRecoveryProfileLabel")) }.id
        compose.onNodeWithTag("nav_dashboard").performClick(); compose.waitForIdle()
        manager.stop().getOrThrow(); manager.selectProfile(selected, repo::selectProfile)
        // 作者 long：恢复仅接受命令明确列出的两份本轮副本，并核对原字节；正式配置行和正文不参与删除或重写。
        temporary.forEach { repo.deleteProfile(it.id) }
        assertEquals(formal, e.database().profiles().getAll())
        assertEquals(files, formal.associate { it.id to signature(repo.profileFile(it.id)) })
        assertEquals(selected, repo.currentProfileId.value)
    }

    @Test fun formalProvidersFinishLoadingAcrossPollingAndRecreation() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val rows = e.database().profiles().getAll()
        val id = requireNotNull(repo.currentProfileId.value)
        check(rows.size == 2 && rows.none { it.label.startsWith("mainflow-") })
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        compose.onNodeWithTag("nav_dashboard").performClick()
        compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
        compose.onNodeWithTag("dashboard_profile_card").performClick()
        fun model(): ProfileStatisticsViewModel {
            lateinit var value: ProfileStatisticsViewModel
            compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
            return value
        }
        var model = model()
        fun ready() = model.providers.value.let { it.profileId == id && !it.isLoading && it.providers != null && it.error == null }
        compose.waitUntil(15_000) { ready() }
        compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_providers"))
        compose.onNodeWithTag("profile_providers_loading").assertDoesNotExist()
        capture("formal_providers_loaded")
        val deadline = android.os.SystemClock.elapsedRealtime() + 16_000
        // 作者 long：跨越三次真实轮询持续检查，不能只在第一次成功后便认定不会重新挂起。
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            assertTrue(ready()); kotlinx.coroutines.delay(250)
        }
        compose.activityRule.scenario.recreate(); model = model()
        compose.waitUntil(15_000) { ready() }
        compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_providers"))
        compose.onNodeWithTag("profile_providers_loading").assertDoesNotExist()
        capture("formal_providers_recreated")
        compose.onNodeWithTag("dashboard_profile_detail_back").performClick()
        assertEquals(rows, e.database().profiles().getAll())
        assertEquals(files, rows.associate { it.id to signature(repo.profileFile(it.id)) })
        assertEquals(id, repo.currentProfileId.value)
    }

    @Test fun runningOriginalYamlProvidersRemainLoadedAcrossPollingAndRecreation() = runBlocking {
        fixture { e, ids ->
            val manager = e.runtimeManager()
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            compose.onNodeWithTag("nav_dashboard").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
            compose.onNodeWithTag("dashboard_profile_card").performClick()
            fun model(): ProfileStatisticsViewModel {
                lateinit var value: ProfileStatisticsViewModel
                compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
                return value
            }
            var model = model()
            fun ready() = model.providers.value.let {
                it.profileId == ids[0] && !it.isLoading && !it.providers.isNullOrEmpty() && it.error == null
            }
            compose.waitUntil(15_000) { ready() }
            val count = model.providers.value.providers!!.size
            compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_row_0"))
            compose.onNodeWithTag("profile_providers_loading").assertDoesNotExist()
            capture("running_yaml_providers_loaded")
            val observationMs = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("kclashProvidersObservationMs")?.toLong()?.also { require(it in 16_000L..300_000L) } ?: 16_000L
            // 作者 long：分钟级卡住必须用分钟级非空列表观察验收；保持每250ms检查一次，不能用空列表或几轮轮询代替。
            val deadline = android.os.SystemClock.elapsedRealtime() + observationMs
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                assertTrue(ready()); assertEquals(count, model.providers.value.providers!!.size)
                kotlinx.coroutines.delay(250)
            }
            compose.activityRule.scenario.recreate(); model = model()
            compose.waitUntil(15_000) { ready() }
            assertEquals(count, model.providers.value.providers!!.size)
            compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_row_0"))
            capture("running_yaml_providers_recreated")
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("kclash_providers_polling", "running-count=$count polling-window-ms=$observationMs recreation=passed session-preserved=true")
            })
            compose.onNodeWithTag("dashboard_profile_detail_back").performClick()
        }
    }

    @Test fun providersRealPageOpenUpdateAndResumeFinishWithoutStaleBusy() = runBlocking {
        fixture { e, ids ->
            val manager = e.runtimeManager()
            fun model(): ProfileStatisticsViewModel {
                lateinit var value: ProfileStatisticsViewModel
                compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
                return value
            }
            var model = model()
            fun ready() = model.providers.value.let {
                it.profileId == ids[0] && !it.isLoading && !it.providers.isNullOrEmpty() && it.error == null
            }
            fun idle() = model.providers.value.let { it.updatingName == null && it.updatingNames.isEmpty() }
            fun report(phase: String, elapsed: Long, failed: Boolean = false) {
                // 作者 long：只记录阶段、数量和耗时，不把真实资源名称或地址写进验收日志。
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                    putString("kclash_providers_reproduction", "phase=$phase elapsed-ms=$elapsed count=${model.providers.value.providers?.size} update-failed=$failed")
                })
            }
            fun open(phase: String) {
                compose.onNodeWithTag("nav_dashboard").performClick()
                compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
                val started = android.os.SystemClock.elapsedRealtime()
                compose.onNodeWithTag("dashboard_profile_card").performClick()
                compose.waitUntil(15_000) { ready() }
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_row_0"))
                compose.onNodeWithTag("profile_providers_loading").assertDoesNotExist()
                assertTrue(idle())
                report(phase, android.os.SystemClock.elapsedRealtime() - started)
            }
            fun close() {
                compose.onNodeWithTag("dashboard_profile_detail_back").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("dashboard_profile_detail_sheet").fetchSemanticsNodes().isEmpty() }
            }
            open("stopped-first-open")
            capture("providers_real_stopped_loaded")
            close(); open("stopped-reopen"); close()
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            open("running-first-open")
            val count = model.providers.value.providers!!.size
            // 作者 long：只点击原1.yaml已有的两个HTTP资源，避免全量90项下载掩盖单项更新忙态的触发条件。
            val targets = model.providers.value.providers!!.filter { it.canUpdate }.take(2)
            assertEquals(2, targets.size)
            targets.forEachIndexed { ordinal, target ->
                val index = model.providers.value.providers!!.indexOfFirst { it.name == target.name }
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_update_$index"))
                val started = android.os.SystemClock.elapsedRealtime()
                compose.onNodeWithTag("profile_provider_update_$index").assertIsDisplayed().assertIsEnabled().performClick()
                compose.waitUntil(5_000) { model.providers.value.updatingName != null || model.providers.value.updateMessage != null }
                if (ordinal == 1) {
                    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    kotlinx.coroutines.delay(1_000)
                    compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                }
                // 作者 long：保留真实下载的结束条件；超过两分钟直接失败并保留现场，不能靠关闭页面消除转圈后宣称通过。
                compose.waitUntil(120_000) { idle() }
                compose.waitUntil(15_000) { ready() }
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                compose.onNodeWithTag("profile_provider_updating_$index").assertDoesNotExist()
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_update_$index"))
                compose.onNodeWithTag("profile_provider_update_$index").assertIsEnabled()
                val updated = manager.profileProviders(ids[0]).single { it.name == target.name }
                assertNotNull(updated.updatedAtMillis)
                assertTrue("真实更新必须推进核心资源时间", updated.updatedAtMillis!! > (target.updatedAtMillis ?: 0))
                assertTrue(updated.count > 0)
                if (ordinal == 0) {
                    assertNotNull(model.providers.value.updateMessage)
                    assertFalse(model.providers.value.updateFailed)
                }
                report("ui-update-${ordinal + 1}", elapsed, model.providers.value.updateFailed)
                capture("providers_real_update_${ordinal + 1}_finished")
                close(); open("running-reopen-${ordinal + 1}")
            }
            val observationMs = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("kclashProvidersObservationMs")?.toLong()?.also { require(it in 16_000L..300_000L) } ?: 16_000L
            val started = android.os.SystemClock.elapsedRealtime()
            while (android.os.SystemClock.elapsedRealtime() - started < observationMs) {
                assertTrue("Providers读取不能重新挂起", ready())
                assertTrue("已结束的资源更新不能恢复忙态", idle())
                assertEquals(count, model.providers.value.providers!!.size)
                kotlinx.coroutines.delay(250)
            }
            report("continuous-polling", android.os.SystemClock.elapsedRealtime() - started)
            compose.activityRule.scenario.recreate(); model = model()
            compose.waitUntil(15_000) { ready() && idle() }
            compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_provider_row_0"))
            compose.onNodeWithTag("profile_providers_loading").assertDoesNotExist()
            assertEquals(count, model.providers.value.providers!!.size)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertTrue(manager.profileInputsApplied(ids[0]))
            capture("providers_real_recreated")
            close()
        }
    }

    @Test fun runningHomeAndProxySelectionsPersistAcrossRevisionAndRecreation() = runBlocking {
        fixture { e, ids ->
            val repo = e.profileRepository(); val manager = e.runtimeManager()
            manager.setOutboundMode(CoreOutboundMode.RULE).getOrThrow()
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            var model = model()
            compose.waitUntil(30_000) { model.state.value.profileId == ids[0] && model.state.value.groups.isNotEmpty() && !model.state.value.isLoading }
            val group = model.state.value.groups.first { it.isSelectable && !it.hidden && it.all.size >= 2 && it.name != "GLOBAL" }
            val first = group.all.first { it != selectedProxyName(group, model.state.value.selectedMap) }
            repo.updateProfileCurrentGroupName(ids[0], group.name)
            compose.onNodeWithTag("nav_proxy").performClick()
            compose.onNodeWithTag("proxy_group_tab_${group.name}").performScrollTo().performClick()
            clickNode(group, first)
            compose.waitUntil(30_000) { model.state.value.switchingGroup == null && model.state.value.selectedMap[group.name] == first }
            assertEquals(first, decodeSelectedMap(e.database().profiles().getById(ids[0])!!.selectedMapJson)[group.name])
            assertEquals(first, manager.getProxyGroups(ids[0]).getOrThrow().groups.first { it.name == group.name }.now)
            capture("proxy_selection_running")

            compose.onNodeWithTag("nav_dashboard").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_proxy_groups_card"))
            compose.onNodeWithTag("dashboard_proxy_groups_card").performClick()
            val nextGroup = model.state.value.groups.first { it.name == group.name }
            val second = nextGroup.all.first { it != first }
            clickNode(nextGroup, second)
            compose.waitUntil(30_000) { model.state.value.switchingGroup == null && model.state.value.selectedMap[group.name] == second }
            assertEquals(second, decodeSelectedMap(e.database().profiles().getById(ids[0])!!.selectedMapJson)[group.name])
            compose.onNodeWithTag("dashboard_proxy_picker_close").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("dashboard_proxy_picker").fetchSemanticsNodes().isEmpty() }
            capture("home_proxy_selection_running")

            compose.activityRule.scenario.recreate()
            model = model()
            compose.waitUntil(30_000) { model.state.value.selectedMap[group.name] == second && !model.state.value.isLoading }
            assertEquals(second, manager.getProxyGroups(ids[0]).getOrThrow().groups.first { it.name == group.name }.now)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertTrue(manager.profileInputsApplied(ids[0]))
        }
    }

    @Test fun runningModeDelayAndOldRenderedActionsKeepNewProfileAndSession() = runBlocking {
        fixture { e, ids ->
            val manager = e.runtimeManager(); val repo = e.profileRepository()
            manager.setOutboundMode(CoreOutboundMode.RULE).getOrThrow(); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            val model = model()
            compose.waitUntil(30_000) { model.state.value.profileId == ids[0] && model.state.value.groups.isNotEmpty() && !model.state.value.isLoading }
            val group = model.state.value.groups.first { it.isSelectable && !it.hidden && it.all.isNotEmpty() && it.name != "GLOBAL" }
            val node = group.all.first()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_outbound_mode_card"))
            compose.onNodeWithTag("dashboard_mode_global").performClick()
            compose.waitUntil(10_000) { manager.outboundMode.value == CoreOutboundMode.GLOBAL }
            compose.onNodeWithTag("dashboard_mode_rule").performClick()
            compose.waitUntil(10_000) { manager.outboundMode.value == CoreOutboundMode.RULE }
            repo.updateProfileCurrentGroupName(ids[0], group.name)
            compose.onNodeWithTag("nav_proxy").performClick()
            compose.onNodeWithTag("proxy_group_tab_${group.name}").performScrollTo().performClick()
            val selection = decodeSelectedMap(e.database().profiles().getById(ids[0])!!.selectedMapJson)
            compose.onNodeWithTag("proxy_group_members_${group.name}").performScrollToNode(hasTestTag("proxy_node_delay_${group.name}_$node"))
            compose.onNodeWithTag("proxy_node_delay_${group.name}_$node").performClick()
            compose.waitUntil(45_000) { model.state.value.delayState(group, node)?.isTesting == false }
            assertEquals(selection, decodeSelectedMap(e.database().profiles().getById(ids[0])!!.selectedMapJson))
            capture("proxy_delay_running")
            // 作者 long：先离开自动记忆分组的代理页，避免把合法的新页面记忆误判成旧操作越界写入。
            compose.onNodeWithTag("nav_dashboard").performClick()
            compose.waitForIdle()
            manager.selectProfile(ids[1], repo::selectProfile)
            compose.waitUntil(30_000) { model.state.value.profileId == ids[1] && !model.state.value.isLoading && model.state.value.groups.isNotEmpty() }
            val before = e.database().profiles().getById(ids[1])!!
            compose.runOnIdle {
                model.selectProxy(ids[0], group, node); model.toggleGroupExpanded(ids[0], group.name)
                model.testDelay(ids[0], group, node); model.testDelayGroup(ids[0], group)
            }
            compose.waitForIdle()
            assertEquals(before, e.database().profiles().getById(ids[1]))
            assertEquals(ids[1], repo.currentProfileId.value)
            assertTrue(model.state.value.delayResults.isEmpty())
            assertTrue(manager.changeProxy(ids[0], group.name, node).isFailure)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        }
    }

    private fun model(): ProxyGroupsViewModel {
        lateinit var value: ProxyGroupsViewModel
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProxyGroupsViewModel::class.java] }
        return value
    }

    private fun clickNode(group: ProxyGroupInfo, node: String) {
        val tag = "proxy_node_${group.name}_$node"
        compose.onNodeWithTag("proxy_group_members_${group.name}").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private suspend fun fixture(block: suspend (ConnectionAcceptanceEntryPoint, List<Long>) -> Unit) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashHomeProxyAcceptance"))
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = e.runtimeManager(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll(); val selected = requireNotNull(repo.currentProfileId.value)
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val scripts = e.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) }
        val vpn = e.vpnSettingsStore().read(); val mode = manager.outboundMode.value
        val options = ProxyPageOptionsStore.load(compose.activity)
        val bytes = readAuthorizedProxyProfile(); val ids = List(2) { System.nanoTime().and(Long.MAX_VALUE) }
        var failure: Throwable? = null
        try {
            ids.forEachIndexed { index, id ->
                e.database().profiles().insert(Profile.custom(id, "mainflow-$id").copy(type = ProfileType.FILE, orderIndex = rows.size + index).toEntity())
                repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            }
            ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
            manager.setVpnEnabled(false).getOrThrow(); manager.selectProfile(ids[0], repo::selectProfile)
            block(e, ids)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
            // 作者 long：退出代理页后再恢复原选择，避免页面自动记忆分组意外写入正式配置。
            // 作者 long：宿主已退出时也必须继续执行数据恢复，UI清理失败不能跳过核心停止和临时配置回收。
            runCatching {
                if (compose.onAllNodesWithTag("nav_dashboard").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("nav_dashboard").performClick()
                    compose.waitForIdle()
                }
            }
            manager.stop().getOrThrow(); manager.setOutboundMode(mode).getOrThrow()
            manager.selectProfile(selected, repo::selectProfile)
            ids.forEach { id ->
                val row = e.database().profiles().getById(id)
                check(row == null || row.label == "mainflow-$id")
                if (row != null) repo.deleteProfile(id)
            }
            manager.setVpnEnabled(vpn).getOrThrow(); ProxyPageOptionsStore.save(compose.activity, options)
            assertEquals(rows, e.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
            assertEquals(files, rows.associate { it.id to signature(repo.profileFile(it.id)) })
            assertEquals(scripts, e.database().scripts().getAll())
            assertEquals(scriptFiles, scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) })
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun signature(file: File) = file.lastModified() to java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun capture(name: String) {
        compose.waitForIdle()
        // 作者 long：真实窗口的进出场由平台时钟驱动，截图在过渡结束后采集，避免把半透明过渡当成最终页面。
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
