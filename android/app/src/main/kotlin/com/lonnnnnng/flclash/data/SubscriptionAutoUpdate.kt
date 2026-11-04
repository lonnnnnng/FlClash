package com.lonnnnnng.flclash.data

data class AutoUpdateFailure(
    val profileId: Long,
    val label: String,
    val message: String,
)

data class AutoUpdateResult(
    val attempted: Int,
    val succeeded: Int,
    val failures: List<AutoUpdateFailure>,
    val applyFailures: List<AutoUpdateFailure> = emptyList(),
    val downloadedIds: List<Long> = emptyList(),
    val appliedSourceIds: List<Long> = emptyList(),
    val checks: List<AutoUpdateCheck> = emptyList(),
) {
    val failed: Int get() = failures.size + applyFailures.size
}

data class AutoUpdateCheck(val profileId: Long, val lastUpdateAt: Long?, val intervalMillis: Long, val due: Boolean)

/** 作者 long：网络下载和解密先完成，只有原子提交与受影响配置应用进入核心生命周期锁。 */
fun interface RemoteProfileCommitter {
    suspend fun commit(sourceId: Long, write: suspend () -> Profile): Profile
}

interface RemoteProfileUpdater {
    suspend fun updateDueProfiles(nowMillis: Long = System.currentTimeMillis()): AutoUpdateResult
}

internal fun isAutoUpdateDue(profile: Profile, nowMillis: Long): Boolean {
    val lastUpdated = profile.lastUpdateAt ?: return true
    val interval = profile.autoUpdateIntervalMillis.coerceAtLeast(15 * 60 * 1_000L)
    return lastUpdated <= nowMillis - interval
}
