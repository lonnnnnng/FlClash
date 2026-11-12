package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import android.graphics.Paint

@Composable
internal fun profileCodeStyle() = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)

/** 作者 long：着色只添加显示span，OffsetMapping保持恒等；大正文跳过装饰扫描，不能为视觉处理改写或截断配置。 */
internal fun profileYamlText(text: String, key: Color, comment: Color, literal: Color): AnnotatedString {
    if (text.length > 524_288) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        Regex("(?m)^\\s*(?:-\\s+)?([^\\s:#][^:#\\n]*):(?=\\s|$)").findAll(text).forEach {
            val part = it.groups[1]!!; addStyle(SpanStyle(color = key), part.range.first, part.range.last + 1)
        }
        Regex("(?m)(?:^|\\s)(#[^\\n]*)$").findAll(text).forEach {
            val part = it.groups[1]!!; addStyle(SpanStyle(color = comment), part.range.first, part.range.last + 1)
        }
        Regex("(?<=[: \\[])\\b(?:true|false|null|[0-9]+)\\b").findAll(text).forEach {
            addStyle(SpanStyle(color = literal), it.range.first, it.range.last + 1)
        }
    }
}

@Composable
internal fun ProfileCodeField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, enabled: Boolean, modifier: Modifier = Modifier,
    contentTag: String = "profile_editor_content",
    highlightYaml: Boolean = true,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()) {
    val windowed = remember(value.text) { value.text.length > 65_536 || value.text.count { it == '\n' || it == '\r' } > 1500 }
    if (windowed) {
        WindowedProfileCodeField(value, onChange, enabled, modifier, contentTag)
        return
    }
    val style = profileCodeStyle().copy(color = MaterialTheme.colorScheme.onSurface)
    val colors = MaterialTheme.colorScheme
    val transformation = remember(colors.primary, colors.onSurfaceVariant, colors.tertiary, highlightYaml) {
        VisualTransformation { TransformedText(if (highlightYaml) profileYamlText(it.text, colors.primary, colors.onSurfaceVariant, colors.tertiary) else it, OffsetMapping.Identity) }
    }
    val scroll = scrollState
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val starts = remember(value.text) { buildList {
        add(0)
        value.text.forEachIndexed { index, char ->
            // 作者 long：保留源字节的CR/CRLF也需要正确行号；不能为显示行号先把配置换行格式改写。
            if (char == '\n' || char == '\r' && value.text.getOrNull(index + 1) != '\n') add(index + 1)
        }
    } }
    // 作者 long：查找时焦点仍在查询框，必须显式定位正文选区，不能依赖输入法的光标跟随。
    LaunchedEffect(value.selection, layout) {
        val result = layout ?: return@LaunchedEffect
        val cursor = result.getCursorRect(value.selection.min.coerceIn(0, value.text.length))
        val viewport = scroll.viewportSize
        if (viewport > 0 && (cursor.top < scroll.value || cursor.bottom > scroll.value + viewport)) {
            scroll.animateScrollTo((cursor.top.toInt() - viewport / 3).coerceAtLeast(0))
        }
    }
    val gutterColor = colors.onSurfaceVariant
    BoxWithConstraints(modifier.testTag("profile_code_viewport")) {
        val viewportHeight = constraints.maxHeight.toFloat()
        Row(Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = maxHeight).height(IntrinsicSize.Min).padding(top = 12.dp, bottom = 24.dp)) {
            Canvas(Modifier.width(48.dp).fillMaxHeight()) {
                val result = layout ?: return@Canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(255, (gutterColor.red * 255).toInt(), (gutterColor.green * 255).toInt(), (gutterColor.blue * 255).toInt())
                    textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT
                }
                val firstVisual = result.getLineForVerticalPosition(scroll.value.toFloat())
                val lastVisual = result.getLineForVerticalPosition(scroll.value + viewportHeight)
                val first = starts.binarySearch(result.getLineStart(firstVisual)).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
                for (line in first until starts.size) {
                    val visual = result.getLineForOffset(starts[line])
                    if (visual > lastVisual) break
                    drawContext.canvas.nativeCanvas.drawText((line + 1).toString(), size.width - 12.dp.toPx(), result.getLineBaseline(visual), paint)
                }
            }
            BasicTextField(value, onChange, Modifier.weight(1f).padding(end = 16.dp).testTag(contentTag), enabled = enabled,
                textStyle = style, cursorBrush = SolidColor(colors.primary), visualTransformation = transformation,
                onTextLayout = { layout = it })
        }
    }
}

@Composable
private fun WindowedProfileCodeField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, enabled: Boolean,
    modifier: Modifier, contentTag: String) {
    var start by remember { mutableIntStateOf(0) }
    val safeStart = start.coerceAtMost(value.text.length)
    val end = remember(value.text, safeStart) { profileCodeWindowEnd(value.text, safeStart) }
    // 作者 long：查找/错误定位传入全文偏移，只有排版窗口变化；不能把窗口内容误写成整份配置。
    LaunchedEffect(value.selection, safeStart, end) {
        val target = value.selection.min
        if (target < safeStart || target >= end && end < value.text.length || value.selection.max > end && target > safeStart) {
            start = if (target < value.text.length) target else profileCodeWindowBefore(value.text, target)
        }
    }
    fun move(target: Int) {
        start = target
        onChange(value.copy(selection = TextRange(target), composition = null))
    }
    Column(modifier.testTag("profile_code_window")) {
        Text("大正文分段编辑 · 字符 ${safeStart + 1}–$end / ${value.text.length}（段内行号）",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ move(0) }, enabled = enabled && safeStart > 0, modifier = Modifier.testTag("profile_code_first")) { Text("开头") }
            TextButton({ move(profileCodeWindowBefore(value.text, safeStart)) }, enabled = enabled && safeStart > 0, modifier = Modifier.testTag("profile_code_previous")) { Text("上一段") }
            TextButton({ move(end) }, enabled = enabled && end < value.text.length, modifier = Modifier.testTag("profile_code_next")) { Text("下一段") }
            TextButton({ move(profileCodeWindowBefore(value.text, value.text.length)) }, enabled = enabled && end < value.text.length, modifier = Modifier.testTag("profile_code_last")) { Text("末尾") }
        }
        val local = TextFieldValue(value.text.substring(safeStart, end), TextRange(
            (value.selection.start - safeStart).coerceIn(0, end - safeStart),
            (value.selection.end - safeStart).coerceIn(0, end - safeStart)), value.composition?.let {
                TextRange((it.start - safeStart).coerceIn(0, end - safeStart), (it.end - safeStart).coerceIn(0, end - safeStart))
            })
        key(safeStart) {
            ProfileCodeField(local, { next ->
                onChange(replaceProfileCodeWindow(value, safeStart, end, next))
            }, enabled, Modifier.fillMaxWidth().weight(1f), contentTag = contentTag, highlightYaml = false)
        }
    }
}
