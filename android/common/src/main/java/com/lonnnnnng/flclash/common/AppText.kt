package com.lonnnnnng.flclash.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** 作者 long：只翻译应用自有文案，调用方不得把节点名、配置正文或核心日志送入此入口。 */
object AppText {
    @Volatile private var english: Context? = null
    @Volatile var useEnglish: Boolean = false
    private val parameter = Regex("\\{(\\d+)\\}")
    private val cache = object : LinkedHashMap<String, String>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 256
    }

    fun initialize(context: Context) {
        // 作者 long：attachBaseContext时Application尚未登记到LoadedApk，不能再取尚为空的applicationContext。
        val owner = if (context is android.app.Application) context else context.applicationContext
        english = owner.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
    }

    fun text(source: String, vararg arguments: Any?): String {
        val template = if (useEnglish) translatedTemplate(source) else source
        // 作者 long：一次替换保留参数中的花括号、美元符号和中文名称，不把用户内容再次当作翻译模板。
        if (arguments.isNotEmpty()) return parameter.replace(template) { match ->
            arguments.getOrNull(match.groupValues[1].toInt())?.toString().orEmpty()
        }
        return template
    }

    private fun translatedTemplate(source: String): String {
        val context = english ?: return source
        AppTextCatalog.ids[source]?.let { return context.getString(it) }
        if (source.none { it in '\u4e00'..'\u9fff' }) return source
        synchronized(cache) { cache[source]?.let { return it } }
        // 作者 long：旧错误对象仍保留原始中文供业务判定，只在显示边界匹配完整模板；不改异常原文或持久化数据。
        val translated = AppTextCatalog.patterns.firstNotNullOfOrNull { entry ->
            entry.pattern.matchEntire(source)?.let { match ->
                parameter.replace(context.getString(entry.resource)) { token ->
                    val index = entry.parameters.indexOf(token.groupValues[1].toInt())
                    match.groupValues.getOrNull(index + 1).orEmpty()
                }
            }
        } ?: source
        synchronized(cache) { cache[source] = translated }
        return translated
    }
}
