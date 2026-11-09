package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/** 作者 long：自然时钟宿主只组合正式页面和固定领域对象，不接Room/网络/核心；外部录屏不能用Compose测试时钟替代真实帧。 */
class VisualMotionTestActivity : ComponentActivity() {
    internal var destination by mutableStateOf(MainDestination.DASHBOARD)
    var dark by mutableStateOf(false)
    var empty by mutableStateOf(false)
    var selectedProfile by mutableLongStateOf(901)
    var selectedGroup by mutableStateOf("常用节点")
    var nodeSelections = 0
    var profileSelections = 0
    var addRequests = 0
    var syncRequests = 0
    var showMoreEditor by mutableStateOf(false)
    private var moreState by mutableStateOf(ProfileMoreEditorUiState(Profile.custom(903, "动效参考")))
    private var showAdd by mutableStateOf(false)
    private var toolbarAction by mutableStateOf<ProfilesToolbarAction?>(null)
    private var selectedNode by mutableStateOf("香港节点")
    private var unfolded by mutableStateOf(setOf("常用节点", "自动选择"))
    private var qualityLoading by mutableStateOf(true)
    fun finishQuality() { qualityLoading = false }
    fun loadQuality() { qualityLoading = true }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val profiles = if (empty) emptyList() else listOf(
                Profile.remote(901, "https://example.test/never-requested", "常用线路 · 很长的订阅配置名称用于检查排版").copy(
                    subscriptionInfo = SubscriptionInfo(1_073_741_824, 2_147_483_648, 10_737_418_240, 0)),
                Profile.custom(902, "家庭配置"),
            )
            val groups = if (empty) emptyList() else listOf(
                ProxyGroupInfo("常用节点", "Selector", selectedNode, listOf("香港节点", "日本节点", "美国节点"), false),
                ProxyGroupInfo("自动选择", "URLTest", "美国节点", listOf("美国节点"), false),
            )
            val proxyState = ProxyGroupsUiState(groups = groups, selectedMap = mapOf("常用节点" to selectedNode), unfoldSet = unfolded)
            KClashTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    if (showMoreEditor) ProfileMoreEditor(moreState, "ntp", "", { showMoreEditor = false }) { ntp, sniffer ->
                        // 作者 long：自然录制复用正式添加面板；固定保存回执只驱动UI，不接正式Room或网络。
                        moreState = moreState.copy(ntp = ntp, sniffer = sniffer, revision = moreState.revision + 1)
                    } else MainNavigationHost(destination, MainDestination.entries, { destination = it }) { page ->
                        if (page == MainDestination.PROXY) ProxyGroupsCard(modifier = Modifier.statusBarsPadding(), state = proxyState, runtimeState = CoreRuntimeStatus.STOPPED,
                            currentProfileId = selectedProfile, currentGroupName = selectedGroup,
                            onSelectGroup = { selectedGroup = it }, onToggleGroup = { unfolded = if (it in unfolded) unfolded - it else unfolded + it },
                            onRefresh = {}, onSelect = { _, name -> nodeSelections++; selectedNode = name }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
                        else MainPageScaffold(page, { destination = it }, showNavigation = false, topBarActions = {
                            if (page == MainDestination.PROFILES) ProfilesTopBarActions(profiles, false, true,
                                onAdd = { showAdd = true; addRequests++ }, onAction = { toolbarAction = it })
                        }) { padding ->
                            if (page == MainDestination.PROFILES) Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                                ProfilesPanel(state = ProfilesUiState(profiles = profiles, currentProfileId = selectedProfile),
                                    editorState = ProfileEditorUiState(), runtimeState = CoreRuntimeStatus.STOPPED,
                                    showInlineToolbar = false, toolbarAction = toolbarAction, onToolbarActionHandled = { toolbarAction = null },
                                    showAddDialog = showAdd, onShowAddDialog = { showAdd = true; addRequests++ }, onDismissAddDialog = { showAdd = false },
                                    onPickFile = {}, onPickQrCode = {}, onAddRemote = { _, _ -> }, onAddCustom = {},
                                    onSelect = { selectedProfile = it; profileSelections++ }, onEdit = {}, onUpdate = { syncRequests++ },
                                    onCloseEditor = {}, onSaveEditor = {}, onDelete = {})
                            } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardProfileCard(profile = profiles.firstOrNull { it.id == selectedProfile }, profiles = profiles,
                                    currentProfileId = selectedProfile, onShowProfiles = { destination = MainDestination.PROFILES }, onAddProfile = { destination = MainDestination.PROFILES },
                                    onSelectProfile = { selectedProfile = it; profileSelections++ }, onUpdateProfile = { syncRequests++ })
                                OutboundIpPanel(OutboundIpUiState(info = OutboundIpInfo("203.0.113.7", "US"), qualityLoading = qualityLoading,
                                    quality = if (qualityLoading) null else IpQuality("203.0.113.7", IpQualitySource.IP_QUERY, IpType.HOSTING, "示例组织", 64512)),
                                    active = true, onRefresh = {}, onRefreshQuality = ::loadQuality)
                            }
                        }
                    }
                }
            }
        }
    }
}
