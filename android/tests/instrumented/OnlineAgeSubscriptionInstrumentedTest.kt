package com.lonnnnnng.flclash

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.data.toDomain
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 作者 long：在线密文必须经过正式表单、Repository、Bridge与CoreRuntimeManager，不能用静态密文或替身证明导入。 */
@RunWith(AndroidJUnit4::class)
class OnlineAgeSubscriptionInstrumentedTest {
    @get:Rule(order = 0)
    val notificationPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private data class Input(
        val token: String,
        val url: String,
        val identity: String,
        val wrongIdentity: String,
        val cipherSha256: String,
        val plainSha256: String,
    )

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun readInput(): Input {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashOnlineAgeAcceptance"))
        assertPortraitDefaultFontConfiguration()
        val path = checkNotNull(args.getString("kclashOnlineAgeInput"))
        assertTrue(path, Regex("/data/local/tmp/kclash-online-age-[a-f0-9]{32}\\.json").matches(path))
        val bytes = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat -- $path").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        assertEquals(args.getString("kclashOnlineAgeInputSha256"), sha(bytes))
        val json = JSONObject(bytes.decodeToString())
        val input = Input(json.getString("token"), json.getString("authorizedUrl"), json.getString("identity").trim(),
            json.getString("wrongIdentity").trim(), json.getString("cipherSha256"), json.getString("plainSha256"))
        assertTrue(input.token, input.token.matches(Regex("[a-f0-9]{32}")))
        assertEquals(args.getString("kclashOnlineAgeUrlSha256"), sha(input.url.toByteArray()))
        assertEquals(args.getString("kclashOnlineAgeCipherSha256"), input.cipherSha256)
        assertEquals(args.getString("kclashOnlineAgePlainSha256"), input.plainSha256)
        assertTrue(input.identity.startsWith("AGE-SECRET-KEY-1"))
        assertTrue(input.wrongIdentity.startsWith("AGE-SECRET-KEY-1"))
        assertTrue("测试身份必须不同", input.identity != input.wrongIdentity)
        return input
    }

