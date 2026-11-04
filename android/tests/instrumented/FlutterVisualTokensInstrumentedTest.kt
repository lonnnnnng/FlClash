package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 作者 long：图集固定为上游oracle的3倍24单位网格；真实Android渲染与Flutter输出独立比较，不能只用更新自己的哈希证明对齐。 */
class FlutterVisualTokensInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun glyphAtlasUsesFlutterPathsAndTransparentFilledDetails() {
        assertPortraitDefaultFontConfiguration()
        val glyphs = listOf(AppGlyphs.dashboard, AppGlyphs.proxies, AppGlyphs.profiles, AppGlyphs.tools,
            AppGlyphs.sync, AppGlyphs.sort, AppGlyphs.addCircle, AppGlyphs.search, AppGlyphs.bolt, AppGlyphs.more,
            AppGlyphs.check, AppGlyphs.checkCircle, AppGlyphs.close, AppGlyphs.clearField, AppGlyphs.arrowBack,
            AppGlyphs.chevronBack, AppGlyphs.chevronForward, AppGlyphs.chevronDown, AppGlyphs.chevronUp,
            AppGlyphs.refresh, AppGlyphs.locate, AppGlyphs.edit, AppGlyphs.delete)
        var filled by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                Column(Modifier.size(240.dp, 160.dp).background(Color(0xFFEEEEEE)).testTag("glyph_atlas")) {
                    repeat(4) { row -> Row {
                        repeat(6) { col -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            glyphs.getOrNull(row * 6 + col)?.let { AppGlyphIcon(it, Modifier.size(24.dp), Color(0xFF007AFF), filled) }
                        } }
                    } }
                }
            }
        }
        for (mode in listOf(false, true)) {
            compose.runOnIdle { filled = mode }
            val bitmap = compose.onNodeWithTag("glyph_atlas").captureToImage().asAndroidBitmap()
            recordVisualPng("glyphs_$mode", ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        }
    }

    @Test fun anchoredMenuPaintsDistinctIntermediateFramesAndCanCloseDuringOpening() {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        var opened by mutableStateOf(false); var anchor by mutableStateOf(IntRect.Zero); var actions = 0
        compose.setContent { KClashTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Button({ opened = true }, Modifier.align(Alignment.TopEnd).testTag("morph_open")
                    .onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() }) { Text("菜单") }
                AppDropdownMenu(opened, { opened = false }, anchor = anchor) {
                    repeat(4) { index -> AppDropdownMenuItem({ Text("菜单项 $index") }, { actions++; opened = false }) }
                }
            }
        } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("morph_open").performClick(); compose.mainClock.advanceTimeByFrame()
        val hashes = mutableSetOf<Int>()
        repeat(4) { frame ->
            compose.mainClock.advanceTimeBy(64)
            val bitmap = compose.onNodeWithTag("app_popup_window").captureToImage().asAndroidBitmap()
            val bytes = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            recordVisualPng("menu_morph_$frame", bytes); hashes += bytes.contentHashCode()
        }
        assertTrue("形变中间帧不能退化为同一静态画面", hashes.size >= 3)
        compose.runOnUiThread { opened = false }
        compose.mainClock.advanceTimeBy(600); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        assertEquals(0, actions)
        compose.onNodeWithTag("morph_open").performClick()
        compose.onNodeWithText("菜单项 3").performClick()
        assertEquals(1, actions)
    }
}
