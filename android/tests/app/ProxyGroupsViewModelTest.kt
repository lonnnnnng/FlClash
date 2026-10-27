package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.service.ProxyDelayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyGroupsViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `computed selection toggles current node back to automatic mode`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-a",
            all = listOf("node-a", "node-b"),
            hidden = false,
        )

        assertEquals("node-b", computedSelectionTarget(group, "node-b"))
        assertEquals("node-a", computedSelectionTarget(group, "node-a"))
        assertEquals("", computedSelectionTarget(group, "node-a", mapOf("Auto" to "node-a")))
    }

    @Test
    fun `non selectable groups reject selection targets`() {
        val group = ProxyGroupInfo(
            name = "Relay",
            type = "LoadBalance",
            now = "node-a",
            all = listOf("node-a"),
            hidden = false,
        )

        assertNull(computedSelectionTarget(group, "node-a"))
    }

    @Test
    fun `automatic delay event updates only groups with matching url and node`() {
        val state = ProxyGroupsUiState(
            groups = listOf(
                ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one"),
                ProxyGroupInfo("Fallback", "Fallback", "node-a", listOf("node-a"), false, "https://two"),
            ),
        )

        val updated = state.applyDelayEvent(
            CoreEvent.Delay(ProxyDelay("https://one", "node-a", 188)),
        )

        assertEquals(188, updated.delayResults.values.single().value)
        assertTrue(updated.delayResults.keys.single().groupName == "Auto")
    }

    @Test
    fun `proxy search filters members without changing group order`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "Tokyo-01",
            all = listOf("Tokyo-01", "Hong Kong-01", "Tokyo-02"),
            hidden = false,
        )

        assertEquals(listOf("Tokyo-01", "Tokyo-02"), filterProxyMembers(group, "tokyo"))
        assertEquals(group.all, filterProxyMembers(group, "  "))
    }

    @Test
    fun `delay result expires after cache ttl`() {
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one")
        val state = ProxyGroupsUiState(
            groups = listOf(group),
            delayResults = mapOf(
                ProxyDelayKey("Auto", "node-a", "https://one") to
                    ProxyDelayUiState(value = 188, updatedAtMillis = 1_000L),
            ),
        )

        assertEquals(null, state.delayState(group, "node-a", nowMillis = 1_000L + DELAY_RESULT_TTL_MS + 1L))
        assertEquals(188, state.delayState(group, "node-a", nowMillis = 1_000L + DELAY_RESULT_TTL_MS)?.value)
    }

    @Test
    fun `batch delay targets resolve nested groups and deduplicate leaf requests`() {
        val nested = ProxyGroupInfo(
            name = "Relay",
            type = "Selector",
            now = "node-a",
            all = listOf("node-a", "node-b"),
            hidden = false,
            testUrl = "https://nested",
        )
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-a",
            all = listOf("Relay", "Relay"),
            hidden = false,
            testUrl = "https://outer",
        )

        assertEquals(
            listOf(ProxyDelayTarget("Relay", ProxyDelayRequest("node-a", "https://nested"))),
            resolveDelayTargets(group, listOf(group, nested)),
        )
    }

    @Test
    fun `batch delay target cycle is ignored instead of recursing forever`() {
        val first = ProxyGroupInfo("First", "Selector", "Second", listOf("Second"), false)
        val second = ProxyGroupInfo("Second", "Selector", "First", listOf("First"), false)
        val group = ProxyGroupInfo("Auto", "URLTest", null, listOf("First"), false)

        assertTrue(resolveDelayTargets(group, listOf(group, first, second)).isEmpty())
    }

    @Test
    fun `selection resolver follows three nested groups and selected map`() {
        val leaf = ProxyGroupInfo("Leaf", "Selector", "node-c", listOf("node-c"), false)
        val middle = ProxyGroupInfo("Middle", "Selector", "Leaf", listOf("Leaf"), false)
        val outer = ProxyGroupInfo("Outer", "URLTest", "Middle", listOf("Middle"), false)

        val resolved = resolveSelectedProxy(outer, listOf(outer, middle, leaf))

        assertEquals("node-c", resolved.leafName)
        assertEquals(listOf("Outer", "Middle", "Leaf", "node-c"), resolved.path)
        assertTrue(!resolved.cycleDetected)
    }

    @Test
    fun `selection resolver rejects nested cycles`() {
        val first = ProxyGroupInfo("First", "Selector", "Second", listOf("Second"), false)
        val second = ProxyGroupInfo("Second", "Selector", "First", listOf("First"), false)

        val resolved = resolveSelectedProxy(first, listOf(first, second))

        assertEquals(null, resolved.leafName)
        assertTrue(resolved.cycleDetected)
    }

    @Test
    fun `computed selected name prefers explicit pin then fixed then automatic now`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "auto-node",
            fixed = "fixed-node",
            all = listOf("auto-node", "fixed-node", "pinned-node"),
            hidden = false,
        )

        assertEquals("fixed-node", selectedProxyName(group))
        assertEquals("pinned-node", selectedProxyName(group, mapOf("Auto" to "pinned-node")))
    }

    @Test
    fun `successful selection persists to the active profile`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a", "node-b"), false)
        val profile = Profile.custom(100L, "选择持久化").copy(
            selectedMapJson = encodeSelectedMap(mapOf("Auto" to "node-a")),
        )
        val store = SelectionProfileStore(profile)
        val runtime = FakeProxyGroupsRuntime(
            status = CoreRuntimeStatus.RUNNING,
            snapshot = ProxyGroupsSnapshot(listOf(group)),
        )
        val viewModel = ProxyGroupsViewModel(runtime, store)
        val stateJob = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.state.first { it.groups.isNotEmpty() }
        viewModel.selectProxy(group, "node-b")
        viewModel.state.first { it.selectedMap == mapOf("Auto" to "node-b") }

        assertEquals("node-b", runtime.lastProxyName)
        assertEquals(
            mapOf("Auto" to "node-b"),
            decodeSelectedMap(store.profiles.value.single().selectedMapJson),
        )
        assertEquals(mapOf("Auto" to "node-b"), viewModel.state.value.selectedMap)
        stateJob.cancel()
    }

    @Test
    fun `failed selection keeps the previous persisted mapping`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a", "node-b"), false)
        val profile = Profile.custom(101L, "选择失败").copy(
            selectedMapJson = encodeSelectedMap(mapOf("Auto" to "node-a")),
        )
        val store = SelectionProfileStore(profile)
        val runtime = FakeProxyGroupsRuntime(
            status = CoreRuntimeStatus.RUNNING,
            snapshot = ProxyGroupsSnapshot(listOf(group)),
            changeResult = Result.failure(IllegalStateException("节点不可用")),
        )
        val viewModel = ProxyGroupsViewModel(runtime, store)
        val stateJob = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.state.first { it.groups.isNotEmpty() }
        viewModel.selectProxy(group, "node-b")
        viewModel.state.first { it.switchingGroup == null && it.groups.isNotEmpty() }

        assertEquals(
            mapOf("Auto" to "node-a"),
            decodeSelectedMap(store.profiles.value.single().selectedMapJson),
        )
        assertEquals(mapOf("Auto" to "node-a"), viewModel.state.value.selectedMap)
        stateJob.cancel()
    }
}

