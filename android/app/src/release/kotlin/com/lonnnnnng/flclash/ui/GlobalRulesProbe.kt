package com.lonnnnnng.flclash.ui

/** 作者 long：发布变体不保留数据库故障诊断计数和测试标记。 */
object GlobalRulesProbe {
    fun failed(@Suppress("UNUSED_PARAMETER") error: Exception) = Unit
}
