package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/** 作者 long：用已知阶跃边缘检查实际GPU绘制，几何帧有blur字段不代表屏幕已应用正确sigma。 */
class MenuContentParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun contentBlurKeepsScreenWidthAfterCardShrinksAndClearsAtZero() {
        assertPortraitDefaultFontConfiguration()
        var scale by mutableFloatStateOf(1f)
        var sigma by mutableFloatStateOf(4f)
        var unit = 1f
        compose.setContent {
            unit = LocalDensity.current.density
            Box(Modifier.size(300.dp).background(Color.White).testTag("filtered_content")) {
                Box(Modifier.size(300.dp).appMenuMorph(Color.Transparent, { size ->
                    AppMenuMorphFrame(Rect(Offset(size.width, size.height) * ((1f - scale) / 2f), size * scale),
                        0f, 1f, blur = sigma * unit, returning = true)
                }).graphicsLayer().drawBehind { drawRect(Color.Black, Offset(size.width / 2, size.height / 4), Size(size.width / 3, size.height / 2)) })
            }
        }
        val widths = listOf(1f, .5f).map { value ->
            compose.runOnIdle { scale = value }
            val image = snapshot("filtered_content", "content_blur_scale_$value")
            edgeWidth(image, 150f * unit, unit).also {
                // 作者 long：高斯阶跃的10%至90%宽度约2.56σ；保留GPU近似与像素取整误差，但拒绝radius当sigma和缩放前模糊。
                assertTrue("屏幕模糊宽度应接近10.25dp，实际${it / unit}", it / unit in 8.8f..11.8f)
            }
        }
        assertTrue("卡片缩小不能让模糊同步缩窄", abs(widths[0] - widths[1]) <= 2)
        compose.runOnIdle { sigma = 0f }
        assertTrue("终态须移除滤镜", edgeWidth(snapshot("filtered_content", "content_blur_cleared"), 150f * unit, unit) <= 2)
    }

    @Test fun sourceSnapshotBlursInScreenSpaceAndLeavesNoResidueWhenHidden() {
        assertPortraitDefaultFontConfiguration()
        var alpha by mutableFloatStateOf(1f)
        var unit = 1f
        val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888).apply {
            android.graphics.Canvas(this).drawRect(30f, 0f, 60f, 60f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
        }.asImageBitmap()
        compose.setContent {
            unit = LocalDensity.current.density
            Box(Modifier.size(300.dp).background(Color.White).testTag("filtered_source")) {
                Box(Modifier.size(300.dp).appMenuMorph(Color.Transparent, {
                    AppMenuMorphFrame(Rect(0f, 0f, 300f * unit, 300f * unit), 0f, 0f,
                        droplet = Rect(90f * unit, 90f * unit, 210f * unit, 210f * unit),
                        sourceAlpha = alpha, sourceBlur = 4f * unit)
                }, source = bitmap))
            }
        }
        val image = snapshot("filtered_source", "source_blur")
        val width = edgeWidth(image, 150f * unit, unit)
        assertTrue("源图必须应用4dp sigma，实际${width / unit}", width / unit in 8.8f..11.8f)
        compose.runOnIdle { alpha = 0f }
        val hidden = snapshot("filtered_source", "source_hidden")
        assertEquals(android.graphics.Color.WHITE, hidden.getPixel((170 * unit).roundToInt(), (150 * unit).roundToInt()))
    }

    private fun edgeWidth(image: Bitmap, y: Float, unit: Float): Int {
        val row = y.roundToInt()
        val from = (130 * unit).roundToInt()
        val to = (170 * unit).roundToInt()
        val light = (from..to).first { android.graphics.Color.red(image.getPixel(it, row)) <= 230 }
        val dark = (light..to).first { android.graphics.Color.red(image.getPixel(it, row)) <= 25 }
        return dark - light
    }

    private fun snapshot(tag: String, name: String): Bitmap {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        return bitmap
    }
}
