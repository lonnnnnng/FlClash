package com.lonnnnnng.flclash.ui

import android.content.Context

internal enum class DashboardSection(
    val title: String,
    val description: String,
    val visibleByDefault: Boolean = true,
) {
    NETWORK_STATS("网络速度", "查看实时上传和下载流量"),
    OUTBOUND_MODE("出站模式", "切换规则、全局或直连"),
    PROFILES("配置", "查看当前配置或快速切换"),
    PROXY_GROUPS("代理组", "选择代理节点并执行测速"),
    BATTERY_OPTIMIZATION("电池优化", "查看后台运行限制状态"),
    ALWAYS_ON_VPN("始终开启 VPN", "管理 Android 系统 VPN 选项"),
    ACCESS_CONTROL("按应用代理", "选择需要经过代理的应用"),
    PROBE("真实请求诊断", "检查当前配置的连通性"),
    CONNECTION_STATS("连接统计", "查看实时连接、最近请求和 DNS 查询", visibleByDefault = false),
}

internal data class DashboardLayoutState(
    val order: List<DashboardSection> = DashboardSection.entries.toList(),
    val visible: Set<DashboardSection> = DashboardSection.entries
        .filter(DashboardSection::visibleByDefault)
        .toSet(),
) {
    fun visibleInOrder(): List<DashboardSection> = order.filter(visible::contains)

    fun setVisible(section: DashboardSection, isVisible: Boolean): DashboardLayoutState = copy(
        order = if (isVisible && section !in visible) {
            order.filterNot { it == section } + section
        } else {
            order
        },
        visible = if (isVisible) visible + section else visible - section,
    )

    fun reorderVisible(from: DashboardSection, to: DashboardSection): DashboardLayoutState {
        val visibleOrder = visibleInOrder().toMutableList()
        val fromIndex = visibleOrder.indexOf(from)
        val toIndex = visibleOrder.indexOf(to)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return this
        visibleOrder.add(toIndex, visibleOrder.removeAt(fromIndex))
        // 作者 long：隐藏卡片不参与拖动目标，避免排序时把用户移除的卡片重新插入首页。
        val hiddenOrder = order.filterNot(visible::contains)
        return copy(order = visibleOrder + hiddenOrder)
    }

    fun move(section: DashboardSection, offset: Int): DashboardLayoutState {
        val from = order.indexOf(section)
        val to = from + offset
        // 作者 long：列表两端的排序操作在模型层再次拦截，避免边界按钮写出无效卡片顺序。
        if (from < 0 || to !in order.indices) return this
        return copy(order = order.toMutableList().apply {
            add(to, removeAt(from))
        })
    }

    companion object {
        fun fromStored(
            orderNames: List<String>?,
            visibleNames: List<String>?,
            knownSectionNames: List<String>? = null,
        ): DashboardLayoutState {
            val decodedOrder = orderNames.orEmpty().mapNotNull { name ->
                DashboardSection.entries.firstOrNull { it.name == name }
            }
            // 作者 long：新增卡片追加到旧顺序末尾；可选卡片仅进入编辑候选区，不强行改变用户首页。
            val order = (decodedOrder + DashboardSection.entries).distinct()
            val newlyAdded = knownSectionNames?.toSet()?.let { known ->
                DashboardSection.entries.filter {
                    it.name !in known && it.visibleByDefault
                }
            }.orEmpty()
            val visible = visibleNames?.mapNotNull { name ->
                DashboardSection.entries.firstOrNull { it.name == name }
            }?.toSet()?.plus(newlyAdded) ?: DashboardSection.entries
                .filter(DashboardSection::visibleByDefault)
                .toSet()
            return DashboardLayoutState(order = order, visible = visible)
        }
    }
}

/** 作者 long：主页布局只保存 Android 应用级展示偏好，不写入 Profile 配置或订阅文件。 */
internal object DashboardLayoutStore {
    private const val PREFERENCES_NAME = "dashboard_layout"
    private const val KEY_ORDER = "order"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_KNOWN_SECTIONS = "known_sections"

    fun load(context: Context): DashboardLayoutState {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return DashboardLayoutState.fromStored(
            orderNames = preferences.getString(KEY_ORDER, null)?.split(','),
            visibleNames = preferences.getString(KEY_VISIBLE, null)?.split(','),
            knownSectionNames = preferences.getString(KEY_KNOWN_SECTIONS, null)?.split(','),
        )
    }

    fun save(context: Context, layout: DashboardLayoutState) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, layout.order.joinToString(",") { it.name })
            .putString(KEY_VISIBLE, layout.visible.joinToString(",") { it.name })
            .putString(KEY_KNOWN_SECTIONS, DashboardSection.entries.joinToString(",") { it.name })
            .apply()
    }

    internal fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
