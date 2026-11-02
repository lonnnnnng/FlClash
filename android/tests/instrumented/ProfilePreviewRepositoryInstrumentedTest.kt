package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
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
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProfilePreviewRepositoryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun noncurrentPreviewUsesExactRoomIdWithoutChangingSelectionFilesOrCallingCore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("仅显式授权的 1.yaml 验收运行", arguments.getString("kclashProfilePreviewLocal") == "true")
        assertPortraitDefaultFontConfiguration()
        val bytes = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-profile-preview-1.yaml").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(bytes.isNotEmpty())
        assertEquals(arguments.getString("kclashProfilePreviewSha256"), hash)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "profile-preview-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        // 作者 long：当前配置故意绑定缺失脚本，证明只读其他卡片不依赖当前配置可渲染；正式 Room 完全不参与。
        val current = Profile.custom(741, "当前配置").copy(scriptId = 999)
        val targets = listOf(
            Profile.remote(742, "https://example.test/not-downloaded", "另一份订阅"),
            Profile.custom(743, "另一份文件").copy(type = ProfileType.FILE),
            Profile.custom(744, "另一份自定义配置"),
        )
        runBlocking { (listOf(current) + targets).forEach { database.profiles().insert(it.toEntity()) } }
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("预览不能下载订阅")
        })
        compose.waitUntil(10_000) { repository.profiles.value.size == 4 }
        runBlocking { repository.selectProfile(current.id) }
        val files = (listOf(current) + targets).map { repository.profileFile(it.id).apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
        val metadata = files.map { Triple(it.name, it.length(), it.lastModified()) }
        val home = File(directory, "core").apply { mkdirs() }
        val staged = File(home, "config.yaml").apply { writeBytes(bytes) }
        val stagedModified = staged.lastModified()
        var coreCalls = 0
        val bridge = Proxy.newProxyInstance(CoreRuntimeBridge::class.java.classLoader, arrayOf(CoreRuntimeBridge::class.java)) { _, method, _ ->
            when (method.name) {
                "getEvents" -> kotlinx.coroutines.flow.emptyFlow<com.lonnnnnng.flclash.core.CoreEvent>()
                "getEventFlowStats" -> kotlinx.coroutines.flow.MutableStateFlow(com.lonnnnnng.flclash.core.CoreEventFlowStats())
                else -> {
                    coreCalls++
                    error("只读预览不能调用核心")
                }
            }
        } as CoreRuntimeBridge
        val manager = CoreRuntimeManager(CoreRuntimePaths(home), repository, bridge,
            object : ProxyServiceController {
                override suspend fun start(options: com.lonnnnnng.flclash.service.models.VpnOptions) = error("预览不能启动服务")
                override fun stop() = error("预览不能停止服务")
            }, DefaultVpnOptionsProvider(), object : RuntimeResumeStore {
                override suspend fun shouldResume() = false
                override suspend fun setShouldResume(value: Boolean) = error("预览不能写运行意图")
            })
        lateinit var model: RuntimeConfigPreviewViewModel
        compose.runOnUiThread { model = RuntimeConfigPreviewViewModel(manager) }
        compose.setContent {
            val profiles by repository.profiles.collectAsState()
            val currentId by repository.currentProfileId.collectAsState()
            val state by model.state.collectAsState()
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = currentId),
                    editorState = ProfileEditorUiState(), runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false, onShowAddDialog = {}, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                    onAddRemote = { _, _ -> }, onAddCustom = {}, onSelect = { error("预览不能选择配置") },
                    onPreviewProfile = model::open, onEdit = {}, onUpdate = {}, onCloseEditor = {}, onSaveEditor = {}, onDelete = {},
                )
                RuntimeConfigPreviewHost(state, profiles.firstOrNull { it.id == state.profileId }, null, currentId,
                    CoreRuntimeStatus.STOPPED, "PROFILES", onRetry = { state.profileId?.let(model::open) }, onClose = model::close)
            }
        }
        try {
            assertTrue(runCatching { runBlocking { repository.activeProfile() } }.isFailure)
            assertNull(runBlocking { repository.profileById(9999) })
            for (target in targets) {
                compose.onNodeWithTag("profile_actions_${target.id}").performClick()
                if (target.type != ProfileType.CUSTOM) compose.onNodeWithTag("profile_more_${target.id}").performClick()
                compose.onNodeWithTag("profile_preview_${target.id}").performClick()
                compose.waitUntil(10_000) { model.state.value.preview != null || model.state.value.error != null }
                assertNull(model.state.value.error)
                val preview = checkNotNull(model.state.value.preview)
                assertEquals(target.id, preview.profileId)
                assertEquals(target.label, preview.label)
                assertFalse(preview.isApplied)
                assertTrue("预览必须来自目标缓存原文", bytes.contentEquals(preview.content.toByteArray(Charsets.UTF_8)))
                assertEquals(current.id, repository.currentProfileId.value)
                compose.onNodeWithTag("runtime_config_preview_close").performClick()
                compose.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
            }
            assertEquals(0, coreCalls)
            assertTrue("暂存配置不能被改写", bytes.contentEquals(staged.readBytes()))
            assertEquals(stagedModified, staged.lastModified())
            assertEquals(metadata, files.map { Triple(it.name, it.length(), it.lastModified()) })
            assertEquals(4, runBlocking { database.profiles().getAll().size })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        } finally {
            compose.runOnUiThread { model.close(); model.viewModelScope.cancel() }
            database.close()
            // 作者 long：目录是本用例创建的独占缓存，清理前核对父路径和前缀，不触及正式 Profile 文件。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("profile-preview-"))
            directory.deleteRecursively()
        }
    }
}
