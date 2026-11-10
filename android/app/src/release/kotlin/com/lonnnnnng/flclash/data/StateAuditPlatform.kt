package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** 作者 long：正式包不加载诊断会话或导出记录，持久化继续走原Framework驱动。 */
object StateAuditPlatform {
    fun initialize(context: Context) = Unit
    fun factory(): SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
}
