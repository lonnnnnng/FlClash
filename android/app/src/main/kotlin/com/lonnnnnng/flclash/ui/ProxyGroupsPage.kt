package com.lonnnnnng.flclash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class ProxyToolsMenuPage {
    ROOT,
    DISPLAY_MODE,
    SORT,
    DENSITY,
    CARD_SIZE,
    ICON_STYLE,
}

private enum class ProxyGroupsPageSlot { LOADING, ERROR, EMPTY, CONTENT }

private const val PROXY_GROUPS_SLOT_ENTER_MILLIS = 300
private const val PROXY_GROUPS_SLOT_EXIT_MILLIS = 150
private const val PROXY_LIST_GROUP_TOGGLE_LOCK_MILLIS = 480
private val ProxyGroupsEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val ProxyGroupsExitEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

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
            Modifier.fillMaxWidth().heightIn(min = if (picker) 64.dp else 72.dp).padding(horizontal = 16.dp)
                .testTag("proxy_page_toolbar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                Surface(Modifier.weight(1f).height(44.dp), shape = CircleShape, color = dashboardCardColor()) {
                    Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppGlyphs.search, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus)
                                .testTag("proxy_group_search"),
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
                            AppGlyphIcon(AppGlyphs.close, Modifier.size(20.dp).testTag("action:清除搜索"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Spacer(Modifier.width(12.dp))
                    }
                }
                if (!picker) IconButton(onClick = { onSearchExpandedChange(false) },
                    modifier = Modifier.testTag("proxy_group_search_toggle")) {
                    AppGlyphIcon(AppGlyphs.close, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                primaryAction()
            } else {
                Text("代理", Modifier.weight(1f), style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface)
                Surface(shape = CircleShape, color = dashboardCardColor()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSearchExpandedChange(true) }, modifier = Modifier.size(44.dp).testTag("proxy_group_search_toggle")) {
                            AppGlyphIcon(AppGlyphs.search, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        primaryAction()
                    }
                }
            }
            if (!searchExpanded && !picker) { Spacer(Modifier.width(8.dp)); secondaryActions() }
        }
    }
}

