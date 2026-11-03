package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ResourceAutoUpdateEntryPoint { fun resourceUpdates(): ResourceUpdatesRepository }

/** 作者 long：Geo 由核心调度，其余远程资源按同一周期扫描；后台任务不为更新而启动 VPN。 */
class ResourceAutoUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val updates = EntryPointAccessors.fromApplication(applicationContext, ResourceAutoUpdateEntryPoint::class.java).resourceUpdates()
        return if (updates.updateDue() || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
object ResourceAutoUpdateScheduler {
    const val WORK_NAME = "kclash-resource-auto-update"
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ResourceAutoUpdateWorker>(20, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
