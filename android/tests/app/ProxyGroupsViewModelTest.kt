package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProxyDelayEntity
import com.lonnnnnng.flclash.data.ProxyDelayStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.data.decodeUnfoldSet
import com.lonnnnnng.flclash.data.encodeUnfoldSet
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.service.ProxyDelayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyGroupsViewModelTest {
    @Test fun localImportPolicySeparatesCustomOwnedProviderBuiltinAndNestedGroups() {
        val group = ProxyGroupInfo("nested", "Selector", "leaf", listOf("leaf"), false)
        val remote = ProxyGroupsUiState(groups = listOf(group), proxyTypes = mapOf("leaf" to "Vless", "relay" to "Relay"))
        assertTrue(remote.canAddToLocal("leaf"))
        assertTrue(!remote.canAddToLocal("DIRECT") && !remote.canAddToLocal("GLOBAL") && !remote.canAddToLocal("nested") && !remote.canAddToLocal("relay"))
        val custom = remote.copy(customProfile = true)
        assertTrue(!custom.canAddToLocal("leaf"))
        assertTrue(custom.copy(providerProxies = setOf("leaf")).canAddToLocal("leaf"))
    }
    @Test fun protocolDescriptionsUseLeafTypesAndLooseNestedGroupExit() {
        val nested = ProxyGroupInfo("nested", "Selector", "leaf", listOf("leaf"), false)
        val state = ProxyGroupsUiState(groups = listOf(nested), proxyTypes = mapOf("leaf" to "Vless", "DIRECT" to "Direct"))
        assertEquals("Vless", state.proxyType("leaf", ProxyNodeCardSize.LOOSE))
        assertEquals("Direct", state.proxyType("DIRECT", ProxyNodeCardSize.COMPACT))
        assertEquals("Selector", state.proxyType("nested", ProxyNodeCardSize.STANDARD))
        assertEquals("Selector(leaf)", state.proxyType("nested", ProxyNodeCardSize.LOOSE))
        assertNull(state.proxyType("missing", ProxyNodeCardSize.STANDARD))
        val cyclic = state.copy(groups = listOf(nested.copy(now = "nested", all = listOf("nested"))))
        assertEquals("Selector", cyclic.proxyType("nested", ProxyNodeCardSize.LOOSE))
    }

    @Test fun typeMetadataRefreshesAfterSelectionAndIsReplacedWhenProfileChanges() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val group = ProxyGroupInfo("Shared", "Selector", "a", listOf("a", "b"), false)
        val old = Profile.custom(701, "old")
        val next = Profile.custom(702, "next")
        val store = SelectionProfileStore(old).apply { profiles.value = listOf(old, next) }
        val first = ProxyGroupsSnapshot(listOf(group), mapOf("a" to "Trojan", "b" to "Vless"), setOf("a"))
        val selected = first.copy(proxyTypes = first.proxyTypes + ("b" to "Shadowsocks"), providerProxies = setOf("b"))
        val runtime = FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, first, Result.success(selected))
        val model = ProxyGroupsViewModel(runtime, store)
        val observing = backgroundScope.launch { model.state.collect {} }
        try {
            runCurrent()
            assertEquals(first.proxyTypes, model.state.value.proxyTypes)
            assertEquals(setOf("a"), model.state.value.providerProxies)
            assertEquals(old.id, model.state.value.profileId)
            assertTrue(model.state.value.customProfile)
            model.selectProxy(group, "b")
            runCurrent()
            assertEquals(selected.proxyTypes, model.state.value.proxyTypes)
            assertEquals(setOf("b"), model.state.value.providerProxies)
            runtime.snapshot = ProxyGroupsSnapshot(listOf(group), mapOf("a" to "Direct"))
            store.currentProfileId.value = next.id
            runCurrent()
            assertEquals(mapOf("a" to "Direct"), model.state.value.proxyTypes)
            assertEquals(emptySet<String>(), model.state.value.providerProxies)
            assertEquals(next.id, model.state.value.profileId)
        } finally { observing.cancel(); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun listLocationCountsVisibleRowsAndTargetsDirectMemberAfterFiltering() {
        val first = ProxyGroupInfo("One", "Selector", "a", listOf("a", "b", "c"), false)
        val second = ProxyGroupInfo("Two", "Selector", "nested", listOf("x", "nested"), false)
        val groups = listOf(first, second)
        val members = mapOf("One" to first.all, "Two" to listOf("nested", "x"))
        assertEquals(4, proxyListSelectedItemIndex(groups, members, setOf("One", "Two"), emptyMap(), "Two", 2))
        assertEquals(2, proxyListSelectedItemIndex(groups, members, setOf("Two"), emptyMap(), "Two", 2))
        assertEquals(3, proxyListSelectedItemIndex(groups, members, setOf("One"), emptyMap(), "Two", 2))
        assertNull(proxyListSelectedItemIndex(groups, members + ("Two" to listOf("x")), setOf("Two"), emptyMap(), "Two", 2))
        assertNull(proxyListSelectedItemIndex(groups, members, emptySet(), emptyMap(), "missing", 2))
    }

    @Test fun semanticScrollAnchorFollowsNodeAcrossListAndGridShapeChanges() {
        val group = ProxyGroupInfo("One", "Selector", "node-20", listOf("node-20", "node-00", "node-10", "node-30"), false)
        val oldMembers = mapOf(group.name to group.all)
        val oldItems = proxyListScrollItems(listOf(group), oldMembers, setOf(group.name), columns = 2)
        val anchor = proxyListScrollAnchor(oldItems, firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 19)
        assertEquals(ProxyScrollAnchor("One", "node-20", 19), anchor)

        val sortedMembers = mapOf(group.name to group.all.sorted())
        val sortedItems = proxyListScrollItems(listOf(group), sortedMembers, setOf(group.name), columns = 3)
        assertEquals(1, proxyListScrollItemIndex(sortedItems, checkNotNull(anchor)))

        val filteredItems = proxyListScrollItems(
            listOf(group),
            mapOf(group.name to sortedMembers.getValue(group.name).filterNot { it == "node-20" }),
            setOf(group.name),
            columns = 3,
        )
        // 作者 long：锚定节点被搜索过滤后回到同组组头，不跳到旧像素对应的其他节点。
        assertEquals(0, proxyListScrollItemIndex(filteredItems, checkNotNull(anchor)))

        val gridAnchor = proxyGridScrollAnchor("One", group.all, firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 11)
        assertEquals(ProxyScrollAnchor("One", "node-10", 11), gridAnchor)
        assertEquals(0, proxyGridScrollItemIndex(sortedMembers.getValue(group.name), checkNotNull(gridAnchor), columns = 3))
        assertEquals(0, proxyGridScrollItemIndex(sortedMembers.getValue(group.name).filterNot { it == "node-10" }, checkNotNull(gridAnchor), columns = 3))
    }

    @Test
    fun oldSelectionPersistenceCannotReplaceNewProfileSnapshot() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val oldGroup = ProxyGroupInfo("Old", "Selector", "a", listOf("a", "b"), false)
        val newGroup = oldGroup.copy(name = "New")
        val oldProfile = Profile.custom(501L, "旧配置")
        val nextProfile = Profile.custom(502L, "新配置")
        val store = SelectionProfileStore(oldProfile)
        store.profiles.value = listOf(oldProfile, nextProfile)
        val save = CompletableDeferred<Unit>()
        store.beforeSelectionSave = { save.await() }
        val runtime = FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, ProxyGroupsSnapshot(listOf(oldGroup)))
        val model = ProxyGroupsViewModel(runtime, store)
        val observing = backgroundScope.launch { model.state.collect {} }
        try {
            runCurrent()
            model.selectProxy(oldGroup, "b")
            runCurrent()
            runtime.snapshot = ProxyGroupsSnapshot(listOf(newGroup))
            store.currentProfileId.value = nextProfile.id
            runCurrent()
            save.complete(Unit)
            runCurrent()
            assertEquals(listOf(newGroup), model.state.value.groups)
            assertTrue(model.state.value.selectedMap.isEmpty())
        } finally { observing.cancel(); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test
    fun cancelledOldBatchCannotPublishIntoNewProfile() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val group = ProxyGroupInfo("Shared", "Selector", "a", listOf("a", "b"), false)
        val oldProfile = Profile.custom(503L, "旧配置")
        val nextProfile = Profile.custom(504L, "新配置")
        val store = SelectionProfileStore(oldProfile)
        store.profiles.value = listOf(oldProfile, nextProfile)
        val completion = CompletableDeferred<Unit>()
        val runtime = FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, ProxyGroupsSnapshot(listOf(group)))
        // 作者 long：模拟底层把取消包装成普通失败，旧批次仍不能污染同名新配置节点。
        runtime.beforeBatch = { withContext(NonCancellable) { completion.await() }; error("旧批次结束") }
        val model = ProxyGroupsViewModel(runtime, store)
        val observing = backgroundScope.launch { model.state.collect {} }
        try {
            runCurrent()
            model.testDelayGroup(group)
            runCurrent()
            store.currentProfileId.value = nextProfile.id
            runCurrent()
            completion.complete(Unit)
            runCurrent()
            assertEquals(listOf(group), model.state.value.groups)
            assertNull(model.state.value.batchTestingGroup)
            assertTrue(model.state.value.delayResults.isEmpty())
        } finally { completion.complete(Unit); observing.cancel(); model.viewModelScope.cancel(); runCurrent() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `outbound mode controls visible groups without changing core order`() {
        val visible = ProxyGroupInfo("Visible", "Selector", "node", listOf("node"), false)
        val hidden = visible.copy(name = "Hidden", hidden = true)
        val groups = listOf(hidden, visible)
        assertEquals(listOf(visible), visibleProxyGroups(groups, CoreOutboundMode.RULE))
        assertEquals(groups, visibleProxyGroups(groups, CoreOutboundMode.GLOBAL))
        assertEquals(listOf(visible), visibleProxyGroups(groups, CoreOutboundMode.DIRECT))
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
    fun `delay result survives viewmodel recreation and remains isolated by profile`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Shared", "Selector", "node-a", listOf("node-a"), false, "https://one")
        val profile = Profile.custom(601L, "原配置")
        val store = InMemoryProxyDelayStore()
        val runtime = FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, ProxyGroupsSnapshot(listOf(group)))
        val first = ProxyGroupsViewModel(runtime, SelectionProfileStore(profile), store)
        val firstJob = backgroundScope.launch { first.state.collect {} }

        first.state.first { it.groups.isNotEmpty() }
        first.testDelay(group, "node-a")
        first.state.first { it.delayState(group, "node-a")?.value == 100 }
        assertEquals(100, store.load(profile.id).single().value)
        first.viewModelScope.cancel()
        firstJob.cancel()

        val restored = ProxyGroupsViewModel(runtime, SelectionProfileStore(profile), store)
        val restoredJob = backgroundScope.launch { restored.state.collect {} }
        restored.state.first { it.groups.isNotEmpty() && it.delayState(group, "node-a")?.value == 100 }
        assertEquals(100, restored.state.value.delayState(group, "node-a")?.value)
        restored.viewModelScope.cancel()
        restoredJob.cancel()

        val otherProfile = Profile.custom(602L, "同名新配置")
        val isolated = ProxyGroupsViewModel(runtime, SelectionProfileStore(otherProfile), store)
        val isolatedJob = backgroundScope.launch { isolated.state.collect {} }
        isolated.state.first { it.groups.isNotEmpty() }
        assertNull(isolated.state.value.delayState(group, "node-a"))
        isolated.viewModelScope.cancel()
        isolatedJob.cancel()
    }

    @Test
    fun `expired delay result is removed before profile restoration`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one")
        val profile = Profile.custom(603L, "过期缓存")
        val store = InMemoryProxyDelayStore(
            listOf(
                ProxyDelayEntity(
                    profileId = profile.id,
                    groupName = group.name,
                    proxyName = "node-a",
                    testUrl = "https://one",
                    value = 188,
                    updatedAtMillis = System.currentTimeMillis() - DELAY_RESULT_TTL_MS - 1L,
                ),
            ),
        )
        val model = ProxyGroupsViewModel(
            FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, ProxyGroupsSnapshot(listOf(group))),
            SelectionProfileStore(profile),
            store,
        )
        val stateJob = backgroundScope.launch { model.state.collect {} }

        model.state.first { it.groups.isNotEmpty() }
        assertNull(model.state.value.delayState(group, "node-a"))
        assertTrue(store.load(profile.id).isEmpty())
        model.viewModelScope.cancel()
        stateJob.cancel()
    }

    @Test
    fun `empty delay result deletes stale cache while failure and timeout are restorable`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one")
        val profile = Profile.custom(604L, "测速状态")
        val stale = ProxyDelayEntity(profile.id, "Auto", "node-a", "https://one", 188, System.currentTimeMillis())
        val store = InMemoryProxyDelayStore(listOf(stale))
        val runtime = FakeProxyGroupsRuntime(CoreRuntimeStatus.STOPPED, ProxyGroupsSnapshot(listOf(group)))
        val emptyModel = ProxyGroupsViewModel(runtime, SelectionProfileStore(profile), store)
        val emptyJob = backgroundScope.launch { emptyModel.state.collect {} }
        emptyModel.state.first { it.groups.isNotEmpty() }
        runtime.singleDelayResult = Result.success(null)
        emptyModel.testDelay(group, "node-a")
        emptyModel.state.first { it.delayState(group, "node-a")?.isTesting == false }
        assertTrue(store.load(profile.id).isEmpty())
        emptyModel.viewModelScope.cancel()
        emptyJob.cancel()

        runtime.singleDelayResult = Result.failure(IllegalStateException("失败"))
        val failedModel = ProxyGroupsViewModel(runtime, SelectionProfileStore(profile), store)
        val failedJob = backgroundScope.launch { failedModel.state.collect {} }
        failedModel.state.first { it.groups.isNotEmpty() }
        failedModel.testDelay(group, "node-a")
        failedModel.state.first { it.delayState(group, "node-a")?.value == CoreBridge.DELAY_FAILED }
        assertEquals(CoreBridge.DELAY_FAILED, store.load(profile.id).single().value)
        failedModel.viewModelScope.cancel()
        failedJob.cancel()

        runtime.singleDelayResult = Result.success(ProxyDelay("https://one", "node-a", CoreBridge.DELAY_TIMED_OUT))
        val timeoutModel = ProxyGroupsViewModel(runtime, SelectionProfileStore(profile), store)
        val timeoutJob = backgroundScope.launch { timeoutModel.state.collect {} }
        timeoutModel.state.first { it.groups.isNotEmpty() && it.delayState(group, "node-a")?.value == CoreBridge.DELAY_FAILED }
        timeoutModel.testDelay(group, "node-a")
        timeoutModel.state.first { it.delayState(group, "node-a")?.value == CoreBridge.DELAY_TIMED_OUT }
        assertEquals(CoreBridge.DELAY_TIMED_OUT, store.load(profile.id).single().value)
        timeoutModel.viewModelScope.cancel()
        timeoutJob.cancel()
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
    fun `batch delay targets follow nested manual selection when automatic result is absent`() {
        val nested = ProxyGroupInfo(
            name = "Relay",
            type = "Selector",
            now = null,
            fixed = "node-fixed",
            all = listOf("node-a", "node-fixed"),
            hidden = false,
            testUrl = "https://nested",
        )
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "Relay",
            all = listOf("Relay"),
            hidden = false,
            testUrl = "https://outer",
        )

        assertEquals(
            listOf(ProxyDelayTarget("Relay", ProxyDelayRequest("node-fixed", "https://nested"))),
            resolveDelayTargets(group, listOf(group, nested), mapOf("Relay" to "node-fixed")),
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
    fun `computed selected name prefers automatic now then explicit pin and fixed fallback`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "auto-node",
            fixed = "fixed-node",
            all = listOf("auto-node", "fixed-node", "pinned-node"),
            hidden = false,
        )

        assertEquals("auto-node", selectedProxyName(group))
        assertEquals("auto-node", selectedProxyName(group, mapOf("Auto" to "pinned-node")))
        assertEquals("pinned-node", selectedProxyName(group.copy(now = null), mapOf("Auto" to "pinned-node")))
        assertEquals("fixed-node", selectedProxyName(group.copy(now = null)))
    }

    @Test
    fun `computed selected name ignores invalid automatic pinned and fixed nodes`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "Fallback",
            now = "stale-auto",
            fixed = "stale-fixed",
            all = listOf("node-a"),
            hidden = false,
        )

        assertNull(selectedProxyName(group))
        assertNull(selectedProxyName(group, mapOf("Auto" to "stale-pinned")))
    }

    @Test
    fun `selector selected name keeps explicit mapping ahead of core now`() {
        val group = ProxyGroupInfo(
            name = "Manual",
            type = "Selector",
            now = "node-a",
            all = listOf("node-a", "node-b"),
            hidden = false,
        )

        assertEquals("node-b", selectedProxyName(group, mapOf("Manual" to "node-b")))
        assertEquals("node-a", selectedProxyName(group))
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
    fun `list group expansion restores from and persists to the active profile`() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a", "node-b"), false)
        val profile = Profile.custom(101L, "展开状态").copy(
            unfoldSetJson = encodeUnfoldSet(setOf("Auto")),
        )
        val store = SelectionProfileStore(profile)
        val runtime = FakeProxyGroupsRuntime(
            status = CoreRuntimeStatus.RUNNING,
            snapshot = ProxyGroupsSnapshot(listOf(group)),
        )
        val viewModel = ProxyGroupsViewModel(runtime, store)
        val stateJob = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.state.first { it.groups.isNotEmpty() }
        assertEquals(setOf("Auto"), viewModel.state.value.unfoldSet)

        viewModel.toggleGroupExpanded("Auto")
        viewModel.state.first { it.unfoldSet.isEmpty() }
        assertTrue(store.unfoldSet().isEmpty())

        viewModel.toggleGroupExpanded("Auto")
        viewModel.state.first { it.unfoldSet == setOf("Auto") }
        assertEquals(setOf("Auto"), store.unfoldSet())
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

    @Test
    fun `proxy page options sort by name and keep nested groups when hiding timeouts`() {
        val nested = ProxyGroupInfo("Relay", "Selector", "node-c", listOf("node-c"), false)
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-timeout",
            all = listOf("node-timeout", "Relay", "node-fast", "node-fail"),
            hidden = false,
            testUrl = "https://one",
        )
        val state = ProxyGroupsUiState(
            groups = listOf(group, nested),
            delayResults = mapOf(
                ProxyDelayKey("Auto", "node-timeout", "https://one") to
                    ProxyDelayUiState(value = CoreBridge.DELAY_TIMED_OUT, updatedAtMillis = Long.MAX_VALUE),
                ProxyDelayKey("Auto", "node-fast", "https://one") to
                    ProxyDelayUiState(value = 120, updatedAtMillis = Long.MAX_VALUE),
                ProxyDelayKey("Auto", "node-fail", "https://one") to
                    ProxyDelayUiState(value = CoreBridge.DELAY_FAILED, updatedAtMillis = Long.MAX_VALUE),
            ),
        )

        assertEquals(
            listOf("Relay", "node-fail", "node-fast", "node-timeout"),
            sortProxyMembers(group, state.groups, state, group.all, ProxySortOption.NAME),
        )
        assertEquals(
            listOf("node-timeout", "Relay", "node-fast"),
            hideTimedOutProxyMembers(group, state.groups, state, group.all),
        )
    }

    @Test
    fun `delay summary counts success timeout failure and in flight only for the group url`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-fast",
            all = listOf("node-fast", "node-timeout", "node-fail", "node-running", "node-other-url"),
            hidden = false,
            testUrl = "https://group.example",
        )
        val state = ProxyGroupsUiState(
            groups = listOf(group),
            delayResults = mapOf(
                ProxyDelayKey("Auto", "node-fast", "https://group.example") to ProxyDelayUiState(value = 120, updatedAtMillis = 10),
                ProxyDelayKey("Auto", "node-timeout", "https://group.example") to ProxyDelayUiState(value = CoreBridge.DELAY_TIMED_OUT, updatedAtMillis = 10),
                ProxyDelayKey("Auto", "node-fail", "https://group.example") to ProxyDelayUiState(value = CoreBridge.DELAY_FAILED, updatedAtMillis = 10),
                ProxyDelayKey("Auto", "node-running", "https://group.example") to ProxyDelayUiState(isTesting = true),
                ProxyDelayKey("Auto", "node-other-url", "https://other.example") to ProxyDelayUiState(value = 80, updatedAtMillis = 10),
            ),
        )

        assertEquals(
            ProxyDelaySummary(tested = 3, success = 1, timedOut = 1, failed = 1, testing = 1),
            state.delaySummary(group, nowMillis = 10),
        )
    }

    @Test
    fun `delay summary drops expired values and does not turn empty results into failures`() {
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a", "node-b"), false, "https://group.example")
        val state = ProxyGroupsUiState(
            groups = listOf(group),
            delayResults = mapOf(
                ProxyDelayKey("Auto", "node-a", "https://group.example") to ProxyDelayUiState(value = CoreBridge.DELAY_FAILED, updatedAtMillis = 100),
                ProxyDelayKey("Auto", "node-b", "https://group.example") to ProxyDelayUiState(value = null, updatedAtMillis = 100),
            ),
        )

        assertEquals(ProxyDelaySummary(), state.delaySummary(group, nowMillis = 100 + DELAY_RESULT_TTL_MS + 1))
        assertEquals(
            ProxyDelaySummary(tested = 1, failed = 1),
            state.delaySummary(group, nowMillis = 100),
        )
    }

    @Test
    fun `proxy grid density follows flutter column rules`() {
        assertEquals(2, proxyGridColumnCount(360f, ProxyGridDensity.STANDARD))
        assertEquals(3, proxyGridColumnCount(360f, ProxyGridDensity.COMPACT))
        assertEquals(1, proxyGridColumnCount(360f, ProxyGridDensity.LOOSE))
    }
}

