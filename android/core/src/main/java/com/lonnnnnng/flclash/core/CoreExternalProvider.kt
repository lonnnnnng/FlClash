package com.lonnnnnng.flclash.core

/** 作者 long：详情只保留可展示字段，核心的磁盘路径和订阅信息不跨出 Bridge。 */
data class CoreExternalProvider(val name: String, val type: String, val vehicleType: String,
    val count: Long, val updatedAtMillis: Long?) {
    val canUpdate: Boolean get() = vehicleType == "HTTP"
}
