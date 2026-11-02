package com.lonnnnnng.flclash.core

data class CoreRouteStamp(val epoch: Long, val version: Long)

/** 作者 long：原始正文只在解析边界短暂持有，不允许对象日志泄露 IP 服务返回内容。 */
class CoreIpResponse(
    val sourceUrl: String,
    val body: String,
    val error: String,
    val statusCode: Int,
    val stamp: CoreRouteStamp,
) {
    override fun toString() = "CoreIpResponse(statusCode=$statusCode, stamp=$stamp)"
}
