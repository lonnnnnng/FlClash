package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.RuntimeResumeStore

/**
 * 作者 long：只保存是否应在进程重建后恢复核心，不保存 VPN 句柄或临时运行状态。
 */
class RoomRuntimeResumeStore(
    private val preferences: AppPreferenceDao,
) : RuntimeResumeStore {
    override suspend fun shouldResume(): Boolean =
        preferences.get(RUNTIME_RESUME_KEY) == "true"

    override suspend fun setShouldResume(value: Boolean) {
        if (value) {
            preferences.put(
                AppPreferenceEntity(
                    key = RUNTIME_RESUME_KEY,
                    value = "true",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            preferences.delete(RUNTIME_RESUME_KEY)
        }
    }

    private companion object {
        const val RUNTIME_RESUME_KEY = "runtime_should_resume"
    }
}
