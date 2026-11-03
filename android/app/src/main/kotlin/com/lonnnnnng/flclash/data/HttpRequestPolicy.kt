package com.lonnnnnng.flclash.data

import android.annotation.SuppressLint
import com.lonnnnnng.flclash.service.GeneralSettings
import java.net.HttpURLConnection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/** 作者 long：仅在用户明确关闭证书校验的本次客户端使用宽松TLS，不修改进程或系统的默认校验器。 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
internal object UserSelectedTlsPolicy {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val socketFactory by lazy { SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }.socketFactory }
    val hostnameVerifier = HostnameVerifier { _, _ -> true }
}

internal fun applyHttpRequestPolicy(connection: HttpURLConnection, settings: GeneralSettings?) {
    settings?.userAgent?.let { connection.setRequestProperty("User-Agent", it) }
    if (connection is HttpsURLConnection && settings?.checkCertificate == false) {
        connection.sslSocketFactory = UserSelectedTlsPolicy.socketFactory
        connection.hostnameVerifier = UserSelectedTlsPolicy.hostnameVerifier
    }
}

internal fun OkHttpClient.withRequestPolicy(settings: GeneralSettings?): OkHttpClient =
    if (settings?.checkCertificate != false) this else newBuilder()
        .sslSocketFactory(UserSelectedTlsPolicy.socketFactory, UserSelectedTlsPolicy.trustManager)
        .hostnameVerifier(UserSelectedTlsPolicy.hostnameVerifier).build()
