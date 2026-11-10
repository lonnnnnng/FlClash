package com.lonnnnnng.flclash.ui

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：故意不创建ComposeTestRule，帧由Android Choreographer自然推进；注入真实触点并让外部screenrecord独立记录连续过程。 */
class VisualMotionNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private var suffix = ""
    private val profileFrames get() = InstrumentationRegistry.getArguments().getString("kclashMotionProfile") == "true"
    private var segment = 0
    private var methodTrace: java.io.File? = null
    @Test fun lightPagesPanelsEmptyAndContinuousMotion() = run(false)
    @Test fun darkPagesPanelsEmptyAndContinuousMotion() = run(true)
    @Test fun lightMenuFirstOpenAndRepeatedTopCard() = runMenus(false)
    @Test fun darkMenuFirstOpenAndRepeatedTopCard() = runMenus(true)
    @Test fun lightMenuFirstOpenAndCloseFrameTimeline() = runMenuBoundaryDiagnostic(false)
    @Test fun darkMenuFirstOpenAndCloseFrameTimeline() = runMenuBoundaryDiagnostic(true)
    @Test fun lightDockedSearchKeepsSheetAcrossImeAndCancel() = runDockedSearch(false)
    @Test fun darkDockedSearchKeepsSheetAcrossImeAndCancel() = runDockedSearch(true)

    private fun runDockedSearch(dark: Boolean) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalMotion"))
        suffix = if (dark) "dark" else "light"
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.dark = dark; it.moreSection = "sniffer"; it.showMoreEditor = true }
            click("添加设置项"); await("完成"); settle(); capture("dock_idle")
            fun field() = checkNotNull(root()?.let { nodes(it).firstOrNull { node -> node.isEditable } })
            fun text(value: String) {
                assertTrue(field().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }))
            }
            fun cancel() {
                val editBounds = bounds(field())
                // 作者 long：Dialog不继承宿主测试ID；从实际输入框同行的最右可点击节点定位取消，避免固定屏幕坐标受IME位移影响。
                val button = checkNotNull(root()?.let { nodes(it).filter { node ->
                    val rect = bounds(node)
                    node.isClickable && node.isEnabled && rect.left > editBounds.right &&
                        rect.top <= editBounds.centerY() && rect.bottom >= editBounds.centerY()
                }.maxByOrNull { node -> bounds(node).right } })
                tap(bounds(button))
            }
            event("dock-focus"); tap(bounds(field())); settle()
            text("HTTP"); await("HTTP"); settle(); capture("dock_ime")
            var imeShown = false
            ins.runOnMainSync {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                imeShown = view?.let(androidx.core.view.ViewCompat::getRootWindowInsets)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
            assertTrue("自然触点必须实际唤起IME", imeShown)
            event("dock-cancel"); cancel(); settle(); await("完成")
            assertTrue(field().text.isNullOrEmpty())
            scenario.onActivity { assertEquals(0, it.moreSaveRequests); assertTrue(it.showMoreEditor) }
            capture("dock_cancel")
            event("dock-reopen-select"); tap(bounds(field())); text("HTTP"); await("HTTP"); settle()
            click("HTTP"); await("无搜索结果"); settle()
            scenario.onActivity { assertEquals(1, it.moreSaveRequests) }
            cancel(); settle(); click("完成"); awaitGone("完成"); await("HTTP"); settle()
            capture("dock_parent")
            scenario.onActivity { assertEquals(1, it.moreSaveRequests) }
            event("dock-completed")
            if (!profileFrames) {
                val run = checkNotNull(InstrumentationRegistry.getArguments().getString("kclashMotionRun"))
                require(run.matches(Regex("[a-z0-9-]+")))
                saveFrames("visual-motion-$run-$suffix.gfx")
            }
        }
    }

    @Test fun moreSelectionEntriesUseNaturalContinuousExit() {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalMotion"))
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.showMoreEditor = true }
            // 作者 long：正式编辑器是独立Dialog，外层宿主的testTagsAsResourceId不会跨Window；按当前窗口可见文案定位真实触点。
            await("添加设置项"); click("添加设置项"); await("服务器"); settle()
            ins.sendStatus(2, Bundle().apply { putString("kclash_selection_motion", "server-dispatch:${System.nanoTime()}") })
            click("服务器"); awaitGone("服务器"); settle()
            click("端口"); awaitGone("端口"); settle()
            assertNotNull(find("同步间隔（分钟）"))
            click("完成"); awaitGone("完成")
            await("服务器"); await("端口")
        }
    }
    @Test fun darkMenuCloseFramesBeforeWindowRemoval() = kotlinx.coroutines.runBlocking {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashCloseWindowCapture"))
        assertEquals("true", args.getString("kclashMotionInlineFrames"))
        assertNotEquals("true", args.getString("kclashCapturePng"))
        val run = checkNotNull(args.getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        val old = ProxyPageOptionsStore.load(ins.targetContext)
        try {
            ProxyPageOptionsStore.save(ins.targetContext, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark = true }
                click("nav_proxy"); await("proxy_tools"); settle()
                val source = bounds(checkNotNull(find("proxy_tools")))
                repeat(3) { pass ->
                    tap(source); await("样式：标签"); settle()
                    shell("dumpsys gfxinfo ${ins.targetContext.packageName} reset")
                    val gate = MenuCloseCaptureProbe.arm()
                    val dispatched = System.nanoTime()
                    back()
                    val completed = kotlinx.coroutines.withTimeout(5_000) { gate.completed.await() }
                    // 作者 long：动画结束后才执行dumpsys；留窗只改变收尾寿命，不在待测500ms内查询UI或采图。
                    SystemClock.sleep(100)
                    saveFrames("visual-motion-$run-dark-close-$pass.gfx")
                    ins.sendStatus(2, Bundle().apply { putString("kclash_close_capture", JSONObject()
                        .put("pass", pass).put("dispatchNs", dispatched).put("animationCompletedNs", completed)
                        .put("capturedBeforeRemoval", true).toString()) })
                    gate.release(); awaitGone("样式：标签"); settle()
                }
            }
        } finally { MenuCloseCaptureProbe.reset(); ProxyPageOptionsStore.save(ins.targetContext, old) }
    }

    @Test fun darkMenuCloseOwnUidNativeProbe() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashNativeProbe"))
        assertNotEquals("true", args.getString("kclashCapturePng"))
        val run = checkNotNull(args.getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        val old = ProxyPageOptionsStore.load(ins.targetContext)
        try {
            ProxyPageOptionsStore.save(ins.targetContext, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark = true }
                click("nav_proxy"); await("proxy_tools"); settle()
                var targetPid = -1
                scenario.onActivity { targetPid = android.os.Process.myPid() }
                click("proxy_tools"); await("样式：标签"); settle()
                MenuNativeProbe().record(run, targetPid) { back(); SystemClock.sleep(750) }
                // 作者 long：权限失败也必须正常关闭现场；失败原文和状态由探针独立报告，不能冒充采样成功。
                if (find("样式：标签") != null) { back(); settle() }
                awaitGone("样式：标签")
            }
        } finally { ProxyPageOptionsStore.save(ins.targetContext, old) }
    }
    @Test fun darkMenuOpenAndCloseThreadCpuSamples() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMotionThreadCpu"))
        assertNotEquals("true", args.getString("kclashCapturePng"))
        assertNotEquals("true", args.getString("kclashMotionMethodTrace"))
        val run = checkNotNull(args.getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        val context = ins.targetContext
        val old = ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark = true }
                click("nav_proxy"); await("proxy_tools"); settle()
                val source = bounds(checkNotNull(find("proxy_tools")))
                val probe = MenuThreadCpuProbe()
                repeat(3) { pass ->
                    probe.sample("$run-$pass-open") { tap(source) }
                    await("样式：标签"); settle()
                    probe.sample("$run-$pass-close") { back() }
                    awaitGone("样式：标签"); settle()
                }
                scenario.onActivity { assertEquals("常用节点", it.selectedGroup); assertEquals(0, it.nodeSelections) }
            }
        } finally { ProxyPageOptionsStore.save(context, old) }
    }
    @Test fun darkMenuOpenAndCloseMethodSamples() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMotionMethodTrace"))
        assertEquals("true", args.getString("kclashMotionInlineTrace"))
        assertNotEquals("true", args.getString("kclashCapturePng"))
        val run = checkNotNull(args.getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        suffix = "dark"
        val context = ins.targetContext
        val old = ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark = true }
                click("nav_proxy"); await("proxy_tools"); settle()
                val source = bounds(checkNotNull(find("proxy_tools")))
                // 作者 long：采样窗口内只注入已定位触点/Back并等待动效，不让查找节点、截图或gfxinfo混入热点。
                repeat(3) { pass ->
                    sampleMenuMethods("$run-$pass-open") { tap(source) }
                    await("样式：标签")
                    // 作者 long：采样会拖慢首开，窗口外等动效结束后再测关闭，避免把中断入场误当稳定回源。
                    settle()
                    sampleMenuMethods("$run-$pass-close") { back() }
                    awaitGone("样式：标签")
                    settle()
                }
                scenario.onActivity { assertEquals("常用节点", it.selectedGroup); assertEquals(0, it.nodeSelections) }
            }
        } finally { stopMethodTrace(); ProxyPageOptionsStore.save(context, old) }
    }

    private fun sampleMenuMethods(name: String, action: () -> Unit) {
        val file = java.io.File(ins.targetContext.cacheDir, "motion-method-$name.trace")
        check(!file.exists()); methodTrace = file
        android.os.Debug.startMethodTracingSampling(file.absolutePath, 32 * 1024 * 1024, 1_000)
        try { action(); SystemClock.sleep(700) } finally { stopMethodTrace() }
    }

    @Test fun removeExactOwnInterruptedFrameArtifacts() {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMotionCleanup"))
        val run = checkNotNull(args.getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        val manifest = org.json.JSONArray(checkNotNull(args.getString("kclashMotionCleanupManifest")))
        val cache = ins.targetContext.cacheDir
        // 作者 long：部分系统拒绝run-as；仅应用UID能回收中断采集文件，先逐个核对全部原始SHA，再删除，不能清整个缓存。
        val files = (0 until manifest.length()).map { index ->
            val item = manifest.getJSONObject(index)
            val name = item.getString("name")
            require(name.matches(Regex("visual-motion-${Regex.escape(run)}-(light|dark)\\.gfx")))
            val expected = item.getString("sha256")
            require(expected.matches(Regex("[0-9a-f]{64}")))
            java.io.File(cache, name).also { file ->
                val bytes = file.readBytes()
                assertEquals(item.getLong("bytes"), bytes.size.toLong())
                assertEquals(expected, java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            }
        }
        require(files.distinct().size == files.size)
        files.forEach { file -> assertTrue(file.delete()); assertFalse(file.exists()) }
        val remaining = cache.listFiles().orEmpty().filter { it.name.startsWith("visual-motion-") || it.name.startsWith("motion-method-") }
        assertTrue("验收帧文件仍未精确回收：${remaining.map { it.name }}", remaining.isEmpty())
        ins.sendStatus(2, Bundle().apply { putString("kclash_motion_cleanup", JSONObject().put("removed", files.size).put("remaining", remaining.size).toString()) })
    }

    private fun runMenus(dark: Boolean) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalMotion"))
        suffix = if (dark) "dark" else "light"
        val context=ins.targetContext
        val old=ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context,ProxyPageOptions(displayMode=ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark=dark }
                await("nav_proxy");click("nav_proxy");await("proxy_tools");settle()
                event("menu-ready")
                // 作者 long：首次菜单与同进程重复打开分开记，循环期间不访问其他面板来预热阴影或文字缓存。
                repeat(3) { pass ->
                    event("menu-$pass-open-start");click("proxy_tools");await("样式：标签");settle();capture("menu_${pass}_open")
                    click("样式：标签");await("列表");settle();capture("menu_${pass}_top")
                    back();settle();await("样式：标签");capture("menu_${pass}_fold")
                    back();settle();assertNull(find("样式：标签"));capture("menu_${pass}_closed")
                }
                scenario.onActivity { assertEquals("常用节点",it.selectedGroup);assertEquals(0,it.nodeSelections) }
                event("menu-completed")
                if (!profileFrames) {
                    val run=checkNotNull(InstrumentationRegistry.getArguments().getString("kclashMotionRun"))
                    require(run.matches(Regex("[a-z0-9-]+")))
                    saveFrames("visual-motion-$run-$suffix.gfx")
                }
            }
        } finally { ProxyPageOptionsStore.save(context,old) }
    }

    private fun runMenuBoundaryDiagnostic(dark: Boolean) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalMotion"))
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashMotionProfile"))
        suffix = if (dark) "dark" else "light"
        val context=ins.targetContext
        val old=ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context,ProxyPageOptions(displayMode=ProxyDisplayMode.TAB))
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark=dark }
                await("nav_proxy");click("nav_proxy");await("proxy_tools");settle()
                event("diagnostic-ready")
                // 作者 long：只测首次打开与根菜单收回，十次独立窗口扩充样本，不把submenu交互或录像开销混进trace。
                repeat(10) { pass ->
                    event("diagnostic-$pass-open-start")
                    traceSection("kclash_menu_open_$pass") {
                        click("proxy_tools");await("样式：标签")
                    }
                    event("diagnostic-$pass-open-visible")
                    SystemClock.sleep(550)
                    event("diagnostic-$pass-close-start")
                    traceSection("kclash_menu_close_$pass") {
                        back();awaitGone("样式：标签")
                    }
                    event("diagnostic-$pass-close-visible")
                    SystemClock.sleep(250)
                }
                scenario.onActivity { assertEquals("常用节点",it.selectedGroup);assertEquals(0,it.nodeSelections) }
                event("diagnostic-completed")
            }
        } finally { ProxyPageOptionsStore.save(context,old) }
    }

    private inline fun traceSection(name: String, action: () -> Unit) {
        android.os.Trace.beginSection(name)
        try { action() } finally { android.os.Trace.endSection() }
    }

    private fun run(dark: Boolean) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalMotion"))
        suffix = if (dark) "dark" else "light"
        val context = ins.targetContext
        val old = ProxyPageOptionsStore.load(context)
        try {
            ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
            if (profileFrames) shell("dumpsys gfxinfo ${context.packageName} reset")
            ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.dark = dark }
                await("dashboard_profile_switch"); settle(); capture("home")
                val title = bounds(find("dashboard_profile_name")!!)
                assertTrue("长配置名保持单行", title.height() <= 70)
                event("profile-picker-open"); click("dashboard_profile_switch"); await("家庭配置"); settle(); capture("profile_picker")
                click("家庭配置"); settle()
                scenario.onActivity { assertEquals(902L, it.selectedProfile); assertEquals(1, it.profileSelections) }
                event("profile-picker-reopen-back"); click("dashboard_profile_switch"); await("家庭配置"); settle(); back(); settle()
                // 作者 long：开始前采集Dock真实坐标，快速反向点击过程中不调用Compose同步或等待终帧。
                val profiles = bounds(find("nav_profiles")!!); val proxy = bounds(find("nav_proxy")!!); val home = bounds(find("nav_dashboard")!!)
                event("navigation-rapid-reversal")
                tap(profiles); SystemClock.sleep(110); tap(proxy); SystemClock.sleep(95); tap(home); SystemClock.sleep(80); tap(profiles)
                await("profile_card_901"); settle(); capture("profiles")
                scenario.onActivity { assertEquals(MainDestination.PROFILES, it.destination); assertEquals(0, it.nodeSelections) }
                click("profiles_add_fab"); settle(); capture("profiles_sources"); back(); settle()
                click("nav_proxy"); await("proxy_node_常用节点_香港节点"); settle(); capture("proxy")
                event("search-partial"); click("proxy_group_search_toggle"); await("proxy_group_search"); input("Japan-not-found")
                await("没有匹配的节点"); settle(); capture("proxy_search_empty")
                assertNull(find("proxy_group_tab_常用节点"));assertNull(find("proxy_group_tab_自动选择"))
                click("清除搜索"); await("proxy_node_常用节点_香港节点")
                input("香港"); await("proxy_node_常用节点_香港节点");settle();capture("proxy_partial")
                assertNull(find("proxy_group_tab_自动选择"))
                back(); SystemClock.sleep(350); back(); settle()
                scenario.onActivity { assertEquals("常用节点", it.selectedGroup); assertEquals(0, it.nodeSelections) }
                event("proxy-menu-nested"); click("proxy_tools");settle();capture("proxy_menu")
                click("样式：标签");await("列表");settle();capture("proxy_submenu");back();settle()
                await("样式：标签");click("样式：标签");await("列表");click("列表");settle();capture("proxy_list")
                click("nav_dashboard"); await("dashboard_outbound_ip");settle()
                event("quality-loading");click("dashboard_outbound_ip");await("IP 质量");settle()
                // 作者 long：初始半屏面板的后半字段尚在屏外，先通过真实手柄展开后比较同一视口中的行位置。
                val handle = find("connection_detail_handle")
                    ?: root()?.let { nodes(it).firstOrNull { n -> n.isClickable && bounds(n).height() in 120..150 && bounds(n).width() > 900 } }
                checkNotNull(handle) { "未找到真实面板手柄" }
                tap(bounds(handle));settle();capture("quality_loading")
                val labels=listOf("质量等级","IP 类型","风险标记","组织","ASN","查询来源")
                val before=labels.associateWith { bounds(checkNotNull(find(it)) { "缺少质量字段 $it" }).top }
                scenario.onActivity { it.finishQuality() };await("示例组织");settle();capture("quality_ready")
                val after=labels.associateWith { bounds(checkNotNull(find(it)) { "缺少质量字段 $it" }).top }
                assertEquals("加载完成不得推移质量行",before,after)
                event("quality-return");back();settle()
                scenario.onActivity { it.empty=true };click("nav_profiles");await("profiles_empty_add");settle();capture("profiles_empty")
                click("nav_proxy");await("暂无代理");settle();capture("proxy_empty")
                assertNull(find("proxy_groups_empty_action"))
                scenario.onActivity { assertEquals(1,it.profileSelections);assertEquals(0,it.nodeSelections) }
                event("completed")
                // 作者 long：测试进程结束后gfxinfo已不可读，必须在宿主仍存活时保存本轮帧统计；独占文件由外部SHA回读后收尾。
                val run = checkNotNull(InstrumentationRegistry.getArguments().getString("kclashMotionRun"))
                require(run.matches(Regex("[a-z0-9-]+")))
                if (!profileFrames) saveFrames("visual-motion-$run-$suffix.gfx")
            }
        } finally { stopMethodTrace(); ProxyPageOptionsStore.save(context,old) }
    }
    private fun root(): AccessibilityNodeInfo? {
        // 作者 long：跨Compose退场后系统缓存可能保留旧标签；先刷新原生节点缓存，画面消失不能用过期节点判失败。
        if (android.os.Build.VERSION.SDK_INT >= 33) ins.uiAutomation.clearCache()
        return ins.uiAutomation.rootInActiveWindow
    }
    private fun nodes(node: AccessibilityNodeInfo):Sequence<AccessibilityNodeInfo> = sequence {
        yield(node);for(i in 0 until node.childCount)node.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    private fun find(value:String):AccessibilityNodeInfo? = root()?.let { nodes(it).firstOrNull { node ->
        node.viewIdResourceName == value || node.text?.toString() == value
    } }
    private fun bounds(node:AccessibilityNodeInfo)=Rect().also(node::getBoundsInScreen)
    private fun await(value:String) {
        val deadline=SystemClock.uptimeMillis()+15_000
        while(SystemClock.uptimeMillis()<deadline) { if(find(value)!=null)return;SystemClock.sleep(100) }
        capture("failure")
        val values=root()?.let { nodes(it).map { n -> "${n.viewIdResourceName}:${n.text}" }.toList() }
        error("未找到自然场景入口 $value; nodes=$values")
    }
    private fun awaitGone(value:String) {
        val deadline=SystemClock.uptimeMillis()+15_000
        while(SystemClock.uptimeMillis()<deadline) { if(find(value)==null)return;SystemClock.sleep(50) }
        capture("failure")
        error("菜单退出后仍存在 $value")
    }
    private fun click(value:String){await(value);tap(bounds(checkNotNull(find(value))))}
    private fun tap(rect:Rect){
        val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
            e.source=InputDevice.SOURCE_TOUCHSCREEN
            try{assertTrue(ins.uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}
            if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(35)
        }
    }
    private fun input(text:String){
        val node=checkNotNull(find("proxy_group_search"))
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)}))
    }
    private fun back(){ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)}
    private fun settle(){SystemClock.sleep(1000)}
    private fun event(name:String){
        if (name == "quality-loading") stopMethodTrace()
        if (profileFrames) sampleFrames("before-$name")
        if (name == "proxy-menu-nested" && InstrumentationRegistry.getArguments().getString("kclashMotionMethodTrace") == "true") {
            val run=checkNotNull(InstrumentationRegistry.getArguments().getString("kclashMotionRun"))
            require(run.matches(Regex("[a-z0-9-]+")))
            val file=java.io.File(ins.targetContext.cacheDir,"motion-method-$run-$suffix.trace")
            check(!file.exists());methodTrace=file
            // 作者 long：方法采样只用于菜单热点归因，另跑诊断批次，不能把其扰动后的耗时计入性能对照。
            android.os.Debug.startMethodTracingSampling(file.absolutePath,32*1024*1024,1000)
        }
        ins.sendStatus(2,Bundle().apply{putString("kclash_motion_event",JSONObject().put("theme",suffix).put("event",name)
            .put("elapsedNanos",SystemClock.elapsedRealtimeNanos()).put("uptimeNanos",System.nanoTime()).toString())})
    }
    private fun capture(name:String){
        if (profileFrames) sampleFrames(name)
        // 作者 long：性能对照必须在截图和PNG压缩之前退出；只禁回传仍会污染渲染与CPU采样窗口。
        if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
        val bitmap=checkNotNull(ins.uiAutomation.takeScreenshot())
        val out=ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle()
        recordVisualPng("natural_${suffix}_$name",out.toByteArray())
    }
    private fun shell(command:String):ByteArray = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }
    private fun sampleFrames(label:String) {
        val run=checkNotNull(InstrumentationRegistry.getArguments().getString("kclashMotionRun"))
        require(run.matches(Regex("[a-z0-9-]+")))
        // 作者 long：在面板仍存活的稳定边界取各窗口统计，再清零；主题与阶段不能共享累计计数。
        saveFrames("visual-motion-$run-$suffix-${segment++}-${label.replace('_','-')}.gfx")
        shell("dumpsys gfxinfo ${ins.targetContext.packageName} reset")
    }
    private fun saveFrames(name:String) {
        val before=System.nanoTime()
        val gfx=shell("dumpsys gfxinfo ${ins.targetContext.packageName} framestats")
        val file=java.io.File(ins.targetContext.cacheDir,name)
        val inline=InstrumentationRegistry.getArguments().getString("kclashMotionInlineFrames") == "true"
        // 作者 long：非Debug独立包不允许run-as；小型帧表直接由签名Instrumentation回传，不开放文件组件或改正式权限。
        if (!inline) { check(!file.exists());file.writeBytes(gfx) }
        ins.sendStatus(2,Bundle().apply { putString("kclash_motion_gfx",JSONObject().put("name",file.name)
            .put("sampleStartUptimeNanos",before).put("sampleEndUptimeNanos",System.nanoTime())
            .apply { if (inline) put("inlineBase64",android.util.Base64.encodeToString(gfx,android.util.Base64.NO_WRAP)) }
            .put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(gfx).joinToString(""){"%02x".format(it)})
            .put("bytes",gfx.size).toString()) })
    }
    private fun stopMethodTrace() {
        val file=methodTrace ?: return
        android.os.Debug.stopMethodTracing();methodTrace=null
        val bytes=file.readBytes()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (InstrumentationRegistry.getArguments().getString("kclashMotionInlineTrace") == "true") {
            // 作者 long：Redmi拒绝run-as；通过既有Instrumentation分片回传，接收端必须核验全部索引、长度及SHA。
            val chunks = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).chunked(48 * 1024)
            chunks.forEachIndexed { index, chunk ->
                ins.sendStatus(2, Bundle().apply { putString("kclash_method_chunk", JSONObject()
                    .put("name", file.name).put("index", index).put("count", chunks.size)
                    .put("bytes", bytes.size).put("sha256", sha).put("base64", chunk).toString()) })
            }
            check(file.length() == bytes.size.toLong() && file.readBytes().contentEquals(bytes))
            check(file.delete())
        }
        ins.sendStatus(2,Bundle().apply { putString("kclash_motion_trace",JSONObject().put("name",file.name)
            .put("sha256",sha)
            .put("bytes",bytes.size).toString()) })
    }
}
