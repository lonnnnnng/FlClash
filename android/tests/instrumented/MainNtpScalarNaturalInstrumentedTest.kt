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
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：真实主页面以Android自然帧运行，不创建Compose规则；录像只能证明连续交互，不能混算成性能采样。 */
class MainNtpScalarNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()

    @Test fun ntpScalarDialogsSaveReopenAndRestoreWithRealRoom() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashNtpScalarAcceptance"))
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        val bytes = File(requireNotNull(args.getString("kclashLocalYamlPath"))).readBytes()
        assertEquals(args.getString("kclashLocalYamlSha256"), hash(bytes))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val entry = EntryPointAccessors.fromApplication(ins.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
            val repository = entry.profileRepository()
            repository.profilesLoaded.first { it }
            settle()
            // 作者 long：首次首页会异步落出口质量缓存，先等初始化写入稳定，再建立本次编辑的保护窗口。
            var previous = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
            var stableSince = SystemClock.uptimeMillis()
            val start = stableSince
            while (SystemClock.uptimeMillis() - start < 20_000) {
                SystemClock.sleep(250)
                val current = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
                if (current != previous) { previous = current; stableSince = SystemClock.uptimeMillis() }
                if (SystemClock.uptimeMillis() - start >= 5_000 && SystemClock.uptimeMillis() - stableSince >= 2_000) break
            }
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            val rows = entry.database().profiles().getAll()
            val files = rows.associate { it.id to signature(repository.profileFile(it.id)) }
            val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
            val scripts = entry.database().scripts().getAll()
            val selected = repository.currentProfileId.value
            var own: Profile? = null
            var primaryFailure: Throwable? = null
            val label = "NTP验收-${UUID.randomUUID().toString().take(6)}"
            try {
                own = repository.addCustomProfile(label)
                repository.updateLocalProfileContent(own.id, bytes.decodeToString())
                val source = signature(repository.profileFile(own.id))
                await("配置"); settle()
                // 作者 long：首页卡片与底栏均有“配置”文案，必须按真实底栏位置选入口，不能误进运行详情。
                val nav = checkNotNull(root()?.let { nodes(it).filter { node -> node.text?.toString() == "配置" }.maxByOrNull { bounds(it).bottom } })
                tap(bounds(nav)); await(label); settle(); longClick(label); click("编辑"); click("更多")
                click("NTP"); await("暂无设置项"); settle(); capture("empty")
                click("添加设置项"); click("服务器"); click("端口"); click("同步间隔（分钟）"); click("拨号代理")
                lateinit var viewModel: ProfilesViewModel
                scenario.onActivity { viewModel = ViewModelProvider(it)[ProfilesViewModel::class.java] }
                val addedDeadline = SystemClock.uptimeMillis() + 15_000
                while (viewModel.moreEditorState.value.isSaving && SystemClock.uptimeMillis() < addedDeadline) SystemClock.sleep(100)
                assertFalse(viewModel.moreEditorState.value.isSaving)
                assertNotNull(viewModel.moreEditorState.value.ntp.dialerProxy)
                settle()
                val heading = bounds(checkNotNull(find("添加设置项")))
                tap(Rect((24 * density).toInt(), heading.top, (60 * density).toInt(), heading.bottom))
                awaitParent()
                await("time.apple.com"); settle()
                // 作者 long：自然帧下额外记录同字段开/关及入场早退，独立于冻结时钟的几何断言，不混作性能采样。
                if (args.getString("kclashScalarMotionAcceptance") == "true") {
                    val started = SystemClock.uptimeMillis()
                    fun event(value: String) { ins.sendStatus(2, Bundle().apply {
                        putString("kclash_scalar_motion_event", "$value;elapsedMs=${SystemClock.uptimeMillis() - started}")
                    }) }
                    await("服务器"); settle(); event("open"); tap(bounds(checkNotNull(find("服务器")))); await("取消"); settle(); capture("motion_open")
                    settle(); event("close"); tap(bounds(checkNotNull(find("取消")))); awaitParent(); capture("motion_closed")
                    await("服务器"); settle(); event("early-open"); tap(bounds(checkNotNull(find("服务器")))); await("取消")
                    SystemClock.sleep(80); event("early-back"); back(); awaitParent()
                    event("finished"); capture("motion_early_closed")
                }
                click("服务器"); settle(); capture("server")
                setText("time.example.com"); awaitIme(true); settle(); capture("server_ime")
                back(); awaitIme(false); scenario.recreate(); await("提交"); settle()
                assertNotNull(find("time.example.com")); capture("draft_recreated")
                click("提交"); awaitParent(); capture("server_saved")
                click("端口"); setText("0"); awaitIme(true); click("提交")
                await("端口必须在 1–65535 之间"); settle(); capture("port_error")
                setText("124"); click("提交"); awaitParent()
                click("同步间隔（分钟）"); setText("60"); click("提交"); awaitParent()
                click("拨号代理"); click("提交"); awaitParent()
                val saved = ProfileProviderOverrides.fromJsonString(requireNotNull(entry.database().profiles().getById(own.id)).overridesJson)
                assertEquals("time.example.com", saved.ntp.server); assertEquals(124, saved.ntp.port)
                assertEquals(60, saved.ntp.interval); assertEquals("", saved.ntp.dialerProxy)
                back(); await("Dialers"); click("NTP"); await("time.example.com"); capture("reopened")
                scenario.recreate(); await("time.example.com"); settle(); capture("recreated")
                click("服务器"); setText(""); click("提交"); awaitParent()
                val cleared = ProfileProviderOverrides.fromJsonString(requireNotNull(entry.database().profiles().getById(own.id)).overridesJson)
                assertNull(cleared.ntp.server); assertEquals(124, cleared.ntp.port); assertEquals(60, cleared.ntp.interval)
                assertEquals(source, signature(repository.profileFile(own.id)))
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                capture("cleared")
                ins.sendStatus(2, Bundle().apply { putString("kclash_ntp_natural", "real-navigation=true;ime=true;draft-recreation=true;server-saved=true;port=124;interval=60;empty-dialer-preserved=true;clear-server=true;reopen=true;activity-recreation=true;room-readback=true;source-unchanged=true") })

            } catch (failure: Throwable) { primaryFailure = failure; throw failure }
            finally { try {
                scenario.onActivity { ViewModelProvider(it)[ProfilesViewModel::class.java].closeMoreEditor() }
                own?.let { profile ->
                    entry.database().profiles().getById(profile.id)?.let { row ->
                        check(row.type == ProfileType.CUSTOM && row.label == label && rows.none { it.id == row.id })
                        repository.deleteProfile(profile.id)
                    }
                    assertFalse(repository.profileFile(profile.id).exists())
                }
                if (repository.currentProfileId.value != selected) repository.selectProfile(selected)
                assertEquals(rows, entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(scripts, entry.database().scripts().getAll())
                assertEquals(preferences, entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(selected, repository.currentProfileId.value)
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                ins.sendStatus(2, Bundle().apply { putString("kclash_ntp_preservation", "rows-bodies-scripts-preferences-selection-preserved=true;own-row-and-file-removed=true;runtime=STOPPED") })
            } catch (cleanup: Throwable) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
            }
            }
        }
    }

    private fun awaitParent() {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var since = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            val active = root()
            val nodes = active?.let { nodes(it).toList() }.orEmpty()
            // 作者 long：输入弹框退场和父页重新接焦点分开完成，空树不能被当成保存后已返回。
            val ready = active?.packageName?.toString() == ins.targetContext.packageName &&
                nodes.any { it.text?.toString() == "NTP" } && nodes.none { it.isEditable || it.text?.toString() == "提交" }
            if (ready) {
                if (since == 0L) since = SystemClock.uptimeMillis()
                if (SystemClock.uptimeMillis() - since >= 600) return
            } else since = 0
            SystemClock.sleep(100)
        }
        capture("parent_failure"); error("NTP保存后未返回稳定父页")
    }

    private val density get() = ins.targetContext.resources.displayMetrics.density
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), hash(file.readBytes()))
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
        error("未找到NTP验收入口：$text")
    }
    private fun click(text: String) { await(text); settle(); tap(bounds(checkNotNull(find(text)))) }
    private fun awaitTitle(text: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val title = find(text)
            if (title != null && bounds(title).top < 100 * density) return
            SystemClock.sleep(25)
        }
        error("子页标题尚未接管前台：$text")
    }
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
    private fun longClick(text: String) { await(text); tap(bounds(checkNotNull(find(text))), 800) }
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
            var visible = false
            ins.runOnMainSync { visible = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                ?.let(ViewCompat::getRootWindowInsets)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            if (visible == expected) return
            SystemClock.sleep(100)
        }
        error("嗅探窗口IME状态不符：$expected")
    }
    private fun back() = ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun settle() = SystemClock.sleep(900)
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
        val bitmap = checkNotNull(ins.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        recordVisualPng("vis060_main_$name", output.toByteArray())
    }
}
