package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.data.decodeUnfoldSet
import com.lonnnnnng.flclash.data.encodeUnfoldSet
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
import org.junit.After
import org.junit.Before
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

    private lateinit var previousPageOptions: ProxyPageOptions

    @Before
    fun isolateProxyPageOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previousPageOptions = ProxyPageOptionsStore.load(context)
        ProxyPageOptionsStore.save(context, ProxyPageOptions())
    }

    @After
    fun restoreProxyPageOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ProxyPageOptionsStore.save(context, previousPageOptions)
    }

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
        composeRule.onNodeWithTag("proxy_group_search_toggle").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            viewModel.state.value.selectedMap == mapOf("Auto" to "node-b")
        }
        assertEquals("node-b", runtime.lastTarget)
        assertEquals(mapOf("Auto" to "node-b"), store.selectedMap())

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
        composeRule.onNodeWithTag("proxy_group_search_toggle").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            runtime.lastTarget == "node-b" && viewModel.state.value.switchingGroup == null
        }
        assertTrue(viewModel.state.value.selectedMap.isEmpty())
        assertTrue(store.selectedMap().isEmpty())
        assertEquals("node-b", runtime.lastTarget)
    }

    @Test
    fun nestedProxyGroupSummaryShowsResolvedLeafNode() {
        val outer = ProxyGroupInfo(
            name = "Outer",
            type = "URLTest",
            now = "Middle",
            all = listOf("Middle"),
            hidden = false,
            testUrl = "https://example.com",
        )
        val middle = ProxyGroupInfo(
            name = "Middle",
            type = "Selector",
            now = "Leaf",
            all = listOf("Leaf"),
            hidden = false,
        )
        val leaf = ProxyGroupInfo(
            name = "Leaf",
            type = "Selector",
            now = "node-c",
            all = listOf("node-c"),
            hidden = false,
        )
        val store = FakeProxyPanelProfileStore()
        val runtime = FakeProxyPanelRuntime(ProxyGroupsSnapshot(listOf(outer, middle, leaf)))
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
            composeRule.onAllNodesWithTag("proxy_group_Outer").fetchSemanticsNodes().isNotEmpty()
        }
        // 作者 long：完整代理页不再增加独立摘要排；嵌套出口与 Flutter 一样在宽松节点卡的协议行展示。
        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_card_size").performClick()
        composeRule.onNodeWithTag("proxy_card_size_option_LOOSE").performClick()
        composeRule.onNodeWithText("Selector(node-c)").assertIsDisplayed()
    }

    @Test
    fun listGroupHeaderTogglesNodesAndPersistsProfileViewState() {
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
                    onToggleGroup = viewModel::toggleGroupExpanded,
                    onSelect = viewModel::selectProxy,
                    onTestDelay = viewModel::testDelay,
                    onTestGroupDelay = viewModel::testDelayGroup,
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_Auto").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_display_mode").performClick()
        composeRule.onNodeWithTag("proxy_display_mode_LIST").performClick()
        assertTrue(composeRule.onAllNodesWithTag("proxy_list_node_Auto_node-a").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("proxy_list_group_header_Auto").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_list_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(setOf("Auto"), store.unfoldSet())
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
    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(selectedMapJson = encodeSelectedMap(selectedMap))
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateProfileGroupViewState(
        id: Long,
        groupName: String,
        unfoldSet: Set<String>,
    ): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(
            currentGroupName = groupName,
            unfoldSetJson = encodeUnfoldSet(unfoldSet),
        )
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit

    fun selectedMap(): Map<String, String> = decodeSelectedMap(mutableProfiles.value.single().selectedMapJson)

    fun unfoldSet(): Set<String> = decodeUnfoldSet(mutableProfiles.value.single().unfoldSetJson)
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
