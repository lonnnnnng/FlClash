package com.lonnnnnng.flclash.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.URI

enum class ProfileType {
    FILE,
    URL,
    CUSTOM,
}

/**
 * 订阅服务器通过 `subscription-userinfo` 返回的用量信息；数值统一按字节和 Unix 秒保存，
 * 这样页面、通知和后续自动更新逻辑都不会各自解析一遍供应商格式。
 */
data class SubscriptionInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
) {
    val usage: Float?
        get() = if (total > 0) ((upload + download).toFloat() / total).coerceIn(0f, 1f) else null

    val expireAtMillis: Long?
        get() = if (expire > 0) expire * 1_000L else null

    companion object {
        fun parseHeader(value: String?): SubscriptionInfo {
            if (value.isNullOrBlank()) return SubscriptionInfo()
            val values = value.split(';').mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator < 0) return@mapNotNull null
                val key = field.substring(0, separator).trim().lowercase()
                val number = field.substring(separator + 1).trim().toDoubleOrNull()
                    ?.takeIf { it.isFinite() }
                    ?.toLong()
                    ?: return@mapNotNull null
                key to number
            }.toMap()
            return SubscriptionInfo(
                upload = values["upload"] ?: 0,
                download = values["download"] ?: 0,
                total = values["total"] ?: 0,
                expire = values["expire"] ?: 0,
            )
        }
    }
}

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["url"], unique = false)],
)
data class ProfileEntity(
    @PrimaryKey val id: Long,
    val type: ProfileType,
    val label: String,
    val url: String,
    val ageSecretKey: String,
    val lastUpdateAt: Long?,
    val autoUpdate: Boolean,
    val autoUpdateIntervalMillis: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireAtSeconds: Long,
    val currentGroupName: String?,
    val selectedMapJson: String,
    val unfoldSetJson: String,
    val extendType: String,
    val scriptId: Long?,
    val matchTarget: String?,
    val overridesJson: String,
    val orderIndex: Int,
)

data class Profile(
    val id: Long,
    val type: ProfileType,
    val label: String,
    val url: String,
    val ageSecretKey: String,
    val lastUpdateAt: Long?,
    val autoUpdate: Boolean,
    val autoUpdateIntervalMillis: Long,
    val subscriptionInfo: SubscriptionInfo?,
    val orderIndex: Int,
    val currentGroupName: String? = null,
    val selectedMapJson: String = "{}",
    val unfoldSetJson: String = "[]",
    val extendType: String = "standard",
    val scriptId: Long? = null,
    val matchTarget: String? = null,
    val overridesJson: String = "{}",
) {
    val realLabel: String
        get() = label.trim().ifEmpty { defaultLabel }

    val defaultLabel: String
        get() = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: id.toString()

    val fileName: String
        get() = "$id.yaml"

    fun toEntity(): ProfileEntity = ProfileEntity(
        id = id,
        type = type,
        label = label,
        url = url,
        ageSecretKey = ageSecretKey,
        lastUpdateAt = lastUpdateAt,
        autoUpdate = autoUpdate,
        autoUpdateIntervalMillis = autoUpdateIntervalMillis,
        uploadBytes = subscriptionInfo?.upload ?: 0,
        downloadBytes = subscriptionInfo?.download ?: 0,
        totalBytes = subscriptionInfo?.total ?: 0,
        expireAtSeconds = subscriptionInfo?.expire ?: 0,
        currentGroupName = currentGroupName,
        selectedMapJson = selectedMapJson,
        unfoldSetJson = unfoldSetJson,
        extendType = extendType,
        scriptId = scriptId,
        matchTarget = matchTarget,
        overridesJson = overridesJson,
        orderIndex = orderIndex,
    )

    companion object {
        const val DEFAULT_UPDATE_INTERVAL_MILLIS = 86_400_000L

        fun remote(id: Long, url: String, label: String = "") = Profile(
            id = id,
            type = ProfileType.URL,
            label = label,
            url = url,
            ageSecretKey = "",
            lastUpdateAt = null,
            autoUpdate = true,
            autoUpdateIntervalMillis = DEFAULT_UPDATE_INTERVAL_MILLIS,
            subscriptionInfo = null,
            orderIndex = 0,
        )

        fun custom(id: Long, label: String) = Profile(
            id = id,
            type = ProfileType.CUSTOM,
            label = label,
            url = "",
            ageSecretKey = "",
            lastUpdateAt = System.currentTimeMillis(),
            autoUpdate = false,
            autoUpdateIntervalMillis = DEFAULT_UPDATE_INTERVAL_MILLIS,
            subscriptionInfo = null,
            orderIndex = 0,
        )
    }
}

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    type = type,
    label = label,
    url = url,
    ageSecretKey = ageSecretKey,
    lastUpdateAt = lastUpdateAt,
    autoUpdate = autoUpdate,
    autoUpdateIntervalMillis = autoUpdateIntervalMillis,
    subscriptionInfo = if (uploadBytes == 0L && downloadBytes == 0L && totalBytes == 0L && expireAtSeconds == 0L) {
        null
    } else {
        SubscriptionInfo(uploadBytes, downloadBytes, totalBytes, expireAtSeconds)
    },
    orderIndex = orderIndex,
    currentGroupName = currentGroupName,
    selectedMapJson = selectedMapJson,
    unfoldSetJson = unfoldSetJson,
    extendType = extendType,
    scriptId = scriptId,
    matchTarget = matchTarget,
    overridesJson = overridesJson,
)
