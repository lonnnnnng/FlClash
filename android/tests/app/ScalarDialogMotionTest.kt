package com.lonnnnnng.flclash.ui

import org.junit.Assert.*
import org.junit.Test

/** 作者 long：数值来自固定Flutter物理参数的独立采样；区分完整退出与提前反转，防止套用同一淡出曲线。 */
class ScalarDialogMotionTest {
    @Test fun flutterEntrySamplesAndSettledScaleMatch() {
        val motion = ScalarDialogMotion()
        assertEquals(0f, motion.alpha, 0f); assertEquals(1.2f, motion.scale, 0f)
        motion.advance(.1f)
        assertEquals(.66587261f, motion.alpha, .00001f); assertEquals(1.06682548f, motion.scale, .00001f)
        motion.advance(.102f); assertEquals(.94491356f, motion.alpha, .00001f)
        motion.advance(.202f); assertEquals(1f, motion.alpha, .00001f); assertEquals(1f, motion.scale, .00001f)
        assertFalse(motion.moving)
    }

    @Test fun settledExitFadesWithHeldScale() {
        val motion = ScalarDialogMotion(); motion.finish(); motion.close(); motion.advance(.1f)
        assertEquals(.33412739f, motion.alpha, .00001f); assertEquals(1f, motion.scale, 0f)
        motion.advance(.304f); assertEquals(0f, motion.alpha, .00001f); assertFalse(motion.moving)
    }

    @Test fun interruptedEntryRetainsForwardCurveAndIgnoresRepeatedClose() {
        val motion = ScalarDialogMotion(); motion.advance(.1f)
        val beforeAlpha = motion.alpha; val beforeScale = motion.scale
        motion.close(); assertEquals(beforeAlpha, motion.alpha, 0f)
        motion.advance(.05f); motion.close()
        assertEquals(.3165853f, motion.alpha, .00001f)
        assertEquals(beforeScale, motion.scale, 0f)
        motion.advance(.05f); assertEquals(0f, motion.alpha, .00001f); assertFalse(motion.moving)
    }

    @Test fun disabledMotionAndRestoredClosingFinishWithoutReentering() {
        val motion = ScalarDialogMotion(); motion.finish(); assertFalse(motion.moving)
        motion.close(); motion.finish(); assertEquals(0f, motion.alpha, 0f)
        val restored = ScalarDialogMotion(restoredClosing = true)
        assertTrue(restored.closing); assertFalse(restored.moving); assertEquals(0f, restored.alpha, 0f)
    }
}
