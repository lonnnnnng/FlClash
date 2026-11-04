package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest

/** 作者 long：授权订阅里的真实Fallback用于验证核心契约，异常now由独立协议测试覆盖，不能改写原1.yaml强造核心状态。 */
class FallbackRealCoreInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun authorizedFallbackKeepsValidNowStoppedRunningPinnedClearedAndAfterInvalidSelection() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashFallbackAcceptance"))
        assertPortraitDefaultFontConfiguration()
        val input = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-fallback-authorized.json").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        val authorized = org.json.JSONObject(input).getString("url")
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll()
        if (rows.isNotEmpty()) compose.waitUntil(30_000) { repo.currentProfileId.value in rows.map { it.id } }
        val selected = repo.currentProfileId.value
        fun signatures() = rows.associate { row -> repo.profileFile(row.id).let {
            row.id to Triple(it.length(), it.lastModified(), MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList())
        } }
        val files = signatures(); val vpn = e.vpnSettingsStore().read()
        val token = "fallback-core-${System.nanoTime()}"
        var temporary: Profile? = null
        var originalFixed: Triple<Long, String, String>? = null
        var phase = "authorized-input"
        var sampled = 0
        try {
            phase = "download-or-reuse"
            val profile = repo.profiles.value.singleOrNull { it.url == authorized }
                ?: repo.addRemoteProfile(authorized, token).also { temporary = it }
            phase = "disable-vpn"
            manager.setVpnEnabled(false).getOrThrow()
            phase = "select-profile"
            manager.selectProfile(profile.id, repo::selectProfile)
            phase = "find-fallback"
            var group = manager.getProxyGroups(profile.id).getOrThrow().groups.firstOrNull { it.type == "Fallback" && it.all.isNotEmpty() }
                ?: error("授权订阅未提供Fallback组")
            val groupName = group.name
            originalFixed = Triple(profile.id, groupName, group.fixed?.takeIf { it in group.all }.orEmpty())
            fun report() = runBlocking {
                group = manager.getProxyGroups(profile.id).getOrThrow().groups.single { it.name == groupName }
                assertTrue("合法非空Fallback必须返回组内now", group.now != null && group.now in group.all)
                assertEquals(group.now, selectedProxyName(group))
                sampled++
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("kclash_fallback_core", "phase=$phase;members=${group.all.size};valid-now=true;sample=$sampled")
                })
            }
            for (running in listOf(false, true)) {
                if (running) manager.startProxy().getOrThrow()
                val since = manager.runningSinceElapsedMillis.value
                phase = "$running-automatic"; manager.changeProxy(profile.id, groupName, "").getOrThrow(); report()
                phase = "$running-pin"; manager.changeProxy(profile.id, groupName, requireNotNull(group.now)).getOrThrow(); report()
                phase = "$running-invalid"
                assertTrue(manager.changeProxy(profile.id, groupName, "__KCLASH_INVALID_FALLBACK_MEMBER__").isFailure); report()
                phase = "$running-clear"; manager.changeProxy(profile.id, groupName, "").getOrThrow(); report()
                assertEquals(since, manager.runningSinceElapsedMillis.value)
            }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_fallback_contract", "real-core-samples=$sampled;invalid-now=0;null-now-injection=false")
            })
        } catch (failure: Throwable) {
            // 作者 long：订阅和核心错误可能包含真实地址；测试只报告阶段和类型，不输出对象、节点名称或底层消息。
            val location = failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }
            throw AssertionError("Fallback实核阶段=$phase 类型=${failure.javaClass.simpleName} 位置=$location")
        } finally {
            // 作者 long：直接操作核心不修改Room，但复用同一配置可能命中加载缓存；先恢复原固定节点，避免验收留下自动选择状态。
            originalFixed?.let { (id, name, fixed) -> manager.changeProxy(id, name, fixed).getOrThrow() }
            manager.stop().getOrThrow()
            if (selected == null) repo.selectProfile(null) else manager.selectProfile(selected, repo::selectProfile)
            temporary?.let { profile ->
                val row = e.database().profiles().getById(profile.id)
                check(row == null || row.label == token && rows.none { it.id == row.id })
                if (row != null) repo.deleteProfile(profile.id)
            }
            if (selected == null) repo.selectProfile(null)
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue("正式配置行保持", rows == e.database().profiles().getAll())
            assertTrue("正式正文和时间保持", files == signatures())
            assertEquals(selected, repo.currentProfileId.value)
        }
    }
}
