package com.lonnnnnng.flclash.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lonnnnnng.flclash.ui.tr
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 作者 long：更新检查只在工具页上方显示结果弹框，下载交给浏览器，不再创建详细更新页面。 */
@AndroidEntryPoint
class AppUpdateActivity : ComponentActivity() {
    @Inject lateinit var appearanceSettings: com.lonnnnnng.flclash.data.AppearanceSettingsStore
    @Inject lateinit var repository: AppUpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by appearanceSettings.state.collectAsState()
            com.lonnnnnng.flclash.ui.AppAppearance(this, appearance) {
                // 作者 long：每次主动打开只检查一次，先显示等待态；不把单例中上次的结果闪回给用户。
                val observed by repository.state.collectAsState()
                var checked by remember { mutableStateOf(false) }
                var browserError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                suspend fun check() {
                    browserError = null
                    checked = false
                    repository.check()
                    checked = true
                }
                LaunchedEffect(Unit) { check() }
                val result = if (checked) observed else UpdateState(UpdatePhase.CHECKING)
                UpdateDialog(result, repository.currentVersion, browserError, ::finish,
                    { scope.launch { check() } }, {
                        result.release?.let { release ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, release.releaseUrl.toUri()))
                                finish()
                            } catch (_: ActivityNotFoundException) {
                                browserError = "未找到浏览器，请安装浏览器后重试"
                            }
                        }
                    })
            }
        }
    }
}

@Composable
internal fun UpdateDialog(state: UpdateState, currentVersion: String, browserError: String?, dismiss: () -> Unit,
    retry: () -> Unit, openRelease: () -> Unit) {
    val checking = state.phase == UpdatePhase.IDLE || state.phase == UpdatePhase.CHECKING
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("app_update_dialog"),
        title = { Text(tr("应用更新")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("当前版本 {0}", currentVersion))
                Text(when {
                    checking -> tr("正在检查更新")
                    browserError != null -> tr(browserError)
                    state.phase == UpdatePhase.LATEST -> tr("已是最新正式版本")
                    state.phase == UpdatePhase.AVAILABLE -> tr("发现新版本 {0}", state.release?.version.orEmpty())
                    else -> tr("检查更新失败，请检查网络后重试") +
                        (state.failure?.let { " [${it.diagnosticCode}]" } ?: "")
                }, Modifier.testTag("app_update_status"))
                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("app_update_loading"))
            }
        },
        confirmButton = {
            when {
                checking -> Unit
                state.phase == UpdatePhase.AVAILABLE -> TextButton(onClick = openRelease,
                    modifier = Modifier.testTag("app_update_github")) { Text(tr("前往 GitHub 下载")) }
                state.phase == UpdatePhase.FAILED -> TextButton(onClick = retry,
                    modifier = Modifier.testTag("app_update_check")) { Text(tr("重试")) }
                else -> TextButton(onClick = dismiss, modifier = Modifier.testTag("app_update_done")) { Text(tr("确定")) }
            }
        },
        dismissButton = {
            if (state.phase != UpdatePhase.LATEST) TextButton(onClick = dismiss,
                modifier = Modifier.testTag("app_update_cancel")) { Text(tr("取消")) }
        },
    )
}
