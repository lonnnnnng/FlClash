package com.lonnnnnng.flclash.ui

/** 作者 long：只计数本例固定SQL标记，不记录名称、正文或异常内容；正式失败链路仍由真实Room执行。 */
object ProfileManagementProbe {
    @Volatile var databaseAborts: Int = 0
        private set
    fun failed(error: Exception) {
        if (generateSequence(error as Throwable) { it.cause }.take(8).any {
                it is android.database.sqlite.SQLiteConstraintException && it.message.orEmpty().contains("fixture-management-save-failure")
            }) databaseAborts++
    }
}
