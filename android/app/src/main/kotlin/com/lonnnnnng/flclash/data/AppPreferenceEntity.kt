package com.lonnnnnng.flclash.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 作者 long：第一阶段只保存 Kotlin 应用自己的键值偏好，避免把旧 Flutter 数据库结构带入新应用。
 */
@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)
