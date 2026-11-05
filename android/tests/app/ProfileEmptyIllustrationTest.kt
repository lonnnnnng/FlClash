package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 作者 long：验证三套空态编排共享终帧，同时确认 morph 与 orbit 的中间几何确实发生变化。 */
class ProfileEmptyIllustrationTest {
    @Test fun everyEntranceSettlesToTheSameShapeAndVisibleAccents() {
        for (entrance in EmptyIllustrationEntrance.values()) {
            val pose = emptyIllustrationPose(entrance, 1f)
            assertEquals(1f, pose.haloScale, .0001f)
            assertEquals(1f, pose.bodyScale, .0001f)
            assertEquals(0f, pose.haloRotation, .0001f)
            assertEquals(0f, pose.bodyRotation, .0001f)
            assertEquals(1f, pose.bodyMorph, .0001f)
            assertEquals(1f, pose.glyphScale, .0001f)
            assertEquals(1f, pose.glyphFill, .0001f)
            assertEquals(1f, pose.glyphAlpha, .0001f)
            assertTrue(pose.accentScale.all { it == 1f })
            assertTrue(pose.accentDrift.all { it == 1f })
            assertTrue(pose.accentOrbit.all { it == 0f })
            assertTrue(pose.accentRotation.all { it == 0f })
        }
    }

    @Test fun morphCrossfadesFromCircleAndOrbitTravelsAccentsBeforeSettling() {
        val morph = emptyIllustrationPose(EmptyIllustrationEntrance.MORPH, .35f)
        assertTrue(morph.bodyMorph in 0f..1f)
        assertTrue(morph.bodyMorph < 1f)
        assertTrue(morph.bodyRotation > 0f)
        assertTrue(morph.glyphRise > 0f)

        val orbit = emptyIllustrationPose(EmptyIllustrationEntrance.ORBIT, .45f)
        assertTrue(orbit.haloAlpha in 0f..1f)
        assertTrue(orbit.accentOrbit.any { it < 0f })
        assertTrue(orbit.accentDrift.any { it < 1f })
        // 作者 long：弹性入场允许主体短暂过冲，验证已离开初始缩放即可，不把合法过冲误判为失败。
        assertTrue(orbit.bodyScale > .3f)
    }
}
