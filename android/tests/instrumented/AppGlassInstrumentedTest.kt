package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 作者 long：同一背景上的材质差异必须落实到像素；API27还需实际触发无RuntimeShader的降级，防止只凭版本判断宣布兼容。 */
class AppGlassInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun sharedMaterialsAndQualityRenderDistinctPixelsAcrossThemes() {
        val pixels = IntArray(800 * 1000) { i ->
            if ((i % 800 / 29 + i / 800 / 41) % 2 == 0) 0xff2851b0.toInt() else 0xffd0a040.toInt()
        }
        val backdrop = kotlinx.coroutines.runBlocking {
            prepareAppMenuBackdrop(Bitmap.createBitmap(pixels, 800, 1000, Bitmap.Config.ARGB_8888), compose.activity.resources.displayMetrics.density)
        }
        assertFalse(backdrop.raw.sameAs(backdrop.frosted))
        var dark by mutableStateOf(false)
        var quality by mutableStateOf(AppGlassQuality.ULTRA)
        var intensity by mutableStateOf(1f)
        val hardware = mutableMapOf<Int, Boolean>()
        compose.setContent { KClashTheme(darkTheme = dark) {
            val bright = MaterialTheme.colorScheme.surfaceBright
            val styles = listOf(AppGlassStyle.regular(dark), AppGlassStyle.pill(dark), AppGlassStyle.menu(dark, bright),
                AppGlassStyle.alert(dark, bright), AppGlassStyle.tinted(dark, Color(0xFF007AFF)), AppGlassStyle.lens(dark))
            Column(Modifier.fillMaxSize().background(dashboardPageColor()).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API ${Build.VERSION.SDK_INT} · $quality · ${if (dark) "深色" else "浅色"}", color = MaterialTheme.colorScheme.onSurface)
                styles.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { column, style ->
                            val index = row * 2 + column
                            val renderer = remember { AppMenuGlassRenderer() }
                            Canvas(Modifier.weight(1f).height(120.dp).testTag("material_$index")) {
                                val frame = AppMenuMorphFrame(Rect(Offset.Zero, size), 24.dp.toPx(), 1f)
                                val rendered = renderer.draw(this, frame, backdrop, Offset.Zero, bright, dark, style = style, quality = quality, amount = intensity)
                                hardware[index] = rendered
                                if (!rendered) clipPath(frame.path()) { drawAppGlassFallback(frame, backdrop, Offset.Zero, style, quality, amount = intensity) }
                            }
                        }
                    }
                }
            }
        } }
        for (theme in listOf(false, true)) {
            var ultraLens: androidx.compose.ui.graphics.ImageBitmap? = null
            var ultraPill: androidx.compose.ui.graphics.ImageBitmap? = null
            for (tier in listOf(AppGlassQuality.ULTRA, AppGlassQuality.STANDARD)) {
                compose.runOnIdle { dark = theme; quality = tier }
                val images = (0..5).map { compose.onNodeWithTag("material_$it").captureToImage() }
                compose.runOnIdle { assertTrue(hardware.values.all { it == (Build.VERSION.SDK_INT >= 33) }) }
                assertTrue("regular/menu必须实际改变背景", difference(images[0], images[2]) > .01f)
                assertTrue("带色材质不能沿用menu染色", difference(images[2], images[4]) > .02f)
                assertTrue("lens不能变成整块menu", difference(images[2], images[5]) > .03f)
                if (tier == AppGlassQuality.ULTRA) { ultraLens = images[5]; ultraPill = images[1] }
                else {
                    assertTrue("standard合并haze必须落实到采样", difference(checkNotNull(ultraPill), images[1]) > .001f)
                    if (Build.VERSION.SDK_INT >= 33) assertTrue("ultra lens色散必须实际渲染", difference(checkNotNull(ultraLens), images[5]) > .0001f)
                }
                capture("materials_${Build.VERSION.SDK_INT}_${theme}_$tier")
                if (tier == AppGlassQuality.STANDARD) {
                    compose.runOnIdle { intensity = 0f }
                    val bare = compose.onNodeWithTag("material_1").captureToImage()
                    compose.runOnIdle { intensity = .001f }
                    val beginning = compose.onNodeWithTag("material_1").captureToImage()
                    assertTrue("standard pill不能在极小强度跳到完整模糊", difference(bare, beginning) < .005f)
                    compose.runOnIdle { intensity = 1f }
                }
            }
        }
    }

    @Test fun transparentFrostKeepsColorAndFallbackInterpolatesPremultipliedAlpha() {
        val source = Bitmap.createBitmap(IntArray(400 * 200) { if (it % 400 < 200) 0xffff0000.toInt() else 0 }, 400, 200, Bitmap.Config.ARGB_8888)
        val prepared = kotlinx.coroutines.runBlocking { prepareAppMenuBackdrop(source, 1f) }
        var transitions = 0
        for (y in 0 until prepared.frosted.height) for (x in 0 until prepared.frosted.width) {
            val pixel = prepared.frosted.getPixel(x,y)
            if (android.graphics.Color.alpha(pixel) in 20..220) {
                transitions++
                assertTrue("透明黑色不能污染红色模糊边缘", android.graphics.Color.red(pixel) >= 250)
            }
        }
        assertTrue(transitions > 0)
        val raw = Bitmap.createBitmap(IntArray(40 * 40) { 0x80ff0000.toInt() }, 40, 40, Bitmap.Config.ARGB_8888)
        val frost = Bitmap.createBitmap(IntArray(40 * 40) { 0x800000ff.toInt() }, 40, 40, Bitmap.Config.ARGB_8888)
        val backdrop = AppMenuBackdrop(raw, frost, 1f, 1f)
        for (haze in listOf(0f, .5f, .72f)) {
            val output = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f),
                androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(output.asImageBitmap()),
                androidx.compose.ui.geometry.Size(40f, 40f)) {
                drawAppGlassFallback(AppMenuMorphFrame(Rect(Offset.Zero, size), 0f, 1f), backdrop, Offset.Zero,
                    AppGlassStyle(Color.Transparent, blur = 6f, saturation = 1f, haze = haze, hazeBlur = 12f))
            }
            val middle = output.getPixel(20,20)
            assertTrue("haze=${haze}不能把半透明叠成更厚的玻璃", abs(android.graphics.Color.alpha(middle) - 128) <= 1)
            if (haze == 0f) {
                assertTrue(abs(android.graphics.Color.red(middle) - 128) <= 2)
                assertTrue(abs(android.graphics.Color.blue(middle) - 128) <= 2)
            }
        }
    }

    @Test fun windowCaptureAndDefaultDisabledStayUsableOnCurrentApi() {
        var complete by mutableStateOf(false)
        var captured: AppMenuBackdrop? = null
        compose.setContent { KClashTheme {
            val view = LocalView.current
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            assertFalse(LocalAppMenuGlass.current)
            assertEquals(AppGlassQuality.ULTRA, LocalAppGlassQuality.current)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Blue); drawRect(Color.Yellow, Offset(size.width / 2, 0f))
            }
            LaunchedEffect(Unit) {
                // 作者 long：等待宿主首帧后再PixelCopy；必须验证真实Window入口，离屏Bitmap不能证明API26分支可用。
                withFrameNanos { }; withFrameNanos { }
                captured = captureAppMenuBackdrop(view, density); complete = true
            }
        } }
        compose.waitUntil(15_000) { complete }
        if (Build.VERSION.SDK_INT >= 26) {
            val backdrop = checkNotNull(captured)
            assertTrue(backdrop.raw.width > 0 && backdrop.raw.height > 0)
            assertFalse(backdrop.raw.sameAs(backdrop.frosted))
        } else assertNull(captured)
    }

    private fun difference(a: androidx.compose.ui.graphics.ImageBitmap, b: androidx.compose.ui.graphics.ImageBitmap): Float {
        val first = a.toPixelMap(); val second = b.toPixelMap()
        var sum = 0f; var count = 0
        for (y in 2 until minOf(a.height, b.height) - 2 step 3) for (x in 2 until minOf(a.width, b.width) - 2 step 3) {
            val left = first[x,y]; val right = second[x,y]
            sum += abs(left.red-right.red)+abs(left.green-right.green)+abs(left.blue-right.blue); count++
        }
        return sum / count.coerceAtLeast(1)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
    }
}
