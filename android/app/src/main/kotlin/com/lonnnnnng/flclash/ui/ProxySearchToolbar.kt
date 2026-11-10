package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 作者 long：仅根页使用顶栏变形；选择面板仍使用原输入容器，避免把Flutter下沿搜索与根页混用。 */
@Composable
internal fun ProxySearchToolbar(
    query: String,
    expanded: Boolean,
    hasPrimary: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    primaryAction: @Composable RowScope.() -> Unit,
    secondaryActions: @Composable RowScope.() -> Unit,
) {
    val motion = rememberToolbarSearchMotion(expanded, hasPrimary)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 作者 long：过滤可能暂时没有组/测速按钮，只在展开中冻结来源；关闭态必须跟随异步组加载，避免搜索与新测速按钮重叠。
    val openedSourceHasPrimary = remember(expanded) { hasPrimary }
    val sourceHasPrimary = if (expanded) openedSourceHasPrimary else hasPrimary
    val visible = expanded || motion.moving
    var hadSearchFocus by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) { focus.requestFocus(); hadSearchFocus = true }
        else if (hadSearchFocus) {
            // 作者 long：只有本搜索曾取得焦点才结束输入；根页初次挂载不能清掉其他编辑窗口的键盘。
            focusManager.clearFocus(force = true); keyboard?.hide(); hadSearchFocus = false
        }
    }
    val color = dashboardCardColor()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp).testTag("proxy_page_toolbar")) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val unit = with(density) { 1.dp.toPx() }
            val fieldWidth = (maxWidth - 56.dp - if (hasPrimary) 56.dp else 0.dp).coerceAtLeast(0.dp)
            fun geometry() = searchToolbarGeometry(widthPx, unit, sourceHasPrimary, hasPrimary, motion.frame)
            Canvas(Modifier.fillMaxSize()) {
                val frame = motion.frame
                val geometry = geometry()
                val group = geometry.group
                drawRoundRect(color.copy(alpha = frame.lead.coerceIn(0f, 1f)),
                    topLeft = group.topLeft, size = group.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(group.height / 2))
                if (hasPrimary && frame.flank > 0f) drawRoundRect(color.copy(alpha = frame.flank.coerceIn(0f, 1f)),
                    topLeft = geometry.primary.topLeft, size = geometry.primary.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(geometry.primary.height / 2))
            }
            Layout(content = {
                Text("代理", Modifier.searchFade { motion.frame.title },
                    style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { onExpandedChange(true) }, enabled = !expanded,
                    modifier = Modifier.size(44.dp).searchFade { motion.frame.glyph }
                        .testTag(if (expanded) "proxy_search_source_leaving" else "proxy_group_search_toggle")) {
                    AppGlyphIcon(AppGlyphs.search)
                }
                // 作者 long：测速按钮只有一个组合身份，搜索前后移动同一按钮，避免重复点击回调或重复测试标签。
                Row(Modifier.searchFade { max(motion.frame.actions, motion.frame.flank) }) { primaryAction() }
                Row(Modifier.searchInactive(expanded).searchFade { motion.frame.actions }) { secondaryActions() }
                Box(Modifier.graphicsLayer { alpha = motion.frame.field }.clip(CircleShape).background(color)
                    .testTag("proxy_search_morph_surface"), contentAlignment = Alignment.CenterStart) {
                    // 作者 long：内容按终态宽度排版后裁剪，胶囊变宽不压扁文字、不让每帧挤出换行。
                    Row(Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true).requiredWidth(fieldWidth).height(44.dp)
                        .searchFade { motion.frame.content }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(41.dp), contentAlignment = Alignment.Center) { AppGlyphIcon(AppGlyphs.search, Modifier.size(20.dp)) }
                        BasicTextField(query, { onQueryChange(it.take(256)) }, enabled = expanded, singleLine = true,
                            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus)
                                .testTag(if (expanded) "proxy_group_search" else "proxy_search_text_leaving"),
                            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            decorationBox = { input -> Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("搜索节点", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp))
                                input()
                            } })
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, enabled = expanded,
                            modifier = Modifier.size(44.dp).testTag("proxy_search_clear")) {
                            AppGlyphIcon(AppGlyphs.clearField, Modifier.size(24.dp).testTag("action:清除搜索"), fillProgress = 1f)
                        } else Spacer(Modifier.width(12.dp))
                    }
                }
                IconButton(onClick = { onExpandedChange(false) }, enabled = expanded,
                    modifier = Modifier.size(44.dp).searchFade { motion.frame.flank }.background(color, CircleShape)
                        .testTag(if (expanded) "proxy_group_search_toggle" else "proxy_search_cancel_leaving")) {
                    AppGlyphIcon(AppGlyphs.close)
                }
            }, modifier = Modifier.fillMaxSize()) { children, constraints ->
                val g = geometry()
                val button = (44 * unit).roundToInt()
                val title = children[0].measure(Constraints(maxWidth = max(0, g.group.left.roundToInt()), maxHeight = constraints.maxHeight))
                val lead = children[1].measure(Constraints.fixed(button, button))
                val primary = children[2].measure(Constraints(maxWidth = button, maxHeight = button))
                val secondary = children[3].measure(Constraints(maxWidth = button, maxHeight = button))
                val field = children[4].measure(Constraints.fixed(max(0, g.field.width.roundToInt()), max(0, g.field.height.roundToInt())))
                val cancel = children[5].measure(Constraints.fixed(button, button))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (!expanded || motion.moving) {
                        title.placeRelative(0, (constraints.maxHeight - title.height) / 2)
                        secondary.placeRelative(constraints.maxWidth - secondary.width, (constraints.maxHeight - secondary.height) / 2)
                    }
                    if (!expanded || motion.moving) lead.placeRelativeWithLayer(g.lead.left.roundToInt(), g.lead.top.roundToInt()) {
                        scaleX = g.lead.width / button; scaleY = g.lead.height / button
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    primary.placeRelativeWithLayer(g.primary.left.roundToInt(), g.primary.top.roundToInt()) {
                        scaleX = g.primary.width / button; scaleY = g.primary.height / button
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    if (visible) {
                        field.placeRelative(g.field.left.roundToInt(), g.field.top.roundToInt())
                        cancel.placeRelativeWithLayer(g.cancel.left.roundToInt(), g.cancel.top.roundToInt()) {
                            scaleX = g.cancel.width / button; scaleY = g.cancel.height / button
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                    }
                }
            }
        }
    }
}

