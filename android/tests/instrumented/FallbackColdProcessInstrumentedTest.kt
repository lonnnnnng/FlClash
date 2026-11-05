package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** 作者 long：Fallback 的固定选择必须跨应用进程由 Room 选择和核心重载共同恢复，不能用 Activity 重建代替冷进程。 */
class FallbackColdProcessInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun entry() = EntryPointAccessors.fromApplication(
        compose.activity.application,
        ConnectionAcceptanceEntryPoint::class.java,
    )
    private fun marker() = File(compose.activity.cacheDir, "fallback-cold-process.json")

    private fun gate(phase: String) {
        assertPortraitDefaultFontConfiguration()
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals("true", arguments.getString("kclashFallbackColdAcceptance"))
        assertEquals(phase, arguments.getString("kclashFallbackColdPhase"))
        instrumentation.sendStatus(2, Bundle().apply {
            putString("kclash_fallback_cold_pid", Process.myPid().toString())
        })
    }

    @Test
    fun prepareFallbackFixedSelectionForColdProcess(): Unit = runBlocking(Dispatchers.IO) {
        gate("prepare")
        assertFalse("不能覆盖未收尾的Fallback冷进程标记", marker().exists())
        val e = entry()
        val repo = e.profileRepository()
        val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val originalRows = e.database().profiles().getAll()
        val originalSelected = repo.currentProfileId.value
        val originalMapById = originalRows.associate { it.id to decodeSelectedMap(it.selectedMapJson) }
        val originalFiles = originalRows.associate { row ->
            row.id to repo.profileFile(row.id).let { file -> Triple(file.length(), file.lastModified(), sha(file.readBytes())) }
        }
        val originalVpn = e.vpnSettingsStore().read()
        val originalMode = manager.outboundMode.value.name
        val url = readAuthorizedUrl()
        var profile: Profile? = null
        var added = false
        var stage = "授权源"
        try {
            profile = repo.profiles.value.singleOrNull { it.url == url }
                ?: repo.addRemoteProfile(url, "fallback-cold-${System.nanoTime()}").also { added = true }
            manager.setVpnEnabled(false).getOrThrow()
            manager.selectProfile(profile.id, repo::selectProfile)
            assertEquals(profile.id, repo.currentProfileId.value)
            stage = "读取Fallback"
            val initial = manager.getProxyGroups(profile.id).getOrThrow()
            val group = initial.groups.firstOrNull { it.type.equals("Fallback", true) && it.all.isNotEmpty() }
                ?: error("授权源没有非空Fallback")
            val automatic = group.now?.takeIf { it in group.all }
                ?: error("授权源Fallback没有组内有效now")
            val groupName = group.name
            stage = "持久化固定选择"
            manager.changeProxy(profile.id, groupName, automatic).getOrThrow()
            repo.updateProfileProxySelection(profile.id, groupName, automatic)
            val pinned = manager.getProxyGroups(profile.id).getOrThrow().groups.single { it.name == groupName }
            assertEquals(automatic, pinned.fixed)
            assertTrue(pinned.now != null && pinned.now in pinned.all)
            val profileRow = e.database().profiles().getById(profile.id)!!.toDomain()
            val record = JsonObject().apply {
                addProperty("preparedPid", Process.myPid())
                addProperty("profileId", profile.id)
                addProperty("profileAdded", added)
                addProperty("groupName", groupName)
                addProperty("fixed", automatic)
                addProperty("automaticNow", pinned.now)
                addProperty("originalSelected", originalSelected?.toString() ?: "")
                addProperty("originalMode", originalMode)
                addProperty("originalVpn", originalVpn)
                addProperty("originalRowsSha", sha(originalRows.joinToString("|") { it.toString() }.toByteArray()))
                addProperty("originalFiles", originalFiles.entries.joinToString("|") { (id, value) -> "$id:${value.first}:${value.second}:${value.third}" })
                addProperty("profileBodySha", sha(repo.profileFile(profile.id).readBytes()))
                addProperty("originalMap", encodeSelectedMap(originalMapById[profile.id].orEmpty()))
                addProperty("selectedMapAfterPin", profileRow.selectedMapJson)
            }
            marker().writeText(record.toString())
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_fallback_cold", "prepared;members=${pinned.all.size};fixed=true;valid-now=${pinned.now in pinned.all}")
            })
        } catch (failure: Throwable) {
            // 作者 long：失败时只报告阶段和类型，避免把授权正文、节点地址或密钥写入测试输出。
            val location = failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }
                ?.let { "${it.fileName}:${it.lineNumber}" }
            throw AssertionError("Fallback冷恢复准备失败：$stage/${failure.javaClass.simpleName}/$location")
        } finally {
            // 成功准备后必须保留临时状态给下一进程；只有异常路径负责立即回收，避免留下不可解释的Profile。
            if (!marker().exists()) {
                runCatching { manager.stop() }
                profile?.takeIf { added }?.let { runCatching { repo.deleteProfile(it.id) } }
                runCatching { if (originalSelected == null) repo.selectProfile(null) else manager.selectProfile(originalSelected, repo::selectProfile) }
                runCatching { manager.setVpnEnabled(originalVpn) }
            }
        }
    }

    @Test
    fun verifyFallbackFixedSelectionAfterColdProcessAndClear(): Unit = runBlocking(Dispatchers.IO) {
        gate("verify")
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertNotEquals("验证必须运行在force-stop后的新进程", record.get("preparedPid").asInt, Process.myPid())
        val e = entry()
        val repo = e.profileRepository()
        val manager = e.runtimeManager()
        val id = record.get("profileId").asLong
        val groupName = record.get("groupName").asString
        val fixed = record.get("fixed").asString
        var success = false
        try {
            compose.waitUntil(15_000) { repo.currentProfileId.value == id && repo.profiles.value.any { it.id == id } }
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertEquals(record.get("profileBodySha").asString, sha(repo.profileFile(id).readBytes()))
            val restoredProfile = e.database().profiles().getById(id)!!.toDomain()
            assertEquals(fixed, decodeSelectedMap(restoredProfile.selectedMapJson)[groupName])
            val restored = manager.getProxyGroups(id).getOrThrow().groups.single { it.name == groupName }
            assertTrue("冷进程恢复后Fallback成员不能为空", restored.all.isNotEmpty())
            assertTrue("冷进程恢复后Fallback now必须属于当前组", restored.now != null && restored.now in restored.all)
            assertEquals(fixed, restored.fixed)
            assertEquals(fixed, restored.now)

            manager.changeProxy(id, groupName, "").getOrThrow()
            repo.updateProfileProxySelection(id, groupName, null)
            val automatic = manager.getProxyGroups(id).getOrThrow().groups.single { it.name == groupName }
            assertTrue("清除固定后Fallback now必须仍属于当前组", automatic.now != null && automatic.now in automatic.all)
            assertTrue("清除固定后核心 fixed 必须为空", automatic.fixed.isNullOrBlank())
            assertTrue("清除固定后Room选择必须为空", decodeSelectedMap(e.database().profiles().getById(id)!!.toDomain().selectedMapJson)[groupName].isNullOrBlank())
            success = true
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_fallback_cold", "restored;members=${automatic.all.size};fixed-restored=true;now-valid=${automatic.now in automatic.all};clear-fixed=true")
            })
        } finally {
            runCatching { manager.stop() }
            val originalSelected = record.get("originalSelected").asString.toLongOrNull()
            val originalVpn = record.get("originalVpn").asBoolean
            val originalMode = record.get("originalMode").asString
            runCatching { repo.updateProfileSelectedMap(id, JsonParser.parseString(record.get("originalMap").asString).asJsonObject.entrySet().associate { it.key to it.value.asString }) }
            if (record.get("profileAdded").asBoolean) {
                runCatching { if (originalSelected == null) repo.selectProfile(null) else manager.selectProfile(originalSelected, repo::selectProfile) }
                runCatching { repo.deleteProfile(id) }
            } else if (originalSelected != null) {
                runCatching { manager.selectProfile(originalSelected, repo::selectProfile) }
            } else {
                runCatching { repo.selectProfile(null) }
            }
            runCatching { manager.setOutboundMode(com.lonnnnnng.flclash.core.CoreOutboundMode.valueOf(originalMode)) }
            runCatching { manager.setVpnEnabled(originalVpn) }
            if (success) assertTrue("成功验收必须清理跨进程标记", marker().delete())
        }
    }

    private fun readAuthorizedUrl(): String {
        val input = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-fallback-cold-authorized.json").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        return org.json.JSONObject(input).getString("url")
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
