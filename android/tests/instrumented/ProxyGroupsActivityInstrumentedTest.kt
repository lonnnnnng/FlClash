package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
