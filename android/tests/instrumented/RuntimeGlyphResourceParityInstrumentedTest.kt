package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 作者 long：字形使用独立Flutter光栅作oracle；资源浮框仅持有本例输入，不向示例地址请求或写正式设置。 */
class RuntimeGlyphResourceParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        recordVisualPng("vis072_$name", ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }

    @Test fun playPauseAndSuspendedGlyphsMatchEightFlutterRasters() {
        val cases = javaClass.getResourceAsStream("/runtime-glyph-flutter-reference.json")!!.bufferedReader()
            .use { JsonParser.parseReader(it).asJsonArray }
        for (item in cases) {
            val p = item.asJsonObject["progress"].asFloat
            val mask = item.asJsonObject["mask"].asJsonArray.map { it.asString }
            val bitmap = ImageBitmap(96, 96)
            val painter = AppGlyphPainter({ if (p == 2f) runtimeWifiOffGlyph else runtimePlayPauseGlyph(p) }, 1f)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(96f, 96f)) { with(painter) { draw(size) } }
            val raster = bitmap.asAndroidBitmap()
            var mismatches = 0
            repeat(96) { y -> repeat(96) { x ->
                val actual = android.graphics.Color.alpha(raster.getPixel(x, y)) >= 128
                if (actual != (mask[y][x] == '1')) {
                    mismatches++
                    // 作者 long：仅允许边缘1物理像素的不同栅格器取整，内部丢线/圆角错误不能用整体相似度掩盖。
                    assertTrue("progress=$p pixel=$x,$y", (maxOf(0,y-1)..minOf(95,y+1)).any { yy ->
                        (maxOf(0,x-1)..minOf(95,x+1)).any { xx -> (mask[yy][xx]=='1') == actual }
                    })
                }
            } }
            assertTrue("progress=$p mismatches=$mismatches", mismatches < 180)
            recordVisualPng("vis072_glyph_${(p*100).toInt().toString().replace('-', 'n')}", ByteArrayOutputStream().also {
                raster.compress(Bitmap.CompressFormat.PNG,100,it)
            }.toByteArray())
        }
    }

    @Test fun glyphAnimationDrawsReversesAndSuspendsWithoutChangingActions() {
        assertPortraitDefaultFontConfiguration()
        var running by mutableStateOf(false); var suspended by mutableStateOf(false); var busy by mutableStateOf(false)
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalContentColor provides Color.White) { Box(Modifier.size(88.dp).background(Color.Black).testTag("glyph_frame")) {
            RuntimeToggleGlyph(running, suspended, busy, Modifier.size(88.dp))
        } } } }
        fun pixels(): IntArray {
            val b=compose.onNodeWithTag("glyph_frame").captureToImage().asAndroidBitmap()
            return IntArray(b.width*b.height).also { b.getPixels(it,0,b.width,0,0,b.width,b.height) }
        }
        val play=pixels()
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { running=true };compose.mainClock.advanceTimeBy(160)
        assertFalse(play.contentEquals(pixels()))
        compose.runOnIdle { running=false };compose.mainClock.advanceTimeBy(600)
        assertArrayEquals(play,pixels())
        compose.runOnIdle { running=true;busy=true };compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("runtime_toggle_busy").assertExists()
        compose.runOnIdle { busy=false;suspended=true };compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("runtime_toggle_suspended").assertExists()
        val paused=pixels();compose.mainClock.advanceTimeBy(1000);assertArrayEquals(paused,pixels())
        compose.runOnIdle { suspended=false };compose.mainClock.advanceTimeBy(32)
        assertFalse(play.contentEquals(pixels()))
        compose.mainClock.autoAdvance=true
    }

    @Test fun resourcePanelsCaptureSameValuesInLightAndDarkAndKeepReturnContracts() {
        prepareTouchVisualCapture()
        var dark by mutableStateOf(true)
        var kind by mutableStateOf<String?>(null)
        var interval="24";var address="https://example.invalid/country.mmdb"
        val default="https://example.invalid/default.mmdb"
        compose.setContent { KClashTheme(darkTheme=dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { Text("资源参考") }
            when(kind) {
                "interval" -> ResourceInputDialog("资源更新周期",interval,"小时",null,{kind=null},{
                    require((it.toIntOrNull() ?: 0)>0) { "请输入有效小时数" };interval=it
                },numeric=true)
                "geo" -> ResourceInputDialog("MMDB",address,"下载地址",default,{kind=null},{
                    require(it.startsWith("https://")) { "请输入有效网址" };address=it
                })
            }
        } }
        for (theme in listOf(true,false)) {
            val label=if(theme) "dark" else "light"
            compose.runOnIdle { dark=theme;kind="interval";interval="24";address="https://example.invalid/country.mmdb" }
            capture("${label}_interval")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("48")
            capture("${label}_interval_ime")
            compose.onNodeWithTag("resource_setting_input").performImeAction()
            compose.onNodeWithTag("resource_setting_dialog").assertDoesNotExist()
            compose.runOnIdle { assertEquals("48",interval);kind="interval" }
            compose.onNodeWithTag("resource_setting_input").assertTextEquals("48")
            compose.onNodeWithTag("resource_setting_cancel").performClick()
            compose.onNodeWithTag("resource_setting_dialog").assertDoesNotExist()
            compose.runOnIdle { kind="geo" }
            capture("${label}_geo")
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("https://example.invalid/edited.mmdb")
            capture("${label}_geo_ime")
            androidx.test.espresso.Espresso.pressBack()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("resource_setting_dialog").assertDoesNotExist()
            compose.runOnIdle { assertEquals("https://example.invalid/country.mmdb",address);kind="geo" }
            compose.onNodeWithTag("resource_setting_reset").performClick()
            compose.onNodeWithTag("resource_setting_dialog").assertDoesNotExist()
            compose.runOnIdle { assertEquals(default,address) }
        }
    }
}
