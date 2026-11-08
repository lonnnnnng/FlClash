package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** 作者 long：检查真实合成像素，防止状态里已记录按钮颜色但绘制仍覆盖为菜单底色。 */
class MenuFillParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun coloredControlTransfersFillAndGlassReturnsToUncoveredBackdrop() {
        assertPortraitDefaultFontConfiguration()
        val blue = Color(0xFF0066CC)
        val surface = Color(0xFFE6CCB3)
        val background = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff306040.toInt()) }
        val backdrop = kotlinx.coroutines.runBlocking { prepareAppMenuBackdrop(background, compose.activity.resources.displayMetrics.density) }
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var progress by mutableFloatStateOf(0f)
        var color by mutableStateOf(blue)
        compose.setContent { KClashTheme(darkTheme = dark) {
            Box(Modifier.size(300.dp).background(Color.White).testTag("fill_parity")) {
                Box(Modifier.size(300.dp).appMenuMorph(surface, { size ->
                    AppMenuMorphFrame(Rect(size.width * .1f, size.height * .1f, size.width * .9f, size.height * .9f),
                        24f, 0f, materialProgress = progress)
                }, backdrop = if (glass) backdrop else null, sourceColor = color))
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; glass = false; progress = 0f; color = blue }
            assertPixel(blue.toArgb(), center("plain_source_$theme"))
            compose.runOnIdle { progress = .5f }
            assertPixel(0xff7399c0.toInt(), center("plain_middle_$theme"), 2)
            compose.runOnIdle { progress = 1f }
            assertPixel(surface.toArgb(), center("plain_menu_$theme"))
            compose.runOnIdle { glass = true; progress = 0f }
            assertPixel(blue.toArgb(), center("glass_source_$theme"))
            compose.runOnIdle { progress = 1f }
            val reached = center("glass_menu_$theme")
            compose.runOnIdle { color = Color.Transparent }
            assertPixel(reached, center("glass_transparent_source_$theme"))
            assertTrue("玻璃终态不能被不透明菜单底色盖住", reached != surface.toArgb())
        }
    }

    private fun center(name: String): Int {
        val bitmap = compose.onNodeWithTag("fill_parity").captureToImage().asAndroidBitmap()
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    private fun assertPixel(expected: Int, actual: Int, tolerance: Int = 1) {
        for (shift in listOf(0, 8, 16, 24)) assertTrue("颜色交接错误 expected=$expected actual=$actual", abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= tolerance)
    }
}
