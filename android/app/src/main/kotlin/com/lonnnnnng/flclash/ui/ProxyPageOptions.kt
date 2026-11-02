package com.lonnnnnng.flclash.ui

import android.content.Context
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import kotlin.math.ceil

internal enum class ProxySortOption(val label: String) {
    DEFAULT("默认"),
    DELAY("延迟"),
    NAME("名称"),
}

internal enum class ProxyGridDensity(val label: String) {
    COMPACT("紧凑"),
    STANDARD("标准"),
    LOOSE("宽松"),
}

internal enum class ProxyNodeCardSize(val label: String) {
    COMPACT("紧凑"),
    STANDARD("标准"),
    LOOSE("宽松"),
}

internal enum class ProxyDisplayMode(val label: String) {
    TAB("标签"),
    LIST("列表"),
}

internal enum class ProxyGroupIconStyle(val label: String) {
    FILLED("填充"),
    PLAIN("简洁"),
    HIDDEN("隐藏"),
}

internal data class ProxyPageOptions(
    val displayMode: ProxyDisplayMode = ProxyDisplayMode.TAB,
    val sort: ProxySortOption = ProxySortOption.DEFAULT,
    val gridDensity: ProxyGridDensity = ProxyGridDensity.STANDARD,
    val cardSize: ProxyNodeCardSize = ProxyNodeCardSize.STANDARD,
    val iconStyle: ProxyGroupIconStyle = ProxyGroupIconStyle.FILLED,
    val hideTimeout: Boolean = false,
)

/** 作者 long：节点展示选项属于应用级界面偏好，不应跟随订阅 Profile 切换或写入用户配置。 */
internal object ProxyPageOptionsStore {
    private const val PREFERENCES_NAME = "proxy_page_options"
    private const val KEY_SORT = "sort"
    private const val KEY_GRID_DENSITY = "grid_density"
    private const val KEY_CARD_SIZE = "card_size"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_ICON_STYLE = "icon_style"
    private const val KEY_HIDE_TIMEOUT = "hide_timeout"

    fun load(context: Context): ProxyPageOptions {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ProxyPageOptions(
            displayMode = preferences.enumValue(KEY_DISPLAY_MODE, ProxyDisplayMode.TAB),
            sort = preferences.enumValue(KEY_SORT, ProxySortOption.DEFAULT),
            gridDensity = preferences.enumValue(KEY_GRID_DENSITY, ProxyGridDensity.STANDARD),
            cardSize = preferences.enumValue(KEY_CARD_SIZE, ProxyNodeCardSize.STANDARD),
            iconStyle = preferences.enumValue(KEY_ICON_STYLE, ProxyGroupIconStyle.FILLED),
            hideTimeout = preferences.getBoolean(KEY_HIDE_TIMEOUT, false),
        )
    }

    fun save(context: Context, options: ProxyPageOptions) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_MODE, options.displayMode.name)
            .putString(KEY_SORT, options.sort.name)
            .putString(KEY_GRID_DENSITY, options.gridDensity.name)
            .putString(KEY_CARD_SIZE, options.cardSize.name)
            .putString(KEY_ICON_STYLE, options.iconStyle.name)
            .putBoolean(KEY_HIDE_TIMEOUT, options.hideTimeout)
            .apply()
    }

    internal fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        fallback: T,
    ): T = getString(key, null)?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } } ?: fallback
}

internal fun proxyGridColumnCount(maxWidthDp: Float, density: ProxyGridDensity): Int {
    val base = maxOf(2, ceil((maxWidthDp - 32f).coerceAtLeast(0f) / 250f).toInt())
    return when (density) {
        ProxyGridDensity.COMPACT -> base + 1
        ProxyGridDensity.STANDARD -> base
        ProxyGridDensity.LOOSE -> maxOf(1, base - 1)
    }
}

internal fun sortProxyMembers(
    group: ProxyGroupInfo,
    groups: List<ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    members: List<String>,
    sort: ProxySortOption,
): List<String> = when (sort) {
    ProxySortOption.DEFAULT -> members
    ProxySortOption.NAME -> members.sorted()
    ProxySortOption.DELAY -> members.withIndex()
        .sortedWith(
            compareBy<IndexedValue<String>> { delaySortPriority(state.delayState(group, it.value)?.value) }
                .thenBy { state.delayState(group, it.value)?.value ?: 0 }
                .thenBy { if (groups.any { candidate -> candidate.name == it.value }) 0 else 1 }
                .thenBy { it.index },
        )
        .map { it.value }
}

