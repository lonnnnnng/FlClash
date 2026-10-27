package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyDelayBatchResult
import com.lonnnnnng.flclash.service.ProxyDelayRequest
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：代理选择页面必须经过真实 ViewModel 状态流，才能同时验证 Compose 事件、核心切换参数和
 * Profile 持久化；仅测试按钮回调无法发现切换成功后状态回写或失败后旧选择被覆盖的问题。
 */
@RunWith(AndroidJUnit4::class)
class ProxyGroupsViewModelPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionAndComputedAutoRecoveryFlowThroughRealViewModel() {
        val group = autoGroup()
        val store = FakeProxyPanelProfileStore()
        val runtime = FakeProxyPanelRuntime(ProxyGroupsSnapshot(listOf(group)))
        val viewModel = ProxyGroupsViewModel(runtime, store)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            KClashTheme {
                ProxyGroupsCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onRefresh = viewModel::refresh,
                    onSelect = viewModel::selectProxy,
                    onTestDelay = viewModel::testDelay,
                    onTestGroupDelay = viewModel::testDelayGroup,
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_Auto").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            viewModel.state.value.selectedMap == mapOf("Auto" to "node-b")
        }
        assertEquals("node-b", runtime.lastTarget)
        assertEquals(mapOf("Auto" to "node-b"), store.selectedMap())

        composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            viewModel.state.value.selectedMap.isEmpty()
        }
        assertEquals("", runtime.lastTarget)
        assertTrue(store.selectedMap().isEmpty())
    }

    @Test
    fun failedSelectionKeepsPreviousProfileMapping() {
        val group = autoGroup()
        val store = FakeProxyPanelProfileStore()
        val runtime = FakeProxyPanelRuntime(
            snapshot = ProxyGroupsSnapshot(listOf(group)),
            changeResult = Result.failure(IllegalStateException("节点不可用")),
        )
        val viewModel = ProxyGroupsViewModel(runtime, store)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            KClashTheme {
                ProxyGroupsCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onRefresh = viewModel::refresh,
                    onSelect = viewModel::selectProxy,
                    onTestDelay = viewModel::testDelay,
                    onTestGroupDelay = viewModel::testDelayGroup,
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_Auto").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            runtime.lastTarget == "node-b" && viewModel.state.value.switchingGroup == null
        }
        assertTrue(viewModel.state.value.selectedMap.isEmpty())
        assertTrue(store.selectedMap().isEmpty())
        assertEquals("node-b", runtime.lastTarget)
    }

    private fun autoGroup() = ProxyGroupInfo(
        name = "Auto",
        type = "URLTest",
        now = "node-a",
        all = listOf("node-a", "node-b"),
        hidden = false,
        testUrl = "https://example.com",
    )
}

private class FakeProxyPanelProfileStore : ProfileStore {
    private val initial = Profile.custom(301L, "代理选择回归")
    private val mutableProfiles = MutableStateFlow(listOf(initial))
    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = MutableStateFlow<Long?>(initial.id).asStateFlow()

    override suspend fun selectProfile(id: Long?) = Unit
    override suspend fun addRemoteProfile(url: String, label: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(selectedMapJson = encodeSelectedMap(selectedMap))
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit

    fun selectedMap(): Map<String, String> = decodeSelectedMap(mutableProfiles.value.single().selectedMapJson)
}

private class FakeProxyPanelRuntime(
    private val snapshot: ProxyGroupsSnapshot,
    private val changeResult: Result<ProxyGroupsSnapshot> = Result.success(snapshot),
) : ProxyGroupsRuntime {
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.RUNNING)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val events: Flow<CoreEvent> = emptyFlow()
    var lastTarget: String? = null

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = Result.success(snapshot)

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> {
        lastTarget = proxyName
        return changeResult
    }

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): Result<ProxyDelay?> =
        Result.success(ProxyDelay(testUrl, proxyName, 100))

    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<ProxyDelayBatchResult>> = Result.success(emptyList())
}
