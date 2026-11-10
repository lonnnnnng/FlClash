package com.lonnnnnng.flclash.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 作者 long：恢复策略是用户的持续选择；退出页面或重建Activity不应悄悄退回兼容模式。 */
@Singleton
class BackupUiPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context, "backup_ui")
    fun read(): BackupRestoreStrategy = preferences.getString("strategy", null)?.let { saved ->
        BackupRestoreStrategy.entries.firstOrNull { it.name == saved }
    } ?: BackupRestoreStrategy.COMPATIBLE
    fun write(value: BackupRestoreStrategy) {
        check(preferences.edit().putString("strategy", value.name).commit()) { "恢复策略保存失败" }
    }
}
