package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 作者 long：记录卡片的异常状态必须有可见文字，不能仅靠颜色或成功记录截图代表完整状态覆盖。 */
@RunWith(AndroidJUnit4::class)
class RecordCardsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordCardsMatchDarkAndLightBaselines() {
        var dark by mutableStateOf(true)
        var kind by mutableStateOf(RecordKind.CONNECTION)
        composeRule.setContent {
            KClashTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxWidth().testTag("record_gallery"), color = recordPageColor()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (kind) {
                            RecordKind.CONNECTION -> {
                                ConnectionRow(connection())
                                ConnectionRow(connection().copy(
                                    metadata = connection().metadata.copy(host = LONG_HOST),
                                    chains = listOf("DIRECT", "用于观察代理链换行的较长节点名称", "GLOBAL"),
                                    rule = "DomainSuffix",
                                    rulePayload = "example.test",
                                ))
                                ConnectionRow(connection().copy(
                                    start = "",
                                    metadata = connection().metadata.copy(
                                        host = "", destinationIp = "", destinationPort = "", remoteDestination = "",
                                        network = "", process = "", uid = 0L, sourceIp = "", sourcePort = "",
                                    ),
                                    chains = emptyList(), rule = "", rulePayload = "",
                                ))
                            }
                            RecordKind.DNS -> {
                                DnsQueryRow(dnsQuery().copy(cached = true))
                                DnsQueryRow(dnsQuery().copy(domain = LONG_HOST, answers = emptyList(), rcode = "NXDOMAIN", delay = 0L))
                                DnsQueryRow(dnsQuery().copy(domain = "timeout.example.test", answers = emptyList(), error = "DNS 上游连接超时", delay = 5_000L))
                            }
                            RecordKind.LOG -> {
                                LogRow(CoreEvent.Log("info", "核心配置已加载", TIMESTAMP))
                                LogRow(CoreEvent.Log("warning", "DNS 上游响应较慢，将重试查询。", TIMESTAMP))
                                LogRow(CoreEvent.Log("error", "连接失败：无法连接目标服务器，稍后重试。", TIMESTAMP))
                                LogRow(CoreEvent.Log("", "", TIMESTAMP))
                            }
                        }
                    }
                }
            }
        }
        val actual = buildMap {
            for (isDark in listOf(true, false)) {
                dark = isDark
                for (recordKind in RecordKind.entries) {
                    kind = recordKind
                    composeRule.waitForIdle()
                    assertPortraitDefaultFontConfiguration()
                    when (recordKind) {
                        RecordKind.CONNECTION -> {
                            composeRule.onNodeWithText("api.example.test:443  192.0.2.10").assertIsDisplayed()
                            composeRule.onNodeWithText("未知目标").assertIsDisplayed()
                            // 作者 long：累计流量不能伪装成实时速率；Bridge 没有该字段时不展示每秒数值。
                            composeRule.onNodeWithText("/s", substring = true).assertDoesNotExist()
                        }
                        RecordKind.DNS -> {
                            composeRule.onNodeWithText("缓存").assertIsDisplayed()
                            composeRule.onNodeWithText("NXDOMAIN").assertIsDisplayed()
                            composeRule.onNodeWithText("0 ms").assertIsDisplayed()
                            composeRule.onNodeWithText("DNS 上游连接超时").assertIsDisplayed()
                            composeRule.onNodeWithText("失败").assertIsDisplayed()
                        }
                        RecordKind.LOG -> {
                            composeRule.onNodeWithText("warning").assertIsDisplayed()
                            composeRule.onNodeWithText("error").assertIsDisplayed()
                            composeRule.onNodeWithText("（空日志）").assertIsDisplayed()
                        }
                    }
                    val name = "${recordKind.name.lowercase()}_${if (isDark) "dark" else "light"}"
                    val bitmap = composeRule.onNodeWithTag("record_gallery").captureToImage().asAndroidBitmap()
                    val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    recordVisualPng("records-$name", png)
                    put(name, MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) })
                }
            }
        }
        assertEquals(
            mapOf(
                "connection_dark" to "dcb7054f26f8c5f23149572a42ee136fa5e3319565e2e33eac4b32203f07b2ca",
                "dns_dark" to "8e1f88106e1b8885ffc4553e29832e14b2624d01cb4a7fca61de6dd12964cdd7",
                "log_dark" to "dcb443b64cdefe94f123d0bd7b90c1659268c04ba498e755a9c51b22af8dbf77",
                "connection_light" to "58f136f5e54ed37f59ebaa6f1f3650e5ab03ca1605fb97ae3e05183d0e5314df",
                "dns_light" to "fad91e33843c8a636c2640090d2b9f81bb079d40036abfb2d645502c6d8e0c4a",
                "log_light" to "9e8ff042d49da3faf274c827470440b34c8a8bf5eab33b0d4b731b9bd6f9c72f",
            ),
            actual,
        )
    }

    private fun connection() = CoreConnection(
        id = "record-fixture", upload = 12_345L, download = 67_890L, start = "2026-10-28T00:00:00Z",
        metadata = CoreConnectionMetadata(
            network = "tcp", sourceIp = "10.0.0.2", sourcePort = "54321", destinationIp = "192.0.2.10",
            destinationPort = "443", host = "api.example.test", process = "com.example.client", processPath = "",
            remoteDestination = "", uid = 10_000L, specialProxy = "", specialRules = "",
        ),
        chains = listOf("GLOBAL", "DIRECT"), rule = "MATCH", rulePayload = "",
    )

    private fun dnsQuery() = CoreDnsQuery(
        domain = "api.example.test", type = "A", initiator = "com.example.client", upstream = "192.0.2.53",
        cached = false, answers = listOf("192.0.2.10", "2001:db8::10"), rcode = "NOERROR", error = "",
        delay = 24L, time = "2026-10-28T00:00:00Z",
    )

    private enum class RecordKind { CONNECTION, DNS, LOG }

    private companion object {
        const val LONG_HOST = "a-long-hostname-for-record-wrapping-and-visual-verification.api.example.test"
        const val TIMESTAMP = 1_793_145_600_000L
    }
}
