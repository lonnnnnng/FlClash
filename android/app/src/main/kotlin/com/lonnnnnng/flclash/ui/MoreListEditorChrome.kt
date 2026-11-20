package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun MoreListActionPill(content: @Composable RowScope.() -> Unit) {
    Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** 作者 long：单项输入在键盘上方保持完整表单；确认只改草稿，不伪装成父配置已保存。 */
@Composable
internal fun MoreListEntryDialog(title: String, input: String, error: String?, onInput: (String) -> Unit,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dark = LocalKClashDarkTheme.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // 作者 long：只设置本输入窗口的遮罩，沿Prism深浅色透明度；不能依赖Android默认dim把浅色背景压黑。
        SideEffect { window?.setDimAmount(if (dark) .48f else .20f) }
        val panelColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else dashboardCardColor()
        val inputColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else dashboardPageColor()
        Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } }.safeDrawingPadding().imePadding().padding(horizontal = 40.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 348.dp).fillMaxWidth().testTag("more_list_entry_dialog"),
                shape = AppSuperellipseShape(34.dp), color = panelColor) {
                Column(Modifier.pointerInput(Unit) { detectTapGestures { } }.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, Modifier.padding(start = 30.dp, top = 21.dp, end = 30.dp), style = MaterialTheme.typography.bodyLarge)
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp)) {
                        Surface(shape = AppSuperellipseShape(24.dp), color = inputColor) {
                            BasicTextField(input, onInput, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp).testTag("more_list_entry_input"),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), minLines = 1, maxLines = 5,
                                decorationBox = { inner -> Box {
                                    if (input.isEmpty()) Text(tr("值"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    inner()
                                } })
                        }
                        error?.let { Text(tr(it), Modifier.padding(top = 8.dp).testTag("more_list_input_error"),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(onClick = onConfirm, shape = AppSuperellipseShape(24.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().height(48.dp)
                            .testTag("more_list_entry_confirm")) { Text(tr("确定")) }
                }
            }
        }
    }
}

/** 作者 long：仅保留有界文本历史；选区/查找不生成撤销项，系统重建交给父级已有加密草稿注册表。 */
private fun appendQuickHistory(history: List<String>, text: String): List<String> {
    var next = (history + text).takeLast(64)
    while (next.size > 1 && next.sumOf(String::length) > 262_144) next = next.drop(1)
    return next
}

@Composable
internal fun MoreListQuickEditor(title: String, text: String, contentTag: String,
    onTextChange: (String) -> Unit, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val original by rememberSaveable { mutableStateOf(text) }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    MoreCodeEditor(title, text, contentTag, onTextChange, onExit = {
        // 作者 long：列表快捷编辑只提交父列表草稿，持久化仍由列表保存；原文未改时不重新校验已有配置。
        if (text == original) onDismiss()
        else runCatching { onApply(text); onDismiss() }.onFailure { failure = it.message ?: "列表无效" }
    })
    failure?.let { message ->
        AlertDialog(onDismissRequest = { failure = null }, title = { Text(tr("放弃修改？")) },
            text = { Text(tr(message), Modifier.testTag("more_list_input_error")) },
            confirmButton = { TextButton({ failure = null; onDismiss() }) { Text(tr("放弃")) } },
            dismissButton = { TextButton({ failure = null }) { Text(tr("继续编辑")) } })
    }
}

