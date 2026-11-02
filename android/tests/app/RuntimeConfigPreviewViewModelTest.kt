package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.RuntimeConfigPreview
import com.lonnnnnng.flclash.service.RuntimeConfigPreviewProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeConfigPreviewViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `retry clears old content and parser errors never expose config text`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail = true
        val model = RuntimeConfigPreviewViewModel(object : RuntimeConfigPreviewProvider {
            override suspend fun previewConfig(profileId: Long): RuntimeConfigPreview {
                if (fail) error("password: fixture-secret")
                return RuntimeConfigPreview(profileId, "预览", "mode: rule", false)
            }
        })
        try {
            model.open(1)
            assertTrue(model.state.value.isLoading)
            runCurrent()
            assertEquals("读取运行配置失败，请重试", model.state.value.error)
            fail = false
            model.open(1)
            assertNull(model.state.value.error)
            runCurrent()
            assertEquals("mode: rule", model.state.value.preview?.content)
            model.open(1)
            assertNull(model.state.value.preview)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `close cancels waiting provider and cannot reopen from stale result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<RuntimeConfigPreview>()
        val model = RuntimeConfigPreviewViewModel(object : RuntimeConfigPreviewProvider {
            override suspend fun previewConfig(profileId: Long) = withContext(NonCancellable) { pending.await() }
        })
        try {
            model.open(1)
            runCurrent()
            model.close()
            pending.complete(RuntimeConfigPreview(1, "旧页面", "old", false))
            runCurrent()
            assertEquals(RuntimeConfigPreviewUiState(), model.state.value)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `switching preview ignores the first request and preserves the new profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<RuntimeConfigPreview>()
        val model = RuntimeConfigPreviewViewModel(object : RuntimeConfigPreviewProvider {
            override suspend fun previewConfig(profileId: Long): RuntimeConfigPreview =
                if (profileId == 1L) withContext(NonCancellable) { pending.await() }
                else RuntimeConfigPreview(profileId, "新配置", "new", true)
        })
        try {
            model.open(1)
            runCurrent()
            model.open(2)
            runCurrent()
            pending.complete(RuntimeConfigPreview(1, "旧配置", "old", false))
            runCurrent()
            assertEquals(2L, model.state.value.profileId)
            assertEquals("new", model.state.value.preview?.content)
        } finally { model.viewModelScope.cancel() }
    }
}
