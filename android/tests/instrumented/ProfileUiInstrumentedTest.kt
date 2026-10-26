package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：Profiles 的关键操作先在无网络 Compose 环境验证状态边界，避免页面回归依赖真实订阅和 VPN 权限。
 */
@RunWith(AndroidJUnit4::class)
class ProfileUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addProfileDialogSwitchesToCustomAndInvokesCallback() {
        var added = false
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = { added = true },
                )
            }
        }

        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertTrue(added)
    }

    @Test
    fun profileEditorSavesInitialYamlAndShowsProfileLabel() {
        var saved: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfileEditorDialog(
                    profile = Profile.custom(9L, "E2E 配置"),
                    initialContent = "mixed-port: 7890",
                    saving = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onNodeWithText("编辑 E2E 配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("mixed-port: 7890", saved)
    }

    @Test
    fun rulesEditorShowsCustomProfileRulesAndForwardsSave() {
        var saved: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfileRulesEditorDialog(
                    profile = Profile.custom(11L, "规则配置"),
                    initialRules = "DOMAIN,example.com,DIRECT",
                    saving = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onNodeWithText("规则 · 规则配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("DOMAIN,example.com,DIRECT", saved)
    }

    @Test
    fun proxiesEditorShowsCustomProfileProxiesAndForwardsSave() {
        var saved: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfileProxiesEditorDialog(
                    profile = Profile.custom(12L, "代理配置"),
                    initialProxies = "- name: node\n  type: vmess",
                    saving = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onNodeWithText("代理 · 代理配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("- name: node\n  type: vmess", saved)
    }

    @Test
    fun profileMutationButtonsDisableWhileCoreIsRunning() {
        composeRule.setContent {
            KClashTheme {
                ProfileCard(
                    profile = Profile.custom(10L, "运行中配置"),
                    selected = true,
                    canMutate = false,
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("编辑").assertIsNotEnabled()
        composeRule.onNodeWithText("高级").assertIsNotEnabled()
        composeRule.onNodeWithText("代理").assertIsNotEnabled()
        composeRule.onNodeWithText("删除").assertIsNotEnabled()
    }
}
