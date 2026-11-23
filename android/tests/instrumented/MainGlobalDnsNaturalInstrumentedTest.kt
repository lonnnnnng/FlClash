package com.lonnnnnng.flclash.ui

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.AppTheme
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：DNS自然路线只允许空库隔离包；真实点击、IME和重建验收后恢复完整偏好键，不影响正式VPN。 */
class MainGlobalDnsNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val started = SystemClock.uptimeMillis()

    @Test fun dnsNonEmptyEditorsPersistAndRestoreWithoutReplay() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashDnsNaturalAcceptance"))
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        val entry = EntryPointAccessors.fromApplication(ins.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        repository.profilesLoaded.first { it }
        assertTrue(entry.database().profiles().getAll().isEmpty())
        assertNull(repository.currentProfileId.value)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val store = entry.globalDnsSettingsStore()
        val original = store.read()
        assertTrue(original.isEmpty)
        val dnsPrefs = ins.targetContext.getSharedPreferences("global_dns", android.content.Context.MODE_PRIVATE)
        val dnsKeys = dnsPrefs.all.toMap()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var appearance: com.lonnnnnng.flclash.data.AppearanceSettingsStore
            scenario.onActivity { appearance = it.appearanceSettings }
            val originalAppearance = appearance.read()
            val appearancePrefs = ins.targetContext.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
            val appearanceKeys = appearancePrefs.all.toMap()
            val light = InstrumentationRegistry.getArguments().getString("kclashVisualTheme") == "light"
            var failure: Throwable? = null
            try {
                appearance.write(originalAppearance.copy(theme = if (light) AppTheme.LIGHT else AppTheme.DARK, glass = false))
                click("工具"); click("高级配置"); click("DNS"); await("暂无覆写项"); settle()
                event("add-open"); click("添加覆写项"); settle()
                for ((key, label) in listOf("listen" to "监听地址", "ipv6" to "IPv6", "prefer-h3" to "Prefer H3", "enhanced-mode" to "DNS 模式", "nameserver" to "Nameserver")) {
                    setText(key); awaitIme(true); back(); awaitIme(false); settle(); clickRow(label)
                    awaitState { store.read().toJson().has(key) }; settle()
                }
                toolbar("添加覆写项", 0); parent("DNS"); capture("nonempty")
                event("listen-open"); click("监听地址"); settle(); capture("listen")
                setText("127.0.0.1:1053"); awaitIme(true); settle(); capture("listen_ime")
                back(); awaitIme(false); click("保存"); awaitState { store.read().listen == "127.0.0.1:1053" }; parent("DNS")
                click("DNS 模式"); await("fake-ip"); settle(); capture("mode"); click("fake-ip")
                awaitState { store.read().enhancedMode == "fake-ip" }; parent("DNS")
                click("IPv6"); awaitState { store.read().ipv6 == true }; settle()
                clickRow("Nameserver"); parent("Nameserver"); capture("list")
                event("list-menu"); toolbar("Nameserver", 0); await("快速编辑"); settle(); capture("list_menu")
                click("添加"); await("值"); settle(); capture("list_add")
                setText("9.9.9.9"); awaitIme(true); back(); awaitIme(false); click("确定"); parent("Nameserver")
                assertNotNull(find("9.9.9.9")); assertEquals(2, store.read().nameserver!!.size)
                scenario.recreate(); parent("Nameserver"); assertNotNull(find("9.9.9.9")); capture("list_draft_recreated")
                event("list-save"); toolbar("Nameserver", 2)
                awaitState { store.read().nameserver?.size == 3 }; parent("DNS"); capture("saved")
                scenario.recreate(); parent("DNS"); assertEquals(listOf("https://doh.pub/dns-query", "tls://223.5.5.5:853", "9.9.9.9"), store.read().nameserver)
                assertEquals("127.0.0.1:1053", store.read().listen); assertEquals("fake-ip", store.read().enhancedMode); assertEquals(true, store.read().ipv6)
                capture("saved_recreated"); back(); parent("高级配置")
                assertTrue(entry.database().profiles().getAll().isEmpty()); assertNull(repository.currentProfileId.value)
                assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
                event("verified:real-IME,draft-recreation,save-readback,empty-profiles,null-selection,STOPPED")
            } catch (caught: Throwable) { failure = caught; throw caught }
            finally { try {
                awaitState { manager.state.value == CoreRuntimeStatus.STOPPED }
                manager.updateGlobalDns(store.read(), original).getOrThrow()
                if ("overrides" !in dnsKeys) check(dnsPrefs.edit().remove("overrides").commit())
                assertEquals(dnsKeys, dnsPrefs.all); assertEquals(original, store.read())
                appearance.write(originalAppearance)
                val editor = appearancePrefs.edit()
                for (key in listOf("theme", "language", "glass")) if (key !in appearanceKeys) editor.remove(key)
                check(editor.commit()); assertEquals(appearanceKeys, appearancePrefs.all)
                event("restored:exact-DNS-and-appearance-preferences")
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup } }
        }
    }

    private fun event(name: String) = ins.sendStatus(2, Bundle().apply {
        putString("kclash_dns_natural_event", "$name;elapsedMs=${SystemClock.uptimeMillis() - started}")
    })
    private fun awaitState(test: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!test() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        check(test()) { "DNS异步状态超时" }
    }
    private fun parent(title: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var since = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            val active = root(); val all = active?.let { nodes(it).toList() }.orEmpty()
            val ready = active?.packageName?.toString() == ins.targetContext.packageName &&
                all.any { it.text?.toString() == title } && all.none { it.text?.toString() in setOf("添加规则", "编辑规则", "放弃未保存修改？") }
            if (ready) { if (since == 0L) since = SystemClock.uptimeMillis(); if (SystemClock.uptimeMillis() - since >= 600) return } else since = 0L
            SystemClock.sleep(100)
        }
        capture("parent_failure"); error("未回到稳定父页：$title")
    }
    private val density get() = ins.targetContext.resources.displayMetrics.density
    private fun root(): AccessibilityNodeInfo? { ins.uiAutomation.clearCache(); return ins.uiAutomation.rootInActiveWindow }
    private fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node); for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    // 作者 long：真实可点击字段将标题和摘要合并为多行语义；只匹配完整一行，不能用任意子串误中相邻配置。
    private fun find(text: String) = root()?.let { nodes(it).firstOrNull { n -> n.text?.toString()?.lines()?.any { it == text } == true } }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun await(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) { if (find(text) != null) return; SystemClock.sleep(100) }
        capture("failure")
        error("未找到DNS验收入口：$text")
    }
    private fun click(text: String) { await(text); settle(); tap(bounds(checkNotNull(find(text)))) }
    private fun clickRow(text: String) {
        await(text); settle()
        val matches = checkNotNull(root()).let { nodes(it).filter { n -> n.text?.toString()?.lines()?.contains(text) == true }.toList() }
        // 作者 long：Compose可能把Text与点击语义拆成父子节点，选择最后的字段标题再向上找可点击行；分组标题不作操作对象。
        var target: AccessibilityNodeInfo? = matches.lastOrNull()
        while (target != null && !target.isClickable) target = target.parent
        tap(bounds(checkNotNull(target)))
    }
    private fun toolbar(title: String, fromRight: Int) {
        await(title); settle()
        val y = bounds(checkNotNull(find(title))).centerY()
        val buttons = checkNotNull(root()).let { nodes(it).filter { n -> n.isClickable && bounds(n).let {
            y in it.top..it.bottom && it.width() in (24 * density).toInt()..(60 * density).toInt()
        } }.sortedByDescending { n -> bounds(n).centerX() }.toList() }
        tap(bounds(buttons[fromRight]))
    }
    private fun tap(rect: Rect, hold: Long = 60) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(hold)
        }
    }
    private fun setText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var candidate: AccessibilityNodeInfo? = null
        // 作者 long：真实点击只发起Dialog创建，焦点窗口和可编辑语义异步到达；不能在点击返回的同一帧读取旧父页。
        while (SystemClock.uptimeMillis() < deadline) {
            candidate = root()?.let { nodes(it).firstOrNull { n -> n.isEditable } }
            if (candidate != null) break
            SystemClock.sleep(100)
        }
        if (candidate == null) capture("input_failure")
        val node = checkNotNull(candidate)
        tap(bounds(node)); SystemClock.sleep(350)
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
    }
    private fun awaitIme(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var visible: Boolean? = null
            ins.runOnMainSync { visible = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                ?.let(ViewCompat::getRootWindowInsets)?.isVisible(WindowInsetsCompat.Type.ime()) }
            if (visible == expected) return
            SystemClock.sleep(100)
        }
        error("DNS窗口IME状态不符：$expected")
    }
    private fun back() = ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun settle() = SystemClock.sleep(900)
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
        val bitmap = checkNotNull(ins.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        recordVisualPng("vis095_${InstrumentationRegistry.getArguments().getString("kclashVisualTheme") ?: "dark"}_$name", output.toByteArray())
    }
}
