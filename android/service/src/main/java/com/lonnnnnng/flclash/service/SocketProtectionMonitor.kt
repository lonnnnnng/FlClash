package com.lonnnnnng.flclash.service

import java.util.concurrent.atomic.AtomicLong

/** 作者 long：TUN存在不代表核心出站已绕过VPN；只计数保护结果，不保存fd或网络端点。 */
class SocketProtectionMonitor {
    private val accepted = AtomicLong()
    private val rejected = AtomicLong()
    private val errors = AtomicLong()

    fun protect(action: () -> Boolean): Boolean = try {
        action().also { if (it) accepted.incrementAndGet() else rejected.incrementAndGet() }
    } catch (failure: Throwable) {
        errors.incrementAndGet()
        throw failure
    }

    fun snapshot() = SocketProtectionSnapshot(accepted.get(), rejected.get(), errors.get())
}

data class SocketProtectionSnapshot(val accepted: Long, val rejected: Long, val errors: Long)
