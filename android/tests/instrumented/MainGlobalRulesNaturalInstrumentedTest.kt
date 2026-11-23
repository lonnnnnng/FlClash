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
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ExtensionRules
import androidx.room.withTransaction
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：规则专项用真实触摸与系统帧覆盖窗口交接；只允许空配置独立包，保存不得投影到正在使用的VPN。 */
class MainGlobalRulesNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val started = SystemClock.uptimeMillis()

    @Test fun rulesFormImeDiscardSaveAndDnsSelectorUseNaturalFrames() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGlobalRulesNaturalAcceptance"))
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val entry = EntryPointAccessors.fromApplication(ins.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
            val repository = entry.profileRepository()
            repository.profilesLoaded.first { it }
            assertTrue(entry.database().profiles().getAll().isEmpty())
            assertNull(repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            click("工具"); click("高级配置"); settle()
            // 作者 long：首页初始化可能落质量缓存，进入静态工具页后等偏好稳定再建立完整行保护快照。
            var previous = entry.database().appPreferences().getByPrefix("")
            var since = SystemClock.uptimeMillis()
            val init = since
            while (SystemClock.uptimeMillis() - init < 20_000) {
                SystemClock.sleep(250)
                val now = entry.database().appPreferences().getByPrefix("")
                if (now != previous) { previous = now; since = SystemClock.uptimeMillis() }
                if (SystemClock.uptimeMillis() - init >= 5_000 && SystemClock.uptimeMillis() - since >= 2_000) break
            }
            val preferences = entry.database().appPreferences().getByPrefix("").sortedBy { it.key }
            val original = preferences.firstOrNull { it.key == ExtensionRules.PREFERENCE_KEY }
            val scripts = entry.database().scripts().getAll()
            assertTrue(repository.globalExtensionRules().isEmpty())
            lateinit var controller: GlobalRulesController
            scenario.onActivity { controller = ViewModelProvider(it)[ProfilesViewModel::class.java].globalRules }
            var failure: Throwable? = null
            try {
                event("route-open"); click("附加规则"); awaitState { controller.state.value.let { it.ready && it.loaded && !it.loading } }
                parent("附加规则"); capture("empty")
                event("add-open"); toolbar("附加规则", 0); await("添加规则"); settle(); capture("form")
                event("ime-open"); setText("discarded.invalid"); awaitIme(true); settle()
                // 作者 long：短规则表单应保留父页可见区域，键盘不能把三行内容错误铺成整页；旧实现标题会升到屏幕顶部。
                assertTrue(bounds(checkNotNull(find("添加规则"))).top > ins.targetContext.resources.displayMetrics.heightPixels / 4)
                capture("ime")
                event("ime-back"); back(); awaitIme(false); settle(); capture("ime_closed")
                event("form-back"); back(); await("放弃未保存修改？"); settle(); capture("discard")
                click("继续编辑"); await("添加规则"); settle(); assertNotNull(find("discarded.invalid"))
                formAction(false); click("放弃"); parent("附加规则"); capture("cancelled")
                event("add-reopen"); toolbar("附加规则", 0); await("添加规则"); settle()
                val editor = checkNotNull(root()?.let { nodes(it).firstOrNull { n -> n.isEditable } })
                assertTrue(editor.text.isNullOrEmpty() || editor.isShowingHintText)
                setText("route-acceptance.invalid"); awaitIme(true); back(); awaitIme(false); settle()
                event("save-close"); formAction(true); parent("附加规则")
                awaitState { !controller.state.value.saving && !controller.state.value.applying }
                val saved = repository.globalExtensionRules()
                assertEquals(listOf("DOMAIN,route-acceptance.invalid,DIRECT"), saved.map { it.value })
                assertNotNull(find("route-acceptance.invalid")); capture("saved")
                val savedRow = bounds(checkNotNull(find("route-acceptance.invalid")))
                val toggle = checkNotNull(root()?.let { nodes(it).filter { n -> n.isCheckable && bounds(n).centerY() in savedRow.top - (32 * density).toInt()..savedRow.bottom }.maxByOrNull { n -> bounds(n).centerX() } })
                tap(bounds(toggle)); await("已选 1 项"); settle(); capture("selected")
                // 作者 long：批选工具栏会把规则行向下推，第二次必须重取当前按钮，不能复用入场前坐标。
                val checkedToggle = checkNotNull(root()?.let { nodes(it).filter { n -> n.isCheckable }.toList().singleOrNull() })
                tap(bounds(checkedToggle)); parent("附加规则"); assertNull(find("已选 1 项"))
                event("edit-open"); click("route-acceptance.invalid"); await("编辑规则"); settle(); capture("edit")
                event("edit-cancel"); formAction(false); parent("附加规则")
                scenario.recreate(); parent("附加规则"); assertEquals(saved, repository.globalExtensionRules()); capture("recreated")
                event("route-back"); back(); parent("高级配置"); awaitState { controller.state.value.draft == null }
                event("dns-open"); click("DNS"); await("尚未添加 DNS 覆盖"); settle(); capture("dns_empty")
                click("添加字段"); await("添加 DNS 字段"); settle(); capture("dns_selector")
                val selectorTop = bounds(checkNotNull(find("添加 DNS 字段"))).top
                event("dns-scroll"); swipeUp(); settle()
                assertTrue(bounds(checkNotNull(find("添加 DNS 字段"))).top < selectorTop - 24 * density)
                capture("dns_scrolled")
                // 作者 long：只搜索和滚动DNS可选项，不保存设置，避免视觉验收触发运行中核心应用。
                val search = checkNotNull(root()?.let { nodes(it).firstOrNull { n -> n.isEditable } })
                tap(bounds(search)); awaitIme(true)
                check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "fake")
                }))
                settle()
                assertEquals("fake", checkNotNull(root()?.let { nodes(it).firstOrNull { n -> n.isEditable } }).text.toString())
                assertNotNull(find("Fake-IP 过滤")); assertNotNull(find("Fake-IP 过滤模式"))
                assertNull(find("监听地址")); assertNull(find("Nameserver"))
                capture("dns_search"); back(); awaitIme(false); back(); await("尚未添加 DNS 覆盖"); settle(); capture("dns_return")
                back(); parent("高级配置"); event("finished")
            } catch (caught: Throwable) { failure = caught; throw caught }
            finally { try {
                // 作者 long：若保存仍卡在运行中，恢复偏好会与其竞争并被迟到写入覆盖；清理失败必须让本例失败，由外部回收独立包，不能强写后宣称保持。
                awaitState { controller.state.value.ready && !controller.state.value.loading && !controller.state.value.saving && !controller.state.value.applying }
                if (controller.state.value.draft != null) {
                    ins.runOnMainSync { controller.close {} }; awaitState { controller.state.value.draft == null }
                }
                entry.database().withTransaction {
                    if (original == null) entry.database().appPreferences().delete(ExtensionRules.PREFERENCE_KEY)
                    else entry.database().appPreferences().put(original)
                }
                assertNull(entry.globalRulesStore().read())
                assertTrue(entry.database().profiles().getAll().isEmpty()); assertNull(repository.currentProfileId.value)
                assertEquals(scripts, entry.database().scripts().getAll())
                assertEquals(preferences, entry.database().appPreferences().getByPrefix("").sortedBy { it.key })
                assertTrue(entry.database().appPreferences().getByPrefix("extension_commit/").isEmpty())
                for (suffix in listOf("", ".bak", ".new")) assertFalse(File(ins.targetContext.noBackupFilesDir, "global-rules.v1$suffix").exists())
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                event("preserved:preferences-full-rows,scripts,empty-profiles,null-selection,no-draft,no-receipt,STOPPED")
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup } }
        }
    }

    private fun swipeUp() {
        val metrics = ins.targetContext.resources.displayMetrics
        val start = SystemClock.uptimeMillis()
        for (i in 0..20) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(),
                when (i) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE },
                metrics.widthPixels * .55f, metrics.heightPixels * (.83f - .23f * i / 20f), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(20)
        }
    }

    private fun event(name: String) = ins.sendStatus(2, Bundle().apply {
        putString("kclash_rules_natural_event", "$name;elapsedMs=${SystemClock.uptimeMillis() - started}")
    })
    private fun awaitState(test: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!test() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        check(test()) { "规则异步状态超时" }
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
    private fun formAction(save: Boolean) {
        val title = find("添加规则") ?: find("编辑规则") ?: error("表单标题未出现")
        val y = bounds(title).centerY()
        val candidates = checkNotNull(root()).let { nodes(it).filter { n -> n.isClickable && bounds(n).let { y in it.top..it.bottom && it.width() in (24 * density).toInt()..(60 * density).toInt() } }.toList() }
        val target = if (save) candidates.maxByOrNull { bounds(it).centerX() } else candidates.minByOrNull { bounds(it).centerX() }
        tap(bounds(checkNotNull(target))); settle()
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
        error("未找到规则验收入口：$text")
    }
    private fun click(text: String) { await(text); settle(); tap(bounds(checkNotNull(find(text)))) }
    private fun toolbar(title: String, fromRight: Int) {
        await(title); settle(); val rect = bounds(checkNotNull(find(title))); val x = ins.targetContext.resources.displayMetrics.widthPixels - (24 + fromRight * 48) * density
        tap(Rect(x.toInt() - 8, rect.centerY() - 8, x.toInt() + 8, rect.centerY() + 8))
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
        error("规则窗口IME状态不符：$expected")
    }
    private fun back() = ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun settle() = SystemClock.sleep(900)
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
        val bitmap = checkNotNull(ins.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        recordVisualPng("vis093_main_$name", output.toByteArray())
    }
}
