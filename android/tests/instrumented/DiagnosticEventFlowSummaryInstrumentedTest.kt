package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：事件流健康摘要是诊断证据的可见边界，必须在 Android Compose 环境验证健康和丢失两种文案。
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticEventFlowSummaryInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun healthyStatsShowAllCountersWithoutIncompleteWarning() {
        composeRule.setContent {
            KClashTheme {
                DiagnosticEventFlowSummary(
                    CoreEventFlowStats(
                        parsedEvents = 170,
                        emittedEvents = 170,
                    ),
                )
            }
        }

        composeRule
            .onNodeWithText("事件流：解析 170 · 投递 170 · 丢弃 0 · 截断 0")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("事件流存在丢弃或截断，诊断证据可能不完整。").assertCountEquals(0)
    }

    @Test
    fun lossStatsShowIncompleteWarning() {
        composeRule.setContent {
            KClashTheme {
                DiagnosticEventFlowSummary(
                    CoreEventFlowStats(
                        parsedEvents = 170,
                        emittedEvents = 168,
                        droppedEvents = 2,
                        truncatedEvents = 1,
                    ),
                )
            }
        }

        composeRule
            .onNodeWithText("事件流：解析 170 · 投递 168 · 丢弃 2 · 截断 1")
            .assertIsDisplayed()
        composeRule.onNodeWithText("事件流存在丢弃或截断，诊断证据可能不完整。").assertIsDisplayed()
    }

    @Test
    fun zeroParsedLossStatsStillShowIncompleteWarning() {
        composeRule.setContent {
            KClashTheme {
                DiagnosticEventFlowSummary(
                    CoreEventFlowStats(droppedEvents = 1),
                )
            }
        }

        composeRule
            .onNodeWithText("事件流：解析 0 · 投递 0 · 丢弃 1 · 截断 0")
            .assertIsDisplayed()
        composeRule.onNodeWithText("事件流存在丢弃或截断，诊断证据可能不完整。").assertIsDisplayed()
    }

    @Test
    fun findingSummaryShowsHttp403GuidanceAndRedactedDetails() {
        composeRule.setContent {
            KClashTheme {
                DiagnosticFindingSummary(
                    DiagnosticSessionUiState(
                        status = DiagnosticSessionStatus.FAIL,
                        sessionId = 3L,
                        result = com.lonnnnnng.flclash.core.CoreProbeResult(
                            statusCode = 403,
                            delay = 120L,
                            url = "https://example.test/config",
                            chains = listOf("GLOBAL", "DIRECT"),
                            rule = "MATCH",
                            rulePayload = "",
                            error = "",
                            message = "访问失败 https://example.test/config?token=secret-value",
                            coreEpoch = 1L,
                            routeVersion = 2L,
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("诊断归因：HTTP 响应已收到（HTTP_RESPONSE）").assertIsDisplayed()
        composeRule.onNodeWithText("已收到 HTTP 403", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("查看原始详情").performClick()
        composeRule.onNodeWithText("原始详情：访问失败", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("[REDACTED_SECRET]", substring = true).assertIsDisplayed()
    }
}
