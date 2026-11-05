package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：prepare/verify分次执行；外部必须杀掉后台进程并确认PID消失，不能主动重开子页伪装恢复。 */
class StructuredEditorColdProcessInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "structured-editor-cold.json")
    private fun scenario(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProfileClosureAcceptance"))
        val value = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashEditorCase"))
        require(value in setOf("rules-form", "rules-batch", "rules-conflict", "rules-yaml", "rules-yaml-discard", "rules-type-open",
            "groups-icon", "groups-option", "groups-value", "groups-yaml", "single-yaml", "groups-members", "groups-members-sort", "groups-batch"))
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_editor_process_pid", android.os.Process.myPid().toString()) })
        return value
    }

    @Test fun prepareEncryptedSubpageWithoutCommittingDraft(): Unit = runBlocking {
        val case = scenario()
        val e = entry(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先收尾上一组冷进程验收", marker().exists())
        assertNull("不能覆盖现有编辑草稿", e.profileDraftStore().read())
        val before = e.database().profiles().getAll().sortedBy { it.id }
        val id = System.nanoTime().and(Long.MAX_VALUE)
        val profile = Profile.custom(id, "cold-editor").copy(orderIndex = before.size)
        val bytes = readAuthorizedProxyProfile()
        val record = JsonObject().apply {
            addProperty("id", id); addProperty("case", case); addProperty("selected", repo.currentProfileId.value)
            addProperty("preparedPid", android.os.Process.myPid())
            addProperty("rows", digest(Gson().toJson(before).toByteArray()))
            addProperty("files", files(before.map { it.id }))
        }
        e.database().profiles().insert(profile.toEntity())
        repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        marker().writeText(record.toString())
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(profile) }
        compose.waitUntil(20_000) { model.editorState.value.overview != null }
        if (case.startsWith("rules")) {
            compose.runOnIdle { model.openRulesEditor(profile) }
            compose.waitUntil(20_000) { model.rulesEditorState.value.profile?.id == id && !model.rulesEditorState.value.isLoading }
            compose.onNodeWithTag("custom_rule_menu_0").performClick()
            compose.onNodeWithTag("custom_rule_duplicate_0").performClick()
            compose.waitUntil(20_000) { !model.rulesEditorState.value.isSaving && CustomRules.decode(model.rulesEditorState.value.rules, validate = false).size == CustomRules.decode(ProfileRulesEditor.extractRules(bytes.toString(Charsets.UTF_8)), validate = false).size + 1 }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("保存中…").fetchSemanticsNodes().isEmpty() }
            if (case in setOf("rules-yaml", "rules-yaml-discard")) {
                compose.onNodeWithTag("custom_rules_menu").performClick()
                compose.onNodeWithTag("custom_rules_yaml").performClick()
                val original = compose.onNodeWithTag("profile_rules_yaml").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
                val invalid = original + "\n# $DRAFT_MARKER\n- NETWORK,any,DIRECT"
                compose.onNodeWithTag("profile_rules_yaml").performTextReplacement(invalid)
                compose.onNodeWithTag("custom_rules_yaml_confirm").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("custom_rules_yaml_error").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("custom_rules_yaml_error").assertTextContains("TCP", substring = true)
                val start = invalid.indexOf(DRAFT_MARKER) + 2
                compose.onNodeWithTag("profile_rules_yaml").performTextInputSelection(androidx.compose.ui.text.TextRange(start, start + 8))
                closeSoftKeyboard()
                val node = compose.onNodeWithTag("profile_rules_yaml").fetchSemanticsNode().config
                record.addProperty("selectionStart", node[SemanticsProperties.TextSelectionRange].start)
                record.addProperty("selectionEnd", node[SemanticsProperties.TextSelectionRange].end)
                record.addProperty("yamlSha256", digest(node[SemanticsProperties.EditableText].text.toByteArray()))
                record.addProperty("yamlError", compose.onNodeWithTag("custom_rules_yaml_error").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
                if (case == "rules-yaml-discard") compose.onNodeWithTag("custom_rules_yaml_back").performClick()
            } else if (case == "rules-batch") {
                compose.onNodeWithTag("custom_rules_menu").performClick()
                compose.onNodeWithTag("custom_rules_select").performClick()
                compose.onNodeWithTag("custom_rule_select_1").performClick()
            } else {
                compose.onNodeWithTag("custom_rule_edit_1").performClick()
                compose.onNodeWithTag("custom_rule_type").performClick()
                compose.onNodeWithTag("custom_rule_pick_DOMAIN").performClick()
                compose.onNodeWithTag("custom_rule_payload").performTextReplacement(DRAFT_MARKER)
                if (case == "rules-type-open") {
                    compose.onNodeWithTag("custom_rule_type").performClick()
                    compose.onNodeWithTag("custom_rule_picker_search").performTextReplacement("DOMAIN")
                }
            }
        } else {
            compose.runOnIdle {
                if (case in setOf("groups-yaml", "groups-batch")) model.openProxyGroupsEditor(profile) else model.openSingleProxyGroupEditor(profile, 0)
            }
            compose.waitUntil(20_000) { model.proxyGroupsEditorState.value.profile?.id == id && !model.proxyGroupsEditorState.value.isLoading }
            when (case) {
                "groups-batch" -> {
                    compose.onNodeWithTag("proxy_group_select").performClick()
                    compose.onNodeWithTag("proxy_group_check_0").performClick()
                }
                "groups-members", "groups-members-sort" -> {
                    compose.onNodeWithTag("proxy_group_members").performScrollTo().performClick()
                    compose.onNodeWithTag("group_members_add").performClick()
                    if (case == "groups-members") {
                        compose.onNodeWithTag("group_member_search").performTextReplacement("DIRECT")
                        val choice = memberCheckbox("DIRECT")
                        val wasChecked = choice.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On
                        record.addProperty("directChecked", !wasChecked)
                        compose.onNodeWithTag("group_member_choice_false_DIRECT").performClick()
                    } else {
                        // 作者 long：只补正式成员选择器中的内建项，保留原1.yaml副本，不生成替代配置或提交成员变更。
                        for (name in listOf("DIRECT", "REJECT")) {
                            compose.onNodeWithTag("group_member_search").performTextReplacement(name)
                            val choice = memberCheckbox(name)
                            if (choice.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] != androidx.compose.ui.state.ToggleableState.On)
                                compose.onNodeWithTag("group_member_choice_false_$name").performClick()
                        }
                        closeSoftKeyboard()
                        compose.onNodeWithTag("group_member_picker_done").performClick()
                        compose.onNodeWithTag("group_members_sort").performClick()
                        val beforeOrder = memberOrder()
                        assertTrue(beforeOrder.size >= 2)
                        compose.onNodeWithTag("group_move_down_0").performScrollTo().performClick()
                        val expected = beforeOrder.toMutableList().apply { add(1, removeAt(0)) }
                        assertEquals(expected, memberOrder())
                        record.add("memberOrder", Gson().toJsonTree(expected))
                    }
                }
                "groups-icon" -> {
                    compose.onNodeWithTag("proxy_group_icon").performScrollTo().performClick()
                    compose.onNodeWithTag("group_icon_search").performTextReplacement(DRAFT_MARKER)
                    compose.onNodeWithTag("group_icon_link").performClick()
                    compose.onNodeWithTag("group_icon_url").performTextReplacement(DRAFT_MARKER)
                    compose.onNodeWithTag("group_icon_url_confirm").performClick()
                }
                "groups-option" -> compose.onNodeWithTag("proxy_group_add_option").performScrollTo().performClick()
                "groups-value" -> {
                    if (compose.onAllNodesWithTag("group_option_interval").fetchSemanticsNodes().isEmpty()) {
                        compose.onNodeWithTag("proxy_group_add_option").performScrollTo().performClick()
                        compose.onNodeWithTag("group_add_interval").performScrollTo().performClick()
                        compose.onNodeWithTag("proxy_group_add_option_back").performClick()
                    }
                    compose.onNodeWithTag("group_option_interval").performScrollTo().performClick()
                    compose.onNodeWithTag("group_value_input").performTextReplacement("-2")
                    compose.onNodeWithTag("group_value_confirm").performClick()
                }
                else -> {
                    val prefix = if (case == "groups-yaml") "proxy_groups_yaml" else "proxy_group_yaml"
                    // 作者 long：整组入口在滚动列表，单组快捷入口位于固定顶栏，不能向没有滚动父层的按钮请求滚动。
                    if (case == "groups-yaml") {
                        compose.onNodeWithTag("proxy_groups_yaml").performScrollTo().performClick()
                    } else {
                        compose.onNodeWithTag("proxy_group_quick_yaml").performClick()
                    }
                    val original = compose.onNodeWithTag("${prefix}_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
                    val invalid = original + "\n# $DRAFT_MARKER\ninvalid: ["
                    compose.onNodeWithTag("${prefix}_content").performTextReplacement(invalid)
                    compose.onNodeWithTag("${prefix}_confirm").performClick()
                    compose.onNodeWithTag("${prefix}_error").assertIsDisplayed()
                }
            }
        }
        closeSoftKeyboard()
        if (case in setOf("groups-yaml", "single-yaml")) {
            // 作者 long：确认校验会主动定位错误，冷恢复必须比较回收前的真实选区，不能把输入结束位置当成校验后的光标。
            val prefix = if (case == "groups-yaml") "proxy_groups_yaml" else "proxy_group_yaml"
            val node = compose.onNodeWithTag("${prefix}_content").fetchSemanticsNode().config
            val selection = node[SemanticsProperties.TextSelectionRange]
            record.addProperty("selectionStart", selection.start)
            record.addProperty("selectionEnd", selection.end)
            record.addProperty("yamlSha256", digest(node[SemanticsProperties.EditableText].text.toByteArray()))
        }
        record.addProperty("tempBody", digest(repo.profileFile(id).readBytes()))
        record.addProperty("tempMtime", repo.profileFile(id).lastModified())
        marker().writeText(record.toString())
        compose.waitUntil(15_000) {
            val ui = if (case.startsWith("rules")) model.rulesEditorState.value.uiDraft else model.proxyGroupsEditorState.value.uiDraft
            ui != null && if (case in setOf("rules-batch", "groups-option", "groups-value", "groups-members", "groups-members-sort", "groups-batch")) ui.values.isNotEmpty() else Gson().toJson(ui).contains(DRAFT_MARKER)
        }
        withTimeout(15_000) {
            while (true) {
                val snapshot = e.profileDraftStore().read()?.let { Gson().fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java) }
                val expected = if (case.startsWith("rules")) model.rulesEditorState.value.uiDraft else model.proxyGroupsEditorState.value.uiDraft
                if (expected != null && expected == if (case.startsWith("rules")) snapshot?.rules?.uiDraft else snapshot?.groups?.uiDraft) break
                delay(100)
            }
        }
        assertNoPlaintextInSystemStateOrEncryptedFile()
        if (case == "rules-conflict") {
            // 作者 long：只改变临时副本，验证恢复保留旧CAS基线；正式用户配置不参与冲突构造。
            repo.profileFile(id).appendText("\n# changed-after-draft\n")
            record.addProperty("tempBody", digest(repo.profileFile(id).readBytes()))
            record.addProperty("tempMtime", repo.profileFile(id).lastModified())
            marker().writeText(record.toString())
        }
        capture("${case}_prepared")
    }

    @Test fun verifyDifferentProcessRestoresExactSubpageAndClearsDiscardedDraft(): Unit = runBlocking {
        val case = scenario()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record.get("case").asString)
        assertNotEquals("冷进程必须更换PID", record.get("preparedPid").asInt, android.os.Process.myPid())
        val id = record.get("id").asLong
        val e = entry(); val repo = e.profileRepository()
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
        try {
            compose.waitUntil(20_000) { if (case.startsWith("rules")) model.rulesEditorState.value.profile?.id == id else model.proxyGroupsEditorState.value.profile?.id == id }
            when (case) {
                "rules-yaml", "rules-yaml-discard" -> {
                    if (case == "rules-yaml-discard") {
                        compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
                        capture("${case}_dialog_restored")
                        compose.onNodeWithText("继续编辑", substring = false).performClick()
                    }
                    val node = compose.onNodeWithTag("profile_rules_yaml").fetchSemanticsNode().config
                    assertEquals(record.get("yamlSha256").asString, digest(node[SemanticsProperties.EditableText].text.toByteArray()))
                    assertEquals(androidx.compose.ui.text.TextRange(record.get("selectionStart").asInt, record.get("selectionEnd").asInt), node[SemanticsProperties.TextSelectionRange])
                    compose.onNodeWithTag("custom_rules_yaml_error").assertTextEquals(record.get("yamlError").asString)
                    capture("${case}_restored")
                    compose.onNodeWithTag("custom_rules_yaml_back").performClick()
                    compose.onNodeWithText("放弃", substring = false).performClick()
                    compose.onNodeWithTag("custom_rules_back").performClick()
                }
                "rules-type-open" -> {
                    compose.onNodeWithTag("custom_rule_picker_search").assertTextContains("DOMAIN")
                    compose.onNodeWithTag("custom_rule_pick_DOMAIN").assertExists()
                    compose.onNodeWithTag("custom_rule_pick_MATCH").assertDoesNotExist()
                    capture("${case}_restored")
                    compose.onNodeWithTag("custom_rule_picker_back").performClick()
                    compose.onNodeWithTag("custom_rule_payload").assertTextContains(DRAFT_MARKER)
                    compose.onNodeWithTag("custom_rule_confirm").assertIsEnabled()
                    compose.onNodeWithTag("custom_rule_form_back").performClick()
                    compose.onNodeWithText("放弃", substring = false).performClick()
                    compose.onNodeWithTag("custom_rules_back").performClick()
                }
                "groups-members" -> {
                    compose.onNodeWithTag("group_member_search").assertTextContains("DIRECT")
                    if (record.get("directChecked").asBoolean) memberCheckbox("DIRECT").assertIsOn() else memberCheckbox("DIRECT").assertIsOff()
                    capture("${case}_restored")
                    compose.onNodeWithTag("group_member_picker_back").performClick()
                    compose.onNodeWithTag("proxy_group_members_back").performClick()
                    compose.onNodeWithText("放弃修改", substring = false).performClick()
                    compose.onNodeWithTag("proxy_group_form_back").performClick()
                }
                "groups-members-sort" -> {
                    compose.onNodeWithText("长按拖动调整代理与资源顺序").assertExists()
                    compose.onNodeWithTag("group_member_remove_0").assertDoesNotExist()
                    assertEquals(record.getAsJsonArray("memberOrder").map { it.asString }, memberOrder())
                    capture("${case}_restored")
                    compose.onNodeWithTag("proxy_group_members_back").performClick()
                    compose.onNodeWithText("放弃修改", substring = false).performClick()
                    compose.onNodeWithTag("proxy_group_form_back").performClick()
                }
                "groups-batch" -> {
                    compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
                    compose.onNodeWithTag("proxy_group_check_0").assertIsOn()
                    compose.onNodeWithTag("proxy_group_check_1").assertIsOff()
                    compose.onNodeWithTag("proxy_group_batch_copy").assertIsEnabled()
                    compose.onNodeWithTag("proxy_group_batch_delete").assertIsEnabled()
                    compose.onNodeWithTag("proxy_group_selection_done").assertExists()
                    capture("${case}_restored")
                    compose.onNodeWithTag("proxy_group_list_back").performClick()
                    compose.onNodeWithTag("proxy_group_selection_done").assertDoesNotExist()
                    compose.onNodeWithTag("proxy_group_check_0").assertIsOff()
                    compose.onNodeWithTag("proxy_group_list_back").performClick()
                }
                "rules-batch" -> {
                    compose.onNodeWithText("已选 1 项").assertIsDisplayed()
                    compose.onNodeWithTag("custom_rule_select_1").assertIsOn()
                    compose.onNodeWithTag("custom_rule_select_0").assertIsOff()
                    capture("${case}_restored")
                    compose.onNodeWithTag("custom_rules_back").performClick()
                    compose.onNodeWithTag("custom_rules_back").performClick()
                }
                "rules-form", "rules-conflict" -> {
                    compose.onNodeWithTag("custom_rule_payload").assertTextContains(DRAFT_MARKER)
                    compose.onNodeWithTag("custom_rule_confirm").assertIsEnabled()
                    capture("${case}_restored")
                    if (case == "rules-conflict") {
                        compose.onNodeWithTag("custom_rule_confirm").performClick()
                        compose.waitUntil(15_000) { !model.rulesEditorState.value.isSaving && model.rulesEditorState.value.error != null }
                        compose.onNodeWithTag("custom_rule_payload").assertTextContains(DRAFT_MARKER)
                        compose.onNodeWithTag("custom_rule_form_error").assertIsDisplayed()
                        capture("${case}_rejected")
                    }
                    compose.onNodeWithTag("custom_rule_form_back").performClick()
                    compose.onNodeWithText("放弃", substring = false).performClick()
                    compose.onNodeWithTag("custom_rules_back").performClick()
                }
                "groups-icon" -> {
                    compose.onNodeWithTag("group_icon_url").assertTextContains(DRAFT_MARKER)
                    compose.onNodeWithText("请输入有效的 HTTP 或 HTTPS 图标地址").assertIsDisplayed()
                    capture("${case}_restored")
                    compose.onNodeWithText("取消", substring = false).performClick()
                    compose.onNodeWithTag("group_icon_search").assertTextContains(DRAFT_MARKER)
                    compose.onNodeWithTag("group_icon_back").performClick()
                    compose.onNodeWithTag("proxy_group_form_back").performClick()
                }
                "groups-option" -> {
                    compose.onNodeWithTag("proxy_group_add_option_back").assertExists()
                    capture("${case}_restored")
                    compose.onNodeWithTag("proxy_group_add_option_back").performClick()
                    compose.onNodeWithTag("proxy_group_form_back").performClick()
                }
                "groups-value" -> {
                    compose.onNodeWithTag("group_value_input").assertTextContains("-2")
                    compose.onNodeWithText("请输入非负整数").assertIsDisplayed()
                    capture("${case}_restored")
                    compose.onNodeWithText("取消", substring = false).performClick()
                    compose.onNodeWithTag("proxy_group_form_back").performClick()
                    if (compose.onAllNodesWithText("放弃修改", substring = false).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("放弃修改", substring = false).performClick()
                }
                else -> {
                    val prefix = if (case == "groups-yaml") "proxy_groups_yaml" else "proxy_group_yaml"
                    val node = compose.onNodeWithTag("${prefix}_content").fetchSemanticsNode().config
                    assertEquals(record.get("yamlSha256").asString, digest(node[SemanticsProperties.EditableText].text.toByteArray()))
                    assertEquals(androidx.compose.ui.text.TextRange(record.get("selectionStart").asInt, record.get("selectionEnd").asInt), node[SemanticsProperties.TextSelectionRange])
                    compose.onNodeWithTag("${prefix}_error").assertIsDisplayed()
                    capture("${case}_restored")
                    compose.onNodeWithTag("${prefix}_back").performClick()
                    compose.onNodeWithText("放弃修改", substring = false).performClick()
                    compose.onNodeWithTag(if (case == "groups-yaml") "proxy_group_list_back" else "proxy_group_form_back").performClick()
                }
            }
            compose.waitUntil(15_000) { if (case.startsWith("rules")) model.rulesEditorState.value.profile == null else model.proxyGroupsEditorState.value.profile == null }
            assertEquals(record.get("tempBody").asString, digest(repo.profileFile(id).readBytes()))
            assertEquals(record.get("tempMtime").asLong, repo.profileFile(id).lastModified())
            val snapshot = e.profileDraftStore().read()?.let { Gson().fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java) }
            assertNull(snapshot?.rules); assertNull(snapshot?.groups)
        } finally {
            compose.runOnIdle { model.closeEditor() }
            withTimeout(15_000) { while (e.profileDraftStore().read() != null) delay(100) }
            repo.deleteProfile(id)
            val after = e.database().profiles().getAll().sortedBy { it.id }
            assertEquals(record.get("rows").asString, digest(Gson().toJson(after).toByteArray()))
            assertEquals(record.get("files").asString, files(after.map { it.id }))
            assertEquals(record.get("selected").asLong, repo.currentProfileId.value)
            assertTrue(marker().delete())
        }
    }

    private fun assertNoPlaintextInSystemStateOrEncryptedFile() {
        val state = Bundle()
        compose.runOnIdle { instrumentation.callActivityOnSaveInstanceState(compose.activity, state) }
        val parcel = Parcel.obtain()
        val bytes = try { parcel.writeBundle(state); parcel.marshall() } finally { parcel.recycle() }
        assertTrue(bytes.isNotEmpty())
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val needle = DRAFT_MARKER.toByteArray(charset)
            assertFalse("未确认正文不得进入系统Bundle", bytes.asList().windowed(needle.size).any { it == needle.asList() })
        }
        val encrypted = File(compose.activity.noBackupFilesDir, "profile-editor-draft.v1").readBytes()
        assertFalse("物理草稿只能是密文", encrypted.toString(Charsets.ISO_8859_1).contains(DRAFT_MARKER))
    }

    private fun files(ids: List<Long>) = ids.joinToString("|") { id -> entry().profileRepository().profileFile(id).let { "$id:${it.length()}:${it.lastModified()}:${digest(it.readBytes())}" } }
    private fun memberCheckbox(name: String) = compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("group_member_choice_false_$name")))
    private fun memberOrder(): List<String> {
        // 作者 long：同名文本存在不能证明顺序；按显示行索引读取成员标签，核对完整有序列表。
        return generateSequence(0) { it + 1 }.map { index ->
            compose.onAllNodesWithTag("group_member_label_$index").fetchSemanticsNodes().singleOrNull()
        }.takeWhile { it != null }.map { node -> checkNotNull(node).config[SemanticsProperties.Text].single().text }.toList()
    }
    private fun digest(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng("cold_$name", ByteArrayOutputStream().also { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        screen.recycle()
    }
    private companion object { const val DRAFT_MARKER = "cold-private-draft-20261105" }
}