/** 作者 long：列表与分区YAML共享编辑交互，但离开/提交由调用者决定，异步保存不得在受理前关闭页面。 */
@Composable
internal fun MoreCodeEditor(title: String, text: String, contentTag: String,
    onTextChange: (String) -> Unit, onExit: () -> Unit,
    enabled: Boolean = true, highlightYaml: Boolean = false, backTag: String = "more_list_quick_back",
    header: @Composable () -> Unit = {}) {
    val latestText by rememberUpdatedState(text)
    val latestEnabled by rememberUpdatedState(enabled)
    var selectionStart by rememberSaveable { mutableIntStateOf(0) }
    var selectionEnd by rememberSaveable { mutableIntStateOf(0) }
    var composition by remember { mutableStateOf<TextRange?>(null) }
    var undo by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var redo by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableIntStateOf(16) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    val menuSource = rememberAppMenuSource()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun exit() {
        if (!enabled) return
        if (searching) { searching = false; query = ""; return }
        focus.clearFocus(force = true); keyboard?.hide(); onExit()
    }
    fun replace(next: String) {
        onTextChange(next); selectionStart = next.length; selectionEnd = next.length; composition = null
    }
    fun findNext(from: Int) {
        if (query.isEmpty()) return
        val index = text.indexOf(query, from.coerceIn(0, text.length), ignoreCase = true)
            .takeIf { it >= 0 } ?: text.indexOf(query, ignoreCase = true)
        if (index >= 0) { selectionStart = index; selectionEnd = index + query.length; composition = null }
    }
    ProfileEditorPage(::exit, title = { Text(title) }, backTag = backTag, navigationButtonSurface = true,
        contentPadding = PaddingValues(0.dp), searchActionFirst = true,
        containerColor = MaterialTheme.colorScheme.surface,
        topBarSearch = ProfileEditorSearchState(searching, query, tr("查找"), {
            query = it; findNext(0)
        }, { searching = it }), confirmButton = {
            Row(Modifier.padding(end = 8.dp)) {
                if (searching) IconButton({ findNext(selectionEnd) }, enabled = query.isNotEmpty(),
                    modifier = Modifier.testTag("more_list_quick_next")) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
                Box {
                    AppMenuSourceContent(menuSource) {
                        MoreListActionPill {
                            IconButton({ sizeMenu = false; menu = true }, Modifier.appMenuSource(menuSource, dashboardCardColor())
                                .size(44.dp).testTag("more_list_quick_menu")) { AppGlyphIcon(AppGlyphs.more) }
                        }
                    }
                    AppDropdownMenu(menu, { menu = false }, source = menuSource, pageKey = sizeMenu,
                        onNavigateBack = if (sizeMenu) ({ sizeMenu = false }) else null) { level ->
                        // 作者 long：父卡在子菜单展开时仍参与过渡，按该卡自身level渲染，避免父卡内容瞬间变成字号列表。
                        if (level == true) {
                            AppDropdownMenuItem(text = { Text(tr("大小")) }, isBack = true, dismissOnClick = false,
                                modifier = Modifier.testTag("more_list_quick_size_back"), onClick = { sizeMenu = false })
                            listOf(16 to tr("标准"), 18 to tr("大"), 20 to tr("超大")).forEach { (size, label) ->
                                AppDropdownMenuItem(text = { Text(label) },
                                    trailingIcon = if (fontSize == size) ({ AppGlyphIcon(AppGlyphs.check) }) else null,
                                    modifier = Modifier.testTag("more_list_quick_size_$size"), onClick = { fontSize = size; menu = false })
                            }
                        } else {
                            AppDropdownMenuItem(text = { Text(tr("撤销")) }, enabled = enabled && undo.isNotEmpty(), modifier = Modifier.testTag("more_list_quick_undo"),
                                leadingIcon = { AppGlyphIcon(AppGlyphs.undo) }, onClick = {
                                    // 作者 long：回源期间输入法仍可能交付迟到编辑；历史动作仅作用于点击时正文，不能覆盖新输入。
                                    val previous = undo.lastOrNull()
                                    if (latestEnabled && latestText == text && previous != null) {
                                        undo = undo.dropLast(1); redo = appendQuickHistory(redo, text); replace(previous)
                                    }
                                    menu = false
                                })
                            AppDropdownMenuItem(text = { Text(tr("重做")) }, enabled = enabled && redo.isNotEmpty(), modifier = Modifier.testTag("more_list_quick_redo"),
                                leadingIcon = { AppGlyphIcon(AppGlyphs.redo) }, onClick = {
                                    val next = redo.lastOrNull()
                                    if (latestEnabled && latestText == text && next != null) {
                                        redo = redo.dropLast(1); undo = appendQuickHistory(undo, text); replace(next)
                                    }
                                    menu = false
                                })
                            AppDropdownMenuItem(text = { Text(tr("大小")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.textSize) },
                                trailingIcon = { AppMenuSubmenuArrow() }, opensSubmenu = true, dismissOnClick = false,
                                modifier = Modifier.testTag("more_list_quick_sizes"), onClick = { sizeMenu = true })
                            AppDropdownMenuItem(text = { Text(tr("自动换行")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.wrap) },
                                trailingIcon = if (wrap) ({ AppGlyphIcon(AppGlyphs.check) }) else null,
                                modifier = Modifier.testTag("more_list_quick_wrap"), onClick = { wrap = !wrap; menu = false })
                        }
                    }
                }
            }
        }, text = {
            Column(Modifier.fillMaxSize()) {
                header()
                if (searching && query.isNotEmpty() && !text.contains(query, ignoreCase = true)) {
                    Text(tr("无匹配结果"), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("more_list_quick_no_match"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProfileCodeField(TextFieldValue(text, TextRange(selectionStart.coerceIn(0, text.length), selectionEnd.coerceIn(0, text.length)), composition),
                    { next ->
                        if (next.text != text) { undo = appendQuickHistory(undo, text); redo = emptyList(); onTextChange(next.text) }
                        selectionStart = next.selection.start; selectionEnd = next.selection.end; composition = next.composition
                    }, enabled, Modifier.fillMaxWidth().weight(1f), contentTag, highlightYaml = highlightYaml,
                    fontSize = fontSize.sp, softWrap = wrap, lineHeight = (fontSize * 1.2f).sp)
            }
        })
}
