package com.lonnnnnng.flclash.core

enum class ServiceTarget(val id: String, val label: String) {
    GOOGLE("google", "Google"), GITHUB("github", "GitHub"), YOUTUBE("youtube", "YouTube"),
    CHATGPT("chatgpt", "ChatGPT"), CLAUDE("claude", "Claude"), GEMINI("gemini", "Gemini"),
    NETFLIX("netflix", "Netflix"), DISNEY_PLUS("disney-plus", "Disney+"), PRIME_VIDEO("prime-video", "Prime Video"),
    SPOTIFY("spotify", "Spotify"), TIKTOK("tiktok", "TikTok"), DISCORD("discord", "Discord"), BILIBILI("bilibili", "Bilibili");
}

enum class ServiceStatus(val wire: String) {
    AVAILABLE("available"), UNAVAILABLE("unavailable"), RESTRICTED("restricted"),
    DISALLOWED_ISP("disallowed-isp"), BLOCKED("blocked"), UNSUPPORTED_REGION("unsupported-region"),
    ORIGINALS_ONLY("originals-only"), COMING_SOON("coming-soon"), TIMEOUT("timeout"), FAILED("failed");
    val retryable: Boolean get() = this == TIMEOUT || this == FAILED
}

/** 作者 long：检测结果保留路由版本，切配置或节点后不能把旧可用性归到当前出口。 */
data class CoreServiceCheck(
    val target: ServiceTarget,
    val status: ServiceStatus,
    val delayMillis: Long? = null,
    val region: String? = null,
    val chains: List<String> = emptyList(),
    val checkedAt: Long,
    val stamp: CoreRouteStamp,
    val hits: List<CoreRouteHit> = emptyList(),
)

/** 作者 long：完整命中信息才可核验；缺失出口的失败记录不能成为可复用证据。 */
data class CoreRouteHit(val host: String, val proxy: String, val exit: String) {
    val reusable: Boolean get() = (host.isNotBlank() || proxy.isNotBlank()) && exit.isNotBlank()
}

data class CoreRouteCheck(val stamp: CoreRouteStamp, val held: List<Boolean>)
