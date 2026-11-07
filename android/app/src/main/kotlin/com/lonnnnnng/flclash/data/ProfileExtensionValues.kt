package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.ProfileExtensionOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides

/** 作者 long：扩展提交只比较自己拥有的字段，既拒绝旧规则覆盖，也允许DNS等其他分区独立保存。 */
data class ProfileExtensionValues(
    val mode: String,
    val scriptId: Long?,
    val matchTarget: String?,
    val settings: ProfileExtensionOverrides,
) {
    override fun toString() = "ProfileExtensionValues([REDACTED])"

    companion object {
        fun from(profile: Profile): ProfileExtensionValues {
            val settings = ProfileProviderOverrides.fromJsonString(profile.overridesJson).extension
            val mode = if (profile.extendType == "script" || !settings.configured && profile.scriptId != null) "script" else "standard"
            return ProfileExtensionValues(mode, profile.scriptId, profile.matchTarget, settings)
        }
    }
}
