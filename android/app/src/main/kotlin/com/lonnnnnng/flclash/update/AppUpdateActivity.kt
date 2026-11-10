package com.lonnnnnng.flclash.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 作者 long：独立更新页允许安装器返回和后台通知进入同一任务状态，不把确认Intent写入普通磁盘。 */
@AndroidEntryPoint
class AppUpdateActivity:ComponentActivity() {
    @Inject lateinit var repository:AppUpdateRepository
    private var localError by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KClashTheme {
            val state by repository.state.collectAsState()
            LaunchedEffect(state.phase) {
                if(state.phase==UpdatePhase.NEEDS_CONFIRMATION&&lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    repository.takeConfirmation()?.let { startActivity(it) }
                }
            }
            UpdatePanel(state,repository.currentVersion,repository.supported,localError,::finish,
                { execute {repository.check()} },{execute {repository.download()}},{execute {repository.cancel()}},::requestInstall)
        } }
    }
    override fun onResume() {super.onResume();execute {repository.refresh()} }
    private fun execute(action:suspend ()->Unit) {lifecycleScope.launch {
        localError=null
        try {action()} catch(canceled:CancellationException){throw canceled}
        catch(_:Exception){localError="操作未完成，请稍后重试"}
    } }
    private fun requestInstall()=execute {
        if(Build.VERSION.SDK_INT>=26&&!packageManager.canRequestPackageInstalls()) {
            repository.state.value.token?.let { repository.setInstaller(it,UpdatePhase.NEEDS_PERMISSION,repository.state.value.installerSession) }
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
            return@execute
        }
        val confirmation=repository.takeConfirmation()
        if(confirmation!=null)startActivity(confirmation)
        else AppUpdateInstaller.install(repository)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatePanel(state:UpdateState,currentVersion:String,supported:Boolean,localError:String?,back:()->Unit,
    check:()->Unit,download:()->Unit,cancel:()->Unit,install:()->Unit) {
    Scaffold(topBar={TopAppBar(title={Text("应用更新")},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回")}})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("KClash",style=MaterialTheme.typography.titleLarge)
                Text("当前版本 $currentVersion",style=MaterialTheme.typography.bodyMedium)
                Text(when(state.phase){
                    UpdatePhase.IDLE->"检查可用的正式版本"
                    UpdatePhase.CHECKING->"正在检查更新"
                    UpdatePhase.LATEST->"已是最新正式版本"
                    UpdatePhase.AVAILABLE->"发现新版本 ${state.release?.version.orEmpty()}"
                    UpdatePhase.DOWNLOADING->"正在下载 ${state.release?.version.orEmpty()}"
                    UpdatePhase.VERIFYING->"正在校验安装包"
                    UpdatePhase.READY->"安装包已校验，可以安装"
                    UpdatePhase.NEEDS_PERMISSION->"请允许 KClash 安装更新"
                    UpdatePhase.INSTALLING->"正在提交系统安装"
                    UpdatePhase.NEEDS_CONFIRMATION->"等待系统安装确认"
                    UpdatePhase.INSTALLED->"更新已安装"
                    UpdatePhase.CANCELED->"更新已取消"
                    UpdatePhase.FAILED->"更新未完成"
                })
                if(state.phase==UpdatePhase.DOWNLOADING) {
                    val total=state.release?.apkBytes?:0L
                    LinearProgressIndicator(progress={if(total>0)(state.downloaded.toFloat()/total).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                    Text("${state.downloaded/1024/1024} / ${total/1024/1024} MB",style=MaterialTheme.typography.bodySmall)
                } else if(state.phase in setOf(UpdatePhase.CHECKING,UpdatePhase.VERIFYING,UpdatePhase.INSTALLING))LinearProgressIndicator(Modifier.fillMaxWidth())
                (localError?:state.error)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(!supported)Text("当前安装包无法覆盖正式版，请安装同签名的 KClash 正式版后使用应用内更新。",style=MaterialTheme.typography.bodySmall)
                when(state.phase){
                    UpdatePhase.AVAILABLE,UpdatePhase.FAILED,UpdatePhase.CANCELED -> {
                        if(state.release!=null&&supported)Button(onClick=download){Text(if(state.phase==UpdatePhase.FAILED)"继续下载" else "下载更新")}
                        TextButton(onClick=check){Text("重新检查")}
                    }
                    UpdatePhase.READY,UpdatePhase.NEEDS_PERMISSION,UpdatePhase.NEEDS_CONFIRMATION -> {
                        Button(onClick=install,enabled=supported){Text(if(state.phase==UpdatePhase.NEEDS_PERMISSION)"授权并安装" else "继续安装")}
                        TextButton(onClick=cancel){Text("取消更新")}
                    }
                    UpdatePhase.CHECKING,UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING -> TextButton(onClick=cancel){Text("取消")}
                    UpdatePhase.INSTALLING -> Unit
                    else -> Button(onClick=check){Text("检查更新")}
                }
            } }
            state.release?.notes?.takeIf {it.isNotBlank()}?.let {notes ->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("版本说明",style=MaterialTheme.typography.titleMedium);Text(notes,style=MaterialTheme.typography.bodyMedium)
            }}}
        }
    }
}
