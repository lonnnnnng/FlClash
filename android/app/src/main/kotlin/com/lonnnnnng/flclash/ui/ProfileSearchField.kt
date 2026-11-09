package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** 作者 long：沿用固定Prism搜索框的41dp文本起点与胶囊底，清空只恢复候选，不能触发保存或退出当前选择层。 */
@Composable
internal fun ProfileSearchField(value: String, onChange: (String) -> Unit, tag: String,
    placeholder: String = "搜索", enabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(shape = AppSuperellipseShape(28.dp), color = dashboardCardColor()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(41.dp), contentAlignment = Alignment.Center) {
                AppGlyphIcon(AppGlyphs.search, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(value, { onChange(it.take(256)) }, enabled = enabled, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).testTag(tag),
                decorationBox = { input -> Box {
                    if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    input()
                } })
            if (value.isNotEmpty()) IconButton({ onChange("") }, enabled = enabled,
                modifier = Modifier.size(49.dp).testTag("${tag}_clear")) {
                AppGlyphIcon(AppGlyphs.clearField, Modifier.size(24.dp).testTag("action:清除搜索"), fillProgress = 1f)
            } else Spacer(Modifier.width(12.dp))
        }
    }
}
