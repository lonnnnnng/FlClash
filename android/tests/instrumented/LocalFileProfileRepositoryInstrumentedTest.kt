package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocalFileProfileRepositoryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun keyOnlyEditKeepsExactCacheTimestampSelectionAndProfileCount() {
        fixture().use { f -> runBlocking {
            val before = f.row()
            val modified = f.targetFile.lastModified()
            val updated = f.repository.updateFileProfile(f.target.id, " new-fixture \n")
            assertTrue(updated.ageSecretKey == "new-fixture")
            assertEquals(before.lastUpdateAt, f.row().lastUpdateAt)
            assertEquals(modified, f.targetFile.lastModified())
            assertTrue(f.bytes.contentEquals(f.targetFile.readBytes()))
            assertEquals(f.selected.id, f.repository.currentProfileId.value)
            assertEquals(2, f.database.profiles().getAll().size)
            assertTrue(runCatching { f.repository.updateFileProfile(f.selected.id, "fixture") }.isFailure)
        } }
    }

    @Test fun failedDecodeValidationDatabaseAndLateChangesKeepPreviousBodyAndMetadata() {
        fixture().use { f -> runBlocking {
            val before = f.row()
            val modified = f.targetFile.lastModified()
            suspend fun rejected() {
                assertTrue(runCatching { f.repository.updateFileProfile(f.target.id, "new-fixture", f.bytes) }.isFailure)
                assertEquals(before, f.row())
                assertEquals(modified, f.targetFile.lastModified())
                assertTrue(f.bytes.contentEquals(f.targetFile.readBytes()))
                assertTrue(f.targetFile.parentFile!!.list()!!.none { it.startsWith(".profile-") })
            }
            f.decode = { _, _ -> throw AgeSecretKeyRequiredException() }
            rejected()
            f.decode = { _, _ -> throw IllegalArgumentException("age 解密失败，请检查密钥与配置文件") }
            rejected()
            f.decode = { data, _ -> data }
            f.failValidation = true
            rejected()
            f.failValidation = false
            f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_local_save BEFORE UPDATE ON profiles WHEN NEW.id = 952 BEGIN SELECT RAISE(ABORT, 'isolated test failure'); END")
            rejected()
            f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_local_save")
            var allowed = true
            f.decode = { data, _ -> allowed = false; data }
            assertTrue(runCatching { f.repository.updateFileProfile(f.target.id, "new-fixture", f.bytes) { allowed } }.isFailure)
            assertEquals(before, f.row())
            assertEquals(modified, f.targetFile.lastModified())
            f.decode = { data, _ -> f.targetFile.setLastModified(modified + 1000); data }
            assertTrue(runCatching { f.repository.updateFileProfile(f.target.id, "new-fixture", f.bytes) }.isFailure)
            assertEquals(before, f.row())
            assertEquals(modified + 1000, f.targetFile.lastModified())
            f.decode = { data, key -> assertTrue(key == "new-fixture"); data }
            f.repository.updateFileProfile(f.target.id, " new-fixture ", f.bytes)
            assertTrue(f.bytes.contentEquals(f.targetFile.readBytes()))
            assertTrue(f.row().ageSecretKey == "new-fixture")
            assertTrue(checkNotNull(f.row().lastUpdateAt) > 1)
            assertEquals(f.selected.id, f.repository.currentProfileId.value)
            assertEquals(2, f.database.profiles().getAll().size)
        } }
    }

    @Test fun realOpenDocumentCancelsAndStagesWithoutWritingThenSavesExactAuthorizedFile() {
        fixture().use { f ->
            val runtime = object : CoreRuntimeStatusProvider { override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED) }
            lateinit var model: ProfilesViewModel
            compose.runOnUiThread { model = ProfilesViewModel(f.repository, runtime, fileReader = AndroidProfileFileDocumentReader(f.context)) }
            compose.setContent {
                val state by model.state.collectAsState()
                val editor by model.editorState.collectAsState()
                KClashTheme {
                    ProfilesPanel(state = state, editorState = editor, runtimeState = CoreRuntimeStatus.STOPPED,
                        showAddDialog = false, onShowAddDialog = {}, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                        onAddRemote = { _, _ -> error("文件替换不能添加订阅") }, onAddCustom = {}, onSelect = { error("文件替换不能切换配置") },
                        onEdit = model::openEditor, onUpdate = {}, onCloseEditor = model::closeEditor, onSaveEditor = model::saveEditor, onDelete = {},
                        onSaveFileEditor = model::saveFileEditor, onBeginFileReplacementPick = model::beginFileReplacementPick,
                        onFileReplacementPicked = model::acceptFileReplacement, onClearFileReplacement = model::clearFileReplacement)
                }
            }
            try {
                compose.waitUntil(10_000) { model.state.value.profiles.size == 2 }
                compose.onNodeWithTag("profile_actions_${f.target.id}").performClick()
                compose.onNodeWithText("编辑").performClick()
                compose.waitUntil(10_000) { model.editorState.value.profile?.id == f.target.id && !model.editorState.value.isLoading }
                val before = runBlocking { f.row() }
                val modified = f.targetFile.lastModified()
                compose.onNodeWithTag("file_profile_replace_pick").performClick()
                waitForPicker()
                shell("input keyevent KEYCODE_BACK")
                waitFor { root()?.packageName?.toString() == f.context.packageName }
                compose.waitUntil(5_000) { compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
                assertEquals(f.target.id, model.editorState.value.profile?.id)
                compose.onNodeWithTag("file_profile_replace_pick").assertIsDisplayed()
                assertNull(model.editorState.value.replacement)
                assertEquals(before, runBlocking { f.row() })
                assertEquals(modified, f.targetFile.lastModified())
                for (clear in listOf(true, false)) {
                    compose.onNodeWithTag("file_profile_replace_pick").performClick()
                    chooseAuthorizedFile()
                    compose.waitUntil(15_000) { model.editorState.value.replacement != null }
                    assertTrue(checkNotNull(model.editorState.value.replacement).isValidYaml)
                    assertEquals(34814, model.editorState.value.replacement?.sizeBytes)
                    assertEquals(before, runBlocking { f.row() })
                    assertEquals(modified, f.targetFile.lastModified())
                    if (clear) {
                        compose.onNodeWithTag("file_profile_replacement_clear").performClick()
                        assertNull(model.editorState.value.replacement)
                    }
                }
                compose.onNodeWithTag("file_profile_editor_age_key").performTextClearance()
                compose.onNodeWithTag("file_profile_editor_age_key").performTextInput(" new-fixture ")
                compose.onNodeWithTag("file_profile_editor_save").performClick()
                compose.waitUntil(15_000) { model.editorState.value.profile == null }
                assertTrue(f.bytes.contentEquals(f.targetFile.readBytes()))
                assertTrue(runBlocking { f.row() }.ageSecretKey == "new-fixture")
                assertTrue(checkNotNull(runBlocking { f.row() }.lastUpdateAt) > 1)
                assertEquals(f.selected.id, f.repository.currentProfileId.value)
                assertEquals(2, runBlocking { f.database.profiles().getAll().size })
            } finally { compose.runOnUiThread { model.closeEditor(); model.viewModelScope.cancel() } }
        }
    }

    private fun fixture(): FileFixture {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("只使用授权 1.yaml 运行", args.getString("kclashLocalFile") == "true")
        assertPortraitDefaultFontConfiguration()
        val bytes = shell("cat /sdcard/Download/1.yaml")
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashLocalFileSha256"), hash)
        val f = FileFixture(ApplicationProvider.getApplicationContext(), bytes)
        compose.waitUntil(10_000) { f.repository.profiles.value.size == 2 }
        runBlocking { f.repository.selectProfile(f.selected.id) }
        return f
    }

    private fun shell(command: String): ByteArray = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }
    private fun waitForPicker() = waitFor { root()?.packageName?.toString()?.endsWith("documentsui") == true }
    private fun root() = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            if (current?.isClickable == true && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current?.parent
        }
        return false
    }
    private fun chooseAuthorizedFile() {
        waitForPicker()
        fun choose(): Boolean = root()?.findAccessibilityNodeInfosByText("1.yaml")?.firstOrNull {
            it.text?.toString() == "1.yaml" && it.isVisibleToUser
        }?.let(::click) == true
        if (choose()) return
        val pickerRoot = checkNotNull(root())
        val drawer = pickerRoot.findAccessibilityNodeInfosByViewId("android:id/home").firstOrNull()
            ?: allNodes(pickerRoot).firstOrNull { node -> node.contentDescription?.toString()?.let { it.contains("根目录") || it.contains("Show roots", true) || it.contains("navigation", true) } == true }
        check(drawer != null && click(drawer)) { "系统文件选择器未找到目录入口" }
        waitFor {
            allNodes(root() ?: return@waitFor false).firstOrNull { node ->
                node.text?.toString()?.let { it.startsWith("下载") || it.equals("Downloads", true) } == true && node.isVisibleToUser
            }?.let(::click) == true
        }
        waitFor(::choose)
    }
    private fun allNodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(allNodes(it)) }
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("系统文件选择器未在规定时间内完成")
    }
}

