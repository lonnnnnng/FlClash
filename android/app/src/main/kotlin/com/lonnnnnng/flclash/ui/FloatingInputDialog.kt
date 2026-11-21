package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import kotlinx.coroutines.currentCoroutineContext

/** 作者 long：重命名与补密钥沿Prism居中输入框，取消只延迟移窗，提交仍立即交给原控制器并等待业务回执。 */
@Composable
internal fun FloatingInputDialog(
    title: String, tag: String, busy: Boolean, confirmEnabled: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit, confirmLabel: String,
    cancelLabel: String = tr("取消"), error: String? = null, errorTag: String = "${tag}_error",
    confirmTag: String = "${tag}_confirm", externallyClosing: Boolean = false,
    dismissAfterAcknowledgement: Boolean = false,
    onExitComplete: (() -> Unit)? = null,
    cancelButtonAction: (() -> Unit)? = null, cancelTag: String = "${tag}_cancel",
    field: @Composable (enabled: Boolean, submit: () -> Unit) -> Unit,
) {
    val registry = rememberEditorWindowRegistry()
    var closing by remember { mutableStateOf(false) }
    val motion = remember { ScalarDialogMotion() }
    var requestedClose by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val exit by rememberUpdatedState(onExitComplete)
    val external by rememberUpdatedState(externallyClosing)
    val enabled = !busy && !closing && !externallyClosing
    val latestCanSubmit by rememberUpdatedState(enabled && confirmEnabled)
    val latestConfirm by rememberUpdatedState(onConfirm)
    LaunchedEffect(externallyClosing) { if (externallyClosing) closing = true }
    LaunchedEffect(busy, error, externallyClosing) {
        // 作者 long：加密草稿清除可能失败，只有回执成功才淡出；失败时必须留下可见错误及可重试的取消按钮。
        if (requestedClose && !busy && !externallyClosing && error != null) requestedClose = false
    }
    LaunchedEffect(closing) {
        if (closing) motion.close()
        var previous = withFrameNanos { it }
        while (motion.moving) {
            val now = withFrameNanos { it }
            val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale <= 0f) motion.finish() else motion.advance((now - previous) / 1_000_000_000f / scale)
            previous = now
        }
        if (closing) { if (!external && !dismissAfterAcknowledgement) dismiss(); exit?.invoke() }
    }
    fun requestClose() {
        if (!enabled || requestedClose) return
        if (dismissAfterAcknowledgement) { requestedClose = true; onDismiss() } else closing = true
    }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = enabled, dismissOnClickOutside = enabled,
    )) {
        ProvideEditorWindowRegistry(registry) {
            val dark = LocalKClashDarkTheme.current
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            val view = LocalView.current
            val window = (view.parent as? DialogWindowProvider)?.window
            fun ownVeil() {
                window?.setWindowAnimations(0); window?.setDimAmount(0f)
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            SideEffect { ownVeil() }
            DisposableEffect(view, window) {
                val listener = object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) { ownVeil() }
                    override fun onViewDetachedFromWindow(v: android.view.View) = Unit
                }
                view.addOnAttachStateChangeListener(listener)
                onDispose { view.removeOnAttachStateChangeListener(listener) }
            }
            fun cancel() { if (enabled) { focus.clearFocus(force = true); keyboard?.hide(); requestClose() } }
            fun submit() { if (latestCanSubmit) { focus.clearFocus(force = true); keyboard?.hide(); latestConfirm() } }
            val inputColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else dashboardPageColor()
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = (if (dark) .48f else .2f) * motion.alpha)) }
                .pointerInput(enabled) { detectTapGestures { cancel() } }.safeDrawingPadding().imePadding()
                .padding(horizontal = 40.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 348.dp).fillMaxWidth().graphicsLayer {
                    scaleX = motion.scale; scaleY = motion.scale; alpha = motion.alpha
                }.testTag(tag), shape = AppSuperellipseShape(34.dp),
                    color = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else dashboardCardColor()) {
                    Column(Modifier.pointerInput(Unit) { detectTapGestures {} }.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, Modifier.padding(start = 30.dp, top = 21.dp, end = 30.dp), style = MaterialTheme.typography.bodyLarge)
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp)) {
                            field(enabled, ::submit)
                            error?.let { Text(tr(it), Modifier.padding(top = 8.dp).testTag(errorTag),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("${tag}_busy"))
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ if (cancelButtonAction != null) { focus.clearFocus(force = true); keyboard?.hide(); cancelButtonAction() } else cancel() },
                                Modifier.weight(1f).heightIn(min = 48.dp).testTag(cancelTag), enabled = enabled,
                                shape = AppSuperellipseShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = inputColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = if (closing) inputColor else MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),
                                    disabledContentColor = if (closing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))) {
                                // 作者 long：Prism浮框操作使用17sp正文，默认Material按钮15sp会让同一浮框的动作明显偏小。
                                Text(cancelLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                            Button(::submit, Modifier.weight(1f).heightIn(min = 48.dp).testTag(confirmTag), enabled = enabled && confirmEnabled,
                                shape = AppSuperellipseShape(24.dp), colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = if (closing && confirmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),
                                    disabledContentColor = if (closing && confirmEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))) {
                                Text(confirmLabel, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 作者 long：输入值仍由业务草稿持有；显隐只在窗口内存，后台恢复遮蔽，密钥不会因视觉状态进入系统保存树。 */
@Composable
internal fun FloatingInputField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit,
    tag: String, enabled: Boolean, submit: () -> Unit, secret: Boolean = false,
    placeholder: String = "", invalid: Boolean = false,
    keyboardType: KeyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
    suffix: String? = null) {
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val latestSubmit by rememberUpdatedState(submit)
    if (secret) ClearAgeInputOnBackground { revealed = false }
    val colors = MaterialTheme.colorScheme
    Surface(shape = AppSuperellipseShape(24.dp),
        color = if (LocalKClashDarkTheme.current) colors.surfaceContainerHighest else dashboardPageColor(),
        border = if (focused) BorderStroke(2.dp, if (invalid) colors.error else colors.primary) else null) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = if (secret) 0.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = 12.dp).onFocusChanged { focused = it.isFocused }.testTag(tag),
                enabled = enabled, singleLine = secret && !revealed, maxLines = if (secret && !revealed) 1 else 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
                visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType,
                    autoCorrectEnabled = !secret && keyboardType == KeyboardType.Text, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { latestSubmit() }),
                decorationBox = { input -> Box { if (value.text.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant); input() } })
            suffix?.let { Text(it, Modifier.padding(start = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge) }
            if (secret) IconButton({ revealed = !revealed }, Modifier.size(44.dp).testTag("${tag}_visibility"), enabled = enabled) {
                Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, Modifier.size(20.dp))
            }
        }
    }
}
