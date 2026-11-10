package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred

/** 作者 long：隔离表单草稿验证真实Sheet、触摸和图片解码；不写正式配置或启动网络核心。 */
class ProxyGroupSheetInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun source(count: Int = 3): String = JsonObject().apply {
        add("proxies", JsonArray().apply { repeat(count) { index -> add(JsonObject().apply {
            addProperty("name", "Node $index"); addProperty("type", "direct")
        }) } })
        add("proxy-groups", JsonArray().apply { add(JsonObject().apply {
            addProperty("name", "Main"); addProperty("type", "select")
            add("proxies", JsonArray().apply { repeat(count) { add("Node $it") } })
        }) })
    }.toString()

    @Test fun sheetDetentsInlineBadgesAndSystemBackKeepSelectedDraftAcrossRestoration() {
        assertPortraitDefaultFontConfiguration()
        val source = source()
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            VisualProxyGroupsEditor(Profile.custom(3101, "Sheet"), "", source, false, initialGroupIndex = 0,
                onDismiss = {}, onSave = {}, onSaveDraft = { writes++ })
        } }
        val viewport = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot.height
        assertTrue(compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.height in (viewport * .50f)..(viewport * .57f))
        capture("group_sheet_partial")
        compose.onNodeWithTag("connection_detail_handle").performClick()
        assertTrue(compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.height <= viewport * .9f + 2f)
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_0"))
        compose.onNodeWithTag("group_member_protocol_0").onChild().assertTextEquals("direct")
        compose.onNodeWithTag("group_member_handle_PROXY:Node 0").assertIsDisplayed()
        capture("group_sheet_expanded_badges")
        compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_search").performTextReplacement("REJECT-DROP")
        compose.onNodeWithTag("group_member_choice_false_REJECT-DROP").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("group_member_search").assertTextContains("REJECT-DROP")
        pressBack()
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_3"))
        compose.onNodeWithTag("group_member_label_3").assertTextEquals("REJECT-DROP")
        compose.onNodeWithTag("group_member_protocol_3").onChild().assertTextEquals("基础策略")
        compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_search").assertTextContains("")
        compose.onNodeWithTag("group_member_search").performTextReplacement("REJECT-DROP")
        compose.onNodeWithTag("group_member_empty").assertTextEquals("无搜索结果")
        // 作者 long：此处验重开会话后的草稿，IME位移中的触点另由自然流程覆盖，避免点击落在变化前的坐标。
        compose.onNodeWithTag("group_member_picker_back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("proxy_group_form_back").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("proxy_group_form_back").performClick()
        compose.onNodeWithText("放弃此策略组修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun inlineDragAutoScrollCancelAndDropPreserveDraftUntilExplicitSave() {
        assertPortraitDefaultFontConfiguration()
        var saved: ProxyGroupsSave? = null
        var writes = 0
        compose.setContent { KClashTheme {
            VisualProxyGroupsEditor(Profile.custom(3102, "Long"), "", source(36), false, initialGroupIndex = 0,
                onDismiss = {}, onSave = {}, onSaveDraft = { saved = it; writes++ })
        } }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        val list = compose.onNodeWithTag("group_form_list")
        list.performScrollToNode(hasTestTag("group_member_label_0"))
        var bounds = list.fetchSemanticsNode().boundsInRoot
        var row = compose.onNodeWithTag("group_member_row_PROXY:Node 0").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        list.performTouchInput { down(Offset(200f, row.center.y - bounds.top)); advanceEventTime(800); moveTo(Offset(200f, bounds.height - 12f), delayMillis = 200) }
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        val first = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > first)
        list.performTouchInput { cancel() }; compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, writes) }
        list.performScrollToNode(hasTestTag("group_member_label_0"))
        compose.onNodeWithTag("group_member_label_0").assertTextEquals("Node 0")
        bounds = list.fetchSemanticsNode().boundsInRoot
        row = compose.onNodeWithTag("group_member_row_PROXY:Node 0").fetchSemanticsNode().boundsInRoot
        list.performTouchInput { down(Offset(200f, row.center.y - bounds.top)); advanceEventTime(800); moveBy(Offset(0f, row.height * 1.2f), delayMillis = 300); up() }
        compose.onNodeWithTag("group_member_label_0").assertTextEquals("Node 1")
        compose.runOnIdle { assertEquals(0, writes) }
        capture("group_sheet_reordered")
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertEquals(listOf("Node 1", "Node 0"), checkNotNull(saved).groups.single().strings("proxies").take(2))
        }
    }

    @Test fun nonemptyGalleryDecodesImageAndReturnsToSameSheetDraft() {
        assertPortraitDefaultFontConfiguration()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val url = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val gallery = ResourceIconSet("loaded", "已加载图库", icons = listOf(ResourceIcon("Red", url)), updatedAt = 1)
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            CompositionLocalProvider(LocalResourceIconSets provides listOf(gallery)) {
                VisualProxyGroupsEditor(Profile.custom(3103, "Images"), "", source(), false, initialGroupIndex = 0,
                    onDismiss = {}, onSave = {}, onSaveDraft = { saved = it })
            }
        } }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        val expanded = compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("proxy_group_icon").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("group_icon_source_set:loaded").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("group_icon_source_set:loaded").performClick()
        val tile = compose.onNodeWithTag("group_icon_choice_0")
        compose.waitUntil(5000) {
            val image = tile.captureToImage().asAndroidBitmap()
            image.getPixel(image.width / 2, image.height / 2) == Color.RED
        }
        val viewport = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
        val short = compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot
        assertTrue("单图图库应适配短内容", short.height < expanded && short.height <= viewport.height * .57f)
        assertTrue("短图库应恢复浮动边距", short.left > viewport.left)
        capture("group_sheet_nonempty_gallery")
        tile.performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(url, checkNotNull(saved).groups.single().text("icon")) }
    }

    @Test fun encryptedRegistryRestoresSheetAndMemberDraftAfterSubtreeRecreation() {
        assertPortraitDefaultFontConfiguration()
        var mounted by mutableStateOf(true)
        var session by mutableIntStateOf(0)
        var restored: EditorSaveableState? = null
        var snapshot: EditorSaveableState? = null
        var checkpoint: (() -> Boolean)? = null
        var failures = 0
        compose.setContent { KClashTheme {
            if (mounted) EncryptedEditorState(session, restored, { snapshot = it }, { failures++ }) { save ->
                checkpoint = save
                VisualProxyGroupsEditor(Profile.custom(3104, "Encrypted"), "", source(), false, initialGroupIndex = 0,
                    onDismiss = {}, onSave = {})
            }
        } }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_search").performTextReplacement("REJECT-DROP")
        compose.onNodeWithTag("group_member_choice_false_REJECT-DROP").performClick()
        compose.runOnIdle {
            assertTrue(checkNotNull(checkpoint).invoke())
            restored = checkNotNull(snapshot)
            assertEquals(0, failures)
            mounted = false
        }
        compose.waitForIdle()
        compose.runOnIdle { session++; mounted = true }
        compose.onNodeWithTag("group_member_search").assertTextContains("REJECT-DROP")
        compose.onNodeWithTag("group_member_picker_back").performClick()
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_3"))
        compose.onNodeWithTag("group_member_label_3").assertTextEquals("REJECT-DROP")
        val viewport = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot.height
        assertTrue(compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.height <= viewport * .9f + 2f)
        compose.runOnIdle { assertEquals(0, failures) }
    }

    @Test fun discardDuringValidationRejectsLateSuccessBeforeExitAnimationEnds() {
        assertPortraitDefaultFontConfiguration()
        val gate = CompletableDeferred<List<String>>()
        val data = com.google.gson.JsonParser.parseString(source()).asJsonObject.apply {
            getAsJsonArray("proxy-groups")[0].asJsonObject.addProperty("filter", "Node")
        }.toString()
        var writes = 0
        var exits = 0
        compose.setContent { KClashTheme {
            VisualProxyGroupsEditor(Profile.custom(3105, "Pending"), "", data, false, initialGroupIndex = 0,
                onDismiss = { exits++ }, onSave = {}, onSaveDraft = { writes++ }, onValidateFilters = { gate.await() })
        } }
        compose.onNodeWithTag("proxy_group_name").performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("Changed")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_back").performClick()
        compose.onNodeWithText("放弃此策略组修改？").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { gate.complete(emptyList()) }
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, writes); assertEquals(1, exits) }
    }

    @Test fun repeatedMembersHaveIndependentRowsAndDeleteOnlyOneOccurrence() {
        assertPortraitDefaultFontConfiguration()
        val data = com.google.gson.JsonParser.parseString(source(2)).asJsonObject.apply {
            getAsJsonArray("proxy-groups")[0].asJsonObject.add("proxies", JsonArray().apply {
                add("Node 0"); add("Node 0"); add("Node 1")
            })
        }.toString()
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            VisualProxyGroupsEditor(Profile.custom(3106, "Repeated"), "", data, false, initialGroupIndex = 0,
                onDismiss = {}, onSave = {}, onSaveDraft = { saved = it })
        } }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_1"))
        compose.onNodeWithTag("group_member_label_0").assertTextEquals("Node 0")
        compose.onNodeWithTag("group_member_label_1").assertTextEquals("Node 0")
        compose.onNodeWithTag("group_member_remove_0").performClick()
        compose.onNodeWithTag("group_member_label_0").assertTextEquals("Node 0")
        compose.onNodeWithTag("group_member_label_1").assertTextEquals("Node 1")
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("Node 0", "Node 1"), checkNotNull(saved).groups.single().strings("proxies")) }
    }

    @Test fun galleryAndYamlMiddleScrollDoNotCollapseExpandedSheet() {
        assertPortraitDefaultFontConfiguration()
        val gallery = ResourceIconSet("long", "长图库", icons = (0 until 80).map { index ->
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(index, 80, 120)) }
            val data = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            ResourceIcon("Icon $index", "data:image/png;base64," + Base64.encodeToString(data, Base64.NO_WRAP))
        }, updatedAt = 1)
        compose.setContent { KClashTheme {
            CompositionLocalProvider(LocalResourceIconSets provides listOf(gallery)) {
                VisualProxyGroupsEditor(Profile.custom(3107, "Long children"), "", source(80), false, initialGroupIndex = 0,
                    onDismiss = {}, onSave = {})
            }
        } }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        val top = compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("proxy_group_icon").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("group_icon_grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("group_icon_grid").performScrollToIndex(4).performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(top, compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("group_icon_back").performClick()
        compose.onNodeWithTag("proxy_group_quick_yaml").performClick()
        val scroll = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("proxy_group_yaml_content")))
        // 作者 long：只用视口内的真实手势；越界的语义ScrollBy会把剩余位移交给Sheet，不能伪装成正文中部起手。
        val viewport = compose.onNodeWithTag("profile_code_viewport")
        viewport.performTouchInput { swipe(center, center - Offset(0f, 350f), durationMillis = 600) }
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        viewport.performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(top, compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("proxy_group_yaml_content").assertTextContains("Node 79", substring = true)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(450)
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
