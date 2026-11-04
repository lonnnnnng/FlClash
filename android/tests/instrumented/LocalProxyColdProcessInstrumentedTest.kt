package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式入口分两进程验收；恢复阶段不调用open，避免主动初始化掩盖密文草稿丢失。 */
class LocalProxyColdProcessInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "local-proxy-cold.json")
    private fun scenario(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProfileClosureAcceptance"))
        val case = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashEditorCase"))
        require(case in setOf("proxies", "proxies-conflict", "local-node", "local-map", "local-list", "local-links-single", "local-links-batch", "local-replacement", "local-conflict", "local-selection"))
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_editor_process_pid", android.os.Process.myPid().toString()) })
        return case
    }

    @Test fun prepareEncryptedDraftWithoutCommitting(): Unit = runBlocking {
        val case = scenario(); val e = entry(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先收尾上一场冷进程验收", marker().exists())
        assertNull("不能覆盖用户配置草稿", e.profileDraftStore().read())
        assertNull("不能覆盖用户节点草稿", e.localProxyDraftStore().read())
        repo.refreshCurrentProfile()
        val profiles = e.database().profiles().getAll().sortedBy { it.id }
        val local = repo.localProxies()
        val record = JsonObject().apply {
            addProperty("case", case); addProperty("preparedPid", android.os.Process.myPid())
            addProperty("selected", repo.currentProfileId.value)
            addProperty("profiles", digest(Gson().toJson(profiles).toByteArray()))
            addProperty("files", profileFiles(profiles.map { it.id }))
            addProperty("local", digest(Gson().toJson(local).toByteArray()))
        }
        // 作者 long：marker仅记录摘要和本测试ID；目录和原配置中的凭据不得为夹具另存明文副本。
        marker().writeText(record.toString())
        if (case.startsWith("proxies")) {
            val id = System.nanoTime().and(Long.MAX_VALUE)
            val profile = Profile.custom(id, "cold-proxies").copy(orderIndex = profiles.size)
            e.database().profiles().insert(profile.toEntity())
            repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(readAuthorizedProxyProfile()) }
            record.addProperty("profileId", id); marker().writeText(record.toString())
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(profile) }
            compose.waitUntil(20_000) { model.editorState.value.overview != null }
            compose.runOnIdle { model.openProxiesEditor(profile) }
            compose.waitUntil(20_000) { model.proxiesEditorState.value.profile?.id == id && !model.proxiesEditorState.value.isLoading }
            val text = compose.onNodeWithTag("profile_proxies_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text + "\n# $PRIVATE"
            compose.onNodeWithTag("profile_proxies_content").performTextReplacement(text)
            compose.onNodeWithTag("profile_proxies_content").performTextInputSelection(androidx.compose.ui.text.TextRange(text.length - 7, text.length - 2))
            closeSoftKeyboard()
            compose.onNodeWithTag("profile_proxies_back").performClick()
            compose.onNodeWithTag("profile_proxies_discard").assertIsDisplayed()
            record.addProperty("text", digest(text.toByteArray()))
            withTimeout(15_000) { while (true) {
                val saved = e.profileDraftStore().read()?.let { Gson().fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java) }
                if (saved?.proxies?.uiDraft != null && saved.proxies.uiDraft == model.proxiesEditorState.value.uiDraft && Gson().toJson(saved.proxies.uiDraft).contains(PRIVATE)) break
                delay(100)
            } }
            if (case == "proxies-conflict") repo.profileFile(id).appendText("\n# external-after-draft\n")
            record.addProperty("body", digest(repo.profileFile(id).readBytes())); record.addProperty("mtime", repo.profileFile(id).lastModified())
        } else {
            val ownId = "cold-local-${System.nanoTime()}"
            val fields = ProxyGroupDrafts.root(readAuthorizedProxyProfile().toString(Charsets.UTF_8)).getAsJsonArray("proxies").first().asJsonObject.deepCopy().apply {
                // 作者 long：节点源沿用授权原文件，但本组只验草稿；清除无关可选字段，截图不能带出原服务器路径或认证参数。
                keySet().toList().filterNot { it in setOf("name", "type", "server", "port", "password", "username", "headers", "alpn", "udp") }.forEach(::remove)
                addProperty("name", ownId); addProperty("type", if (case == "local-list") "trojan" else "http")
                addProperty("server", "192.0.2.1"); addProperty("port", 443); addProperty("password", "test-only"); addProperty("username", "test-only")
                add("headers", JsonObject().apply { addProperty("Authorization", "initial") })
                add("alpn", JsonArray().apply { add("h2") })
            }
            val row = LocalProxy(ownId, fields.toString())
            record.addProperty("nodeId", ownId); marker().writeText(record.toString())
            repo.saveLocalProxies(local, local + row)
            lateinit var model: LocalProxiesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[LocalProxiesViewModel::class.java] }
            compose.waitUntil(15_000) { !model.state.value.loading && model.state.value.rows.any { it.id == ownId } }
            compose.runOnIdle { model.open() }
            if (case == "local-selection") {
                compose.onNodeWithTag("local_proxies_search").performTextReplacement(ownId)
                compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("local_proxy_$ownId")), useUnmergedTree = true).performClick()
            } else if (case in setOf("local-links-batch", "local-replacement")) {
                compose.onNodeWithTag("local_proxies_links").performClick()
                compose.waitUntil(15_000) { model.state.value.links != null && !model.state.value.busy }
                if (case == "local-replacement") {
                    val links = requireNotNull(model.state.value.links)
                    val renamed = CoreBridge().encodeShareLinks(listOf(fields.deepCopy().apply { addProperty("name", ownId + PRIVATE); addProperty("password", PRIVATE) })).single()
                    val text = links.previous.indices.map { if (links.previous[it].id == ownId) renamed else links.links[it] }.filter(String::isNotEmpty).joinToString("\n")
                    compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(text)
                    compose.onNodeWithTag("local_proxy_links_save").performClick()
                    compose.waitUntil(15_000) { model.state.value.replacement != null && !model.state.value.busy }
                    compose.onNodeWithTag("local_proxy_links_replace").assertIsDisplayed()
                } else {
                    val original = requireNotNull(model.state.value.links).text
                    compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(original + "\ninvalid-$PRIVATE")
                    compose.onNodeWithTag("local_proxy_links_save").performClick()
                    compose.waitUntil(15_000) { model.state.value.error != null && !model.state.value.busy }
                    compose.onNodeWithTag("local_proxy_links_error").assertIsDisplayed()
                }
            } else {
                compose.onNodeWithTag("local_proxies_list").performScrollToNode(hasTestTag("local_proxy_$ownId"))
                compose.onNodeWithTag("local_proxy_$ownId").performClick()
                when (case) {
                    "local-map", "local-list" -> {
                        compose.onNodeWithText(if (case == "local-map") "请求头" else "ALPN", substring = false).performScrollTo().performClick()
                        compose.onNodeWithTag("local_proxy_collection_value_0").performTextReplacement(PRIVATE)
                    }
                    "local-links-single" -> {
                        compose.onActionMarker("从分享链接填写").performClick()
                        compose.waitUntil(15_000) { model.state.value.links != null && !model.state.value.busy }
                        compose.onNodeWithTag("local_proxy_links_content").performTextReplacement("invalid-$PRIVATE")
                        compose.onNodeWithTag("local_proxy_links_save").performClick()
                        compose.waitUntil(15_000) { model.state.value.error != null && !model.state.value.busy }
                    }
                    else -> compose.onNodeWithTag("local_proxy_field_password").performScrollTo().performTextReplacement(PRIVATE)
                }
            }
            closeSoftKeyboard()
            compose.waitUntil(15_000) { model.state.value.uiDraft != null }
            withTimeout(15_000) { while (true) {
                val saved = e.localProxyDraftStore().read()?.let { Gson().fromJson(it.toString(Charsets.UTF_8), LocalProxyDraftSnapshot::class.java) }
                val state = model.state.value
                if (saved != null && saved.state == state.copy(rows = emptyList()) && (case == "local-selection" || Gson().toJson(saved).contains(PRIVATE))) break
                delay(100)
            } }
            if (case == "local-conflict") {
                val extra = row.copy(id = ownId + "-external", json = fields.deepCopy().apply { addProperty("name", ownId + "-external") }.toString())
                repo.saveLocalProxies(local + row, local + row + extra)
            }
            record.addProperty("directory", digest(Gson().toJson(repo.localProxies()).toByteArray()))
        }
        marker().writeText(record.toString())
        assertPrivateValueAbsentFromBundleAndCiphertext(if (case.startsWith("proxies")) "profile-editor-draft.v1" else "local-proxy-draft.v1")
        capture("${case}_prepared")
    }

    @Test fun verifyAutomaticRecoveryAndDiscardWithoutChangingUserData(): Unit = runBlocking {
        val case = scenario(); val e = entry(); val repo = e.profileRepository()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record["case"].asString)
        assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        if (case.startsWith("proxies")) {
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
            val id = record["profileId"].asLong
            compose.waitUntil(20_000) { model.proxiesEditorState.value.profile?.id == id }
            compose.onNodeWithTag("profile_proxies_discard").assertIsDisplayed()
            capture("${case}_restored")
            compose.onNodeWithText("继续编辑", substring = false).performClick()
            val content = compose.onNodeWithTag("profile_proxies_content").fetchSemanticsNode().config
            assertEquals(record["text"].asString, digest(content[SemanticsProperties.EditableText].text.toByteArray()))
            val length = content[SemanticsProperties.EditableText].text.length
            assertEquals(androidx.compose.ui.text.TextRange(length - 7, length - 2), content[SemanticsProperties.TextSelectionRange])
            if (case == "proxies-conflict") {
                compose.onNodeWithTag("profile_proxies_save").performClick()
                compose.waitUntil(15_000) { !model.proxiesEditorState.value.isSaving && model.proxiesEditorState.value.error != null }
                compose.onNodeWithTag("profile_proxies_error").assertIsDisplayed()
                capture("${case}_rejected")
            }
            compose.onNodeWithTag("profile_proxies_back").performClick()
            compose.onNodeWithTag("profile_proxies_discard").performClick()
            compose.waitUntil(15_000) { model.proxiesEditorState.value.profile == null }
            assertEquals(record["body"].asString, digest(repo.profileFile(id).readBytes()))
            assertEquals(record["mtime"].asLong, repo.profileFile(id).lastModified())
            compose.runOnIdle { model.closeEditor() }
            awaitEmpty(e.profileDraftStore(), "profile-editor-draft.v1")
            repo.deleteProfile(id)
        } else {
            lateinit var model: LocalProxiesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[LocalProxiesViewModel::class.java] }
            compose.waitUntil(20_000) { model.state.value.isOpen && !model.state.value.loading }
            val id = record["nodeId"].asString
            when (case) {
                "local-selection" -> {
                    compose.onNodeWithTag("local_proxies_search").assertTextContains(id)
                    compose.onNodeWithText("已选择 1 项", substring = false).assertIsDisplayed()
                    compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("local_proxy_$id")), useUnmergedTree = true).assertIsOn()
                    capture("${case}_restored")
                    compose.onNodeWithTag("local_proxies_back").performClick()
                }
                "local-replacement" -> {
                    compose.onNodeWithTag("local_proxy_links_replace").assertIsDisplayed()
                    capture("${case}_restored")
                    compose.onNodeWithText("继续编辑", substring = false).performClick()
                    discardLinks()
                }
                "local-links-single", "local-links-batch" -> {
                    compose.onNodeWithTag("local_proxy_links_content").assertTextContains(PRIVATE, substring = true)
                    compose.onNodeWithTag("local_proxy_links_error").assertIsDisplayed()
                    capture("${case}_restored")
                    discardLinks()
                    if (case == "local-links-single") compose.onNodeWithTag("local_proxy_editor_back").performClick()
                }
                else -> {
                    if (case in setOf("local-map", "local-list")) {
                        compose.onNodeWithTag("local_proxy_collection_value_0").assertTextContains(PRIVATE)
                        capture("${case}_restored")
                        compose.onNodeWithTag("local_proxy_collection_back").performClick()
                    } else {
                        assertEquals(PRIVATE, LocalProxies.value(requireNotNull(model.state.value.editor).value.fields, "password")?.asString)
                        capture("${case}_restored")
                    }
                    if (case == "local-conflict") {
                        compose.onNodeWithTag("local_proxy_save").performClick()
                        compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.error != null }
                        assertTrue(model.state.value.error.orEmpty().contains("已变化"))
                        assertNotNull(model.state.value.editor)
                        capture("${case}_rejected")
                    }
                    compose.onNodeWithTag("local_proxy_editor_back").performClick()
                    compose.onNodeWithText("放弃修改", substring = false).performClick()
                }
            }
            compose.waitUntil(15_000) { model.state.value.editor == null && model.state.value.links == null }
            assertEquals(record["directory"].asString, digest(Gson().toJson(repo.localProxies()).toByteArray()))
            compose.onNodeWithTag("local_proxies_back").performClick()
            compose.waitUntil(15_000) { !model.state.value.isOpen }
            awaitEmpty(e.localProxyDraftStore(), "local-proxy-draft.v1")
            // 作者 long：只移除本测试独有ID；恢复不能用整目录旧快照覆盖用户在测试期间的其他写入。
            val current = repo.localProxies()
            repo.saveLocalProxies(current, current.filterNot { it.id == id || it.id == id + "-external" })
        }
        val profiles = e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(record["profiles"].asString, digest(Gson().toJson(profiles).toByteArray()))
        assertEquals(record["files"].asString, profileFiles(profiles.map { it.id }))
        assertEquals(record["selected"]?.takeUnless { it.isJsonNull }?.asLong, repo.currentProfileId.value)
        assertEquals(record["local"].asString, digest(Gson().toJson(repo.localProxies()).toByteArray()))
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertTrue(marker().delete())
    }

    private fun discardLinks() {
        compose.onNodeWithTag("local_proxy_links_back").performClick()
        compose.onNodeWithText("放弃修改", substring = false).performClick()
    }
    private suspend fun awaitEmpty(store: ProfileDraftStore, name: String) {
        withTimeout(15_000) { while (store.read() != null) delay(100) }
        for (suffix in listOf("", ".bak", ".new")) assertFalse("清除后不能残留草稿文件$suffix", File(compose.activity.noBackupFilesDir, name + suffix).exists())
    }
    private fun assertPrivateValueAbsentFromBundleAndCiphertext(name: String) {
        val state = Bundle()
        compose.runOnIdle { instrumentation.callActivityOnSaveInstanceState(compose.activity, state) }
        val parcel = Parcel.obtain()
        val bytes = try { parcel.writeBundle(state); parcel.marshall() } finally { parcel.recycle() }
        assertTrue(bytes.isNotEmpty())
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val needle = PRIVATE.toByteArray(charset).asList()
            assertFalse("未保存凭据不得进入系统Bundle", bytes.asList().windowed(needle.size).any { it == needle })
        }
        assertFalse(File(compose.activity.noBackupFilesDir, name).readBytes().toString(Charsets.ISO_8859_1).contains(PRIVATE))
    }
    private fun profileFiles(ids: List<Long>) = ids.joinToString("|") { id -> entry().profileRepository().profileFile(id).let { "$id:${it.length()}:${it.lastModified()}:${digest(it.readBytes())}" } }
    private fun digest(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun capture(name: String) {
        // 作者 long：真实配置正文和正式目录链接可能含凭据；冷验仅输出断言，排版截图另用脱敏固定宿主。
        if (name.startsWith("proxies") || name.startsWith("local-links") || name.startsWith("local-replacement")) return
        instrumentation.waitForIdleSync()
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng("local_cold_$name", ByteArrayOutputStream().also { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        screen.recycle()
    }
    private companion object { const val PRIVATE = "cold-local-private-20261105" }
}