private class SelectionProfileStore(profile: Profile) : ProfileStore {
    var beforeSelectionSave: suspend () -> Unit = {}
    override val profiles = MutableStateFlow(listOf(profile))
    override val currentProfileId = MutableStateFlow<Long?>(profile.id)

    override suspend fun selectProfile(id: Long?) = Unit
    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")
    override suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile = error("unused")

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile {
        beforeSelectionSave()
        val updated = profiles.value.single { it.id == id }.copy(selectedMapJson = encodeSelectedMap(selectedMap))
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProfileGroupViewState(
        id: Long,
        groupName: String,
        unfoldSet: Set<String>,
    ): Profile {
        val updated = profiles.value.single { it.id == id }.copy(
            currentGroupName = groupName,
            unfoldSetJson = encodeUnfoldSet(unfoldSet),
        )
        profiles.value = listOf(updated)
        return updated
    }

    fun unfoldSet(): Set<String> = decodeUnfoldSet(profiles.value.single().unfoldSetJson)

    override suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile = error("unused")
    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit
}

private class InMemoryProxyDelayStore(
    initial: List<ProxyDelayEntity> = emptyList(),
) : ProxyDelayStore {
    private val values = initial.toMutableList()

    override suspend fun load(profileId: Long): List<ProxyDelayEntity> =
        values.filter { it.profileId == profileId }.toList()

    override suspend fun upsert(value: ProxyDelayEntity) {
        values.removeAll {
            it.profileId == value.profileId &&
                it.groupName == value.groupName &&
                it.proxyName == value.proxyName &&
                it.testUrl == value.testUrl
        }
        values += value
    }

    override suspend fun delete(profileId: Long, groupName: String, proxyName: String, testUrl: String) {
        values.removeAll {
            it.profileId == profileId && it.groupName == groupName && it.proxyName == proxyName && it.testUrl == testUrl
        }
    }

    override suspend fun deleteOlderThan(profileId: Long, cutoffMillis: Long) {
        values.removeAll { it.profileId == profileId && it.updatedAtMillis < cutoffMillis }
    }
}

private class FakeProxyGroupsRuntime(
    status: CoreRuntimeStatus,
    var snapshot: ProxyGroupsSnapshot,
    private val changeResult: Result<ProxyGroupsSnapshot> = Result.success(snapshot),
) : ProxyGroupsRuntime {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val events: Flow<CoreEvent> = emptyFlow()
    var lastProxyName: String? = null
    var beforeBatch: suspend () -> Unit = {}
    var singleDelayResult: Result<ProxyDelay?>? = null

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = Result.success(snapshot)

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> {
        lastProxyName = proxyName
        return changeResult
    }

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): Result<ProxyDelay?> =
        singleDelayResult ?: Result.success(ProxyDelay(testUrl, proxyName, 100))

    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<com.lonnnnnng.flclash.service.ProxyDelayBatchResult>> = runCatching {
        beforeBatch()
        requests.map { request ->
            com.lonnnnnng.flclash.service.ProxyDelayBatchResult(
                request = request,
                result = ProxyDelay(request.testUrl, request.proxyName, 100),
            )
        }
    }
}
