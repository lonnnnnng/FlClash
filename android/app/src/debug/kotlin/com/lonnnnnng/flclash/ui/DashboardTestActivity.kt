package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.SubscriptionInfo
import com.lonnnnnng.flclash.data.OutboundIpInfo
import com.lonnnnnng.flclash.data.IpQuality
import com.lonnnnnng.flclash.data.IpQualitySource
import com.lonnnnnng.flclash.data.IpSourceFailure
import com.lonnnnnng.flclash.data.IpType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：Dashboard 截图只渲染 Flutter Android 默认五张正式卡片，
 * 不读取 Room、设备权限、已安装应用或 Mihomo 核心，避免把设备环境差异写入视觉基线。
 */
class DashboardTestActivity : ComponentActivity() {
    private var scenario by mutableStateOf(DashboardScenario.DEFAULT)
    private var useDarkTheme by mutableStateOf(true)
    private var selectedProfileId by mutableStateOf<Long?>(null)
    private var isProfileSyncing by mutableStateOf(false)
    private var profilePickerRuntimeStopped by mutableStateOf(false)
    private var outboundMode by mutableStateOf(CoreOutboundMode.RULE)
    private var proxyState by mutableStateOf(ProxyGroupsUiState())
    private var currentGroupName by mutableStateOf("机场选择")
    private var outboundIpState by mutableStateOf(fixedOutboundIp())
    private var statistics by mutableStateOf(ProfileStatisticsUiState())
    private var providerState by mutableStateOf(ProfileProvidersUiState())
    var providerUpdateCount = 0
        private set
    var providerRetryCount = 0
        private set
    var statisticsRetries = 0
        private set
    private var configPreview by mutableStateOf(RuntimeConfigPreviewUiState())
    private var memoryState by mutableStateOf(fixedMemoryState())
    private var vpnState by mutableStateOf(com.lonnnnnng.flclash.service.VpnSettingsState())
    private var vpnBusy by mutableStateOf(false)
    private var serviceState by mutableStateOf(ServiceStatusUiState(settingsLoading = false))
    var serviceOrderSaveCount = 0
        private set
    val serviceSettings get() = serviceState.settings
    var serviceCheckCount = 0
        private set
    var vpnChangeCount = 0
        private set
    var vpnRestartCount = 0
        private set
    private var intranetState by mutableStateOf(IntranetIpUiState())
    var intranetChangeCount = 0
        private set
    var intranetCopyCount = 0
        private set
    var memoryReleaseCount = 0
        private set
    var ipRefreshCount = 0
        private set
    var qualityRefreshCount = 0
        private set
    var proxySelectionCount = 0
        private set
    var profilesNavigationCount = 0
        private set
    var runtimeStopCount = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            scenario = DashboardScenario.valueOf(it.getString("dashboard_scenario", DashboardScenario.DEFAULT.name))
            useDarkTheme = it.getBoolean("dashboard_dark_theme", true)
            if (scenario == DashboardScenario.PROXY_GROUPS) {
                // 作者 long：正式数据由仓库在重建后回供；夹具也须回供同一选组和节点，才能独立验收面板的保存状态。
                currentGroupName = it.getString("dashboard_proxy_group", "机场选择")
                val selections = it.getBundle("dashboard_proxy_selections") ?: Bundle()
                proxyState = ProxyGroupsUiState(
                    groups = dashboardProxyGroups(),
                    selectedMap = selections.keySet().mapNotNull { group ->
                        selections.getString(group)?.let { name -> group to name }
                    }.toMap(),
                    unfoldSet = it.getStringArrayList("dashboard_proxy_unfold").orEmpty().toSet(),
                )
                proxySelectionCount = it.getInt("dashboard_proxy_selection_count")
            }
        }
        setContent {
            val running = scenario == DashboardScenario.RUNNING || scenario == DashboardScenario.PROXY_GROUPS
                || scenario == DashboardScenario.RUNNING_PROFILE_PICKER && !profilePickerRuntimeStopped
            val homeProfiles = when (scenario) {
                DashboardScenario.PROFILE_PICKER, DashboardScenario.RUNNING_PROFILE_PICKER -> dashboardPickerProfiles()
                DashboardScenario.RUNNING, DashboardScenario.PROXY_GROUPS -> listOf(runningProfile())
                else -> emptyList()
            }
            val currentProfileId = when (scenario) {
                DashboardScenario.PROFILE_PICKER, DashboardScenario.RUNNING_PROFILE_PICKER -> selectedProfileId
                DashboardScenario.RUNNING, DashboardScenario.PROXY_GROUPS -> 99L
                else -> null
            }
            val runtimeState = if (scenario == DashboardScenario.RUNNING_PROFILE_PICKER) {
                if (profilePickerRuntimeStopped) CoreRuntimeStatus.STOPPED else CoreRuntimeStatus.RUNNING
            } else if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED
            KClashTheme(darkTheme = useDarkTheme) {
                RuntimeConfigPreviewSheet(
                    state = configPreview,
                    onRetry = { configPreview = RuntimeConfigPreviewUiState(configPreview.profileId, isLoading = true) },
                    onClose = { configPreview = RuntimeConfigPreviewUiState() },
                )
                MainPageScaffold(
                    selectedDestination = MainDestination.DASHBOARD,
                    onDestinationSelected = {},
                    // 作者 long：固定宿主也遵循正式导航门控，空配置截图不得显示代理 Tab。
                    destinations = mainDestinations(homeProfiles.isNotEmpty() || proxyState.groups.isNotEmpty()),
                    modifier = Modifier.testTag("dashboard_page"),
                ) { insets ->
                    DashboardWidth { horizontalInset ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(insets)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = horizontalInset, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (scenario == DashboardScenario.SERVICES) DashboardServiceStatusCard(serviceState,
                                onCheck = { targets ->
                                    serviceCheckCount++
                                    serviceState = serviceState.copy(entries = serviceState.entries + targets.associateWith { ServiceEntry(loading = true) })
                                },
                                onToggle = { target, enabled ->
                                    serviceState = serviceState.copy(settings = serviceState.settings.copy(disabled =
                                        if (enabled) serviceState.settings.disabled - target else serviceState.settings.disabled + target))
                                },
                                onMove = { target, offset ->
                                    val order = serviceState.settings.order.toMutableList()
                                    val index = order.indexOf(target)
                                    order.add(index + offset, order.removeAt(index))
                                    serviceState = serviceState.copy(settings = serviceState.settings.copy(order = order))
                                }, onDetailVisible = {}, onRetrySettings = {},
                                onReorder = { order -> serviceOrderSaveCount++; serviceState = serviceState.copy(settings = serviceState.settings.copy(order = order)) },
                                onRefreshExit = { node -> serviceState = serviceState.copy(exits = serviceState.exits + (node to OutboundIpUiState(isLoading = true))) },
                                onOpenQuality = { node -> serviceState = serviceState.copy(qualityNode = node, exits = serviceState.exits + (node to serviceState.exits.getValue(node).copy(qualityLoading = true))) },
                                onCloseQuality = { serviceState = serviceState.copy(qualityNode = null) },
                                onRefreshQuality = { serviceState.qualityNode?.let { node -> serviceState = serviceState.copy(exits = serviceState.exits + (node to serviceState.exits.getValue(node).copy(qualityLoading = true))) } },
                            )
                            if (scenario == DashboardScenario.VPN) DashboardVpnCard(vpnState, vpnBusy,
                                { vpnChangeCount++; vpnState = vpnState.copy(enabled = it) },
                                { vpnRestartCount++ },
                            )
                            if (scenario == DashboardScenario.INTRANET) DashboardIntranetIpCard(intranetState,
                                onAllowLan = { intranetChangeCount++; intranetState = intranetState.copy(saving = true) },
                                onCopy = { intranetCopyCount++ },
                                onRetry = { intranetState = intranetState.copy(local = com.lonnnnnng.flclash.data.LocalAddressState()) },
                            )
                            if (scenario == DashboardScenario.MEMORY) DashboardMemoryCard(memoryState) {
                                memoryReleaseCount++
                                memoryState = memoryState.copy(isReleasing = true, error = null, feedback = null)
                            }
                            RuntimeCard(
                                state = runtimeState,
                                uptimeMillis = if (running) 3_661_000L else null,
                                outboundIp = outboundIpState,
                                onRefreshIp = { ipRefreshCount++; outboundIpState = outboundIpState.copy(isLoading = true, error = null) },
                                onRefreshIpQuality = { qualityRefreshCount++; outboundIpState = outboundIpState.copy(qualityLoading = true, qualityFailures = emptyMap()) },
                                onStart = {},
                                onStop = {
                                    runtimeStopCount++
                                    profilePickerRuntimeStopped = true
                                },
                            )
                            RuntimeStatsCard(
                                state = if (running) runningStats() else RuntimeStatsUiState(),
                                runtimeState = runtimeState,
                            )
                            OutboundModeCard(
                                mode = outboundMode,
                                onModeChange = { outboundMode = it },
                            )
                            DashboardProfileCard(
                                profile = homeProfiles.firstOrNull { it.id == currentProfileId },
                                profiles = homeProfiles,
                                currentProfileId = currentProfileId,
                                isBusy = isProfileSyncing,
                                runtimeState = runtimeState,
                                onShowProfiles = { profilesNavigationCount++ },
                                onAddProfile = {},
                                onSelectProfile = { selectedProfileId = it },
                                onUpdateProfile = { isProfileSyncing = true },
                                onPreviewConfig = { configPreview = RuntimeConfigPreviewUiState(it.id, isLoading = true) },
                                statistics = statistics.copy(profileId = currentProfileId),
                                providers = providerState.copy(profileId = currentProfileId),
                                onRetryProviders = { providerRetryCount++; providerState = providerState.copy(isLoading = true, error = null) },
                                onUpdateProvider = { providerUpdateCount++; providerState = providerState.copy(updatingName = it, updateMessage = null) },
                                onRetryStatistics = { statisticsRetries++; statistics = statistics.copy(isLoading = true, error = null) },
                                onStopRuntime = {
                                    runtimeStopCount++
                                    profilePickerRuntimeStopped = true
                                },
                            )
                            DashboardProxyGroupsCard(
                                state = proxyState,
                                runtimeState = runtimeState,
                                mode = outboundMode,
                                currentProfileId = currentProfileId,
                                currentGroupName = currentGroupName,
                                onRefresh = {},
                                onSelectGroup = { currentGroupName = it },
                                onToggleGroup = { name ->
                                    currentGroupName = name
                                    proxyState = proxyState.copy(
                                        unfoldSet = proxyState.unfoldSet.toMutableSet().apply {
                                            if (!add(name)) remove(name)
                                        },
                                    )
                                },
                                onSelect = { group, name ->
                                    proxyState = proxyState.copy(selectedMap = proxyState.selectedMap + (group.name to name))
                                    proxySelectionCount++
                                },
                                onTestDelay = { _, _ -> },
                                onTestGroupDelay = {},
                            )
                        }
                    }
                }
            }
        }
    }

    fun showProfileProviders(phase: String, dark: Boolean = true) {
        showProfileStatistics("ready", dark)
        val rows = listOf(
            com.lonnnnnng.flclash.core.CoreExternalProvider("香港节点资源 · 示例长名称用于检查换行与更新按钮", "Proxy", "HTTP", 1024, null),
            com.lonnnnnng.flclash.core.CoreExternalProvider("本地规则", "Rule", "File", 70000, null))
        statistics = statistics.copy(counts = statistics.counts?.copy(providers = rows.size.toLong()))
        providerState = when (phase) {
            "loading" -> ProfileProvidersUiState(isLoading = true)
            "error" -> ProfileProvidersUiState(error = "读取 Providers 失败，请重试")
            "empty" -> ProfileProvidersUiState(providers = emptyList())
            "failed" -> ProfileProvidersUiState(providers = rows, updateMessage = "更新服务返回异常，请稍后重试", updateFailed = true)
            "updated" -> ProfileProvidersUiState(providers = rows, updateMessage = "Provider 已更新")
            else -> ProfileProvidersUiState(providers = rows)
        }
    }

    fun showProfileStatistics(phase: String, dark: Boolean = true) {
        showProfilePickerScenario()
        useDarkTheme = dark
        statistics = when (phase) {
            "loading" -> ProfileStatisticsUiState(isLoading = true)
            "error" -> ProfileStatisticsUiState(error = "读取配置统计失败，请重试")
            "empty" -> ProfileStatisticsUiState(counts = com.lonnnnnng.flclash.service.ProfileStatistics(101, 0, 0, 0, 0))
            else -> ProfileStatisticsUiState(counts = com.lonnnnnng.flclash.service.ProfileStatistics(101, 12, 10024, 70000, 9))
        }
    }

    fun showVpn(enabled: Boolean, applied: Boolean?, busy: Boolean = false, dark: Boolean = true) {
        scenario = DashboardScenario.VPN
        vpnState = com.lonnnnnng.flclash.service.VpnSettingsState(enabled, applied)
        vpnBusy = busy
        useDarkTheme = dark
    }

    fun showServices(dark: Boolean = true) {
        scenario = DashboardScenario.SERVICES
        useDarkTheme = dark
        // 作者 long：固定结果只验证全部状态和操作呈现，真实可用性另走正式运行管理器。
        serviceState = ServiceStatusUiState(settingsLoading = false, entries = com.lonnnnnng.flclash.core.ServiceTarget.entries
            .mapIndexed { index, target -> target to ServiceEntry(com.lonnnnnng.flclash.core.CoreServiceCheck(
                target, com.lonnnnnng.flclash.core.ServiceStatus.entries[index % 10], 28L, "US",
                listOf("DIRECT"), 1L, com.lonnnnnng.flclash.core.CoreRouteStamp(1L, 1L))) }.toMap())
    }

    fun showServiceExit(phase: String = "ready", ip: String = "203.0.113.7", dark: Boolean = true) {
        showServices(dark)
        val exit = when (phase) {
            "loading" -> OutboundIpUiState(isLoading = true)
            "failed" -> OutboundIpUiState(error = "出口 IP 查询失败，请重试")
            else -> OutboundIpUiState(info = OutboundIpInfo(ip, "US"))
        }
        serviceState = serviceState.copy(exits = mapOf("DIRECT" to exit))
    }
    fun finishServiceQuality(success: Boolean) {
        val node = serviceState.qualityNode ?: return
        val entry = serviceState.exits.getValue(node)
        serviceState = serviceState.copy(exits = serviceState.exits + (node to entry.copy(qualityLoading = false,
            quality = if (success) IpQuality(checkNotNull(entry.info).ip, IpQualitySource.IP_QUERY, IpType.HOSTING, "示例组织", 64512) else null,
            qualityFailures = if (success) emptyMap() else mapOf(IpQualitySource.IP_QUERY to IpSourceFailure.RATE_LIMITED))))
    }

    fun showIntranetScenario(address: String? = "192.168.1.10", loading: Boolean = false, error: Boolean = false) {
        scenario = DashboardScenario.INTRANET
        intranetState = IntranetIpUiState(
            local = com.lonnnnnng.flclash.data.LocalAddressState(address, loading, if (error) "无法读取内网地址，请重试" else null),
            sharing = com.lonnnnnng.flclash.service.LanSharingState(com.lonnnnnng.flclash.core.CoreLanConfig(false, 7890), true),
            settingsLoading = false,
        )
    }
    fun finishIntranetSave(success: Boolean) {
        intranetState = if (success) intranetState.copy(saving = false, error = null,
            sharing = intranetState.sharing.copy(config = intranetState.sharing.config.copy(allowLan = true)))
        else intranetState.copy(saving = false, error = "局域网设置失败，请重试")
    }

    fun showMemoryScenario() {
        scenario = DashboardScenario.MEMORY
        memoryState = fixedMemoryState()
    }

    fun finishMemoryRelease(success: Boolean) {
        memoryState = if (success) fixedMemoryState().copy(
            snapshot = fixedMemoryState().snapshot?.copy(rss = 96L * 1024 * 1024),
            feedback = "已请求回收，内存数据已刷新",
        ) else memoryState.copy(isReleasing = false, error = "释放内存失败，请重试")
    }

    fun showSystemReadyScenario() {
        scenario = DashboardScenario.SYSTEM_READY
        outboundIpState = OutboundIpUiState()
    }

    fun finishConfigPreview(success: Boolean, applied: Boolean = false) {
        val id = configPreview.profileId ?: return
        configPreview = if (success) RuntimeConfigPreviewUiState(id, preview =
            com.lonnnnnng.flclash.service.RuntimeConfigPreview(id, "固定预览配置", "mixed-port: 7890\nmode: rule\nrules: []\n", applied))
        else RuntimeConfigPreviewUiState(id, error = "读取运行配置失败，请重试")
    }

    fun finishIpRefresh(success: Boolean) {
        outboundIpState = if (success) fixedOutboundIp() else outboundIpState.copy(isLoading = false, error = "出口查询失败")
    }

    fun failQualityRefresh() {
        outboundIpState = outboundIpState.copy(qualityLoading = false,
            qualityFailures = mapOf(IpQualitySource.IDENT_ME to IpSourceFailure.TIMEOUT))
    }

    fun showInitialIpFailure() {
        scenario = DashboardScenario.RUNNING
        outboundIpState = OutboundIpUiState(error = "出口查询失败")
    }

    fun showRunningScenario() {
        outboundIpState = fixedOutboundIp()
        scenario = DashboardScenario.RUNNING
    }

    fun showProfilePickerScenario() {
        selectedProfileId = 101L
        isProfileSyncing = false
        scenario = DashboardScenario.PROFILE_PICKER
    }

    fun showRunningProfilePickerScenario() {
        selectedProfileId = 101L
        isProfileSyncing = false
        profilePickerRuntimeStopped = false
        runtimeStopCount = 0
        scenario = DashboardScenario.RUNNING_PROFILE_PICKER
    }

    fun showProxyGroupsScenario() {
        outboundMode = CoreOutboundMode.RULE
        currentGroupName = "机场选择"
        proxySelectionCount = 0
        proxyState = ProxyGroupsUiState(groups = dashboardProxyGroups())
        scenario = DashboardScenario.PROXY_GROUPS
    }

    fun setDarkTheme(value: Boolean) {
        useDarkTheme = value
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("dashboard_scenario", scenario.name)
        outState.putBoolean("dashboard_dark_theme", useDarkTheme)
        if (scenario == DashboardScenario.PROXY_GROUPS) {
            outState.putString("dashboard_proxy_group", currentGroupName)
            outState.putBundle("dashboard_proxy_selections", Bundle().apply {
                proxyState.selectedMap.forEach { (group, name) -> putString(group, name) }
            })
            outState.putStringArrayList("dashboard_proxy_unfold", ArrayList(proxyState.unfoldSet))
            outState.putInt("dashboard_proxy_selection_count", proxySelectionCount)
        }
        super.onSaveInstanceState(outState)
    }
}

