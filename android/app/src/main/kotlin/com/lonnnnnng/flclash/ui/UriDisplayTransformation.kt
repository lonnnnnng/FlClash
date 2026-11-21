package com.lonnnnnng.flclash.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** 作者 long：Android把点号视作URI可断点，容易把扩展名单独挤到下一行；只保护显示层的点号边界，草稿及提交URL保持原字节。 */
internal object UriDisplayTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val display = StringBuilder(original.length)
        val forward = IntArray(original.length + 1)
        val backward = ArrayList<Int>(original.length + 1).apply { add(0) }
        original.forEachIndexed { index, char ->
            if (index > 0 && (char == '.' || original[index - 1] == '.')) {
                display.append('\u2060')
                backward.add(index)
            }
            forward[index] = display.length
            display.append(char)
            backward.add(index + 1)
        }
        forward[original.length] = display.length
        // 作者 long：不可见连接符不占原文选区；光标、删除、IME组合和复制仍回到原始UTF-16偏移，不能把显示符号带进地址。
        return TransformedText(AnnotatedString(display.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = forward[offset.coerceIn(forward.indices)]
            override fun transformedToOriginal(offset: Int) = backward[offset.coerceIn(backward.indices)]
        })
    }
}
