package com.lonnnnnng.flclash.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 作者 long：安装交给系统确认，不申请静默安装；回调由本任务token和session双重绑定，拒绝迟到回执。 */
internal object AppUpdateInstaller {
    suspend fun install(repository:AppUpdateRepository)=repository.actionLock.withLock { withContext(Dispatchers.IO) {
        val context=repository.context;val state=repository.state.value;val token=checkNotNull(state.token);val release=checkNotNull(state.release)
        check(state.phase in setOf(UpdatePhase.READY,UpdatePhase.NEEDS_PERMISSION,UpdatePhase.NEEDS_CONFIRMATION))
        if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()) {
            repository.setInstaller(token,UpdatePhase.NEEDS_PERMISSION,state.installerSession);return@withContext
        }
        if (!repository.verifyForInstallation(state)) return@withContext
        val installer=context.packageManager.packageInstaller
        repository.installationInProgress=true
        var created:Int?=null
        try {
            // 作者 long：确认Intent已丢失或写包中断时显式放弃旧会话并完整重建，不能复用半包或重复commit已封口会话。
            state.installerSession?.takeIf { installer.getSessionInfo(it)!=null }?.let { installer.abandonSession(it) }
            val id=installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(UPDATE_PACKAGE);setSize(release.apkBytes)
                if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            })
            created=id
            repository.setInstaller(token,UpdatePhase.INSTALLING,id)
            installer.openSession(id).use { session ->
                repository.apk(token).inputStream().use { input ->session.openWrite("base.apk",0,release.apkBytes).use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true){currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
                    session.fsync(output)
                } }
                val callback=Intent(context,AppUpdateInstallReceiver::class.java).setAction("${context.packageName}.APP_UPDATE_RESULT")
                    .setData(android.net.Uri.parse("kclash-update://$token/$id")).putExtra("token",token).putExtra("session",id)
                val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(context,id,callback,flags).intentSender)
                repository.markInstallerCommitted(token,id)
            }
        } catch(failure:Throwable){
            created?.let { runCatching { installer.abandonSession(it) } }
            withContext(NonCancellable){repository.setInstaller(token,UpdatePhase.READY,null,"无法提交安装，请重试")}
            throw failure
        } finally {repository.installationInProgress=false}
    } }
}

class AppUpdateInstallReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="${context.packageName}.APP_UPDATE_RESULT")return
        val token=intent.getStringExtra("token")?:return
        val session=intent.getIntExtra("session",-1);if(session<0)return
        if(intent.hasExtra(PackageInstaller.EXTRA_SESSION_ID)&&intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=session)return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val repository=EntryPointAccessors.fromApplication(context.applicationContext,AppUpdateEntryPoint::class.java).appUpdateRepository()
                @Suppress("DEPRECATION") val confirmation=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
                repository.installerResult(token,session,status,confirmation)
                // 作者 long：后台回调不强行拉起界面；通知将用户带回本应用，前台页面再启动系统确认。
                if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    val channel="app-updates"
                    val manager=context.getSystemService(android.app.NotificationManager::class.java)
                    if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(android.app.NotificationChannel(channel,"应用更新",android.app.NotificationManager.IMPORTANCE_DEFAULT))
                    val action=PendingIntent.getActivity(context,811,Intent(context,AppUpdateActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    if(Build.VERSION.SDK_INT<33||context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                        manager.notify(811,androidx.core.app.NotificationCompat.Builder(context,channel).setSmallIcon(com.lonnnnnng.flclash.R.drawable.ic_shortcut_toggle)
                            .setContentTitle("KClash 更新已准备好").setContentText("点击继续系统安装确认").setAutoCancel(true).setContentIntent(action).build())
                }
            } catch(_:Exception) {
                // 作者 long：系统回执失败仍保留磁盘session供前台核对，不能让广播异常崩溃整个代理进程。
            } finally {pending.finish()}
        }
    }
}
