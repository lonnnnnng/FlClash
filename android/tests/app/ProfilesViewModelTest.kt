package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete is rejected while core is running and repository is untouched`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(1L, "运行中配置")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING),
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.delete(profile)

        assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, error.await())
        advanceUntilIdle()
        assertEquals(0, store.deleteCalls)
    }

    @Test
    fun `delete delegates after core is stopped`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(2L, "已停止配置")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )

        viewModel.delete(profile)
        advanceUntilIdle()

        assertEquals(1, store.deleteCalls)
    }

    @Test
    fun `custom rules editor reads and saves only the rules section`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(3L, "规则配置")
        val store = FakeProfileStore(profile).apply {
            content = "mixed-port: 7890\nrules:\n  - DOMAIN,example.com,DIRECT\ndns:\n  enable: true\n"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )

        viewModel.openRulesEditor(profile)
        advanceUntilIdle()
        assertEquals("DOMAIN,example.com,DIRECT", viewModel.rulesEditorState.value.rules)

        viewModel.saveRules("MATCH,DIRECT")
        advanceUntilIdle()

        assertEquals(
            "mixed-port: 7890\nrules:\n  - MATCH,DIRECT\ndns:\n  enable: true\n",
            store.updatedContent,
        )
        assertNull(viewModel.rulesEditorState.value.profile)
    }

    @Test
    fun `custom rules editor keeps dialog open when a rule is invalid`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(4L, "非法规则")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )
        viewModel.openRulesEditor(profile)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.saveRules("SUB-RULE,local,DIRECT")
        advanceUntilIdle()

        assertEquals("第 1 行不支持 SUB-RULE，请展开为具体规则", error.await())
        assertNull(store.updatedContent)
        assertEquals(profile.id, viewModel.rulesEditorState.value.profile?.id)
    }

    @Test
    fun `custom proxies editor reads and saves only the proxies section`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(5L, "代理配置")
        val store = FakeProfileStore(profile).apply {
            content = "mixed-port: 7890\nproxies:\n  - name: old\n    type: vmess\nrules:\n  - MATCH,DIRECT\n"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )

        viewModel.openProxiesEditor(profile)
        advanceUntilIdle()
        assertEquals("- name: old\n  type: vmess", viewModel.proxiesEditorState.value.proxies)

        viewModel.saveProxies("- name: new\n  type: trojan")
        advanceUntilIdle()

        assertEquals(
            "mixed-port: 7890\nproxies:\n  - name: new\n    type: trojan\nrules:\n  - MATCH,DIRECT\n",
            store.updatedContent,
        )
        assertNull(viewModel.proxiesEditorState.value.profile)
    }

    @Test
    fun `custom proxies editor keeps dialog open when a proxy is invalid`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(6L, "非法代理")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )
        viewModel.openProxiesEditor(profile)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.saveProxies("- name: only-name")
        advanceUntilIdle()

        assertEquals("第 1 个代理缺少 type", error.await())
        assertNull(store.updatedContent)
        assertEquals(profile.id, viewModel.proxiesEditorState.value.profile?.id)
    }

    @Test
    fun `custom proxy groups editor reads and saves only the proxy groups section`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(7L, "代理组配置")
        val store = FakeProfileStore(profile).apply {
            content = "mixed-port: 7890\nproxy-groups:\n  - name: Old\n    type: select\nrules:\n  - MATCH,DIRECT\n"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )

        viewModel.openProxyGroupsEditor(profile)
        advanceUntilIdle()
        assertEquals("- name: Old\n  type: select", viewModel.proxyGroupsEditorState.value.groups)

        viewModel.saveProxyGroups("- name: Auto\n  type: url-test")
        advanceUntilIdle()

        assertEquals(
            "mixed-port: 7890\nproxy-groups:\n  - name: Auto\n    type: url-test\nrules:\n  - MATCH,DIRECT\n",
            store.updatedContent,
        )
        assertNull(viewModel.proxyGroupsEditorState.value.profile)
    }

    @Test
    fun `custom proxy groups editor keeps dialog open when a group is invalid`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(8L, "非法代理组")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )
        viewModel.openProxyGroupsEditor(profile)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.saveProxyGroups("- name: Auto\n  type: random")
        advanceUntilIdle()

        assertEquals("第 1 个代理组 type 不受支持：random", error.await())
        assertNull(store.updatedContent)
        assertEquals(profile.id, viewModel.proxyGroupsEditorState.value.profile?.id)
    }

    @Test
    fun `custom providers editor reads and saves both provider sections`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(14L, "Provider 配置")
        val store = FakeProfileStore(profile).apply {
            content = "mixed-port: 7890\nproxy-providers:\n  old:\n    type: http\nrules:\n  - MATCH,DIRECT\n"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )

        viewModel.openProvidersEditor(profile)
        advanceUntilIdle()
        assertEquals("old:\n  type: http", viewModel.providersEditorState.value.proxyProviders)
        assertEquals("", viewModel.providersEditorState.value.ruleProviders)

        viewModel.saveProviders("new:\n  type: file\n  path: ./new.yaml", "ads:\n  type: http")
        advanceUntilIdle()

        assertEquals(
            "mixed-port: 7890\nproxy-providers:\n  new:\n    type: file\n    path: ./new.yaml\nrules:\n  - MATCH,DIRECT\nrule-providers:\n  ads:\n    type: http\n",
            store.updatedContent,
        )
        assertNull(viewModel.providersEditorState.value.profile)
    }

    @Test
    fun `custom providers editor keeps dialog open when a provider is malformed`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(15L, "非法 Provider")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
        )
        viewModel.openProvidersEditor(profile)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.saveProviders("cloud: http", "")
        advanceUntilIdle()

        assertEquals("第 1 行 Provider 条目必须使用嵌套 map", error.await())
        assertNull(store.updatedContent)
        assertEquals(profile.id, viewModel.providersEditorState.value.profile?.id)
    }
}

private class FakeRuntimeStatusProvider(
    status: CoreRuntimeStatus,
) : CoreRuntimeStatusProvider {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
}

private class FakeProfileStore(
    private val profile: Profile,
) : ProfileStore {
    override val profiles = MutableStateFlow(listOf(profile))
    override val currentProfileId = MutableStateFlow<Long?>(profile.id)
    var deleteCalls = 0
    var content = ""
    var updatedContent: String? = null

    override suspend fun selectProfile(id: Long?) = Unit

    override suspend fun addRemoteProfile(url: String, label: String): Profile =
        error("unused test operation")

    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile =
        error("unused test operation")

    override suspend fun addCustomProfile(label: String): Profile =
        error("unused test operation")

    override suspend fun readLocalProfileContent(id: Long): String = content

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile {
        updatedContent = content
        this.content = content
        return profile
    }

    override suspend fun updateRemoteProfile(id: Long): Profile =
        error("unused test operation")

    override suspend fun deleteProfile(id: Long) {
        deleteCalls++
    }

    override suspend fun reorder(ids: List<Long>) = Unit
}
