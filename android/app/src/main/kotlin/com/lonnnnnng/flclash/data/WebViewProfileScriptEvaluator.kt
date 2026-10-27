package com.lonnnnnng.flclash.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.lonnnnnng.flclash.service.ProfileScriptEvaluator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 作者 long：Android 手机端使用系统 WebView/V8 执行现代 JavaScript，避免 Rhino 无法支持
 * 对象展开、可选链和 async 的兼容缺口；每次执行创建独立页面并关闭网络与文件访问。
 */
class WebViewProfileScriptEvaluator(
    context: Context,
) : ProfileScriptEvaluator {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun evaluate(script: String, configJson: String): String =
        withTimeout(TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                evaluateOnMain(script, configJson)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun evaluateOnMain(script: String, configJson: String): String =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(appContext)
            val finished = AtomicBoolean(false)
            val started = AtomicBoolean(false)

            fun cleanup() {
                webView.removeJavascriptInterface(BRIDGE_NAME)
                webView.stopLoading()
                webView.destroy()
            }

            fun complete(result: Result<String>) {
                if (!finished.compareAndSet(false, true)) return
                mainHandler.post {
                    cleanup()
                    if (!continuation.isActive) return@post
                    result.fold(
                        onSuccess = continuation::resume,
                        onFailure = continuation::resumeWithException,
                    )
                }
            }

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    mainHandler.post(::cleanup)
                }
            }

            configure(webView)
            webView.addJavascriptInterface(ResultBridge(::complete), BRIDGE_NAME)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view == null || !started.compareAndSet(false, true)) return
                    runCatching {
                        view.evaluateJavascript(buildInvocation(script, configJson), null)
                    }.onFailure { complete(Result.failure(it)) }
                }
            }
            webView.loadDataWithBaseURL(
                null,
                "<html><body></body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }

    private fun configure(view: WebView) {
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.blockNetworkLoads = true
        view.settings.blockNetworkImage = true
        view.settings.domStorageEnabled = false
        view.settings.javaScriptCanOpenWindowsAutomatically = false
        view.settings.setSupportMultipleWindows(false)
    }

    private fun buildInvocation(script: String, configJson: String): String {
        val configLiteral = gson.toJson(configJson)
        val bridge = "window.$BRIDGE_NAME"
        return "(function() {\n" +
            "  var original = JSON.parse($configLiteral);\n" +
            "  function fail(error) {\n" +
            "    var message = String(error && (error.stack || error.message) || error);\n" +
            "    $bridge.reject(message);\n" +
            "  }\n" +
            "  function finish(value) {\n" +
            "    try {\n" +
            "      var effective = (value === undefined || value === null) ? original : value;\n" +
            "      if (typeof effective !== 'object' || Array.isArray(effective)) " +
            "        throw new Error('script must return a configuration object');\n" +
            "      var encoded = JSON.stringify(effective);\n" +
            "      if (typeof encoded !== 'string') throw new Error('script result is not JSON');\n" +
            "      $bridge.resolve(encoded);\n" +
            "    } catch (error) { fail(error); }\n" +
            "  }\n" +
            "  try {\n" +
            script + "\n" +
            "    if (typeof main !== 'function') throw new Error('script does not define main()');\n" +
            "    Promise.resolve(main(original)).then(finish, fail);\n" +
            "  } catch (error) { fail(error); }\n" +
            "})()"
    }

    private class ResultBridge(
        private val callback: (Result<String>) -> Unit,
    ) {
        @JavascriptInterface
        fun resolve(value: String) = callback(Result.success(value))

        @JavascriptInterface
        fun reject(message: String) = callback(Result.failure(IllegalStateException(message)))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val BRIDGE_NAME = "KClashBridge"
    }
}
