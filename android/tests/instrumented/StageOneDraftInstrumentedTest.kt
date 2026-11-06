package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实宿主重建验证凭据草稿只留在同一ViewModel，隔离Room用于检查父保存前零落库。 */
class StageOneDraftInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<LocalProxyDraftTestActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    private fun start(row: LocalProxy) {
        runBlocking { compose.activity.fixture.repository.saveLocalProxies(emptyList(), listOf(row)) }
        compose.waitUntil(5_000) { compose.activity.model.state.value.rows == listOf(row) }
        compose.runOnIdle { compose.activity.model.open(); compose.activity.model.edit(row) }
    }

    @Test fun mapDraftSurvivesRealActivityRecreationAndCredentialsStayOutOfBundle() {
        val secret = "draft-credential-marker-20261105"
        val row = LocalProxy("map", """{"name":"Map","type":"http","server":"192.0.2.1","port":443,"password":"$secret","headers":{"Authorization":"initial"}}""")
        start(row)
        compose.onNodeWithText("请求头", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_value_0").performTextReplacement(secret)
        val old = compose.activity
        val model = old.model
        closeSoftKeyboard()
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertSame(model, compose.activity.model) }
        compose.onNodeWithTag("local_proxy_collection_value_0").assertTextContains(secret)
        // 作者 long：系统Parcel同时检查UTF-8和UTF-16表示，避免仅凭不使用SavedStateHandle就宣称未保存凭据。
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val needle = secret.toByteArray(charset)
            assertFalse("凭据不得进入Activity保存状态", old.savedBytes.asList().windowed(needle.size).any { it == needle.asList() })
        }
        assertTrue(old.savedBytes.isNotEmpty())
        assertFalse(model.state.value.editor.toString().contains(secret))
        assertFalse(model.state.value.editor?.collection.toString().contains(secret))
        compose.onNodeWithTag("local_proxy_collection_back").performClick()
        assertEquals(listOf(row), runBlocking { compose.activity.fixture.repository.localProxies() })
        assertEquals(secret, LocalProxies.value(checkNotNull(model.state.value.editor).value.fields, "headers.Authorization")?.asString)
        compose.onNodeWithTag("local_proxy_editor_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("请求头", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_add").performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_key_1").performScrollTo().performTextReplacement("authorization")
        compose.onNodeWithTag("local_proxy_collection_value_1").performScrollTo().performTextReplacement("duplicate")
        compose.onNodeWithTag("local_proxy_collection_confirm").performClick()
        compose.onNodeWithTag("local_proxy_collection_error").assertIsDisplayed().assertTextContains("重复", substring = true)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("local_proxy_collection_error").assertIsDisplayed()
        compose.onNodeWithTag("local_proxy_collection_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("local_proxy_collection_back").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("local_proxy_save").performClick()
        compose.waitUntil(5_000) { !model.state.value.busy && model.state.value.editor == null }
        val saved = runBlocking { compose.activity.fixture.repository.localProxies() }.single()
        assertEquals(secret, LocalProxies.value(saved.fields, "headers.Authorization")?.asString)
        assertEquals(1, saved.fields["headers"].asJsonObject.size())
    }

    @Test fun listDraftReturnsToParentCanRemoveLastValueAndRejectsOldSession() {
        val row = LocalProxy("list", """{"name":"List","type":"trojan","server":"192.0.2.1","port":443,"password":"test-only","alpn":["h2"]}""")
        start(row)
        val model = compose.activity.model
        val old = checkNotNull(model.state.value.editor)
        compose.onNodeWithText("ALPN", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_add").performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_value_1").performTextReplacement("http/1.1")
        // 作者 long：不在输入和重建之间额外等待，连续覆盖带IME的立即重建窗口，避免断言自身掩盖时序问题。
        repeat(8) { round ->
            val value = if (round % 2 == 0 && round > 0) "h3" else "http/1.1"
            if (round > 0) compose.onNodeWithTag("local_proxy_collection_value_1").performTextReplacement(value)
            compose.activityRule.scenario.recreate()
            assertEquals("立即重建第${round + 1}轮VM列表值", value, model.state.value.editor?.collection?.values?.get(1)?.second)
            compose.onNodeWithTag("local_proxy_collection_value_1").assertTextContains(value)
        }
        compose.onNodeWithTag("local_proxy_collection_value_1").assertTextContains("http/1.1")
        compose.onNodeWithTag("local_proxy_collection_back").performClick()
        assertEquals(listOf("h2", "http/1.1"), LocalProxies.value(checkNotNull(model.state.value.editor).value.fields, "alpn")?.asJsonArray?.map { it.asString })
        assertEquals(listOf(row), runBlocking { compose.activity.fixture.repository.localProxies() })
        compose.onNodeWithText("ALPN", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_remove_1").performClick()
        compose.onNodeWithTag("local_proxy_collection_remove_0").performClick()
        compose.onNodeWithTag("local_proxy_collection_confirm").performClick()
        assertNull(LocalProxies.value(checkNotNull(model.state.value.editor).value.fields, "alpn"))
        compose.runOnIdle { model.dismissEditor(); model.edit(row); model.updateEditor(old) }
        assertNotEquals(old.session, model.state.value.editor?.session)
        assertEquals(row, model.state.value.editor?.value)
    }
}
