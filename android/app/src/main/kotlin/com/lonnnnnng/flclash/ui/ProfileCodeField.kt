package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
    highlightYaml: Boolean = true) {
    val style = profileCodeStyle().copy(color = MaterialTheme.colorScheme.onSurface)
    val colors = MaterialTheme.colorScheme
    val transformation = remember(colors.primary, colors.onSurfaceVariant, colors.tertiary, highlightYaml) {
        VisualTransformation { TransformedText(if (highlightYaml) profileYamlText(it.text, colors.primary, colors.onSurfaceVariant, colors.tertiary) else it, OffsetMapping.Identity) }
    }
    val scroll = rememberScrollState()
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
