package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.LocalProxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：分别压缩无输入会话的语义操作窗口与平台InputConnection窗口，诊断不能靠输入后的等待变绿。 */
@SdkSuppress(minSdkVersion = 29)
class LocalProxyAlpnBoundaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<LocalProxyDraftTestActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val row = LocalProxy("alpn-boundary", """{"name":"ALPN边界","type":"trojan","server":"192.0.2.1","port":443,"password":"fixture-only","alpn":["h2"]}""")

    private fun start() {
        assertPortraitDefaultFontConfiguration()
        runBlocking { compose.activity.fixture.repository.saveLocalProxies(emptyList(), listOf(row)) }
        compose.waitUntil(5_000) { compose.activity.model.state.value.rows == listOf(row) }
        compose.runOnIdle { compose.activity.fixture.traceAlpn = true; compose.activity.model.open(); compose.activity.model.edit(row) }
        compose.onNodeWithText("ALPN", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("local_proxy_collection_add").performScrollTo().performClick()
    }

    @Test fun immediateSemanticInputAndFocusTransferPreserveAlpn() {
        start()
        val model = compose.activity.model
        val fixture = compose.activity.fixture
        try {
            repeat(8) { round ->
                val value = listOf("http/1.1", "h3", "", "h2")[round % 4]
                val second = compose.onNodeWithTag("local_proxy_collection_value_1").fetchSemanticsNode().config
                val first = compose.onNodeWithTag("local_proxy_collection_value_0").fetchSemanticsNode().config
                val focusSecond = checkNotNull(second[SemanticsActions.RequestFocus].action)
                val focusFirst = checkNotNull(first[SemanticsActions.RequestFocus].action)
                val replace = checkNotNull(second[SemanticsActions.SetText].action)
                var immediate: String? = null
                var blurred: String? = null
                // 作者 long：三步在同一个主线程任务内执行，不让重组或额外断言在输入与失焦之间改变时序。
                instrumentation.runOnMainSync {
                    focusSecond(); replace(AnnotatedString(value))
                    immediate = model.state.value.editor?.collection?.values?.get(1)?.second
                    focusFirst()
                    blurred = model.state.value.editor?.collection?.values?.get(1)?.second
                    fixture.recordAlpn("semantic-$round;immediate=${immediate?.length};blurred=${blurred?.length}")
                }
                compose.activityRule.scenario.recreate()
                assertEquals("语义输入即时值", value, immediate)
                assertEquals("同任务失焦值", value, blurred)
                assertEquals("重建后VM值", value, model.state.value.editor?.collection?.values?.get(1)?.second)
                assertEquals("重建后页面精确文本", value, compose.onNodeWithTag("local_proxy_collection_value_1").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            }
        } finally { report(fixture, "semantics") }
    }

    @Test fun platformInputConnectionCommitAndImmediateRecreationPreserveAlpn() {
        start()
        val model = compose.activity.model
        val fixture = compose.activity.fixture
        try {
            repeat(8) { round ->
                val value = listOf("http/1.1", "h3", "", "h2")[round % 4]
                compose.onNodeWithTag("local_proxy_collection_value_1").performClick()
                var committed = false
                var immediate: String? = null
                instrumentation.runOnMainSync {
                    val view = checkNotNull(WindowInspector.getGlobalWindowViews().last { it.hasWindowFocus() }.findFocus())
                    val connection = checkNotNull(view.onCreateInputConnection(EditorInfo())) { "平台输入连接未就绪" }
                    connection.beginBatchEdit()
                    connection.setSelection(0, model.state.value.editor!!.collection!!.values[1].second.length)
                    connection.setComposingText(value, 1)
                    committed = connection.commitText(value, 1)
                    connection.endBatchEdit()
                    immediate = model.state.value.editor?.collection?.values?.get(1)?.second
                    fixture.recordAlpn("connection-$round;committed=$committed;length=${immediate?.length}")
                }
                compose.activityRule.scenario.recreate()
                assertTrue("平台接受提交", committed)
                assertEquals("输入连接即时值", value, immediate)
                assertEquals("重建后VM值", value, model.state.value.editor?.collection?.values?.get(1)?.second)
                assertEquals("重建后页面精确文本", value, compose.onNodeWithTag("local_proxy_collection_value_1").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            }
        } finally { report(fixture, "input-connection") }
    }

    private fun report(fixture: LocalProxyDraftFixture, kind: String) {
        val events = mutableListOf<String>()
        instrumentation.runOnMainSync { events += fixture.alpnEvents; fixture.traceAlpn = false }
        events.forEachIndexed { index, event -> instrumentation.sendStatus(2, Bundle().apply {
            putString("kclash_alpn_boundary", "$kind;$index;$event")
        }) }
        assertEquals("诊断不写隔离目录正文", listOf(row), runBlocking { fixture.repository.localProxies() })
    }
}
