package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStatisticsViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun singleProviderTimeoutClearsBusyAndAllowsRetryWithoutReopeningDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        try {
            model.setForeground(true); model.show(1); runCurrent()
            runtime.update = { withTimeout(100) { awaitCancellation() } }
            model.updateProvider("http"); runCurrent()
            assertEquals(setOf("http"), model.providers.value.updatingNames)
            advanceTimeBy(100); runCurrent()
            assertNull(model.providers.value.updatingName)
            assertTrue(model.providers.value.updatingNames.isEmpty())
            assertEquals("更新超时，请重试", model.providers.value.updateMessage)
            assertEquals(setOf("http"), model.providers.value.failedNames)
            runtime.update = {}; model.updateProvider("http"); runCurrent()
            assertEquals(2, runtime.updateCalls)
            assertEquals("Provider 已更新", model.providers.value.updateMessage)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun cancelledSingleProviderUpdateCannotLeaveStaleBusyOnAnotherDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val pending = CompletableDeferred<Unit>()
        try {
            model.setForeground(true); model.show(1); runCurrent()
            runtime.update = { pending.await(); throw CancellationException("fixture cancelled") }
            model.updateProvider("http"); runCurrent()
            model.show(2); runCurrent(); pending.complete(Unit); runCurrent()
            assertNull(model.providers.value.updatingName)
            assertTrue(model.providers.value.updatingNames.isEmpty())
            assertNull(model.providers.value.updateMessage)
            model.setForeground(false); model.setForeground(true); runCurrent()
            assertNull(model.providers.value.updatingName)
        } finally { pending.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun providersTimeoutSurvivesNonCancellableReadAndRevisionBurstUntilExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val gate = CompletableDeferred<Unit>()
        runtime.providerRead = { withContext(NonCancellable) { gate.await() }; providerSamples() }
        try {
            model.setForeground(true); model.show(1); runCurrent()
            repeat(8) {
                advanceTimeBy(1_000); runtime.profileStatisticsRevision.value++; runCurrent()
            }
            assertFalse(model.providers.value.isLoading)
            assertEquals("读取 Providers 超时，请重试", model.providers.value.error)
            val calls = runtime.providerCalls
            advanceTimeBy(15_000); runCurrent()
            assertEquals(calls, runtime.providerCalls)
            runtime.providerRead = { providerSamples() }
            model.retryProviders(); runCurrent()
            assertEquals(2, model.providers.value.providers?.size)
            assertNull(model.providers.value.error)
        } finally { gate.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun stalledOldProviderReadDoesNotBlockNewProfileAndCannotOverwriteIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val gate = CompletableDeferred<Unit>()
        runtime.providerRead = { withContext(NonCancellable) { gate.await() }; providerSamples() }
        try {
            model.setForeground(true); model.show(1); runCurrent()
            runtime.providerRead = { emptyList() }; model.show(2); runCurrent()
            assertEquals(2L, model.providers.value.profileId)
            assertEquals(emptyList<com.lonnnnnng.flclash.core.CoreExternalProvider>(), model.providers.value.providers)
            gate.complete(Unit); runCurrent()
            assertTrue(model.providers.value.providers!!.isEmpty())
        } finally { gate.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun statisticsTimeoutAlsoLeavesErrorVisibleUntilRetryForNonCancellableRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val gate = CompletableDeferred<Unit>()
        runtime.read = { id -> withContext(NonCancellable) { gate.await() }; sample(id) }
        try {
            model.setForeground(true); model.show(1); runCurrent()
            advanceTimeBy(8_000); runCurrent()
            assertFalse(model.state.value.isLoading)
            assertEquals("读取配置统计超时，请重试", model.state.value.error)
            advanceTimeBy(15_000); runCurrent(); assertEquals(1, runtime.calls)
            runtime.read = { sample(it) }; model.retry(); runCurrent()
            assertEquals(sample(1), model.state.value.counts)
        } finally { gate.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun coldPickerResultWaitsForRestoreKeepsSameDetailAndRejectsChangedInput() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (changed in listOf(false, true)) {
            val runtime = StatisticsFixture(); val drafts = PickerMemoryStore()
            val reader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader {
                com.lonnnnnng.flclash.data.ProfileFileDocument("1.yaml", "payload: []".toByteArray())
            }
            val first = ProfileStatisticsViewModel(runtime, reader, pickerStore = drafts)
            first.setForeground(true); first.show(1); runCurrent()
            first.openProvider("http", pickFile = true); runCurrent()
            val token = first.document.value!!.pickerToken!!
            assertNotNull(drafts.bytes); first.viewModelScope.cancel(); runCurrent()
            val gate = CompletableDeferred<Unit>(); drafts.readGate = gate
            if (changed) runtime.documentRead = { id, name -> ProfileProviderDocument(id,
                com.lonnnnnng.flclash.core.CoreProviderDocument(name, "text", "payload: []", "missing"), recoveryKey = "changed") }
            val restored = ProfileStatisticsViewModel(runtime, reader, pickerStore = drafts)
            try {
                restored.show(null); restored.show(1); restored.setForeground(true)
                restored.receiveProviderFile(token, "content://fixture")
                restored.receiveProviderFile(token, "content://duplicate")
                runCurrent(); assertEquals(0, runtime.saves)
                gate.complete(Unit); runCurrent()
                assertEquals(if (changed) 0 else 1, runtime.saves)
                assertNull(drafts.bytes)
                if (changed) assertNotNull(restored.document.value?.error) else assertNull(restored.document.value)
            } finally { restored.viewModelScope.cancel(); runCurrent() }
        }
    }

    @Test fun committedFailureOnlyRetriesReadbackAndDoesNotPromptForUnsavedChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (runtimePostCommitFailure in listOf(false, true)) {
            val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
            try {
                model.setForeground(true); model.show(1); runCurrent()
                model.openProvider("http", edit = true); runCurrent()
                model.changeProviderContent("payload: [new]")
                runtime.save = {
                    if (runtimePostCommitFailure) throw ProviderOperationException("已提交", committed = true)
                    runtime.documentRead = { _, _ -> error("fixture-secret") }
                }
                model.saveProvider(); runCurrent()
                assertTrue(model.document.value!!.committed); assertFalse(model.document.value!!.dirty)
                assertFalse(model.document.value!!.error.orEmpty().contains("fixture-secret"))
                model.saveProvider(); runCurrent(); assertEquals(1, runtime.saves)
                runtime.documentRead = { id, name -> ProfileProviderDocument(id,
                    com.lonnnnnng.flclash.core.CoreProviderDocument(name, "text", "payload: [new]", "new-digest"), recoveryKey = "fixture-input") }
                model.retryProviderReadback(); runCurrent()
                assertEquals(1, runtime.saves); assertNull(model.document.value)
            } finally { model.viewModelScope.cancel(); runCurrent() }
        }
    }

    @Test fun lateRestoreFailureCannotReplaceAnotherDocument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val drafts = PickerMemoryStore()
        val first = ProfileStatisticsViewModel(runtime, pickerStore = drafts)
        first.setForeground(true); first.show(1); runCurrent(); first.openProvider("http", pickFile = true); runCurrent()
        val token = first.document.value!!.pickerToken!!; first.viewModelScope.cancel(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        runtime.documentRead = { _, _ -> withContext(NonCancellable) { gate.await(); error("late fixture failure") } }
        val restored = ProfileStatisticsViewModel(runtime, pickerStore = drafts)
        try {
            restored.setForeground(true); runCurrent(); restored.receiveProviderFile(token, "content://fixture"); runCurrent()
            restored.show(2); runCurrent()
            runtime.documentRead = { id, name -> providerDocument(id, name) }
            restored.openProvider("file"); runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals("file", restored.document.value?.name); assertNull(restored.document.value?.error)
            assertEquals(0, runtime.saves)
        } finally { gate.complete(Unit); restored.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun batchUpdatesTrackFailuresAndRetryOnlyFailedNames() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        runtime.providerRead = { providerSamples() + com.lonnnnnng.flclash.core.CoreExternalProvider("other", "Rule", "HTTP", 4, null) }
        val batches = mutableListOf<List<String>>()
        runtime.batch = { names -> batches += names; names.map { com.lonnnnnng.flclash.core.CoreProviderUpdateResult(it, if (it == "other" && batches.size == 1) "request_timeout" else "") } }
        try {
            model.setForeground(true); model.show(1); runCurrent()
            model.updateAllProviders(); model.updateAllProviders(); runCurrent()
            assertEquals(listOf(listOf("http", "other")), batches)
            assertEquals(setOf("other"), model.providers.value.failedNames)
            model.updateAllProviders(onlyFailed = true); runCurrent()
            assertEquals(listOf("other"), batches.last()); assertTrue(model.providers.value.failedNames.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun providerDraftKeepsInputOnFailureAndRejectsOldPickerAndLateRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        try {
            model.setForeground(true); model.show(1); runCurrent()
            model.openProvider("http", edit = true); runCurrent()
            model.changeProviderContent("new-content"); runtime.save = { error("fixture-secret") }
            model.saveProvider(); model.saveProvider(); runCurrent()
            assertEquals(1, runtime.saves); assertEquals("new-content", model.document.value?.content)
            assertFalse(model.document.value?.error.orEmpty().contains("fixture-secret"))
            runtime.save = {}; model.saveProvider(); runCurrent(); assertNull(model.document.value)
            model.openProvider("http", pickFile = true); runCurrent()
            val token = model.document.value!!.pickerToken!!
            model.show(2); runCurrent(); model.receiveProviderFile(token, "content://stale"); runCurrent()
            assertEquals(2, runtime.saves)
            val pending = CompletableDeferred<ProfileProviderDocument>()
            runtime.documentRead = { id, name -> withContext(NonCancellable) { pending.await() } }
            model.openProvider("http"); runCurrent(); model.closeProvider()
            pending.complete(providerDocument(2, "http")); runCurrent(); assertNull(model.document.value)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun filePickerKeepsOriginalBindingAcrossBackgroundAndRejectsInvalidUtf8() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture()
        var bytes = byteArrayOf(0xff.toByte())
        val model = ProfileStatisticsViewModel(runtime, com.lonnnnnng.flclash.data.ProfileFileDocumentReader { com.lonnnnnng.flclash.data.ProfileFileDocument("rules", bytes) })
        try {
            model.setForeground(true); model.show(1); runCurrent(); model.openProvider("http", pickFile = true); runCurrent()
            val token = model.document.value!!.pickerToken!!
            model.setForeground(false); model.receiveProviderFile(token, "content://fixture"); runCurrent()
            assertEquals(0, runtime.saves); assertNotNull(model.document.value?.error)
            model.setForeground(true); runCurrent(); model.openProvider("http", pickFile = true); runCurrent()
            bytes = "payload: []".toByteArray()
            model.receiveProviderFile(model.document.value!!.pickerToken!!, "content://fixture"); runCurrent()
            assertEquals(1, runtime.saves); assertNull(model.document.value)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun providersLoadOnlyWhenVisibleAndUpdateSurvivesPollingButRejectsDuplicateAndFile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val pending = CompletableDeferred<Unit>()
        runtime.update = { pending.await() }
        try {
            model.show(1); runCurrent(); assertEquals(0, runtime.providerCalls)
            model.setForeground(true); runCurrent(); assertEquals(2, model.providers.value.providers?.size)
            model.updateProvider("file"); runCurrent(); assertEquals(0, runtime.updateCalls)
            model.updateProvider("http"); model.updateProvider("http"); runCurrent(); assertEquals(1, runtime.updateCalls)
            advanceTimeBy(5_000); runCurrent(); assertEquals("http", model.providers.value.updatingName)
            pending.complete(Unit); runCurrent()
            assertNull(model.providers.value.updatingName)
            assertEquals("Provider 已更新", model.providers.value.updateMessage)
            model.setForeground(false); runCurrent(); assertNull(model.providers.value.providers)
        } finally { pending.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun providersFailuresAreSafeRetryableAndOldUpdateCannotAffectAnotherProfile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val pending = CompletableDeferred<Unit>()
        try {
            runtime.providerRead = { error("fixture-secret") }
            model.setForeground(true); model.show(1); runCurrent()
            assertEquals("读取 Providers 失败，请重试", model.providers.value.error)
            runtime.providerRead = { providerSamples() }; model.retryProviders(); runCurrent()
            runtime.update = { error("fixture-secret") }; model.updateProvider("http"); runCurrent()
            assertEquals("更新失败，请稍后重试", model.providers.value.updateMessage)
            runtime.update = { withContext(NonCancellable) { pending.await() } }
            model.updateProvider("http"); runCurrent(); model.show(2); runCurrent()
            pending.complete(Unit); runCurrent()
            assertEquals(2L, model.providers.value.profileId)
            assertNull(model.providers.value.updateMessage); assertNull(model.providers.value.updatingName)
            model.show(null); runCurrent(); assertNull(model.providers.value.providers)
        } finally { pending.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun onlyForegroundOpenDetailLoadsAndBackgroundStopsPolling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        try {
            model.show(1); runCurrent(); advanceTimeBy(10_000); runCurrent(); assertEquals(0, runtime.calls)
            model.setForeground(true); runCurrent(); assertEquals(1, runtime.calls)
            assertEquals(7L, model.state.value.counts?.proxies)
            advanceTimeBy(5_000); runCurrent(); assertEquals(2, runtime.calls)
            model.setForeground(false); runCurrent(); advanceTimeBy(10_000); runCurrent(); assertEquals(2, runtime.calls)
            assertNull(model.state.value.counts)
            model.show(null); model.setForeground(true); runCurrent(); assertEquals(2, runtime.calls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun configChangeClearsCountsAndFailureHasSafeRetryWithoutOldNumbers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        try {
            model.setForeground(true); model.show(1); runCurrent()
            runtime.read = { error("password: fixture-secret") }
            runtime.profileStatisticsRevision.value++; runCurrent()
            assertNull(model.state.value.counts)
            assertEquals("读取配置统计失败，请重试", model.state.value.error)
            runtime.read = { sample(it) }; model.retry(); runCurrent()
            assertEquals(sample(1), model.state.value.counts); assertNull(model.state.value.error)
            runtime.state.value = CoreRuntimeStatus.STARTING; runCurrent(); assertNull(model.state.value.counts)
            val calls = runtime.calls; advanceTimeBy(10_000); runCurrent(); assertEquals(calls, runtime.calls)
            runtime.state.value = CoreRuntimeStatus.RUNNING; runCurrent(); assertEquals(calls + 1, runtime.calls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun oldNonCancellableResultCannotOverwriteNewProfileOrReopenClosedDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        val pending = CompletableDeferred<ProfileStatistics>()
        runtime.read = { id -> if (id == 1L) withContext(NonCancellable) { pending.await() } else sample(id) }
        try {
            model.setForeground(true); model.show(1); runCurrent()
            model.show(2); runCurrent(); pending.complete(sample(1)); runCurrent()
            assertEquals(sample(2), model.state.value.counts)
            model.show(null); runCurrent(); assertNull(model.state.value.profileId); assertNull(model.state.value.counts)
        } finally { pending.complete(sample(1)); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun timeoutAndWrongProfileAreFailuresAndExplicitRetryRecovers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = StatisticsFixture(); val model = ProfileStatisticsViewModel(runtime)
        runtime.read = { delay(100_000); sample(it) }
        try {
            model.setForeground(true); model.show(1); runCurrent(); advanceTimeBy(8_000); runCurrent()
            assertEquals("读取配置统计超时，请重试", model.state.value.error)
            runtime.read = { sample(2) }; model.retry(); runCurrent(); assertNull(model.state.value.counts)
            runtime.read = { sample(it) }; model.retry(); runCurrent(); assertEquals(sample(1), model.state.value.counts)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }
}

private fun sample(id: Long) = ProfileStatistics(id, 2, 7, 3, 1)
private class StatisticsFixture : ProfileStatisticsRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    override val profileStatisticsRevision = MutableStateFlow(0L)
    var providerCalls = 0
    var updateCalls = 0
    var providerRead: suspend () -> List<com.lonnnnnng.flclash.core.CoreExternalProvider> = { providerSamples() }
    var update: suspend () -> Unit = {}
    var batch: suspend (List<String>) -> List<com.lonnnnnng.flclash.core.CoreProviderUpdateResult> = { names -> names.map { com.lonnnnnng.flclash.core.CoreProviderUpdateResult(it, "") } }
    override suspend fun updateProfileProviders(profileId: Long, names: List<String>) = batch(names)
    private var content = "payload: []"
    var documentRead: suspend (Long, String) -> ProfileProviderDocument = { id, name -> ProfileProviderDocument(id,
        com.lonnnnnng.flclash.core.CoreProviderDocument(name, "text", content, "missing"), recoveryKey = "fixture-input") }
    var save: suspend () -> Unit = {}
    var saves = 0
    override suspend fun readProfileProvider(profileId: Long, name: String) = documentRead(profileId, name)
    override suspend fun saveProfileProvider(document: ProfileProviderDocument, content: String) { saves++; save(); this.content = content }
    override suspend fun profileProviders(profileId: Long): List<com.lonnnnnng.flclash.core.CoreExternalProvider> { providerCalls++; return providerRead() }
    override suspend fun updateProfileProvider(profileId: Long, name: String) { updateCalls++; update() }
    var calls = 0
    var read: suspend (Long) -> ProfileStatistics = { sample(it) }
    override suspend fun profileStatistics(profileId: Long): ProfileStatistics { calls++; return read(profileId) }
}

private fun providerDocument(id: Long, name: String) = ProfileProviderDocument(id,
    com.lonnnnnng.flclash.core.CoreProviderDocument(name, "text", "payload: []", "missing"), recoveryKey = "fixture-input")

private fun providerSamples() = listOf(
    com.lonnnnnng.flclash.core.CoreExternalProvider("http", "Proxy", "HTTP", 3, null),
    com.lonnnnnng.flclash.core.CoreExternalProvider("file", "Rule", "File", 5, null))
