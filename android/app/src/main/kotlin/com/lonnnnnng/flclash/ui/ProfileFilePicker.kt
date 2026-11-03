package com.lonnnnnng.flclash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 作者 long：在主页面无条件注册两个独立回调，异步恢复的父表单尚未出现也不会丢掉系统文件结果。 */
@Composable
internal fun rememberProfileFilePicker(model: ProfilesViewModel, remote: Boolean): (Long) -> Unit {
    var launchedToken by rememberSaveable { mutableStateOf<Long?>(null) }
    val restoredToken = remember { launchedToken }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = launchedToken
        launchedToken = null
        model.receiveFilePickerResult(remote, token, uri?.toString())
    }
    LaunchedEffect(Unit) {
        if (!model.reconcileFilePicker(remote, restoredToken) && launchedToken == restoredToken) launchedToken = null
    }
    return { token ->
        if (launchedToken == null) {
            scope.launch {
                try {
                    if (model.persistPickerBeforeLaunch(token)) {
                        // 作者 long：只有系统即将受理时才保存launcher令牌，暂存期间重建不能恢复一个从未启动的选择器。
                        launchedToken = token
                        picker.launch(arrayOf("*/*"))
                    }
                    else {
                        launchedToken = null
                        model.receiveFilePickerResult(remote, token, null)
                    }
                } catch (failure: Exception) {
                    launchedToken = null
                    model.receiveFilePickerResult(remote, token, null)
                    if (failure is CancellationException) throw failure
                }
            }
        } else model.receiveFilePickerResult(remote, token, null)
    }
}
