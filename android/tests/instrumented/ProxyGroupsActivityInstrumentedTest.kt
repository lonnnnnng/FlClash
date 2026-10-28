package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：代理选择需要在真实 Activity 生命周期中验证页面事件、ViewModel 状态和 Profile 映射一起收敛；
 * 这里仅替换页面依赖的运行时契约，不启动真实 Mihomo 或 VPN，避免把网络偶发性混入 UI 回归。
 */
@RunWith(AndroidJUnit4::class)
class ProxyGroupsActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProxyGroupsTestActivity>()

    @Test
    fun proxySelectionPersistsAndComputedGroupCanReturnToAutomatic() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_Auto").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.activity.viewModel.state.value.selectedMap == mapOf("Auto" to "node-b")
        }
        assertEquals("node-b", composeRule.activity.runtime.lastTarget)
        assertEquals(mapOf("Auto" to "node-b"), composeRule.activity.profileStore.selectedMap())

        composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.activity.viewModel.state.value.selectedMap.isEmpty()
        }
        assertEquals("", composeRule.activity.runtime.lastTarget)
        assertEquals(emptyMap<String, String>(), composeRule.activity.profileStore.selectedMap())
    }

    @Test
    fun proxyGroupsScreenshotMatrixMatchesBaseline() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_Auto").fetchSemanticsNodes().isNotEmpty()
        }

        fun captureHash(tag: String): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag(tag)
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "default_card" to "d2dd27e3d2b89b7f2aa41a3e05f27b1a837dd5c042c7e934017c7d35fab7f4ff",
            "dialog_initial" to "bb9ce7f5ed33e3145023546eb8bc12c3b329cee56d520c5dcfe47e70688dcf0b",
        )
        val actual = buildMap {
            put("default_card", captureHash("proxy_groups_card"))
            composeRule.onNodeWithTag("proxy_group_select_Auto").performClick()
            composeRule.onNodeWithTag("proxy_group_dialog_content_Auto").assertExists()
            put("dialog_initial", captureHash("proxy_group_dialog_content_Auto"))
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("proxy screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }
}
