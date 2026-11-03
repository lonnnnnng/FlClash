package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整版批选、问题及图标集中回归，远程图片只访问测试自建回环服务，不读取正式配置。 */
class ProxyGroupParityInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val source = "proxy-groups: [{name: Main, type: select, proxies: [DIRECT]}, {name: Other, type: fallback, proxies: [Main]}]\nrules: ['MATCH,Main']\n"

    @Test fun batchCopyDeleteAndBackPreserveOriginalGroupsInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var saved: ProxyGroupsSave? = null
        var dismissed = false
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            ProfileProxyGroupsEditorDialog(Profile.custom(if (dark) 891 else 890, "策略组批选"), ProfileProxyGroupsEditor.extractGroups(source), false,
                { dismissed = true }, {}, sourceContent = source, onSaveDraft = { saved = it })
        } } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; saved = null; dismissed = false }
            compose.onNodeWithTag("proxy_group_check_0").performClick()
            compose.onNodeWithTag("proxy_group_select_all").performClick()
            capture("groups_batch_selected_$theme")
            compose.onNodeWithTag("proxy_group_batch_copy").performClick()
            compose.onNodeWithText("Main 副本").assertIsDisplayed()
            compose.onNodeWithTag("proxy_group_check_0").performClick()
            compose.onNodeWithTag("proxy_group_batch_delete").performClick()
            compose.onNodeWithText("以下策略组仍被引用：Main。删除后需要修复规则或节点引用。").assertIsDisplayed()
            capture("groups_batch_delete_warning_$theme")
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("proxy_group_list_back").performClick()
            assertFalse(dismissed)
            compose.onNodeWithTag("proxy_group_select").performClick()
            compose.onNodeWithTag("proxy_group_check_1").performClick()
            compose.onNodeWithTag("proxy_group_check_3").performClick()
            compose.onNodeWithTag("proxy_group_batch_delete").performClick()
            compose.onNodeWithTag("proxy_group_delete_confirm").performClick()
            compose.onNodeWithTag("proxy_group_list_save").performClick()
            compose.runOnIdle { assertEquals(listOf("Main", "Other"), checkNotNull(saved).groups.map { it.name }) }
        }
    }

    @Test fun missingMemberWarningDoesNotBlockConfirmAndLoopsStayVisible() {
        assertPortraitDefaultFontConfiguration()
        var saved: ProxyGroupsSave? = null
        val content = source.replace("proxies: [DIRECT]", "proxies: [Gone]")
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(892, "问题提示"), ProfileProxyGroupsEditor.extractGroups(content), false,
                {}, {}, sourceContent = content, initialGroupIndex = 0, onSaveDraft = { saved = it })
        } }
        compose.onNodeWithText("缺失代理或策略组：Gone（可保留，运行前请修复）").assertIsDisplayed()
        capture("groups_missing_member_warning")
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("Gone"), checkNotNull(saved).groups.first().strings("proxies")) }
    }

    @Test fun realCoreFilterValidationRejectsInvalidPartAndAcceptsRegexp2Lookbehind() {
        assertPortraitDefaultFontConfiguration()
        val validator = NativeProxyGroupFilterValidator(CoreBridge())
        val errors = runBlocking { validator.validate(listOf("(?<=US)A", "valid`[", "")) }
        assertEquals(3, errors.size); assertEquals("", errors[0]); assertTrue(errors[1].isNotBlank()); assertEquals("", errors[2])
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(893, "过滤校验"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { saved = it }, onValidateFilters = validator::validate)
        } }
        compose.onNodeWithTag("proxy_group_filter").performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("[")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("proxy_group_form_issues").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle { assertNull(saved) }
        capture("groups_invalid_filter")
        compose.onNodeWithTag("proxy_group_filter").performScrollTo().performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("(?<=US)A")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals("(?<=US)A", checkNotNull(saved).groups.first().text("filter"))
    }

    @Test fun iconPickerSearchRemovalAndUrlValidationKeepOriginalUntilSelection() {
        assertPortraitDefaultFontConfiguration()
        val svg = "data:image/svg+xml;base64," + android.util.Base64.encodeToString(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"32\" height=\"32\"><circle cx=\"16\" cy=\"16\" r=\"12\" fill=\"#7258a6\"/></svg>".toByteArray(), android.util.Base64.NO_WRAP)
        var dark by mutableStateOf(false)
        var chosen: String? = null
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            ProfileGroupIconPicker(svg, "Main", listOf(svg), { _, _ -> }, onDismiss = {}, onSelect = { chosen = it })
        } } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; chosen = null }
            compose.onNodeWithTag("group_icon_search").performTextReplacement("没有这个图标")
            compose.onNodeWithText("没有匹配的图标").assertIsDisplayed()
            compose.onNodeWithTag("group_icon_search").performTextClearance()
            compose.onNodeWithTag("group_icon_link").performClick()
            compose.onNodeWithTag("group_icon_url").performTextReplacement("file:///private/image.png")
            compose.onNodeWithTag("group_icon_url_confirm").performClick()
            compose.onNodeWithText("请输入有效的 HTTP 或 HTTPS 图标地址").assertIsDisplayed()
            assertNull(chosen)
            compose.onNodeWithText("取消").performClick()
            capture("groups_icon_picker_$theme")
            compose.onNodeWithTag("group_icon_remove").performClick()
            assertEquals("", chosen)
        }
    }

    @Test fun remoteSvgAndRasterLoadWhileFailedImageShowsFallback() {
        assertPortraitDefaultFontConfiguration()
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"32\" height=\"32\"><path d=\"M4 4H28V28H4Z\" fill=\"#7258a6\"/></svg>".toByteArray()
        val raster = ByteArrayOutputStream().also { output -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff338855.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, output) }.toByteArray()
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true, name = "group-icon-loopback") {
            runCatching {
                while (!server.isClosed) server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader().readLine().orEmpty()
                    val isSvg = request.contains("/icon.svg")
                    val failed = request.contains("/missing")
                    val body = if (failed) ByteArray(0) else if (isSvg) svg else raster
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 " + if (failed) "404 Not Found\r\n" else "200 OK\r\n").toByteArray())
                        write("Content-Type: ${if (isSvg) "image/svg+xml" else "image/png"}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body); flush()
                    }
                }
            }
        }
        try {
            val base = "http://localhost:${server.localPort}"
            compose.setContent { KClashTheme { Row {
                ProfileGroupIcon("$base/icon.svg", "SVG", Modifier.size(56.dp).testTag("svg_icon"))
                ProfileGroupIcon("$base/icon.png", "PNG", Modifier.size(56.dp).testTag("png_icon"))
                ProfileGroupIcon("$base/missing", "Missing", Modifier.size(56.dp).testTag("missing_icon"))
            } } }
            val loaded = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "图标已加载")
            val failed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "图标加载失败")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("svg_icon") and loaded).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("png_icon") and loaded).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("missing_icon") and failed).fetchSemanticsNodes().isNotEmpty() }
        } finally { server.close(); worker.join(1000) }
    }

    @Test fun recentIconsPersistAndRemovalLeavesProfileDataUntouched() {
        assertPortraitDefaultFontConfiguration()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(context, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("图标历史不下载订阅")
        })
        try { runBlocking {
            val icon = "https://example.test/icon.svg"
            repository.recordGroupIcon(icon, false)
            repository.recordGroupIcon("https://example.test/other.png", false)
            repository.recordGroupIcon(icon, false)
            assertEquals(listOf(icon, "https://example.test/other.png"), repository.recentGroupIcons())
            // 作者 long：独立读取同一 Room 偏好，证明历史来自持久化记录，不只是页面内存。
            val row = database.appPreferences().get("group_icon_history")
            assertTrue(checkNotNull(row).contains("icon.svg"))
            repository.recordGroupIcon(icon, true)
            assertEquals(listOf("https://example.test/other.png"), repository.recentGroupIcons())
            repeat(30) { repository.recordGroupIcon("https://example.test/$it.svg", false) }
            assertEquals(24, repository.recentGroupIcons().size)
            assertTrue(database.profiles().getAll().isEmpty())
        } } finally { database.close() }
    }

    @Test fun lateFilterCheckCannotCommitAnEditedDraft() {
        assertPortraitDefaultFontConfiguration()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(894, "异步校验"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { saved = it },
                onValidateFilters = { values -> gate.await(); List(values.size) { "" } })
        } }
        compose.onNodeWithTag("proxy_group_filter").performClick()
        compose.onNodeWithTag("group_value_input").performTextInput("Ready")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.onNodeWithTag("proxy_group_name").performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("Changed")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("表单已更新，请再次确认").assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals("Changed", checkNotNull(saved).groups.first().name)
    }

    private fun capture(name: String) {
        compose.waitForIdle(); val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
