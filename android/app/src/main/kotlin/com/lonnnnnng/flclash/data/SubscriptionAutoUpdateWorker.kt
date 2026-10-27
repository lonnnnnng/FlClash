package com.lonnnnnng.flclash.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.R
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.common.ensureNotificationChannel
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SubscriptionAutoUpdateEntryPoint {
    fun remoteProfileUpdater(): RemoteProfileUpdater
}

/**
 * 作者 long：WorkManager 只负责唤醒和重试，实际 Profile 筛选、下载、校验和原子替换仍由仓库统一处理，
 * 这样前台手动同步与后台自动同步不会形成两套数据一致性规则。
 */
class SubscriptionAutoUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val updater = EntryPointAccessors.fromApplication(
            applicationContext,
            SubscriptionAutoUpdateEntryPoint::class.java,
        ).remoteProfileUpdater()
        val result = updater.updateDueProfiles()
        if (result.failed == 0) return Result.success()
        AutoUpdateNotifier.notifyFailures(applicationContext, result.failures)
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}

object SubscriptionAutoUpdateScheduler {
    const val WORK_NAME = "kclash-subscription-auto-update"
    private const val PERIOD_MINUTES = 20L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionAutoUpdateWorker>(
            PERIOD_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

internal object AutoUpdateNotifier {
    private const val NOTIFICATION_ID = 2

    fun notifyFailures(context: Context, failures: List<AutoUpdateFailure>) {
        if (failures.isEmpty()) return
        context.ensureNotificationChannel()
        val detail = failures.take(2).joinToString("；") { failure ->
            "${failure.label}：${failure.message}"
        }.take(300)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, GlobalState.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_shortcut_toggle)
            .setContentTitle("KClash 订阅自动更新失败")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
