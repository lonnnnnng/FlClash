package com.lonnnnnng.flclash.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 作者 long：脚本元数据只记录可排序的资源信息，JavaScript 正文单独落在应用私有目录，
 * 避免把大段可编辑文本塞进 Room，也为后续远程同步和执行沙箱保留文件边界。
 */
@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: Long,
    val label: String,
    val url: String,
    val lastUpdateAt: Long?,
    val orderIndex: Int,
)

data class Script(
    val id: Long,
    val label: String,
    val url: String = "",
    val lastUpdateAt: Long? = null,
    val orderIndex: Int = 0,
) {
    val fileName: String
        get() = "$id.js"

    fun toEntity() = ScriptEntity(
        id = id,
        label = label,
        url = url,
        lastUpdateAt = lastUpdateAt,
        orderIndex = orderIndex,
    )
}

fun ScriptEntity.toDomain() = Script(
    id = id,
    label = label,
    url = url,
    lastUpdateAt = lastUpdateAt,
    orderIndex = orderIndex,
)
