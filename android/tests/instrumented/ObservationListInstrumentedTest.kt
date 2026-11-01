package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 作者 long：全屏观测列表只使用固定 Debug 夹具，验证工具栏视觉、DNS 详情快照恢复和返回列表状态。 */
@RunWith(AndroidJUnit4::class)
class ObservationListInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ObservationListTestActivity>()

    @Test
    fun observationRequestAndDnsListsCaptureDarkAndLightScreens() {
        assertPortraitDefaultFontConfiguration()
        val actual = linkedMapOf<String, String>()

        fun capture(name: String) {
            composeRule.waitForIdle()
            assertPortraitDefaultFontConfiguration()
            val bitmap = composeRule.onNodeWithTag("observation_list_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("observation-list-$name", png)
            actual[name] = MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
            assertTrue("full page screenshot must be portrait", bitmap.height > bitmap.width)
        }

        for (darkTheme in listOf(true, false)) {
            composeRule.activity.runOnUiThread {
                composeRule.activity.overrideDarkTheme(darkTheme)
                composeRule.activity.showRequestsPage()
            }
            capture("requests_${if (darkTheme) "dark" else "light"}")
            composeRule.activity.runOnUiThread { composeRule.activity.showDnsPage() }
            capture("dns_${if (darkTheme) "dark" else "light"}")
        }

        assertEquals(
            linkedMapOf(
                "requests_dark" to "ea9f2e3823f8c98f3ccc73455d8aab1eeaa4055581c00f29a5fc0b0d940be9bd",
                "dns_dark" to "5dc9d65a63a81f139dc82378cea41f56f02f5ad24cf73015548876c737833ed7",
                "requests_light" to "b952b8b44099bd6629fd33333d62b8d1cf92cbcb74a99cb38265faa50f96b38f",
                "dns_light" to "2550daaaa8bebb50314839cf5ce3adef05fc8845342da7dfbc487b0511aaf7b8",
            ),
            actual,
        )
    }

    @Test
    fun logsPageCapturesDarkAndLightScreens() {
        assertPortraitDefaultFontConfiguration()
        val actual = linkedMapOf<String, String>()

        for (darkTheme in listOf(true, false)) {
            composeRule.activity.runOnUiThread {
                composeRule.activity.overrideDarkTheme(darkTheme)
                composeRule.activity.showLogsPage()
            }
            composeRule.waitForIdle()
            assertPortraitDefaultFontConfiguration()
            val bitmap = composeRule.onNodeWithTag("observation_list_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("logs-page-${if (darkTheme) "dark" else "light"}", png)
            actual[if (darkTheme) "logs_dark" else "logs_light"] = MessageDigest
                .getInstance("SHA-256")
                .digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
            assertTrue("full Logs page screenshot must be portrait", bitmap.height > bitmap.width)
        }

        assertEquals(
            linkedMapOf(
                "logs_dark" to "018be45df75a8b7f2d121fdf2af1ff2ce062847b6b16488624857c4d9560e5bf",
                "logs_light" to "cbe7afba7d17037586701e4dea342629ba92c1caf0b5d2f8bded9f8c204425d5",
            ),
            actual,
        )
    }

    @Test
    fun logsPageFiltersByLevelKeywordAndExportsFromToolbar() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activity.runOnUiThread { composeRule.activity.showLogsPage() }

        composeRule.onNodeWithTag("observation_list_page").assertIsDisplayed()
        composeRule.onNodeWithText("日志").assertIsDisplayed()
        composeRule.onNodeWithText("显示 3/3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出日志").performClick()
        assertEquals(1, composeRule.activity.logExportRequests)

        composeRule.onNodeWithTag("log_level_error").performClick()
        composeRule.onNodeWithText("显示 1/3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("移除筛选 error").performClick()
        composeRule.onNodeWithText("显示 3/3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithText("搜索日志级别或内容").performTextInput("上游")
        composeRule.onNodeWithText("显示 1/3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithText("显示 3/3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("返回日志列表").performClick()
    }

    @Test
    fun requestsAndDnsEmptyStatesCaptureDarkAndLightScreens() {
        assertPortraitDefaultFontConfiguration()
        val actual = linkedMapOf<String, String>()

        fun capture(name: String) {
            composeRule.waitForIdle()
            assertPortraitDefaultFontConfiguration()
            val bitmap = composeRule.onNodeWithTag("observation_list_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("observation-empty-$name", png)
            actual[name] = MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
            assertTrue("full page screenshot must be portrait", bitmap.height > bitmap.width)
        }

        for (darkTheme in listOf(true, false)) {
            val theme = if (darkTheme) "dark" else "light"
            composeRule.activity.runOnUiThread {
                composeRule.activity.overrideDarkTheme(darkTheme)
                composeRule.activity.showRequestsEmptyPage()
            }
            composeRule.onNodeWithTag("requests_empty_state").assertIsDisplayed()
            composeRule.onNodeWithText("暂无请求").assertIsDisplayed()
            capture("requests_$theme")

            composeRule.activity.runOnUiThread { composeRule.activity.showDnsEmptyPage() }
            composeRule.onNodeWithTag("dns_empty_state").assertIsDisplayed()
            composeRule.onNodeWithText("DNS查询为空").assertIsDisplayed()
            capture("dns_$theme")
        }
        assertEquals(
            linkedMapOf(
                "requests_dark" to "8dfb97ab132dc843e4ab9657018742a1c81f1b3123029c38b884bd8adf528d4a",
                "dns_dark" to "60176bad2d5bdca7308a017f764520397b3f00b65a2f3fcb905cbfe513d86279",
                "requests_light" to "56e732cadcee8b8ccd0d429f71ac0c62982501c73a28d26322b3d0e07308e54c",
                "dns_light" to "dd2a5b4db443229465b6d292fc9603448c9f696883d18cf5867ef9dc0f9f7768",
            ),
            actual,
        )
    }

    @Test
    fun requestsAndDnsNoResultsUseTheSharedSearchEmptyState() {
        assertPortraitDefaultFontConfiguration()
        val actual = linkedMapOf<String, String>()

        fun capture(name: String, tag: String) {
            composeRule.waitForIdle()
            assertPortraitDefaultFontConfiguration()
            val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("observation-empty-$name", png)
            actual[name] = MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        for (darkTheme in listOf(true, false)) {
            val theme = if (darkTheme) "dark" else "light"
            composeRule.activity.runOnUiThread {
                composeRule.activity.overrideDarkTheme(darkTheme)
                composeRule.activity.showRequestsPage()
            }
            composeRule.onNodeWithContentDescription("搜索").performClick()
            composeRule.onNodeWithTag("observation_search_field").performTextInput("no-such-target")
            composeRule.onNodeWithTag("requests_no_results_state").assertIsDisplayed()
            composeRule.onNodeWithText("没有匹配的结果").assertIsDisplayed()
            capture("requests_no_results_$theme", "requests_no_results_state")

            composeRule.activity.runOnUiThread { composeRule.activity.showDnsPage() }
            composeRule.onNodeWithContentDescription("搜索").performClick()
            composeRule.onNodeWithTag("observation_search_field").performTextInput("no-such-domain")
            composeRule.onNodeWithTag("dns_no_results_state").assertIsDisplayed()
            composeRule.onNodeWithText("没有匹配的结果").assertIsDisplayed()
            capture("dns_no_results_$theme", "dns_no_results_state")
        }
        assertEquals(
            linkedMapOf(
                "requests_no_results_dark" to "2758d5841d3f62b00a8b6d71177d4a1a969daec998be0e47f32f944be7739250",
                "dns_no_results_dark" to "2758d5841d3f62b00a8b6d71177d4a1a969daec998be0e47f32f944be7739250",
                "requests_no_results_light" to "b4679d6d989ba343468a42f7bda2170d5b785a8fbd2c869b576026f7a80799f8",
                "dns_no_results_light" to "b4679d6d989ba343468a42f7bda2170d5b785a8fbd2c869b576026f7a80799f8",
            ),
            actual,
        )
    }

    @Test
    fun dnsDetailSnapshotAndSearchRestoreAfterActivityRecreation() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activity.runOnUiThread { composeRule.activity.showDnsPage() }
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("observation_search_field").performTextInput("rebuild-me.example.test")
        composeRule.onNodeWithTag("dns_row_0").assertIsDisplayed()
        composeRule.onNodeWithTag("dns_row_0").performTouchInput {
            click(Offset(width * 0.9f, height * 0.15f))
        }
        composeRule.onNodeWithText("DNS 详情").assertIsDisplayed()
        composeRule.onNodeWithTag("dns_detail_answer_0").performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("DNS 详情").assertIsDisplayed()
        composeRule.onNodeWithTag("dns_detail_answer_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭 DNS 详情").performClick()
        composeRule.onNodeWithTag("observation_search_field")
            .assertIsDisplayed()
            .assertTextEquals("rebuild-me.example.test")
        composeRule.onNodeWithTag("dns_row_0").assertIsDisplayed()
        composeRule.onNodeWithTag("dns_row_1").assertDoesNotExist()
    }

    @Test
    fun expandedSearchReplacesToolbarActionsAndClearKeepsSearchOpen() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activity.runOnUiThread { composeRule.activity.showDnsPage() }
        composeRule.onNodeWithContentDescription("清除 DNS 缓存").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("observation_search_field")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(44.dp)
        val capsuleBounds = composeRule.onNodeWithTag("observation_search_capsule")
            .fetchSemanticsNode().boundsInRoot
        val iconBounds = composeRule.onNodeWithTag("observation_search_icon")
            .fetchSemanticsNode().boundsInRoot
        val inputBounds = composeRule.onNodeWithTag("observation_search_field")
            .fetchSemanticsNode().boundsInRoot
        // 作者 long：Flutter 搜索字段把前缀固定为 41dp，分别由 12dp 图标内边距、20dp 图标和 9dp 间隔组成。
        with(composeRule.density) {
            assertEquals("search icon inset", 12.dp.toPx(), iconBounds.left - capsuleBounds.left, 1f)
            assertEquals("search text inset", 41.dp.toPx(), inputBounds.left - capsuleBounds.left, 1f)
            assertEquals("search icon to text gap", 9.dp.toPx(), inputBounds.left - iconBounds.right, 1f)
        }
        composeRule.onNodeWithContentDescription("返回DNS查询列表").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("清除 DNS 缓存").assertDoesNotExist()
        composeRule.onNodeWithTag("observation_search_clear").assertDoesNotExist()

        composeRule.onNodeWithTag("observation_search_field").performTextInput("resolver")
        composeRule.onNodeWithContentDescription("清除搜索").assertIsDisplayed().performClick()
        assertEquals(
            "",
            composeRule.onNodeWithTag("observation_search_field")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("observation_search_field").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回DNS查询列表").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("清除 DNS 缓存").assertIsDisplayed()
    }

    @Test
    fun dnsToolbarShowsSuccessfulCacheClearAndKeepsHistory() {
        composeRule.activity.runOnUiThread { composeRule.activity.showDnsPage() }
        composeRule.onNodeWithContentDescription("清除 DNS 缓存").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("DNS 缓存已清除").assertIsDisplayed()
        composeRule.onNodeWithTag("observation_list_page").assertIsDisplayed()
        composeRule.onNodeWithText("resolver.example.test").assertIsDisplayed()
        composeRule.activity.runOnUiThread {
            assertEquals(1, composeRule.activity.dnsCacheClearRequests)
        }
    }

    @Test
    fun dnsToolbarShowsCacheClearFailureAndKeepsHistory() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.showDnsPage()
            composeRule.activity.setDnsCacheClearSucceeds(false)
        }
        composeRule.onNodeWithContentDescription("清除 DNS 缓存").performClick()
        composeRule.onNodeWithText("清除 DNS 缓存失败").assertIsDisplayed()
        composeRule.onNodeWithText("resolver.example.test").assertIsDisplayed()
        composeRule.activity.runOnUiThread {
            assertEquals(1, composeRule.activity.dnsCacheClearRequests)
        }
    }
}
