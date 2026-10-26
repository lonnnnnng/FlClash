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
