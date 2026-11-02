package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.junit.Assert.assertFalse
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
    fun localFileImportPreviewShowsMetadataAndConfirmsOnlyAfterReview() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            KClashTheme {
                ProfileFileImportPreviewDialog(
                    fileName = "家庭网络.yaml",
                    fileSizeBytes = 1_024,
                    isValidYaml = true,
                    busy = false,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("家庭网络.yaml").assertIsDisplayed()
        composeRule.onNodeWithText("文件大小：1024 字节").assertIsDisplayed()
        composeRule.onNodeWithText("YAML 格式校验通过").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_file_import_confirm").performClick()

        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun invalidLocalFilePreviewBlocksImportAndCanBeCancelled() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            KClashTheme {
                ProfileFileImportPreviewDialog(
                    fileName = "broken.yaml",
                    fileSizeBytes = 24,
                    isValidYaml = false,
                    busy = false,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("无法读取 YAML 配置，文件未导入").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_file_import_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("profile_file_import_cancel").performClick()

        assertFalse(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun addProfileDialogSwitchesToCustomAndInvokesCallback() {
        var added = false
        var customLabel: String? = null
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = { added = true; customLabel = it },
                )
            }
        }

        composeRule.onNodeWithTag("profile_add_source_custom").performClick()

        assertTrue(added)
        assertEquals("", customLabel)
        composeRule.onNodeWithTag("profile_add_custom_label").assertDoesNotExist()
    }

    @Test
    fun addProfileSourceMenuRoutesQrAndFileImportActions() {
        var pickedQr = false
        var pickedFile = false
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = { pickedFile = true },
                    onPickQrCode = { pickedQr = true },
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_add_source_qr").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("profile_add_source_file").assertIsDisplayed().performClick()

        assertTrue(pickedQr)
        assertTrue(pickedFile)
    }

    @Test
    fun addProfileSourceMenuOpensDedicatedRemoteSubscriptionForm() {
        var added: List<String>? = null
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { label, url -> added = listOf(label, url) },
                    onAddCustom = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_add_source_remote").performClick()
        composeRule.onNodeWithText("从链接导入").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_add_remote_label").performTextInput("我的订阅")
        composeRule.onNodeWithTag("profile_add_remote_url").performTextInput("https://example.invalid/sub")
        composeRule.onNodeWithText("导入").performClick()

        assertEquals(listOf("我的订阅", "https://example.invalid/sub"), added)
    }

    @Test
    fun batchProfileImportShowsDuplicateCountAndSubmitsOnlyNewUrls() {
        val existing = "https://existing.invalid/sub"
        val first = "https://first.invalid/sub"
        var added: List<String>? = null
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddRemoteBatch = { added = it },
                    existingUrls = setOf(existing),
                    onAddCustom = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_add_source_remote").performClick()
        composeRule.onNodeWithTag("profile_add_mode_batch").performClick()
        composeRule.onNodeWithTag("profile_add_remote_batch")
            .performTextInput("$first\n$existing\n$first")
        composeRule.onNodeWithText("识别到 1 个链接").assertIsDisplayed()
        composeRule.onNodeWithText("跳过已添加的重复项 1 个").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_add_submit").performClick()

        assertEquals(listOf(first), added)
    }

    @Test
    fun batchProfileImportBlocksInvalidEntriesWithoutShowingTheirContents() {
        var added = false
        composeRule.setContent {
            KClashTheme {
                AddProfileDialog(
                    busy = false,
                    onDismiss = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddRemoteBatch = { added = true },
                    onAddCustom = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_add_source_remote").performClick()
        composeRule.onNodeWithTag("profile_add_mode_batch").performClick()
        composeRule.onNodeWithTag("profile_add_remote_batch")
            .performTextInput("https://valid.invalid/sub\nfile:///private/config")

        composeRule.onNodeWithText("有 1 条链接格式无效，修正后才能导入").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
        composeRule.onAllNodesWithText("file:///private/config").assertCountEquals(0)
        assertEquals(false, added)
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

        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("mixed-port: 7890", saved)
    }

    @Test
    fun savedNewProfileOffersRenameWhenLeavingEditor() {
        var finished = false
        var selectedName: String? = "not-selected"
        composeRule.setContent {
            KClashTheme {
                ProfileEditorDialog(
                    profile = Profile.custom(10L, "未命名配置"),
                    initialContent = "mixed-port: 7890",
                    saving = false,
                    isNewDraft = true,
                    hasPersistedChanges = true,
                    onDismiss = {},
                    onDiscard = {},
                    onFinishNewDraft = { name ->
                        finished = true
                        selectedName = name
                    },
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.onNodeWithText("为配置命名").assertIsDisplayed()
        composeRule.onNodeWithTag("new_profile_name").performTextClearance()
        composeRule.onNodeWithTag("new_profile_name").performTextInput("家庭网络")
        composeRule.onNodeWithText("保存名称").performClick()

        assertTrue(finished)
        assertEquals("家庭网络", selectedName)
    }

    @Test
    fun savedNewProfileCanKeepDefaultNameWhenLeavingEditor() {
        var finished = false
        var selectedName: String? = "not-selected"
        composeRule.setContent {
            KClashTheme {
                ProfileEditorDialog(
                    profile = Profile.custom(11L, "未命名配置"),
                    initialContent = "mixed-port: 7890",
                    saving = false,
                    isNewDraft = true,
                    hasPersistedChanges = true,
                    onDismiss = {},
                    onDiscard = {},
                    onFinishNewDraft = { name ->
                        finished = true
                        selectedName = name
                    },
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.onNodeWithText("为配置命名").assertIsDisplayed()
        composeRule.onNodeWithText("保留默认名称").performClick()

        assertTrue(finished)
        assertEquals(null, selectedName)
    }

    @Test
    fun unsavedYamlRequiresConfirmationBeforeOpeningStructuredSection() {
        var opened = false
        composeRule.setContent {
            KClashTheme {
                ProfileEditorDialog(
                    profile = Profile.custom(12L, "结构化配置"),
                    initialContent = "mixed-port: 7890",
                    saving = false,
                    onDismiss = {},
                    onSave = {},
                    onOpenRulesEditor = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_editor_content").performTextInput("\nallow-lan: true")
        composeRule.onNodeWithTag("profile_editor_scroll").performScrollToNode(hasText("规则"))
        composeRule.onNodeWithText("规则").performClick()
        composeRule.onNodeWithText("原始 YAML 尚未保存").assertIsDisplayed()
        composeRule.onNodeWithText("继续编辑").performClick()
        assertEquals(false, opened)

        composeRule.onNodeWithTag("profile_editor_scroll").performScrollToNode(hasText("规则"))
        composeRule.onNodeWithText("规则").performClick()
        composeRule.onNodeWithText("放弃并继续").performClick()

        assertTrue(opened)
        composeRule.onNodeWithTag("profile_editor_content")
            .assertTextContains("mixed-port: 7890", substring = true)
    }

    @Test
    fun remoteProfileMenuOpensEditorAndEditorForwardsSubscriptionSettings() {
        val profile = Profile.remote(111L, "https://old.example/subscription", "远程配置")
        var showEditor by mutableStateOf(false)
        var saved: List<Any>? = null
        composeRule.setContent {
            KClashTheme {
                if (showEditor) {
                    RemoteProfileEditorDialog(
                        profile = profile,
                        saving = false,
                        onDismiss = {},
                        onSave = { label, url, autoUpdate, interval ->
                            saved = listOf(label, url, autoUpdate, interval)
                        },
                    )
                } else {
                    ProfileCard(
                        profile = profile,
                        selected = true,
                        canMutate = true,
                        onSelect = {},
                        onEdit = { showEditor = true },
                        onUpdate = {},
                        onDelete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithTag("profile_edit_${profile.id}").performClick()
        composeRule.onNodeWithText("编辑订阅").assertIsDisplayed()
        composeRule.onNodeWithTag("remote_profile_editor_label").performTextClearance()
        composeRule.onNodeWithTag("remote_profile_editor_label").performTextInput("新远程配置")
        composeRule.onNodeWithTag("remote_profile_editor_url").performTextClearance()
        composeRule.onNodeWithTag("remote_profile_editor_url").performTextInput("https://new.example/subscription")
        composeRule.onNodeWithTag("remote_profile_editor_interval").performTextClearance()
        composeRule.onNodeWithTag("remote_profile_editor_interval").performTextInput("45")
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(listOf("新远程配置", "https://new.example/subscription", true, 45L), saved)
    }

    @Test
    fun remoteProfileEditorDisablesIntervalWhenAutoUpdateIsOff() {
        val profile = Profile.remote(112L, "https://same.example/subscription", "远程配置")
        var saved: List<Any>? = null
        composeRule.setContent {
            KClashTheme {
                RemoteProfileEditorDialog(
                    profile = profile,
                    saving = false,
                    onDismiss = {},
                    onSave = { label, url, autoUpdate, interval ->
                        saved = listOf(label, url, autoUpdate, interval)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("remote_profile_editor_auto_update").performClick()
        composeRule.onNodeWithTag("remote_profile_editor_interval").assertDoesNotExist()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(listOf("远程配置", profile.url, false, 1_440L), saved)
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
        var batchGroup: String? = null
        var selected: String? = null
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
                    onSelect = { _, proxy -> selected = proxy },
                    onTestDelay = { _, _ -> },
                    onTestGroupDelay = { group -> batchGroup = group.name },
                )
            }
        }

        composeRule.onNodeWithContentDescription("批量测速").performClick()
        assertEquals("Auto", batchGroup)
        composeRule.onNodeWithTag("proxy_group_tab_Auto").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()
        assertEquals("node-b", selected)
        composeRule.onNodeWithTag("proxy_group_search_toggle").performClick()
        composeRule.onNodeWithText("搜索节点").performTextInput("node-b")
        assertTrue(composeRule.onAllNodesWithText("node-b").fetchSemanticsNodes().isNotEmpty())
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
        var previewed = false
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
                    onPreview = { previewed = true },
                )
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        for (action in listOf("编辑", "重命名", "删除")) {
            composeRule.onNodeWithText(action).assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("profile_preview_10").performClick()
        assertTrue(previewed)
    }
}
