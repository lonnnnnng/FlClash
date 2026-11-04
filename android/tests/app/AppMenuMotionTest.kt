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

    @Test fun returnStartsAtInterruptedPlatterAndEndsExactlyAtSource() {
        for (time in listOf(.04f, .12f, .45f)) {
            val from = appMenuMorphFrame(anchor, card, time, 1f, true)
            val first = appMenuReturnFrame(anchor, from, 0f, 1f)
            assertEquals(from.platter.left, first.platter.left, .0001f)
            assertEquals(from.platter.top, first.platter.top, .0001f)
            assertEquals(from.platter.right, first.platter.right, .0001f)
            assertEquals(from.platter.bottom, first.platter.bottom, .0001f)
            val last = appMenuReturnFrame(anchor, from, .5f, 1f)
            assertEquals(anchor, last.platter); assertEquals(anchor, last.droplet)
            assertEquals(0f, last.progress); assertEquals(1f, last.sourceAlpha)
        }
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
