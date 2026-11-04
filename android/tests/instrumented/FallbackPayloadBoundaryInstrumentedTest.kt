package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：验证真实Bridge解码后的防御边界，注入的是协议响应而非配置；不声称Mihomo自然产生空now。 */
class FallbackPayloadBoundaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun missingInvalidAndEmptyNowPassThroughBridgeViewModelAndBothPageLayouts() {
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity
        val options = ProxyPageOptionsStore.load(context)
        ProxyPageOptionsStore.save(context, ProxyPageOptions())
        val bridge = CoreBridge()
        // 作者 long：解析器属于core模块internal；仅在测试中反射调用现有实现，避免为故障注入扩大生产API或接管真实核心。
        val decode = CoreBridge::class.java.declaredMethods.single {
            it.name.startsWith("parseProxyGroupsResponse") && it.parameterTypes.contentEquals(arrayOf(JSONObject::class.java))
        }.apply { isAccessible = true }
        fun snapshot(now: String?, fixed: String?): ProxyGroupsSnapshot {
            val group = JSONObject().put("name", "Fallback边界").put("type", "Fallback")
                .put("all", org.json.JSONArray(listOf("node-a", "node-b")))
            if (now != null) group.put("now", now)
            if (fixed != null) group.put("fixed", fixed)
            return decode.invoke(bridge, JSONObject().put("all", org.json.JSONArray(listOf("Fallback边界")))
                .put("proxies", JSONObject().put("Fallback边界", group))) as ProxyGroupsSnapshot
        }
        var payload = snapshot(null, "node-b")
        val runtime = object : ProxyGroupsRuntime by ActivityProxyGroupsRuntime() {
            override suspend fun getProxyGroups() = Result.success(payload)
            // 作者 long：正式VM携Profile ID调用读取，必须覆盖此入口；接口委托的默认重载会绕回旧夹具。
            override suspend fun getProxyGroups(profileId: Long) = Result.success(payload)
        }
        val store = ActivityProxyProfileStore()
        lateinit var model: ProxyGroupsViewModel
        var dark by mutableStateOf(false)
        compose.runOnUiThread { model = ProxyGroupsViewModel(runtime, store) }
        try {
            compose.setContent { KClashTheme(darkTheme = dark) {
                val state by model.state.collectAsState()
                ProxyGroupsCard(state = state.copy(unfoldSet = setOf("Fallback边界")), runtimeState = CoreRuntimeStatus.STOPPED,
                    currentProfileId = 501, currentGroupName = "Fallback边界", onRefresh = model::refresh,
                    onSelect = model::selectProxy, onTestDelay = model::testDelay, onTestGroupDelay = model::testDelayGroup)
            } }
            data class Case(val now: String?, val pin: String?, val fixed: String?, val expected: String?)
            val cases = listOf(Case(null, "node-a", "node-b", "node-a"), Case("gone", "gone", "node-b", "node-b"),
                Case("", null, "node-a", "node-a"), Case(null, "gone", "gone", null), Case("node-b", "node-a", "node-a", "node-b"))
            for (layout in listOf("TAB", "LIST")) {
                if (layout == "LIST") {
                    compose.onNodeWithTag("proxy_tools").performClick()
                    compose.onNodeWithTag("proxy_tools_display_mode").performClick()
                    compose.onNodeWithTag("proxy_display_mode_LIST").performClick()
                }
                for (theme in listOf(false, true)) {
                    compose.runOnIdle { dark = theme }
                    cases.forEachIndexed { index, case ->
                        payload = snapshot(case.now, case.fixed)
                        runBlocking { store.updateProfileSelectedMap(501, case.pin?.let { mapOf("Fallback边界" to it) }.orEmpty()) }
                        compose.runOnIdle { model.refresh() }
                        compose.waitUntil(5_000) { !model.state.value.isLoading && model.state.value.groups == payload.groups &&
                            model.state.value.selectedMap == store.selectedMap() }
                        assertEquals(case.expected, selectedProxyName(model.state.value.groups.single(), model.state.value.selectedMap))
                        for (name in listOf("node-a", "node-b")) {
                            val tag = if (layout == "TAB") "proxy_node_" else "proxy_list_node_"
                            val node = compose.onNodeWithTag("${tag}Fallback边界_$name").assertIsDisplayed()
                            if (case.expected == name) node.assertProxyInUse(compose) else node.assertProxyNotInUse(compose)
                        }
                        if (index == 3) {
                            val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                            val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                            recordVisualPng("fallback_boundary_${layout}_${theme}", bytes)
                        }
                    }
                }
            }
        } finally {
            compose.runOnUiThread { model.viewModelScope.cancel() }
            ProxyPageOptionsStore.save(context, options)
        }
    }
}
