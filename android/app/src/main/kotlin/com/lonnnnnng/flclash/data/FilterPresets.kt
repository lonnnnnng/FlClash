package com.lonnnnnng.flclash.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val FILTER_PRESETS_KEY = "global_filter_presets"
data class FilterPreset(val label: String, val regex: String)

object FilterPresets {
    fun parts(value: String): List<String> = value.split('`').filter(String::isNotEmpty)
    fun isApplied(preset: FilterPreset, value: String): Boolean = parts(preset.regex).let { own ->
        own.isNotEmpty() && parts(value).containsAll(own)
    }
    // 作者 long：预设只是追加表达式片段，保存后不绑定预设名称；预设改名或删除不能改变已有配置。
    fun apply(preset: FilterPreset, value: String): String =
        (parts(value) + parts(preset.regex)).distinct().joinToString("`")

    fun encode(rows: List<FilterPreset>): String {
        require(rows.size <= 256 && rows.map { it.label }.distinct().size == rows.size) { "预设目录无效或超过 256 项" }
        rows.forEach {
            require(it.label.isNotBlank() && it.label == it.label.trim() && it.label.length <= 128) { "名称须为 1 至 128 字符" }
            require(it.regex.isNotBlank() && it.regex == it.regex.trim() && it.regex.length <= 8192 && parts(it.regex).isNotEmpty()) { "过滤表达式为空或过长" }
        }
        return Gson().toJson(rows).also { require(it.toByteArray().size <= 4 * 1024 * 1024) { "预设目录超过容量限制" } }
    }
    fun decode(value: String?): List<FilterPreset> {
        if (value == null) return emptyList()
        require(value.toByteArray().size <= 4 * 1024 * 1024) { "预设目录超过容量限制" }
        val root = JsonParser.parseString(value)
        require(root.isJsonArray && root.asJsonArray.size() <= 256) { "预设目录无效" }
        return root.asJsonArray.map { element ->
            val item = element.asJsonObject
            fun string(key: String): String = item[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: error("预设字段无效")
            FilterPreset(string("label"), string("regex"))
        }.also(::encode)
    }
}

@Singleton
class FilterPresetRepository @Inject constructor(
    database: KClashDatabase,
    private val validator: ProxyGroupFilterValidator,
) {
    private val preferences = database.appPreferences()
    private val lock = Mutex()
    val changes = preferences.observe(FILTER_PRESETS_KEY).map(FilterPresets::decode)
    suspend fun all() = FilterPresets.decode(preferences.get(FILTER_PRESETS_KEY))
    private suspend fun write(rows: List<FilterPreset>) = preferences.put(AppPreferenceEntity(FILTER_PRESETS_KEY, FilterPresets.encode(rows), System.currentTimeMillis()))
    suspend fun save(original: FilterPreset?, label: String, regex: String) {
        val next = FilterPreset(label.trim(), regex.trim())
        FilterPresets.encode(listOf(next))
        // 作者 long：先用 Mihomo regexp2 校验，目录锁只保护最终提交，防止较慢的核心校验阻塞列表操作。
        val errors = validator.validate(listOf(next.regex))
        require(errors.size == 1 && errors.single().isEmpty()) { "过滤表达式无效，请检查后重试" }
        lock.withLock {
            val rows = all()
            check(original == null || rows.firstOrNull { it.label == original.label } == original) { "预设已变化，请重新打开" }
            require(rows.none { it.label == next.label && it.label != original?.label }) { "预设名称已存在" }
            write(if (original == null) rows + next else rows.map { if (it.label == original.label) next else it })
        }
    }
    suspend fun delete(values: List<FilterPreset>) = lock.withLock {
        // 作者 long：只删除仍与用户选中时一致的版本，避免批选期间覆盖其他页面的新编辑。
        write(all().filterNot { it in values })
    }
    suspend fun reorder(labels: List<String>) = lock.withLock {
        val rows = all()
        write(labels.distinct().mapNotNull { name -> rows.firstOrNull { it.label == name } } + rows.filterNot { it.label in labels })
    }
}
