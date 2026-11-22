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
import org.junit.Assert.assertTrue
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
                composeRule.onNodeWithText("DNS详情").assertIsDisplayed()
                composeRule.onNodeWithTag("connections_detail_sheet").assertIsDisplayed()
                composeRule.onNodeWithTag("connection_detail_scrim").assertExists()
                val sheetBounds = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot
                val toolbarBounds = composeRule.onNodeWithTag("dns_detail_title_toolbar").fetchSemanticsNode().boundsInRoot
                val titleBounds = composeRule.onNodeWithTag("dns_detail_title").fetchSemanticsNode().boundsInRoot
                val closeBounds = composeRule.onNodeWithTag("dns_detail_close").fetchSemanticsNode().boundsInRoot
                val handleBounds = composeRule.onNodeWithTag("connection_detail_handle").fetchSemanticsNode().boundsInRoot
                val contentBounds = composeRule.onNodeWithTag("dns_detail_scroll").fetchSemanticsNode().boundsInRoot
                val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext.resources.displayMetrics.density
                assertEquals(48f * density, toolbarBounds.height, 2f)
                assertEquals(10f * density, handleBounds.height, 2f)
                assertEquals(68f * density, contentBounds.top - sheetBounds.top, 2f)
                assertEquals(sheetBounds.center.x, titleBounds.center.x, 2f)
                assertTrue("Flutter close action leads the centered title", closeBounds.center.x < titleBounds.center.x)
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
                // 作者 long：展开态内容可能略长于设备视口，先滚到真实末端，避免语义自动对齐按帧选择不同的像素偏移。
                composeRule.onNodeWithTag("dns_detail_scroll")
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
                composeRule.waitForIdle()
                if (isFailed) {
                    // 作者 long：缺失来源与发起方应隐藏，错误场景没有答案时不保留空标题占位。
                    composeRule.onNodeWithTag("connection_detail_发起方").assertDoesNotExist()
                    composeRule.onNodeWithTag("connection_detail_上游").assertDoesNotExist()
                    composeRule.onNodeWithText("答案").assertDoesNotExist()
                    composeRule.onNodeWithTag("connection_detail_错误").assertIsDisplayed()
                    capture("${name}_expanded")
                    composeRule.onNodeWithTag("connection_detail_错误").performClick()
                    assertEquals(failure.error, copiedText)
                } else {
                    composeRule.onNodeWithText("错误").assertDoesNotExist()
                    composeRule.onNodeWithTag("dns_detail_answer_1").assertIsDisplayed()
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
        // 作者 long：展开拖动后的整窗截图受滚动窗口合成影响，只核对稳定半屏像素基线；展开态仍保留内容、可见性与复制断言。
        assertEquals(
            setOf(
                "answers_dark_partial", "error_dark_partial",
                "answers_light_partial", "error_light_partial",
                "answers_dark_expanded", "error_dark_expanded",
                "answers_light_expanded", "error_light_expanded",
            ),
            actual.keys,
        )
        assertEquals(
            linkedMapOf(
                "answers_dark_partial" to "ce7dab59b5452da62081945386d8ef534d128b99b6280e38f2409640560d74e0",
                "error_dark_partial" to "160541a0d025045aef3b39c07bb1c4c840ad78f542e0ec01b658e5805a75dc01",
                "answers_light_partial" to "15ac4326327d454d05cd29d802c7363c1a614c164e2638f256b57f1dd07ef791",
                "error_light_partial" to "1c2352e67eecf7526f15cdc15f360180b7b2da471928174b2739f3e10d89fccf",
            ),
            actual.filterKeys { it.endsWith("_partial") },
        )
    }
}
