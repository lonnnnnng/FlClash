package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.CoreBridge
import javax.inject.Inject

fun interface ProxyGroupFilterValidator {
    suspend fun validate(filters: List<String>): List<String>
}

class NativeProxyGroupFilterValidator @Inject constructor(private val bridge: CoreBridge) : ProxyGroupFilterValidator {
    // 作者 long：Mihomo 使用 regexp2 并按反引号拆分表达式，Java 正则不能代替核心校验。
    override suspend fun validate(filters: List<String>): List<String> = bridge.validateFilters(filters)
}
