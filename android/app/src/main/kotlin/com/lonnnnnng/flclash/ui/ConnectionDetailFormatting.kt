package com.lonnnnnng.flclash.ui

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun formatConnectionStart(raw: String, zone: TimeZone = TimeZone.getDefault()): String {
    val match = CONNECTION_START_PATTERN.matchEntire(raw) ?: return raw
    // 作者 long：Mihomo 的 RFC3339Nano 时间可带纳秒；详情只展示本地秒，先截为毫秒，避免旧版 Android 日期解析把小数当成额外秒数。
    val fraction = match.groupValues[2].removePrefix(".").padEnd(3, '0').take(3)
    val normalized = "${match.groupValues[1]}.$fraction${match.groupValues[3]}"
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    val date = parser.parse(normalized, position)?.takeIf { position.index == normalized.length } ?: return raw
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = zone }.format(date)
}

internal fun connectionProcessText(process: String, uid: Long): String =
    if (uid == 0L) process.trim() else "${process}($uid)".trim()

private val CONNECTION_START_PATTERN = Regex(
    "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})$",
)
