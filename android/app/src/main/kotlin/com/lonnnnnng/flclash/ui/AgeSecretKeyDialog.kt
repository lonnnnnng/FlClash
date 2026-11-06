package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun AgeSecretKeyField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, tag: String, modifier: Modifier = Modifier,
    optional: Boolean = true, singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(if (optional) "age 解密密钥（可选）" else "age 解密密钥") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
internal fun ClearAgeInputOnBackground(onClear: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clear by rememberUpdatedState(onClear)
    LaunchedEffect(lifecycle) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) clear()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) clear() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun AgeSecretKeyDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit, busy: Boolean = false, error: String? = null) {
    // 作者 long：密钥只保留在当前弹窗内存，退出或后台即清除，不进入 SavedState 或恢复后的表单。
    var key by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onDismiss()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                key = ""
                onDismiss()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("age 加密配置") },
        text = { androidx.compose.foundation.layout.Column {
            AgeSecretKeyField(key, { key = it }, !busy, "profile_age_required_key", optional = false)
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_age_required_error")) }
        } },
        confirmButton = {
            TextButton(onClick = { onSubmit(key.trim()); key = "" }, enabled = !busy && key.isNotBlank()) { Text(if (busy) "解密中…" else "解密并导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}
