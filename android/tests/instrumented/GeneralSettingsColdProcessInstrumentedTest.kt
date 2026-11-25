package com.lonnnnnng.flclash.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.GeneralCoreOverrideKey
import com.lonnnnnng.flclash.service.GeneralSettings
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 作者 long：确认设备级常规覆写经独立 Android 进程退出后仍从私有偏好恢复，结束时恢复用户原值。 */
@RunWith(AndroidJUnit4::class)
class GeneralSettingsColdProcessInstrumentedTest {
    @Test fun generalSettingsSurviveFreshProcessAndRestoreOriginalValues() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashGeneralSettingsColdProcess") == "true")
        val phase = args.getString("kclashGeneralSettingsColdPhase").orEmpty()
        val requestedToken = args.getString("kclashGeneralSettingsColdToken").orEmpty()
        require(phase in setOf("write", "read-restore"))

        val context = instrumentation.targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals("偏好冷恢复验收不得启动核心", CoreRuntimeStatus.STOPPED, manager.state.value)
        val store = entry.generalSettingsStore()
        val backupFiles = context.noBackupFilesDir.listFiles { file ->
            Regex("general-settings-cold-[a-f0-9]{32}\\.json").matches(file.name)
        }.orEmpty()
        val token = when {
            phase == "write" && Regex("[a-f0-9]{32}").matches(requestedToken) -> requestedToken
            phase == "read-restore" && requestedToken == "recover" && backupFiles.size == 1 ->
                backupFiles.single().name.removePrefix("general-settings-cold-").removeSuffix(".json")
            phase == "read-restore" && Regex("[a-f0-9]{32}").matches(requestedToken) -> requestedToken
            else -> error("本轮冷恢复令牌无效或快照不唯一")
        }
        val backup = File(context.noBackupFilesDir, "general-settings-cold-$token.json")
        val gson = Gson()

        when (phase) {
            "write" -> {
                check(!backup.exists()) { "临时偏好备份目标已存在" }
                val original = store.read()
                backup.writeText(gson.toJson(original), Charsets.UTF_8)
                val next = original.copy(
                    delayTestTimeout = 500,
                    testUrl = "https://cold-$token.invalid/generate_204",
                    generalOverrideKeys = original.generalOverrideKeys + setOf(
                        GeneralCoreOverrideKey.TEST_URL,
                        GeneralCoreOverrideKey.UNIFIED_DELAY,
                    ),
                    unifiedDelay = !original.unifiedDelay,
                ).validated()
                store.write(next)
                assertEquals(next, store.read())
                instrumentation.sendStatus(2, android.os.Bundle().apply {
                    putString("kclash_general_settings_cold_write", "${tokenDigest(token)};pid=${android.os.Process.myPid()}")
                })
            }

            "read-restore" -> {
                check(backup.isFile) { "首进程未留下本轮私有恢复快照" }
                val original = gson.fromJson(backup.readText(Charsets.UTF_8), GeneralSettings::class.java).validated()
                try {
                    val restored = store.read()
                    assertEquals(500, restored.delayTestTimeout)
                    assertEquals("https://cold-$token.invalid/generate_204", restored.testUrl)
                    assertTrue(GeneralCoreOverrideKey.TEST_URL in restored.generalOverrideKeys)
                    assertTrue(GeneralCoreOverrideKey.UNIFIED_DELAY in restored.generalOverrideKeys)
                    assertEquals(!original.unifiedDelay, restored.unifiedDelay)
                } finally {
                    store.write(original)
                    check(backup.delete() || !backup.exists()) { "本轮私有偏好恢复快照未能删除" }
                }
                assertEquals(original, store.read())
                assertEquals("核心不得因偏好冷恢复而启动", CoreRuntimeStatus.STOPPED, manager.state.value)
                instrumentation.sendStatus(2, android.os.Bundle().apply {
                    putString("kclash_general_settings_cold_restore", "passed-${tokenDigest(token)};pid=${android.os.Process.myPid()}")
                })
            }
        }
    }

    private fun tokenDigest(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.US_ASCII)).take(6).joinToString("") { "%02x".format(it) }
}
