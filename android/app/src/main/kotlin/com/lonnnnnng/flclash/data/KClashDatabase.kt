package com.lonnnnnng.flclash.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ProfileTypeConverters {
    @TypeConverter
    fun encode(value: ProfileType): String = value.name

    @TypeConverter
    fun decode(value: String): ProfileType = ProfileType.valueOf(value)
}

/**
 * 作者 long：Room 只管理 KClash 自己的模型；诊断事件和 Profiles 都属于 Kotlin 应用自己的数据，
 * 不读取 Flutter/Drift 的历史数据库。开发阶段结构变化直接重建，不保留旧版本迁移链。
 */
@Database(
    entities = [
        AppPreferenceEntity::class,
        ProfileEntity::class,
        RuntimeDiagnosticEntity::class,
        ScriptEntity::class,
        ProxyDelayEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(ProfileTypeConverters::class)
abstract class KClashDatabase : RoomDatabase() {
    abstract fun appPreferences(): AppPreferenceDao

    abstract fun profiles(): ProfileDao

    abstract fun runtimeDiagnostics(): RuntimeDiagnosticDao

    abstract fun scripts(): ScriptDao

    abstract fun proxyDelayResults(): ProxyDelayDao

}
