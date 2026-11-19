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
class MainSnifferNaturalInstrumentedTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()

    @Test fun snifferPortsSaveReopenAndRapidBackUseRealNavigation() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashSnifferNaturalAcceptance"))
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
            val label = "嗅探验收-${UUID.randomUUID().toString().take(6)}"
            try {
                own = repository.addCustomProfile(label)
                repository.updateLocalProfileContent(own.id, bytes.decodeToString())
                val source = signature(repository.profileFile(own.id))
                await("配置"); settle()
                // 作者 long：首页卡片与底栏均有“配置”文案，必须按真实底栏位置选入口，不能误进运行详情。
                val nav = checkNotNull(root()?.let { nodes(it).filter { node -> node.text?.toString() == "配置" }.maxByOrNull { bounds(it).bottom } })
                tap(bounds(nav)); await(label); settle(); longClick(label); click("编辑"); click("更多")
                // 作者 long：在真实窗口刚入场时反向返回，再走同一路径；保存次数以Room最终覆盖值核验。
                click("域名嗅探"); awaitTitle("域名嗅探"); SystemClock.sleep(80); back(); await("Dialers"); settle()
                click("域名嗅探"); await("暂无设置项"); settle(); capture("empty")
                click("添加设置项"); settle(); capture("add")
                setText("HTTP"); await("HTTP"); awaitIme(true); capture("search_ime")
                back(); awaitIme(false); click("HTTP"); settle()
                val heading = bounds(checkNotNull(find("添加设置项")))
                tap(Rect((24 * density).toInt(), heading.top, (60 * density).toInt(), heading.bottom))
                await("域名嗅探"); click("HTTP"); await("端口"); settle(); capture("protocol")
                click("端口"); await("暂无数据"); settle(); capture("ports_empty")
                toolbar("端口", 0); click("快速编辑"); settle()
                setText("8080\n8000-9000"); awaitIme(true); capture("ports_ime")
                back(); awaitIme(false); back(); await("8000-9000"); awaitIme(false); settle(); capture("ports_draft")
                toolbar("端口", 1); await("覆盖目标地址"); settle()
                click("覆盖目标地址"); click("已关闭")
                val receiptDeadline = SystemClock.uptimeMillis() + 15_000
                while (SystemClock.uptimeMillis() < receiptDeadline) {
                    val saved = ProfileProviderOverrides.fromJsonString(requireNotNull(entry.database().profiles().getById(own.id)).overridesJson)
                    if (saved.sniffer.sniff?.get("HTTP")?.overrideDestination == false && find("已开启") == null) break
                    SystemClock.sleep(100)
                }
                assertEquals(false, ProfileProviderOverrides.fromJsonString(requireNotNull(entry.database().profiles().getById(own.id)).overridesJson)
                    .sniffer.sniff?.get("HTTP")?.overrideDestination)
                assertNull("菜单必须真实退场后再验父页返回", find("已开启"))
                settle(); capture("destination")
                back(); await("域名嗅探"); back(); await("Dialers")
                click("域名嗅探"); click("HTTP"); click("端口"); await("8000-9000"); settle(); capture("reopened")
                scenario.recreate(); await("8000-9000"); settle(); capture("recreated")
                back(); await("覆盖目标地址"); assertNotNull(find("已关闭"))
                val saved = ProfileProviderOverrides.fromJsonString(requireNotNull(entry.database().profiles().getById(own.id)).overridesJson)
                assertEquals(listOf("8080", "8000-9000"), saved.sniffer.sniff?.get("HTTP")?.ports)
                assertEquals(false, saved.sniffer.sniff?.get("HTTP")?.overrideDestination)
                assertEquals(source, signature(repository.profileFile(own.id)))
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                ins.sendStatus(2, Bundle().apply { putString("kclash_sniffer_natural", "real-navigation=true;rapid-back=true;ime=true;ports=2;destination=false;reopen=true;activity-recreation=true;room-readback=true;source-unchanged=true") })
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
                ins.sendStatus(2, Bundle().apply { putString("kclash_sniffer_preservation", "rows-bodies-scripts-preferences-selection-preserved=true;own-row-and-file-removed=true;runtime=STOPPED") })
            } catch (cleanup: Throwable) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
            }
            }
        }
    }

    private val density get() = ins.targetContext.resources.displayMetrics.density
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), hash(file.readBytes()))
    private fun root(): AccessibilityNodeInfo? { ins.uiAutomation.clearCache(); return ins.uiAutomation.rootInActiveWindow }
    private fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node); for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    private fun find(text: String) = root()?.let { nodes(it).firstOrNull { n -> n.text?.toString() == text } }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun await(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) { if (find(text) != null) return; SystemClock.sleep(100) }
        capture("failure")
        error("未找到嗅探验收入口：$text")
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
        val node = checkNotNull(root()?.let { nodes(it).firstOrNull { n -> n.isEditable } })
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
        recordVisualPng("vis056_main_$name", output.toByteArray())
    }
}
