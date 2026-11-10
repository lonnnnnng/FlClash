package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val modelJobs = mutableListOf<kotlinx.coroutines.Job>()
    private val modelSchedulers = mutableSetOf<kotlinx.coroutines.test.TestCoroutineScheduler>()

    // 作者 long：真实IO可以晚于runTest虚拟队列结束，统一跟踪VM工作，不能让迟到回调跨越resetMain污染下一用例。
    private fun newModel(
        repository: ProfileStore,
        runtimeManager: CoreRuntimeStatusProvider,
        configValidator: com.lonnnnnng.flclash.data.ProfileConfigValidator = com.lonnnnnng.flclash.data.YamlProfileConfigValidator(),
        fileReader: com.lonnnnnng.flclash.data.ProfileFileDocumentReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("文件读取器不可用") },
        overviewDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        groupFilterValidator: com.lonnnnnng.flclash.data.ProxyGroupFilterValidator = com.lonnnnnng.flclash.data.ProxyGroupFilterValidator { error("过滤表达式校验不可用") },
        draftStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
    ): ProfilesViewModel = ProfilesViewModel(repository, runtimeManager, configValidator, fileReader, overviewDispatcher, groupFilterValidator, draftStore).also { model ->
        modelJobs += checkNotNull(model.viewModelScope.coroutineContext[kotlinx.coroutines.Job])
        (overviewDispatcher as? kotlinx.coroutines.test.TestDispatcher)?.let { modelSchedulers += it.scheduler }
    }

    @Test fun metadataOnlyGroupRecoveryRequiresCommittedLayoutsAndNeverReplays() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (committed in listOf(false, true)) {
            val profile = Profile.custom(830, "layout-only")
            val initial = com.lonnnnnng.flclash.data.ProxyGroupDraft(json = """{"name":"Main","type":"select","proxies":["DIRECT"],"filter":"HK"}""")
            val save = com.lonnnnnng.flclash.data.ProxyGroupsSave(listOf(initial.copy(memberLayout = listOf("filter"))))
            val body = com.lonnnnnng.flclash.data.ProxyGroupDrafts.apply("", save)
            val expected = com.lonnnnnng.flclash.data.ProxyGroupDrafts.updateProfileReferences(profile, save)
            val store = FakeProfileStore(profile).apply { content = body }
            var writes = 0
            val wrapper = object : ProfileStore by store {
                override suspend fun extensionProfile(id: Long): Profile = if (committed) expected else profile
                override suspend fun saveCustomProxyGroups(id: Long, save: com.lonnnnnng.flclash.data.ProxyGroupsSave,
                    expectedContent: String, canWrite: () -> Boolean): Profile { writes++; return expected }
            }
            val drafts = PickerMemoryStore()
            val owner = androidx.lifecycle.ViewModelStore()
            val first = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            owner.put("draft", first); first.openEditor(profile); advanceUntilIdle()
            val snapshot = com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
            owner.clear()
            drafts.bytes = com.google.gson.Gson().toJson(snapshot.copy(groups = ProfileProxyGroupsEditorUiState(profile = profile,
                sourceContent = body, isSaving = true, pendingContent = body,
                pendingLayouts = com.lonnnnnng.flclash.data.ProxyGroupMembers.layoutsJson(save.groups)))).toByteArray()
            val second = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            advanceUntilIdle()
            if (committed) assertNull(second.proxyGroupsEditorState.value.profile)
            else { assertNotNull(second.proxyGroupsEditorState.value.profile); assertTrue(second.proxyGroupsEditorState.value.error.orEmpty().contains("保存已中断")) }
            assertEquals(0, writes)
            second.closeEditor(); advanceUntilIdle(); owner2.clear()
        }
    }

    @Test fun profileSnapshotDistinguishesInitialReadFromLoadedEmptyAndDeletion() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(829, "loading-boundary")
        val store = FakeProfileStore(profile).apply { profiles.value = emptyList(); currentProfileId.value = null }
        val loaded = MutableStateFlow(false)
        val pending = object : ProfileStore by store { override val profilesLoaded: StateFlow<Boolean> = loaded }
        val model = newModel(pending, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("profiles", model) }
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        try {
            runCurrent(); assertFalse(model.state.value.profilesLoaded)
            // 作者 long：首读未就绪时即使配置流先到，也不能把尚未校正的选择暴露给恢复中的详情页。
            store.profiles.value = listOf(profile); runCurrent()
            assertFalse(model.state.value.profilesLoaded); assertTrue(model.state.value.profiles.isEmpty())
            store.profiles.value = emptyList(); runCurrent()
            loaded.value = true; runCurrent()
            assertTrue(model.state.value.profilesLoaded); assertTrue(model.state.value.profiles.isEmpty())
            store.profiles.value = listOf(profile); store.currentProfileId.value = profile.id; runCurrent()
            assertEquals(profile.id, model.state.value.currentProfileId)
            store.profiles.value = emptyList(); store.currentProfileId.value = null; runCurrent()
            // 作者 long：删除最后一项是已加载的空仓库，页面应关闭旧详情，不能再次退回首次读取保护。
            assertTrue(model.state.value.profilesLoaded); assertTrue(model.state.value.profiles.isEmpty())
            assertNull(model.state.value.currentProfileId)
        } finally { collection.cancel(); owner.clear() }
    }

    @Test fun earlyEditorOpenWaitsForFirstReadAndPreservesRecoveredDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (hasSaved in listOf(false, true)) for (entry in 0..3) {
            val profile = Profile.custom(827, "initial-draft-read")
            val content = "proxies: []\nproxy-groups: []\nrules: []"
            val store = FakeProfileStore(profile).apply { this.content = content }
            val ui = EditorSaveableState(mapOf("private" to listOf(EditorSavedValue.capture("original-private-draft"))))
            val snapshot = ProfileEditorDraftSnapshot(editor = ProfileEditorUiState(profile = profile, content = content),
                proxies = ProfileProxiesEditorUiState(profile = profile, sourceContent = content, proxies = "[]", uiDraft = ui))
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val drafts = PickerMemoryStore().apply {
                if (hasSaved) bytes = com.google.gson.Gson().toJson(snapshot).toByteArray()
                readGate = gate
            }
            val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", model) }
            when (entry) {
                0 -> model.openEditor(profile)
                1 -> model.openRulesEditor(profile)
                2 -> model.openProxiesEditor(profile)
                else -> model.openSingleProxyGroupEditor(profile, 0)
            }
            assertNull(model.editorState.value.profile)
            assertNull(model.rulesEditorState.value.profile)
            assertNull(model.proxiesEditorState.value.profile)
            assertNull(model.proxyGroupsEditorState.value.profile)
            gate.complete(Unit); advanceUntilIdle()
            if (hasSaved) {
                assertEquals(ui, model.proxiesEditorState.value.uiDraft)
                val recovered = com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
                assertEquals(ui, recovered.proxies!!.uiDraft)
            } else assertEquals(profile.id, when (entry) {
                0 -> model.editorState.value.profile?.id
                1 -> model.rulesEditorState.value.profile?.id
                2 -> model.proxiesEditorState.value.profile?.id
                else -> model.proxyGroupsEditorState.value.profile?.id
            })
            model.closeEditor(); advanceUntilIdle(); owner.clear()
        }
    }

    @Test fun closingDuringFirstMetadataReadCannotResurrectRecoveredParentOrProxies() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(828, "metadata-restore-race")
        val content = "proxies: []\nrules: []"
        val store = FakeProfileStore(profile).apply { this.content = content }
        val snapshot = ProfileEditorDraftSnapshot(editor = ProfileEditorUiState(profile = profile, content = content),
            proxies = ProfileProxiesEditorUiState(profile = profile, sourceContent = content, proxies = "[]"))
        val drafts = PickerMemoryStore().apply { bytes = com.google.gson.Gson().toJson(snapshot).toByteArray() }
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>(); val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val wrapper = object : ProfileStore by store {
            override suspend fun extensionProfile(id: Long): Profile? { entered.complete(Unit); release.await(); return profile }
        }
        val model = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", model) }
        entered.await(); model.closeEditor(); release.complete(Unit); advanceUntilIdle()
        assertNull(model.editorState.value.profile)
        assertNull(model.proxiesEditorState.value.profile)
        assertNull(drafts.bytes); owner.clear()
    }

    @Test fun failedProxiesRecoveryNeverDeletesCiphertextAndOpeningAgainRetriesWholeDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (failStore in listOf(true, false)) {
            val profile = Profile.custom(826, "proxies-recovery-io")
            val original = "proxies: [{name: original, type: direct}]\nrules: [MATCH,DIRECT]"
            val store = FakeProfileStore(profile).apply { content = original }
            var failRead = false; var writes = 0; var bytes: ByteArray? = null
            val drafts = object : com.lonnnnnng.flclash.data.ProfileDraftStore {
                override suspend fun read(): ByteArray? { check(!failRead); return bytes?.copyOf() }
                override suspend fun write(content: ByteArray?) { writes++; bytes = content?.copyOf() }
            }
            fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle(); first.openProxiesEditor(profile); advanceUntilIdle()
            val ui = EditorSaveableState(mapOf("private" to listOf(EditorSavedValue.capture("private-proxies-io"))))
            first.updateProxiesUiDraft(profile.id, first.proxiesEditorState.value.session, ui); advanceUntilIdle(); owner.clear()
            val gson = com.google.gson.Gson()
            val saved = gson.fromJson(bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
            bytes = gson.toJson(saved.copy(proxies = saved.proxies!!.copy(isSaving = true, pendingContent = original + "\n# pending"))).toByteArray()
            val expected = bytes!!.copyOf(); writes = 0
            failRead = failStore
            store.beforeRead = { check(failStore) { "temporary source read failed" } }
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            try {
                advanceUntilIdle()
                assertEquals(0, writes); org.junit.Assert.assertArrayEquals(expected, bytes)
                second.openProxiesEditor(profile); second.saveProxies("- name: replaced\n  type: direct")
                second.closeProxiesEditor(); second.closeEditor(); advanceUntilIdle()
                assertEquals(0, writes); org.junit.Assert.assertArrayEquals(expected, bytes); assertNull(store.updatedContent)
                failRead = false; store.beforeRead = {}
                second.openEditor(profile); advanceUntilIdle()
                assertEquals(ui, second.proxiesEditorState.value.uiDraft)
                assertEquals(original, second.proxiesEditorState.value.sourceContent)
                assertTrue(second.proxiesEditorState.value.error.orEmpty().contains("保存已中断"))
                assertNull(store.updatedContent)
                second.closeEditor(); advanceUntilIdle(); assertNull(bytes)
            } finally { owner2.clear() }
        }
    }

    @Test fun proxiesColdRecoveryRetainsEncryptedUiAndOriginalConflictBaseline() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(823, "proxies-draft")
        val original = "proxies: [{name: original, type: direct}]\nrules: [MATCH,DIRECT]\n"
        val store = FakeProfileStore(profile).apply { content = original }
        val drafts = PickerMemoryStore()
        fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
        first.openEditor(profile); advanceUntilIdle(); first.openProxiesEditor(profile); advanceUntilIdle()
        val session = first.proxiesEditorState.value.session
        val ui = EditorSaveableState(mapOf("body" to listOf(EditorSavedValue.capture("node-private-input"))))
        first.updateProxiesUiDraft(profile.id, session, ui); advanceUntilIdle(); owner.clear()
        store.content = original + "# external change\n"
        val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
        advanceUntilIdle()
        assertEquals(ui, second.proxiesEditorState.value.uiDraft)
        assertEquals(original, second.proxiesEditorState.value.sourceContent)
        assertTrue(second.proxiesEditorState.value.error!!.contains("正文已变化"))
        second.saveProxies("- name: renamed\n  type: direct"); advanceUntilIdle()
        assertNull(store.updatedContent); assertNotNull(second.proxiesEditorState.value.error)
        assertFalse(second.proxiesEditorState.value.toString().contains("node-private-input"))
        second.closeProxiesEditor(); advanceUntilIdle()
        second.updateProxiesUiDraft(profile.id, session, ui); advanceUntilIdle()
        assertNull(com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java).proxies)
        second.closeEditor(); advanceUntilIdle(); owner2.clear(); assertNull(drafts.bytes)
    }

    @Test fun proxiesInterruptedSaveConfirmsCommittedBodyWithoutSubmittingTwice() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (committed in listOf(false, true)) {
            val profile = Profile.custom(824, "proxies-interrupted")
            val store = FakeProfileStore(profile).apply { content = "proxies: [{name: original, type: direct}]" }
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            var writes = 0
            val wrapper = object : ProfileStore by store {
                override suspend fun saveCustomDocument(id: Long, content: String, expectedContent: String, canWrite: () -> Boolean): Profile {
                    writes++; if (committed) store.updateLocalProfileContent(id, content)
                    entered.complete(Unit); release.await(); return profile
                }
            }
            val drafts = PickerMemoryStore()
            fun model() = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle(); first.openProxiesEditor(profile); advanceUntilIdle()
            first.saveProxies("- name: renamed\n  type: direct"); entered.await()
            first.saveProxies("- name: duplicate\n  type: direct")
            assertEquals(1, writes)
            assertNotNull(com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java).proxies?.pendingContent)
            owner.clear(); advanceUntilIdle()
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }; advanceUntilIdle()
            assertEquals(1, writes)
            if (committed) assertNull(second.proxiesEditorState.value.profile) else {
                assertEquals(profile.id, second.proxiesEditorState.value.profile?.id)
                assertTrue(second.proxiesEditorState.value.error!!.contains("保存已中断"))
            }
            second.closeEditor(); advanceUntilIdle(); owner2.clear()
        }
    }

    @Test fun proxiesClosedWhileReadingCannotReopenFromLateResult() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(825, "proxies-read")
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>(); val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = FakeProfileStore(profile).apply {
            content = "proxies: []"
            beforeRead = { entered.complete(Unit); release.await() }
        }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", model) }
        model.openProxiesEditor(profile); entered.await(); model.closeProxiesEditor(); advanceUntilIdle()
        release.complete(Unit); advanceUntilIdle(); assertNull(model.proxiesEditorState.value.profile); owner.clear()
    }

    @Test fun earlySystemResultWaitsForEncryptedDraftAndFreshLaunchReleasesOrphanPicker() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (remote in listOf(false, true)) {
            val profile = if (remote) Profile.remote(816, "https://example.test/pending", "pending")
                else Profile.custom(816, "pending").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
            val store = FakeProfileStore(profile).apply { content = "rules: []" }
            val drafts = PickerMemoryStore()
            fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
                fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader {
                    com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", "rules: []\n".toByteArray())
                }, overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle()
            val token = requireNotNull(if (remote) first.beginRemoteFilePick() else first.beginFileReplacementPick())
            assertTrue(first.persistPickerBeforeLaunch(token))
            val pendingBytes = drafts.bytes!!.copyOf(); owner.clear()
            val readGate = kotlinx.coroutines.CompletableDeferred<Unit>(); drafts.readGate = readGate
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            val reconcile = async { second.reconcileFilePicker(remote, token) }
            second.receiveFilePickerResult(remote, token + 1, "content://wrong")
            second.receiveFilePickerResult(remote, token, "content://fixture")
            second.receiveFilePickerResult(remote, token, "content://duplicate")
            assertNull(second.editorState.value.profile)
            readGate.complete(Unit); reconcile.await(); advanceUntilIdle()
            if (remote) second.remoteEditorState.first { it.replacement != null }
            else second.editorState.first { it.replacement != null }
            assertFalse(if (remote) second.remoteEditorState.value.isPickingFile else second.editorState.value.isPickingFile)
            assertTrue(store.fileUpdates.isEmpty()); assertEquals("rules: []", store.content)
            owner2.clear()
            drafts.bytes = pendingBytes; drafts.readGate = null
            val fresh = model(); val owner3 = androidx.lifecycle.ViewModelStore().apply { put("draft", fresh) }
            assertFalse(fresh.reconcileFilePicker(remote, null)); advanceUntilIdle()
            assertFalse(if (remote) fresh.remoteEditorState.value.isPickingFile else fresh.editorState.value.isPickingFile)
            assertNotNull(if (remote) fresh.beginRemoteFilePick() else fresh.beginFileReplacementPick())
            fresh.closeEditor(); advanceUntilIdle(); owner3.clear()
        }
    }

    @Test fun persistedFileAndRemotePickerTokensAcceptOnlyMatchingResultAfterViewModelRecovery() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (remote in listOf(false, true)) {
            val profile = if (remote) Profile.remote(815, "https://example.test/pending", "pending")
                else Profile.custom(815, "pending").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
            val store = FakeProfileStore(profile).apply { content = "rules: []" }
            var saved: ByteArray? = null
            val drafts = object : com.lonnnnnng.flclash.data.ProfileDraftStore {
                override suspend fun read() = saved
                override suspend fun write(content: ByteArray?) { saved = content?.copyOf() }
            }
            fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
                fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader {
                    com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", "rules: []\n".toByteArray())
                }, overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle()
            val token = requireNotNull(if (remote) first.beginRemoteFilePick() else first.beginFileReplacementPick())
            assertTrue(first.persistPickerBeforeLaunch(token)); owner.clear()
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            advanceUntilIdle()
            if (remote) second.acceptRemoteFilePick(token + 1, "content://fixture/pending")
            else second.acceptFileReplacement(token + 1, "content://fixture/pending")
            assertNull(if (remote) second.remoteEditorState.value.replacement else second.editorState.value.replacement)
            if (remote) second.acceptRemoteFilePick(token, "content://fixture/pending")
            else second.acceptFileReplacement(token, "content://fixture/pending")
            if (remote) second.remoteEditorState.first { it.replacement != null }
            else second.editorState.first { it.replacement != null }
            advanceUntilIdle()
            assertNotNull(if (remote) second.remoteEditorState.value.replacement else second.editorState.value.replacement)
            assertEquals("rules: []", store.content); assertTrue(store.fileUpdates.isEmpty())
            second.closeEditor()
            // 作者 long：文件预检经过真实IO线程，不能让虚拟时钟跳过5秒并抢先判超时；仍等页面关闭终态证明密文已清。
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                kotlinx.coroutines.withTimeout(5_000) {
                    if (remote) second.remoteEditorState.first { it.profile == null }
                    else second.editorState.first { it.profile == null }
                }
            }
            advanceUntilIdle(); owner2.clear(); assertNull(saved)
        }
    }

    @Test fun closingWaitsForDelayedDraftWriteAndClearsItEvenWhenViewModelIsDestroyed() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(813, "closing")
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var saved: ByteArray? = null
        var delayNext = false
        val drafts = object : com.lonnnnnng.flclash.data.ProfileDraftStore {
            override suspend fun read() = saved
            override suspend fun write(content: ByteArray?) {
                if (content != null && delayNext) {
                    delayNext = false; entered.complete(Unit)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                }
                saved = content?.copyOf()
            }
        }
        val model = newModel(FakeProfileStore(profile), FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", model) }
        model.openEditor(profile); advanceUntilIdle(); assertNotNull(saved)
        delayNext = true; model.changeCustomDraft("rules: ['MATCH,DIRECT']"); entered.await()
        model.closeEditor(); owner.clear(); release.complete(Unit); advanceUntilIdle()
        assertNull(saved); assertNull(model.editorState.value.profile)
    }

    @Test fun dnsRetryUsesOriginalBaselineAndKeepsOtherNewOverrides() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(814, "dns-retry")
        val store = FakeProfileStore(profile)
        var fail = true
        val wrapper = object : ProfileStore by store {
            override suspend fun patchProfileOverrides(id: Long, canWrite: () -> Boolean, patch: (ProfileProviderOverrides) -> ProfileProviderOverrides): Profile {
                if (fail) error("temporary write failure")
                return store.patchProfileOverrides(id, canWrite, patch)
            }
        }
        val model = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openDnsEditor(profile)
        val dns = ProfileDnsOverrides(enable = true)
        model.saveDns(dns); advanceUntilIdle()
        assertEquals(dns, model.dnsEditorState.value.overrides)
        val latest = ProfileProviderOverrides(dialers = mapOf("A" to "B"))
        store.updateProfileOverrides(profile.id, latest.toJsonString()); fail = false
        model.saveDns(dns); advanceUntilIdle()
        assertNull(model.dnsEditorState.value.profile)
        assertEquals(latest.copy(dns = dns), ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson))
        model.openDnsEditor(profile)
        model.saveDns(ProfileDnsOverrides(enable = false)); advanceUntilIdle()
        assertNotNull(model.dnsEditorState.value.profile)
        assertEquals(dns, ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dns)
    }

    @Test fun runningCustomSaveAppliesAndRejectsStaleParentWithoutDroppingDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(811, "运行编辑")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING)
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.openEditor(profile); advanceUntilIdle()
        assertEquals(profile.id, model.editorState.value.profile?.id)
        store.content = "rules: []\n"
        model.saveEditor("rules: ['MATCH,DIRECT']"); advanceUntilIdle()
        assertNotNull(model.editorState.value.error)
        assertEquals("rules: ['MATCH,DIRECT']", model.editorState.value.draftContent)
        assertTrue(runtime.applied.isEmpty())
        model.closeEditor(); model.openEditor(profile); advanceUntilIdle()
        model.saveEditor("rules: ['MATCH,DIRECT']"); advanceUntilIdle()
        assertEquals(listOf(profile.id), runtime.applied)
        assertNull(model.editorState.value.profile)
        assertEquals("rules: ['MATCH,DIRECT']", store.content)
    }

    @Test fun encryptedDraftSnapshotRestoresParentChildAndOriginalConflictBaselineInNewViewModel() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(812, "https://example.test/draft", "original")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        var saved: ByteArray? = null
        val drafts = object : com.lonnnnnng.flclash.data.ProfileDraftStore {
            override suspend fun read() = saved?.copyOf()
            override suspend fun write(content: ByteArray?) { saved = content?.copyOf() }
        }
        val first = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
        first.openEditor(profile); advanceUntilIdle()
        first.updateRemoteDraft(first.remoteEditorState.value.draft.copy(label = "pending-name", ageKey = "pending-key"))
        first.openProfileDocument(true); first.changeProfileDocument("rules: ['MATCH,DIRECT']"); advanceUntilIdle()
        assertNotNull(saved); owner.clear()
        store.content = "rules: []\n"
        val second = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
        advanceUntilIdle()
        assertEquals("pending-name", second.remoteEditorState.value.draft.label)
        assertEquals("pending-key", second.remoteEditorState.value.draft.ageKey)
        assertEquals("rules: []", second.remoteEditorState.value.document?.content)
        assertEquals("rules: ['MATCH,DIRECT']", second.documentEditorState.value.content)
        assertEquals(0, store.documentSaves)
        second.acceptProfileDocument(); advanceUntilIdle()
        second.saveRemoteEditor("pending-name", profile.url, false, 60); advanceUntilIdle()
        assertNotNull(second.remoteEditorState.value.error)
        assertEquals(0, store.documentSaves)
        second.closeEditor(); advanceUntilIdle(); assertNull(saved)
        owner2.clear()
    }

    @Test fun structuredEditorsRecoverInNewViewModelWithoutReplacingConflictBaseline() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (rules in listOf(true, false)) {
            val profile = Profile.custom(819, "structured-draft")
            val original = "proxy-groups:\n  - name: group\n    type: select\n    proxies: [DIRECT]\nrules:\n  - MATCH,DIRECT\n"
            val store = FakeProfileStore(profile).apply { content = original }
            val drafts = PickerMemoryStore()
            fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle()
            if (rules) first.openRulesEditor(profile) else first.openSingleProxyGroupEditor(profile, 0)
            advanceUntilIdle()
            val session = if (rules) first.rulesEditorState.value.session else first.proxyGroupsEditorState.value.session
            val ui = EditorSaveableState(mapOf("pending" to listOf(EditorSavedValue.capture("private-form"))))
            if (rules) first.updateRulesUiDraft(profile.id, session, ui) else first.updateGroupsUiDraft(profile.id, session, ui)
            advanceUntilIdle(); owner.clear()
            store.content = original + "# external change\n"
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            advanceUntilIdle()
            if (rules) {
                assertEquals(ui, second.rulesEditorState.value.uiDraft)
                assertEquals(original, second.rulesEditorState.value.sourceContent)
                assertTrue(second.rulesEditorState.value.error!!.contains("正文已变化"))
                second.saveRules("MATCH,DIRECT"); advanceUntilIdle()
            } else {
                assertEquals(ui, second.proxyGroupsEditorState.value.uiDraft)
                assertEquals(0, second.proxyGroupsEditorState.value.initialGroupIndex)
                assertEquals(original, second.proxyGroupsEditorState.value.sourceContent)
                assertTrue(second.proxyGroupsEditorState.value.error!!.contains("正文已变化"))
                second.saveProxyGroups("- name: group\n  type: select\n  proxies: [DIRECT]"); advanceUntilIdle()
            }
            assertNull(store.updatedContent)
            if (rules) second.closeRulesEditor() else second.closeProxyGroupsEditor()
            advanceUntilIdle()
            if (rules) second.updateRulesUiDraft(profile.id, session, ui) else second.updateGroupsUiDraft(profile.id, session, ui)
            advanceUntilIdle()
            val snapshot = com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
            assertNull(snapshot.rules); assertNull(snapshot.groups)
            assertNotNull(second.editorState.value.profile)
            second.closeEditor(); advanceUntilIdle(); owner2.clear(); assertNull(drafts.bytes)
        }
    }

    @Test fun interruptedStructuredSavesInspectCommittedBodyAndNeverResubmitAutomatically() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (rules in listOf(true, false)) for (committed in listOf(false, true)) {
            val profile = Profile.custom(820, "interrupted")
            val original = "proxy-groups:\n  - name: group\n    type: select\n    proxies: [DIRECT]\nrules:\n  - MATCH,DIRECT\n"
            val store = FakeProfileStore(profile).apply { content = original }
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            var writes = 0
            val wrapper = object : ProfileStore by store {
                override suspend fun saveCustomRules(id: Long, text: String, expectedContent: String, canWrite: () -> Boolean): Profile {
                    writes++
                    if (committed) store.updateLocalProfileContent(id, com.lonnnnnng.flclash.data.ProfileRulesEditor.replaceRules(expectedContent, text))
                    entered.complete(Unit); release.await()
                    return profile
                }
                override suspend fun saveCustomDocument(id: Long, content: String, expectedContent: String, canWrite: () -> Boolean): Profile {
                    writes++
                    if (committed) store.updateLocalProfileContent(id, content)
                    entered.complete(Unit); release.await()
                    return profile
                }
            }
            val drafts = PickerMemoryStore()
            fun model() = newModel(wrapper, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle()
            if (rules) { first.openRulesEditor(profile); advanceUntilIdle(); first.saveRules("DOMAIN,pending.test,DIRECT\nMATCH,DIRECT") }
            else { first.openProxyGroupsEditor(profile); advanceUntilIdle(); first.saveProxyGroups("- name: renamed\n  type: select\n  proxies: [DIRECT]") }
            entered.await(); advanceUntilIdle()
            val pending = com.google.gson.Gson().fromJson(drafts.bytes!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
            assertNotNull(if (rules) pending.rules?.pendingContent else pending.groups?.pendingContent)
            owner.clear()
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            advanceUntilIdle(); assertEquals(1, writes)
            if (rules) {
                assertFalse(second.rulesEditorState.value.isSaving)
                assertEquals(if (committed) store.content else original, second.rulesEditorState.value.sourceContent)
                if (committed) assertNull(second.rulesEditorState.value.error)
                else {
                    assertTrue(second.rulesEditorState.value.error!!.contains("保存已中断"))
                    assertEquals("DOMAIN,pending.test,DIRECT\nMATCH,DIRECT", second.rulesEditorState.value.interruptedRules)
                }
            } else if (committed) assertNull(second.proxyGroupsEditorState.value.profile)
            else {
                assertFalse(second.proxyGroupsEditorState.value.isSaving)
                assertTrue(second.proxyGroupsEditorState.value.error!!.contains("保存已中断"))
            }
            assertEquals(if (committed) store.content else original, second.editorState.value.content)
            second.closeEditor(); advanceUntilIdle(); owner2.clear()
        }
    }

    @Test fun childCloseWaitsForOldWriteAndRejectsLateSnapshotAfterViewModelDestruction() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(821, "child-close")
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var bytes: ByteArray? = null; var block = false
        val drafts = object : com.lonnnnnng.flclash.data.ProfileDraftStore {
            override suspend fun read() = bytes
            override suspend fun write(content: ByteArray?) {
                if (block && content != null) { block = false; entered.complete(Unit); kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() } }
                bytes = content?.copyOf()
            }
        }
        val model = newModel(FakeProfileStore(profile).apply { content = "rules: [MATCH,DIRECT]" }, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
        val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", model) }
        model.openRulesEditor(profile); advanceUntilIdle()
        val session = model.rulesEditorState.value.session
        block = true
        val ui = EditorSaveableState(mapOf("form" to listOf(EditorSavedValue.capture("pending"))))
        model.updateRulesUiDraft(profile.id, session, ui); entered.await()
        model.closeRulesEditor(); assertTrue(model.rulesEditorState.value.isSaving)
        model.updateRulesUiDraft(profile.id, session, ui)
        owner.clear(); release.complete(Unit); advanceUntilIdle()
        assertNull(bytes); assertNull(model.rulesEditorState.value.profile)
    }

    @Test fun closingParentDuringSuspendedChildRestoreCannotResurrectOldSession() = runTest {
        Dispatchers.setMain(mainDispatcher)
        for (rules in listOf(true, false)) {
            val profile = Profile.custom(822, "restore-race")
            val store = FakeProfileStore(profile).apply { content = "proxy-groups: []\nrules: []\n" }
            val drafts = PickerMemoryStore()
            fun model() = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher, draftStore = drafts)
            val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("draft", first) }
            first.openEditor(profile); advanceUntilIdle()
            if (rules) first.openRulesEditor(profile) else first.openProxyGroupsEditor(profile)
            advanceUntilIdle(); owner.clear()
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            store.beforeRead = { entered.complete(Unit); release.await() }
            val second = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("draft", second) }
            entered.await()
            second.closeEditor(); release.complete(Unit); advanceUntilIdle()
            assertNull(second.editorState.value.profile)
            assertNull(second.rulesEditorState.value.profile)
            assertNull(second.proxyGroupsEditorState.value.profile)
            assertNull(drafts.bytes); owner2.clear()
        }
    }

    @Test fun remoteDocumentStagesWithoutWritingAndFailedOuterSaveKeepsDraftForRetry() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(801, "https://example.test/source", "source")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
        model.openEditor(profile); advanceUntilIdle()
        model.openProfileDocument(true)
        model.changeProfileDocument("rules: [bad")
        model.acceptProfileDocument(); advanceUntilIdle()
        assertNotNull(model.documentEditorState.value.error)
        assertEquals(1, model.documentEditorState.value.errorLocation?.line)
        assertEquals("rules: [bad".length, model.documentEditorState.value.errorLocation?.offset)
        assertEquals(0, store.documentSaves)
        model.changeProfileDocument("rules: []\n")
        assertNull(model.documentEditorState.value.errorLocation)
        model.acceptProfileDocument(); advanceUntilIdle()
        assertNull(model.documentEditorState.value.ownerId)
        assertEquals("rules: []\n", model.remoteEditorState.value.draftContent)
        assertEquals("rules: []", store.content)
        store.documentSaveFailure = true
        model.saveRemoteEditor("source", profile.url, false, 60)
        advanceUntilIdle()
        assertNotNull(model.remoteEditorState.value.error)
        assertEquals("rules: []\n", model.remoteEditorState.value.draftContent)
        store.documentSaveFailure = false
        model.saveRemoteEditor("source", profile.url, false, 60)
        advanceUntilIdle()
        assertEquals(1, store.documentSaves)
        assertTrue(store.remoteUpdateCalls.isEmpty())
        assertNull(model.remoteEditorState.value.profile)
    }

    @Test fun invalidDocumentKeepsParentBytesAndLocatesParserErrorWithoutLeakingYaml() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(817, "正文定位")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openEditor(profile); advanceUntilIdle(); model.openProfileDocument(false)
        val invalid = "name: 😀\r\nrules: [ok, }"
        model.changeProfileDocument(invalid); model.acceptProfileDocument(); advanceUntilIdle()
        assertEquals(invalid, model.documentEditorState.value.content)
        assertEquals(invalid.indexOf('}'), model.documentEditorState.value.errorLocation?.offset)
        assertTrue(model.documentEditorState.value.error.orEmpty().contains("第 2 行，第 13 列"))
        assertFalse(model.documentEditorState.value.error.orEmpty().contains("😀"))
        assertEquals("rules: []", store.content)
        assertNull(model.editorState.value.draftContent)
        model.changeProfileDocument("rules: []\n"); model.acceptProfileDocument(); advanceUntilIdle()
        assertNull(model.documentEditorState.value.ownerId)
        assertEquals("rules: []\n", model.editorState.value.draftContent)
        assertEquals("rules: []", store.content)
    }

    @Test fun remotePickerRejectsOldCallbackAndOuterSaveRejectsChangedBaseWithoutLosingDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(802, "https://example.test/source", "source")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        var reads = 0
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { reads++; com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", "rules: []".toByteArray()) }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile); advanceUntilIdle()
        val old = checkNotNull(model.beginRemoteFilePick())
        model.closeEditor(); model.openEditor(profile); advanceUntilIdle()
        model.acceptRemoteFilePick(old, "content://old"); advanceUntilIdle()
        assertEquals(0, reads)
        val token = checkNotNull(model.beginRemoteFilePick())
        model.acceptRemoteFilePick(token, "content://current"); advanceUntilIdle()
        assertEquals(1, reads)
        assertNotNull(model.remoteEditorState.value.replacement)
        store.content = "rules: []\n"
        model.saveRemoteEditor("source", profile.url, false, 60); advanceUntilIdle()
        assertNotNull(model.remoteEditorState.value.error)
        assertNotNull(model.remoteEditorState.value.replacement)
        assertEquals(0, store.documentSaves)
        assertTrue(store.remoteUpdateCalls.isEmpty())
    }

    @Test fun remoteReplacementSemanticFailureKeepsCandidateForCorrection() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(803, "https://example.test/source", "source")
        val store = FakeProfileStore(profile).apply { content = "rules: []" }
        val candidate = "rules:\n  - MATCH,missing\n"
        val validator = object : com.lonnnnnng.flclash.data.ProfileConfigValidator {
            override suspend fun validate(bytes: ByteArray) = Unit
            override suspend fun validateFull(bytes: ByteArray): com.lonnnnnng.flclash.data.ProfileValidationSummary =
                throw com.lonnnnnng.flclash.validation.ConfigSemanticException("invalid_config", "rules", 0)
        }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), configValidator = validator,
            fileReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader {
                com.lonnnnnng.flclash.data.ProfileFileDocument("candidate.yaml", candidate.toByteArray())
            }, overviewDispatcher = mainDispatcher)
        model.openEditor(profile); advanceUntilIdle()
        model.acceptRemoteFilePick(checkNotNull(model.beginRemoteFilePick()), "content://candidate"); advanceUntilIdle()
        assertEquals(candidate, model.remoteEditorState.value.draftContent)
        assertEquals(false, model.remoteEditorState.value.replacement?.isValidYaml)
        assertTrue(model.remoteEditorState.value.error.orEmpty().contains("规则第 1 项"))
        assertEquals("rules: []", store.content)
        assertEquals(0, store.documentSaves)
        model.openProfileDocument(true)
        assertEquals(candidate, model.documentEditorState.value.content)
        model.acceptProfileDocument(); advanceUntilIdle()
        assertEquals(2, model.documentEditorState.value.errorLocation?.line)
        assertEquals(candidate, model.documentEditorState.value.content)
    }

    @Test fun batchSyncLimitsConcurrencyRejectsDuplicateOperationsAndRetriesOnlyFailures() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val rows = (1L..6L).map { Profile.remote(it, "https://example.test/$it", "订阅$it") }
        val store = FakeProfileStore(rows.first(), rows.drop(1))
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var active = 0; var peak = 0; var reject = true
        store.beforeRemoteUpdate = { id ->
            active++; peak = maxOf(peak, active)
            try { release.await(); if (id == 2L && reject) error("https://private.test/?token=secret") }
            finally { active-- }
        }
        val applied = mutableListOf<Long>()
        val runtime = object : CoreRuntimeStatusProvider {
            override val state = MutableStateFlow(CoreRuntimeStatus.RUNNING)
            override suspend fun applyCurrentProfile(id: Long) { applied += id }
        }
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.updateAll(); model.updateAll(); model.update(rows.first()); model.rename(rows.first(), "must not run")
        assertEquals(4, peak)
        assertEquals(4, store.remoteUpdateCalls.size)
        assertNull(store.updatedLabel)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(6, store.remoteUpdateCalls.size)
        assertEquals(1, model.syncState.value.items.count { it.status == ProfileSyncStatus.FAILED })
        assertEquals(listOf(1L), applied)
        assertFalse(model.syncState.value.toString().contains("secret"))
        reject = false; model.retrySyncFailures(); advanceUntilIdle()
        assertEquals(listOf(2L), store.remoteUpdateCalls.drop(6))
        assertTrue(model.syncState.value.items.all { it.status == ProfileSyncStatus.SUCCEEDED })
        assertFalse(model.syncState.value.running)
    }

    @Test fun retryingDownloadFailureKeepsEarlierSavedItemsAndUnresolvedApplyFailure() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val rows = (21L..22L).map { Profile.remote(it, "https://example.test/$it", "订阅$it") }
        val store = FakeProfileStore(rows.first(), rows.drop(1)).apply {
            beforeRemoteUpdate = { if (it == 22L) error("download failed") }
        }
        var applies = 0
        val runtime = object : CoreRuntimeStatusProvider {
            override val state = MutableStateFlow(CoreRuntimeStatus.RUNNING)
            override suspend fun applyCurrentProfile(id: Long) { applies++; error("apply failed") }
        }
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.updateAll(); advanceUntilIdle()
        assertNotNull(model.syncState.value.applyError)
        model.retrySyncFailures(); advanceUntilIdle()
        assertEquals(2, model.syncState.value.items.size)
        assertEquals(ProfileSyncStatus.SUCCEEDED, model.syncState.value.items.first().status)
        assertEquals(ProfileSyncStatus.FAILED, model.syncState.value.items.last().status)
        assertNotNull(model.syncState.value.applyError)
        assertEquals(2, applies)
        assertEquals(listOf(21L, 22L, 22L), store.remoteUpdateCalls)
    }

    @Test fun syncAppliesLatestSelectionAndApplyRetryDoesNotDownloadAgain() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val row = Profile.remote(10L, "https://example.test/sub", "订阅")
        val other = Profile.custom(11L, "引用当前订阅的配置")
        val store = FakeProfileStore(row, listOf(other))
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        store.beforeRemoteUpdate = { release.await() }
        var reject = true
        val applied = mutableListOf<Long>()
        val runtime = object : CoreRuntimeStatusProvider {
            override val state = MutableStateFlow(CoreRuntimeStatus.RUNNING)
            override suspend fun applyCurrentProfile(id: Long) { applied += id; if (reject) error("core secret") }
        }
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.update(row)
        store.currentProfileId.value = other.id
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(other.id), applied)
        assertEquals(listOf(row.id), store.remoteUpdateCalls)
        assertNotNull(model.syncState.value.applyError)
        assertFalse(model.syncState.value.applyError!!.contains("secret"))
        reject = false; model.retrySyncFailures(); advanceUntilIdle()
        assertEquals(listOf(other.id, other.id), applied)
        assertEquals(listOf(row.id), store.remoteUpdateCalls)
        assertNull(model.syncState.value.applyError)
        model.dismissSyncResult()
        assertTrue(model.syncState.value.items.isEmpty())
    }

    @Test fun remoteSettingsAndAutoUpdateRemainAvailableWhileRunningAndTransitionsRejectSync() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val row = Profile.remote(12L, "https://example.test/sub", "订阅")
        val store = FakeProfileStore(row)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING)
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.openEditor(row)
        assertEquals(row, model.remoteEditorState.value.profile)
        model.saveRemoteEditor("新名称", row.url, true, 60)
        model.saveRemoteEditor("不能重复保存", row.url, false, 10)
        advanceUntilIdle()
        assertEquals(1, store.remoteSettingsUpdates.size)
        assertTrue(store.remoteUpdateCalls.isEmpty())
        model.setAutoUpdate(row, true); advanceUntilIdle()
        assertEquals(listOf(row.id to true), store.autoUpdateChanges)
        runtime.set(CoreRuntimeStatus.STARTING)
        model.updateAll(); advanceUntilIdle()
        assertTrue(store.remoteUpdateCalls.isEmpty())
    }

    @Test fun `provider in place save claims once rereads and keeps latest unrelated metadata`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(501, "资源")
        val store = FakeProfileStore(profile).apply { content = "proxy-providers:\n  cloud:\n    type: file\n    path: cloud.yaml\n" }
        val validation = kotlinx.coroutines.CompletableDeferred<List<String>>()
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher,
            groupFilterValidator = com.lonnnnnng.flclash.data.ProxyGroupFilterValidator { validation.await() })
        model.openProvidersEditor(profile); advanceUntilIdle()
        val state = model.providersEditorState.value
        val options = mapOf("cloud" to ProxyProviderOverride(filter = "HK", interval = 300))
        model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders, options)
        model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders, options)
        assertTrue(model.providersEditorState.value.isSaving)
        store.updateProfileOverrides(profile.id, ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false), dialers = emptyMap()).toJsonString())
        validation.complete(listOf("", "")); advanceUntilIdle()
        val saved = model.providersEditorState.value
        assertEquals(1, store.providerSaveCalls)
        assertEquals(1L, saved.revision); assertEquals(profile.id, saved.profile?.id)
        assertEquals(options, saved.proxyProviderOverrides)
        assertEquals(false, ProfileProviderOverrides.fromJsonString(saved.profile?.overridesJson).dns.enable)
        assertEquals(emptyMap<String, String>(), ProfileProviderOverrides.fromJsonString(saved.profile?.overridesJson).dialers)
        assertEquals(store.content, saved.sourceContent)
    }

    @Test fun `closed provider sessions reject late loading and validation results`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(502, "旧资源")
        val second = Profile.custom(503, "新资源")
        val read = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = FakeProfileStore(first, listOf(second)).apply {
            content = "proxy-providers:\n  cloud:\n    type: file\n"
            beforeRead = { id -> if (id == first.id) read.await() }
        }
        val validation = kotlinx.coroutines.CompletableDeferred<List<String>>()
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher,
            groupFilterValidator = com.lonnnnnng.flclash.data.ProxyGroupFilterValidator { validation.await() })
        model.openProvidersEditor(first); model.closeProvidersEditor(); model.openProvidersEditor(second)
        read.complete(Unit); advanceUntilIdle()
        assertEquals(second.id, model.providersEditorState.value.profile?.id)
        val state = model.providersEditorState.value
        model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders, mapOf("cloud" to ProxyProviderOverride(filter = "HK")))
        model.closeProvidersEditor(); validation.complete(listOf("", "")); advanceUntilIdle()
        assertNull(model.providersEditorState.value.profile)
        assertEquals(0, store.providerSaveCalls)
        assertNull(store.updatedOverridesJson)
    }

    @Test fun `provider save failure retains session and retry succeeds without changing remote source`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(504, "https://example.com", "远程")
        val store = FakeProfileStore(profile).apply {
            content = "proxy-providers:\n  cloud:\n    type: file\n"
            beforeProviderSave = { error("持久化失败") }
        }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openProvidersEditor(profile); advanceUntilIdle()
        val state = model.providersEditorState.value
        val options = mapOf("cloud" to ProxyProviderOverride(timeout = 5000))
        model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders, options); advanceUntilIdle()
        assertEquals("持久化失败", model.providersEditorState.value.error)
        assertEquals(0L, model.providersEditorState.value.revision)
        assertEquals(profile.id, model.providersEditorState.value.profile?.id)
        store.beforeProviderSave = {}
        model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders, options); advanceUntilIdle()
        assertEquals(1L, model.providersEditorState.value.revision)
        assertEquals(options, model.providersEditorState.value.proxyProviderOverrides)
        assertNull(store.updatedContent)
    }

    @Test fun moreInPlaceSaveClaimsOnceRejectsTransitionAndRetriesWithoutClosing() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(511, "更多设置")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = FakeProfileStore(profile).apply { beforeMoreSave = { gate.await() } }
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
        model.openMoreEditor(profile)
        val ntp = com.lonnnnnng.flclash.service.ProfileNtpOverrides(enable = false)
        model.saveMoreEditor(ntp, com.lonnnnnng.flclash.service.ProfileSnifferOverrides())
        model.saveMoreEditor(ntp, com.lonnnnnng.flclash.service.ProfileSnifferOverrides())
        assertTrue(model.moreEditorState.value.isSaving)
        runtime.set(CoreRuntimeStatus.STARTING)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, store.moreSaveCalls); assertNull(store.updatedOverridesJson)
        assertEquals(0L, model.moreEditorState.value.revision); assertNotNull(model.moreEditorState.value.error)
        runtime.set(CoreRuntimeStatus.STOPPED)
        model.saveMoreEditor(ntp, com.lonnnnnng.flclash.service.ProfileSnifferOverrides()); advanceUntilIdle()
        assertEquals(2, store.moreSaveCalls); assertEquals(1L, model.moreEditorState.value.revision)
        assertEquals(profile.id, model.moreEditorState.value.profile?.id)
        assertEquals(false, model.moreEditorState.value.ntp.enable); assertNull(model.moreEditorState.value.error)
    }

    @Test fun replacedMoreSessionRejectsLateCommitAndPreservesNewPage() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(512, "旧分区"); val second = Profile.custom(513, "新分区")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = FakeProfileStore(first, listOf(second)).apply { beforeMoreSave = { gate.await() } }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openMoreEditor(first)
        model.saveMoreEditor(com.lonnnnnng.flclash.service.ProfileNtpOverrides(enable = true), com.lonnnnnng.flclash.service.ProfileSnifferOverrides())
        model.openMoreEditor(second); gate.complete(Unit); advanceUntilIdle()
        assertNull(store.updatedOverridesJson); assertEquals(second.id, model.moreEditorState.value.profile?.id)
        assertNull(model.moreEditorState.value.error); assertFalse(model.moreEditorState.value.isSaving)
    }

    @Test fun moreSaveKeepsOtherOverridesAndRetainsNewDraft() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(111, "草稿").copy(overridesJson = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = true)).toJsonString())
        val store = FakeProfileStore(profile)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
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
        val model = newModel(store, runtime, overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
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

    @Test fun `invalid selected file is rejected but stable running metadata can save`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(96, "file").copy(type = com.lonnnnnng.flclash.data.ProfileType.FILE)
        val store = FakeProfileStore(profile)
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = newModel(store, runtime,
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
        assertEquals(1, store.fileUpdates.size)
    }

    @Test fun `key only edit persists trimmed key and refreshes but unchanged key does not`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.remote(35, "https://same.example/subscription").copy(ageSecretKey = "old-fixture")
        val store = FakeProfileStore(profile)
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openEditor(profile)
        model.saveRemoteEditor("source", profile.url, true, 60, " new-fixture \n")
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.addRemote("https://example.test/encrypted", "import")
        advanceUntilIdle()
        model.cancelAgeImport()
        model.submitAgeKey("fixture")
        advanceUntilIdle()
        assertTrue(!model.ageKeyRequired.value)
        assertEquals(1, store.remoteAddCalls.size)
        val running = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
    fun tearDown() = kotlinx.coroutines.runBlocking(Dispatchers.Default) {
        modelJobs.forEach { it.cancel() }
        kotlinx.coroutines.withTimeout(5_000) {
            while (modelJobs.any { !it.isCompleted }) {
                modelSchedulers.forEach { it.runCurrent() }
                kotlinx.coroutines.delay(1)
            }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `delete is rejected during a runtime transition and repository is untouched`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(1L, "运行中配置")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STARTING),
            overviewDispatcher = mainDispatcher,
        )
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.delete(profile)

        assertEquals("核心正在启停，请稍后重试", error.await())
        advanceUntilIdle()
        assertEquals(0, store.deleteCalls)
    }

    @Test
    fun `delete delegates after core is stopped`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(2L, "已停止配置")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(
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
            val viewModel = newModel(store, FakeRuntimeStatusProvider(status), overviewDispatcher = mainDispatcher)
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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)

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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
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
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.openEditor(profile)
        viewModel.saveRemoteEditor("source", profile.url, false, 0L)
        advanceUntilIdle()

        assertEquals(Profile.DEFAULT_UPDATE_INTERVAL_MILLIS, store.remoteSettingsUpdates.single().intervalMillis)
        assertEquals(false, store.remoteSettingsUpdates.single().autoUpdate)
    }

    @Test
    fun `rename trims and persists profile label`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(4L, "旧名称")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(
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
    fun `rename remains available while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(5L, "运行中名称")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
        viewModel.rename(profile, "新名称")
        advanceUntilIdle()
        assertEquals("新名称", store.updatedLabel)
        assertEquals("新名称", store.profiles.value.single().label)
    }

    @Test
    fun `reorder delegates the requested order and keeps omitted profiles`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(20L, "第一项")
        val second = Profile.custom(21L, "第二项")
        val third = Profile.custom(22L, "第三项")
        val store = FakeProfileStore(first, listOf(second, third))
        val viewModel = newModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )

        viewModel.reorder(listOf(third.id, first.id))
        advanceUntilIdle()

        assertEquals(listOf(third.id, first.id, second.id), store.reorderedIds)
    }

    @Test
    fun `reorder remains available while core is running without changing current profile`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val first = Profile.custom(23L, "第一项")
        val second = Profile.custom(24L, "第二项")
        val store = FakeProfileStore(first, listOf(second))
        val viewModel = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.RUNNING), overviewDispatcher = mainDispatcher)
        viewModel.reorder(listOf(second.id, first.id))
        advanceUntilIdle()
        assertEquals(listOf(second.id, first.id), store.reorderedIds)
        assertEquals(first.id, store.currentProfileId.value)
    }

    @Test
    fun `script binding persists and can be cleared`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(25L, "脚本配置")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(
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
    fun `script binding is rejected during a runtime transition`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(26L, "运行中脚本配置")
        val store = FakeProfileStore(profile)
        val viewModel = newModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STARTING),
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
        val viewModel = newModel(
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
        assertEquals(profile.id, viewModel.rulesEditorState.value.profile?.id)
        assertEquals(store.updatedContent, viewModel.rulesEditorState.value.sourceContent)
    }

    @Test
    fun `custom rules editor keeps dialog open when a rule is invalid`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(4L, "非法规则")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = newModel(
            repository = store,
            runtimeManager = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED),
            overviewDispatcher = mainDispatcher,
        )
        viewModel.openRulesEditor(profile)
        advanceUntilIdle()
        viewModel.saveRules("SUB-RULE,local,DIRECT")
        advanceUntilIdle()

        assertEquals("第 1 行：不支持 SUB-RULE，请展开为具体规则", viewModel.rulesEditorState.value.error)
        assertNull(store.updatedContent)
        assertEquals(profile.id, viewModel.rulesEditorState.value.profile?.id)
    }

    @Test
    fun closingRulesWhileLoadingDoesNotReopenTheOldEditor() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(40L, "规则配置")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = object : ProfileStore by FakeProfileStore(profile) {
            override suspend fun readLocalProfileContent(id: Long): String {
                gate.await()
                return "rules: ['MATCH,DIRECT']"
            }
        }
        val model = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        model.openRulesEditor(profile)
        testScheduler.runCurrent()
        assertTrue(model.rulesEditorState.value.isLoading)
        model.closeRulesEditor()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(model.rulesEditorState.value.profile)
    }

    @Test
    fun rulesSaveRejectsRuntimeAndStaleSourceThenCanRetryAfterReopening() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val profile = Profile.custom(41L, "规则配置")
        val original = "rules: ['MATCH,DIRECT']"
        val store = FakeProfileStore(profile).apply { content = original }
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val model = newModel(store, runtime, overviewDispatcher = dispatcher)
        model.openRulesEditor(profile)
        advanceUntilIdle()
        model.saveRules("MATCH,REJECT")
        assertTrue(model.rulesEditorState.value.isSaving)
        runtime.set(CoreRuntimeStatus.STARTING)
        advanceUntilIdle()
        org.junit.Assert.assertNotNull(model.rulesEditorState.value.error)
        assertEquals(original, store.content)
        assertNull(store.updatedContent)

        runtime.set(CoreRuntimeStatus.STOPPED)
        store.content = "rules: ['DOMAIN,external.test,DIRECT']"
        model.saveRules("MATCH,REJECT")
        advanceUntilIdle()
        assertEquals("配置正文已变化，请重新打开规则页", model.rulesEditorState.value.error)
        assertNull(store.updatedContent)
        model.closeRulesEditor()
        model.openRulesEditor(profile)
        advanceUntilIdle()
        model.saveRules("MATCH,REJECT")
        advanceUntilIdle()
        assertNull(model.rulesEditorState.value.error)
        assertEquals(profile.id, model.rulesEditorState.value.profile?.id)
        assertEquals("rules:\n  - MATCH,REJECT\n", store.content)
    }

    @Test
    fun `custom proxies editor reads and saves only the proxies section`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(5L, "代理配置")
        val store = FakeProfileStore(profile).apply {
            content = "mixed-port: 7890\nproxies:\n  - name: old\n    type: vmess\nrules:\n  - MATCH,DIRECT\n"
        }
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
    fun `visual groups save refreshes parent and refuses a runtime transition`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(77L, "Visual")
        val store = FakeProfileStore(profile).apply {
            content = "proxy-groups:\n  - {name: Main, type: select, proxies: [DIRECT]}\nrules: ['MATCH,Main']\n"
        }
        val runtime = FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val viewModel = newModel(repository = store, runtimeManager = runtime, overviewDispatcher = mainDispatcher)
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
        runtime.set(CoreRuntimeStatus.STARTING)
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }
        val before = store.updatedContent
        viewModel.saveProxyGroupsDraft(com.lonnnnnng.flclash.data.ProxyGroupsSave(groups))
        advanceUntilIdle()
        assertEquals("核心正在启停，请稍后重试", error.await())
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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
        val viewModel = newModel(
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

    @Test fun `dialer selection projects globals saves in place and rejects group loops`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(73, "链式列表").copy(overridesJson = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false)).toJsonString())
        val store = FakeProfileStore(profile).apply {
            content = "proxy-groups: [{name: Main, type: select, proxies: [A, B]}]"
            globalNodes = listOf("A", "B").map { com.lonnnnnng.flclash.data.LocalProxy(json = """{"name":"$it","type":"direct"}""") }
        }
        val vm = newModel(store, FakeRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), overviewDispatcher = mainDispatcher)
        vm.openRulesEditor(profile); advanceUntilIdle()
        assertTrue(vm.rulesEditorState.value.catalog.targets.containsAll(listOf("A", "B", "Main")))
        assertEquals(store.content, vm.rulesEditorState.value.sourceContent)
        vm.closeRulesEditor()
        vm.openDialersEditor(profile); advanceUntilIdle()
        assertEquals(2, com.lonnnnnng.flclash.data.ProfileDialerCatalog(vm.dialersEditorState.value.sourceContent).nodes.size)
        vm.setDialerTarget("A", "B"); advanceUntilIdle()
        assertEquals(profile.id, vm.dialersEditorState.value.profile?.id)
        assertEquals(mapOf("A" to "B"), ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dialers)
        assertEquals(false, ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dns.enable)
        vm.setDialerTarget("A", "Main"); advanceUntilIdle()
        assertTrue(vm.dialersEditorState.value.error!!.contains("循环"))
        assertEquals(mapOf("A" to "B"), ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dialers)
        vm.setDialerTarget("A", null); advanceUntilIdle()
        assertTrue(ProfileProviderOverrides.fromJsonString(store.updatedOverridesJson).dialers!!.isEmpty())
        assertTrue(!store.content.contains("proxies: [{"))
    }

    @Test
    fun `custom providers editor keeps dialog open when a provider is malformed`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val profile = Profile.custom(15L, "非法 Provider")
        val store = FakeProfileStore(profile).apply { content = "mixed-port: 7890\n" }
        val viewModel = newModel(
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
    val applied = mutableListOf<Long>()
    override suspend fun applyCurrentProfile(id: Long) { applied += id }
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
    var beforeRead: suspend (Long) -> Unit = {}
    var beforeProviderSave: suspend () -> Unit = {}
    var providerSaveCalls = 0
    var beforeMoreSave: suspend () -> Unit = {}
    var moreSaveCalls = 0
    var globalNodes = emptyList<com.lonnnnnng.flclash.data.LocalProxy>()
    override suspend fun localProxies() = globalNodes
    var fileSaveFailure: Exception? = null
    val fileUpdates = mutableListOf<Pair<String, ByteArray?>>()
    var updatedContent: String? = null
    var updatedLabel: String? = null
    val remoteSettingsUpdates = mutableListOf<RemoteSettingsUpdate>()
    val remoteUpdateCalls = mutableListOf<Long>()
    var beforeRemoteUpdate: suspend (Long) -> Unit = {}
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

    override suspend fun readLocalProfileContent(id: Long): String { beforeRead(id); return content }

    override suspend fun saveCustomProviders(id: Long, sections: com.lonnnnnng.flclash.data.ProfileProvidersEditor.Sections, overrides: Map<String, ProxyProviderOverride>, expectedContent: String, expectedOverrides: Map<String, ProxyProviderOverride>?, canWrite: () -> Boolean): Profile {
        providerSaveCalls++; beforeProviderSave()
        return super<ProfileStore>.saveCustomProviders(id, sections, overrides, expectedContent, expectedOverrides, canWrite)
    }

    override suspend fun saveMoreOptions(id: Long, patch: com.lonnnnnng.flclash.data.ProfileMorePatch, canWrite: () -> Boolean): Profile {
        moreSaveCalls++; beforeMoreSave()
        return super<ProfileStore>.saveMoreOptions(id, patch, canWrite)
    }

    override suspend fun saveProviderOptions(id: Long, overrides: Map<String, ProxyProviderOverride>, expectedOverrides: Map<String, ProxyProviderOverride>?, canWrite: () -> Boolean): Profile {
        providerSaveCalls++; beforeProviderSave()
        return super<ProfileStore>.saveProviderOptions(id, overrides, expectedOverrides, canWrite)
    }

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

    var documentSaves = 0
    var documentSaveFailure = false
    override suspend fun saveProfileDocument(expected: com.lonnnnnng.flclash.data.ProfileDocumentSnapshot,
        settings: com.lonnnnnng.flclash.data.ProfileDocumentSettings, replacement: ByteArray?, canWrite: () -> Boolean): Profile {
        check(canWrite())
        check(content == expected.content) { "stale document" }
        if (expected.profile.type == com.lonnnnnng.flclash.data.ProfileType.FILE) return super<ProfileStore>.saveProfileDocument(expected, settings, replacement, canWrite)
        if (documentSaveFailure) error("save failed")
        documentSaves++
        if (replacement != null) content = replacement.toString(Charsets.UTF_8)
        return updateRemoteProfileSettings(expected.profile.id, settings.label, settings.url, settings.autoUpdate, settings.intervalMillis, settings.ageSecretKey)
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
        beforeRemoteUpdate(id)
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
