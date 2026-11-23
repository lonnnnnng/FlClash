package com.lonnnnnng.flclash.update

import com.lonnnnnng.flclash.BuildConfig
import com.lonnnnnng.flclash.service.CoreDownloadRoute
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class UpdatePhase { IDLE, CHECKING, LATEST, AVAILABLE, FAILED }
data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: AppRelease? = null,
    val failure: UpdateFailure? = null,
)

enum class UpdateRouteKind { CORE_HTTP_PROXY, DIRECT, UNKNOWN }
enum class UpdateFailureKind { CORE_TRANSITION, CORE_ROUTE, DNS, TIMEOUT, TLS, CONNECT, HTTP, RESPONSE, NETWORK, UNKNOWN }

data class UpdateFailure(val route: UpdateRouteKind, val kind: UpdateFailureKind, val httpStatus: Int? = null) {
    val diagnosticCode: String
        get() = "${route.name}/${kind.name}" + (httpStatus?.let { "/$it" } ?: "")
}

internal object UpdateFailureDiagnostics {
    // 作者 long：只保留能判断失败阶段的类型码，避免异常原文、请求地址或认证信息进入页面状态。
    fun classify(error: Throwable, route: CoreDownloadRoute?, runtimeStatus: CoreRuntimeStatus): UpdateFailure {
        val routeKind = when {
            route?.port != null -> UpdateRouteKind.CORE_HTTP_PROXY
            route != null -> UpdateRouteKind.DIRECT
            else -> UpdateRouteKind.UNKNOWN
        }
        if (route == null && (runtimeStatus == CoreRuntimeStatus.STARTING || runtimeStatus == CoreRuntimeStatus.STOPPING)) {
            return UpdateFailure(routeKind, UpdateFailureKind.CORE_TRANSITION)
        }
        if (route == null && runtimeStatus == CoreRuntimeStatus.RUNNING) {
            return UpdateFailure(UpdateRouteKind.UNKNOWN, UpdateFailureKind.CORE_ROUTE)
        }
        val chain = generateSequence(error) { it.cause }.toList()
        val status = chain.filterIsInstance<UpdateHttpStatusException>().firstOrNull()?.statusCode
        val kind = when {
            chain.any { it is UnknownHostException } -> UpdateFailureKind.DNS
            chain.any { it is SocketTimeoutException } -> UpdateFailureKind.TIMEOUT
            chain.any { it is SSLException } -> UpdateFailureKind.TLS
            chain.any { it is ConnectException } -> UpdateFailureKind.CONNECT
            status != null -> UpdateFailureKind.HTTP
            chain.any { it is IllegalArgumentException } -> UpdateFailureKind.RESPONSE
            chain.any { it is IOException } -> UpdateFailureKind.NETWORK
            else -> UpdateFailureKind.UNKNOWN
        }
        return UpdateFailure(routeKind, kind, status)
    }
}

/** 作者 long：应用只检查版本并交给浏览器下载，检查结果留在内存，不维护安装包或后台更新任务。 */
@Singleton
class AppUpdateRepository @Inject constructor(private val runtime: CoreRuntimeManager) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check() = withContext(Dispatchers.IO) {
        if (!lock.tryLock()) return@withContext
        val previous = mutable.value
        mutable.value = UpdateState(UpdatePhase.CHECKING)
        var route: CoreDownloadRoute? = null
        try {
            // 作者 long：VPN运行时让核心接管GitHub出站；停止时才直连，避免更新流量绕过当前代理策略。
            val selectedRoute = runtime.downloadRoute()
            route = selectedRoute
            val latest = UpdateTransport(selectedRoute).use { it.latestRelease() }
            mutable.value = ReleaseCatalog.check(latest, BuildConfig.VERSION_CODE.toLong())
        } catch (cancelled: CancellationException) {
            mutable.value = previous
            throw cancelled
        } catch (failure: Exception) {
            val detail = UpdateFailureDiagnostics.classify(failure, route, runtime.state.value)
            mutable.value = UpdateState(UpdatePhase.FAILED, failure = detail)
        } finally {
            lock.unlock()
        }
    }
}
