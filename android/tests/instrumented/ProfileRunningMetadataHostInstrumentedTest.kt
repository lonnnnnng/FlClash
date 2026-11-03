package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：运行中只调整名称和排序入口，原订阅地址/密钥及正文保持；finally恢复元数据和VPN开关。 */
class ProfileRunningMetadataHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningProfileAllowsRemoteSettingsSyncAndSortWithoutRestartingOrRewritingBody() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("kclashProfileRunningAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager(); val repo = entry.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertNull(android.net.VpnService.prepare(context))
        val before = entry.database().profiles().getAll()
        val selected = repo.currentProfileId.value
        val profile = before.single { it.id == selected }
        assertEquals(ProfileType.URL, profile.type)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(profile.url.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashAuthorizedSourceSha256"), hash)
        val files = before.associate { it.id to repo.profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } }
        val originalVpn = entry.vpnSettingsStore().read()
        try {
            manager.setVpnEnabled(true).getOrThrow(); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profiles_sync_all").assertIsEnabled()
            compose.onNodeWithTag("profiles_sort").assertIsEnabled().performClick()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("profile_card_${profile.id}").performScrollTo()
            compose.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_edit_${profile.id}").assertIsEnabled().performClick()
            val temporaryLabel = "运行态验收-${System.nanoTime()}"
            compose.onNodeWithTag("remote_profile_editor_label").performTextReplacement(temporaryLabel)
            compose.onNodeWithTag("remote_profile_editor_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("remote_profile_editor_form").fetchSemanticsNodes().isEmpty() }
            val updated = checkNotNull(entry.database().profiles().getById(profile.id))
            assertEquals(profile.copy(label = temporaryLabel), updated)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(selected, repo.currentProfileId.value)
            assertEquals(files, before.associate { it.id to repo.profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } })
        } finally {
            manager.stop().getOrThrow()
            repo.updateRemoteProfileSettings(profile.id, profile.label, profile.url, profile.autoUpdate, profile.autoUpdateIntervalMillis, profile.ageSecretKey)
            manager.setVpnEnabled(originalVpn).getOrThrow()
            assertEquals(before, entry.database().profiles().getAll())
        }
    }
}
