package com.lonnnnnng.flclash.ui

import org.junit.Assert.*
import org.junit.Test

/** 作者 long：连续反向不能抹掉速度或跳到端点；独立测这些不变量，避免截图终态掩盖中途闪跳。 */
class ToolbarSearchMotionTest {
    @Test fun reversalKeepsFrameAndVelocityThenReturnsToRest() {
        val motion = ToolbarSearchMotion(false)
        motion.moveTo(true)
        repeat(9) { motion.advance(.016f) }
        val frame = motion.frame
        val velocity = motion.velocity
        assertTrue(frame.growth in .1f.. .99f)
        motion.moveTo(false)
        assertEquals(frame, motion.frame)
        assertEquals(velocity, motion.velocity, 0f)
        repeat(4) { motion.advance(.016f) }
        val reversing = motion.frame
        motion.moveTo(true)
        assertEquals(reversing, motion.frame)
        repeat(120) { motion.advance(.016f) }
        assertEquals(ToolbarSearchFrame.Open, motion.frame)
        assertFalse(motion.moving)
    }

    @Test fun groupedAndLoneReturnsSettleWithoutInvalidGeometryOrAlpha() {
        for (primary in listOf(false, true)) {
            val motion = ToolbarSearchMotion(true, primary)
            motion.moveTo(false, primary)
            repeat(140) {
                motion.advance(.008f)
                val f = motion.frame
                listOf(f.field, f.title, f.actions, f.flank, f.content, f.glyph, f.lead).forEach { assertTrue(it in 0f..1f) }
                val g = searchToolbarGeometry(360f, 1f, primary, primary, f)
                assertTrue(g.field.width >= 0); assertTrue(g.group.width >= 0); assertTrue(g.cancel.width >= 0)
            }
            assertEquals(ToolbarSearchFrame.Closed, motion.frame)
            assertFalse(motion.moving)
        }
    }

    @Test fun settledSearchLeavesSeparatePrimaryAndCancelTargets() {
        for (width in listOf(288f, 360f, 640f)) for (primary in listOf(false, true)) {
            val g = searchToolbarGeometry(width, 1f, primary, primary, ToolbarSearchFrame.Open)
            assertEquals(44f, g.cancel.width, .001f)
            assertEquals(width, g.cancel.right, .001f)
            assertTrue(g.field.right + 11.99f <= g.cancel.left)
            if (primary) { assertEquals(0f, g.primary.left, .001f); assertTrue(g.primary.right + 11.99f <= g.field.left) }
        }
    }

    @Test fun disabledAnimationFinishesLatestIntentAndClearsVelocity() {
        val motion = ToolbarSearchMotion(false)
        motion.moveTo(true); motion.advance(.12f); motion.moveTo(false); motion.finish()
        assertEquals(ToolbarSearchFrame.Closed, motion.frame)
        assertEquals(0f, motion.velocity, 0f)
        motion.moveTo(true); motion.finish()
        assertEquals(ToolbarSearchFrame.Open, motion.frame)
    }
}
