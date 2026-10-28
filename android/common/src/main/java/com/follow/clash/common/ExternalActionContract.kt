package com.lonnnnnng.flclash.common

/**
 * 作者 long：外部自动化入口只允许固定的三种运行动作，并拒绝 URI 与额外参数，
 * 将 Tasker/MacroDroid 的调用面固定成可审计协议，避免 Intent 演变成任意命令通道。
 */
object ExternalActionContract {
    private val supportedActions = QuickAction.entries
        .mapTo(linkedSetOf()) { "${Components.PACKAGE_NAME}.action.${it.name}" }

    fun accepts(action: String?, hasData: Boolean, hasExtras: Boolean): Boolean =
        action in supportedActions && !hasData && !hasExtras
}