@Composable
private fun ProxyGroupTabs(names: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val tabsState = rememberLazyListState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tabStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val widths = names.map { name ->
        maxOf(with(density) { 64.dp.roundToPx() }, textMeasurer.measure(AnnotatedString(name), tabStyle).size.width + with(density) { 32.dp.roundToPx() })
    }
    val viewportWidth by remember { derivedStateOf { tabsState.layoutInfo.viewportSize.width } }
    var initiallyRevealed by remember(names) { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    val hasOverflow by remember { derivedStateOf { tabsState.canScrollForward || tabsState.canScrollBackward } }
    LaunchedEffect(selectedIndex, names, viewportWidth) {
        if (selectedIndex in names.indices && viewportWidth > 0) {
            // 作者 long：与上游标签带一样把选中项尽量放在视口中央，越界由列表夹紧；初次定位不播放从首组跨越而来的动画。
            val offset = with(density) { 16.dp.roundToPx() } - (viewportWidth - widths[selectedIndex]) / 2
            if (initiallyRevealed) tabsState.animateScrollToItem(selectedIndex, offset)
            else { tabsState.scrollToItem(selectedIndex, offset); initiallyRevealed = true }
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = tabsState,
            modifier = Modifier.weight(1f).testTag("proxy_group_tabs")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val info = tabsState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    val remaining = if (last?.index == names.lastIndex) (last.offset + last.size - info.viewportEndOffset).toFloat() else 24.dp.toPx()
                    val fade = if (tabsState.canScrollForward) (remaining / 24.dp.toPx()).coerceIn(0f, 1f) else 0f
                    if (fade > 0f) {
                        // 作者 long：只淡化末端尚未露出的标签，接近列表结尾时恢复不透明，提示横向内容而不遮住最后一组。
                        drawRect(Brush.horizontalGradient(
                            colors = if (rtl) listOf(Color.Black.copy(alpha = 1f - fade), Color.Black) else listOf(Color.Black, Color.Black.copy(alpha = 1f - fade)),
                            startX = if (rtl) 0f else (size.width - 24.dp.toPx()).coerceAtLeast(0f),
                            endX = if (rtl) 24.dp.toPx() else size.width,
                        ), blendMode = BlendMode.DstIn)
                    }
                },
            contentPadding = PaddingValues(start = 16.dp, end = if (hasOverflow) 0.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(names, key = { _, name -> name }) { index, name ->
                val active = index == selectedIndex
                val tabColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else dashboardCardColor(), tween(200), label = "groupTabBackground")
                val tabContent by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(200), label = "groupTabContent")
                Surface(
                    modifier = Modifier.height(36.dp).widthIn(min = 64.dp)
                        .appFocusRing(CircleShape)
                        .selectable(active, onClick = { onSelect(index) })
                        .testTag("proxy_group_tab_" + name),
                    shape = CircleShape,
                    color = tabColor,
                    contentColor = tabContent,
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = tabStyle)
                    }
                }
            }
        }
        if (hasOverflow) Box(Modifier.padding(start = 8.dp, end = 16.dp)) {
            val source = rememberAppMenuSource()
            IconButton(onClick = { showGroups = true },
                modifier = Modifier.appMenuSource(source).background(dashboardCardColor(), CircleShape).testTag("proxy_group_more")) {
                AppGlyphIcon(AppGlyphs.chevronDown)
            }
            AppDropdownMenu(expanded = showGroups, onDismissRequest = { showGroups = false }, source = source) {
                names.forEachIndexed { index, name ->
                    AppDropdownMenuItem(text = { Text(name) },
                        trailingIcon = { if (index == selectedIndex) AppGlyphIcon(AppGlyphs.check) },
                        onClick = { showGroups = false; onSelect(index) },
                        modifier = Modifier.testTag("proxy_group_menu_" + name))
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
    var toolsExpanded by remember(currentProfileId, runtimeState, state.isLoading) { mutableStateOf(false) }
    var toolsMenuPage by remember(currentProfileId, runtimeState, state.isLoading) { mutableStateOf(ProxyToolsMenuPage.ROOT) }
    var locateSelectedRequest by rememberSaveable(currentProfileId) { mutableStateOf(0) }
    var locateTargetGroup by rememberSaveable(currentProfileId) { mutableStateOf<String?>(null) }
    var searchExpanded by rememberSaveable(currentProfileId) { mutableStateOf(picker) }
    var proxyQuery by rememberSaveable(currentProfileId) { mutableStateOf("") }
    val allGroups = visibleProxyGroups(state.groups, mode)
    // 作者 long：Flutter先移除搜索后无节点的组，再决定整页空态；只过滤展示组，不改原始成员或已选节点。
    val groups = if (proxyQuery.isBlank()) allGroups else allGroups.filter { filterProxyMembers(it, proxyQuery).isNotEmpty() }
    val groupNames = groups.map { it.name }
    val initialPage = groupNames.indexOf(currentGroupName).coerceAtLeast(0)
    var lastContentState by remember(currentProfileId) { mutableStateOf(state) }
    var lastContentGroups by remember(currentProfileId) { mutableStateOf(groups) }
    var lastContentQuery by remember(currentProfileId) { mutableStateOf(proxyQuery) }
    SideEffect {
        // 作者 long：搜索进入空态时退场层保留上一份内容和关键词，避免节点先消失、组头单独闪过后才淡出。
        if (groups.isNotEmpty()) { lastContentState = state; lastContentGroups = groups; lastContentQuery = proxyQuery }
    }
    val retainedGroups = groups.ifEmpty { lastContentGroups }
    val pagerState = rememberPagerState(initialPage = initialPage) { retainedGroups.size }
    val searchEmpty = allGroups.isNotEmpty() && proxyQuery.isNotBlank() && groups.isEmpty()
    val pageSlot = when {
        groups.isNotEmpty() -> ProxyGroupsPageSlot.CONTENT
        searchEmpty -> ProxyGroupsPageSlot.EMPTY
        state.isLoading -> ProxyGroupsPageSlot.LOADING
        state.error != null -> ProxyGroupsPageSlot.ERROR
        else -> ProxyGroupsPageSlot.EMPTY
    }
    val coroutineScope = rememberCoroutineScope()
    var requestedGroupIndex by remember(currentProfileId, groupNames) { mutableStateOf<Int?>(null) }
    var groupNavigation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var groupNavigationGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentProfileId, currentGroupName, groupNames) {
        if (groupNames.isNotEmpty()) {
            val savedIndex = groupNames.indexOf(currentGroupName)
            val page = if (savedIndex >= 0) savedIndex else pagerState.currentPage.coerceIn(groupNames.indices)
            if (pagerState.currentPage != page) pagerState.scrollToPage(page)
        }
    }
    LaunchedEffect(pagerState, groupNames, proxyQuery) {
        // 作者 long：只在组切换稳定后写入 Profile，取消中的滑动不应改变下次恢复的分组。
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                // 作者 long：搜索过滤导致的页码变化只是临时展示，清除后仍回到用户原组。
                if (proxyQuery.isBlank()) groups.getOrNull(page)?.name?.let(onSelectGroup)
            }
    }
    val selectedGroupIndex = if (groupNames.isEmpty()) 0 else pagerState.currentPage.coerceIn(groupNames.indices)
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val canRefresh = state.canRefresh(runtimeState)
    fun updatePageOptions(updated: ProxyPageOptions) {
        pageOptions = updated
        ProxyPageOptionsStore.save(optionsContext, updated)
    }
    BackHandler(enabled = searchExpanded && !picker) {
        proxyQuery = ""
        searchExpanded = false
    }
    Column(
        // 作者 long：外层已消费 Dock 的空间，这里只避让剩余 IME，搜索空态和节点操作才能留在键盘上方。
        modifier = modifier.fillMaxSize().background(proxyPageColor()).imePadding().testTag("proxy_groups_page"),
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
                        modifier = Modifier.size(44.dp).testTag("proxy_group_batch_${group.name}"),
                    ) {
                        if (state.batchTestingGroup == group.name) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else AppGlyphIcon(AppGlyphs.bolt, modifier = Modifier.testTag("action:批量测速"))
                    }
                }
            },
            secondaryActions = {
                Box {
                    val source = rememberAppMenuSource()
                    IconButton(
                        onClick = {
                            toolsMenuPage = ProxyToolsMenuPage.ROOT
                            toolsExpanded = true
                        },
                        modifier = Modifier.appMenuSource(source).size(44.dp).background(dashboardCardColor(), CircleShape).testTag("proxy_tools"),
                    ) {
                        AppGlyphIcon(AppGlyphs.more)
                    }
                    AppDropdownMenu(
                        expanded = toolsExpanded,
                        source = source,
                        onDismissRequest = { toolsExpanded = false },
                        pageKey = toolsMenuPage,
                        onNavigateBack = if (toolsMenuPage != ProxyToolsMenuPage.ROOT) ({ toolsMenuPage = ProxyToolsMenuPage.ROOT }) else null,
                    ) { level ->
                        when (level as ProxyToolsMenuPage) {
                            ProxyToolsMenuPage.ROOT -> {
                                // 作者 long：搜索、测速和更多已经占满上游手机工具栏，定位动作折入更多，避免标题被第四个按钮挤压。
                                if (pageOptions.displayMode == ProxyDisplayMode.TAB) selectedGroup?.let { group ->
                                    AppDropdownMenuItem(text = { Text("定位当前节点") },
                                        onClick = { toolsExpanded = false; locateTargetGroup = group.name; locateSelectedRequest++ },
                                        enabled = group.all.isNotEmpty(), leadingIcon = { AppGlyphIcon(AppGlyphs.locate) },
                                        modifier = Modifier.testTag("proxy_scroll_to_selected"))
                                }
                                selectedGroup?.let { group ->
                                    val summary = state.delaySummary(group)
                                    if (summary.tested > 0 || summary.testing > 0) AppDropdownMenuItem(
                                        text = { ProxyDelaySummaryText(summary) }, enabled = false, onClick = {},
                                    )
                                }
                                AppDropdownMenuItem(
                                    text = { Text("刷新代理组") },
                                    onClick = { toolsExpanded = false; onRefresh() },
                                    enabled = canRefresh,
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.refresh) },
                                    modifier = Modifier.testTag("proxy_groups_refresh"),
                                )
                                AppDropdownMenuItem(
                                    text = { Text("样式：${pageOptions.displayMode.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.DISPLAY_MODE }, dismissOnClick = false, opensSubmenu = true,
                                    modifier = Modifier.testTag("proxy_tools_display_mode"),
                                    trailingIcon = { AppMenuSubmenuArrow() },
                                )
                                AppDropdownMenuItem(
                                    text = { Text("排序：${pageOptions.sort.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.SORT }, dismissOnClick = false, opensSubmenu = true,
                                    modifier = Modifier.testTag("proxy_tools_sort"),
                                    trailingIcon = { AppMenuSubmenuArrow() },
                                )
                                AppDropdownMenuItem(
                                    text = { Text("布局：${pageOptions.gridDensity.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.DENSITY }, dismissOnClick = false, opensSubmenu = true,
                                    modifier = Modifier.testTag("proxy_tools_density"),
                                    trailingIcon = { AppMenuSubmenuArrow() },
                                )
                                AppDropdownMenuItem(
                                    text = { Text("尺寸：${pageOptions.cardSize.label}") },
                                    onClick = { toolsMenuPage = ProxyToolsMenuPage.CARD_SIZE }, dismissOnClick = false, opensSubmenu = true,
                                    modifier = Modifier.testTag("proxy_tools_card_size"),
                                    trailingIcon = { AppMenuSubmenuArrow() },
                                )
                                if (pageOptions.displayMode == ProxyDisplayMode.LIST) {
                                    AppDropdownMenuItem(
                                        text = { Text("列表图标：${pageOptions.iconStyle.label}") },
                                        onClick = { toolsMenuPage = ProxyToolsMenuPage.ICON_STYLE }, dismissOnClick = false, opensSubmenu = true,
                                        modifier = Modifier.testTag("proxy_tools_icon_style"),
                                        trailingIcon = { AppMenuSubmenuArrow() },
                                    )
                                }
                                AppDropdownMenuItem(
                                    text = { Text("隐藏超时节点") },
                                    onClick = { updatePageOptions(pageOptions.copy(hideTimeout = !pageOptions.hideTimeout)) }, dismissOnClick = false,
                                    trailingIcon = {
                                        if (pageOptions.hideTimeout) AppGlyphIcon(AppGlyphs.check)
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
        if (!picker) Spacer(Modifier.height(12.dp))
        // 作者 long：刷新保留旧节点时仍明确展示忙态，卡片选择、菜单和测速一起停用，不能看起来可点却静默拒绝。
        if (state.isLoading && groups.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("proxy_groups_refreshing"))
        state.error?.takeIf { groups.isNotEmpty() }?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("proxy_groups_error"), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onRefresh, enabled = canRefresh, modifier = Modifier.testTag("proxy_groups_retry")) { Text("重试") }
            }
        }
        AnimatedContent(
            targetState = pageSlot,
            modifier = Modifier.fillMaxSize().testTag("proxy_groups_content_transition"),
            transitionSpec = {
                (fadeIn(tween(PROXY_GROUPS_SLOT_ENTER_MILLIS, easing = ProxyGroupsEnterEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(PROXY_GROUPS_SLOT_ENTER_MILLIS, easing = ProxyGroupsEnterEasing))) togetherWith
                    fadeOut(tween(PROXY_GROUPS_SLOT_EXIT_MILLIS, easing = ProxyGroupsExitEasing))
            },
            label = "proxyGroupsStatus",
        ) { slot ->
            val active = slot == pageSlot
            val interaction = if (active) Modifier else Modifier.pointerInput(slot) {
                // 作者 long：状态切换时旧页面继续参与淡出，但手指事件不能穿过旧内容触发另一项业务操作。
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            Column(Modifier.fillMaxSize().then(interaction)) {
                when (slot) {
                    ProxyGroupsPageSlot.LOADING,
                    ProxyGroupsPageSlot.ERROR,
                    ProxyGroupsPageSlot.EMPTY -> {
                        val loading = slot == ProxyGroupsPageSlot.LOADING
                        val failed = slot == ProxyGroupsPageSlot.ERROR
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.BiasAlignment(0f, -.2f)) {
                            ProxyPageEmptyState(
                                title = when (slot) {
                                    ProxyGroupsPageSlot.LOADING -> "正在加载代理组"
                                    ProxyGroupsPageSlot.ERROR -> "代理组加载失败"
                                    else -> if (searchEmpty) "没有匹配的节点" else "暂无代理"
                                },
                                description = when (slot) {
                                    ProxyGroupsPageSlot.LOADING -> null
                                    ProxyGroupsPageSlot.ERROR -> state.error
                                    else -> null
                                },
                                tag = if (failed) "proxy_groups_error" else if (searchEmpty) "proxy_groups_search_empty" else "proxy_groups_empty",
                                searching = searchEmpty,
                                loading = loading,
                                actionLabel = if (loading) null else if (failed) "重试" else if (searchEmpty) "清除搜索" else null,
                                actionTag = if (failed) "proxy_groups_retry" else if (searchEmpty) "proxy_groups_search_empty_action" else "proxy_groups_empty_action",
                                actionEnabled = active && (searchEmpty || canRefresh),
                                onAction = { if (active) { if (searchEmpty) proxyQuery = "" else onRefresh() } },
                            )
                        }
                    }
                    ProxyGroupsPageSlot.CONTENT -> {
                        // 作者 long：异常或切换配置时旧内容留在退场层；当前页面仍只让目标层接收输入。
                        val contentState = if (groups.isEmpty()) lastContentState else state
                        val contentGroups = if (groups.isEmpty()) lastContentGroups else groups
                        val contentQuery = if (groups.isEmpty()) lastContentQuery else proxyQuery
                        val contentGroupNames = contentGroups.map { it.name }
                        val contentSelectedIndex = if (contentGroups.isEmpty()) 0 else pagerState.currentPage.coerceIn(contentGroups.indices)
                        val contentSelectedGroup = contentGroups.getOrNull(contentSelectedIndex)
                        if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                            ProxyGroupTabs(contentGroupNames, requestedGroupIndex ?: contentSelectedIndex) { index ->
                                if (active) {
                                    // 作者 long：点击立即高亮目标组，手指滑动仍跟随当前页；取消旧点击后只允许最后一个导航清除目标，避免快速连点闪回。
                                    val generation = ++groupNavigationGeneration
                                    groupNavigation?.cancel()
                                    requestedGroupIndex = index
                                    groupNavigation = coroutineScope.launch {
                                        try { pagerState.animateScrollToPage(index, animationSpec = tween(MAIN_PAGE_SLIDE_MILLIS, easing = MainPageSlideEasing)) }
                                        finally { if (groupNavigationGeneration == generation) requestedGroupIndex = null }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                            HorizontalPager(
                                state = pagerState,
                                key = { page -> "$currentProfileId:${contentGroupNames[page]}" },
                                modifier = Modifier.fillMaxWidth().weight(1f).testTag("proxy_groups_pager"),
                            ) { page ->
                                val group = contentGroups.getOrNull(page) ?: return@HorizontalPager
                                // 作者 long：Pager 会释放离屏组，位置按配置/组名独立保留；测速刷新与再次进入页面不能覆盖用户浏览位置。
                                scrollStateHolder.SaveableStateProvider("tab:$currentProfileId:${group.name}") {
                                    ProxyGroupNodesPage(
                                        group = group,
                                        groups = contentState.groups,
                                        state = contentState,
                                        query = contentQuery,
                                        options = pageOptions,
                                        locateRequest = locateSelectedRequest,
                                        locateTargetGroup = locateTargetGroup,
                                        isCurrentPage = active && page == pagerState.currentPage && runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING,
                                        onSelect = { selectedGroup, name -> if (active) onSelect(selectedGroup, name) },
                                        onTestDelay = { testedGroup, name -> if (active) onTestDelay(testedGroup, name) },
                                        onClearSearch = { if (active) proxyQuery = "" },
                                        onContentAtTopChanged = { if (active) onContentAtTopChanged(it) },
                                    )
                                }
                            }
                        } else {
                            scrollStateHolder.SaveableStateProvider("list:$currentProfileId") {
                                ProxyGroupsListPage(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    groups = contentGroups,
                                    state = contentState,
                                    runtimeState = runtimeState,
                                    query = contentQuery,
                                    locateGroupName = contentSelectedGroup?.name,
                                    locateRequest = locateSelectedRequest,
                                    options = pageOptions,
                                    onToggleGroup = { groupName -> if (active) onToggleGroup(groupName) },
                                    onSelect = { selectedGroup, name -> if (active) onSelect(selectedGroup, name) },
                                    onTestDelay = { testedGroup, name -> if (active) onTestDelay(testedGroup, name) },
                                    onTestGroupDelay = { testedGroup -> if (active) onTestGroupDelay(testedGroup) },
                                    onClearSearch = { if (active) proxyQuery = "" },
                                    onContentAtTopChanged = { if (active) onContentAtTopChanged(it) },
                                )
                            }
                        }
                    }
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
    onClearSearch: () -> Unit,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val restoredPosition = remember(listState) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(listState) {
        if (restoredPosition.first > 0 || restoredPosition.second > 0) {
            // 作者 long：重建首帧布局可能先裁剪末尾位置；等实际列表完成布局后再应用保存的锚点，连同吸顶组头一起恢复。
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            listState.scrollToItem(restoredPosition.first, restoredPosition.second)
        }
    }
    val scope = rememberCoroutineScope()
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    var groupScrollActive by remember { mutableStateOf(false) }
    val groupToggleMotion = remember { Animatable(1f) }
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
        suspend fun scrollGroupHeaderIntoView(groupName: String) {
            val headerIndex = scrollLayout.items.indexOfFirst { item ->
                item.isHeader && item.groupName == groupName
            }
            if (headerIndex < 0) return
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == headerIndex }
            val fullyVisible = header != null && header.offset >= viewportStart &&
                header.offset + header.size <= viewportEnd
            if (!fullyVisible) {
                // 作者 long：Flutter 在切换折叠状态前先让组头进入可视区；避免远处点击后内容展开在屏幕外，用户看不到状态变化。
                listState.scrollToItem(headerIndex)
            }
        }
        fun toggleGroup(groupName: String) {
            if (groupScrollActive) return
            groupScrollActive = true
            scope.launch {
                try {
                    scrollGroupHeaderIntoView(groupName)
                    onToggleGroup(groupName)
                    // 作者 long：组头圆角与节点行错峰动效共用 Compose 动画时钟，过渡完成前屏蔽重复组头操作。
                    groupToggleMotion.snapTo(0f)
                    groupToggleMotion.animateTo(1f, animationSpec = tween(PROXY_LIST_GROUP_TOGGLE_LOCK_MILLIS))
                } finally {
                    groupScrollActive = false
                }
            }
        }
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
                    val headerBottomRadius by animateDpAsState(
                        targetValue = if (expanded) 0.dp else 24.dp,
                        animationSpec = tween(240, easing = if (expanded) ProxyGroupsEnterEasing else ProxyGroupsExitEasing),
                        label = "proxyListHeaderBottomRadius",
                    )
                    val headerShadow by animateDpAsState(
                        targetValue = if (listScrolled) 8.dp else 0.dp,
                        animationSpec = tween(180),
                        label = "proxyListHeaderShadow",
                    )
                    val headerTonal by animateDpAsState(
                        targetValue = if (listScrolled) 2.dp else 0.dp,
                        animationSpec = tween(180),
                        label = "proxyListHeaderTonal",
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = if (groupIndex == 0) 0.dp else 16.dp).testTag("proxy_list_group_${group.name}"),
                        shape = AppSuperellipseShape(topStart = 24.dp, topEnd = 24.dp,
                            bottomStart = headerBottomRadius, bottomEnd = headerBottomRadius),
                        color = dashboardCardColor(),
                        tonalElevation = headerTonal,
                        shadowElevation = headerShadow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isLoading && !groupScrollActive) { toggleGroup(group.name) }
                                .onSizeChanged { headerHeights[group.name] = it.height }
                                .heightIn(min = 62.dp).padding(horizontal = 10.dp, vertical = 10.dp)
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
                                    enabled = !groupScrollActive,
                                    modifier = Modifier.size(32.dp).testTag("proxy_list_group_locate_${group.name}")) {
                                    AppGlyphIcon(AppGlyphs.locate)
                                }
                                IconButton(
                                    onClick = { onTestGroupDelay(group) },
                                    enabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                        !state.isLoading && !groupScrollActive && state.switchingGroup == null && state.batchTestingGroup == null && group.all.isNotEmpty(),
                                    modifier = Modifier.size(32.dp).testTag("proxy_list_group_batch_${group.name}"),
                                ) {
                                    if (state.batchTestingGroup == group.name) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        AppGlyphIcon(AppGlyphs.bolt, modifier = Modifier.testTag("action:批量测速"))
                                    }
                                }
                            } else {
                                Text(group.type, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { toggleGroup(group.name) }, enabled = !state.isLoading && !groupScrollActive,
                                modifier = Modifier.padding(start = 6.dp).size(32.dp).testTag("proxy_list_group_toggle_${group.name}")) {
                                AppGlyphIcon(if (expanded) AppGlyphs.chevronUp else AppGlyphs.chevronDown)
                            }
                        }
                    }
                }
                if (expanded) {
                    if (visible.isEmpty()) item(key = "empty:${group.name}") {
                        Surface(color = dashboardCardColor(),
                            shape = AppSuperellipseShape(bottomStart = 24.dp, bottomEnd = 24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp).testTag("proxy_group_empty_${group.name}")) {
                                Text(if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                                    modifier = Modifier,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (query.isNotBlank()) TextButton(onClearSearch, Modifier.testTag("proxy_group_empty_${group.name}_action")) { Text("清除搜索") }
                            }
                        }
                    }
                    val rows = visible.chunked(columns)
                    rows.forEachIndexed { rowIndex, row ->
                        item(key = "row:${group.name.length}:${group.name}:${row.first()}") {
                            val lastRow = rowIndex == rows.lastIndex
                            val rowReveal = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                // 作者 long：Flutter 按可见行顺序展开，给后续行留出轻微错峰，避免整组同时闪现。
                                rowReveal.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 240,
                                        delayMillis = rowIndex * 24,
                                        easing = ProxyGroupsEnterEasing,
                                    ),
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(220, delayMillis = rowIndex * 24, easing = ProxyGroupsEnterEasing),
                                        fadeOutSpec = tween(160, easing = ProxyGroupsExitEasing),
                                        placementSpec = tween(240, easing = ProxyGroupsEnterEasing),
                                    )
                                    .graphicsLayer {
                                        // 作者 long：行内容从顶部收敛进入，保持标题底部圆角过渡期间的连续视觉。
                                        alpha = rowReveal.value
                                        scaleY = 0.94f + rowReveal.value * 0.06f
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                    }
                                    .testTag("proxy_list_group_row_${group.name}_$rowIndex"),
                                color = dashboardCardColor(),
                                shape = AppSuperellipseShape(bottomStart = if (lastRow) 24.dp else 0.dp,
                                    bottomEnd = if (lastRow) 24.dp else 0.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp,
                                    bottom = if (lastRow) 10.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { proxyName ->
                                        ProxyNodeCard(name = proxyName, type = state.proxyType(proxyName, options.cardSize),
                                            groupType = group.type, addable = state.canAddToLocal(proxyName), profileId = state.profileId, busy = state.isLoading,
                                            menuEnabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                                !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
                                            cardSize = options.cardSize, insideGroup = true, inUse = proxyName == selected,
                                            preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                                            enabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                                group.isSelectable && !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
                                            delayEnabled = runtimeState != CoreRuntimeStatus.STARTING && runtimeState != CoreRuntimeStatus.STOPPING &&
                                                !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
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
            remember(name) { groupInitial(name).uppercase() },
            modifier = Modifier.size(39.dp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        ProxyGroupIconStyle.FILLED -> Surface(
            modifier = Modifier.size(39.dp),
            shape = AppSuperellipseShape(14.dp),
            color = proxyNodeColor(true),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(remember(name) { groupInitial(name).uppercase() }, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ProxyMenuBackItem(label: String, onBack: () -> Unit) {
    AppDropdownMenuItem(
        text = { Text(label) },
        onClick = onBack, dismissOnClick = false, isBack = true,
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
    AppDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (isSelected) AppGlyphIcon(AppGlyphs.check)
        },
        modifier = Modifier.testTag(testTag),
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
    onClearSearch: () -> Unit,
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
                ProxyPageEmptyState(
                    title = if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                    description = if (query.isBlank()) "可以切换其他代理组或检查配置资源" else "尝试其他关键词，或清除搜索查看该组节点",
                    tag = "proxy_group_empty_${group.name}", searching = query.isNotBlank(),
                    actionLabel = if (query.isBlank()) null else "清除搜索", onAction = onClearSearch,
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
                        modifier = Modifier.animateItem(),
                        name = proxyName,
                        type = state.proxyType(proxyName, options.cardSize),
                        groupType = group.type,
                        addable = state.canAddToLocal(proxyName),
                        profileId = state.profileId,
                        busy = state.isLoading,
                        menuEnabled = isCurrentPage && !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
                        cardSize = options.cardSize,
                        inUse = proxyName == inUseName,
                        preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                        enabled = isCurrentPage && group.isSelectable &&
                            !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
                        delayEnabled = isCurrentPage && !state.isLoading && state.switchingGroup == null && state.batchTestingGroup == null,
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
