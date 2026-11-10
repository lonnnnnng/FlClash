package com.lonnnnnng.flclash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberProfileFilePicker(controller: ProfileFileImportController): () -> Unit {
    val state by controller.state.collectAsState()
    var token by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending = token; token = null
        controller.acceptFile(pending, uri?.toString())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.onBackground() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    when {
        state.reading -> AlertDialog(onDismissRequest = controller::dismiss, title = { Text("读取配置") },
            text = { CircularProgressIndicator() }, confirmButton = {}, dismissButton = { TextButton(onClick = controller::dismiss) { Text("取消") } })
        state.needsKey -> AgeSecretKeyDialog(controller::confirm, controller::dismiss, busy = state.saving, error = state.error)
        state.fileName != null -> ProfileFileImportPreviewDialog(state.fileName!!, state.size, state.validYaml, state.saving,
            { controller.confirm() }, controller::dismiss, isEncrypted = state.encrypted, error = state.error, validationNote = state.validationNote)
        state.error != null -> AlertDialog(onDismissRequest = controller::dismiss, title = { Text("导入配置") },
            text = { Text(state.error!!) }, confirmButton = { TextButton(onClick = controller::dismiss) { Text("关闭") } })
    }
    return {
        controller.beginPick()?.let { pending ->
            token = pending
            try { picker.launch(arrayOf("*/*")) }
            catch (_: Exception) { token = null; controller.dismiss() }
        }
    }
}
