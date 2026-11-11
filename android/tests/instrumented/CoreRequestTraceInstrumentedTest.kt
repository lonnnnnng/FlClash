package com.lonnnnnng.flclash

import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：失败取证不能依赖双向成功，且新增摘要不能泄露连接ID、规则或节点名称。 */
class CoreRequestTraceInstrumentedTest {
    private fun connection(id: String, upload: Long, download: Long) = CoreConnection(id, upload, download, "private-time",
        CoreConnectionMetadata("tcp", "private-ip", "12345", "private-destination", "443", "private-host", "private-process",
            "private-path", "private-remote", 1, "private-proxy", "private-rules"), listOf("private-node"), "private-rule", "private-payload")

    @Test fun oneWayConnectionRemainsVisibleOnFailureAndMaxCountersNeverRegress() {
        val trace = AcceptanceConnectionTrace()
        trace.record(connection("private-id", 0, 0), 10)
        trace.record(connection("private-id", 517, 0), 20)
        trace.record(connection("private-id", 0, 0), 30)
        val result = trace.snapshot(); val row = result.getJSONArray("matchedConnections").getJSONObject(0)
        assertEquals(517L, row.getLong("maxUpload")); assertEquals(0L, row.getLong("maxDownload"))
        assertEquals(10L, row.getLong("firstSeenElapsedMs")); assertEquals(30L, row.getLong("lastSeenElapsedMs"))
        assertFalse(row.getBoolean("positiveTraffic")); assertTrue(row.getBoolean("hasNonDirectChain"))
        assertFalse(result.getBoolean("wireBytesClaimed")); assertFalse(result.toString().contains("private-"))
        trace.record(connection("private-id", 800, 1500), 40)
        assertTrue(trace.snapshot().getJSONArray("matchedConnections").getJSONObject(0).getBoolean("positiveTraffic"))
    }

    @Test fun traceBoundsIdentitiesAndKeepsReadFailuresSeparateFromZeroTraffic() {
        val trace = AcceptanceConnectionTrace()
        repeat(7) { trace.record(connection("private-$it", 0, 0), it.toLong()) }
        trace.readFailed()
        val result = trace.snapshot()
        assertEquals(4, result.getJSONArray("matchedConnections").length())
        assertEquals(3, result.getInt("overflow")); assertEquals(1, result.getInt("readFailures"))
        assertFalse(result.toString().contains("private-"))
    }
}
