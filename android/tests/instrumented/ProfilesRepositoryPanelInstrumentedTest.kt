package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.KClashDatabase
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.ScriptRepository
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.data.SubscriptionClient
import com.lonnnnnng.flclash.data.SubscriptionDownload
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：使用真实 ProfileRepository、内存 Room 和应用文件目录验证页面写入边界，
 * 确保 fake Store 通过后，Profile 文件、数据库记录和当前选择仍能在同一条 UI 流程中收敛。
 */
@RunWith(AndroidJUnit4::class)
class ProfilesRepositoryPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryBackedPanelPersistsAndDeletesProfile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ProfileRepository(
            context = context,
            database = database,
            subscriptionClient = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload =
                    error("该页面流程不访问远程订阅")
            },
        )
        val runtime = RepositoryPanelRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val viewModel = ProfilesViewModel(repository, runtime)
        var showAddDialog by mutableStateOf(false)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            val editorState by viewModel.editorState.collectAsState()
            val rulesEditorState by viewModel.rulesEditorState.collectAsState()
            val runtimeState by runtime.state.collectAsState()
            KClashTheme {
                ProfilesPanel(
                    state = state,
                    editorState = editorState,
                    runtimeState = runtimeState,
                    showAddDialog = showAddDialog,
                    onShowAddDialog = { showAddDialog = true },
                    onDismissAddDialog = { showAddDialog = false },
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { label, url ->
                        showAddDialog = false
                        viewModel.addRemote(url, label)
                    },
                    onAddCustom = { label ->
                        showAddDialog = false
                        viewModel.addCustom(label)
                    },
                    onSelect = viewModel::select,
                    onEdit = viewModel::openEditor,
                    onUpdate = viewModel::update,
                    onCloseEditor = viewModel::closeEditor,
                    onSaveEditor = viewModel::saveEditor,
                    onDelete = viewModel::delete,
                    rulesEditorState = rulesEditorState,
                    onOpenRulesEditor = viewModel::openRulesEditor,
                    onCloseRulesEditor = viewModel::closeRulesEditor,
                    onSaveRules = viewModel::saveRules,
                    proxiesEditorState = viewModel.proxiesEditorState.collectAsState().value,
                    onOpenProxiesEditor = viewModel::openProxiesEditor,
                    onCloseProxiesEditor = viewModel::closeProxiesEditor,
                    onSaveProxies = viewModel::saveProxies,
                )
            }
        }

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("未命名配置").fetchSemanticsNodes().isNotEmpty()
        }

        val createdProfile = repository.profiles.value.single()
        assertEquals(1, runBlocking { database.profiles().getAll().size })
        assertEquals(createdProfile.id, repository.currentProfileId.value)
        assertTrue(repository.profileFile(createdProfile.id).isFile)

        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 未命名配置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 未命名配置").fetchSemanticsNodes().isEmpty()
        }
        assertEquals("mixed-port: 7890", runBlocking {
            repository.readLocalProfileContent(createdProfile.id)
        })
        assertEquals(createdProfile.id, runBlocking {
            database.profiles().getById(createdProfile.id)?.id
        })

        composeRule.onNodeWithText("高级").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · 未命名配置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("规则 YAML").performTextInput("DOMAIN,example.com,DIRECT")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · 未命名配置").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(
            "mixed-port: 7890\nrules:\n  - DOMAIN,example.com,DIRECT\n",
            runBlocking { repository.readLocalProfileContent(createdProfile.id) },
        )

        composeRule.onNodeWithText("代理").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · 未命名配置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理 YAML").performTextInput("- name: node\n  type: vmess")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · 未命名配置").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(
            "mixed-port: 7890\nrules:\n  - DOMAIN,example.com,DIRECT\nproxies:\n  - name: node\n    type: vmess\n",
            runBlocking { repository.readLocalProfileContent(createdProfile.id) },
        )

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“未命名配置”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").assertCountEquals(2)
        composeRule.onAllNodesWithText("删除")[1].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("还没有配置").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking { database.profiles().getAll().size })
        assertEquals(null, repository.currentProfileId.value)
        assertFalse(repository.profileFile(createdProfile.id).exists())
    }

    @Test
    fun repositoryPersistsProviderOverridesForActiveProfile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ProfileRepository(
            context = context,
            database = database,
            subscriptionClient = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload =
                    error("该持久化流程不访问远程订阅")
            },
        )

        val profile = runBlocking { repository.addCustomProfile("Provider 持久化") }
        val overrides = ProfileProviderOverrides(
            mapOf("cloud" to ProxyProviderOverride(timeout = 2_000, udp = false)),
        )
        runBlocking { repository.updateProfileOverrides(profile.id, overrides.toJsonString()) }

        assertEquals(
            overrides.proxyProviders,
            ProfileProviderOverrides.fromJsonString(
                runBlocking { database.profiles().getById(profile.id)?.overridesJson },
            ).proxyProviders,
        )
        assertEquals(overrides.toJsonString(), runBlocking { repository.activeProfile().overridesJson })
    }

    @Test
    fun repositoryPersistsDnsOverridesAlongsideProviderOverrides() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ProfileRepository(
            context = context,
            database = database,
            subscriptionClient = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload =
                    error("该 DNS 持久化流程不访问远程订阅")
            },
        )

        val profile = runBlocking { repository.addCustomProfile("DNS 持久化") }
        val overrides = ProfileProviderOverrides(
            proxyProviders = mapOf("cloud" to ProxyProviderOverride(timeout = 2_000)),
            dns = ProfileDnsOverrides(
                enable = true,
                enhancedMode = "fake-ip",
                nameserver = listOf("https://doh.example/dns-query"),
            ),
        )
        runBlocking { repository.updateProfileOverrides(profile.id, overrides.toJsonString()) }

        val decoded = ProfileProviderOverrides.fromJsonString(
            runBlocking { repository.activeProfile().overridesJson },
        )
        assertEquals(overrides.proxyProviders, decoded.proxyProviders)
        assertEquals(overrides.dns, decoded.dns)
        database.close()
    }

    @Test
    fun repositoryPersistsAndValidatesProfileScriptBinding() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ProfileRepository(
            context = context,
            database = database,
            subscriptionClient = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload =
                    error("该绑定流程不访问远程订阅")
            },
        )
        val script = runBlocking {
            ScriptRepository(context, database).create("绑定脚本", "const main = (config) => config;")
        }
        val profile = runBlocking { repository.addCustomProfile("脚本配置") }

        runBlocking { repository.updateProfileScript(profile.id, script.id) }
        assertEquals(
            script.id,
            runBlocking { database.profiles().getById(profile.id)?.scriptId },
        )

        val missing = runCatching {
            runBlocking { repository.updateProfileScript(profile.id, 999_999L) }
        }.exceptionOrNull()
        assertTrue(missing is IllegalArgumentException)

        runBlocking { repository.updateProfileScript(profile.id, null) }
        assertEquals(
            null,
            runBlocking { database.profiles().getById(profile.id)?.scriptId },
        )
    }

    @Test
    fun repositoryReordersProfilesAndKeepsCurrentSelection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ProfileRepository(
            context = context,
            database = database,
            subscriptionClient = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload =
                    error("该排序流程不访问远程订阅")
            },
        )

        val first = runBlocking { repository.addCustomProfile("第一项") }
        val second = runBlocking { repository.addCustomProfile("第二项") }
        val third = runBlocking { repository.addCustomProfile("第三项") }
        composeRule.waitUntil(5_000) { repository.profiles.value.size == 3 }

        runBlocking {
            repository.selectProfile(second.id)
            repository.reorder(listOf(third.id, first.id))
        }
        composeRule.waitUntil(5_000) {
            repository.profiles.value.map { it.id } == listOf(third.id, first.id, second.id)
        }

        assertEquals(second.id, repository.currentProfileId.value)
        assertEquals(
            listOf(third.id, first.id, second.id),
            runBlocking { database.profiles().getAll().map { it.id } },
        )
    }
}

private class RepositoryPanelRuntimeStatusProvider(
    status: CoreRuntimeStatus,
) : CoreRuntimeStatusProvider {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
}