private class SelectionProfileStore(profile: Profile) : ProfileStore {
    override val profiles = MutableStateFlow(listOf(profile))
    override val currentProfileId = MutableStateFlow<Long?>(profile.id)

    override suspend fun selectProfile(id: Long?) = Unit
    override suspend fun addRemoteProfile(url: String, label: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")
    override suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile = error("unused")

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile {
        val updated = profiles.value.single { it.id == id }.copy(selectedMapJson = encodeSelectedMap(selectedMap))
        profiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile = error("unused")
    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit
}

private class FakeProxyGroupsRuntime(
    status: CoreRuntimeStatus,
    private val snapshot: ProxyGroupsSnapshot,
    private val changeResult: Result<ProxyGroupsSnapshot> = Result.success(snapshot),
) : ProxyGroupsRuntime {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val events: Flow<CoreEvent> = emptyFlow()
    var lastProxyName: String? = null

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = Result.success(snapshot)

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> {
        lastProxyName = proxyName
        return changeResult
    }

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): Result<ProxyDelay?> =
        Result.success(ProxyDelay(testUrl, proxyName, 100))

    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<com.lonnnnnng.flclash.service.ProxyDelayBatchResult>> = Result.success(
        requests.map { request ->
            com.lonnnnnng.flclash.service.ProxyDelayBatchResult(
                request = request,
                result = ProxyDelay(request.testUrl, request.proxyName, 100),
            )
        },
    )
}
