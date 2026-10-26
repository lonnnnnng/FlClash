package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
}
