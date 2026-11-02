package com.lonnnnnng.flclash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProfileExtensionActivityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realNoncurrentExtensionPersistsAndReopensWithoutSwitchingOrStarting() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = repository.currentProfileId.value
        val before = runBlocking(Dispatchers.IO) { entry.database().profiles().getAll().map { it.id }.toSet() }
        val profile = runBlocking(Dispatchers.IO) { repository.addFileProfile("扩展页 Activity 验收", bytes) }
        val file = repository.profileFile(profile.id)
        val modified = file.lastModified()
        try {
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_card_${profile.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_actions_${profile.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_${profile.id}").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_extension_mode_standard").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_extension_apply").assertDoesNotExist()
            compose.onNodeWithTag("extension_rule_add").performScrollTo().performClick()
            compose.onNodeWithTag("extension_rule_content").performTextInput("activity.test")
            compose.onNodeWithTag("extension_rule_confirm").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("extension_rule_confirm").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("profile_extension_back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_extension_back").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("profile_actions_${profile.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_${profile.id}").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_extension_mode_standard").fetchSemanticsNodes().isNotEmpty() }
            val saved = runBlocking(Dispatchers.IO) { checkNotNull(entry.database().profiles().getById(profile.id)) }
            val rules = ProfileProviderOverrides.fromJsonString(saved.overridesJson).extension.rules
            assertEquals("DOMAIN-SUFFIX,activity.test,DIRECT", rules.single().value)
            compose.onNodeWithTag("extension_rule_${rules.single().id}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("profile_extension_back").performClick()
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            assertTrue(file.readBytes().contentEquals(bytes)); assertEquals(modified, file.lastModified())
        } finally {
            // 作者 long：仅回收本用例创建并核对 ID/名称的临时配置，不触碰正式配置、当前选择或授权源文件。
            runBlocking(Dispatchers.IO) {
                val row = entry.database().profiles().getById(profile.id)
                if (row != null) { check(profile.id !in before && row.label == "扩展页 Activity 验收"); repository.deleteProfile(profile.id) }
                assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
                assertEquals(selected, repository.currentProfileId.value)
            }
        }
    }
    @Test fun stoppedMihomoAppliesStandardAndScriptAndRollsBackFailureWithoutStartingVpn() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        suspend fun step(name: String, action: suspend () -> Unit) {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("kclash_extension_phase", name) })
            try { action() } catch (failure: Exception) {
                // 作者 long：YAML/核心异常可能附带真实节点正文；定位阶段只输出异常类型链，不能把原始消息带进验收日志。
                val types = generateSequence<Throwable>(failure) { it.cause }.take(6).joinToString("/") { it.javaClass.simpleName }
                val frames = failure.stackTrace.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                throw IllegalStateException("扩展核心验收失败阶段：$name；类型：$types\n$frames")
            }
        }
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); val runtime = entry.runtimeManager()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = checkNotNull(repository.currentProfileId.value)
        val before = entry.database().profiles().getAll().map { it.id }.toSet()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val profile = repository.addFileProfile("扩展核心验收临时配置", bytes)
        val scripts = com.lonnnnnng.flclash.data.ScriptRepository(compose.activity.application, entry.database())
        var scriptId: Long? = null
        val file = repository.profileFile(profile.id); val modified = file.lastModified()
        try {
            step("select-local") { runtime.selectProfile(profile.id, repository::selectProfile) }
            val old = entry.paths().configFile.readText()
            val own = com.lonnnnnng.flclash.service.ProfileExtensionOverrides(true,
                listOf(com.lonnnnnng.flclash.service.ExtensionRule("native-standard", "DOMAIN,extension-standard.test,DIRECT")))
            repository.saveExtension(profile.id, "standard", null, null, own)
            runtime.getProxyGroups().getOrThrow()
            assertTrue("后台刷新不能提前应用扩展", old == entry.paths().configFile.readText())
            assertFalse(runtime.profileInputsApplied(profile.id))
            step("render-standard") {
                val preview = runtime.previewConfig(profile.id)
                assertTrue(preview.content.contains("extension-standard.test"))
            }
            step("apply-standard") { runtime.applyCurrentProfile(profile.id) }
            assertTrue(runtime.profileInputsApplied(profile.id))
            assertTrue(entry.paths().configFile.readText().contains("extension-standard.test"))
            val validScript = "function main(config) { config.rules.unshift('DOMAIN,extension-script.test,REJECT'); return config; }"
            val script = scripts.create("扩展核心验收临时脚本", validScript); scriptId = script.id
            repository.saveExtension(profile.id, "script", script.id, null, own)
            val standardApplied = entry.paths().configFile.readText()
            runtime.getProxyGroups().getOrThrow()
            assertTrue("切换模式后后台刷新仍应沿用已应用输入", standardApplied == entry.paths().configFile.readText())
            step("apply-script") { runtime.applyCurrentProfile(profile.id) }
            val applied = entry.paths().configFile.readText()
            assertTrue(applied.contains("extension-script.test")); assertFalse(applied.contains("extension-standard.test"))
            scripts.update(script.id, script.label, "function main(config) { throw new Error('extension-test'); }")
            assertTrue(runCatching { runtime.applyCurrentProfile(profile.id) }.isFailure)
            assertTrue("脚本失败必须恢复旧输入", applied == entry.paths().configFile.readText())
            assertFalse(runtime.profileInputsApplied(profile.id))
            scripts.update(script.id, script.label, validScript)
            step("retry-script") { runtime.applyCurrentProfile(profile.id) }
            assertTrue(runtime.profileInputsApplied(profile.id))
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            assertTrue(file.readBytes().contentEquals(bytes)); assertEquals(modified, file.lastModified())
        } finally {
            // 作者 long：真实核心只加载授权文件的暂存版本，不启监听/VPN；先恢复正式选择，再回收本用例唯一创建的行和文件。
            step("restore-original") { runtime.selectProfile(selected, repository::selectProfile) }
            check(profile.id !in before && entry.database().profiles().getById(profile.id)?.label == "扩展核心验收临时配置")
            repository.deleteProfile(profile.id)
            scriptId?.let { id ->
                check(entry.database().scripts().getById(id)?.label == "扩展核心验收临时脚本")
                scripts.delete(id)
            }
            assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        }
    }

}
