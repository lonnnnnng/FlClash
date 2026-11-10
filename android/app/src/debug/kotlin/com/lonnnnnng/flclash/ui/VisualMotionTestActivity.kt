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
import androidx.compose.ui.platform.testTag
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
    var moreSection by mutableStateOf("ntp")
    var moreSaveRequests = 0
    var showDnsPage by mutableStateOf(false)
    var showGroupEditor by mutableStateOf(false)
    var groupSaveRequests = 0
    var savedGroup: ProxyGroupDraft? = null
    private var dnsQuery by mutableStateOf("")
    private var dnsSearchExpanded by mutableStateOf(false)
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
        // 作者 long：非空图库使用有效PNG字节验证解码与转场，避免把失败占位图冒充已加载图片。
        val image = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val encoded = java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val gallery = ResourceIconSet("motion", "非空参考图库", icons = listOf(ResourceIcon("Red", "data:image/png;base64," + android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP))), updatedAt = 1)
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
                    if (showGroupEditor) CompositionLocalProvider(LocalResourceIconSets provides listOf(gallery)) {
                        val source = """{"proxies":[{"name":"Local","type":"direct"}],"proxy-groups":[{"name":"Main","type":"select","proxies":["DIRECT","Local"]}]}"""
                        VisualProxyGroupsEditor(Profile.custom(3104, "Sheet动效"), "", source, false, initialGroupIndex = 0,
                            onDismiss = { showGroupEditor = false }, onSave = {},
                            onSaveDraft = { groupSaveRequests++; savedGroup = it.groups.single(); showGroupEditor = false })
                    }
                    if (showMoreEditor) ProfileMoreEditor(moreState, moreSection, "", { showMoreEditor = false }) { ntp, sniffer ->
                        // 作者 long：自然录制复用正式添加面板；固定保存回执只驱动UI，不接正式Room或网络。
                        moreSaveRequests++
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
                            } else if (page == MainDestination.TOOLS) Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                                androidx.compose.material3.TextButton(onClick = { showDnsPage = true }, modifier = Modifier.testTag("tools_dns_open")) {
                                    androidx.compose.material3.Text("DNS 查询")
                                }
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
                    if (showDnsPage) ObservationListPage(
                        title = "DNS 查询", searchLabel = "搜索 DNS", searchQuery = dnsQuery,
                        searchExpanded = dnsSearchExpanded, keywords = listOf("A", "AAAA"),
                        onDismiss = { showDnsPage = false; dnsQuery = ""; dnsSearchExpanded = false },
                        onSearchQueryChange = { dnsQuery = it }, onSearchExpandedChange = { dnsSearchExpanded = it },
                        onRemoveKeyword = {}, content = { androidx.compose.material3.Text("${dnsQuery.ifBlank { "resolver.example.test" }} · A") },
                    )
                }
            }
        }
    }
}
