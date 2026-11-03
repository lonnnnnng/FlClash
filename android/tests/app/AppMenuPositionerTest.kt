package com.lonnnnnng.flclash.ui

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：定位直接验证Flutter的避让和固定规则，避免截图只覆盖一种恰好放得下的锚点。 */
class AppMenuPositionerTest {
    private val screen = IntSize(400, 900)
    private val safe = AppMenuInsets(0, 24, 0, 32)
    @Test fun longPressPrefersAboveRowAndOtherwiseBelowIt() {
        val row = IntRect(20, 400, 380, 500)
        assertEquals(IntOffset(120, 192), AppMenuPositioner(16, 8).position(IntRect(120, 450, 120, 450), screen,
            IntSize(240, 200), safe, AppMenuPlacement.BELOW_POINT, row))
        assertEquals(IntOffset(120, 148), AppMenuPositioner(16, 8).position(IntRect(120, 100, 120, 100), screen,
            IntSize(240, 200), safe, AppMenuPlacement.BELOW_POINT, IntRect(20, 40, 380, 140)))
    }
    @Test fun overflowingPointFlipsAboveAndHonorsKeyboardInset() {
        assertEquals(IntOffset(144, 500), AppMenuPositioner(16, 8).position(IntRect(390, 800, 390, 800), screen,
            IntSize(240, 300), safe, AppMenuPlacement.BELOW_POINT))
        assertEquals(IntOffset(144, 334), AppMenuPositioner(16, 8).position(IntRect(390, 800, 390, 800), screen,
            IntSize(240, 300), safe.copy(bottom = 250), AppMenuPlacement.OVER_ANCHOR_END))
    }
    @Test fun submenuKeepsTopRightAndRechecksChangedWindowIdentity() {
        val layout = AppMenuPositioner(16, 8); val anchor = IntRect(340, 60, 388, 108)
        assertEquals(IntOffset(184, 52), layout.position(anchor, screen, IntSize(200, 350), safe, AppMenuPlacement.OVER_ANCHOR_END))
        assertEquals(IntOffset(104, 52), layout.position(anchor, screen, IntSize(280, 180), safe, AppMenuPlacement.OVER_ANCHOR_END))
        assertEquals(IntOffset(16, 52), layout.position(IntRect(16, 60, 64, 108), screen, IntSize(200, 180), safe, AppMenuPlacement.OVER_ANCHOR_END))
    }
    @Test fun topToolbarMayUseSafeTopWithoutAddingMarginTwice() {
        assertEquals(IntOffset(144, 24), AppMenuPositioner(16, 8).position(IntRect(340, 24, 388, 72), screen,
            IntSize(240, 300), safe, AppMenuPlacement.OVER_ANCHOR_END))
    }
    @Test fun allEdgesAndImeKeepMeasuredMenusWithinSafeViewport() {
        for (bottom in listOf(32, 300)) for (x in listOf(0, 200, 399)) for (y in listOf(24, 450, 880)) {
            val inset = safe.copy(left = 12, right = 18, bottom = bottom)
            for (placement in AppMenuPlacement.entries) {
                val p = AppMenuPositioner(16, 8).position(IntRect(x, y, x, y), screen, IntSize(240, 200), inset, placement)
                assertTrue(p.x >= 28 && p.x + 240 <= 366)
                assertTrue(p.y >= 24 && p.y + 200 <= 900 - bottom - 16)
            }
        }
    }
}
