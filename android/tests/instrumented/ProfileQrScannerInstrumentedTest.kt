package com.lonnnnnng.flclash

import android.Manifest
import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.lonnnnnng.flclash.data.DeepLinkProfileParser
import com.lonnnnnng.flclash.data.QrCodeImageImporter
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import com.lonnnnnng.flclash.ui.recordVisualPng
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实镜头生命周期和真实离线识别分开验收，固定链接只用于识别，不访问网络或创建配置。 */
class ProfileQrScannerInstrumentedTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val entry get() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun rows() = runBlocking(Dispatchers.IO) { entry.database().profiles().getAll() }

    @Test fun cameraOpensTogglesTorchReleasesInBackgroundAndOnClose() {
        assertPortraitDefaultFontConfiguration()
        val before = rows()
        val files = before.associate { it.id to entry.profileRepository().profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } }
        val manager = compose.activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
        val available = ConcurrentHashMap<String, Boolean>()
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) { available[cameraId] = true }
            override fun onCameraUnavailable(cameraId: String) { available[cameraId] = false }
        }
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        try {
            openScanner()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_qr_torch").fetchSemanticsNodes().any { androidx.compose.ui.semantics.SemanticsProperties.Disabled !in it.config } }
            waitFor { available[id] == false }
            capture("profile_qr_camera")
            compose.onNodeWithTag("profile_qr_torch").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("关闭手电筒").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_qr_torch").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("打开手电筒").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            waitFor { available[id] == true }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            // 作者 long：Compose 测试时钟需先推进一帧恢复重组；直接睡眠等待系统相机会阻止新会话绑定。
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_qr_torch").fetchSemanticsNodes().any { androidx.compose.ui.semantics.SemanticsProperties.Disabled !in it.config } }
            waitFor { available[id] == false }
            compose.onNodeWithTag("profile_qr_close").performClick()
            compose.onNodeWithTag("profile_qr_scanner").assertDoesNotExist()
            waitFor { available[id] == true }
            assertEquals(before, rows())
            assertEquals(files, before.associate { it.id to entry.profileRepository().profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } })
        } finally { manager.unregisterAvailabilityCallback(callback) }
    }

    @Test fun offlineImageDecoderAcceptsInstallLinkAndRejectsUnrelatedQr() = runBlocking {
        val cache = instrumentation.targetContext.cacheDir
        val file = File.createTempFile("qr-acceptance-", ".png", cache)
        val before = rows()
        try {
            for ((raw, expected) in listOf(
                "clash://install-config?url=https%3A%2F%2Fexample.com%2Fprofile.yaml&name=QR" to "https://example.com/profile.yaml",
                "WIFI:T:WPA;S:example;;" to null,
            )) {
                val matrix = MultiFormatWriter().encode(raw, BarcodeFormat.QR_CODE, 800, 800)
                val pixels = IntArray(800 * 800) { if (matrix[it % 800, it / 800]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                val bitmap = Bitmap.createBitmap(pixels, 800, 800, Bitmap.Config.ARGB_8888)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val result = DeepLinkProfileParser.fromQrCodes(QrCodeImageImporter.decode(instrumentation.targetContext, Uri.fromFile(file)))
                assertEquals(expected, result?.url)
                if (expected != null) assertEquals("QR", result?.label)
            }
            assertEquals(before, rows())
        } finally { check(file.parentFile == cache && file.name.startsWith("qr-acceptance-")); file.delete() }
    }

    @Test fun systemAlbumReturnsQrToConfirmationWithoutImporting() {
        assertPortraitDefaultFontConfiguration()
        val before = rows()
        val selected = entry.profileRepository().currentProfileId.value
        val files = before.associate { it.id to entry.profileRepository().profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } }
        val resolver = compose.activity.contentResolver
        val filename = "kclash-qr-acceptance-${System.nanoTime()}.png"
        val image = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Download")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            // 作者 long：测试只交付固定离线二维码，不确认下载；系统相册返回必须走真实 ActivityResult 与扫码页回调。
            val matrix = MultiFormatWriter().encode(
                "clash://install-config?url=https%3A%2F%2Fexample.com%2Fqr-album.yaml&name=AlbumQR",
                BarcodeFormat.QR_CODE, 800, 800,
            )
            val pixels = IntArray(800 * 800) { if (matrix[it % 800, it / 800]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            val bitmap = Bitmap.createBitmap(pixels, 800, 800, Bitmap.Config.ARGB_8888)
            try { checkNotNull(resolver.openOutputStream(image)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            resolver.update(image, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            openScanner()
            compose.onNodeWithTag("profile_qr_album").performClick()
            chooseAlbumImage(filename)
            waitFor { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_add_remote_url").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_qr_scanner").assertDoesNotExist()
            compose.onNodeWithTag("profile_add_remote_label").assertTextContains("AlbumQR")
            compose.onNodeWithTag("profile_add_remote_url").assertTextContains("https://example.com/qr-album.yaml")
            assertEquals(before, rows())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, before.associate { it.id to entry.profileRepository().profileFile(it.id).let { file -> Triple(file.name, file.length(), file.lastModified()) } })
            capture("profile_qr_album_confirmation")
            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(5000) { compose.onAllNodesWithTag("profile_add_remote_url").fetchSemanticsNodes().isEmpty() }
        } finally {
            // 作者 long：仅回收本次插入且名称仍匹配的图片 URI，不删除相册中任何既有图片。
            val owned = resolver.query(image, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == filename
            } == true
            check(owned) { "测试图片身份发生变化，保留现场" }
            assertEquals(1, resolver.delete(image, null, null))
        }
    }

    private fun chooseAlbumImage(filename: String) {
        fun root() = instrumentation.uiAutomation.rootInActiveWindow
        fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
            yield(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let { yieldAll(nodes(it)) }
        }
        fun click(node: AccessibilityNodeInfo): Boolean {
            var candidate: AccessibilityNodeInfo? = node
            repeat(5) {
                if (candidate?.isClickable == true && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                candidate = candidate?.parent
            }
            return false
        }
        waitFor { root()?.packageName?.toString()?.let { it != compose.activity.packageName } == true }
        if (root()?.packageName?.toString()?.endsWith("documentsui") != true) {
            val menu = root()?.let(::nodes)?.firstOrNull {
                it.isVisibleToUser && it.contentDescription?.toString() in setOf("更多选项", "More options")
            }
            if (menu != null) click(menu)
            waitFor {
                root()?.let(::nodes)?.firstOrNull {
                    it.isVisibleToUser && it.text?.toString()?.let { text -> text.startsWith("浏览") || text.startsWith("Browse", true) } == true
                }?.let(::click) == true
            }
        }
        waitFor { root()?.packageName?.toString()?.endsWith("documentsui") == true }
        fun choose() = root()?.findAccessibilityNodeInfosByText(filename)?.firstOrNull {
            it.isVisibleToUser && !it.isEditable && it.text?.toString() == filename
        }?.let(::click) == true
        if (choose()) return
        val current = checkNotNull(root())
        val drawer = current.findAccessibilityNodeInfosByViewId("android:id/home").firstOrNull()
            ?: nodes(current).firstOrNull {
                it.contentDescription?.toString()?.let { value -> value.contains("根目录") || value.contains("Show roots", true) || value.contains("navigation", true) } == true
            }
        check(drawer != null && click(drawer)) { "系统文件选择器缺少目录入口" }
        waitFor {
            root()?.let(::nodes)?.firstOrNull {
                it.isVisibleToUser && it.text?.toString()?.let { text -> text.startsWith("下载") || text.equals("Downloads", true) } == true
            }?.let(::click) == true
        }
        if (choose()) return
        val search = checkNotNull(root()).let(::nodes).firstOrNull {
            it.isVisibleToUser && it.contentDescription?.toString() in setOf("搜索", "Search")
        }
        check(search != null && click(search)) { "系统文件选择器缺少搜索入口" }
        waitFor {
            // 作者 long：DocumentsUI 的搜索控件随系统包使用不同资源前缀，按可编辑语义定位才能覆盖真实系统选择器。
            root()?.let(::nodes)?.firstOrNull { it.isVisibleToUser && it.isEditable }?.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename) },
            ) == true
        }
        shell("input keyevent KEYCODE_ENTER")
        waitFor(::choose)
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }

    private fun openScanner() {
        compose.onNodeWithTag("nav_profiles").performClick()
        compose.onNodeWithTag("profiles_add_fab").performClick()
        compose.onNodeWithTag("profile_add_source_qr").performClick()
        compose.onNodeWithTag("profile_qr_scanner").assertIsDisplayed()
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(100) }
        error("设备状态未在规定时间内完成切换")
    }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); SystemClock.sleep(400)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
