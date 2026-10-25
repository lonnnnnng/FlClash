package com.lonnnnnng.flclash.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class ProfileTypeConverters {
    @TypeConverter
    fun encode(value: ProfileType): String = value.name

    @TypeConverter
    fun decode(value: String): ProfileType = ProfileType.valueOf(value)
}

/**
 * 作者 long：Room 只管理 KClash 自己的模型；第二版迁移只为第一阶段已经存在的 KClash 数据补充
 * profiles 表，不读取 Flutter/Drift 的历史数据库。
 */
@Database(entities = [AppPreferenceEntity::class, ProfileEntity::class], version = 2, exportSchema = false)
@TypeConverters(ProfileTypeConverters::class)
abstract class KClashDatabase : RoomDatabase() {
    abstract fun appPreferences(): AppPreferenceDao

    abstract fun profiles(): ProfileDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profiles (
                        id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        url TEXT NOT NULL,
                        ageSecretKey TEXT NOT NULL,
                        lastUpdateAt INTEGER,
                        autoUpdate INTEGER NOT NULL,
                        autoUpdateIntervalMillis INTEGER NOT NULL,
                        uploadBytes INTEGER NOT NULL,
                        downloadBytes INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        expireAtSeconds INTEGER NOT NULL,
                        currentGroupName TEXT,
                        selectedMapJson TEXT NOT NULL,
                        unfoldSetJson TEXT NOT NULL,
                        extendType TEXT NOT NULL,
                        scriptId INTEGER,
                        matchTarget TEXT,
                        overridesJson TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_profiles_url ON profiles(url)")
            }
        }
    }
}
