package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.LocalProxy

/** 作者 long：UI树之外还要保存两层目录基线；恢复旧输入时不能刷新CAS基线而覆盖并发保存。 */
internal data class LocalProxyDraftSnapshot(
    val version: Int = 1,
    val state: LocalProxiesUiState,
    val opened: List<LocalProxy>,
    val expected: List<LocalProxy>,
    val seenNames: Set<String>,
    val pending: LocalProxyPendingWrite? = null,
    val pendingClose: Boolean = false,
) { override fun toString() = "LocalProxyDraftSnapshot([REDACTED])" }

internal data class LocalProxyPendingWrite(val kind: String, val next: List<LocalProxy>, val rename: Pair<String, String>? = null) {
    override fun toString() = "LocalProxyPendingWrite([REDACTED])"
}
