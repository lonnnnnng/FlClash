package com.lonnnnnng.flclash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class ProxyToolsMenuPage {
    ROOT,
    DISPLAY_MODE,
    SORT,
    DENSITY,
    CARD_SIZE,
    ICON_STYLE,
}

// 作者 long：代理页与 Flutter SurfaceLevel 使用同一层级；Tab 卡片在页面上一层，List 节点在组容器上一层。
@Composable
internal fun proxyPageColor() = dashboardPageColor()

@Composable
internal fun proxyNodeColor(insideGroup: Boolean) = if (insideGroup) {
    if (com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainer
} else dashboardCardColor()

@Composable
private fun ProxyPageToolbar(
    query: String,
    searchExpanded: Boolean,
    picker: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    primaryAction: @Composable RowScope.() -> Unit,
    secondaryActions: @Composable RowScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    // 作者 long：选择器开场不抢键盘，完整页主动展开搜索才请求焦点，避免遮住当前节点。
    LaunchedEffect(searchExpanded, picker) {
        if (searchExpanded && !picker) focus.requestFocus()
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp)
                .testTag("proxy_page_toolbar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                Surface(Modifier.weight(1f).height(44.dp), shape = CircleShape, color = dashboardCardColor()) {
                    Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus).testTag("proxy_group_search"),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            decorationBox = { field ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text("搜索节点", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp))
                                    field()
                                }
                            },
                        )
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") },
                            modifier = Modifier.size(44.dp).testTag("proxy_search_clear")) {
                            Icon(Icons.Outlined.Close, "清除搜索", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Spacer(Modifier.width(12.dp))
                    }
                }
                if (!picker) IconButton(onClick = { onSearchExpandedChange(false) },
                    modifier = Modifier.testTag("proxy_group_search_toggle")) {
                    Icon(Icons.Outlined.Close, "关闭节点搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("代理", Modifier.weight(1f), style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { onSearchExpandedChange(true) }, modifier = Modifier.testTag("proxy_group_search_toggle")) {
                    Icon(Icons.Outlined.Search, "搜索节点", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            primaryAction()
            if (!searchExpanded && !picker) secondaryActions()
        }
    }
}