private class FileFixture(val context: Context, val bytes: ByteArray) : AutoCloseable {
    private val directory = File(context.cacheDir, "local-file-edit-${System.nanoTime()}").apply { mkdirs() }
    private val isolated = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }
    val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
    val selected = Profile.custom(951, "当前配置")
    val target = Profile.custom(952, "另一份本地文件").copy(type = ProfileType.FILE, ageSecretKey = "old-fixture", lastUpdateAt = 1)
    var decode: suspend (ByteArray, String) -> ByteArray = { data, _ -> data }
    var failValidation = false
    val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
        override suspend fun download(url: String): SubscriptionDownload = error("本地配置不能访问订阅")
    }, ProfileConfigValidator { data ->
        if (failValidation) error("isolated validation failure")
        YamlProfileConfigValidator().validate(data)
    }, ProfileContentDecoder { data, key -> decode(data, key) })
    val targetFile get() = repository.profileFile(target.id)
    init {
        runBlocking { listOf(selected, target).forEach { database.profiles().insert(it.toEntity()) } }
        listOf(selected, target).forEach { repository.profileFile(it.id).apply { parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(1_600_000_000_000) } }
    }
    suspend fun row() = checkNotNull(database.profiles().getById(target.id))
    override fun close() {
        database.close()
        // 作者 long：本用例只清理已核对父路径的独占缓存；正式 Room、Profile、系统下载目录均不修改。
        check(directory.parentFile == context.cacheDir && directory.name.startsWith("local-file-edit-"))
        directory.deleteRecursively()
    }
}
