package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/** 作者 long：远程资源的名称与URL沿用Prism同行表单；提交保留在原窗口等回执，失败不能丢掉输入或重建Sheet。 */
@Composable
internal fun ResourceNamedUrlSheet(title: String, name: String, url: String,
    onName: (String) -> Unit, onUrl: (String) -> Unit, busy: Boolean, error: String?,
    closing: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val scroll = rememberScrollState()
    var height by remember { mutableStateOf<Int?>(null) }
    val chrome = with(LocalDensity.current) { 68.dp.roundToPx() }
    val urlFocus = remember { FocusRequester() }
    ConnectionModalSheet(onDismiss, contentScrollState = scroll, avoidIme = true, restoreDetentAfterIme = true,
        dismissAllowed = { !busy }, sheetTag = "icon_set_options_sheet", closeRequested = closing,
        floatingForm = true, fitFormOverKeyboard = true, naturalContentHeight = height?.plus(chrome),
        handleHeight = 10.dp, handleWidth = 28.dp, handleVerticalOffset = 3.dp) { close ->
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val window = LocalWindowInfo.current
        val locked by rememberUpdatedState(busy || closing)
        fun hideInput() { focus.clearFocus(force = true); keyboard?.hide() }
        fun submit() { if (!locked) { hideInput(); onSave() } }
        LaunchedEffect(Unit) {
            snapshotFlow { window.isWindowFocused }.first { it }
            if (!locked) urlFocus.requestFocus()
        }
        ProfileFormHeader(title, { hideInput(); close() }, !locked, "icon_set_cancel") {
            IconButton(::submit, Modifier.size(44.dp).testTag("icon_set_save"), enabled = !locked && url.isNotBlank()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else AppGlyphIcon(AppGlyphs.check, Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { height = it.height }.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("icon_set_error")) }
            ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp, color = profileInputSurfaceColor()) {
                ProfileFormInput(tr("名称"), name, onName, "icon_set_name", enabled = !locked, placeholder = tr("可选"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { urlFocus.requestFocus() }))
                ProfileFormDivider()
                ProfileFormInput(tr("图标集地址"), url, onUrl, "icon_set_url", Modifier.focusRequester(urlFocus),
                    enabled = !locked, placeholder = tr("必填"),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