@Composable
private fun ProxyGroupTabs(names: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val tabsState = rememberLazyListState()
    var showGroups by remember { mutableStateOf(false) }
    val hasOverflow by remember { derivedStateOf { tabsState.canScrollForward || tabsState.canScrollBackward } }
    LaunchedEffect(selectedIndex, names) {
        if (selectedIndex in names.indices) tabsState.animateScrollToItem(selectedIndex)
    }
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = tabsState,
            modifier = Modifier.weight(1f).testTag("proxy_group_tabs"),
            contentPadding = PaddingValues(start = 16.dp, end = if (hasOverflow) 0.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(names, key = { _, name -> name }) { index, name ->
                val active = index == selectedIndex
                Surface(
                    modifier = Modifier.height(36.dp).widthIn(min = 64.dp)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
                        .testTag("proxy_group_tab_" + name),
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else dashboardCardColor(),
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
        if (hasOverflow) Box(Modifier.padding(start = 8.dp, end = 16.dp)) {
            IconButton(onClick = { showGroups = true },
                modifier = Modifier.background(dashboardCardColor(), CircleShape).testTag("proxy_group_more")) {
                Icon(Icons.Outlined.ExpandMore, "选择代理组")
            }
            DropdownMenu(expanded = showGroups, onDismissRequest = { showGroups = false }) {
                names.forEachIndexed { index, name ->
                    DropdownMenuItem(text = { Text(name) },
                        trailingIcon = { if (index == selectedIndex) Icon(Icons.Outlined.Check, "已选择") },
                        onClick = { showGroups = false; onSelect(index) }, modifier = Modifier.testTag("proxy_group_menu_" + name))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyGroupsCard(
    modifier: Modifier = Modifier,
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    currentProfileId: Long? = null,
    currentGroupName: String? = null,
    mode: CoreOutboundMode = CoreOutboundMode.GLOBAL,
    picker: Boolean = false,
    scrollStateHolder: SaveableStateHolder = rememberSaveableStateHolder(),
    onContentAtTopChanged: (Boolean) -> Unit = {},
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit = {},
    onToggleGroup: (String) -> Unit = {},
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo) -> Unit,
) {
    val optionsContext = LocalContext.current.applicationContext
    var pageOptions by remember(optionsContext) {
        mutableStateOf(ProxyPageOptionsStore.load(optionsContext))
    }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    var toolsMenuPage by rememberSaveable { mutableStateOf(ProxyToolsMenuPage.ROOT) }
    var locateSelectedRequest by rememberSaveable(currentProfileId) { mutableStateOf(0) }
    var locateTargetGroup by rememberSaveable(currentProfileId) { mutableStateOf<String?>(null) }
    val groups = visibleProxyGroups(state.groups, mode)
    val groupNames = groups.map { it.name }
    val initialPage = groupNames.indexOf(currentGroupName).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }
    val coroutineScope = rememberCoroutineScope()
    var searchExpanded by rememberSaveable(currentProfileId) { mutableStateOf(picker) }
    var proxyQuery by rememberSaveable(currentProfileId) { mutableStateOf("") }

    LaunchedEffect(currentProfileId, currentGroupName, groupNames) {
        if (groupNames.isNotEmpty()) {
            val savedIndex = groupNames.indexOf(currentGroupName)
            val page = if (savedIndex >= 0) savedIndex else pagerState.currentPage.coerceIn(groupNames.indices)
            if (pagerState.currentPage != page) pagerState.scrollToPage(page)
        }
    }
    LaunchedEffect(pagerState, groupNames) {
        // 作者 long：只在组切换稳定后写入 Profile，取消中的滑动不应改变下次恢复的分组。
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> groups.getOrNull(page)?.name?.let(onSelectGroup) }
    }
    val selectedGroupIndex = if (groupNames.isEmpty()) 0 else pagerState.currentPage.coerceIn(groupNames.indices)
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val canRefresh = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
        !state.isLoading &&
        state.switchingGroup == null &&
        state.batchTestingGroup == null
    fun updatePageOptions(updated: ProxyPageOptions) {
        pageOptions = updated
        ProxyPageOptionsStore.save(optionsContext, updated)
    }
    BackHandler(enabled = searchExpanded && !picker) {
        proxyQuery = ""
        searchExpanded = false
    }
    Column(
        modifier = modifier.fillMaxSize().background(proxyPageColor()).testTag("proxy_groups_page"),
    ) {
        ProxyPageToolbar(
            query = proxyQuery,
            searchExpanded = searchExpanded,
            picker = picker,
            onQueryChange = { proxyQuery = it },
            onSearchExpandedChange = { expanded ->
                searchExpanded = expanded
                if (!expanded) proxyQuery = ""
            },
            primaryAction = {
                if (pageOptions.displayMode == ProxyDisplayMode.TAB) selectedGroup?.let { group ->
                    IconButton(
                        onClick = { onTestGroupDelay(group) },
                        enabled = canRefresh && group.all.isNotEmpty(),
                        modifier = Modifier.testTag("proxy_group_batch_${group.name}"),
                    ) {
                        if (state.batchTestingGroup == group.name) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Bolt, contentDescription = "批量测速")
                    }
                }
            },
            secondaryActions = {
                if (pageOptions.displayMode == ProxyDisplayMode.TAB) selectedGroup?.let { group ->
                    IconButton(
                        onClick = { locateTargetGroup = group.name; locateSelectedRequest++ },
                        enabled = group.all.isNotEmpty(),
                        modifier = Modifier.testTag("proxy_scroll_to_selected"),
                    ) { Icon(Icons.Outlined.MyLocation, contentDescription = "定位当前节点") }
                }
                Box {
                    IconButton(
                        onClick = {
                            toolsMenuPage = ProxyToolsMenuPage.ROOT
                            toolsExpanded = true
                        },
                        modifier = Modifier.testTag("proxy_tools"),
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "代理组工具")
                    }
                    DropdownMenu(
                        expanded = toolsExpanded,
                        onDismissRequest = { toolsExpanded = false },
                    ) {
                        when (toolsMenuPage) {
                            ProxyToolsMenuPage.ROOT -> {
                                selectedGroup?.let { group ->
                                    val summary = state.delaySummary(group)
                                    if (summary.tested > 0 || summary.testing > 0) DropdownMenuItem(
                                        text = { ProxyDelaySummaryText(summary) }, enabled = false, onClick = {},
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("刷新代理组") },
                                    onClick = { toolsExpanded = false; onRefresh() },
                                    enabled = canRefresh,
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                    modifier = Modifier.testTag("proxy_groups_refresh"),
                                )
                                DropdownMenuItem(
                                    text = { Text("样式：${pageOptions.displayMode.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.DISPLAY_MODE },
                                    modifier = Modifier.testTag("proxy_tools_display_mode"),
                                )
                                DropdownMenuItem(
                                    text = { Text("排序：${pageOptions.sort.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.SORT },
                                    modifier = Modifier.testTag("proxy_tools_sort"),
                                )
                                DropdownMenuItem(
                                    text = { Text("布局：${pageOptions.gridDensity.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.DENSITY },
                                    modifier = Modifier.testTag("proxy_tools_density"),
                                )
                                DropdownMenuItem(
                                    text = { Text("尺寸：${pageOptions.cardSize.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.CARD_SIZE },
                                    modifier = Modifier.testTag("proxy_tools_card_size"),
                                )
                                if (pageOptions.displayMode == ProxyDisplayMode.LIST) {
                                    DropdownMenuItem(
                                        text = { Text("列表图标：${pageOptions.iconStyle.label}") },
                                        onClick = { toolsMenuPage = ProxyToolsMenuPage.ICON_STYLE },
                                        modifier = Modifier.testTag("proxy_tools_icon_style"),
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("隐藏超时节点") },
                                    onClick = { updatePageOptions(pageOptions.copy(hideTimeout = !pageOptions.hideTimeout)) },
                                    trailingIcon = {
                                        if (pageOptions.hideTimeout) Icon(Icons.Outlined.Check, contentDescription = "已启用")
                                    },
                                    modifier = Modifier.testTag("proxy_tools_hide_timeout"),
                                )
                            }
                            ProxyToolsMenuPage.DISPLAY_MODE -> {
                                ProxyMenuBackItem("样式") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                ProxyDisplayMode.entries.forEach { option ->
                                    ProxyMenuOptionItem(option.label, option == pageOptions.displayMode, "proxy_display_mode_${option.name}") {
                                        updatePageOptions(pageOptions.copy(displayMode = option))
                                        toolsExpanded = false
                                    }
                                }
                            }
                            ProxyToolsMenuPage.SORT -> {
                                ProxyMenuBackItem("排序") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                ProxySortOption.entries.forEach { option ->
                                    ProxyMenuOptionItem(option.label, option == pageOptions.sort, "proxy_sort_option_${option.name}") {
                                        updatePageOptions(pageOptions.copy(sort = option))
                                        toolsExpanded = false
                                    }
                                }
                            }
                            ProxyToolsMenuPage.DENSITY -> {
                                ProxyMenuBackItem("布局") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                ProxyGridDensity.entries.forEach { option ->
                                    ProxyMenuOptionItem(option.label, option == pageOptions.gridDensity, "proxy_density_option_${option.name}") {
                                        updatePageOptions(pageOptions.copy(gridDensity = option))
                                        toolsExpanded = false
                                    }
                                }
                            }
                            ProxyToolsMenuPage.CARD_SIZE -> {
                                ProxyMenuBackItem("尺寸") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                ProxyNodeCardSize.entries.forEach { option ->
                                    ProxyMenuOptionItem(option.label, option == pageOptions.cardSize, "proxy_card_size_option_${option.name}") {
                                        updatePageOptions(pageOptions.copy(cardSize = option))
                                        toolsExpanded = false
                                    }
                                }
                            }
                            ProxyToolsMenuPage.ICON_STYLE -> {
                                ProxyMenuBackItem("列表图标") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                ProxyGroupIconStyle.entries.forEach { option ->
                                    ProxyMenuOptionItem(option.label, option == pageOptions.iconStyle, "proxy_icon_style_${option.name}") {
                                        updatePageOptions(pageOptions.copy(iconStyle = option))
                                        toolsExpanded = false
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator()
                else Text("添加有效配置后显示代理组。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                ProxyGroupTabs(groups.map { it.name }, selectedGroupIndex) { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                HorizontalPager(
                    state = pagerState,
                    key = { page -> "$currentProfileId:${groupNames[page]}" },
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("proxy_groups_pager"),
                ) { page ->
                    val group = groups.getOrNull(page) ?: return@HorizontalPager
                    // 作者 long：Pager 会释放离屏组，位置按配置/组名独立保留；测速刷新与再次进入页面不能覆盖用户浏览位置。
                    scrollStateHolder.SaveableStateProvider("tab:$currentProfileId:${group.name}") {
                        ProxyGroupNodesPage(
                            group = group,
                            groups = state.groups,
                            state = state,
                            query = proxyQuery,
                            options = pageOptions,
                            locateRequest = locateSelectedRequest,
                            locateTargetGroup = locateTargetGroup,
                            isCurrentPage = page == pagerState.currentPage,
                            onSelect = onSelect,
                            onTestDelay = onTestDelay,
                            onContentAtTopChanged = onContentAtTopChanged,
                        )
                    }
                }
            } else {
                scrollStateHolder.SaveableStateProvider("list:$currentProfileId") {
                    ProxyGroupsListPage(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        groups = groups,
                        state = state,
                        runtimeState = runtimeState,
                        query = proxyQuery,
                        locateGroupName = selectedGroup?.name,
                        locateRequest = locateSelectedRequest,
                        options = pageOptions,
                        onToggleGroup = onToggleGroup,
                        onSelect = onSelect,
                        onTestDelay = onTestDelay,
                        onTestGroupDelay = onTestGroupDelay,
                        onContentAtTopChanged = onContentAtTopChanged,
                    )
                }
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProxyGroupsListPage(
    modifier: Modifier,
    groups: List<com.lonnnnnng.flclash.core.ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    query: String,
    locateGroupName: String?,
    locateRequest: Int,
    options: ProxyPageOptions,
    onToggleGroup: (String) -> Unit,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo) -> Unit,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerHeights = remember { mutableStateMapOf<String, Int>() }
    val fallbackHeaderHeight = with(LocalDensity.current) { 68.dp.roundToPx() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged().collect { onContentAtTopChanged(it) }
    }
    BoxWithConstraints(modifier) {
        val columns = proxyGridColumnCount(maxWidth.value - 20f, options.gridDensity)
        val members = groups.associate { group ->
            val sorted = sortProxyMembers(group, state.groups, state, filterProxyMembers(group, query), options.sort)
            group.name to if (options.hideTimeout) hideTimedOutProxyMembers(group, state.groups, state, sorted) else sorted
        }
        val scrollLayout = ProxyListScrollLayout(
            items = proxyListScrollItems(groups, members, state.unfoldSet, columns),
            columns = columns,
            cardSize = options.cardSize,
        )
        val previousScrollLayout = remember { mutableStateOf<ProxyListScrollLayout?>(null) }
        val previousLayout = previousScrollLayout.value
        val semanticAnchor = if (previousLayout != null && previousLayout != scrollLayout) {
            proxyListScrollAnchor(
                items = previousLayout.items,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        } else {
            null
        }
        LaunchedEffect(scrollLayout) {
            semanticAnchor?.let { anchor ->
                val target = proxyListScrollItemIndex(scrollLayout.items, anchor)
                if (target != null) listState.scrollToItem(target, anchor.scrollOffset.coerceAtLeast(0))
            }
        }
        SideEffect { previousScrollLayout.value = scrollLayout }
        fun locate(groupName: String) {
            val index = proxyListSelectedItemIndex(groups, members, state.unfoldSet, state.selectedMap, groupName, columns)
            if (index != null) scope.launch {
                // 作者 long：固定组头仍占据视口顶部，定位必须为它留出空间，不能把当前节点滚到组头背后。
                listState.animateScrollToItem(index, -(headerHeights[groupName] ?: fallbackHeaderHeight))
            }
        }
        var handledLocateRequest by rememberSaveable { mutableStateOf(locateRequest) }
        LaunchedEffect(locateRequest) {
            if (locateRequest != handledLocateRequest) {
                handledLocateRequest = locateRequest
                locateGroupName?.let(::locate)
            }
        }
        // 作者 long：List 的每行是独立懒加载项，组内定位使用真实行索引；不能把长组塞进一个 item 后只滚到组头。
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("proxy_groups_list"), state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            groups.forEachIndexed { groupIndex, group ->
                val visible = members.getValue(group.name)
                val expanded = group.name in state.unfoldSet
                val selected = selectedProxyName(group, state.selectedMap)
                stickyHeader(key = "header:${group.name}") {
                    // 作者 long：Flutter 的 List 组标题是独立的 raised card；折叠时只给出类型，
                    // 展开后才显示定位与批量测速，保证标题、当前节点和操作各自有稳定层级。
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = if (groupIndex == 0) 0.dp else 16.dp).testTag("proxy_list_group_${group.name}"),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp,
                            bottomStart = if (expanded) 0.dp else 24.dp, bottomEnd = if (expanded) 0.dp else 24.dp),
                        color = dashboardCardColor(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleGroup(group.name) }
                                .onSizeChanged { headerHeights[group.name] = it.height }
                                .heightIn(min = 62.dp).padding(horizontal = 10.dp, vertical = 10.dp)
                                .semantics { role = Role.Button; stateDescription = if (expanded) "已展开" else "已折叠" }
                                .testTag("proxy_list_group_header_${group.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProxyGroupIcon(group.name, options.iconStyle)
                            Column(Modifier.weight(1f).padding(start = if (options.iconStyle == ProxyGroupIconStyle.HIDDEN) 4.dp else 12.dp)) {
                                Text(group.name, style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val current = selected
                                Text(
                                    if (current.isNullOrBlank()) group.type else current,
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (expanded) {
                                IconButton(onClick = { locate(group.name) },
                                    modifier = Modifier.size(32.dp).testTag("proxy_list_group_locate_${group.name}")) {
                                    Icon(Icons.Outlined.MyLocation, contentDescription = "定位 ${group.name} 的当前节点")
                                }
                                IconButton(
                                    onClick = { onTestGroupDelay(group) },
                                    enabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                        state.switchingGroup == null && state.batchTestingGroup == null && group.all.isNotEmpty(),
                                    modifier = Modifier.size(32.dp).testTag("proxy_list_group_batch_${group.name}"),
                                ) {
                                    if (state.batchTestingGroup == group.name) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Outlined.Bolt, contentDescription = "批量测速")
                                    }
                                }
                            } else {
                                Text(group.type, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onToggleGroup(group.name) },
                                modifier = Modifier.padding(start = 6.dp).size(32.dp).testTag("proxy_list_group_toggle_${group.name}")) {
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (expanded) "收起 ${group.name}" else "展开 ${group.name}")
                            }
                        }
                    }
                }
                if (expanded) {
                    if (visible.isEmpty()) item(key = "empty:${group.name}") {
                        Surface(color = dashboardCardColor(),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)) {
                            Text(if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                                modifier = Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val rows = visible.chunked(columns)
                    rows.forEachIndexed { rowIndex, row ->
                        item(key = "row:${group.name.length}:${group.name}:${row.first()}") {
                            val lastRow = rowIndex == rows.lastIndex
                            Surface(
                                color = dashboardCardColor(),
                                shape = RoundedCornerShape(bottomStart = if (lastRow) 24.dp else 0.dp,
                                    bottomEnd = if (lastRow) 24.dp else 0.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp,
                                    bottom = if (lastRow) 10.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { proxyName ->
                                        ProxyNodeCard(name = proxyName, type = state.proxyType(proxyName, options.cardSize),
                                            cardSize = options.cardSize, insideGroup = true, inUse = proxyName == selected,
                                            preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                                            enabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                                group.isSelectable && state.switchingGroup == null && state.batchTestingGroup == null,
                                            delayEnabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                                state.switchingGroup == null && state.batchTestingGroup == null,
                                            delay = state.delayState(group, proxyName),
                                            selectionTag = "proxy_list_node_${group.name}_$proxyName",
                                            delayTag = "proxy_list_node_delay_${group.name}_$proxyName",
                                            onSelect = { onSelect(group, proxyName) }, onTestDelay = { onTestDelay(group, proxyName) },
                                            modifier = Modifier.weight(1f))
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyDelaySummaryText(
    summary: ProxyDelaySummary,
    modifier: Modifier = Modifier,
) {
    if (summary.tested == 0 && summary.testing == 0) return
    val parts = buildList {
        if (summary.success > 0) add("成功 ${summary.success}")
        if (summary.timedOut > 0) add("超时 ${summary.timedOut}")
        if (summary.failed > 0) add("失败 ${summary.failed}")
        if (summary.testing > 0) add("进行中 ${summary.testing}")
    }
    Text(
        "测速：${parts.joinToString(" · ")}",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProxyGroupIcon(name: String, style: ProxyGroupIconStyle) {
    when (style) {
        ProxyGroupIconStyle.HIDDEN -> Unit
        ProxyGroupIconStyle.PLAIN -> Text(
            name.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))).uppercase() } ?: "?",
            modifier = Modifier.size(39.dp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        ProxyGroupIconStyle.FILLED -> Surface(
            modifier = Modifier.size(39.dp),
            shape = RoundedCornerShape(14.dp),
            color = proxyNodeColor(true),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))).uppercase() } ?: "?", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ProxyMenuBackItem(label: String, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text("返回：$label") },
        onClick = onBack,
        modifier = Modifier.testTag("proxy_tools_back"),
    )
}

@Composable
private fun ProxyMenuOptionItem(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (isSelected) Icon(Icons.Outlined.Check, contentDescription = "已选择")
        },
        modifier = Modifier.testTag(testTag).semantics { selected = isSelected },
    )
}

@Composable
private fun ProxyGroupNodesPage(
    group: com.lonnnnnng.flclash.core.ProxyGroupInfo,
    groups: List<com.lonnnnnng.flclash.core.ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    query: String,
    options: ProxyPageOptions,
    locateRequest: Int,
    isCurrentPage: Boolean,
    locateTargetGroup: String?,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, isCurrentPage) {
        if (isCurrentPage) snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }.distinctUntilChanged().collect { onContentAtTopChanged(it) }
    }
    val searchedProxies = filterProxyMembers(group, query)
    val sortedProxies = sortProxyMembers(group, groups, state, searchedProxies, options.sort)
    val visibleProxies = if (options.hideTimeout) {
        hideTimedOutProxyMembers(group, groups, state, sortedProxies)
    } else {
        sortedProxies
    }
    val inUseName = selectedProxyName(group, state.selectedMap)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("proxy_group_${group.name}"),
    ) {
        // 作者 long：按密度选项调整 Flutter 同源的 250dp 网格列宽，宽松模式允许单列卡片。
        val columnCount = proxyGridColumnCount(maxWidth.value, options.gridDensity)
        val scrollLayout = ProxyGridScrollLayout(group.name, visibleProxies, columnCount, options.cardSize)
        val previousScrollLayout = remember { mutableStateOf<ProxyGridScrollLayout?>(null) }
        val previousLayout = previousScrollLayout.value
        val semanticAnchor = if (previousLayout != null && previousLayout != scrollLayout) {
            proxyGridScrollAnchor(
                groupName = previousLayout.groupName,
                proxyNames = previousLayout.proxyNames,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        } else {
            null
        }
        LaunchedEffect(scrollLayout) {
            semanticAnchor?.let { anchor ->
                val target = proxyGridScrollItemIndex(scrollLayout.proxyNames, anchor, columnCount)
                if (target != null) gridState.scrollToItem(target, anchor.scrollOffset.coerceAtLeast(0))
            }
        }
        SideEffect { previousScrollLayout.value = scrollLayout }
        var handledLocateRequest by rememberSaveable { mutableStateOf(locateRequest) }
        LaunchedEffect(isCurrentPage, locateRequest) {
            if (isCurrentPage && locateRequest != handledLocateRequest) {
                handledLocateRequest = locateRequest
                val selectedIndex = if (locateTargetGroup == group.name) visibleProxies.indexOf(inUseName) else -1
                if (selectedIndex >= 0) gridState.animateScrollToItem(selectedIndex)
            }
        }
        if (visibleProxies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("proxy_group_members_${group.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("proxy_group_members_${group.name}"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(visibleProxies, key = { it }) { proxyName ->
                    val delay = state.delayState(group, proxyName)
                    ProxyNodeCard(
                        name = proxyName,
                        type = state.proxyType(proxyName, options.cardSize),
                        cardSize = options.cardSize,
                        inUse = proxyName == inUseName,
                        preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                        enabled = isCurrentPage && group.isSelectable &&
                            state.switchingGroup == null && state.batchTestingGroup == null,
                        delayEnabled = isCurrentPage && state.switchingGroup == null && state.batchTestingGroup == null,
                        delay = delay,
                        selectionTag = "proxy_node_${group.name}_$proxyName",
                        delayTag = "proxy_node_delay_${group.name}_$proxyName",
                        onSelect = { onSelect(group, proxyName) },
                        onTestDelay = { onTestDelay(group, proxyName) },
                    )
                }
            }
        }
    }
}
