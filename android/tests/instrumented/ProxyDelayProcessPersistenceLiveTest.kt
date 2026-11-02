package com.lonnnnnng.flclash.ui

import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.ProxyDelayEntity
import com.lonnnnnng.flclash.data.RoomProxyDelayStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 作者 long：正式测速缓存必须经真实 Profile、Room 和目标应用进程重启验证，Activity 重建不能替代进程重建。 */
class ProxyDelayProcessPersistenceLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun seedSuccessfulDelayBeforeProcessRestart() {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("seed", args.getString("kclashDelayPhase"))
        assertPortraitAndDefaultFont()
        val runId = requireRunId(args.getString("kclashDelayRunId"))
        val marker = markerFile(runId)
        assertFalse("本次验收标记不能覆盖未完成的旧验收", marker.exists())

        val entry = EntryPointAccessors.fromApplication(
            compose.activity.application,
            ConnectionAcceptanceEntryPoint::class.java,
        )
        val repository = entry.profileRepository()
        val manager = entry.runtimeManager()
        val database = entry.database()
        val delayStore = RoomProxyDelayStore(database.proxyDelayResults())
        val authorizationUrl = checkNotNull(args.getString("kclashAcceptanceUrl1"))
        runBlocking(Dispatchers.IO) { repository.refreshCurrentProfile() }
        val databaseProfiles = runBlocking(Dispatchers.IO) { database.profiles().getAll() }
        compose.waitUntil(15_000) {
            repository.profiles.value.map { it.id }.toSet() == databaseProfiles.map { it.id }.toSet()
        }
        val originalProfileId = repository.currentProfileId.value
        val authorizedProfile = repository.profiles.value.firstOrNull { it.url == authorizationUrl }
            ?: run {
                assertTrue("正式库有其他配置时不为测试另建 Profile", repository.profiles.value.isEmpty())
                runBlocking(Dispatchers.IO) {
                    repository.addRemoteProfile(authorizationUrl, "代理组缓存真机验收")
                }
            }
        if (repository.currentProfileId.value != authorizedProfile.id) {
            runBlocking(Dispatchers.IO) {
                manager.selectProfile(authorizedProfile.id, repository::selectProfile)
            }
        }
        compose.waitUntil(15_000) { repository.currentProfileId.value == authorizedProfile.id }
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)

        lateinit var groups: ProxyGroupsViewModel
        compose.activityRule.scenario.onActivity { activity ->
            groups = ViewModelProvider(activity)[ProxyGroupsViewModel::class.java]
        }
        val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val stateJob = stateScope.launch { groups.state.collect { } }
        try {
            groups.refresh()
            compose.waitUntil(30_000) {
                !groups.state.value.isLoading && groups.state.value.groups.any { it.all.isNotEmpty() }
            }
            val initial = groups.state.value
            val groupNames = initial.groups.map { it.name }.toSet()
            val candidates = initial.groups.flatMap { group ->
                group.all.asSequence()
                    .filter { it !in groupNames }
                    .map { proxyName -> group to proxyName }
                    .toList()
            }.distinctBy { (group, proxyName) -> "${group.name}:$proxyName" }.take(12)
            assertTrue("授权 Profile 必须至少有一个可测速叶子节点", candidates.isNotEmpty())

            var accepted: Triple<ProxyDelayKey, ProxyDelayUiState, String>? = null
            for ((group, proxyName) in candidates) {
                val testUrl = group.testUrl?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL
                val key = ProxyDelayKey(group.name, proxyName, testUrl)
                compose.runOnIdle { groups.testDelay(group, proxyName) }
                compose.waitUntil(15_000) {
                    groups.state.value.delayResults[key]?.isTesting == false
                }
                val result = groups.state.value.delayResults[key]
                if ((result?.value ?: -1) > 0) {
                    accepted = Triple(key, checkNotNull(result), proxyName)
                    break
                }
            }
            assertNotNull("真实授权 Profile 至少应有一个正延迟节点", accepted)
            val (key, result, proxyName) = checkNotNull(accepted)
            val persisted = runBlocking(Dispatchers.IO) {
                delayStore.load(authorizedProfile.id).firstOrNull {
                    it.groupName == key.groupName && it.proxyName == key.proxyName && it.testUrl == key.testUrl
                }
            }
            assertNotNull("正延迟结果必须写入正式 Room", persisted)
            assertEquals(result.value, checkNotNull(persisted).value)

            // 作者 long：用同名节点构造不同 Profile/旧测试 URL 探针，证明恢复过滤依赖完整复合键。
            val staleProfileId = Long.MAX_VALUE - System.currentTimeMillis()
            assertFalse(repository.profiles.value.any { it.id == staleProfileId })
            val staleUrl = "${key.testUrl}#kclash-delay-probe-$runId"
            runBlocking(Dispatchers.IO) {
                delayStore.upsert(
                    ProxyDelayEntity(
                        staleProfileId,
                        key.groupName,
                        proxyName,
                        key.testUrl,
                        Int.MAX_VALUE,
                        result.updatedAtMillis + 1,
                    ),
                )
                delayStore.upsert(
                    ProxyDelayEntity(
                        authorizedProfile.id,
                        key.groupName,
                        proxyName,
                        staleUrl,
                        Int.MAX_VALUE,
                        result.updatedAtMillis + 2,
                    ),
                )
            }
            marker.writeText(
                JSONObject()
                    .put("profileId", authorizedProfile.id)
                    .put("originalProfileId", originalProfileId ?: JSONObject.NULL)
                    .put("groupName", key.groupName)
                    .put("proxyName", proxyName)
                    .put("testUrl", key.testUrl)
                    .put("staleUrl", staleUrl)
                    .put("staleProfileId", staleProfileId)
                    .put("expectedValue", result.value)
                    .put("seedPid", Process.myPid())
                    .put("writtenAtMillis", result.updatedAtMillis)
                    .toString(),
            )
            InstrumentationRegistry.getInstrumentation().sendStatus(
                2,
                Bundle().apply { putString("kclash_delay_cache", "seeded-positive-room-cache") },
            )
        } finally {
            stateJob.cancel()
            stateScope.cancel()
        }
    }

    @Test
    fun verifySuccessfulDelayAfterProcessRestart() {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("verify", args.getString("kclashDelayPhase"))
        assertPortraitAndDefaultFont()
        val runId = requireRunId(args.getString("kclashDelayRunId"))
        val marker = markerFile(runId)
        assertTrue("必须先运行 seed 阶段并保留进程间标记", marker.isFile)
        val expected = JSONObject(marker.readText())

        val entry = EntryPointAccessors.fromApplication(
            compose.activity.application,
            ConnectionAcceptanceEntryPoint::class.java,
        )
        val repository = entry.profileRepository()
        val manager = entry.runtimeManager()
        val delayStore = RoomProxyDelayStore(entry.database().proxyDelayResults())
        runBlocking(Dispatchers.IO) { repository.refreshCurrentProfile() }
        compose.waitUntil(15_000) {
            repository.currentProfileId.value == expected.getLong("profileId") &&
                repository.profiles.value.any { it.id == expected.getLong("profileId") }
        }
        assertTrue("验收期间不能启动服务或 VPN", manager.state.value == CoreRuntimeStatus.STOPPED)
        assertNotEquals("新 instrumentation 必须运行在重启后的应用进程", expected.getInt("seedPid"), Process.myPid())

        lateinit var groups: ProxyGroupsViewModel
        compose.activityRule.scenario.onActivity { activity ->
            groups = ViewModelProvider(activity)[ProxyGroupsViewModel::class.java]
        }
        val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val stateJob = stateScope.launch { groups.state.collect { } }
        try {
            groups.refresh()
            val expectedKey = ProxyDelayKey(
                expected.getString("groupName"),
                expected.getString("proxyName"),
                expected.getString("testUrl"),
            )
            compose.waitUntil(30_000) {
                val state = groups.state.value
                !state.isLoading &&
                    state.groups.any { it.name == expectedKey.groupName && groupTestUrl(it) == expectedKey.testUrl } &&
                    state.delayResults[expectedKey]?.value == expected.getInt("expectedValue")
            }
            val state = groups.state.value
            val group = checkNotNull(state.groups.firstOrNull { it.name == expectedKey.groupName })
            assertEquals(
                "当前节点徽标必须读取进程重建后恢复的缓存",
                expected.getInt("expectedValue"),
                group.let { state.delayState(it, expectedKey.proxyName)?.value },
            )
            assertFalse(
                "旧测速 URL 的同名节点结果不能进入当前页面",
                state.delayResults.keys.any {
                    it.groupName == expectedKey.groupName &&
                        it.proxyName == expectedKey.proxyName &&
                        it.testUrl == expected.getString("staleUrl")
                },
            )
            val staleProfileId = expected.getLong("staleProfileId")
            val staleProfileRow = runBlocking(Dispatchers.IO) {
                delayStore.load(staleProfileId).single()
            }
            assertEquals(Int.MAX_VALUE, staleProfileRow.value)
            assertEquals(
                "另一个 Profile 的同 URL 同名节点不能覆盖当前 Profile 缓存",
                expected.getInt("expectedValue"),
                state.delayResults[expectedKey]?.value,
            )
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        } finally {
            stateJob.cancel()
            stateScope.cancel()
            val original = expected.opt("originalProfileId")
            if (original is Number && repository.profiles.value.any { it.id == original.toLong() }) {
                runBlocking(Dispatchers.IO) {
                    manager.selectProfile(original.toLong(), repository::selectProfile)
                }
            }
        }
        runBlocking(Dispatchers.IO) {
            delayStore.delete(
                expected.getLong("staleProfileId"),
                expected.getString("groupName"),
                expected.getString("proxyName"),
                expected.getString("testUrl"),
            )
            delayStore.delete(
                expected.getLong("profileId"),
                expected.getString("groupName"),
                expected.getString("proxyName"),
                expected.getString("staleUrl"),
            )
                assertTrue(delayStore.load(expected.getLong("staleProfileId")).isEmpty())
                assertTrue(
                    delayStore.load(expected.getLong("profileId")).any {
                        it.groupName == expected.getString("groupName") &&
                            it.proxyName == expected.getString("proxyName") &&
                            it.testUrl == expected.getString("testUrl") &&
                            it.value == expected.getInt("expectedValue")
                    },
                )
                assertTrue(
                    delayStore.load(expected.getLong("profileId")).none {
                        it.groupName == expected.getString("groupName") &&
                            it.proxyName == expected.getString("proxyName") &&
                            it.testUrl == expected.getString("staleUrl")
                    },
                )
        }
            assertTrue("应用内验收标记应在成功后清理", marker.delete())
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("kclash_delay_cache", "process-restart-profile-and-url-isolation-passed") },
        )
    }

    private fun assertPortraitAndDefaultFont() {
        val configuration = compose.activity.resources.configuration
        assertEquals(Configuration.ORIENTATION_PORTRAIT, configuration.orientation)
        assertEquals(1f, configuration.fontScale, 0.01f)
    }

    private fun markerFile(runId: String): File =
        File(compose.activity.filesDir, "proxy-delay-process-$runId.json")

    private fun groupTestUrl(group: com.lonnnnnng.flclash.core.ProxyGroupInfo): String =
        group.testUrl?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL

    private fun requireRunId(value: String?): String {
        require(!value.isNullOrBlank() && value.all { it.isLetterOrDigit() || it == '-' })
        return value
    }
}
