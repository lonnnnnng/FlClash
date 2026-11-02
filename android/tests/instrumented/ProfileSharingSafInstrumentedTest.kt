package com.lonnnnnng.flclash.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.security.MessageDigest
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProfileSharingSafInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun realSystemPickerCancelsWithoutWritingThenSavesExactAuthorizedFileSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("仅显式 1.yaml 真机验收运行", args.getString("kclashProfileSharingSaf") == "true")
        assertPortraitDefaultFontConfiguration()
        val bytes = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-profile-sharing-1.yaml").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        assertTrue(bytes.isNotEmpty())
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("输入必须与工作区 1.yaml 一致", args.getString("kclashProfileSharingSha256"), hash)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "profile-sharing-saf-${System.nanoTime()}").apply { mkdirs() }
        val isolatedContext = object : android.content.ContextWrapper(context) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val selected = Profile.custom(721, "当前文件").copy(type = ProfileType.FILE)
        val profile = Profile.remote(722, "https://example.test/not-downloaded", "kclash-profile-sharing-${System.nanoTime()}")
        runBlocking {
            database.profiles().insert(selected.toEntity())
            database.profiles().insert(profile.toEntity())
        }
        val store = ProfileRepository(isolatedContext, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("导出不能下载订阅")
        })
        waitFor(10_000) { store.profiles.value.size == 2 }
        runBlocking { store.selectProfile(selected.id) }
        val cached = store.profileFile(profile.id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val metadata = cached.length() to cached.lastModified()
        val writtenUri = AtomicReference<String?>()
        val writeCompleted = AtomicReference(false)
        val feedback = AtomicReference<String?>()
        lateinit var model: ProfileSharingViewModel
        composeRule.runOnUiThread {
            model = ProfileSharingViewModel(store, store,
                ProfileDocumentWriter { destination, snapshot ->
                    // 作者 long：只记录本次系统选择器新建的 URI，写入后回读原字节，finally 仅删除这个验收文件。
                    writtenUri.set(destination)
                    AndroidProfileDocumentWriter(context).write(destination, snapshot)
                    writeCompleted.set(true)
                },
                object : ClipboardGateway {
                    override fun readText(): String? = null
                    override fun writeText(text: String) = error("文件导出不访问剪贴板")
                }, SubscriptionQrEncoder())
        }
        composeRule.setContent {
            KClashTheme {
                val state by model.state.collectAsState()
                ProfileCard(profile, selected = false, canMutate = false, onSelect = {}, onEdit = {},
                    onUpdate = {}, onDelete = {}, onExport = { model.beginExport(profile.id) }, exportBusy = state.exporting)
                ProfileSharingHost(model) { feedback.set(it) }
            }
        }
        try {
            launchExport(profile.id)
            // 作者 long：文件读取在 IO 线程，Compose 测试时钟需持续推进，不能用阻塞轮询饿死保存窗口的 LaunchedEffect。
            composeRule.waitUntil(10_000) { model.state.value.exportRequest?.launched == true || !model.state.value.exporting }
            assertTrue("保存窗口未发起：${feedback.get()}", model.state.value.exportRequest?.launched == true)
            waitForPicker()
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            composeRule.waitUntil(10_000) { !model.state.value.exporting }
            assertNull("取消不能写出文件", writtenUri.get())
            launchExport(profile.id)
            composeRule.waitUntil(10_000) { model.state.value.exportRequest?.launched == true || !model.state.value.exporting }
            assertTrue("保存窗口未发起：${feedback.get()}", model.state.value.exportRequest?.launched == true)
            waitForPicker()
            // 作者 long：通过系统保存按钮完成 SAF 回调，不直接调用 finishExport 冒充文件选择流程。
            waitFor(10_000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@waitFor false
                val save = sequenceOf("com.google.android.documentsui", "com.android.documentsui")
                    .flatMap { root.findAccessibilityNodeInfosByViewId("$it:id/action_menu_save").asSequence() }
                    .firstOrNull { it.isEnabled && it.isVisibleToUser }
                    ?: sequenceOf("保存", "Save", "SAVE").flatMap { root.findAccessibilityNodeInfosByText(it).asSequence() }
                        .firstOrNull { it.isEnabled && it.isClickable && it.isVisibleToUser }
                save?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            composeRule.waitUntil(15_000) { writeCompleted.get() && !model.state.value.exporting }
            val uri = android.net.Uri.parse(checkNotNull(writtenUri.get()))
            val exported = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertTrue("系统导出文件必须与 1.yaml 原字节一致", bytes.contentEquals(exported))
            assertEquals(selected.id, store.currentProfileId.value)
            assertEquals(metadata, cached.length() to cached.lastModified())
            assertEquals(2, runBlocking { database.profiles().getAll().size })
        } finally {
            writtenUri.get()?.let { DocumentsContract.deleteDocument(context.contentResolver, android.net.Uri.parse(it)) }
            composeRule.runOnUiThread { model.viewModelScope.cancel() }
            database.close()
            // 作者 long：目录由本用例独占创建，清理仅针对临时 Room 夹具缓存，不触及正式 Profile 目录。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("profile-sharing-saf-"))
            directory.deleteRecursively()
        }
    }

    private fun launchExport(id: Long) {
        composeRule.onNodeWithTag("profile_actions_$id").performClick()
        composeRule.onNodeWithTag("profile_more_$id").performClick()
        composeRule.onNodeWithTag("profile_export_$id").performClick()
    }

    private fun waitForPicker() = waitFor(10_000) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()
            ?.endsWith("documentsui") == true
    }

    private fun waitFor(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("系统保存窗口未在规定时间内完成")
    }
}
