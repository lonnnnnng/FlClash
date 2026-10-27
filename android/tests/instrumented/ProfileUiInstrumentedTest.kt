package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
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
    fun profileScriptBindingShowsOptionsAndForwardsSelection() {
        var selectedScriptId: Long? = null
        composeRule.setContent {
            KClashTheme {
                ProfileScriptBindingDialog(
                    profile = Profile.custom(18L, "脚本绑定配置"),
                    scripts = listOf(
                        Script(701L, "请求改写"),
                        Script(702L, "备用脚本"),
                    ),
                    saving = false,
                    onDismiss = {},
                    onSelect = { selectedScriptId = it },
                    onOpenScripts = {},
                )
            }
        }

        composeRule.onNodeWithText("脚本 · 脚本绑定配置").assertIsDisplayed()
        composeRule.onNodeWithText("请求改写").performClick()

        assertEquals(701L, selectedScriptId)
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
    fun proxyGroupsEditorShowsCustomProfileGroupsAndForwardsSave() {
        var saved: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfileProxyGroupsEditorDialog(
                    profile = Profile.custom(13L, "代理组配置"),
                    initialGroups = "- name: Auto\n  type: url-test",
                    saving = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onNodeWithText("代理组 · 代理组配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("- name: Auto\n  type: url-test", saved)
    }

    @Test
    fun providersEditorShowsBothProviderMapsAndForwardsSave() {
        var savedProxy: String? = null
        var savedRule: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfileProvidersEditorDialog(
                    profile = Profile.custom(16L, "Provider 配置"),
                    initialProxyProviders = "cloud:\n  type: http",
                    initialRuleProviders = "ads:\n  type: http",
                    saving = false,
                    onDismiss = {},
                    onSave = { proxy, rule ->
                        savedProxy = proxy
                        savedRule = rule
                    },
                )
            }
        }

        composeRule.onNodeWithText("Provider · Provider 配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("cloud:\n  type: http", savedProxy)
        assertEquals("ads:\n  type: http", savedRule)
    }

    @Test
    fun computedProxyGroupShowsAutomaticSelectionAction() {
        composeRule.setContent {
            KClashTheme {
                ProxyGroupsCard(
                    state = ProxyGroupsUiState(
                        groups = listOf(
                            ProxyGroupInfo(
                                name = "Auto",
                                type = "URLTest",
                                now = "node-a",
                                all = listOf("node-a", "node-b"),
                                hidden = false,
                                testUrl = "https://example.com",
                            ),
                        ),
                    ),
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onRefresh = {},
                    onSelect = { _, _ -> },
                    onTestDelay = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Auto").performClick()
        composeRule.onNodeWithText("选择 Auto").assertIsDisplayed()
        composeRule.onNodeWithText("点击当前节点可恢复自动选择").assertIsDisplayed()
        composeRule.onNodeWithText("搜索节点").performTextInput("node-b")
        assertEquals(2, composeRule.onAllNodesWithText("node-b").fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithText("node-a").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun providerOverrideDialogEditsTypedFieldsBeforeParentSave() {
        var savedOverrides: Map<String, ProxyProviderOverride>? = null
        composeRule.setContent {
            KClashTheme {
                ProfileProvidersEditorDialog(
                    profile = Profile.custom(17L, "字段化 Provider"),
                    initialProxyProviders = "cloud:\n  type: http",
                    initialRuleProviders = "",
                    saving = false,
                    onDismiss = {},
                    onSave = { _, _ -> },
                    onSaveWithOverrides = { _, _, overrides -> savedOverrides = overrides },
                )
            }
        }

        composeRule.onNodeWithText("编辑字段").performClick()
        composeRule.onNodeWithText("Provider 覆盖 · cloud").assertIsDisplayed()
        composeRule.onNodeWithText("间隔（秒）").performTextInput("300")
        composeRule.onNodeWithText("超时（毫秒）").performTextInput("5000")

        val saveButtons = composeRule.onAllNodesWithText("保存")
        saveButtons[saveButtons.fetchSemanticsNodes().lastIndex].performClick()
        assertTrue(
            composeRule.onAllNodesWithText("Provider 覆盖 · cloud")
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onAllNodesWithText("保存")[0].performClick()

        assertEquals(
            mapOf("cloud" to ProxyProviderOverride(interval = 300, timeout = 5_000)),
            savedOverrides,
        )
    }

    @Test
    fun dnsOverrideDialogForwardsTypedFields() {
        var saved: ProfileDnsOverrides? = null
        composeRule.setContent {
            KClashTheme {
                ProfileDnsOverrideDialog(
                    profile = Profile.custom(19L, "DNS 配置"),
                    initial = ProfileDnsOverrides(),
                    saving = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onNodeWithText("DNS 覆盖 · DNS 配置").assertIsDisplayed()
        composeRule.onNodeWithText("增强模式（redir-host / fake-ip / hosts）").performTextInput("fake-ip")
        composeRule.onNodeWithTag("profile-dns-override-list")
            .performScrollToNode(hasText("Nameserver"))
        composeRule.onNodeWithText("Nameserver").performTextInput("https://doh.example/dns-query")
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("fake-ip", saved?.enhancedMode)
        assertEquals(listOf("https://doh.example/dns-query"), saved?.nameserver)
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
        composeRule.onNodeWithText("Provider").assertIsNotEnabled()
        composeRule.onNodeWithText("DNS").assertIsNotEnabled()
        composeRule.onNodeWithText("代理组").assertIsNotEnabled()
        composeRule.onNodeWithText("高级").assertIsNotEnabled()
        composeRule.onNodeWithText("代理").assertIsNotEnabled()
        composeRule.onNodeWithText("脚本").assertIsNotEnabled()
        composeRule.onNodeWithText("删除").assertIsNotEnabled()
    }
}
