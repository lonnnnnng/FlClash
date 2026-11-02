package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest

/** 作者 long：显式真机验收复用授权订阅，第二配置只来自工作区 1.yaml；恢复原选择且不输出配置正文或节点名。 */
class ProfileRuntimeParityLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun stoppedNodesHotProfileSwitchAndRealExitFollowRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashProfileRuntimeAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val authorized = checkNotNull(args.getString("kclashAcceptanceUrl1"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val manager = entry.runtimeManager()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val originalId = checkNotNull(repository.currentProfileId.value)
        val profile = repository.profiles.value.first { it.id == originalId }
        assertTrue("必须复用已导入的授权来源", authorized == profile.url)
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val originals = repository.profiles.value.map { it.id }
        val metadata = originals.associateWith { id -> repository.profileFile(id).let { it.length() to it.lastModified() } }
        val originalMap = decodeSelectedMap(profile.selectedMapJson)
        val originalMode = manager.outboundMode.value
        val input = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-profile-parity-input.json").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }.let { org.json.JSONObject(it) }
        val localBytes = android.util.Base64.decode(input.getString("configBase64"), android.util.Base64.DEFAULT)
        val sha = MessageDigest.getInstance("SHA-256").digest(localBytes).joinToString("") { "%02x".format(it) }
        assertEquals(input.getString("configSha256"), sha)
        var addedId: Long? = null
        lateinit var runtime: MainViewModel
        lateinit var groups: ProxyGroupsViewModel
        lateinit var ip: OutboundIpViewModel
        compose.activityRule.scenario.onActivity {
            runtime = ViewModelProvider(it)[MainViewModel::class.java]
            groups = ViewModelProvider(it)[ProxyGroupsViewModel::class.java]
            ip = ViewModelProvider(it)[OutboundIpViewModel::class.java]
        }
        var stage = "stopped-nodes"
        fun exerciseComputedGroup(type: String): Boolean {
            stage = "computed-${type.lowercase()}"
            compose.waitUntil(30_000) {
                groups.state.value.groups.any { it.type.equals(type, ignoreCase = true) && it.all.isNotEmpty() }
            }
            var group = groups.state.value.groups.first { it.type.equals(type, ignoreCase = true) && it.all.isNotEmpty() }
            val profileId = checkNotNull(repository.currentProfileId.value)
            val saved = groups.state.value.selectedMap[group.name]
            if (saved != null && saved in group.all) {
                compose.runOnIdle { groups.selectProxy(group, saved) }
                compose.waitUntil(30_000) {
                    groups.state.value.selectedMap[group.name] == null && groups.state.value.switchingGroup == null &&
                        groups.state.value.groups.firstOrNull { it.name == group.name }?.fixed.isNullOrBlank()
                }
                group = groups.state.value.groups.first { it.name == group.name }
            } else if (saved != null) {
                // 作者 long：配置更新后若持久映射已不属于该组，先清除失效映射，恢复核心自动结果再验证当前交互。
                runBlocking(Dispatchers.IO) {
                    repository.updateProfileSelectedMap(profileId, groups.state.value.selectedMap - group.name)
                    manager.changeProxy(group.name, "").getOrThrow()
                }
                groups.refresh()
                compose.waitUntil(30_000) {
                    groups.state.value.selectedMap[group.name] == null && groups.state.value.groups.firstOrNull { it.name == group.name }?.fixed.isNullOrBlank()
                }
                group = groups.state.value.groups.first { it.name == group.name }
            }
            assertTrue("核心自动结果必须属于当前组", group.now != null && group.now in group.all)
            val groupNames = groups.state.value.groups.map { it.name }.toSet()
            val testUrl = group.testUrl?.takeIf { it.isNotBlank() }
                ?: com.lonnnnnng.flclash.core.CoreBridge.DEFAULT_TEST_URL
            val healthyTarget = runBlocking(Dispatchers.IO) {
                group.all.asSequence()
                    .filter { it !in groupNames }
                    .take(4)
                    .firstOrNull { candidate ->
                        (manager.testDelay(candidate, testUrl, 4_000L).getOrNull()?.value ?: -1) > 0
                    }
            }
            val target = healthyTarget ?: group.all.firstOrNull { it !in groupNames }
            assertTrue("真实组必须包含可选择的叶子节点", target != null)
            compose.runOnIdle { groups.selectProxy(group, checkNotNull(target)) }
            compose.waitUntil(45_000) {
                val pinned = groups.state.value.groups.firstOrNull { it.name == group.name }
                groups.state.value.selectedMap[group.name] == target && groups.state.value.switchingGroup == null &&
                    pinned != null && pinned.fixed == target && (healthyTarget == null || pinned.now == target)
            }
            val pinned = groups.state.value.groups.first { it.name == group.name }
            assertTrue("页面当前节点必须跟随核心 now", pinned.now == selectedProxyName(pinned, groups.state.value.selectedMap))
            val invalid = runBlocking(Dispatchers.IO) {
                manager.changeProxy(group.name, "__KCLASH_INVALID_PROXY__").isFailure
            }
            assertTrue("核心必须拒绝不属于组的节点", invalid)
            val afterInvalid = runBlocking(Dispatchers.IO) {
                manager.getProxyGroups().getOrThrow().groups.first { it.name == group.name }
            }
            assertTrue("无效节点不能覆盖有效手动固定", afterInvalid.fixed == target)
            assertTrue("无效节点不能把当前节点改成组外节点", afterInvalid.now != null && afterInvalid.now in afterInvalid.all)

            compose.runOnIdle {
                groups.selectProxy(checkNotNull(groups.state.value.groups.firstOrNull { it.name == group.name }), checkNotNull(target))
            }
            compose.waitUntil(45_000) {
                val automatic = groups.state.value.groups.firstOrNull { it.name == group.name }
                groups.state.value.selectedMap[group.name] == null && groups.state.value.switchingGroup == null &&
                    automatic?.fixed.isNullOrBlank() && automatic != null && automatic.now in automatic.all
            }
            val automatic = groups.state.value.groups.first { it.name == group.name }
            assertTrue("页面选择解析必须跟随核心自动 now", automatic.now == selectedProxyName(automatic, groups.state.value.selectedMap))
            return healthyTarget != null
        }
        try {
            if (args.getString("kclashComputedGroupsOnly") == "true") {
                val fallbackHadHealthyNode = exerciseComputedGroup("Fallback")
                stage = "computed-url-test-profile"
                val created = runBlocking(Dispatchers.IO) { repository.addFileProfile("验收 1.yaml", localBytes) }
                addedId = created.id
                runBlocking(Dispatchers.IO) { manager.selectProfile(created.id, repository::selectProfile) }
                compose.waitUntil(30_000) {
                    repository.currentProfileId.value == created.id && !groups.state.value.isLoading &&
                        groups.state.value.groups.any { it.type.equals("URLTest", ignoreCase = true) }
                }
                val urlTestHadHealthyNode = exerciseComputedGroup("URLTest")
                instrumentation.sendStatus(2, Bundle().apply {
                    putString(
                        "kclash_computed_groups",
                        "real-core-fallback-and-urltest-pin-clear-invalid-passed-healthy=$fallbackHadHealthyNode/$urlTestHadHealthyNode",
                    )
                })
                return
            }
            compose.waitUntil(30_000) { groups.state.value.groups.any { it.isSelectable && it.all.size > 1 } }
            compose.waitUntil(40_000) { ip.state.value.info != null }
            stage = "stopped-ip"
            val directIp = checkNotNull(ip.state.value.info).ip
            compose.onNodeWithTag("nav_proxy").performClick()
            compose.onNodeWithTag("page_proxy").assertIsDisplayed()
            compose.onNodeWithTag("proxy_group_tabs").assertIsDisplayed()
            val group = groups.state.value.groups.first { it.isSelectable && it.all.size > 1 }
            val target = group.all.first { it != selectedProxyName(group, groups.state.value.selectedMap) }
            compose.runOnIdle { groups.selectProxy(group, target) }
            compose.waitUntil(15_000) { groups.state.value.selectedMap[group.name] == target }
            stage = "stopped-delay"
            compose.runOnIdle { groups.testDelay(group, target) }
            compose.waitUntil(30_000) { groups.state.value.delayState(group, target)?.let { !it.isTesting } == true }
            val directDelay = runBlocking(Dispatchers.IO) { manager.testDelay("DIRECT", com.lonnnnnng.flclash.core.CoreBridge.DEFAULT_TEST_URL).getOrThrow() }
            assertTrue("停止态直连测速必须成功", (directDelay?.value ?: -1) > 0)
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            compose.onNodeWithTag("nav_dashboard").performClick()
            stage = "start"
            compose.onNodeWithContentDescription("启动核心").performClick()
            compose.waitUntil(30_000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.android.vpndialogs") root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                manager.state.value in listOf(CoreRuntimeStatus.RUNNING, CoreRuntimeStatus.FAILED)
            }
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            exerciseComputedGroup("Fallback")
            stage = "local-import"
            val created = runBlocking(Dispatchers.IO) { repository.addFileProfile("验收 1.yaml", localBytes) }
            addedId = created.id
            // 作者 long：运行态配置切换直接复用主页“查看配置”正式入口，避免详情 Sheet 关闭与 Profiles 宿主切换同时发生时丢失列表语义树。
            compose.onNodeWithTag("dashboard_profile_action").performScrollTo().performClick()
            compose.onNodeWithTag("page_profiles").assertIsDisplayed()
            stage = "hot-switch-local"
            // 作者 long：Profile 列表由 Room 流异步刷新，先等待新卡片进入语义树再执行热切换，避免把列表刷新竞态误判成切换失败。
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("profile_card_${created.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("profile_card_" + created.id).performScrollTo().performClick()
            compose.waitUntil(30_000) { repository.currentProfileId.value == created.id && !groups.state.value.isLoading && groups.state.value.groups.isNotEmpty() }
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            exerciseComputedGroup("URLTest")
            stage = "hot-switch-original"
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("profile_card_${originalId}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("profile_card_" + originalId).performScrollTo().performClick()
            compose.waitUntil(30_000) { repository.currentProfileId.value == originalId && groups.state.value.groups.isNotEmpty() && !groups.state.value.isLoading }
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            stage = "running-ip"
            runBlocking(Dispatchers.IO) {
                manager.changeProxy("GLOBAL", "DIRECT").getOrThrow()
                manager.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow()
            }
            compose.onNodeWithTag("nav_dashboard").performClick()
            compose.waitUntil(45_000) { ip.state.value.info != null }
            stage = "running-node"
            val firstRevision = manager.ipRouteRevision.value
            val global = groups.state.value.groups.first { it.name == "GLOBAL" }
            val groupNames = groups.state.value.groups.map { it.name }.toSet()
            val candidates = global.all.filter { it !in groupNames && it !in listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS") }
            val usable = runBlocking(Dispatchers.IO) {
                candidates.take(12).firstOrNull { candidate ->
                    (manager.testDelay(candidate, com.lonnnnnng.flclash.core.CoreBridge.DEFAULT_TEST_URL, 4_000L).getOrNull()?.value ?: -1) > 0
                }
            }
            assertNotNull("至少一个真实代理节点测速成功", usable)
            compose.runOnIdle { groups.selectProxy(global, checkNotNull(usable)) }
            compose.waitUntil(45_000) { manager.ipRouteRevision.value > firstRevision && groups.state.value.selectedMap["GLOBAL"] == usable &&
                groups.state.value.switchingGroup == null && ip.state.value.info != null && !ip.state.value.isLoading }
            // 作者 long：自动组后台健康检查也可能改变版本，独立复查只接受稳定版本的成功结果。
            val actual = runBlocking(Dispatchers.IO) {
                var answer: com.lonnnnnng.flclash.core.CoreIpResponse? = null
                repeat(4) {
                    if (answer == null) {
                        answer = runCatching { manager.queryOutboundIp(com.lonnnnnng.flclash.data.outboundIpSources) }.getOrNull()
                        if (answer == null) kotlinx.coroutines.delay(1_000L)
                    }
                }
                checkNotNull(answer) { "稳定路由下仍未取得出口" }
            }
            val actualInfo = com.lonnnnnng.flclash.data.parseOutboundIp(actual)
            compose.waitUntil(30_000) { ip.state.value.info?.ip == actualInfo.ip && !ip.state.value.isLoading }
            assertTrue("显示 IP 必须等于当前代理的实际查询", actualInfo.ip == ip.state.value.info?.ip)
            val routedProbe = runBlocking(Dispatchers.IO) { manager.probe(actual.sourceUrl).getOrThrow() }
            assertTrue("实际出口链必须包含所选节点", routedProbe?.chains?.contains(usable) == true)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_profile_runtime", "direct-delay-positive-proxy-delay-positive-real-proxy-chain-ip-matched-changed=" + (directIp != actualInfo.ip)) })
            stage = "stop-direct-ip"
            compose.runOnIdle { runtime.stopRuntime() }
            compose.waitUntil(30_000) { manager.state.value == CoreRuntimeStatus.STOPPED && groups.state.value.groups.isNotEmpty() }
            compose.waitUntil(45_000) { ip.state.value.info != null && !ip.state.value.isLoading }
            // 作者 long：竞速来源可分别返回 IPv4/IPv6，停止后的地址必须属于当前网络的独立直连结果。
            val directAnswers = runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.coroutineScope {
                    com.lonnnnnng.flclash.data.outboundIpSources.map { source ->
                        async {
                            runCatching { com.lonnnnnng.flclash.data.parseOutboundIp(manager.queryOutboundIp(listOf(source))).ip }.getOrNull()
                        }
                    }.mapNotNull { it.await() }.toSet()
                }
            }
            assertTrue("停止后必须显示当前网络的真实出口", ip.state.value.info?.ip in directAnswers)
            stage = "stopped-profile-switch"
            runBlocking(Dispatchers.IO) { manager.selectProfile(created.id, repository::selectProfile) }
            compose.waitUntil(30_000) { repository.currentProfileId.value == created.id && groups.state.value.groups.isNotEmpty() && !groups.state.value.isLoading }
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            runBlocking(Dispatchers.IO) { manager.selectProfile(originalId, repository::selectProfile) }
            compose.waitUntil(30_000) { repository.currentProfileId.value == originalId && groups.state.value.groups.isNotEmpty() && !groups.state.value.isLoading }
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_profile_runtime", "stopped-nodes-selection-delay-hot-profile-switch-ip-refresh-stop-passed") })
        } catch (failure: Throwable) {
            val probe = runCatching { runBlocking(Dispatchers.IO) { manager.queryOutboundIp(com.lonnnnnng.flclash.data.outboundIpSources) } }.getOrNull()
            val currentProfiles = repository.profiles.value
            val groupState = runCatching { groups.state.value }.getOrNull()
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_profile_runtime", "failed-stage=" + stage + " vm-error=" + ip.state.value.error +
                    " response=" + probe?.statusCode + "/" + probe?.error +
                    " profile-count=" + currentProfiles.size +
                    " profile-ids=" + currentProfiles.joinToString(",") { it.id.toString() } +
                    " current-profile=" + repository.currentProfileId.value +
                    " groups=" + groupState?.groups?.size +
                    " groups-loading=" + groupState?.isLoading +
                    " groups-error=" + groups.errors.replayCache.joinToString("|") +
                    " core=" + manager.state.value +
                    " stamp=" + probe?.stamp + " current=" + runBlocking(Dispatchers.IO) { manager.ipRouteStamp() })
            })
            throw failure
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                manager.selectProfile(originalId, repository::selectProfile)
                originalMap.forEach { (groupName, nodeName) -> manager.changeProxy(groupName, nodeName) }
                repository.updateProfileSelectedMap(originalId, originalMap)
                manager.setOutboundMode(originalMode).getOrThrow()
                // 作者 long：只删除本方法刚从 1.yaml 新建的已知 ID，已有配置和正文保留原状。
                addedId?.let { id -> repository.deleteProfile(id) }
            }
            assertEquals(originals.toSet(), runBlocking(Dispatchers.IO) { entry.database().profiles().getAll().map { it.id }.toSet() })
            originals.forEach { id -> assertEquals(metadata[id], repository.profileFile(id).let { it.length() to it.lastModified() }) }
        }
    }
}
