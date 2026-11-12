package com.lonnnnnng.flclash.ui

/** 作者 long：历史只存变化的区间，避免大正文每按一次键都保留整串；超预算的单次整篇替换仍允许撤销一次。 */
internal class ProfileDocumentHistory(private val budget: Int = 2_000_000, private val limit: Int = 20) {
    private class Edit(val start: Int, val removed: String, val inserted: String) {
        val size: Long get() = removed.length.toLong() + inserted.length
    }
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    internal val retainedCharacters get() = (undo.asSequence() + redo.asSequence()).sumOf { it.size }

    fun record(before: String, after: String) {
        if (before == after) return
        var start = 0
        val shortest = minOf(before.length, after.length)
        while (start < shortest && before[start] == after[start]) start++
        var oldEnd = before.length
        var newEnd = after.length
        while (oldEnd > start && newEnd > start && before[oldEnd - 1] == after[newEnd - 1]) { oldEnd--; newEnd-- }
        redo.clear()
        undo.addLast(Edit(start, before.substring(start, oldEnd), after.substring(start, newEnd)))
        while (undo.size > 1 && (undo.size > limit || retainedCharacters > budget)) undo.removeFirst()
    }

    fun undo(text: String): String {
        val edit = undo.removeLast()
        redo.addLast(edit)
        return text.replaceRange(edit.start, edit.start + edit.inserted.length, edit.removed)
    }

    fun redo(text: String): String {
        val edit = redo.removeLast()
        undo.addLast(edit)
        return text.replaceRange(edit.start, edit.start + edit.removed.length, edit.inserted)
    }
}
