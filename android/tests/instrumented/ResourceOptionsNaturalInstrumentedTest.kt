package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：不使用ComposeTestRule，开合由Choreographer自然推进；只渲染原面板，外部录像与性能采样分别进行。 */
class ResourceOptionsNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    @Test fun lightAndDarkOptionsUseNaturalEnterImeCancelAndSuccessfulExit() {
        assertPortraitDefaultFontConfiguration()
        assertTrue(ins.targetContext.packageName.endsWith(".motion"))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var open by mutableStateOf("");var dark by mutableStateOf(false)
            var busy by mutableStateOf(false);var closing by mutableStateOf(false);var saves=0;var submitted=""
            fun close() { open="";closing=false;busy=false }
            scenario.onActivity { activity -> activity.setContent { KClashTheme(darkTheme=dark) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    for (name in listOf("Script","Import")) TextButton({open=name}) { Text(name) }
                }
                LaunchedEffect(busy) { if(busy) { delay(600);busy=false;closing=true } }
                when(open) {
                    "Import" -> ScriptRemoteDialog(busy,::close,{_,value->submitted=value;saves++;busy=true},
                        initialUrl="https://example.invalid/scripts/import.js",closing=closing,onClosed={if(closing) close()})
                    "Script" -> ScriptRemoteDialog(busy,::close,{_,value->submitted=value;saves++;busy=true},
                        Script(73,"远程脚本","https://example.invalid/scripts/long-script.js"),closing=closing,onClosed={if(closing) close()})
                }
            } } }
            settle()
            for(theme in listOf(false,true)) {
                scenario.onActivity { dark=theme }
                for(name in listOf("Script","Import")) {
                    mark("${if(theme) "dark" else "light"}-$name-open")
                    click(name);settle()
                    val field=awaitNode{it.isEditable}
                    clickNode(field);settle()
                    assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"https://example.invalid/edited-resource.json")
                    }))
                    mark("$name-ime");settle();back();settle();back();settle()
                    awaitNode { it.text?.toString()==name }
                    scenario.onActivity { assertEquals("",open) }
                    mark("$name-reopen-save");click(name);settle()
                    click(if(name=="Import") "导入" else "保存")
                    settle(1400)
                    scenario.onActivity { assertEquals("",open) };mark("$name-closed")
                }
            }
            scenario.onActivity { assertEquals(4,saves);assertTrue(submitted.startsWith("https://")) }
        }
    }
    private fun allNodes(): List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(n:AccessibilityNodeInfo) { result+=n;repeat(n.childCount){n.getChild(it)?.let(::visit)} }
        ins.uiAutomation.rootInActiveWindow?.let(::visit);return result
    }
    private fun awaitNode(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
        val limit=SystemClock.uptimeMillis()+5000
        do { allNodes().firstOrNull(predicate)?.let{return it};SystemClock.sleep(60) } while(SystemClock.uptimeMillis()<limit)
        error("目标面板控件未出现")
    }
    private fun click(label:String)=clickNode(awaitNode{it.text?.toString()==label})
    private fun clickNode(node:AccessibilityNodeInfo) {
        // 作者 long：文字节点可能没有ACTION_CLICK，按实际可见边界注入触点才能复现用户的点击与窗口交接。
        val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds);assertFalse(bounds.isEmpty)
        val start=SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(ins.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
    }
    private fun back()=ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun settle(ms:Long=700)=SystemClock.sleep(ms)
    private fun mark(name:String)=ins.sendStatus(2,Bundle().apply{putString("vis073_natural_phase",name);putLong("uptimeMs",SystemClock.uptimeMillis())})
}
