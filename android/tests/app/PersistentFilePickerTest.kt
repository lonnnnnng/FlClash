package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.ProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileFileDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentFilePickerTest {
    @Test fun waitingRequestRestoresAndConsumesCallbackOnlyOnce() = runTest {
        val disk = Disk(); val first = PersistentFilePicker(backgroundScope, disk); runCurrent()
        val token = first.begin("rules")!!
        val second = PersistentFilePicker(backgroundScope, disk); runCurrent()
        assertTrue(second.reconcile(token))
        var reads = 0
        repeat(2) { second.receive(token,"content://fixture") { reads++; ProfileFileDocument("rules", byteArrayOf(1,2,3)) } }
        runCurrent(); assertEquals(1,reads); assertEquals(FilePickerRequest.RESULT,second.state.value.request?.phase)
        var accepted = 0
        second.complete(token,{ accepted++ }); second.complete(token,{ accepted++ })
        assertEquals(1,accepted); assertNull(disk.bytes)
    }
    @Test fun cancelRejectsLateReadAndOldCallbackAfterNewLaunch() = runTest {
        val disk=Disk();val picker=PersistentFilePicker(backgroundScope,disk);runCurrent()
        val old=picker.begin("old")!!;val gate=CompletableDeferred<Unit>()
        picker.receive(old,"content://old") { gate.await(); ProfileFileDocument("old",byteArrayOf(1)) };runCurrent()
        assertTrue(picker.cancel(old));gate.complete(Unit);runCurrent();assertNull(picker.state.value.request)
        val next=picker.begin("next")!!
        picker.receive(old,"content://old") { error("旧结果不得读取") };runCurrent()
        assertEquals(next,picker.state.value.request?.token)
        picker.receive(next,null) { error("取消不得读取") };runCurrent();assertNull(picker.state.value.request)
    }
    @Test fun orphanAndInterruptedReadAreRecoverableWithoutReopeningUri() = runTest {
        val disk=Disk();val first=PersistentFilePicker(backgroundScope,disk);runCurrent()
        first.begin("rules");assertFalse(first.reconcile(null));assertNull(disk.bytes)
        val token=first.begin("rules")!!
        first.receive(token,"content://slow") { awaitCancellation() };runCurrent()
        val recovered=PersistentFilePicker(backgroundScope,disk);runCurrent()
        assertEquals(FilePickerRequest.FAILED,recovered.state.value.request?.phase)
        assertNotNull(recovered.state.value.error)
        assertNotNull(recovered.begin("rules"))
    }
    @Test fun resultSurvivesColdRestoreAndCompletedDeliveryNeverReplays() = runTest {
        val disk=Disk();val first=PersistentFilePicker(backgroundScope,disk);runCurrent()
        val token=first.begin("gallery")!!
        first.receive(token,"content://image") { ProfileFileDocument("image",byteArrayOf(10,20)) };runCurrent()
        val restored=PersistentFilePicker(backgroundScope,disk);runCurrent()
        assertArrayEquals(byteArrayOf(10,20),restored.state.value.request!!.bytes)
        var writes=0
        restored.complete(token,{ writes++ },{ error("回执清理失败") })
        assertEquals(FilePickerRequest.DONE,restored.state.value.request?.phase)
        val again=PersistentFilePicker(backgroundScope,disk);runCurrent()
        again.complete(token,{ writes++ });assertEquals(1,writes);assertNull(disk.bytes)
    }
    @Test fun corruptedRecordIsKeptAndLaunchIsRejected() = runTest {
        val disk=Disk().apply { bytes="broken".toByteArray() }
        val picker=PersistentFilePicker(backgroundScope,disk);runCurrent()
        assertTrue(picker.state.value.recoveryFailed);assertNull(picker.begin("rules"));assertEquals("broken",disk.bytes!!.decodeToString())
    }
    private class Disk:ProfileDraftStore {
        var bytes:ByteArray?=null
        override suspend fun read()=bytes
        override suspend fun write(content:ByteArray?) { bytes=content }
    }
}