private enum class DashboardScenario {
    SERVICES,
    DEFAULT,
    MEMORY,
    VPN,
    INTRANET,
    SYSTEM_READY,
    RUNNING,
    PROFILE_PICKER,
    RUNNING_PROFILE_PICKER,
    PROXY_GROUPS,
}

// 作者 long：主页代理入口只验证正式节点组件与回调，固定数据不读取真实订阅、不发起测速或核心请求。
private fun dashboardProxyGroups() = listOf(
    ProxyGroupInfo("机场选择", "Selector", "香港节点", listOf("香港节点", "日本节点") + (1..24).map { "备用节点 $it" }, false),
    ProxyGroupInfo("自动选择", "URLTest", "香港节点", listOf("香港节点", "日本节点"), false),
    ProxyGroupInfo("流媒体", "Selector", "日本节点", listOf("香港节点", "日本节点"), false),
    ProxyGroupInfo("下载", "Selector", "香港节点", listOf("香港节点", "日本节点"), false),
    ProxyGroupInfo("其他", "Selector", "香港节点", listOf("香港节点", "日本节点"), false),
    ProxyGroupInfo("隐藏组", "Selector", "香港节点", listOf("香港节点"), true),
)

// 作者 long：首页配置交互夹具使用固定的远程与自定义配置，不触发订阅网络或 Mihomo 核心。
private fun dashboardPickerProfiles() = listOf(
    Profile.remote(101L, "https://fixture.invalid/subscription", "固定远程订阅").copy(
        lastUpdateAt = System.currentTimeMillis() - 3_600_000L,
        subscriptionInfo = SubscriptionInfo(
            upload = 2L * 1024 * 1024,
            download = 3L * 1024 * 1024,
            total = 10L * 1024 * 1024,
            expire = System.currentTimeMillis() / 1000 + 86_400L,
        ),
    ),
    Profile.custom(102L, "固定自定义配置"),
)

