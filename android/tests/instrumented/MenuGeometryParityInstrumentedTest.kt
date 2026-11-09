package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** 作者 long：固定开关时刻同时留存主体、液滴、阴影和正文，优化前后逐像素对照，不能只看终态掩盖中途轮廓变化。 */
class MenuGeometryParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingAndReturningGeometryInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var closing by mutableStateOf(false)
        var milliseconds by mutableIntStateOf(20)
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                val unit = LocalDensity.current.density
                val card = Size(300 * unit, 340 * unit)
                val measured = remember(unit) { mutableStateOf(IntSize(card.width.roundToInt(), card.height.roundToInt())) }
                val anchor = Rect(250 * unit, -44 * unit, 298 * unit, 4 * unit)
                val frame = remember(unit, closing, milliseconds) {
                    if (closing) appMenuReturnFrame(anchor, appMenuMorphFrame(anchor, card, .45f, unit, true), milliseconds / 1000f, unit)
                    else appMenuMorphFrame(anchor, card, milliseconds / 1000f, unit, true)
                }
                val surface = if (dark) Color(0xFF343537) else Color(0xFFF8F8FA)
                Box(Modifier.size(340.dp, 440.dp).background(if (dark) Color(0xFF191A1D) else Color.White).testTag("geometry_frames")) {
                    Box(Modifier.offset(20.dp, 60.dp).size(300.dp, 340.dp)) {
                        AppMenuPlatterShadow(Modifier.matchParentSize(), measured, { frame }, { true }, dark, false)
                        Column(Modifier.fillMaxSize().appMenuMorph(surface, { frame }, sourceColor = Color(0xFF496CA9))) {
                            Text("代理菜单", Modifier.padding(20.dp))
                            Text("样式：标签", Modifier.padding(20.dp))
                            Text("打开与返回保持连续", Modifier.padding(20.dp))
                        }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (returning in listOf(false, true)) {
            for (time in if (returning) listOf(20, 70, 140, 250, 400) else listOf(20, 70, 140, 250, 450)) {
                compose.runOnIdle { dark = theme; closing = returning; milliseconds = time }
                val bitmap = compose.onNodeWithTag("geometry_frames").captureToImage().asAndroidBitmap()
                recordVisualPng("geometry_${theme}_${returning}_$time", ByteArrayOutputStream().also {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }.toByteArray())
            }
        }
    }
}
