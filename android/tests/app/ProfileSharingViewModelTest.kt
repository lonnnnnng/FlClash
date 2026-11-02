package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSharingViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `copy uses original noncurrent subscription and sensitive clipboard without selecting it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            model.copyLink(2)
            assertEquals(fixture.url, fixture.copied)
            assertTrue(fixture.sensitive)
            assertEquals(1L, fixture.store.currentProfileId.value)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `copy rejects file custom and missing profiles and hides clipboard failures`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            for ((id, message) in listOf(1L to "此配置没有订阅链接", 3L to "此配置没有订阅链接", 9L to "配置不存在")) {
                val result = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
                model.copyLink(id)
                assertEquals(message, result.await())
            }
            fixture.failClipboard = true
            val result = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
            model.copyLink(2)
            assertEquals("复制订阅链接失败，请重试", result.await())
            assertNull(fixture.copied)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `QR roundtrip preserves Unicode query parameters and original URL`() = runTest {
        val url = "https://example.test/sub?token=fixture%2Bvalue&name=家庭网络#原链接"
        val matrix = SubscriptionQrEncoder().encode(url)
        val scale = 8
        val width = matrix.width * scale
        val pixels = IntArray(width * width) { index ->
            if (matrix[index % width / scale, index / width / scale]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, width, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }

    @Test fun `closing QR ignores late encoder result and retry clears previous content`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<com.google.zxing.common.BitMatrix>()
        val fixture = SharingFixture()
        val model = fixture.model(ProfileQrEncoder { withContext(NonCancellable) { pending.await() } })
        try {
            model.showQr(2)
            runCurrent()
            model.closeQr()
            pending.complete(com.google.zxing.common.BitMatrix(21))
            runCurrent()
            assertNull(model.state.value.qr)
            model.showQr(2)
            assertNull(model.state.value.qr?.matrix)
            runCurrent()
            assertNotNull(model.state.value.qr?.matrix)
            assertEquals(1L, fixture.store.currentProfileId.value)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `long QR URL fails with safe recoverable message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        fixture.store.profiles.value = listOf(Profile.remote(2, "https://example.test/" + "x".repeat(10_000), "长订阅"))
        val model = fixture.model()
        try {
            model.showQr(2)
            advanceUntilIdle()
            // 作者 long：Default 编码线程独立于测试调度器，等待错误状态后再断言，不能将尚未完成误判为成功。
            val result = model.state.first { it.qr?.error != null }
            assertNull(result.qr?.matrix)
            assertEquals("无法生成二维码，链接可能过长，请复制订阅链接", result.qr?.error)
            model.closeQr()
            assertNull(model.state.value.qr)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `export freezes requested profile once and survives selection changes before saving`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            model.beginExport(2)
            model.beginExport(1)
            runCurrent()
            assertEquals(listOf(2L), fixture.reads)
            val request = model.state.value.exportRequest!!
            assertNull(model.claimExportRequest(request.token + 1))
            assertEquals("订阅.yaml", model.claimExportRequest(request.token))
            assertNull(model.claimExportRequest(request.token))
            fixture.store.currentProfileId.value = 3
            fixture.exportBytes = byteArrayOf(99)
            model.closeQr()
            assertTrue(model.state.value.exporting)
            model.finishExport("content://fixture/document")
            model.finishExport("content://fixture/duplicate")
            runCurrent()
            assertEquals(1, fixture.writes.size)
            assertArrayEquals(byteArrayOf(0, 1, 2, -1), fixture.writes.single().second)
            assertFalse(model.state.value.exporting)
            assertEquals(3L, fixture.store.currentProfileId.value)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `cancelled export writes nothing and later request can be started`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            model.beginExport(2)
            runCurrent()
            model.claimExportRequest(model.state.value.exportRequest!!.token)
            model.finishExport(null)
            runCurrent()
            assertTrue(fixture.writes.isEmpty())
            assertFalse(model.state.value.exporting)
            model.beginExport(1)
            runCurrent()
            assertEquals(listOf(2L, 1L), fixture.reads)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `export rejects custom and missing cache with controlled feedback and allows retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            for ((id, expected) in listOf(3L to "自定义配置不支持文件导出", 9L to "配置不存在")) {
                val message = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
                model.beginExport(id)
                runCurrent()
                assertEquals(expected, message.await())
            }
            assertTrue(fixture.reads.isEmpty())
            fixture.readFailure = "配置文件不存在"
            val message = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
            model.beginExport(2)
            runCurrent()
            assertEquals("配置文件不存在", message.await())
            assertNull(model.state.value.exportRequest)
            assertFalse(model.state.value.exporting)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `writer and reader exception details do not leak and export resets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = SharingFixture()
        val model = fixture.model()
        try {
            fixture.readFailure = fixture.url
            val readMessage = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
            model.beginExport(2)
            runCurrent()
            assertEquals("读取配置文件失败，请重试", readMessage.await())
            fixture.readFailure = null
            fixture.failWriter = true
            model.beginExport(2)
            runCurrent()
            model.claimExportRequest(model.state.value.exportRequest!!.token)
            val writeMessage = async(start = CoroutineStart.UNDISPATCHED) { model.messages.first() }
            model.finishExport("content://fixture/document")
            runCurrent()
            assertEquals("保存配置文件失败，请重试", writeMessage.await())
            assertFalse(model.state.value.exporting)
            assertNull(model.state.value.exportRequest)
        } finally { model.viewModelScope.cancel() }
    }
}

private class SharingFixture {
    val url = "https://example.test/sub?token=fixture%2Bvalue&x=1"
    val store = SharingTestStore(listOf(Profile.custom(1, "文件").copy(type = ProfileType.FILE), Profile.remote(2, url, "订阅"), Profile.custom(3, "自定义")))
    var copied: String? = null
    var sensitive = false
    var failClipboard = false
    var failWriter = false
    var readFailure: String? = null
    var exportBytes = byteArrayOf(0, 1, 2, -1)
    val reads = mutableListOf<Long>()
    val writes = mutableListOf<Pair<String, ByteArray>>()
    fun model(encoder: ProfileQrEncoder = SubscriptionQrEncoder()) = ProfileSharingViewModel(store,
        ProfileExportReader { id ->
            reads += id
            readFailure?.let { error(it) }
            ProfileExport("订阅.yaml", exportBytes)
        },
        ProfileDocumentWriter { destination, bytes ->
            if (failWriter) error(url)
            writes += destination to bytes
        },
        object : ClipboardGateway {
            override fun readText(): String? = null
            override fun writeText(text: String) { error("必须标记敏感内容") }
            override fun writeSensitiveText(text: String) {
                if (failClipboard) error(url)
                copied = text
                sensitive = true
            }
        }, encoder)
}

private class SharingTestStore(initial: List<Profile>) : ProfileStore {
    override val profiles = MutableStateFlow(initial)
    override val currentProfileId = MutableStateFlow<Long?>(initial.first().id)
    override suspend fun selectProfile(id: Long?) = error("分享不能切换配置")
    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("不能通过编辑入口读取订阅")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")
    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("unused")
    override suspend fun updateRemoteProfile(id: Long): Profile = error("分享不能下载订阅")
    override suspend fun deleteProfile(id: Long) = error("unused")
    override suspend fun reorder(ids: List<Long>) = error("unused")
}
