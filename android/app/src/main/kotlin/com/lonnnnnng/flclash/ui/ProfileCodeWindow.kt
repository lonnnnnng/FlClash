package com.lonnnnnng.flclash.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal const val PROFILE_CODE_WINDOW_CHARS = 4096

/** 作者 long：同时约束字符与行数，单个超长行和大量空行都不能突破 Compose 的排版高度；边界保持 UTF-16/CRLF 完整。 */
internal fun profileCodeWindowEnd(text: String, start: Int): Int {
    var end = start
    var lines = 0
    while (end < text.length && end - start < PROFILE_CODE_WINDOW_CHARS && lines < 128) {
        if (text[end] == '\n' || text[end] == '\r' && text.getOrNull(end + 1) != '\n') lines++
        end++
    }
    if (end < text.length && end > start && (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate() || text[end - 1] == '\r' && text[end] == '\n')) end++
    return end
}

internal fun profileCodeWindowBefore(text: String, end: Int): Int {
    var start = end
    var lines = 0
    while (start > 0 && end - start < PROFILE_CODE_WINDOW_CHARS && lines < 128) {
        start--
        if (text[start] == '\n' || text[start] == '\r' && text.getOrNull(start + 1) != '\n') lines++
    }
    if (start > 0 && (text[start].isLowSurrogate() && text[start - 1].isHighSurrogate() || text[start] == '\n' && text[start - 1] == '\r')) start--
    return start
}

/** 作者 long：全文查找选区可能比排版窗口长；输入替换选区时必须删完整命中，不能只删屏上可见部分。 */
internal fun replaceProfileCodeWindow(value: TextFieldValue, start: Int, end: Int, next: TextFieldValue): TextFieldValue {
    val local = value.text.substring(start, end)
    val selection = TextRange((value.selection.start - start).coerceIn(0, local.length),
        (value.selection.end - start).coerceIn(0, local.length))
    val prefix = local.substring(0, selection.min)
    val suffix = local.substring(selection.max)
    val replacesClippedSelection = next.text != local && !value.selection.collapsed &&
        (value.selection.min < start || value.selection.max > end) &&
        next.text.length >= prefix.length + suffix.length && next.text.startsWith(prefix) && next.text.endsWith(suffix)
    val offset: Int
    val content: String
    if (replacesClippedSelection) {
        val inserted = next.text.substring(prefix.length, next.text.length - suffix.length)
        content = value.text.replaceRange(value.selection.min, value.selection.max, inserted)
        offset = value.selection.min - selection.min
    } else {
        content = if (next.text == local) value.text else value.text.replaceRange(start, end, next.text)
        offset = start
    }
    return TextFieldValue(content, TextRange(next.selection.start + offset, next.selection.end + offset),
        next.composition?.let { TextRange(it.start + offset, it.end + offset) })
}
