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
    override suspend fun profileProviders(profileId: Long): List<com.lonnnnnng.flclash.core.CoreExternalProvider> { providerCalls++; return providerRead() }
    override suspend fun updateProfileProvider(profileId: Long, name: String) { updateCalls++; update() }
    var calls = 0
    var read: suspend (Long) -> ProfileStatistics = { sample(it) }
    override suspend fun profileStatistics(profileId: Long): ProfileStatistics { calls++; return read(profileId) }
}

private fun providerSamples() = listOf(
    com.lonnnnnng.flclash.core.CoreExternalProvider("http", "Proxy", "HTTP", 3, null),
    com.lonnnnnng.flclash.core.CoreExternalProvider("file", "Rule", "File", 5, null))
