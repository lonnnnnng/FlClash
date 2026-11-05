package com.lonnnnnng.flclash.service

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SocketProtectionMonitorTest {
    @Test fun rejectedProtectionRemainsRejectedAndIsCountedSeparately() {
        val monitor = SocketProtectionMonitor()
        assertTrue(monitor.protect { true })
        assertFalse(monitor.protect { false })
        assertEquals(SocketProtectionSnapshot(1, 1, 0), monitor.snapshot())
    }

    @Test fun platformFailureIsRethrownWithoutClaimingProtection() {
        val monitor = SocketProtectionMonitor()
        val failure = IllegalStateException("平台已撤销VPN")
        try { monitor.protect { throw failure }; fail("必须保留平台失败") }
        catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertEquals(SocketProtectionSnapshot(0, 0, 1), monitor.snapshot())
    }

    @Test fun concurrentCoreDialsDoNotLoseProtectionResults() {
        val monitor = SocketProtectionMonitor()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 1000).map { i -> pool.submit { monitor.protect { i % 2 == 0 } } }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(SocketProtectionSnapshot(500, 500, 0), monitor.snapshot())
        } finally { pool.shutdownNow() }
    }
}
