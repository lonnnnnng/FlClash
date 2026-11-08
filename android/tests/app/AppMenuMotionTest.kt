package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AppMenuMotionTest {
    private val anchor = Rect(180f, -40f, 224f, 4f)
    private val card = Size(260f, 380f)

    @Test fun returnTransfersShadowFromContentToPlatterWithoutTintingGlassTwice() {
        val opened = appMenuMorphFrame(anchor, card, .45f, 1f, true)
        val start = appMenuReturnFrame(anchor, opened, 0f, 1f)
        assertEquals(0f, start.outerShadowStrength(false), .00001f)
        assertEquals(1f, start.outerShadowStrength(true), .00001f)
        val textGone = appMenuReturnFrame(anchor, opened, .1f, 1f)
        assertEquals(0f, textGone.progress, .00001f)
        assertTrue(textGone.outerShadowStrength(false) > 0f)
        assertEquals(textGone.outerShadowStrength(true), textGone.outerShadowStrength(false), .00001f)
        val end = appMenuReturnFrame(anchor, opened, .5f, 1f)
        assertEquals(0f, end.outerShadowStrength(false), .00001f)
        assertEquals(0f, end.outerShadowStrength(true), .00001f)
    }

    @Test fun flutterShadowSigmaIsPreservedAtDifferentPixelDensities() {
        for (density in listOf(1f, 2f, 2.75f, 3.5f)) {
            val androidSigma = appMenuShadowRadiusPx(density) * .57735f + .5f
            // 作者 long：固定Flutter的blurRadius=29对应17.24315逻辑像素sigma；不能把29的一半当模糊标准差。
            assertEquals(17.24315f, androidSigma / density, .00001f)
        }
    }

    @Test fun nestedReturnKeepsTopCardGeometryAndUniformContentScale() {
        val top = Rect(20f, 48f, 280f, 188f)
        val from = AppMenuMorphFrame(top, 24f, 1f, contentBounds = top)
        val first = appMenuReturnFrame(anchor, from, 0f, 1f)
        assertEquals(top, first.platter)
        assertEquals(top, first.contentBounds)
        assertEquals(1f, first.contentScale(Size(280f, 420f)), .00001f)
        val partial = appMenuReturnFrame(anchor, from, .06f, 1f)
        assertEquals(top, partial.contentBounds)
        val scale = partial.contentScale(Size(280f, 420f))
        assertTrue(top.width * scale <= partial.platter.width + .00001f)
        assertTrue(top.height * scale <= partial.platter.height + .00001f)
    }

    @Test fun returnStartsAtInterruptedPlatterAndEndsExactlyAtSource() {
        for (time in listOf(.04f, .12f)) {
            val from = appMenuMorphFrame(anchor, card, time, 1f, true)
            val first = appMenuReturnFrame(anchor, from, 0f, 1f)
            assertEquals(from.platter.left, first.platter.left, .0001f)
            assertEquals(from.platter.top, first.platter.top, .0001f)
            assertEquals(from.platter.right, first.platter.right, .0001f)
            assertEquals(from.platter.bottom, first.platter.bottom, .0001f)
            assertEquals(from.blend, first.blend, .0001f)
            assertEquals(from.blur, first.blur, .0001f)
            assertEquals(from.sourceAlpha, first.sourceAlpha, .0001f)
            assertEquals(from.sourceBlur, first.sourceBlur, .0001f)
            val last = appMenuReturnFrame(anchor, from, .5f, 1f)
            assertEquals(anchor, last.platter); assertEquals(anchor, last.droplet)
            assertEquals(0f, last.progress); assertEquals(1f, last.sourceAlpha)
        }
    }

    @Test fun settledReturnUsesFlutterRevealGlyphAndAbsorptionCurves() {
        val from = appMenuMorphFrame(anchor, card, .45f, 1f, true)
        val start = appMenuReturnFrame(anchor, from, 0f, 1f)
        assertEquals(.2f, start.sourceAlpha, .00001f)
        assertEquals(0f, start.blend, .00001f)
        val middle = appMenuReturnFrame(anchor, from, .05f, 1f)
        // 作者 long：数值由固定上游的三次贝塞尔独立求根得到，防止误换成Material同名近似曲线。
        assertEquals(.3153568f, middle.progress, .0001f)
        assertEquals(.2838922f, middle.sourceAlpha, .0001f)
        assertEquals(5.4771457f, middle.blur, .001f)
        assertEquals(28f, middle.blend, .0001f)
        val absorbed = appMenuReturnFrame(anchor, from, .35f, 1f)
        assertEquals(.7682252f, absorbed.frostProgress, .0001f)
    }

    @Test fun capsuleKeepsItsLongAxisWhenMeetingTheSource() {
        for (density in listOf(1f, 2.75f)) {
            for (horizontal in listOf(true, false)) {
                val control = if (horizontal) Rect(0f, 0f, 120f * density, 40f * density)
                    else Rect(0f, 0f, 40f * density, 120f * density)
                val initial = appMenuMorphFrame(control, card * density, 0f, density, true)
                assertEquals(96.4f * density, initial.platter.size.maxDimension, .001f)
                assertEquals(16.4f * density, initial.platter.size.minDimension, .001f)
                val opened = appMenuMorphFrame(control, card * density, .45f, density, true)
                val returning = appMenuReturnFrame(control, opened, .49f, density)
                assertTrue("胶囊回源不能坍成圆形", returning.platter.size.maxDimension > returning.platter.size.minDimension + 75f * density)
                assertEquals(control, appMenuReturnFrame(control, opened, .5f, density).droplet)
            }
        }
    }

    @Test fun opticalReturnOutlivesTextAndInterruptionsKeepMaterialContinuous() {
        for (time in listOf(.04f, .12f, .45f)) {
            val from = appMenuMorphFrame(anchor, card, time, 1f, true)
            val first = appMenuReturnFrame(anchor, from, 0f, 1f)
            assertEquals(from.materialProgress, first.materialProgress, .00001f)
            assertEquals(from.frostProgress, first.frostProgress, .00001f)
            val textGone = appMenuReturnFrame(anchor, from, .1f, 1f)
            assertEquals(0f, textGone.progress, .00001f)
            assertTrue(textGone.materialProgress > 0f)
            assertEquals(from.frostProgress, textGone.frostProgress, .00001f)
            val absorbed = appMenuReturnFrame(anchor, from, .5f, 1f)
            assertEquals(0f, absorbed.materialProgress, .00001f)
            assertEquals(0f, absorbed.frostProgress, .00001f)
        }
    }

    @Test fun materialStrengthPreservesTintAndEdgeGeometryDuringUnfold() {
        val source = AppGlassStyle.pill(true)
        val partial = source.strength(.35f)
        assertEquals(source.tint.red, partial.tint.red, .00001f)
        assertEquals(source.tint.green, partial.tint.green, .00001f)
        assertEquals(source.bevel, partial.bevel, 0f)
        assertEquals(source.rimWidth, partial.rimWidth, 0f)
        assertEquals(source.hazeBlur, partial.hazeBlur, 0f)
        assertEquals(source.refraction * .35f, partial.refraction, .00001f)
        assertEquals(source, source.strength(1f))
    }

    @Test fun standardQualityKeepsLensRefractionAndRimWhileMergingPillHaze() {
        for (dark in listOf(false, true)) {
            val lens = AppGlassStyle.lens(dark)
            val reduced = lens.effective(AppGlassQuality.STANDARD)
            assertEquals(lens.refraction, reduced.refraction, 0f)
            assertEquals(lens.rim, reduced.rim, 0f)
            assertEquals(0f, reduced.dispersion, 0f)
            val pill = AppGlassStyle.pill(dark)
            val flat = pill.effective(AppGlassQuality.STANDARD)
            assertEquals(pill.hazeBlur, flat.blur, 0f)
            assertEquals(0f, flat.haze, 0f)
            assertEquals(pill.tint, flat.tint)
            assertEquals(pill, pill.effective(AppGlassQuality.ULTRA))
            val menu = AppGlassStyle.menu(dark, Color.White)
            assertEquals(pill, pill.interpolate(menu, 0f))
            assertEquals(menu, pill.interpolate(menu, 1f))
        }
    }

    @Test fun submenuClipGrowsFromOwnerWithoutScalingContentHeight() {
        val size = Size(280f, 400f)
        assertEquals(Rect(8f, 8f, 272f, 56f), appMenuStackRect(size, 120f, 128f, 48f, 0f, 1f))
        assertEquals(Rect(Offset.Zero, size), appMenuStackRect(size, 120f, 128f, 48f, 1f, 1f))
        val over = appMenuStackRect(size, 120f, 128f, 48f, 1.02f, 1f)
        assertTrue(over.bottom > size.height)
    }

    @Test fun returnHasFiniteGeometryAtEveryFrameInBothDirections() {
        for (source in listOf(anchor, anchor.translate(Offset(0f, 480f)))) {
            val from = appMenuMorphFrame(source, card, .45f, 1f, true)
            for (frame in 0..30) {
                val value = appMenuReturnFrame(source, from, frame / 60f, 1f)
                assertTrue(value.platter.width >= 0f && value.platter.height >= 0f)
                assertTrue(value.radius.isFinite() && value.radius >= 0f)
                assertTrue(value.progress in 0f..1f && value.sourceAlpha in 0f..1f)
            }
        }
    }

    @Test fun pointerTapAndCancellationNeverReleaseSelection() {
        val carry = AppMenuCarry()
        carry.start(Offset.Zero); carry.move(Offset(2f, 2f), 8f); carry.finish()
        assertEquals(0, carry.release)
        carry.start(Offset.Zero); carry.move(Offset(20f, 20f), 8f); carry.cancel(); carry.finish()
        assertEquals(0, carry.release); assertFalse(carry.dragging)
    }

    @Test fun pointerDragReleasesOnlyOnce() {
        val carry = AppMenuCarry()
        carry.start(Offset.Zero); carry.move(Offset(20f, 20f), 8f)
        assertTrue(carry.dragging)
        carry.finish(); carry.finish()
        assertEquals(1, carry.release)
        carry.cancel()
        assertEquals(Offset(20f, 20f), carry.releasedPosition)
        carry.start(Offset.Zero)
        assertNull(carry.releasedPosition)
    }

    @Test fun yamlDecorationPreservesEveryCharacterAndDoesNotExpandLargeInput() {
        val input = "# 注释\nproxies:\n  - name: '中😀'\n    port: 443\n    udp: true\n"
        val decorated = profileYamlText(input, Color.Blue, Color.Gray, Color.Green)
        assertEquals(input, decorated.text); assertTrue(decorated.spanStyles.isNotEmpty())
        val large = input.repeat(12_000)
        val plain = profileYamlText(large, Color.Blue, Color.Gray, Color.Green)
        assertEquals(large, plain.text); assertTrue(plain.spanStyles.isEmpty())
    }
}
