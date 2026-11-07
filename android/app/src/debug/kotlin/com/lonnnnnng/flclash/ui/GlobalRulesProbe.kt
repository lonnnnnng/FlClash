package com.lonnnnnng.flclash.ui

/** 作者 long：只计数固定验收故障，不记录规则正文或数据库异常文本。 */
object GlobalRulesProbe {
    @Volatile var databaseAborts: Int = 0
        private set
    fun failed(error: Exception) {
        if (generateSequence(error as Throwable) { it.cause }.take(8).any {
                it is android.database.sqlite.SQLiteConstraintException && it.message.orEmpty().contains("fixture-global-rules-failure")
            }) databaseAborts++
    }
}
