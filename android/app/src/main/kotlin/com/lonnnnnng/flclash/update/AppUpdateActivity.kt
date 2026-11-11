package com.lonnnnnng.flclash.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 作者 long：只展示版本检查结果；下载交给浏览器的GitHub发布页，不申请应用安装权限。 */
@AndroidEntryPoint
class AppUpdateActivity : ComponentActivity() {
    @Inject lateinit var repository: AppUpdateRepository
    private var browserError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KClashTheme {
            val state by repository.state.collectAsState()
            LaunchedEffect(Unit) { if (state.phase == UpdatePhase.IDLE) repository.check() }
            UpdatePanel(state, repository.currentVersion, browserError, ::finish,
                { browserError = null; lifecycleScope.launch { repository.check() } }, {
                    state.release?.let { release ->
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl))) }
                        catch (_: ActivityNotFoundException) { browserError = "未找到浏览器，请安装浏览器后重试" }
                    }
                })
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatePanel(state: UpdateState, currentVersion: String, browserError: String?, back: () -> Unit,
    check: () -> Unit, openRelease: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("应用更新") }, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("KClash", style = MaterialTheme.typography.titleLarge)
                    Text("当前版本 $currentVersion")
                    Text(when (state.phase) {
                        UpdatePhase.IDLE -> "检查可用的正式版本"
                        UpdatePhase.CHECKING -> "正在检查更新"
                        UpdatePhase.LATEST -> "已是最新正式版本"
                        UpdatePhase.AVAILABLE -> "发现新版本 ${state.release?.version.orEmpty()}"
                        UpdatePhase.FAILED -> "检查更新失败，请检查网络后重试"
                    }, modifier = Modifier.testTag("app_update_status"))
                    if (state.phase == UpdatePhase.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
                    browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.phase == UpdatePhase.AVAILABLE) {
                        Button(onClick = openRelease, modifier = Modifier.testTag("app_update_github")) { Text("前往 GitHub 下载") }
                    }
                    OutlinedButton(onClick = check, enabled = state.phase != UpdatePhase.CHECKING,
                        modifier = Modifier.testTag("app_update_check")) { Text("检查更新") }
                }
            }
            state.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("版本说明", style = MaterialTheme.typography.titleMedium)
                        Text(notes)
                    }
                }
            }
        }
    }
}
