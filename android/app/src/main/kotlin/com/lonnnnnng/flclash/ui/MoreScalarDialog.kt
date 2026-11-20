package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import kotlinx.coroutines.currentCoroutineContext

/** 作者 long：标量弹框取消不写入；确认后保留输入直到版本回执，重建不能丢失已提交的关闭意图。 */
@Composable
internal fun MoreScalarDialog(field: MoreField, initial: JsonElement, root: JsonObject, ntp: Boolean,
    saving: Boolean, saveError: String?, revision: Long, onDismiss: () -> Unit, onConfirm: (JsonElement?) -> Unit) {
    val original by rememberSaveable(field.key) { mutableStateOf(initial.asString) }
    var text by rememberSaveable(field.key) { mutableStateOf(original) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var awaitingReceipt by rememberSaveable { mutableStateOf(false) }
    var submittedRevision by rememberSaveable { mutableLongStateOf(revision) }
    var closing by rememberSaveable { mutableStateOf(false) }
    val motion = remember { ScalarDialogMotion(closing) }
    val dismissed by rememberUpdatedState(onDismiss)
    fun close() { if (!closing) { motion.close(); closing = true } }
    LaunchedEffect(closing) {
        if (closing) motion.close()
        var previous = withFrameNanos { it }
        while (motion.moving) {
            val now = withFrameNanos { it }
            val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            if (durationScale <= 0f) motion.finish() else motion.advance((now - previous) / 1_000_000_000f / durationScale)
            previous = now
        }
        // 作者 long：只延迟窗口移除；保存已在点击时提交，恢复关闭意图和动画完成都不能再次写配置。
        if (closing) dismissed()
    }
    var focused by remember { mutableStateOf(false) }
    val maxLength = when (field.key) { "server" -> 512; "port" -> 5; "interval" -> 10; else -> 64 }
    fun parsed(input: String): JsonElement? {
        val next = root.deepCopy()
        setMoreField(next, field, input.trim().takeIf(String::isNotEmpty)?.let(::JsonPrimitive))
        validateMoreFields(next, ntp)
        return moreFieldValue(next, field)
    }
    LaunchedEffect(revision, saving, saveError, awaitingReceipt) {
        if (awaitingReceipt && revision > submittedRevision) { awaitingReceipt = false; close() }
        else if (awaitingReceipt && !saving && saveError != null) awaitingReceipt = false
    }
    val enabled = !saving && !closing
    fun cancel() { if (enabled) close() }
    Dialog(onDismissRequest = ::cancel, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = enabled,
        dismissOnClickOutside = enabled)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val dark = LocalKClashDarkTheme.current
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        // 作者 long：禁用系统额外进退场，遮罩与内容共用弹簧进度，避免双重淡入及退出时背景先闪亮。
        fun ownVeilOnly() {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        SideEffect { ownVeilOnly() }
        DisposableEffect(view, window) {
            // 作者 long：首次show/attach可能重新应用Dialog主题的系统dim，挂窗后再次清除标志，由自绘遮罩独占透明度。
            val listener = object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) { ownVeilOnly() }
                override fun onViewDetachedFromWindow(v: android.view.View) = Unit
            }
            view.addOnAttachStateChangeListener(listener)
            if (view.isAttachedToWindow) ownVeilOnly()
            onDispose { view.removeOnAttachStateChangeListener(listener) }
        }
        fun cancelInput() {
            if (!enabled) return
            // 作者 long：取消按钮/外点先结束本窗口输入，避免马上重开字段时仍由旧IME连接消费第一次返回。
            focus.clearFocus(force = true); keyboard?.hide(); close()
        }
        fun confirm() {
            if (!enabled) return
            focus.clearFocus(force = true); keyboard?.hide()
            // 作者 long：已有空入口直接确认应保留；只有把原非空值主动清空才恢复继承，不能误删拨号代理入口。
            if (text.trim() == original.trim()) { close(); return }
            try {
                val value = parsed(text)
                // 作者 long：数字前导零等文本差异可能仍为同一值，父层不会生成新回执，必须直接结束本次编辑。
                if (value == initial) { close(); return }
                error = null; attempted = true; submittedRevision = revision; awaitingReceipt = true
                onConfirm(value)
            } catch (failure: IllegalArgumentException) { error = failure.message ?: "字段无效，请检查后重试" }
        }
        val message = error ?: saveError?.takeIf { attempted }
        val panelColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else dashboardCardColor()
        val inputColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else dashboardPageColor()
        Box(Modifier.fillMaxSize().drawBehind {
            // 作者 long：遮罩只在绘制阶段读取进度，不每帧重组表单或更新WindowManager属性。
            drawRect(Color.Black.copy(alpha = (if (dark) .48f else .20f) * motion.alpha))
        }.testTag("more_value_outside").pointerInput(enabled) { detectTapGestures { cancelInput() } }
            .safeDrawingPadding().imePadding().padding(horizontal = 40.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 348.dp).fillMaxWidth().graphicsLayer {
                scaleX = motion.scale; scaleY = motion.scale; alpha = motion.alpha
            }.testTag("more_value_dialog"),
                shape = AppSuperellipseShape(34.dp), color = panelColor) {
                Column(Modifier.pointerInput(Unit) { detectTapGestures { } }.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(field.label, Modifier.padding(start = 30.dp, top = 21.dp, end = 30.dp), style = MaterialTheme.typography.bodyLarge)
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp)) {
                        Surface(shape = AppSuperellipseShape(24.dp), color = inputColor,
                            border = if (focused) BorderStroke(2.dp, if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else null) {
                            BasicTextField(text, { next ->
                                // 作者 long：长度限制只作用于新输入，已有较长配置仍原样展示；未受理的保存不能永久锁死弹框。
                                if (!enabled) return@BasicTextField
                                text = next.take(maxLength); awaitingReceipt = false; attempted = false
                                error = runCatching { parsed(text) }.exceptionOrNull()?.message
                            }, Modifier.fillMaxWidth().heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
                                .padding(horizontal = 14.dp, vertical = 12.dp).testTag("more_${field.key}_value"),
                                enabled = enabled, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = if (field.kind in setOf(MoreFieldKind.PORT, MoreFieldKind.NUMBER)) KeyboardType.Number else KeyboardType.Uri,
                                    imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { confirm() }))
                        }
                        message?.let { Text(it, Modifier.padding(top = 8.dp).testTag("more_value_error"),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("more_value_saving"))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::cancelInput, enabled = enabled, shape = AppSuperellipseShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = inputColor, contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = if (closing) inputColor else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                                disabledContentColor = if (closing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("more_value_back")) { Text("取消") }
                        Button(onClick = ::confirm, enabled = enabled, shape = AppSuperellipseShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = if (closing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                                disabledContentColor = if (closing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("more_value_confirm")) { Text("提交") }
                    }
                }
            }
        }
    }
}
