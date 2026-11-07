package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：清单摘要绑定本批独占输入，系统选择结果仍由正式Activity回调读取，不注入URI或替代文件读取器。 */
internal object ResourceAcceptanceInputs {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun shell(command: String): ByteArray = instrumentation.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun manifest(): JSONObject {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashResourceSafAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val path = checkNotNull(args.getString("kclashResourceManifest"))
        check(path.matches(Regex("/sdcard/Download/resource-saf-[0-9]+-manifest\\.json")))
        val bytes = shell("cat $path")
        assertEquals(args.getString("kclashResourceManifestSha256"), sha(bytes))
        return JSONObject(bytes.decodeToString()).also { check(path.endsWith("/${it.getString("token")}-manifest.json")) }
    }
    fun name(manifest: JSONObject, key: String): String = manifest.getJSONObject("files").getJSONObject(key).getString("name").also {
        check(it.matches(Regex("${Regex.escape(manifest.getString("token"))}-[A-Za-z]+\\.[a-z]+")))
    }
    fun bytes(manifest: JSONObject, key: String): ByteArray = shell("cat /sdcard/Download/${name(manifest, key)}").also {
        val expected = manifest.getJSONObject("files").getJSONObject(key)
        assertEquals(expected.getInt("bytes"), it.size); assertEquals(expected.getString("sha256"), sha(it))
    }
    fun signature(file: File) = "${file.length()}:${file.lastModified()}:${sha(file.readBytes())}"
    fun report(value: JSONObject) = instrumentation.sendStatus(2, Bundle().apply { putString("kclash_resource_saf", value.toString()) })
}

class ResourceSafHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val entry get() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)

    @Test fun ruleYamlPickerCancelsRejectsEmptyPreviewsAndPersists() = fixture { manifest ->
        open(false)
        launch("rule_resources_add"); cancel(); assertNoNewResources()
        launch("rule_resources_add"); choose(ResourceAcceptanceInputs.name(manifest, "empty"))
        awaitText("无法读取所选文件，请重新选择")
        compose.onNodeWithText("无法读取所选文件，请重新选择").assertIsDisplayed()
        assertNoNewResources(); capture("resource_rule_empty_rejected")
        val name = ResourceAcceptanceInputs.name(manifest, "rules")
        launch("rule_resources_add"); choose(name)
        awaitTag("rule_resource_content")
        compose.onNodeWithTag("rule_resource_content").assertTextContains(ResourceAcceptanceInputs.bytes(manifest, "rules").decodeToString())
        assertNoNewResources(); capture("resource_rule_yaml_preview")
        compose.onNodeWithTag("rule_resource_editor_back").performClick(); assertNoNewResources()
        launch("rule_resources_add"); choose(name)
        awaitTag("rule_resource_save")
        compose.onNodeWithTag("rule_resource_save").performClick()
        compose.waitUntil(30_000) { runBlocking { entry.rules().all().size == beforeRules.size + 1 } }
        val row = runBlocking { entry.rules().all().single { it.id !in beforeRules.map(RuleResource::id) } }
        assertEquals("yaml", row.format); assertEquals("classical", row.behavior); assertTrue(row.editable)
        assertArrayEquals(ResourceAcceptanceInputs.bytes(manifest, "rules"), entry.rules().file(row).readBytes())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("rule_resources_page").assertIsDisplayed()
        assertEquals(row, runBlocking { entry.rules().all().single { it.id == row.id } })
        capture("resource_rule_yaml_saved")
        ResourceAcceptanceInputs.report(JSONObject().put("case", "rule-yaml").put("sha256", ResourceAcceptanceInputs.sha(entry.rules().file(row).readBytes())).put("recreated", true))
    }

    @Test fun ruleMrsPickerPreservesBinaryAndUsesNativePreview() = fixture { manifest ->
        open(false); launch("rule_resources_add"); choose(ResourceAcceptanceInputs.name(manifest, "mrs"))
        awaitTag("rule_resource_preview")
        compose.onNodeWithTag("rule_resource_preview").assertTextEquals("MRS 二进制资源，保存后可预览")
        assertNoNewResources(); compose.onNodeWithTag("rule_resource_save").performClick()
        compose.waitUntil(30_000) { runBlocking { entry.rules().all().size == beforeRules.size + 1 } }
        val row = runBlocking { entry.rules().all().single { it.id !in beforeRules.map(RuleResource::id) } }
        assertEquals("mrs", row.format); assertEquals(manifest.getJSONObject("ruleSource").getString("behavior"), row.behavior)
        assertFalse(row.editable); assertArrayEquals(ResourceAcceptanceInputs.bytes(manifest, "mrs"), entry.rules().file(row).readBytes())
        val preview = runBlocking { entry.rules().content(row) }; assertTrue(preview.isNotBlank())
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("rule_resource_preview").fetchSemanticsNodes().isEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("rule_resources_search").performTextReplacement(row.label)
        compose.onNodeWithTag("rule_resource_menu_${row.id}").performClick()
        compose.onNodeWithText("预览", substring = false).performClick()
        lateinit var model: RuleResourcesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[RuleResourcesViewModel::class.java] }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.editor?.original?.id == row.id }
        awaitTag("rule_resource_preview")
        val shown = compose.onNodeWithTag("rule_resource_preview").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        ResourceAcceptanceInputs.report(JSONObject().put("case", "rule-mrs-preview").put("repositorySha256", ResourceAcceptanceInputs.sha(preview.toByteArray()))
            .put("viewModelSha256", ResourceAcceptanceInputs.sha(model.state.value.editor!!.content.toByteArray())).put("shownSha256", ResourceAcceptanceInputs.sha(shown.toByteArray())))
        assertEquals(ResourceAcceptanceInputs.sha(preview.toByteArray()), ResourceAcceptanceInputs.sha(shown.toByteArray()))
        compose.onNodeWithTag("rule_resource_save").assertDoesNotExist(); capture("resource_rule_mrs_native_preview")
        compose.onNodeWithTag("rule_resource_editor_back").performClick()
        ResourceAcceptanceInputs.report(JSONObject().put("case", "rule-mrs").put("sha256", ResourceAcceptanceInputs.sha(entry.rules().file(row).readBytes())).put("previewBytes", preview.toByteArray().size))
    }

    @Test fun iconSetPickerCancelsRejectsMalformedAndPersists() = fixture { manifest ->
        open(true); launch("icon_sets_add"); cancel(); assertNoNewResources()
        launch("icon_sets_add"); choose(ResourceAcceptanceInputs.name(manifest, "badIcons"))
        awaitText("文件已读取，但处理尚未完成，请重试")
        compose.onNodeWithText("文件已读取，但处理尚未完成，请重试").assertIsDisplayed()
        assertNoNewResources(); capture("resource_icons_invalid_rejected")
        launch("icon_sets_add"); choose(ResourceAcceptanceInputs.name(manifest, "icons"))
        compose.waitUntil(20_000) { runBlocking { entry.icons().all().size == beforeIcons.size + 1 } }
        val row = runBlocking { entry.icons().all().single { it.id !in beforeIcons.map(ResourceIconSet::id) } }
        assertEquals(manifest.getString("token"), row.name); assertEquals(1, row.icons.size)
        compose.activityRule.scenario.recreate(); compose.onNodeWithTag("icon_sets_page").assertIsDisplayed()
        assertEquals(row, runBlocking { entry.icons().all().single { it.id == row.id } }); capture("resource_icons_saved")
        ResourceAcceptanceInputs.report(JSONObject().put("case", "icon-set").put("count", row.icons.size).put("recreated", true))
    }

    @Test fun groupImageGetContentCancelsRejectsInvalidAndPersistsInCustomCopy() = fixture { manifest ->
        val e = entry; val repo = e.profileRepository(); val id = System.nanoTime().and(Long.MAX_VALUE)
        val profile = Profile.custom(id, manifest.getString("token") + "-profile").copy(orderIndex = beforeProfiles.size)
        val original = runBlocking {
            val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
            val source = e.database().profiles().getAll().single { it.type == ProfileType.FILE && ResourceAcceptanceInputs.sha(repo.profileFile(it.id).readBytes()) == expected }
            repo.profileFile(source.id).readBytes()
        }
        lateinit var model: ProfilesViewModel
        try {
            runBlocking { e.database().profiles().insert(profile.toEntity()) }
            repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(original) }
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(profile) }
            compose.waitUntil(20_000) { model.editorState.value.overview != null }
            compose.runOnIdle { model.openProxyGroupsEditor(profile) }
            compose.waitUntil(20_000) { model.proxyGroupsEditorState.value.profile?.id == id && !model.proxyGroupsEditorState.value.isLoading }
            compose.onNodeWithTag("proxy_group_row_0").performClick()
            compose.onNodeWithTag("proxy_group_icon").performScrollTo().performClick()
            val unchanged = ResourceAcceptanceInputs.signature(repo.profileFile(id))
            compose.onNodeWithTag("group_icon_local").performClick(); cancel()
            compose.onNodeWithTag("group_icon_search").assertIsDisplayed()
            assertEquals(unchanged, ResourceAcceptanceInputs.signature(repo.profileFile(id)))
            compose.onNodeWithTag("group_icon_local").performClick(); choose(ResourceAcceptanceInputs.name(manifest, "badImage"))
            awaitTag("group_icon_error")
            compose.onNodeWithTag("group_icon_error").assertIsDisplayed(); capture("resource_group_image_invalid")
            assertEquals(unchanged, ResourceAcceptanceInputs.signature(repo.profileFile(id)))
            compose.onNodeWithTag("group_icon_local").performClick(); choose(ResourceAcceptanceInputs.name(manifest, "oversizedImage"))
            awaitTag("group_icon_error"); capture("resource_group_image_oversized")
            assertEquals(unchanged, ResourceAcceptanceInputs.signature(repo.profileFile(id)))
            compose.onNodeWithTag("group_icon_local").performClick(); systemPicker(); recreateBehindPicker()
            choose(ResourceAcceptanceInputs.name(manifest, "image"))
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("group_icon_search").fetchSemanticsNodes().isEmpty() }
            assertEquals(unchanged, ResourceAcceptanceInputs.signature(repo.profileFile(id)))
            compose.onNodeWithTag("proxy_group_form_back").performClick()
            compose.onNodeWithText("放弃修改").performClick()
            assertEquals(unchanged, ResourceAcceptanceInputs.signature(repo.profileFile(id)))
            compose.onNodeWithTag("proxy_group_row_0").performClick()
            compose.onNodeWithTag("proxy_group_icon").performScrollTo().performClick()
            compose.onNodeWithTag("group_icon_local").performClick(); choose(ResourceAcceptanceInputs.name(manifest, "image"))
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("group_icon_search").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("proxy_group_form_confirm").performClick()
            compose.onNodeWithTag("proxy_group_list_save").performClick()
            compose.waitUntil(30_000) { model.proxyGroupsEditorState.value.profile == null }
            val expected = ProfileGroupIconData.encode(ResourceAcceptanceInputs.bytes(manifest, "image"))
            fun stored() = ProxyGroupDrafts.read(repo.profileFile(id).readText()).first().text("icon")
            assertEquals(expected, stored())
            compose.activityRule.scenario.recreate(); assertEquals(expected, stored())
            ResourceAcceptanceInputs.report(JSONObject().put("case", "group-image").put("dataUriSha256", ResourceAcceptanceInputs.sha(stored().toByteArray())).put("recreated", true))
        } finally {
            var closed = false
            try {
                compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.closeProxyGroupsEditor() }
                compose.waitUntil(15_000) { model.proxyGroupsEditorState.value.profile == null }
                compose.runOnIdle { model.closeEditor() }
                compose.waitUntil(15_000) { model.editorState.value.profile == null }
                closed = true
            } finally {
                runBlocking {
                    val current = repo.recentGroupIcons()
                    val expectedIcon = ProfileGroupIconData.encode(ResourceAcceptanceInputs.bytes(manifest, "image"))
                    check(current == beforeRecent || current == (listOf(expectedIcon) + beforeRecent.filterNot { it == expectedIcon }).take(24))
                    if (beforeHistory == null) e.database().appPreferences().delete("group_icon_history") else e.database().appPreferences().put(beforeHistory!!)
                    if (closed) e.database().profiles().getById(id)?.let { check(it.label == profile.label); repo.deleteProfile(id) }
                    else ResourceAcceptanceInputs.report(JSONObject().put("case", "group-image").put("phase", "cleanup-incomplete").put("retainedProfileId", id))
                }
            }
        }
    }

    @Test fun rulePickerRecreationAndDuplicateLateCallbacksDoNotWrite() = fixture { manifest ->
        open(false); launch("rule_resources_add"); systemPicker()
        lateinit var model: RuleResourcesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[RuleResourcesViewModel::class.java] }
        val token = checkNotNull(model.filePicker.state.value.request).token
        recreateBehindPicker(); systemPicker()
        choose(ResourceAcceptanceInputs.name(manifest,"rules")); awaitTag("rule_resource_content")
        compose.waitUntil(10_000) { !model.state.value.busy }
        compose.onNodeWithTag("rule_resource_content").performTextInputSelection(androidx.compose.ui.text.TextRange(3,11))
        fun tree() = model.state.value.session?.uiDraft?.values?.mapValues { (_, values) ->
            fun scrub(v:EditorSavedValue):String = v.kind+":"+(if(v.kind in setOf("int","long"))v.text else "")+v.children.joinToString(prefix="[",postfix="]"){scrub(it)}
            values.map { scrub(it) }
        }.toString()
        assertEquals(androidx.compose.ui.text.TextRange(3,11),compose.onNodeWithTag("rule_resource_content").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        instrumentation.sendStatus(2,Bundle().apply{putString("kclash_resource_tree_before",tree())})
        compose.activityRule.scenario.recreate()
        instrumentation.sendStatus(2,Bundle().apply{putString("kclash_resource_tree_after",tree())})
        assertEquals(androidx.compose.ui.text.TextRange(3,11),compose.onNodeWithTag("rule_resource_content").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        val same = model.state.value.editor?.session
        compose.runOnIdle { model.receiveFilePick(token,"content://invalid/duplicate") }
        compose.waitForIdle(); assertEquals(same,model.state.value.editor?.session);assertNoNewResources()
        capture("resource_picker_recreated_preview")
        compose.runOnIdle { model.closeEditor() };compose.waitUntil(10_000) { model.state.value.editor==null }
        compose.runOnIdle { model.receiveFilePick(token,"content://invalid/late") }
        compose.waitForIdle();assertNull(model.state.value.editor);assertNoNewResources()
    }

    @Test fun iconPickerRecreationAndDuplicateCallbackImportOnlyOnce() = fixture { manifest ->
        open(true); launch("icon_sets_add");systemPicker()
        lateinit var model: ResourcesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ResourcesViewModel::class.java] }
        val token=checkNotNull(model.filePicker.state.value.request).token
        recreateBehindPicker();choose(ResourceAcceptanceInputs.name(manifest,"icons"))
        compose.waitUntil(20_000) { runBlocking { entry.icons().all().size==beforeIcons.size+1 } && !model.iconBusy.value }
        val before=runBlocking { entry.icons().all() }
        compose.runOnIdle { model.receiveIconsFilePick(token,"content://invalid/duplicate") }
        compose.waitForIdle();assertEquals(before,runBlocking { entry.icons().all() })
        capture("resource_icons_picker_recreated_once")
    }

    @Test fun unavailableUriAndOrphanRequestReleaseBusyAndAllowRealPickerRetry() = fixture { manifest ->
        open(false)
        lateinit var model: RuleResourcesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[RuleResourcesViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        val orphan = runBlocking { checkNotNull(model.beginFilePick()) }
        assertFalse(runBlocking { model.filePicker.reconcile(null) })
        compose.runOnIdle { model.receiveFilePick(orphan, "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fmissing-resource-saf") }
        compose.waitUntil(10_000) { !model.state.value.busy }
        assertNull(model.filePicker.state.value.request); assertNoNewResources()
        // 作者 long：仅失效URI分支显式注入不可读路径；后续重试仍从真实DocumentsUI返回授权URI，不能把它算成系统选择成功。
        val token = runBlocking { checkNotNull(model.beginFilePick()) }
        compose.runOnIdle { model.receiveFilePick(token, "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fmissing-resource-saf") }
        compose.waitUntil(15_000) { model.filePicker.state.value.request?.phase == FilePickerRequest.FAILED && !model.state.value.busy }
        assertNoNewResources(); capture("resource_picker_unavailable_uri")
        pickerCapture = 10
        launch("rule_resources_add"); choose(ResourceAcceptanceInputs.name(manifest, "rules")); awaitTag("rule_resource_content")
        assertNoNewResources()
    }

    @Test fun inspectRealPickerFileInventory() = fixture { manifest ->
        open(false);launch("rule_resources_add");picker()
        fun report(phase:String) { val items=root()?.let { nodes(it).map { node ->
            "${node.className}|${node.viewIdResourceName}|${node.text}|${node.contentDescription}|scroll=${node.isScrollable}"
        }.toList() }.orEmpty()
        instrumentation.sendStatus(2,Bundle().apply{putString("kclash_picker_inventory",phase+"\n"+items.joinToString("\n"))}) }
        report("initial")
        val node=checkNotNull(root());val home=node.findAccessibilityNodeInfosByViewId("android:id/home").firstOrNull()
            ?:nodes(node).firstOrNull{it.contentDescription?.toString()?.contains("根目录")==true}
        check(home!=null&&click(home));SystemClock.sleep(500);report("roots")
        ResourceAcceptanceInputs.shell("input keyevent KEYCODE_BACK")
        cancel()
    }

    @Test fun recoverExactEmptyCatalogPreference() = runBlocking {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashEmptyResourceRecovery"))
        val dao=entry.database().appPreferences()
        val row=dao.getByPrefix(ICON_SETS_KEY).single{it.key==ICON_SETS_KEY}
        assertEquals("[]",row.value)
        assertTrue(entry.icons().all().isEmpty())
        dao.delete(ICON_SETS_KEY)
    }
    private fun recreateBehindPicker() {
        // 作者 long：系统选择器在前台时宿主合法处于STOPPED；Scenario.recreate会强等RESUMED，必须请求真实Activity重建后由系统结果恢复。
        instrumentation.runOnMainSync { compose.activity.recreate() }
        SystemClock.sleep(700)
    }
    private var beforeCatalogRows = emptyList<AppPreferenceEntity>()
    private var beforeProfiles = emptyList<ProfileEntity>()
    private var beforeRules = emptyList<RuleResource>()
    private var beforeIcons = emptyList<ResourceIconSet>()
    private var beforeRecent = emptyList<String>()
    private var beforeHistory: AppPreferenceEntity? = null
    private fun fixture(block: (JSONObject) -> Unit) = runBlocking {
        val manifest = ResourceAcceptanceInputs.manifest(); val e = entry; val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertNull("保留用户已有草稿", e.profileDraftStore().read())
        beforeProfiles = e.database().profiles().getAll()
        val selected = withTimeout(20_000) { repo.currentProfileId.first { id -> beforeProfiles.any { it.id == id } } }
        val files = beforeProfiles.associate { it.id to ResourceAcceptanceInputs.signature(repo.profileFile(it.id)) }
        beforeCatalogRows=e.database().appPreferences().getByPrefix("").filter{it.key in setOf(RULE_RESOURCES_KEY,ICON_SETS_KEY)}
        beforeRules = e.rules().all(); beforeIcons = e.icons().all(); beforeRecent = repo.recentGroupIcons()
        beforeHistory = e.database().appPreferences().getByPrefix("group_icon_history").singleOrNull()
        val ruleFiles = beforeRules.associate { it.id to ResourceAcceptanceInputs.signature(e.rules().file(it)) }
        try { block(manifest) } finally {
            lateinit var ruleVm: RuleResourcesViewModel
            lateinit var iconVm: ResourcesViewModel
            compose.runOnIdle {
                ruleVm = ViewModelProvider(compose.activity)[RuleResourcesViewModel::class.java]
                iconVm = ViewModelProvider(compose.activity)[ResourcesViewModel::class.java]
                ruleVm.closePage {}; iconVm.closeIcons {}
            }
            compose.waitUntil(20_000) { ruleVm.state.value.session == null && ruleVm.filePicker.state.value.request == null && iconVm.filePicker.state.value.request == null }
            // 作者 long：失败也只回收本批唯一名称的新资源；正式目录及文件内容先断言再收尾，避免隐藏并发修改。
            e.rules().all().filter { it.id !in beforeRules.map(RuleResource::id) }.forEach { row ->
                check(row.label.startsWith(manifest.getString("token")))
                e.rules().delete(row)
                for (name in setOf(row.fileName, row.coreFileName)) {
                    val file = File(e.rules().file(row).parentFile, name)
                    check(file.parentFile?.name == "rules" && name.matches(Regex("[a-zA-Z0-9-]+\\.rules(\\.mrs)?")))
                    check(!file.exists() || file.delete())
                }
            }
            e.icons().all().filter { it.id !in beforeIcons.map(ResourceIconSet::id) }.forEach { check(it.name == manifest.getString("token")); e.icons().delete(it.id) }
            for(key in setOf(RULE_RESOURCES_KEY,ICON_SETS_KEY)) {
                val original=beforeCatalogRows.firstOrNull{it.key==key}
                if(original==null)e.database().appPreferences().delete(key)else e.database().appPreferences().put(original)
            }
            assertEquals(beforeRules, e.rules().all()); assertEquals(beforeIcons, e.icons().all())
            assertEquals(ruleFiles, beforeRules.associate { it.id to ResourceAcceptanceInputs.signature(e.rules().file(it)) })
            assertEquals(beforeProfiles, e.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
            assertEquals(files, beforeProfiles.associate { it.id to ResourceAcceptanceInputs.signature(repo.profileFile(it.id)) })
            assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        }
    }
    private fun assertNoNewResources() = runBlocking { assertEquals(beforeRules, entry.rules().all()); assertEquals(beforeIcons, entry.icons().all()) }
    private fun awaitTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun open(icons: Boolean) {
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
        compose.onNodeWithText(if (icons) "图标集" else "规则资源").performClick()
    }
    private fun launch(tag: String) {
        lateinit var rule: RuleResourcesViewModel
        compose.runOnIdle { rule=ViewModelProvider(compose.activity)[RuleResourcesViewModel::class.java] }
        try { compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() } }
        catch(failure:Throwable) {
            val state=rule.state.value;val pick=rule.filePicker.state.value
            instrumentation.sendStatus(2,Bundle().apply{putString("kclash_picker_diagnostic","tag=$tag;ready=${state.ready};busy=${state.busy};loading=${state.loading};recoveryFailed=${state.recoveryFailed};pickerReady=${pick.ready};pickerPhase=${pick.request?.phase};pickerBusy=${pick.busy};pickerFailure=${pick.recoveryFailed};tagCount=${compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size};error=${state.error}")})
            capture("resource_diagnostic_"+tag)
            throw failure
        }
        compose.waitForIdle(); SystemClock.sleep(650)
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("从文件导入").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("从文件导入").performClick()
    }
    private fun root() = instrumentation.uiAutomation.rootInActiveWindow
    private fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node); for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) { if (current?.isClickable == true && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; current = current?.parent }
        return false
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            // 作者 long：文件请求先异步写密文，测试必须推进主窗口帧才能执行其launch续体，不能只阻塞等待系统页。
            compose.mainClock.advanceTimeByFrame()
            if (condition()) return
            SystemClock.sleep(100)
        }
        val info=root()?.takeIf { it.packageName?.toString()?.endsWith("documentsui")==true }?.let { nodes(it).map { node ->
            "${node.text}|enabled=${node.isEnabled}|visible=${node.isVisibleToUser}|click=${node.isClickable}|parentEnabled=${node.parent?.isEnabled}|${node.viewIdResourceName}"
        }.toList() }.orEmpty()
        instrumentation.sendStatus(2,Bundle().apply{putString("kclash_picker_failure_nodes",info.joinToString("\n"))})
        captureWindow("resource_picker_failure_final")
        error("系统文件选择流程超时，前台=${root()?.packageName}")
    }
    private fun systemPicker() = waitFor { root()?.packageName?.toString()?.let { it.endsWith("documentsui") || it == "com.android.providers.media.module" } == true }
    private fun picker() {
        systemPicker()
        // 作者 long：Android 16可能将GET_CONTENT交给系统照片选择器；从真实“浏览”菜单进入DocumentsUI，不伪造URI绕过平台流程。
        if (root()?.packageName?.toString() == "com.android.providers.media.module") {
            val more = nodes(checkNotNull(root())).firstOrNull { it.contentDescription?.toString() in setOf("更多选项", "More options") }
            check(more != null && click(more)) { "系统照片选择器缺少更多选项" }
            waitFor { nodes(root() ?: return@waitFor false).firstOrNull { it.text?.toString() in setOf("浏览…", "浏览...", "Browse…", "Browse...") }?.let(::click) == true }
        }
        waitFor { root()?.packageName?.toString()?.endsWith("documentsui") == true }
        // 作者 long：DocumentsUI图片网格隐藏文件名；切到系统列表后按完整名称匹配，不能根据缩略图位置猜文件。
        root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/sub_menu_list")?.firstOrNull()?.let {
            check(click(it))
            waitFor { root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/sub_menu_grid")?.isNotEmpty() == true }
        }
    }
    private fun cancel() { systemPicker(); ResourceAcceptanceInputs.shell("input keyevent KEYCODE_BACK"); waitFor { root()?.packageName?.toString() == compose.activity.packageName }; compose.waitForIdle() }
    private var pickerCapture = 0
    private fun choose(filename: String) {
        picker()
        captureWindow("resource_picker_${filename.substringAfterLast('-').substringBeforeLast('.')}_${pickerCapture++}")
        fun select(): Boolean {
            val node = root()?.findAccessibilityNodeInfosByText(filename)?.firstOrNull { it.text?.toString() == filename } ?: return false
            if (!node.isVisibleToUser) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            return click(node)
        }
        if (!select()) {
            val node = checkNotNull(root())
            val drawer = nodes(node).firstOrNull { it.contentDescription?.toString()?.let { text -> text.contains("根目录") || text.contains("Show roots", true) } == true }
            check(drawer != null && click(drawer)) { "系统文件选择器缺少目录入口" }
            waitFor { nodes(root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/roots_list")?.firstOrNull() ?: return@waitFor false)
                .firstOrNull { it.viewIdResourceName == "android:id/title" && it.text?.toString() == "Redmi Note 8 Pro" }?.let(::click) == true }
            waitFor { root()?.findAccessibilityNodeInfosByText("Download")?.firstOrNull { it.text?.toString() == "Download" }?.let(::click) == true }
            waitFor {
                if (select()) true else {
                    root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/dir_list")?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    false
                }
            }
        }
        waitFor { root()?.packageName?.toString() == compose.activity.packageName }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); SystemClock.sleep(400)
        captureWindow(name)
    }
    private fun captureWindow(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
    }
}
