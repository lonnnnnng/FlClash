package com.lonnnnnng.flclash.ui

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class ProfileDocumentHistoryTest {
    @Test fun clippedFindSelectionReplacementDeletesWholeMatchAndMapsImeComposition() {
        val original = "x".repeat(4090) + "ABCDEFGHIJKLMNOPQRST" + "x".repeat(66000)
        val full = TextFieldValue(original, TextRange(4090, 4110))
        val next = replaceProfileCodeWindow(full, 0, 4096,
            TextFieldValue("x".repeat(4090) + "Z", TextRange(4091), TextRange(4090, 4091)))
        assertEquals("x".repeat(4090) + "Z" + "x".repeat(66000), next.text)
        assertEquals(TextRange(4091), next.selection)
        assertEquals(TextRange(4090, 4091), next.composition)
        val longSelection = TextFieldValue(original, TextRange(4110, 0))
        val replaced = replaceProfileCodeWindow(longSelection, 0, 4096, TextFieldValue("Z", TextRange(1)))
        assertEquals("Z" + "x".repeat(66000), replaced.text)
    }

    @Test fun upperLimitSingleCharacterEditsKeepMultipleUndosWithoutWholeDocumentCopies() {
        val original = "x".repeat(8 * 1024 * 1024 - 4) + "😀\r\n"
        val next = original.dropLast(2) + "a\r\n"
        val last = next.dropLast(2) + "b\r\n"
        val history = ProfileDocumentHistory()
        history.record(original, next); history.record(next, last)
        assertTrue(history.retainedCharacters < 10)
        assertTrue(history.undo(last) == next)
        assertTrue(history.undo(next) == original)
        assertFalse(history.canUndo)
        assertTrue(history.redo(original) == next)
        history.record(next, "replaced")
        assertFalse(history.canRedo)
        assertTrue(history.undo("replaced") == next)
    }

    @Test fun budgetEvictsOldestButRetainsOneLargeReplacementAndReplaysDeletion() {
        val history = ProfileDocumentHistory(budget = 4, limit = 2)
        history.record("abc", "abcd"); history.record("abcd", "abcde"); history.record("abcde", "abcdef")
        assertEquals("abcde", history.undo("abcdef")); assertEquals("abcd", history.undo("abcde")); assertFalse(history.canUndo)
        history.record("abcd", "a".repeat(100))
        assertEquals("abcd", history.undo("a".repeat(100)))
        assertEquals("a".repeat(100), history.redo("abcd"))
        history.record("a".repeat(100), "")
        assertEquals("a".repeat(100), history.undo(""))
    }

    @Test fun windowsCoverExactBytesAndNeverSplitSurrogatesOrCrLf() {
        for (source in listOf("😀\r\n".repeat(5000), "a".repeat(9000), "\n".repeat(9000))) {
            val chunks = mutableListOf<String>()
            var start = 0
            while (start < source.length) {
                val end = profileCodeWindowEnd(source, start)
                assertTrue(end > start && end - start <= PROFILE_CODE_WINDOW_CHARS + 1)
                if (end < source.length) {
                    assertFalse(source[end].isLowSurrogate())
                    assertFalse(source[end - 1] == '\r' && source[end] == '\n')
                }
                chunks += source.substring(start, end); start = end
            }
            assertEquals(source, chunks.joinToString(""))
            var end = source.length
            val reverse = mutableListOf<String>()
            while (end > 0) {
                val previous = profileCodeWindowBefore(source, end)
                assertTrue(previous < end)
                reverse += source.substring(previous, end); end = previous
            }
            assertEquals(source, reverse.asReversed().joinToString(""))
        }
    }
}
