package com.lonnnnnng.flclash.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 作者 long：新应用先建立独立 Room 边界，业务实体在功能切片中按 Kotlin 模型加入，不读取 Flutter 数据库。
 */
@Database(entities = [AppPreferenceEntity::class], version = 1, exportSchema = false)
abstract class KClashDatabase : RoomDatabase()
