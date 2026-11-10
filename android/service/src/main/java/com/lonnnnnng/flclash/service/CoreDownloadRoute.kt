package com.lonnnnnng.flclash.service

/** 作者 long：下载只借用已应用入站，凭据不进入data class自动toString或持久化任务。 */
class CoreDownloadRoute(val port: Int?, val username: String?, val password: String?, val revision: Long) {
    override fun toString() = "CoreDownloadRoute(proxied=${port != null}, revision=$revision)"
}