// 作者 long：运行态视觉夹具使用固定流量与配置，验证默认首页的排版，不触发真实网络或核心调用。
private fun runningStats() = RuntimeStatsUiState(
    traffic = CoreTraffic(up = 12_345L, down = 67_890L),
    totalTraffic = CoreTraffic(up = 1_234_567L, down = 7_654_321L),
    connectionCount = 3,
    trafficHistory = (1L..30L).map { index ->
        CoreTraffic(up = index * 411L, down = (30 - index) * 2_263L)
    },
)

private fun runningProfile() = Profile.remote(
    id = 99L,
    url = "https://example.invalid/kclash-dashboard-fixture",
    label = "固定诊断样例",
)

// 作者 long：文档保留地址只用于界面夹具，质量标签不代表该地址的真实网络属性。
private fun fixedOutboundIp() = OutboundIpUiState(
    info = OutboundIpInfo("203.0.113.7", "US"),
    quality = IpQuality("203.0.113.7", IpQualitySource.IDENT_ME, IpType.HOSTING, "示例网络", 64512),
)

// 作者 long：固定内存仅供深浅色与交互验收，真实 Go 数据另由正式 MainActivity 验证。
private fun fixedMemoryState() = MemoryUiState(snapshot = com.lonnnnnng.flclash.core.CoreMemoryStats(
    rss = 128L * 1024 * 1024, heapInuse = 24L * 1024 * 1024,
    heapIdle = 8L * 1024 * 1024, stackInuse = 2L * 1024 * 1024, runtimeOther = 6L * 1024 * 1024,
))
