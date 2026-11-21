package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.*

/** 作者 long：以独立Flutter栅格核对中间轮廓，另验真实按钮忙态和快速反向；不启动核心或借用正式配置。 */
class RuntimeToggleShapeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun contoursAndGlassProfilesMatchFlutterAtSixProgressValues() {
        val reference = javaClass.getResourceAsStream("/runtime-toggle-flutter-reference.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        for (item in reference.getAsJsonArray("cases")) {
            val data = item.asJsonObject
            val progress = data["progress"].asFloat
            val width = data["size"].asInt
            val size = Size(width.toFloat(), width.toFloat())
            val shape = RuntimeToggleShape { progress }
            val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
            val bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawPath(outline.path.asAndroidPath(), Paint().apply { color = -1; isAntiAlias = false })
            val spans = data.getAsJsonArray("spans")
            repeat(width) { y ->
                val pixels = (0 until width).filter { bitmap.getPixel(it, y) != 0 }
                assertTrue("progress=$progress y=$y left", abs((pixels.firstOrNull() ?: -1) - spans[y].asJsonArray[0].asInt) <= 1)
                assertTrue("progress=$progress y=$y right", abs((pixels.lastOrNull() ?: -1) - spans[y].asJsonArray[1].asInt) <= 1)
            }
            val profile = shape.profile(size, LayoutDirection.Ltr, Density(1f))
            assertSame("静止重复绘制复用采样", profile, shape.profile(size, LayoutDirection.Ltr, Density(1f)))
            val referenceProfile = data.getAsJsonArray("profile")
            repeat(64) { i ->
                val radius = profile[i]
                assertTrue("profile finite/inside", radius.isFinite() && radius > width * .35f && radius < width * .55f)
                // 作者 long：九瓣形一行可有多个区间，左右跨度不是内部掩码；直接对比Flutter原生PathMetric导出的射线。
                assertEquals("ray=$i progress=$progress", referenceProfile[i].asFloat, radius, .6f)
            }
            bitmap.recycle()
        }
    }

    @Test fun animationReadsStayInDrawingAndReverseFromCurrentContour() {
        var running by mutableStateOf(false)
        lateinit var shape: RuntimeToggleShape
        var compositions = 0
        compose.setContent {
            shape = rememberRuntimeToggleShape(running)
            SideEffect { compositions++ }
            Box(Modifier.size(88.dp).background(Color.Blue, shape).testTag("animated_toggle"))
        }
        compose.waitForIdle()
        val node = compose.onNodeWithTag("animated_toggle")
        fun pixels(): IntArray {
            val bitmap = node.captureToImage().asAndroidBitmap()
            return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        }
        val initialPixels = pixels()
        val size = Size(220f, 220f)
        fun current() = shape.createOutline(size, LayoutDirection.Ltr, Density(1f)).path
        val initial = compose.runOnIdle { current() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { running = true }
        compose.mainClock.advanceTimeBy(32)
        val count = compositions
        compose.mainClock.advanceTimeBy(120)
        val middle = compose.runOnIdle { current() }
        assertNotSame(initial, middle)
        assertFalse("动画值必须使实际绘制失效，不能只让手工调用拿到新路径", initialPixels.contentEquals(pixels()))
        assertEquals("动画不能每帧重组正文", count, compositions)
        compose.runOnIdle { running = false }
        assertSame("反向操作不能立即跳回圆形", middle, compose.runOnIdle { current() })
        compose.mainClock.advanceTimeBy(600)
        val stopped = compose.runOnIdle { shape.profile(size, LayoutDirection.Ltr, Density(1f)) }
        assertTrue("停止后回到圆形", stopped.max() - stopped.min() < 1f)
        val settled = compose.runOnIdle { current() }
        val settledPixels = pixels()
        assertArrayEquals("反向结束的屏幕也须回到圆形", initialPixels, settledPixels)
        compose.mainClock.advanceTimeBy(1000)
        assertSame("终态不再制造路径", settled, compose.runOnIdle { current() })
        assertArrayEquals(settledPixels, pixels())
        compose.mainClock.autoAdvance = true
    }

    @Test fun runtimeButtonPreservesBusyActionsAndBoundsInBothMaterialsAndThemes() {
        assertPortraitDefaultFontConfiguration()
        var status by mutableStateOf(CoreRuntimeStatus.STOPPED)
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var starts = 0; var stops = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalAppMenuGlass provides glass) {
                RuntimeCard(status, { starts++; status = CoreRuntimeStatus.STARTING },
                    { stops++; status = CoreRuntimeStatus.STOPPING })
            }
        } }
        val toggle = compose.onNodeWithTag("dashboard_runtime_toggle")
        val card = compose.onNodeWithTag("dashboard_runtime_card")
        val height = card.fetchSemanticsNode().boundsInRoot.height
        val bounds = toggle.fetchSemanticsNode().boundsInRoot
        for (theme in listOf(false, true)) for (material in listOf(false, true)) {
            compose.runOnIdle { dark = theme; glass = material; status = CoreRuntimeStatus.STOPPED }
            toggle.performClick().assertIsNotEnabled()
            val startCount = starts
            toggle.performTouchInput { click() }
            compose.runOnIdle { assertEquals(startCount, starts); status = CoreRuntimeStatus.RUNNING }
            compose.waitForIdle()
            val image = card.captureToImage().asAndroidBitmap()
            recordVisualPng("runtime_toggle_${theme}_${material}_running", ByteArrayOutputStream().also {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray())
            assertEquals(height, card.fetchSemanticsNode().boundsInRoot.height, .1f)
            assertEquals(bounds, toggle.fetchSemanticsNode().boundsInRoot)
            toggle.performClick().assertIsNotEnabled()
            val stopCount = stops
            toggle.performTouchInput { click() }
            compose.runOnIdle { assertEquals(stopCount, stops); status = CoreRuntimeStatus.FAILED }
            toggle.assertIsEnabled()
            val stopped = card.captureToImage().asAndroidBitmap()
            recordVisualPng("runtime_toggle_${theme}_${material}_failed", ByteArrayOutputStream().also {
                stopped.compress(Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray())
        }
        assertEquals(4, starts); assertEquals(4, stops)
    }
}
