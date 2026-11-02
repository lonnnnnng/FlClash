package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Test fun moreSaveKeepsOtherOverridesAndRetainsNewDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(111, "草稿").copy(overridesJson = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = true)).toJsonString())
        val store = FakeProfileStore(profile)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = ProfilesViewModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.openEditor(profile, isNew = true)
        advanceUntilIdle()
        model.openMoreEditor(profile)
        model.saveMoreEditor(com.lonnnnnng.flclash.service.ProfileNtpOverrides(enable = true), com.lonnnnnng.flclash.service.ProfileSnifferOverrides())
        advanceUntilIdle()
        val saved = ProfileProviderOverrides.fromJsonString(store.profiles.value.single().overridesJson)
        assertEquals(true, saved.dns.enable)
        assertEquals(true, saved.ntp.enable)
        assertTrue(model.editorState.value.hasPersistedChanges)
        assertEquals(true, model.editorState.value.overview?.ntpEnabled)
        model.discardEditor()
        advanceUntilIdle()
        assertEquals(0, store.deleteCalls)
    }

    @Test fun customImportWaitsForConfirmationAndRejectsLateFileResult() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(112, "目标")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { com.lonnnnnng.flclash.data.ProfileFileDocument("导入.yaml", "rules: [MATCH,DIRECT]".toByteArray()) },
            overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val oldToken = model.beginCustomImportPick()
        model.closeEditor()
        model.openEditor(profile)
        advanceUntilIdle()
        model.acceptCustomImportFile(oldToken, "content://test/old")
        advanceUntilIdle()
        assertNull(model.customImportState.value.name)
        val token = model.beginCustomImportPick()
        model.acceptCustomImportFile(token, "content://test/new")
        advanceUntilIdle()
        assertEquals("导入.yaml", model.customImportState.value.name)
        assertEquals("rules: []", store.content)
        model.cancelCustomImport()
        assertNull(model.customImportState.value.name)
        assertNull(store.updatedContent)
    }

    @Test fun urlImportConfirmsIntoSameDraftAndRejectsRuntimeChange() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(113, "草稿")
        val store = FakeProfileStore(profile)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = ProfilesViewModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.openEditor(profile, isNew = true)
        advanceUntilIdle()
        model.importCustomFromUrl("https://example.test/import")
        advanceUntilIdle()
        assertEquals("URL 配置", model.customImportState.value.name)
        assertNull(store.updatedContent)
        runtime.set(CoreRuntimeStatus.RUNNING)
        model.confirmCustomImport()
        advanceUntilIdle()
        assertNull(store.updatedContent)
        runtime.set(CoreRuntimeStatus.STOPPED)
        model.confirmCustomImport()
        advanceUntilIdle()
        assertTrue(store.content.contains("rules:"))
        assertEquals(profile.id, model.editorState.value.profile?.id)
        assertTrue(model.editorState.value.hasPersistedChanges)
        assertNull(model.customImportState.value.name)
        assertEquals(1, store.profiles.value.size)
    }

    @Test fun `file editor saves name and key together and failure retains editor`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(98, "old name").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile).apply { content = "original"; fileSaveFailure = IllegalStateException("write failed") }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val failure = async(start = CoroutineStart.UNDISPATCHED) { model.errors.first() }
        model.saveFileEditor(null, "fixture-key", " new name ")
        advanceUntilIdle()
        assertEquals("write failed", failure.await())
        assertEquals(profile, store.profiles.value.single())
        assertEquals(profile.id, model.editorState.value.profile?.id)
        store.fileSaveFailure = null
        model.saveFileEditor(null, "fixture-key", "new name")
        advanceUntilIdle()
        assertEquals("new name", store.profiles.value.single().label)
        assertEquals("fixture-key", store.profiles.value.single().ageSecretKey)
        assertNull(store.fileUpdates.single().second)
        assertNull(model.editorState.value.profile)
    }

    @Test fun `remote extension saves provider and dns overrides without changing source`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(99, "https://example.test/sub", "remote")
        val store = FakeProfileStore(profile).apply { content = "proxy-providers:\n  cloud:\n    type: http\n" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openProvidersEditor(profile)
        advanceUntilIdle()
        assertEquals(profile.id, model.providersEditorState.value.profile?.id)
        model.saveProviders("ignored", "ignored", mapOf("cloud" to ProxyProviderOverride(interval = 300)))
        advanceUntilIdle()
        assertNull(store.updatedContent)
        val afterProvider = store.profiles.value.single()
        model.openDnsEditor(afterProvider)
        model.saveDns(ProfileDnsOverrides(enable = true))
        advanceUntilIdle()
        val saved = ProfileProviderOverrides.fromJsonString(checkNotNull(store.updatedOverridesJson))
        assertEquals(300, saved.proxyProviders["cloud"]?.interval)
        assertEquals(true, saved.dns.enable)
        assertNull(store.updatedContent)
        assertTrue(store.remoteUpdateCalls.isEmpty())
    }

    @Test fun `local key only save does not submit content or download`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(91, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile).apply { content = "original" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        model.saveFileEditor(null, " local-fixture \n")
        advanceUntilIdle()
        assertEquals("local-fixture", store.profiles.value.single().ageSecretKey)
        assertNull(store.fileUpdates.single().second)
        assertEquals("original", store.content)
        assertTrue(store.remoteUpdateCalls.isEmpty())
        assertNull(model.editorState.value.profile)
    }

    @Test fun `picker cancellation does not read or change file editor`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(92, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile)
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("cancelled picker cannot read") }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val token = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(token, null)
        advanceUntilIdle()
        assertEquals(profile.id, model.editorState.value.profile?.id)
        assertNull(model.editorState.value.replacement)
        assertTrue(store.fileUpdates.isEmpty())
    }

    @Test fun `selected file is frozen only saved on confirmation and overrides raw draft`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val bytes = "selected".toByteArray()
        val profile = Profile.custom(93, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile).apply { content = "original" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            com.lonnnnnng.flclash.data.ProfileConfigValidator {},
            com.lonnnnnng.flclash.data.ProfileFileDocumentReader { com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", bytes) }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val token = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(token, "content://fixture/document")
        model.editorState.first { it.replacement != null }
        assertTrue(store.fileUpdates.isEmpty())
        bytes.fill(0)
        model.saveFileEditor("draft", "fixture")
        advanceUntilIdle()
        assertEquals("selected", store.fileUpdates.single().second?.toString(Charsets.UTF_8))
        assertEquals("selected", store.content)
    }

    @Test fun `late picker and reader cannot populate a reopened editor with the same id`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val profile = Profile.custom(94, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile)
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            com.lonnnnnng.flclash.data.ProfileConfigValidator {},
            com.lonnnnnng.flclash.data.ProfileFileDocumentReader {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                finished.complete(Unit)
                com.lonnnnnng.flclash.data.ProfileFileDocument("stale.yaml", "fixture".toByteArray())
            }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val slowToken = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(slowToken, "content://fixture/slow")
        model.closeEditor()
        model.openEditor(profile)
        gate.complete(Unit)
        finished.await()
        advanceUntilIdle()
        assertNull(model.editorState.value.replacement)
        val staleToken = checkNotNull(model.beginFileReplacementPick())
        model.closeEditor()
        model.openEditor(profile)
        advanceUntilIdle()
        val currentToken = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(staleToken, "content://fixture/stale-picker")
        advanceUntilIdle()
        assertNull(model.editorState.value.replacement)
        model.acceptFileReplacement(currentToken, "content://fixture/current-picker")
        model.editorState.first { it.replacement != null }
        assertEquals("stale.yaml", model.editorState.value.replacement?.name)
    }

    @Test fun `failed file save retains pending file and allows key retry or cancellation`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(95, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile).apply { fileSaveFailure = com.lonnnnnng.flclash.data.AgeSecretKeyRequiredException() }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            com.lonnnnnng.flclash.data.ProfileConfigValidator {},
            com.lonnnnnng.flclash.data.ProfileFileDocumentReader { com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", "fixture".toByteArray()) }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val token = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(token, "content://fixture/one")
        model.editorState.first { it.replacement != null }
        val error = async(start = CoroutineStart.UNDISPATCHED) { model.errors.first() }
        model.saveFileEditor(null, "")
        advanceUntilIdle()
        assertTrue(error.await().contains("密钥"))
        assertEquals("1.yaml", model.editorState.value.replacement?.name)
        assertTrue(!model.editorState.value.isSaving)
        store.fileSaveFailure = null
        model.clearFileReplacement()
        model.saveFileEditor("edited", " fixture ")
        advanceUntilIdle()
        assertEquals("edited", store.content)
    }

    @Test fun `invalid selected file and running state cannot save file metadata or body`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(96, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = ProfilesViewModel(store, runtime,
            com.lonnnnnng.flclash.data.ProfileConfigValidator { error("invalid") },
            com.lonnnnnng.flclash.data.ProfileFileDocumentReader { com.lonnnnnng.flclash.data.ProfileFileDocument("invalid.yaml", "fixture".toByteArray()) }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        advanceUntilIdle()
        val token = checkNotNull(model.beginFileReplacementPick())
        model.acceptFileReplacement(token, "content://fixture/invalid")
        model.editorState.first { it.replacement != null }
        model.saveFileEditor(null, "fixture")
        advanceUntilIdle()
        assertTrue(store.fileUpdates.isEmpty())
        model.clearFileReplacement()
        runtime.set(CoreRuntimeStatus.RUNNING)
        model.saveFileEditor(null, "fixture")
        advanceUntilIdle()
        assertTrue(store.fileUpdates.isEmpty())
    }

    @Test fun `key only edit persists trimmed key and refreshes but unchanged key does not`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(35, "https://same.example/subscription").copy(ageSecretKey = "old-fixture")
        val store = FakeProfileStore(profile)
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        model.saveRemoteEditor("", profile.url, true, 60, " new-fixture \n")
        advanceUntilIdle()
        assertEquals("new-fixture", store.profiles.value.single().ageSecretKey)
        assertEquals(listOf(35L), store.remoteUpdateCalls)
        model.openEditor(store.profiles.value.single())
        model.saveRemoteEditor("new name", profile.url, true, 90, " new-fixture ")
        advanceUntilIdle()
        assertEquals(listOf(35L), store.remoteUpdateCalls)
    }

    @Test fun `single import asks for missing key once and retries with trimmed input`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeProfileStore(Profile.custom(1, "existing")).apply { requiresAgeKey = true }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addRemote("https://example.test/encrypted", "import")
        advanceUntilIdle()
        assertTrue(model.ageKeyRequired.value)
        model.submitAgeKey(" fixture-identity \n")
        advanceUntilIdle()
        assertTrue(!model.ageKeyRequired.value)
        assertEquals(2, store.remoteAddCalls.size)
        assertEquals("fixture-identity", store.profiles.value.last().ageSecretKey)
    }

    @Test fun `cancelling missing key discards retry and running state blocks retry writes`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeProfileStore(Profile.custom(1, "existing")).apply { requiresAgeKey = true }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addRemote("https://example.test/encrypted", "import")
        advanceUntilIdle()
        model.cancelAgeImport()
        model.submitAgeKey("fixture")
        advanceUntilIdle()
        assertTrue(!model.ageKeyRequired.value)
        assertEquals(1, store.remoteAddCalls.size)
        val running = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
        running.addRemote("https://example.test/encrypted", "import", "fixture")
        advanceUntilIdle()
        assertEquals(1, store.remoteAddCalls.size)
    }

    @Test fun `late missing key result after cancellation cannot reopen prompt`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = FakeProfileStore(Profile.custom(1, "existing")).apply {
            requiresAgeKey = true
            beforeRemoteAdd = { gate.await() }
        }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addRemote("https://example.test/slow", "import")
        model.cancelAgeImport()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(!model.ageKeyRequired.value)
        assertEquals(1, store.profiles.value.size)
    }

    @Test fun `wrong key on retry reports failure without reopening key prompt`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val url = "https://example.test/encrypted"
        val store = FakeProfileStore(Profile.custom(1, "existing")).apply {
            requiresAgeKey = true
            remoteAddFailures += url
        }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addRemote(url, "import")
        advanceUntilIdle()
        assertTrue(model.ageKeyRequired.value)
        val error = async(start = CoroutineStart.UNDISPATCHED) { model.errors.first() }
        model.submitAgeKey("wrong-fixture")
        advanceUntilIdle()
        assertEquals("subscription import failed", error.await())
        assertTrue(!model.ageKeyRequired.value)
        assertEquals(1, store.profiles.value.size)
    }

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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
        )

        viewModel.delete(profile)
        advanceUntilIdle()

        assertEquals(1, store.deleteCalls)
    }

    @Test
    fun `profile selection delegates in every runtime state`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val current = Profile.custom(80L, "当前配置")
        val target = Profile.custom(81L, "目标配置")

        listOf(
            CoreRuntimeStatus.RUNNING,
            CoreRuntimeStatus.STARTING,
            CoreRuntimeStatus.STOPPING,
        ).forEach { status ->
            val store = FakeProfileStore(current, listOf(target))
            val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(status), overviewDispatcher = mainDispatcher)
            viewModel.select(target.id)
            advanceUntilIdle()

            assertEquals(listOf(target.id), store.selectedProfiles)
        }
    }

    @Test
    fun `custom creation opens a new editor and discard deletes only the new draft`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(24L, "新草稿")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

        viewModel.addCustom(profile.label)
        advanceUntilIdle()
        assertEquals(profile.id, viewModel.editorState.value.profile?.id)
        assertTrue(viewModel.editorState.value.isNew)
        viewModel.discardEditor()
        advanceUntilIdle()
        assertEquals(1, store.deleteCalls)
        assertNull(viewModel.editorState.value.profile)

        viewModel.openEditor(profile)
        advanceUntilIdle()
        viewModel.discardEditor()
        advanceUntilIdle()
        assertEquals(1, store.deleteCalls)
        assertNull(viewModel.editorState.value.profile)
    }

    @Test
    fun `failed draft deletion keeps editor available for retry`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(25L, "新草稿")
        val store = FakeProfileStore(profile).apply { deleteFailure = true }
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        viewModel.addCustom(profile.label)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.discardEditor()
        advanceUntilIdle()

        assertEquals("draft deletion failed", error.await())
        assertEquals(profile.id, viewModel.editorState.value.profile?.id)
        assertTrue(viewModel.editorState.value.isNew)
        assertEquals(false, viewModel.editorState.value.isSaving)
    }

    @Test
    fun `saving a structured section refreshes the parent editor and preserves the draft`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(27L, "新草稿")
        val original = "mixed-port: 7890\nrules:\n  - DOMAIN,example.com,DIRECT\n"
        val store = FakeProfileStore(profile).apply { content = original }
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

        viewModel.addCustom(profile.label)
        advanceUntilIdle()
        viewModel.openRulesEditor(profile)
        advanceUntilIdle()
        viewModel.saveRules("MATCH,DIRECT")
        advanceUntilIdle()

        val updated = "mixed-port: 7890\nrules:\n  - MATCH,DIRECT\n"
        assertEquals(updated, viewModel.editorState.value.content)
        assertTrue(viewModel.editorState.value.hasPersistedChanges)
        viewModel.discardEditor()
        advanceUntilIdle()

        assertEquals(0, store.deleteCalls)
        assertNull(viewModel.editorState.value.profile)
        assertEquals(updated, store.content)
    }

    @Test
    fun `metadata section save marks a new draft as persisted`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(28L, "新草稿")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

        viewModel.addCustom(profile.label)
        advanceUntilIdle()
        viewModel.openDnsEditor(profile)
        viewModel.saveDns(ProfileDnsOverrides(enable = true, enhancedMode = "fake-ip"))
        advanceUntilIdle()

        assertTrue(viewModel.editorState.value.hasPersistedChanges)
        viewModel.finishNewDraftEditor(null)

        assertEquals(0, store.deleteCalls)
        assertNull(viewModel.editorState.value.profile)
    }

    @Test
    fun `saved new draft can be renamed before leaving`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(29L, "未命名配置")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

        viewModel.addCustom(profile.label)
        advanceUntilIdle()
        viewModel.openRulesEditor(profile)
        advanceUntilIdle()
        viewModel.saveRules("MATCH,DIRECT")
        advanceUntilIdle()
        viewModel.finishNewDraftEditor("家庭网络")
        advanceUntilIdle()

        assertEquals("家庭网络", store.updatedLabel)
        assertEquals(0, store.deleteCalls)
        assertNull(viewModel.editorState.value.profile)
    }

    @Test
    fun renamedDraftKeepsUpdatedTitleAndIsNotDeletedAsUntouched() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(31L, "未命名配置")
        val store = FakeProfileStore(profile).apply { content = "rules: ['MATCH,DIRECT']" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addCustom(profile.label)
        advanceUntilIdle()
        model.rename(profile, "家庭配置")
        advanceUntilIdle()
        assertEquals("家庭配置", model.editorState.value.profile?.label)
        assertTrue(model.editorState.value.hasPersistedChanges)
        assertEquals(1, model.editorState.value.overview?.rules)
        model.discardEditor()
        advanceUntilIdle()
        assertEquals(0, store.deleteCalls)
        assertNull(model.editorState.value.profile)
    }

    @Test
    fun scriptBindingRefreshesDraftMetadataAndPreservesDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(32L, "未命名配置")
        val store = FakeProfileStore(profile).apply { content = "dns: {enable: true}" }
        val model = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addCustom(profile.label)
        advanceUntilIdle()
        model.bindScript(profile, 7L)
        advanceUntilIdle()
        assertEquals(7L, model.editorState.value.profile?.scriptId)
        assertTrue(model.editorState.value.hasPersistedChanges)
        assertTrue(checkNotNull(model.editorState.value.overview).dnsEnabled)
        model.finishNewDraftEditor(null)
        assertEquals(0, store.deleteCalls)
        assertNull(model.editorState.value.profile)
    }

    @Test
    fun `custom creation is rejected while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeProfileStore(Profile.custom(26L, "已有配置"))
        val viewModel = ProfilesViewModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.addCustom("新配置")
        advanceUntilIdle()

        assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, error.await())
        assertEquals(0, store.customAddCalls)
        assertNull(viewModel.editorState.value.profile)
    }

    @Test
    fun `remote profile auto update toggle persists metadata`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(3L, "https://example.test/subscription")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.setAutoUpdate(profile, false)
        advanceUntilIdle()

        assertEquals(listOf(profile.id to false), store.autoUpdateChanges)
        assertEquals(false, store.profiles.value.single().autoUpdate)
    }

    @Test
    fun `remote editor saves settings and refreshes only when subscription url changes`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(31L, "https://old.example/subscription", "旧名称")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openEditor(profile)
        assertEquals(profile.id, viewModel.remoteEditorState.value.profile?.id)
        viewModel.saveRemoteEditor(
            label = "新名称",
            url = " https://new.example/subscription ",
            autoUpdate = false,
            intervalMinutes = 45L,
        )
        advanceUntilIdle()

        assertEquals(
            listOf(RemoteSettingsUpdate(31L, "新名称", "https://new.example/subscription", false, 45L * 60_000L)),
            store.remoteSettingsUpdates,
        )
        assertEquals(listOf(31L), store.remoteUpdateCalls)
        assertNull(viewModel.remoteEditorState.value.profile)
    }

    @Test
    fun `remote editor rejects invalid url without changing the profile`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(32L, "https://old.example/subscription")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.openEditor(profile)
        viewModel.saveRemoteEditor("名称", "file:///tmp/config.yaml", true, 60L)
        advanceUntilIdle()

        assertEquals("请输入有效的 HTTP/HTTPS 订阅链接", error.await())
        assertTrue(store.remoteSettingsUpdates.isEmpty())
        assertEquals(profile.url, store.profiles.value.single().url)
        assertEquals(profile.id, viewModel.remoteEditorState.value.profile?.id)
    }

    @Test
    fun `remote editor does not download when the subscription url is unchanged`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(33L, "https://same.example/subscription", "旧名称")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openEditor(profile)
        viewModel.saveRemoteEditor(
            label = "新名称",
            url = profile.url,
            autoUpdate = true,
            intervalMinutes = 30L,
        )
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), store.remoteUpdateCalls)
        assertEquals("新名称", store.profiles.value.single().label)
        assertNull(viewModel.remoteEditorState.value.profile)
    }

    @Test
    fun `batch import is sequential skips existing urls and continues after failures`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val existing = Profile.remote(35L, "https://existing.invalid/sub")
        val store = FakeProfileStore(Profile.custom(36L, "自定义"), listOf(existing)).apply {
            remoteAddFailures += "https://failed.invalid/sub"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.addRemoteBatch(
            listOf(
                existing.url,
                "https://first.invalid/sub",
                "https://failed.invalid/sub",
                "https://last.invalid/sub",
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                "https://first.invalid/sub",
                "https://failed.invalid/sub",
                "https://last.invalid/sub",
            ),
            store.remoteAddCalls,
        )
        assertEquals(
            "批量导入完成：新增 2 个，跳过重复 1 个，失败 1 个",
            error.await(),
        )
        assertEquals(
            listOf("https://existing.invalid/sub", "https://first.invalid/sub", "https://last.invalid/sub"),
            store.profiles.value.filter { it.type == com.lonnnnnng.flclash.data.ProfileType.URL }.map(Profile::url),
        )
    }

    @Test
    fun `remote editor keeps existing interval when auto update is disabled`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(34L, "https://same.example/subscription")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openEditor(profile)
        viewModel.saveRemoteEditor("", profile.url, false, 0L)
        advanceUntilIdle()

        assertEquals(Profile.DEFAULT_UPDATE_INTERVAL_MILLIS, store.remoteSettingsUpdates.single().intervalMillis)
        assertEquals(false, store.remoteSettingsUpdates.single().autoUpdate)
    }

    @Test
    fun `rename trims and persists profile label`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(4L, "旧名称")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.rename(profile, "  新名称  ")
        advanceUntilIdle()

        assertEquals("新名称", store.profiles.value.single().label)
        assertEquals("新名称", store.updatedLabel)
    }

    @Test
    fun `rename is rejected while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(5L, "运行中名称")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.rename(profile, "新名称")

        assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, error.await())
        advanceUntilIdle()
        assertNull(store.updatedLabel)
        assertEquals("运行中名称", store.profiles.value.single().label)
    }

    @Test
    fun `reorder delegates the requested order and keeps omitted profiles`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(20L, "第一项")
        val second = Profile.custom(21L, "第二项")
        val third = Profile.custom(22L, "第三项")
        val store = FakeProfileStore(first, listOf(second, third))
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.reorder(listOf(third.id, first.id))
        advanceUntilIdle()

        assertEquals(listOf(third.id, first.id, second.id), store.reorderedIds)
    }

    @Test
    fun `reorder is rejected while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(23L, "运行中第一项")
        val second = Profile.custom(24L, "运行中第二项")
        val store = FakeProfileStore(first, listOf(second))
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.reorder(listOf(second.id, first.id))

        assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, error.await())
        advanceUntilIdle()
        assertNull(store.reorderedIds)
    }

    @Test
    fun `script binding persists and can be cleared`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(25L, "脚本配置")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.bindScript(profile, 901L)
        advanceUntilIdle()
        assertEquals(listOf<Long?>(901L), store.scriptBindingUpdates)
        assertEquals(901L, store.profiles.value.single().scriptId)

        viewModel.bindScript(store.profiles.value.single(), null)
        advanceUntilIdle()
        assertEquals(listOf<Long?>(901L, null), store.scriptBindingUpdates)
        assertNull(store.profiles.value.single().scriptId)
    }

    @Test
    fun `script binding is rejected while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(26L, "运行中脚本配置")
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.bindScript(profile, 902L)

        assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, error.await())
        advanceUntilIdle()
        assertEquals(emptyList<Long?>(), store.scriptBindingUpdates)
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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
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
    fun `visual groups save refreshes parent and refuses a running runtime`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(77L, "Visual")
        val store = FakeProfileStore(profile).apply {
            content = "proxy-groups:\n  - {name: Main, type: select, proxies: [DIRECT]}\nrules: ['MATCH,Main']\n"
        }
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val viewModel = ProfilesViewModel(repository = store, runtimeManager = runtime, overviewDispatcher = mainDispatcher)
        viewModel.openEditor(profile)
        advanceUntilIdle()
        viewModel.openSingleProxyGroupEditor(profile, 0)
        advanceUntilIdle()
        assertEquals(0, viewModel.proxyGroupsEditorState.value.initialGroupIndex)
        val groups = com.lonnnnnng.flclash.data.ProxyGroupDrafts.read(store.content).map { it.edit { fields -> fields.addProperty("name", "Renamed") } }
        viewModel.saveProxyGroupsDraft(com.lonnnnnng.flclash.data.ProxyGroupsSave(groups))
        advanceUntilIdle()
        assertNull(viewModel.proxyGroupsEditorState.value.profile)
        assertTrue(checkNotNull(store.updatedContent).contains("MATCH,Renamed"))
        assertEquals("Renamed", viewModel.editorState.value.overview?.groups?.single()?.name)
        viewModel.openProxyGroupsEditor(profile)
        advanceUntilIdle()
        runtime.set(CoreRuntimeStatus.RUNNING)
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        val before = store.updatedContent
        viewModel.saveProxyGroupsDraft(com.lonnnnnng.flclash.data.ProxyGroupsSave(groups))
        advanceUntilIdle()
        assertEquals("核心运行中，请先停止核心后再保存代理组", error.await())
        assertEquals(before, store.updatedContent)
        viewModel.closeProxyGroupsEditor()
        viewModel.saveProxyGroupsDraft(com.lonnnnnng.flclash.data.ProxyGroupsSave(groups))
        advanceUntilIdle()
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
            overviewDispatcher = mainDispatcher,
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
            overviewDispatcher = mainDispatcher,
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
    fun `custom providers editor persists typed proxy provider overrides`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(16L, "字段 Provider")
        val store = FakeProfileStore(profile).apply {
            content = "proxy-providers:\n  cloud:\n    type: http\n"
        }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openProvidersEditor(profile)
        advanceUntilIdle()
        viewModel.saveProviders(
            proxyProviders = "cloud:\n  type: http",
            ruleProviders = "",
            proxyProviderOverrides = mapOf(
                "cloud" to ProxyProviderOverride(timeout = 2_000, udp = false),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            mapOf("cloud" to ProxyProviderOverride(timeout = 2_000, udp = false)),
            ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).proxyProviders,
        )
        assertNull(viewModel.providersEditorState.value.profile)
    }

    @Test
    fun `custom dns editor persists fields without dropping provider overrides`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(17L, "DNS 配置").copy(
            overridesJson = ProfileProviderOverrides(
                proxyProviders = mapOf("cloud" to ProxyProviderOverride(timeout = 2_000)),
            ).toJsonString(),
        )
        val store = FakeProfileStore(profile)
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openDnsEditor(profile)
        viewModel.saveDns(
            ProfileDnsOverrides(
                enable = true,
                enhancedMode = "fake-ip",
                nameserver = listOf("https://doh.example/dns-query"),
            ),
        )
        advanceUntilIdle()

        val decoded = ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson)
        assertEquals(ProxyProviderOverride(timeout = 2_000), decoded.proxyProviders["cloud"])
        assertEquals("fake-ip", decoded.dns.enhancedMode)
        assertEquals(listOf("https://doh.example/dns-query"), decoded.dns.nameserver)
        assertNull(viewModel.dnsEditorState.value.profile)
    }

    @Test
    fun `custom dialers editor reads and persists mappings without changing yaml`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(18L, "Dialers 配置")
        val original = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: vmess
            proxy-groups:
              - name: Relay
                type: select
                proxies:
                  - node-a
        """.trimIndent() + "\n"
        val store = FakeProfileStore(profile).apply { content = original }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openDialersEditor(profile)
        advanceUntilIdle()
        assertEquals("", viewModel.dialersEditorState.value.dialers)

        viewModel.saveDialers("node-a -> Relay")
        advanceUntilIdle()

        assertEquals(original, store.content)
        assertEquals(
            mapOf("node-a" to "Relay"),
            ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dialers,
        )
        assertNull(viewModel.dialersEditorState.value.profile)
    }

    @Test
    fun `custom dialers editor keeps dialog open when mapping is malformed`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(19L, "非法 Dialers")
        val store = FakeProfileStore(profile).apply { content = "proxies:\n  - name: node-a\n    type: vmess\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )
        viewModel.openDialersEditor(profile)
        advanceUntilIdle()
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.saveDialers("node-a")
        advanceUntilIdle()

        assertTrue(error.await().contains("格式"))
        assertNull(store.updatedOverridesJson)
        assertEquals(profile.id, viewModel.dialersEditorState.value.profile?.id)
    }

    @Test
    fun `saving providers keeps previously stored dialers`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val overrides = ProfileProviderOverrides(dialers = mapOf("node-a" to "Relay"))
        val profile = Profile.custom(20L, "共存覆盖").copy(overridesJson = overrides.toJsonString())
        val store = FakeProfileStore(profile).apply { content = "proxy-providers:\n  cloud:\n    type: http\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openProvidersEditor(profile)
        advanceUntilIdle()
        viewModel.saveProviders("cloud:\n  type: http", "")
        advanceUntilIdle()

        assertEquals(
            mapOf("node-a" to "Relay"),
            ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dialers,
        )
    }

    @Test
    fun `custom providers editor keeps dialog open when a provider is malformed`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(15L, "非法 Provider")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = ProfilesViewModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
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
    fun set(status: CoreRuntimeStatus) { mutableState.value = status }
}

private class FakeProfileStore(
    private val profile: Profile,
    extraProfiles: List<Profile> = emptyList(),
) : ProfileStore {
    override val profiles = MutableStateFlow(listOf(profile) + extraProfiles)
    override val currentProfileId = MutableStateFlow<Long?>(profile.id)
    val selectedProfiles = mutableListOf<Long?>()
    var deleteCalls = 0
    var deleteFailure = false
    var customAddCalls = 0
    var reorderedIds: List<Long>? = null
    var content = ""
    var fileSaveFailure: Exception? = null
    val fileUpdates = mutableListOf<Pair<String, ByteArray?>>()
    var updatedContent: String? = null
    var updatedLabel: String? = null
    val remoteSettingsUpdates = mutableListOf<RemoteSettingsUpdate>()
    val remoteUpdateCalls = mutableListOf<Long>()
    val remoteAddCalls = mutableListOf<String>()
    val remoteAddFailures = mutableSetOf<String>()
    var requiresAgeKey = false
    var beforeRemoteAdd: suspend () -> Unit = {}
    private var nextRemoteId = 1_000L
    var updatedOverridesJson: String? = null
    var autoUpdateChanges = mutableListOf<Pair<Long, Boolean>>()
    val scriptBindingUpdates = mutableListOf<Long?>()

    override suspend fun selectProfile(id: Long?) {
        selectedProfiles += id
    }

    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile {
        remoteAddCalls += url
        beforeRemoteAdd()
        if (requiresAgeKey && ageSecretKey.isBlank()) throw com.lonnnnnng.flclash.data.AgeSecretKeyRequiredException()
        if (url in remoteAddFailures) error("subscription import failed")
        val added = Profile.remote(nextRemoteId++, url, label).copy(ageSecretKey = ageSecretKey)
        profiles.value = profiles.value + added
        return added
    }

    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile =
        error("unused test operation")

    override suspend fun addCustomProfile(label: String): Profile {
        customAddCalls++
        return profile.copy(label = label)
    }

    override suspend fun readLocalProfileContent(id: Long): String = content

    override suspend fun downloadCustomImport(url: String): ByteArray = "rules: []".toByteArray()

    override suspend fun replaceCustomProfile(id: Long, content: String, expected: Profile, expectedContent: String, canWrite: () -> Boolean): Profile {
        check(canWrite())
        check(this.content == expectedContent)
        check(profiles.value.first { it.id == id } == expected)
        return updateLocalProfileContent(id, content)
    }

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile {
        updatedContent = content
        this.content = content
        return profile
    }

    override suspend fun updateFileProfile(id: Long, ageSecretKey: String, replacement: ByteArray?, label: String?, canWrite: () -> Boolean): Profile {
        check(canWrite())
        fileSaveFailure?.let { throw it }
        fileUpdates += ageSecretKey to replacement
        if (replacement != null) content = replacement.toString(Charsets.UTF_8)
        val updated = profiles.value.first { it.id == id }.copy(ageSecretKey = ageSecretKey, label = label ?: profile.label)
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProfileLabel(id: Long, label: String): Profile {
        updatedLabel = label
        val updated = profile.copy(label = label)
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateRemoteProfileSettings(
        id: Long,
        label: String,
        url: String,
        autoUpdate: Boolean,
        intervalMillis: Long,
        ageSecretKey: String,
    ): Profile {
        remoteSettingsUpdates += RemoteSettingsUpdate(id, label, url, autoUpdate, intervalMillis)
        val updated = profile.copy(
            label = label,
            url = url,
            autoUpdate = autoUpdate,
            autoUpdateIntervalMillis = intervalMillis,
            ageSecretKey = ageSecretKey,
        )
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProfileOverrides(id: Long, overridesJson: String): Profile {
        updatedOverridesJson = overridesJson
        val updated = profiles.value.first { it.id == id }.copy(overridesJson = overridesJson)
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile =
        profile.copy(selectedMapJson = com.lonnnnnng.flclash.data.encodeSelectedMap(selectedMap))

    override suspend fun updateProfileAutoUpdate(
        id: Long,
        enabled: Boolean,
        intervalMillis: Long,
    ): Profile {
        autoUpdateChanges += id to enabled
        val updated = profile.copy(autoUpdate = enabled, autoUpdateIntervalMillis = intervalMillis)
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateProfileScript(id: Long, scriptId: Long?): Profile {
        scriptBindingUpdates += scriptId
        val updated = profile.copy(scriptId = scriptId)
        profiles.value = profiles.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateRemoteProfile(id: Long): Profile {
        remoteUpdateCalls += id
        return profiles.value.single { it.id == id }
    }

    override suspend fun deleteProfile(id: Long) {
        deleteCalls++
        if (deleteFailure) error("draft deletion failed")
    }

    override suspend fun reorder(ids: List<Long>) {
        reorderedIds = ids
    }
}

private data class RemoteSettingsUpdate(
    val id: Long,
    val label: String,
    val url: String,
    val autoUpdate: Boolean,
    val intervalMillis: Long,
)
