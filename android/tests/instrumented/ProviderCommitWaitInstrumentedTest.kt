package com.lonnnnnng.flclash

import com.lonnnnnng.flclash.ui.ProviderDocumentUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：保存已报错必须立即终止探针等待，成功提交则不依赖文档关闭，不添加固定延迟。 */
class ProviderCommitWaitInstrumentedTest {
    @Test fun rejectedSaveTerminatesWithoutWaitingForCommitTimeout() = runBlocking {
        val document = MutableStateFlow<ProviderDocumentUiState?>(ProviderDocumentUiState("private", saving = true))
        val held = CompletableDeferred<Unit>()
        val result = async { runCatching { withTimeout(2_000) { awaitProviderCommitOrError({ held.await() }, document) } } }
        yield()
        document.value = ProviderDocumentUiState("private", error = "private-error")
        val failure = result.await().exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("Provider save rejected", failure?.message)
        assertFalse(held.isCompleted)
    }

    @Test fun reachedCommitCompletesWhileDocumentRemainsOpen() = runBlocking {
        val document = MutableStateFlow<ProviderDocumentUiState?>(ProviderDocumentUiState("private", saving = true))
        val result = async { withTimeout(2_000) { awaitProviderCommitOrError({}, document) } }
        result.await()
        assertTrue(document.value!!.saving)
        assertNull(document.value!!.error)
    }
}
