package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** 作者 long：沿用固定Prism搜索框的41dp文本起点与胶囊底，清空只恢复候选，不能触发保存或退出当前选择层。 */
@Composable
internal fun ProfileSearchField(value: String, onChange: (String) -> Unit, tag: String,
    placeholder: String = "搜索", enabled: Boolean = true, modifier: Modifier = Modifier,
    height: Dp = 56.dp, onFocusChange: (Boolean) -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(modifier, shape = AppSuperellipseShape(height / 2), color = dashboardCardColor()) {
        Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(41.dp), contentAlignment = Alignment.Center) {
                AppGlyphIcon(AppGlyphs.search, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(value, { onChange(it.take(256)) }, enabled = enabled, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) }.padding(vertical = 12.dp).testTag(tag),
                decorationBox = { input -> Box {
                    if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    input()
                } })
            if (value.isNotEmpty()) IconButton({ onChange("") }, enabled = enabled,
                modifier = Modifier.width(49.dp).height(height).testTag("${tag}_clear")) {
                AppGlyphIcon(AppGlyphs.clearField, Modifier.size(24.dp).testTag("action:清除搜索"), fillProgress = 1f)
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

// 作者 long：固定Flutter dock使用450ms归一化弹簧并补齐末端残差；不能套用根工具栏的液滴关闭时序。
internal val DockedSearchEasing = Easing { fraction ->
    val omega = sqrt(390.0)
    val damping = .75
    val frequency = omega * sqrt(1.0 - damping * damping)
    fun position(seconds: Double) = 1.0 - exp(-damping * omega * seconds) *
        (cos(frequency * seconds) + damping * omega / frequency * sin(frequency * seconds))
    (position(fraction * .45) + (1.0 - position(.45)) * fraction).toFloat()
}

/** 作者 long：下沿搜索在本窗口持有焦点；清空保留输入，取消只结束搜索，不触发选择、保存或退出父页。 */
@Composable
internal fun DockedProfileSearch(value: String, onChange: (String) -> Unit, tag: String,
    placeholder: String = "搜索", enabled: Boolean = true, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotEmpty()
    val progress by animateFloatAsState(if (active) 1f else 0f,
        tween(450, easing = DockedSearchEasing), label = "dockedSearch")
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).testTag("${tag}_dock")) {
        ProfileSearchField(value, onChange, tag, placeholder, enabled,
            Modifier.width((maxWidth - 60.dp * progress.coerceAtLeast(0f)).coerceAtLeast(0.dp)),
            height = 48.dp, onFocusChange = { focused = it })
        if (active || progress > .001f) {
            Surface(Modifier.align(Alignment.CenterEnd).size(48.dp).graphicsLayer {
                translationX = 24.dp.toPx() * (1f - progress)
                scaleX = progress.coerceAtLeast(0f); scaleY = scaleX
                alpha = ((progress - .5f) / .4f).coerceIn(0f, 1f)
            }, shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                IconButton(onClick = { onChange(""); focus.clearFocus(force = true); keyboard?.hide() },
                    enabled = enabled && active, modifier = Modifier.testTag("${tag}_cancel")) {
                    AppGlyphIcon(AppGlyphs.close, Modifier.size(24.dp))
                }
            }
        }
    }
}
