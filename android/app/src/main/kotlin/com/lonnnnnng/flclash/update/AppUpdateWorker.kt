package com.lonnnnnng.flclash.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lonnnnnng.flclash.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@EntryPoint @InstallIn(SingletonComponent::class)
internal interface AppUpdateEntryPoint { fun appUpdateRepository():AppUpdateRepository }

class AppUpdateWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=coroutineScope {
        val token=inputData.getString("token")?:return@coroutineScope Result.failure()
        val repository=EntryPointAccessors.fromApplication(applicationContext,AppUpdateEntryPoint::class.java).appUpdateRepository()
        try {
            setForeground(foreground(repository.state.value))
            val progress=launch { repository.state.collect { state -> if(state.token==token&&state.phase==UpdatePhase.DOWNLOADING)setForeground(foreground(state)) } }
            try { if(repository.performDownload(token))Result.success() else Result.failure() }
            finally { progress.cancel() }
        } catch(canceled:CancellationException){throw canceled}
        catch(_:Exception){repository.failTask(token,"后台下载未能启动，请返回应用重试");Result.failure()}
    }
    private fun foreground(state:UpdateState):ForegroundInfo {
        val channel="app-updates"
        if(Build.VERSION.SDK_INT>=26)applicationContext.getSystemService(android.app.NotificationManager::class.java)
            .createNotificationChannel(android.app.NotificationChannel(channel,"应用更新",android.app.NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(applicationContext,AppUpdateActivity::class.java)
        val pending=PendingIntent.getActivity(applicationContext,810,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val percent=state.release?.apkBytes?.takeIf { it>0 }?.let { (state.downloaded*100/it).toInt().coerceIn(0,100) }?:0
        val notification=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(R.drawable.ic_shortcut_toggle)
            .setContentTitle("正在下载 KClash 更新").setContentText("$percent%").setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(pending).setProgress(100,percent,state.release==null).build()
        return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(810,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(810,notification)
    }
}