internal data class SearchToolbarGeometry(val field: Rect, val group: Rect, val lead: Rect, val primary: Rect, val cancel: Rect)

internal fun searchToolbarGeometry(width: Float, unit: Float, sourceHasPrimary: Boolean, hasPrimary: Boolean,
    f: ToolbarSearchFrame): SearchToolbarGeometry {
    val button = 44 * unit
    val gap = 12 * unit
    val top = 14 * unit
    val groupRight = width - 52 * unit
    val groupLeft = groupRight - button * (if (sourceHasPrimary) 2 else 1)
    val group = Rect(groupLeft + (if (sourceHasPrimary) button * f.slot else 0f), top, groupRight, top + button)
    fun lifted(r: Rect, lift: Float): Rect {
        val extra = min(.25f, 16 * unit / max(r.width, r.height).coerceAtLeast(1f)) * lift
        return Rect(r.left - r.width * extra / 2, r.top - r.height * extra / 2,
            r.right + r.width * extra / 2, r.bottom + r.height * extra / 2)
    }
    val start = if (hasPrimary) button + gap else 0f
    val slot = Rect(start, top, max(start, width - button - gap), top + button)
    val field = lifted(lerp(group, slot, f.growth), f.lift)
    val grown = f.growth.coerceAtLeast(0f)
    val reach = button / 2 + (-gap - button / 2) * grown
    val primaryEnd = field.left + reach
    val openPrimary = Rect(primaryEnd - button * grown, top + button * (1 - grown) / 2,
        primaryEnd, top + button * (1 + grown) / 2)
    val oldPrimary = Rect(groupRight - button, top, groupRight, top + button)
    val blend = if (f.actions + f.flank > 0f) f.flank / (f.actions + f.flank) else 0f
    val cancelLeft = field.right - reach
    var background = lifted(group, f.lift + f.swell)
    if (!sourceHasPrimary) {
        val center = background.center.copy(x = background.center.x + (slot.center.x - group.center.x) * f.dropletOffset)
        background = Rect(center.x - background.width * f.dropletSize / 2, center.y - background.height * f.dropletSize / 2,
            center.x + background.width * f.dropletSize / 2, center.y + background.height * f.dropletSize / 2)
    }
    val cancel = lifted(Rect(cancelLeft, top + button * (1 - grown) / 2,
        cancelLeft + button * grown, top + button * (1 + grown) / 2), f.lift)
    val cancelSize = cancel.size * f.flankScale
    return SearchToolbarGeometry(field, background,
        if (sourceHasPrimary) Rect(group.left, top, group.left + button, top + button) else background,
        lerp(oldPrimary, openPrimary, blend),
        Rect(cancel.center.x - cancelSize.width / 2, cancel.center.y - cancelSize.height / 2,
            cancel.center.x + cancelSize.width / 2, cancel.center.y + cancelSize.height / 2))
}

private fun Modifier.searchFade(opacity: () -> Float): Modifier = graphicsLayer {
    val value = opacity().coerceIn(0f, 1f)
    alpha = value
    // 作者 long：与既有菜单同样按屏幕sigma换算；旧API保留透明度过渡，避免加载不可用的RenderEffect。
    val radius = appMenuBlurRadiusPx(6.dp.toPx() * (1f - value))
    renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
}

private fun Modifier.searchInactive(inactive: Boolean): Modifier = if (!inactive) this else
    pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    }
