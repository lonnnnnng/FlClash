package com.lonnnnnng.flclash

import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import com.lonnnnnng.flclash.ui.recordVisualPng
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProfileFileImportSafInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val entry get() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private val repository get() = entry.profileRepository()
    private fun rows() = runBlocking(Dispatchers.IO) { entry.database().profiles().getAll() }

    @Test fun newPlainFileCancelsPreviewsAndImportsExactAuthorizedSource() {
        val source = authorizedSource()
        val before = rows().map { it.id }.toSet()
        val selected = repository.currentProfileId.value
        try {
            launchPicker(); waitForPicker(); shell("input keyevent KEYCODE_BACK")
            waitFor { root()?.packageName?.toString() == compose.activity.packageName }
            assertEquals(before, rows().map { it.id }.toSet())
            launchPicker(); choose("1.yaml")
            compose.onNodeWithTag("profile_file_import_confirm").assertIsEnabled()
            compose.onNodeWithText("YAML 格式校验通过").assertIsDisplayed()
            assertEquals(before, rows().map { it.id }.toSet()); capture("profile_new_file_preview")
            compose.onNodeWithTag("profile_file_import_cancel").performClick()
            assertEquals(before, rows().map { it.id }.toSet())
            launchPicker(); choose("1.yaml")
            compose.onNodeWithTag("profile_file_import_confirm").performClick()
            compose.waitUntil(15_000) { rows().count { it.id !in before } == 1 }
            val imported = rows().single { it.id !in before }
            assertEquals(ProfileType.FILE, imported.type)
            assertTrue(imported.ageSecretKey.isEmpty())
            assertTrue("新增缓存必须为授权原文件字节", source.contentEquals(repository.profileFile(imported.id).readBytes()))
            assertEquals(selected, repository.currentProfileId.value)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_file_import_confirm").fetchSemanticsNodes().isEmpty() }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profile_card_${imported.id}").assertExists()
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally { removeOnlyCreated(before, setOf("1"), selected) }
    }

    @Test fun encryptedSafWrongKeyRetriesThenRealBridgeImportsAndPersistsKey() {
        val source = authorizedSource()
        val manifest = manifest()
        val filename = manifest.getString("fileName")
        check(filename.matches(Regex("kclash-age-[a-f0-9]+\\.age")))
        val ciphertext = shell("cat /sdcard/Download/$filename")
        assertEquals(manifest.getString("ciphertextSha256"), hash(ciphertext))
        assertTrue(isAgeEncryptedProfile(ciphertext))
        val before = rows().map { it.id }.toSet()
        val selected = repository.currentProfileId.value
        try {
            launchPicker(); choose(filename)
            compose.onNodeWithText("age 加密配置，导入时输入密钥并校验").assertIsDisplayed()
            compose.onNodeWithTag("profile_file_import_confirm").performClick()
            compose.onNodeWithText("解密并导入").assertIsNotEnabled()
            capture("profile_new_age_key_empty")
            compose.onNodeWithTag("profile_age_required_key").performTextInput(manifest.getString("wrongKey"))
            compose.onNodeWithText("解密并导入").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_age_required_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_age_required_error").assertTextContains("age 解密失败", substring = true)
            assertEquals(before, rows().map { it.id }.toSet()); capture("profile_new_age_wrong_key")
            compose.onNodeWithTag("profile_age_required_key").performTextInput(manifest.getString("key"))
            compose.onNodeWithText("解密并导入").performClick()
            compose.waitUntil(15_000) { rows().count { it.id !in before } == 1 }
            val imported = rows().single { it.id !in before }
            assertEquals(ProfileType.FILE, imported.type)
            assertTrue("密钥应保存供文件后续编辑使用", imported.ageSecretKey == manifest.getString("key"))
            assertTrue("真实 Android Bridge 解密后必须保留原字节", source.contentEquals(repository.profileFile(imported.id).readBytes()))
            assertEquals(selected, repository.currentProfileId.value)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_age_required_key").fetchSemanticsNodes().isEmpty() }
            compose.activityRule.scenario.recreate()
            val restored = rows().single { it.id == imported.id }
            assertTrue(restored.ageSecretKey == manifest.getString("key"))
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profile_actions_${imported.id}").performTouchInput { longClick() }
            compose.onNodeWithText("编辑").performClick()
            compose.onNodeWithTag("file_profile_replace_pick").performClick()
            choose(filename, "file_profile_replacement_clear")
            assertTrue("确认替换前仍保留旧明文", source.contentEquals(repository.profileFile(imported.id).readBytes()))
            compose.onNodeWithTag("file_profile_editor_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("file_profile_editor_save").fetchSemanticsNodes().isEmpty() }
            assertTrue("加密 SAF 替换使用已保存密钥解密", source.contentEquals(repository.profileFile(imported.id).readBytes()))
            assertTrue(rows().single { it.id == imported.id }.ageSecretKey == manifest.getString("key"))
            assertTrue(compose.activity.cacheDir.listFiles().orEmpty().none { it.name.startsWith("age-import-") })
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally { removeOnlyCreated(before, setOf(filename.removeSuffix(".age")), selected) }
    }

    @Test fun realAgeRepositoryRejectsWrongIdentityAndLateGuardWithoutChangingCache() {
        val source = authorizedSource(); val manifest = manifest()
        val filename = manifest.getString("fileName"); check(filename.matches(Regex("kclash-age-[a-f0-9]+\\.age")))
        val originalCiphertext = shell("cat /sdcard/Download/$filename")
        var ciphertext = originalCiphertext
        val other = shell("cat ${manifestDirectory()}/other.age")
        assertEquals(manifest.getString("otherCiphertextSha256"), hash(other))
        val context = compose.activity.applicationContext
        val directory = File(context.cacheDir, "real-age-repository-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext() = this; override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val store = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String) = SubscriptionDownload(ciphertext, null, SubscriptionInfo())
        }, contentDecoder = AgeProfileContentDecoder(directory, CoreBridge()::decryptConfig))
        try {
            runBlocking(Dispatchers.IO) {
                assertTrue(runCatching { store.addFileProfile("错误密钥", ciphertext, manifest.getString("wrongKey")) }.isFailure)
                assertTrue(database.profiles().getAll().isEmpty())
                assertTrue(runCatching { store.importFileProfile("迟到导入", ciphertext, manifest.getString("key")) { false } }.isFailure)
                assertTrue(database.profiles().getAll().isEmpty())
                assertTrue(directory.listFiles().orEmpty().none { it.isFile })
                var guardCalls = 0
                assertTrue(runCatching { store.importFileProfile("提交后守卫", ciphertext, manifest.getString("key")) { ++guardCalls < 3 } }.isFailure)
                assertEquals(3, guardCalls)
                assertTrue(database.profiles().getAll().isEmpty())
                assertTrue("事务回滚不得残留新正文", directory.walkTopDown().none { it.isFile })
                val profile = store.addRemoteProfile("https://example.test/local-only", "真实 age 更新验收", manifest.getString("key"))
                val file = store.profileFile(profile.id); val modified = file.lastModified()
                assertTrue(source.contentEquals(file.readBytes()))
                ciphertext = other
                assertTrue(runCatching { store.updateRemoteProfile(profile.id) }.isFailure)
                assertTrue(source.contentEquals(file.readBytes())); assertEquals(modified, file.lastModified())
                assertEquals("age 解密失败，请检查密钥与配置文件", database.profiles().getById(profile.id)?.lastUpdateError)
                assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("age-import-") })
                ciphertext = originalCiphertext
                assertEquals(1, store.updateDueProfiles(Long.MAX_VALUE).succeeded)
                assertTrue(source.contentEquals(file.readBytes()))
                assertTrue(database.profiles().getById(profile.id)?.ageSecretKey == manifest.getString("key"))
            }
        } finally {
            database.close()
            // 作者 long：仅回收本测试独占目录；真实密文只在临时目录解密，正式 Profile、授权原文件不参与写入。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("real-age-repository-")); directory.deleteRecursively()
        }
    }

    @Test(timeout = 300_000)
    fun customSafReplacementKeepsCanceledDraftAndAppliesCachedRuleDirectory() {
        assumeTrue(args.getString("kclashCustomSafBoundary") == "true")
        check(compose.activity.packageName == "com.lonnnnnng.flclash.motion")
        val filename = checkNotNull(args.getString("kclashSafName"))
        require(filename.matches(Regex("kclash-custom-[a-f0-9]{12}\\.yaml")))
        val prefix = filename.removeSuffix(".yaml")
        val bytes = shell("cat /sdcard/Download/$filename")
        check(hash(bytes) == args.getString("kclashLocalFileSha256"))
        val archive = shell("cat /data/local/tmp/$prefix.zip")
        check(hash(archive) == args.getString("kclashRuleArchiveSha256"))
        val input = JSONObject(shell("cat /data/local/tmp/$prefix.json").decodeToString())
        val target = java.net.URI(input.getString("url"))
        check(hash(target.toString().toByteArray()) == args.getString("kclashTargetSha256"))
        fun report(stage: String, vararg fields: Pair<String, Any>) {
            val json = JSONObject().put("stage", stage)
            fields.forEach { (key, value) -> json.put(key, value) }
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("kclash_custom_saf", json.toString()) })
        }
        val runtime = entry.runtimeManager()
        runBlocking { (compose.activity.application as KClashApplication).awaitRuntimeInitialization() }
        check(runtime.state.value == CoreRuntimeStatus.STOPPED && rows().isEmpty())
        val root = ProxyGroupDrafts.root(bytes.decodeToString())
        val definitions = root.getAsJsonObject("rule-providers")
        val sourceNodes = root.getAsJsonArray("proxies")
        val oldVpn = runtime.vpnSettings.value.enabled
        val oldMode = runtime.outboundMode.value
        val ruleStore = entry.rules()
        val custom = runBlocking(Dispatchers.IO) {
            check(repository.localProxies().isEmpty() && ruleStore.all().isEmpty())
            // 作者 long：缓存由原配置 URL 对应的真实正文填充，隔离网络波动，文件选择和提交仍由生产界面完成。
            val byKey = definitions.entrySet().associateBy { hash(it.value.asJsonObject["url"].asString.toByteArray()) }
            require(byKey.size == definitions.size() && byKey.size > 64)
            java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
                val seen = mutableSetOf<String>()
                while (true) {
                    val item = zip.nextEntry ?: break
                    val (name, definition) = byKey.getValue(item.name)
                    check(seen.add(item.name))
                    val saved = ruleStore.save(name, definition.asJsonObject["url"].asString, readProfileFileBytes(zip))
                    check(saved.format == "mrs" && saved.behavior == definition.asJsonObject["behavior"].asString)
                }
                check(seen == byKey.keys)
            }
            repository.addCustomProfile("语义验收-$prefix")
        }
        val cache = runBlocking { ruleStore.all() }
        fun cacheStamps() = cache.map { row ->
            ruleStore.file(row).let { file -> Triple(file.length(), file.lastModified(), hash(file.readBytes())) }
        }
        val stamps = cacheStamps()
        val originalRows = rows()
        val file = repository.profileFile(custom.id)
        val originalBody = hash(file.readBytes()) to file.lastModified()
        fun pickSource() {
            compose.onNodeWithTag("custom_profile_import").performClick()
            compose.onNodeWithText("从文件导入").performClick()
            choose(filename, "custom_import_replace_confirm")
        }
        try {
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_card_${custom.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_card_${custom.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_edit_${custom.id}").performClick()
            pickSource()
            compose.onNodeWithText("取消").performClick()
            check(rows() == originalRows && repository.currentProfileId.value == custom.id)
            check((hash(file.readBytes()) to file.lastModified()) == originalBody)
            check(runBlocking { repository.localProxies().isEmpty() && ruleStore.all() == cache } && cacheStamps() == stamps)
            report("cancel", "unchanged" to true, "cachedProviders" to cache.size)
            pickSource()
            compose.onNodeWithTag("custom_import_replace_confirm").performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("custom_import_replace_confirm").fetchSemanticsNodes().isEmpty() }
            runBlocking(Dispatchers.IO) {
                val stored = ProxyGroupDrafts.root(file.readText())
                val refs = stored.getAsJsonObject("rule-providers")
                check(refs.size() == definitions.size() && cache.all { RuleResources.idOf(refs[it.label]) == it.id })
                check(ruleStore.all() == cache && cacheStamps() == stamps)
                check(rows().single().id == custom.id && repository.currentProfileId.value == custom.id)
                check(repository.localProxies().map { it.name }.toSet() == sourceNodes.map { it.asJsonObject["name"].asString }.toSet())
                val effective = File(repository.activeProfile().filePath).readBytes()
                val projected = ProxyGroupDrafts.root(effective.decodeToString())
                check(projected["proxy-groups"] == root["proxy-groups"] && projected["rules"] == root["rules"])
                val expectedNodes = sourceNodes.associateBy { it.asJsonObject["name"].asString }
                check(projected.getAsJsonArray("proxies").associateBy { it.asJsonObject["name"].asString } == expectedNodes)
                val parsed = com.lonnnnnng.flclash.validation.IsolatedConfigValidator(compose.activity.applicationContext).checkFull(effective)
                check(parsed.passed && parsed.deferredResources == 0)
                report("commit", "grantedRuleFiles" to cache.size, "deferred" to parsed.deferredResources,
                    "nodes" to sourceNodes.size(), "groups" to projected.getAsJsonArray("proxy-groups").size(),
                    "rules" to projected.getAsJsonArray("rules").size())
                checkRuleDescriptorLimit(projected, cache)
                report("descriptor-limit", "allowed" to 128, "rejected" to 129, "followingRequestPassed" to true)
                runtime.setVpnEnabled(false).getOrThrow()
                runtime.startProxy().getOrThrow()
                val leaf = repository.localProxies().single { it.type == "vmess" }
                runtime.setOutboundMode(com.lonnnnnng.flclash.core.CoreOutboundMode.GLOBAL).getOrThrow()
                runtime.changeProxy("GLOBAL", leaf.name).getOrThrow()
                val request = acceptedProxyRequest(entry, target) { _, _ -> }
                val chain = request.optJSONArray("chainHashes")
                val matched = chain != null && (0 until chain.length()).any { chain.getString(it) == hash(leaf.name.toByteArray()) }
                report("traffic", "http" to request.optInt("http"), "sameSocket" to request.optBoolean("attributedSocket"),
                    "positiveTraffic" to request.optBoolean("positiveTraffic"), "expectedLeaf" to matched, "protocol" to leaf.type)
                check(request.optBoolean("passed") && request.optInt("http") == 200 && matched && request.optBoolean("positiveTraffic"))
            }
        } finally {
            runBlocking(Dispatchers.IO) {
                runtime.stop().getOrThrow()
                runtime.setOutboundMode(oldMode).getOrThrow()
                runtime.setVpnEnabled(oldVpn).getOrThrow()
                check(rows().single().id == custom.id)
                repository.deleteProfile(custom.id)
                cache.forEach { ruleStore.delete(it) }
                entry.database().appPreferences().delete(LocalProxies.KEY)
                check(rows().isEmpty() && repository.localProxies().isEmpty() && ruleStore.all().isEmpty())
                check(runtime.state.value == CoreRuntimeStatus.STOPPED)
                report("cleanup", "empty" to true, "stopped" to true)
            }
        }
    }

    private suspend fun checkRuleDescriptorLimit(projected: com.google.gson.JsonObject, cache: List<RuleResource>) {
        val validator = com.lonnnnnng.flclash.validation.IsolatedConfigValidator(compose.activity.applicationContext)
        val candidate = projected.deepCopy()
        val providers = candidate.getAsJsonObject("rule-providers")
        val exemplar = cache.minBy { entry.rules().file(it).length() }
        val template = providers.getAsJsonObject(exemplar.label)
        val created = mutableListOf<File>()
        // 作者 long：沿用原件的真实 MRS，只复制最小资源到独占路径，避免同路径去重掩盖 128/129 个句柄分界。
        fun addDependency() {
            val file = File.createTempFile("semantic-limit-", ".mrs", entry.rules().file(exemplar).parentFile)
            created += file
            entry.rules().file(exemplar).copyTo(file, overwrite = true)
            providers.add(file.nameWithoutExtension, template.deepCopy().apply { addProperty("path", file.path) })
        }
        fun bytes() = com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(candidate.toString()).toByteArray()
        try {
            // 作者 long：四个内置 Geo 文件也占用授权额度，124 个独立规则文件正好触及总上限。
            check(providers.size() in 1..124)
            repeat(124 - providers.size()) { addDependency() }
            check(validator.checkFull(bytes()).let { it.passed && it.deferredResources == 0 })
            addDependency()
            val rejected = runCatching { validator.checkFull(bytes()) }.exceptionOrNull()
            check(rejected is IllegalArgumentException && rejected.message == "配置依赖资源超过校验上限")
            providers.remove(created.last().nameWithoutExtension)
            check(validator.checkFull(bytes()).passed)
        } finally {
            created.forEach { check(it.delete()) }
        }
    }

    private fun authorizedSource(): ByteArray {
        assumeTrue(args.getString("kclashFileImportSaf") == "true")
        assertPortraitDefaultFontConfiguration()
        val bytes = shell("cat /sdcard/Download/1.yaml")
        assertEquals(args.getString("kclashLocalFileSha256"), hash(bytes))
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        return bytes
    }
    private fun manifestDirectory(): String {
        val path = checkNotNull(args.getString("kclashAgeManifest"))
        check(path.matches(Regex("/data/local/tmp/kclash-age-[a-f0-9]+/manifest\\.json")))
        return path.substringBeforeLast('/')
    }
    private fun manifest() = JSONObject(shell("cat ${manifestDirectory()}/manifest.json").toString(Charsets.UTF_8)).also {
        assertEquals(args.getString("kclashLocalFileSha256"), it.getString("plaintextSha256"))
    }
    private fun removeOnlyCreated(before: Set<Long>, labels: Set<String>, selected: Long?) = runBlocking(Dispatchers.IO) {
        // 作者 long：只删除本次新增且名称、类型均匹配的临时配置，失败路径也保留全部原 ID 和原选择。
        entry.database().profiles().getAll().filter { it.id !in before && it.type == ProfileType.FILE && it.label in labels }
            .forEach { repository.deleteProfile(it.id) }
        assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
        assertEquals(selected, repository.currentProfileId.value)
    }
    private fun launchPicker() {
        compose.onNodeWithTag("nav_profiles").performClick()
        compose.onNodeWithTag("profiles_add_fab").performClick()
        compose.onNodeWithTag("profile_add_source_file").performClick()
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun root() = instrumentation.uiAutomation.rootInActiveWindow
    private fun waitForPicker() = waitFor { root()?.packageName?.toString()?.endsWith("documentsui") == true }
    private fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node); for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) { if (current?.isClickable == true && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; current = current?.parent }
        return false
    }
    private fun choose(filename: String, returnTag: String = "profile_file_import_confirm") {
        waitForPicker()
        fun chooseFile() = root()?.findAccessibilityNodeInfosByText(filename)?.firstOrNull { it.text?.toString() == filename && it.isVisibleToUser }?.let(::click) == true
        if (!chooseFile()) {
            val current = checkNotNull(root())
            val drawer = current.findAccessibilityNodeInfosByViewId("android:id/home").firstOrNull()
                ?: nodes(current).firstOrNull { it.contentDescription?.toString()?.let { value -> value.contains("根目录") || value.contains("Show roots", true) || value.contains("navigation", true) } == true }
            check(drawer != null && click(drawer)) { "系统文件选择器缺少目录入口" }
            waitFor { nodes(root() ?: return@waitFor false).firstOrNull { it.isVisibleToUser && (it.text?.toString()?.startsWith("下载") == true || it.text?.toString().equals("Downloads", true)) }?.let(::click) == true }
            waitFor(::chooseFile)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(returnTag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) { if (condition()) return; SystemClock.sleep(100) }
        error("系统文件选择器未在规定时间内完成")
    }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
    }
}
