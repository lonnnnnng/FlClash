package com.lonnnnnng.flclash.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式页面仅编辑本次临时远程Profile的本地副本，原两份配置及原1.yaml保持，不截图真实正文。 */
class ProfileDocumentHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningRemoteBodyStagesAcrossRecreationAndOuterSaveAppliesWithoutDownloading() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("kclashProfileDocumentAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager(); val repo = entry.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertNull(android.net.VpnService.prepare(context))
        val before = entry.database().profiles().getAll()
        val selected = requireNotNull(repo.currentProfileId.value)
        val files = before.associate { row -> row.id to repo.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        val bytes = readAuthorizedProxyProfile()
        val id = System.nanoTime().and(Long.MAX_VALUE)
        assertNull(entry.database().profiles().getById(id))
        val temp = Profile.remote(id, "https://example.test/local-only", "document-${System.nanoTime()}").copy(orderIndex = before.size, lastUpdateAt = 1)
        val originalVpn = entry.vpnSettingsStore().read()
        try {
            entry.database().profiles().insert(temp.toEntity())
            repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(1_600_000_000_000) }
            manager.selectProfile(id, repo::selectProfile)
            manager.setVpnEnabled(true).getOrThrow(); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profile_card_$id").performScrollTo()
            compose.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_edit_$id").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("remote_profile_document_edit").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            val renamed = "document-draft-${System.nanoTime()}"
            compose.onNodeWithTag("remote_profile_editor_label").performTextReplacement(renamed)
            compose.onNodeWithTag("remote_profile_editor_form").performScrollToNode(hasTestTag("remote_profile_document_edit"))
            compose.onNodeWithTag("remote_profile_document_edit").performClick()
            compose.onNodeWithTag("profile_editor_content").assertExists()
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("profile_document_editor").assertExists()
            // 作者 long：不改原1.yaml任何字段；子页确认后仍应零落盘，真正写入只在外层保存。
            compose.onNodeWithTag("profile_document_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_document_editor").fetchSemanticsNodes().isEmpty() }
            assertEquals(temp.toEntity(), entry.database().profiles().getById(id))
            assertEquals(1_600_000_000_000, repo.profileFile(id).lastModified())
            compose.onNodeWithTag("remote_profile_editor_form").performScrollToNode(hasTestTag("remote_profile_editor_label"))
            compose.onNodeWithTag("remote_profile_editor_label").assertTextContains(renamed)
            compose.onNodeWithTag("remote_profile_editor_save").performClick()
            compose.onNodeWithTag("remote_profile_disable_auto_save").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("remote_profile_editor_form").fetchSemanticsNodes().isEmpty() }
            assertEquals(renamed, entry.database().profiles().getById(id)?.label)
            assertEquals(false, entry.database().profiles().getById(id)?.autoUpdate)
            assertArrayEquals(bytes, repo.profileFile(id).readBytes())
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertTrue(manager.profileInputsApplied(id))
            assertEquals(id, repo.currentProfileId.value)
            assertEquals(before, entry.database().profiles().getAll().filter { it.id != id })
            assertEquals(files, before.associate { row -> row.id to repo.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
        } finally {
            manager.stop().getOrThrow()
            manager.selectProfile(selected, repo::selectProfile)
            entry.database().profiles().getById(id)?.let { row -> if (row.url == temp.url) repo.deleteProfile(id) }
            manager.setVpnEnabled(originalVpn).getOrThrow()
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, repo.currentProfileId.value)
        }
    }
}
