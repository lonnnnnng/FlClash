package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：临时配置只复制原1.yaml；网络用例仅使用经外层授权哈希核对的同一订阅，不同步其他既有来源。 */
class ProfileClosureHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun entry(): ConnectionAcceptanceEntryPoint = EntryPointAccessors.fromApplication(compose.activity.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
    private fun gate() {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashProfileClosureAcceptance"))
    }

    @Test fun runningFileAndCustomSavesApplyWithoutRestartingSession() = runBlocking {
        gate()
        val e = entry(); val manager = e.runtimeManager(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val before = e.database().profiles().getAll(); val selected = requireNotNull(repo.currentProfileId.value)
        val originalVpn = e.vpnSettingsStore().read(); val bytes = readAuthorizedProxyProfile()
        val ids = listOf(System.nanoTime().and(Long.MAX_VALUE), System.nanoTime().and(Long.MAX_VALUE))
        var model: ProfilesViewModel? = null
        try {
            for ((index, id) in ids.withIndex()) {
                val row = Profile.custom(id, "closure-$index").copy(type = if (index == 0) ProfileType.FILE else ProfileType.CUSTOM, orderIndex = before.size + index)
                e.database().profiles().insert(row.toEntity())
                repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            }
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
            manager.setVpnEnabled(false).getOrThrow()
            manager.selectProfile(ids[0], repo::selectProfile); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            val file = requireNotNull(repo.extensionProfile(ids[0]))
            compose.runOnIdle { model!!.openEditor(file) }
            compose.waitUntil(15_000) { model!!.editorState.value.document != null }
            compose.onNodeWithTag("file_profile_editor_form").assertExists()
            compose.runOnIdle { model!!.saveFileEditor(bytes.toString(Charsets.UTF_8), "", "file-running") }
            compose.waitUntil(25_000) { model!!.editorState.value.profile == null }
            assertTrue(manager.profileInputsApplied(file.id)); assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertArrayEquals(bytes, repo.profileFile(file.id).readBytes())
            manager.selectProfile(ids[1], repo::selectProfile)
            val custom = requireNotNull(repo.extensionProfile(ids[1]))
            compose.runOnIdle { model!!.openEditor(custom) }
            compose.waitUntil(15_000) { !model!!.editorState.value.isLoading && model!!.editorState.value.profile != null }
            compose.onNodeWithTag("custom_profile_title_menu").assertIsEnabled()
            compose.runOnIdle { model!!.openRulesEditor(custom) }
            compose.waitUntil(15_000) { !model!!.rulesEditorState.value.isLoading && model!!.rulesEditorState.value.profile != null }
            compose.runOnIdle { model!!.saveRules(model!!.rulesEditorState.value.rules) }
            compose.waitUntil(25_000) { !model!!.rulesEditorState.value.isSaving }
            assertNull(model!!.rulesEditorState.value.error)
            assertTrue(manager.profileInputsApplied(custom.id)); assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        } finally {
            compose.runOnIdle { model?.closeEditor() }
            manager.stop().getOrThrow(); manager.selectProfile(selected, repo::selectProfile)
            ids.forEach { repo.deleteProfile(it) }
            manager.setVpnEnabled(originalVpn).getOrThrow()
            assertTrue("原配置必须保持", before == e.database().profiles().getAll())
            assertEquals(selected, repo.currentProfileId.value)
        }
    }

    @Test fun deletingCurrentSwitchesBeforeRemovalWithoutTouchingOriginals() = runBlocking {
        gate()
        val e = entry(); val manager = e.runtimeManager(); val repo = e.profileRepository()
        val before = e.database().profiles().getAll(); val selected = requireNotNull(repo.currentProfileId.value)
        val vpn = e.vpnSettingsStore().read(); val bytes = readAuthorizedProxyProfile()
        val first = System.nanoTime().and(Long.MAX_VALUE); val second = System.nanoTime().and(Long.MAX_VALUE)
        try {
            for (id in listOf(first, second)) {
                e.database().profiles().insert(Profile.custom(id, "delete-$id").copy(type = ProfileType.FILE, orderIndex = before.size).toEntity())
                repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            }
            manager.setVpnEnabled(false).getOrThrow(); manager.selectProfile(first, repo::selectProfile); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            manager.removeProfile(first, { listOf(first, second) }, repo::selectProfile) { repo.deleteProfile(first) }
            assertNull(e.database().profiles().getById(first)); assertFalse(repo.profileFile(first).exists())
            assertEquals(second, repo.currentProfileId.value); assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value); assertTrue(manager.profileInputsApplied(second))

        } finally {
            manager.stop().getOrThrow(); manager.selectProfile(selected, repo::selectProfile)
            listOf(first, second).forEach { repo.deleteProfile(it) }
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue("原配置必须保持", before == e.database().profiles().getAll())
        }
    }

    @Test fun authorizedTwoItemBatchDownloadsAndAppliesOnlyCurrentWithoutTouchingOriginals() = runBlocking {
        gate()
        val e = entry(); val manager = e.runtimeManager(); val repo = e.profileRepository()
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val before = e.database().profiles().getAll(); val selected = requireNotNull(repo.currentProfileId.value)
        val source = before.single { it.type == ProfileType.URL && digest(it.url.toByteArray()) == args.getString("kclashAuthorizedSourceSha256") }
        val preserved = before.associate { it.id to digest(repo.profileFile(it.id).readBytes()) }
        val vpn = e.vpnSettingsStore().read(); val bytes = readAuthorizedProxyProfile()
        val ids = listOf(System.nanoTime().and(Long.MAX_VALUE), System.nanoTime().and(Long.MAX_VALUE))
        val owners = androidx.lifecycle.ViewModelStore()
        try {
            val temporary = ids.mapIndexed { index, id -> source.toDomain().copy(id = id, label = "batch-$index", orderIndex = before.size + index, autoUpdate = false) }
            temporary.forEach { row ->
                e.database().profiles().insert(row.toEntity())
                repo.profileFile(row.id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            }
            // 作者 long：过滤的只是本批目标列表；下载、Room、正文和核心均为正式实例，绝不点击会触达其他来源的“同步全部”。
            val scoped = object : ProfileStore by repo { override val profiles = MutableStateFlow(temporary) }
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ProfilesViewModel(scoped, manager); owners.put("batch", model) }
            manager.setVpnEnabled(false).getOrThrow(); manager.selectProfile(ids[0], repo::selectProfile); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            compose.runOnIdle { model.updateAll() }
            withTimeout(120_000) { while (model.syncState.value.running) delay(100) }
            val result = model.syncState.value
            assertEquals(2, result.items.size)
            assertTrue("授权下载批次必须全部成功", result.items.all { it.status == ProfileSyncStatus.SUCCEEDED })
            assertNull(result.applyError)
            assertEquals(ids[0], repo.currentProfileId.value); assertTrue(manager.profileInputsApplied(ids[0]))
            assertEquals(since, manager.runningSinceElapsedMillis.value); assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertTrue("原配置行必须保持", before == e.database().profiles().getAll().filter { it.id !in ids })
            assertEquals(preserved, before.associate { it.id to digest(repo.profileFile(it.id).readBytes()) })
        } finally {
            compose.runOnIdle { owners.clear() }
            manager.stop().getOrThrow(); manager.selectProfile(selected, repo::selectProfile)
            ids.forEach { repo.deleteProfile(it) }
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue("原配置必须保持", before == e.database().profiles().getAll())
        }
    }

    private fun digest(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
