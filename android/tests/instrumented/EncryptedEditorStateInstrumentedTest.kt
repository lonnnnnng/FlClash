package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：直接丢弃整个组合并从序列化纯值树重建，防止Android窗口自己的注册表掩盖加密子树遗漏。 */
class EncryptedEditorStateInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration() }

    @Test fun nestedDialogDraftRestoresFromEncryptedTreeWithoutActivityRestoration() {
        var shown by mutableStateOf(true)
        var generation by mutableLongStateOf(1)
        var saved: EditorSaveableState? = null
        var restored: EditorSaveableState? = null
        var failures = 0
        // 作者 long：移除整个组合足以销毁注册表；重建时更换调用位置的 key 会伪造不同页面，失去冷启动应保持的保存键。
        compose.setContent { KClashTheme { if (shown) {
            EncryptedEditorState(generation, restored, { saved = it }, { failures++ }) {
                ProfileRulesEditorDialog(Profile.custom(955, "安全子树"), "DOMAIN,same.test,DIRECT\nDOMAIN,same.test,DIRECT", false, {}, {})
            }
        } } }
        phase("initial")
        compose.onNodeWithTag("custom_rule_edit_1").performClick()
        phase("form-open")
        compose.onNodeWithTag("custom_rule_payload").performTextReplacement("private-composition-marker")
        compose.waitUntil(10_000) { Gson().toJson(saved).contains("private-composition-marker") }
        compose.runOnIdle { assertEquals(0, failures) }
        phase("form-captured")
        compose.runOnIdle { restored = copy(checkNotNull(saved)); shown = false }
        compose.waitForIdle()
        phase("form-disposed")
        compose.runOnIdle { generation++; shown = true }
        phase("form-recreated")
        compose.onNodeWithTag("custom_rule_payload").assertTextContains("private-composition-marker")
        phase("form-restored")
        compose.onNodeWithTag("custom_rule_type").performClick()
        compose.onNodeWithTag("custom_rule_picker_search").performTextReplacement("IP-CIDR")
        compose.waitUntil(10_000) { Gson().toJson(saved).contains("IP-CIDR") }
        compose.runOnIdle { assertEquals(0, failures) }
        phase("picker-captured")
        compose.runOnIdle { restored = copy(checkNotNull(saved)); shown = false }
        compose.waitForIdle()
        phase("picker-disposed")
        compose.runOnIdle { generation++; shown = true }
        phase("picker-recreated")
        compose.onNodeWithTag("custom_rule_picker_search").assertTextContains("IP-CIDR")
        compose.onNodeWithTag("custom_rule_picker_back").performClick()
        compose.onNodeWithTag("custom_rule_payload").assertTextContains("private-composition-marker")
        compose.runOnIdle { assertEquals(0, failures) }
    }

    @Test fun interruptedBatchKeepsOperationAndWaitsForExplicitRetry() {
        var shown by mutableStateOf(true)
        var generation by mutableLongStateOf(1)
        var saved: EditorSaveableState? = null
        var restored: EditorSaveableState? = null
        var text by mutableStateOf("DOMAIN,same.test,DIRECT\nMATCH,DIRECT")
        var busy by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var interrupted by mutableStateOf<String?>(null)
        var accepted: String? = null
        var writes = 0
        var failures = 0
        compose.setContent { KClashTheme { if (shown) {
            EncryptedEditorState(generation, restored, { saved = it }, { failures++ }) { checkpoint ->
                ProfileRulesEditorDialog(Profile.custom(956, "中断批选"), text, busy, {}, { next ->
                    checkpoint(); writes++; accepted = next
                    if (writes == 1) busy = true else { text = next; busy = false; interrupted = null; error = null }
                }, saveError = error, interruptedRules = interrupted)
            }
        } } }
        compose.onNodeWithTag("custom_rule_menu_0").performClick()
        compose.onNodeWithTag("custom_rule_duplicate_0").performClick()
        compose.runOnIdle {
            assertEquals(1, writes)
            restored = copy(checkNotNull(saved)); shown = false
            busy = false; interrupted = checkNotNull(accepted); error = "上次保存已中断，草稿已恢复，请检查后重试"
        }
        compose.waitForIdle()
        compose.runOnIdle { generation++; shown = true }
        compose.onNodeWithTag("custom_rules_retry_interrupted").assertIsEnabled()
        compose.onNodeWithTag("custom_rule_edit_2").assertExists()
        compose.runOnIdle { assertEquals(1, writes) }
        compose.onNodeWithTag("custom_rules_retry_interrupted").performClick()
        compose.onNodeWithTag("custom_rules_retry_interrupted").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, writes); assertEquals(0, failures)
            assertEquals(3, com.lonnnnnng.flclash.data.CustomRules.decode(text).size)
        }
    }

    private fun copy(value: EditorSaveableState): EditorSaveableState = Gson().fromJson(Gson().toJson(value), EditorSaveableState::class.java)
    private fun phase(value: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0,
        android.os.Bundle().apply { putString("kclash_registry_phase", value) })
}
