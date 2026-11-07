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
        ins.sendStatus(2,Bundle().apply { putString("kclash_motion_trace",JSONObject().put("name",file.name)
            .put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
            .put("bytes",bytes.size).toString()) })
    }
}
