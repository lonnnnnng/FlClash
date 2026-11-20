package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme

/** 作者 long：浮动表单本身已使用surfaceContainer，输入组必须再亮一级，才能保留上游分组与按钮轮廓。 */
@Composable
internal fun profileInputSurfaceColor() = if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else MaterialTheme.colorScheme.surfaceBright

/** 作者 long：表单同行标签和值共用56dp行高，不叠加Outlined边框；敏感输入显隐仅属当前视图，后台必须恢复遮蔽。 */
@Composable
internal fun ProfileFormInput(
    title: String, value: String, onValueChange: (String) -> Unit, tag: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, secret: Boolean = false,
    invalid: Boolean = false, placeholder: String = "", unit: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var revealed by remember { mutableStateOf(false) }
    if (secret) ClearAgeInputOnBackground { revealed = false }
    val colors = MaterialTheme.colorScheme
    ProfileFormRow(title, Modifier.background(if (invalid) colors.errorContainer.copy(alpha = .3f) else androidx.compose.ui.graphics.Color.Transparent)) {
        BasicTextField(value, onValueChange, modifier.weight(1f).padding(vertical = 12.dp).testTag(tag), enabled = enabled,
            singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End,
                color = (if (invalid) colors.error else colors.onSurface).copy(alpha = if (enabled) 1f else .4f)),
            cursorBrush = SolidColor(colors.primary), keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { input -> Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                input()
            } })
        unit?.let { Text(it, Modifier.padding(start = 4.dp), color = colors.onSurfaceVariant) }
        if (secret) IconButton({ revealed = !revealed }, Modifier.size(44.dp).testTag("${tag}_visibility"), enabled = enabled) {
            Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ProfileFormDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = .5.dp,
        color = MaterialTheme.colorScheme.outlineVariant)
}

/** 作者 long：紧凑表单标题固定居中，左右操作各留相同宽度，批量切换不能把标题推偏。 */
@Composable
internal fun ProfileFormHeader(title: String, onClose: () -> Unit, closeEnabled: Boolean,
    closeTag: String, actions: @Composable RowScope.() -> Unit) {
    val shape = AppSuperellipseShape(1000.dp)
    Box(Modifier.fillMaxWidth().height(58.dp).padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
        IconButton(onClose, Modifier.align(Alignment.CenterStart).size(44.dp)
            .background(profileInputSurfaceColor(), shape).appGlassTouch(shape, closeEnabled).testTag(closeTag), enabled = closeEnabled) {
            AppGlyphIcon(AppGlyphs.close, Modifier.size(24.dp))
        }
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = 94.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Row(Modifier.align(Alignment.CenterEnd).background(profileInputSurfaceColor(), shape),
            verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
