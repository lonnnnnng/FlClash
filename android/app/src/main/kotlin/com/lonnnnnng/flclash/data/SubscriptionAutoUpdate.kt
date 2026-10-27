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
) {
    val failed: Int get() = failures.size
}

interface RemoteProfileUpdater {
    suspend fun updateDueProfiles(nowMillis: Long = System.currentTimeMillis()): AutoUpdateResult
}

internal fun isAutoUpdateDue(profile: Profile, nowMillis: Long): Boolean {
    val lastUpdated = profile.lastUpdateAt ?: return true
    val interval = profile.autoUpdateIntervalMillis.coerceAtLeast(15 * 60 * 1_000L)
    return lastUpdated <= nowMillis - interval
}
