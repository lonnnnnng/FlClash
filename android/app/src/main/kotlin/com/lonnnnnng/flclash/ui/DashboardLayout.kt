package com.lonnnnnng.flclash.ui

import android.content.Context

internal enum class DashboardSection(
    val title: String,
    val description: String? = null,
    val visibleByDefault: Boolean = true,
    val removable: Boolean = true,
) {
    CURRENT_OUTBOUND("状态", "含启动键，不可移除", removable = false),
    NETWORK_STATS("网络速度"),
    OUTBOUND_MODE("出站模式"),
    PROFILES("配置"),
    PROXY_GROUPS("策略组"),
    // 作者 long：手机首页只收录 Flutter 的仪表盘卡片，系统授权、应用代理设置和验收诊断由各自页面承载。
    CONNECTION_STATS("连接统计", "实时连接、最近请求、DNS查询", visibleByDefault = false),
    MEMORY_INFO("内存信息", "应用与核心的内存占用", visibleByDefault = false),
    SERVICE_STATUS("服务状态", "常用服务可用性检测", visibleByDefault = false),
    VPN("VPN", "VPN 流量接管开关", visibleByDefault = false),
    INTRANET_IP("内网 IP", "本机地址与局域网共享", visibleByDefault = false),
}

internal data class DashboardLayoutState(
    val order: List<DashboardSection> = DashboardSection.entries.toList(),
    val visible: Set<DashboardSection> = DashboardSection.entries
        .filter(DashboardSection::visibleByDefault)
        .toSet(),
) {
    fun visibleInOrder(): List<DashboardSection> = order.filter(visible::contains)

    fun setVisible(section: DashboardSection, isVisible: Boolean): DashboardLayoutState {
        // 作者 long：状态卡承载唯一的首页启动入口，可以排序，但不允许移除或通过偏好隐藏。
        if (!isVisible && !section.removable) return this
        return copy(
            order = if (isVisible && section !in visible) {
                order.filterNot { it == section } + section
            } else {
                order
            },
            visible = if (isVisible) visible + section else visible - section,
        )
    }

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
            val required = DashboardSection.entries.filterNot(DashboardSection::removable)
            // 作者 long：旧版状态卡在偏好之外固定置顶，首次纳入排序时保留该位置；已保存的拖动位置不被覆盖。
            val order = (required.filterNot(decodedOrder::contains) + decodedOrder + DashboardSection.entries).distinct()
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
            return DashboardLayoutState(order = order, visible = visible + required)
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
        val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, PREFERENCES_NAME)
        return DashboardLayoutState.fromStored(
            orderNames = preferences.getString(KEY_ORDER, null)?.split(','),
            visibleNames = preferences.getString(KEY_VISIBLE, null)?.split(','),
            knownSectionNames = preferences.getString(KEY_KNOWN_SECTIONS, null)?.split(','),
        )
    }

    fun save(context: Context, layout: DashboardLayoutState) {
        com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, PREFERENCES_NAME)
            .edit()
            .putString(KEY_ORDER, layout.order.joinToString(",") { it.name })
            .putString(KEY_VISIBLE, layout.visible.joinToString(",") { it.name })
            .putString(KEY_KNOWN_SECTIONS, DashboardSection.entries.joinToString(",") { it.name })
            .apply()
    }

    internal fun clear(context: Context) {
        com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, PREFERENCES_NAME)
            .edit().clear().apply()
    }
}
