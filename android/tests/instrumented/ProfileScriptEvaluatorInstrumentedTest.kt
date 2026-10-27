package com.lonnnnnng.flclash.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：真机验证使用系统 WebView/V8 执行现代 Profile JavaScript，确认 Android 实际引擎
 * 支持 Flutter 脚本契约需要的对象展开、可选链、Map/Set 和 Promise。
 */
@RunWith(AndroidJUnit4::class)
class ProfileScriptEvaluatorInstrumentedTest {
    @Test
    fun modernProfileScriptReturnsJsonObject() = runBlocking {
        val evaluator = WebViewProfileScriptEvaluator(
            ApplicationProvider.getApplicationContext(),
        )
        val script = """
            const labels = new Map([['HK', '🇭🇰']]);
            function main(config) {
              const names = [...new Set(config.proxies.map((proxy) => proxy.name))];
              return {
                ...config,
                names: names.map((name) => `${'$'}{labels.get('HK')} ${'$'}{name}`),
                first: config.proxies?.[0]?.name ?? null,
              };
            }
        """.trimIndent()

        val result = evaluator.evaluate(
            script = script,
            configJson = "{\"proxies\":[{\"name\":\"direct\"},{\"name\":\"direct\"}]}",
        )

        assertTrue(result.contains("\"first\":\"direct\""))
        assertTrue(result.contains("🇭🇰 direct"))
    }

    @Test
    fun rejectedPromiseFailsClosed() = runBlocking {
        val evaluator = WebViewProfileScriptEvaluator(
            ApplicationProvider.getApplicationContext(),
        )
        val error = runCatching {
            evaluator.evaluate(
                script = "async function main() { throw new Error('broken profile') }",
                configJson = "{}",
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("broken profile"))
    }

    @Test
    fun missingEntryPointFailsClosed() = runBlocking {
        val evaluator = WebViewProfileScriptEvaluator(
            ApplicationProvider.getApplicationContext(),
        )

        val error = runCatching {
            evaluator.evaluate("const value = 1", "{}")
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("main"))
    }

    @Test
    fun unresolvedPromiseIsCancelledByTheTenSecondBoundary() = runBlocking {
        val evaluator = WebViewProfileScriptEvaluator(
            ApplicationProvider.getApplicationContext(),
        )
        val started = System.currentTimeMillis()

        val error = runCatching {
            evaluator.evaluate(
                script = "function main() { return new Promise(() => {}) }",
                configJson = "{}",
            )
        }.exceptionOrNull()

        assertTrue(error is kotlinx.coroutines.TimeoutCancellationException)
        assertTrue(System.currentTimeMillis() - started < 15_000L)
    }
}
