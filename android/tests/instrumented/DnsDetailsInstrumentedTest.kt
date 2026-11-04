package com.lonnnnnng.flclash.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 作者 long：独立固定 DNS 夹具能核对错误与多答案的实际布局，不依赖核心、网络或设备中保存的配置。 */
@RunWith(AndroidJUnit4::class)
class DnsDetailsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dnsDetailsMatchDarkAndLightBaselinesAndCopyValues() {
        var dark by mutableStateOf(true)
        var failed by mutableStateOf(false)
        var rejectCopy = false
        var copiedText: String? = null
        val success = CoreDnsQuery(
            domain = "a-long-hostname-for-dns-details.api.example.test",
            type = "AAAA",
            initiator = "com.example.client",
            upstream = "https://dns.example.test/dns-query",
            cached = true,
            answers = listOf("192.0.2.10", "2001:db8:1234:5678:90ab:cdef:1234:5678"),
            rcode = "NOERROR",
            error = "",
            delay = 24L,
            time = "2026-10-28T00:00:00Z",
        )
        val failure = success.copy(
            initiator = "",
            upstream = "",
            cached = false,
            answers = emptyList(),
            rcode = "SERVFAIL",
            error = "DNS 上游连接超时：无法从服务器获得响应，请稍后重试。",
            delay = 5_000L,
        )
        composeRule.setContent {
            KClashTheme(darkTheme = dark) {
                Surface {
                    // 作者 long：主题与场景切换都重建面板，保证每张顶部截图从相同停靠点和滚动位置开始。
                    key(dark, failed) {
                        DnsQueryDetailSheet(
                            query = if (failed) failure else success,
                            onDismiss = {},
                            onCopyText = {
                                if (rejectCopy) error("clipboard unavailable")
                                copiedText = it
                            },
                        )
                    }
                }
            }
        }

        val actual = linkedMapOf<String, String>()
        fun capture(name: String) {
            composeRule.waitForIdle()
            assertPortraitDefaultFontConfiguration()
            val bitmap = composeRule.onNodeWithTag("connections_detail_sheet").captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }.toByteArray()
            recordVisualPng("dns-details-$name", png)
            actual[name] = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
        }
        for (isDark in listOf(true, false)) {
            for (isFailed in listOf(false, true)) {
                composeRule.runOnIdle { dark = isDark; failed = isFailed; rejectCopy = false; copiedText = null }
                composeRule.onNodeWithText("DNS 详情").assertIsDisplayed()
                composeRule.onNodeWithTag("connections_detail_sheet").assertIsDisplayed()
                composeRule.onNodeWithTag("connection_detail_scrim").assertExists()
                val name = "${if (isFailed) "error" else "answers"}_${if (isDark) "dark" else "light"}"
                capture("${name}_partial")
                // 作者 long：通过正式拖柄展开，截图前等待点击波纹结束，避免把瞬时反馈写入稳定基线。
                composeRule.onNodeWithTag("connection_detail_handle").performClick()
                composeRule.mainClock.advanceTimeBy(600)
                composeRule.waitUntil(5_000) {
                    val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
                    val sheet = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode()
                    kotlin.math.abs(viewport.height * 0.9f - sheet.boundsInRoot.height) <= 2f
                }
                if (isFailed) {
                    // 作者 long：缺失来源与发起方应隐藏，错误场景没有答案时不保留空标题占位。
                    composeRule.onNodeWithTag("connection_detail_发起方").assertDoesNotExist()
                    composeRule.onNodeWithTag("connection_detail_上游").assertDoesNotExist()
                    composeRule.onNodeWithText("答案").assertDoesNotExist()
                    composeRule.onNodeWithTag("connection_detail_错误").performScrollTo().assertIsDisplayed()
                    capture("${name}_expanded")
                    composeRule.onNodeWithTag("connection_detail_错误").performClick()
                    assertEquals(failure.error, copiedText)
                } else {
                    composeRule.onNodeWithText("错误").assertDoesNotExist()
                    composeRule.onNodeWithTag("dns_detail_answer_1").performScrollTo().assertIsDisplayed()
                    capture("${name}_expanded")
                    composeRule.onNodeWithTag("dns_detail_answer_1").performClick()
                    assertEquals(success.answers[1], copiedText)
                }
                composeRule.onNodeWithText("已复制").assertIsDisplayed()
                composeRule.runOnIdle { rejectCopy = true }
                val copyTag = if (isFailed) "connection_detail_错误" else "dns_detail_answer_1"
                composeRule.onNodeWithTag(copyTag).performScrollTo().performClick()
                composeRule.onNodeWithText("复制失败").assertIsDisplayed()
            }
        }
        // 作者 long：基线经真机 PNG 人工复核，只代表当前设备的稳定渲染，不替代 Flutter 对照验收。
        assertEquals(
            linkedMapOf(
                "answers_dark_partial" to "e2027d9a3006f5f960a133ec9c1e6d72bc281963049767b27fcec0d5701a3c92",
                "answers_dark_expanded" to "175ab123d1cb6fa6b685082e16f8431de29438cef3cf5306a454ad7b23c3e978",
                "error_dark_partial" to "7c8bcd7a0ffa0bc7373c9e85042519a4000fd05a7fab98c64943f7f59800d150",
                "error_dark_expanded" to "80b4a3048bc43ef23d851700c296aea406a2f72a40f191863a04242ee8b6d4c0",
                "answers_light_partial" to "70b21c8814a0ae19eafc7a44c660027a510ba4efb6064181bd3c27304ccee586",
                "answers_light_expanded" to "f7e93cc2eaac60040f7e1b250416d66efa6e10d4468f0b39c47180cc0c40c843",
                "error_light_partial" to "070da8fc42f2f6953190c867673b64d797346f97ea22cef8bc686e9ce1f1f151",
                "error_light_expanded" to "b7d1e6a21c75f4be31d0ca6904bbc63373f59e8497d66c3e9a35a663463895df",
            ),
            actual,
        )
    }
}