/** 作者 long：隐藏超时节点时仍保留可继续切换的当前成员与嵌套代理组入口。 */
internal fun hideTimedOutProxyMembers(
    group: ProxyGroupInfo,
    groups: List<ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    members: List<String>,
): List<String> {
    val selectedName = selectedProxyName(group, state.selectedMap)
    val visible = members.filter { name ->
        name == selectedName || groups.any { it.name == name } ||
            state.delayState(group, name)?.let { it.isTesting || it.value == null || it.value > 0 } != false
    }
    return visible.ifEmpty { members }
}

private fun delaySortPriority(delay: Int?): Int = when {
    delay == null || delay == 0 -> 1
    delay > 0 -> 0
    delay == CoreBridge.DELAY_TIMED_OUT -> 2
    else -> 3
}

internal fun proxyListSelectedItemIndex(
    groups: List<ProxyGroupInfo>,
    members: Map<String, List<String>>,
    expanded: Set<String>,
    selectedMap: Map<String, String>,
    groupName: String,
    columns: Int,
): Int? {
    require(columns > 0)
    var header = 0
    for (group in groups) {
        val visible = members[group.name].orEmpty()
        if (group.name == groupName) {
            if (group.name !in expanded) return header
            // 作者 long：定位本组直接选中的成员，嵌套组定位其卡片；排序/搜索之后按当前展示顺序计算行号。
            val selected = selectedProxyName(group, selectedMap)
            val index = visible.indexOf(selected)
            return if (index < 0) null else header + 1 + index / columns
        }
        header += 1 + if (group.name in expanded) maxOf(1, (visible.size + columns - 1) / columns) else 0
    }
    return null
}

/** 作者 long：筛选、排序或列数变化后，滚动位置要绑定到节点身份而不是旧的懒列表索引。 */
internal data class ProxyScrollAnchor(
    val groupName: String,
    val proxyName: String?,
    val scrollOffset: Int,
)

internal data class ProxyListScrollItem(
    val groupName: String,
    val proxyNames: List<String>,
    val isHeader: Boolean = false,
)

internal data class ProxyListScrollLayout(
    val items: List<ProxyListScrollItem>,
    val columns: Int,
    val cardSize: ProxyNodeCardSize,
)

internal data class ProxyGridScrollLayout(
    val groupName: String,
    val proxyNames: List<String>,
    val columns: Int,
    val cardSize: ProxyNodeCardSize,
)

internal fun proxyListScrollItems(
    groups: List<ProxyGroupInfo>,
    members: Map<String, List<String>>,
    expanded: Set<String>,
    columns: Int,
): List<ProxyListScrollItem> {
    require(columns > 0)
    return buildList {
        groups.forEach { group ->
            add(ProxyListScrollItem(group.name, emptyList(), isHeader = true))
            if (group.name !in expanded) return@forEach
            val visible = members[group.name].orEmpty()
            if (visible.isEmpty()) add(ProxyListScrollItem(group.name, emptyList()))
            else visible.chunked(columns).forEach { row ->
                add(ProxyListScrollItem(group.name, row))
            }
        }
    }
}

internal fun proxyListScrollAnchor(
    items: List<ProxyListScrollItem>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): ProxyScrollAnchor? {
    val item = items.getOrNull(firstVisibleItemIndex) ?: return null
    return ProxyScrollAnchor(item.groupName, item.proxyNames.firstOrNull(), firstVisibleItemScrollOffset)
}

internal fun proxyListScrollItemIndex(
    items: List<ProxyListScrollItem>,
    anchor: ProxyScrollAnchor,
): Int? {
    val exact = anchor.proxyName?.let { proxyName ->
        items.indexOfFirst { it.groupName == anchor.groupName && proxyName in it.proxyNames }
    } ?: -1
    if (exact >= 0) return exact
    // 作者 long：搜索把锚定节点过滤掉时，回到同组组头，避免把用户送到无关组的旧像素位置。
    val groupHeader = items.indexOfFirst { it.groupName == anchor.groupName && it.isHeader }
    if (groupHeader >= 0) return groupHeader
    return items.indexOfFirst { it.isHeader }.takeIf { it >= 0 }
}

internal fun proxyGridScrollAnchor(
    groupName: String,
    proxyNames: List<String>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): ProxyScrollAnchor? = proxyNames.getOrNull(firstVisibleItemIndex)?.let {
    ProxyScrollAnchor(groupName, it, firstVisibleItemScrollOffset)
}

internal fun proxyGridScrollItemIndex(
    proxyNames: List<String>,
    anchor: ProxyScrollAnchor,
    columns: Int,
): Int? {
    require(columns > 0)
    val index = anchor.proxyName?.let(proxyNames::indexOf) ?: -1
    if (index >= 0) return (index / columns) * columns
    return proxyNames.indices.firstOrNull()?.let { (it / columns) * columns }
}