    private fun profileFiles(root: File) = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to "${it.length()}:${it.lastModified()}:${sha(it.readBytes())}"
    }

    private fun report(value: JSONObject) = InstrumentationRegistry.getInstrumentation().sendStatus(2,
        android.os.Bundle().apply { putString("kclash_online_age", value.toString()) })

    @Test(timeout = 300_000)
    fun authorizedAgeSubscriptionDownloadsDecryptsImportsAndAppliesThroughTheCore() {
        val input = readInput()
        assertEquals("com.lonnnnnng.flclash.motion", compose.activity.applicationInfo.packageName)
        val app = compose.activity.application as KClashApplication
        runBlocking(Dispatchers.IO) { app.awaitRuntimeInitialization() }
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val database = entry.database()
        val runtime = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertTrue("独立motion库必须为空，避免接触正式应用资料", runBlocking(Dispatchers.IO) { database.profiles().getAll() }.isEmpty())
        val originalRows = runBlocking(Dispatchers.IO) { database.profiles().getAll() }
        val profileRoot = File(compose.activity.filesDir, "profiles")
        val originalFiles = profileFiles(profileRoot)
        val oldSelection = repository.currentProfileId.value
        val oldVpn = runBlocking(Dispatchers.IO) { entry.vpnSettingsStore().read() }
        val testLabel = "在线age-${input.token.take(8)}"
        var importedId: Long? = null
        var testStage = "download-preflight"
        try {
            val payload = runBlocking(Dispatchers.IO) { entry.subscriptionClient().download(input.url).bytes }
            assertTrue("授权URL当前响应必须是age armor", com.lonnnnnng.flclash.data.isAgeEncryptedProfile(payload))
            assertEquals(input.cipherSha256, sha(payload))
            report(JSONObject().put("phase", testStage).put("ageArmor", true).put("cipherBytes", payload.size)
                .put("cipherSha256", sha(payload)))

            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profiles_empty_add").performClick()
            compose.onNodeWithTag("profile_add_source_remote").performClick()
            compose.onNodeWithTag("profile_add_remote_label").performTextInput(testLabel)
            compose.onNodeWithTag("profile_add_remote_url").performScrollTo().performTextInput(input.url)
            compose.onNodeWithTag("profile_add_submit").performClick()
            testStage = "missing-key-prompt"
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("profile_age_required_key").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("缺钥流程不得修改 Profile", originalRows == runBlocking(Dispatchers.IO) { database.profiles().getAll() })
            assertEquals(originalFiles, profileFiles(profileRoot))
            report(JSONObject().put("phase", testStage).put("passed", true).put("profilesUnchanged", true))

            testStage = "wrong-key-rejection"
            compose.onNodeWithTag("profile_age_required_key").performTextInput(input.wrongIdentity)
            compose.onNodeWithText("解密并导入").performClick()
            compose.waitUntil(90_000) {
                compose.onAllNodesWithTag("profile_add_failure").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("错钥流程不得新增 Profile", runBlocking(Dispatchers.IO) { database.profiles().getAll() }.isEmpty())
            assertEquals(originalFiles, profileFiles(profileRoot))
            report(JSONObject().put("phase", testStage).put("passed", true).put("profilesUnchanged", true))

            testStage = "correct-key-import"
            compose.onNodeWithTag("profile_add_remote_age_key").performScrollTo().performTextReplacement(" ${input.identity} ")
            compose.onNodeWithTag("profile_add_submit").performClick()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithTag("profile_add_result").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("订阅已添加").fetchSemanticsNodes().isNotEmpty()
            }
            val imported = runBlocking(Dispatchers.IO) { database.profiles().getAll().single().toDomain() }
            importedId = imported.id
            assertEquals(ProfileType.URL, imported.type)
            assertTrue("导入后订阅地址未保持", input.url == imported.url)
            assertTrue("导入后 age 密钥未保持", input.identity == imported.ageSecretKey)
            assertEquals(imported.id, repository.currentProfileId.value)
            val plaintext = runBlocking(Dispatchers.IO) { repository.profileFile(imported.id).readBytes() }
            assertFalse(com.lonnnnnng.flclash.data.isAgeEncryptedProfile(plaintext))
            assertEquals(input.plainSha256, sha(plaintext))
            assertNull(imported.lastUpdateError)
            report(JSONObject().put("phase", testStage).put("passed", true).put("profileId", imported.id)
                .put("plaintextBytes", plaintext.size).put("plaintextSha256", sha(plaintext))
                .put("storedKeyMatches", true).put("currentProfileMatches", true))

            compose.onNodeWithTag("profile_add_done").performClick()
            testStage = "core-apply"
            runBlocking(Dispatchers.IO) {
                runtime.setVpnEnabled(false).getOrThrow()
                runtime.startProxy().getOrThrow()
            }
            assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
            assertNotNull(runtime.runningSinceElapsedMillis.value)
            val groups = runBlocking(Dispatchers.IO) { entry.coreBridge().getProxyGroups() }
            assertTrue(groups.groups.isNotEmpty())
            val coreEpoch = runBlocking(Dispatchers.IO) { entry.coreBridge().ipRouteStamp().epoch }
            report(JSONObject().put("phase", testStage).put("running", true).put("groupCount", groups.groups.size)
                .put("runningSinceElapsedMs", runtime.runningSinceElapsedMillis.value)
                .put("vpnEnabled", runtime.vpnSettings.value.enabled).put("configEpoch", coreEpoch))

            testStage = "core-routed-authorized-request"
            val routed = runBlocking(Dispatchers.IO) { acceptedProxyRequest(entry, URI(input.url)) { _, _ -> } }
            assertTrue("经核心代理的请求未成功", routed.optBoolean("passed"))
            assertEquals(200, routed.optInt("http"))
            assertTrue(routed.optBoolean("positiveTraffic"))
            assertTrue(routed.optBoolean("hasNonDirectChain"))
            report(JSONObject().put("phase", testStage).put("passed", true).put("http", routed.optInt("http"))
                .put("positiveTraffic", routed.optBoolean("positiveTraffic"))
                .put("hasNonDirectChain", routed.optBoolean("hasNonDirectChain"))
                .put("chainHashes", routed.optJSONArray("chainHashes")))
        } catch (failure: Throwable) {
            report(JSONObject().put("phase", "failed").put("stage", testStage).put("type", failure.javaClass.simpleName))
            throw failure
        } finally {
            val cleanupFailures = mutableListOf<String>()
            runBlocking(NonCancellable + Dispatchers.IO) {
                try {
                    runtime.stop().getOrThrow()
                } catch (failure: Throwable) {
                    cleanupFailures += "stop:${failure.javaClass.simpleName}"
                }
                try {
                    // 作者 long：即使导入后的回读断言先失败，也按本次随机标签找回临时配置并避免误删其他行。
                    val testRows = database.profiles().getAll().filter { it.label == testLabel }
                    check(testRows.all { it.url == input.url })
                    testRows.forEach { repository.deleteProfile(it.id) }
                    check(importedId == null || testRows.any { it.id == importedId })
                } catch (failure: Throwable) {
                    cleanupFailures += "profile:${failure.javaClass.simpleName}"
                }
                try {
                    runtime.setVpnEnabled(oldVpn).getOrThrow()
                } catch (failure: Throwable) {
                    cleanupFailures += "vpn:${failure.javaClass.simpleName}"
                }
                try {
                    check(originalRows == database.profiles().getAll())
                    check(oldSelection == repository.currentProfileId.value)
                    check(originalFiles == profileFiles(profileRoot))
                    check(runtime.state.value == CoreRuntimeStatus.STOPPED)
                } catch (failure: Throwable) {
                    cleanupFailures += "verify:${failure.javaClass.simpleName}"
                }
            }
            if (cleanupFailures.isNotEmpty()) throw AssertionError("age 验收清理失败: ${cleanupFailures.joinToString()}")
        }
    }
}
