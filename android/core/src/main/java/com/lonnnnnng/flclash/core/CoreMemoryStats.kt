package com.lonnnnnng.flclash.core

/** 作者 long：Go 运行时统计属于当前进程的一部分，rss 不能与各分项重复相加。 */
data class CoreMemoryStats(
    val rss: Long,
    val heapInuse: Long,
    val heapIdle: Long,
    val stackInuse: Long,
    val runtimeOther: Long,
) {
    val runtimeTotal: Long get() = listOf(heapInuse, heapIdle, stackInuse, runtimeOther)
        .fold(0L) { total, bytes -> if (Long.MAX_VALUE - total < bytes) Long.MAX_VALUE else total + bytes }
}
