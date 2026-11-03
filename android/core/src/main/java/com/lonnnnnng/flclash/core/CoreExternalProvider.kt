package com.lonnnnnng.flclash.core

/** 作者 long：详情只保留可展示字段和有界用量，核心文件路径和未知订阅字段不跨出Bridge。 */
data class CoreExternalProvider(val name: String, val type: String, val vehicleType: String,
    val count: Long, val updatedAtMillis: Long?, val subscriptionInfo: CoreProviderSubscription? = null,
    val fileIdentity: String? = null) {
    val canUpdate: Boolean get() = vehicleType == "HTTP"
}

data class CoreProviderSubscription(val upload: Long, val download: Long, val total: Long, val expireAtMillis: Long?) {
    val used: Long get() = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download
    val usage: Float get() = ((upload.toDouble() + download) / total).coerceIn(0.0, 1.0).toFloat()
}

/** 作者 long：正文可能带节点凭据，不使用data class自动生成包含正文的toString。 */
class CoreProviderDocument(val name: String, val kind: String, val content: String, val digest: String) {
    val editable: Boolean get() = kind == "text"
}

data class CoreProviderUpdateResult(val name: String, val code: String)
