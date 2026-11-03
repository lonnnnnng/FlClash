package com.lonnnnnng.flclash.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** 作者 long：手势连续改目标与短按松手是物理动画最容易退化的两条路径，独立于截图验证。 */
class AppSurfacePhysicsTest {
    @Test fun retargetKeepsSpeedAndMovesOnEveryFrame() {
        val spring = AppSpring(0f, 311f, .593f)
        spring.to(1f); spring.advance(.016f)
        val first = spring.value; val speed = spring.velocity
        spring.to(2f)
        assertEquals(speed, spring.velocity, 0f)
        spring.advance(.016f); assertTrue(spring.value > first)
        repeat(20) { spring.to(2f + it / 20f); val before = spring.value; spring.advance(.016f); assertTrue(spring.value > before) }
        repeat(240) { spring.advance(.016f) }
        assertFalse(spring.moving)
    }

    @Test fun shortTapRisesBeforeSettlingAndResetClearsPull() {
        val press = ElasticPressState(AppSpring(0f, 311f, .593f), AppSpring(0f, 311f, .593f), AppSpring(0f, 311f, .593f))
        press.press(); press.release()
        assertEquals(1f, press.lift.target, 0f)
        var peak = 0f
        repeat(240) { press.lift.advance(.008f); peak = maxOf(peak, press.lift.value) }
        assertTrue(peak >= .975f); assertEquals(0f, press.lift.value, .001f)
        press.pull(androidx.compose.ui.geometry.Offset(40f, -10f), 20f)
        press.reset(); assertFalse(press.x.moving); assertFalse(press.y.moving)
    }

    @Test fun dockGeometryKeepsThreeEntriesCenteredAndMirrorsRtl() {
        for (count in 3..5) {
            val geometry = DockGeometry(274f, 62f, count)
            assertTrue(geometry.lensWidth >= 84f)
            repeat(count) { index ->
                val center = geometry.itemLeft(index, false) + geometry.pitch / 2
                assertEquals(index.toFloat(), geometry.position(center, false), .001f)
                assertEquals(index.toFloat(), geometry.position(geometry.width - center, true), .001f)
                assertEquals(62f - 8f, geometry.lens(index.toFloat(), 0f, 0f, false).height, .001f)
            }
        }
        assertTrue(abs(rubberBand(1000f, 20f)) < 20f)
        assertEquals(-rubberBand(50f, 20f), rubberBand(-50f, 20f), .001f)
    }

    @Test fun dockCancelReturnsToSelectionAndShortReleaseDropsLift() {
        val motion = DockMotion(AppSpring(0f, springStiffness(400), .85f), AppSpring(0f, springStiffness(250), 1f),
            AppSpring(0f, 311f, .593f), AppSpring(0f, 311f, .593f), AppSpring(0f, 100f, .4f))
        motion.press(2); motion.slide(1.4f, 1, 0f, 10f); motion.release(0)
        repeat(600) { motion.lens.advance(.008f); motion.lift.advance(.008f); motion.jelly.advance(.008f) }
        assertEquals(0f, motion.lift.value, .001f); assertEquals(0f, motion.lens.value, .001f)
        assertNull(motion.pressedIndex)
    }
}
