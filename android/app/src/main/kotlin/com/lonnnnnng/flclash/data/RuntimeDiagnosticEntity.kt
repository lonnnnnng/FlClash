package com.lonnnnnng.flclash.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind

/**
 * 作者 long：运行诊断需要在应用进程重建后保留，但只保存生命周期和网络摘要，不把订阅正文或请求内容写入数据库。
 */
@Entity(
    tableName = "runtime_diagnostic_events",
    indices = [Index(value = ["timestampMillis"])],
)
data class RuntimeDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val detail: String,
    val timestampMillis: Long,
)

fun RuntimeDiagnosticEntity.toRuntimeDiagnosticEvent(): RuntimeDiagnosticEvent? =
    runCatching {
        RuntimeDiagnosticEvent(
            kind = RuntimeDiagnosticKind.valueOf(kind),
            detail = detail,
            timestampMillis = timestampMillis,
        )
    }.getOrNull()

fun RuntimeDiagnosticEvent.toRuntimeDiagnosticEntity(): RuntimeDiagnosticEntity =
    RuntimeDiagnosticEntity(
        kind = kind.name,
        detail = detail,
        timestampMillis = timestampMillis,
    )
