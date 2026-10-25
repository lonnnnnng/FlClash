package com.lonnnnnng.flclash.service.models

data class NotificationParams(
    val title: String = "KClash",
    val stopText: String = "STOP",
    val onlyStatisticsProxy: Boolean = false,
    val showStopAction: Boolean = true,
)
