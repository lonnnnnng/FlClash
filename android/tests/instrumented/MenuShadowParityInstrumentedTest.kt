package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** 作者 long：单独看阴影像素，正文填充不能遮住错误的内部染黑或液滴投影。 */
class MenuShadowParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun platterShadowLeavesInteriorAndDetachedDropletClearInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var strength by mutableFloatStateOf(1f)
        var unit = 1f
        val legacy = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashLegacyShadow") == "true"
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                unit = LocalDensity.current.density
                val measured = remember(unit) { mutableStateOf(IntSize((340 * unit).roundToInt(), (340 * unit).roundToInt())) }
                Box(Modifier.size(340.dp).background(if (dark) Color(0xFF242528) else Color.White).testTag("shadow_parity")) {
                    val frame: (androidx.compose.ui.geometry.Size) -> AppMenuMorphFrame = {
                        AppMenuMorphFrame(Rect(40 * unit, 80 * unit, 200 * unit, 240 * unit), 24 * unit, 1f,
                            droplet = Rect(250 * unit, 40 * unit, 290 * unit, 80 * unit), shadowProgress = strength)
                    }
                    // 作者 long：显式红测复用旧外层采用的联合轮廓投影，验证断言真能识别内部染黑；默认只跑正式platter路径。
                    if (legacy) AppMenuFrameShadow(Modifier.size(340.dp), measured, frame, { true }, { strength }, dark)
                    else AppMenuPlatterShadow(Modifier.size(340.dp), measured, frame, { true }, dark, false)
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; strength = 1f }
            val image = compose.onNodeWithTag("shadow_parity").captureToImage().asAndroidBitmap()
            fun pixel(x: Int, y: Int) = image.getPixel((x * unit).roundToInt(), (y * unit).roundToInt())
            val background = pixel(5, 5)
            recordVisualPng("platter_shadow_${theme}_legacy_$legacy", ByteArrayOutputStream().also {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray())
            assertEquals("主体内部不能被阴影染黑", background, pixel(120, 160))
            assertEquals("分离液滴内部保持透明", background, pixel(270, 60))
            assertEquals("分离液滴不应自行产生投影", background, pixel(270, 90))
            assertTrue("主体下方必须有可见投影", android.graphics.Color.red(pixel(120, 252)) < android.graphics.Color.red(background))
            compose.runOnIdle { strength = 0f }
            val zero = compose.onNodeWithTag("shadow_parity").captureToImage().asAndroidBitmap()
            assertEquals("回源结束不残留外层阴影", background, zero.getPixel((120 * unit).roundToInt(), (252 * unit).roundToInt()))
        }
    }
}
