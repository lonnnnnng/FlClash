package com.lonnnnnng.flclash.update

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.BuildConfig
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：独立包打开真实更新页查询GitHub；只核对版本检查，不下载APK或改正式包。 */
class AppUpdateCheckInstrumentedTest {
    @Test fun actualPageChecksCurrentVersionWithoutDownloadOrInstallation() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        entry.profileRepository().profilesLoaded.first { it }
        val before = entry.database().profiles().getAll()
        ActivityScenario.launch(AppUpdateActivity::class.java).use { scenario ->
            lateinit var repository: AppUpdateRepository
            scenario.onActivity { repository = it.repository }
            val result = withTimeout(40_000) {
                repository.state.first { it.phase !in setOf(UpdatePhase.IDLE, UpdatePhase.CHECKING) }
            }
            assertNotEquals("真实版本请求失败", UpdatePhase.FAILED, result.phase)
            val latest = checkNotNull(result.release)
            assertEquals(InstrumentationRegistry.getArguments().getString("kclashExpectedLatestVersion"), latest.version)
            assertEquals(if (latest.versionCode > BuildConfig.VERSION_CODE) UpdatePhase.AVAILABLE else UpdatePhase.LATEST, result.phase)
            assertEquals(BuildConfig.VERSION_NAME, repository.currentVersion)
            assertEquals("https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v${latest.version}", latest.releaseUrl)
            assertFalse(File(context.noBackupFilesDir, "app-update").exists())
            assertTrue(context.packageManager.packageInstaller.mySessions.isEmpty())
            ins.sendStatus(2, android.os.Bundle().apply { putString("kclash_update_check", org.json.JSONObject()
                .put("passed", true).put("currentVersion", repository.currentVersion).put("latestVersion", latest.version)
                .put("phase", result.phase.name).put("apkDownloaded", false).put("installerSessions", 0).toString()) })
        }
        assertEquals(before, entry.database().profiles().getAll())
    }
}
