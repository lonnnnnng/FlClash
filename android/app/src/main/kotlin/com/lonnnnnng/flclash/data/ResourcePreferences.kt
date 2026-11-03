package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceType

data class ResourcePreferences(val icons: List<ResourceIconSet>, val filters: List<FilterPreset>, val geo: GeoResourceSettings)

/** 作者 long：全局资源偏好独立于配置依赖，完整校验归档后才允许写入 Room 和 Geo 设置。 */
object ResourcePreferencesCodec {
    fun encode(value: ResourcePreferences): String = JsonObject().apply {
        add("icons", JsonParser.parseString(IconSetCodec.encode(value.icons)))
        add("filters", JsonParser.parseString(FilterPresets.encode(value.filters)))
        val settings = value.geo.validated()
        add("geo", JsonObject().apply {
            addProperty("autoUpdate", settings.autoUpdate); addProperty("intervalHours", settings.intervalHours)
            add("urls", JsonObject().apply { settings.urls.forEach { (type, url) -> addProperty(type.name, url) } })
        })
    }.toString().also { require(it.toByteArray().size <= 8 * 1024 * 1024) { "资源设置超过容量限制" } }
    fun decode(raw: String): ResourcePreferences {
        require(raw.toByteArray().size <= 8 * 1024 * 1024) { "资源设置超过容量限制" }
        val root = JsonParser.parseString(raw).asJsonObject
        val icons = IconSetCodec.decode(root["icons"]?.toString() ?: error("备份缺少图标集"))
        val filters = FilterPresets.decode(root["filters"]?.toString() ?: error("备份缺少过滤预设"))
        val geo = root.getAsJsonObject("geo") ?: error("备份缺少 Geo 设置")
        val auto = geo["autoUpdate"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: error("Geo 自动更新设置无效")
        val interval = geo["intervalHours"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toIntOrNull() ?: error("Geo 周期无效")
        val urls = geo.getAsJsonObject("urls") ?: error("Geo 地址缺失")
        val settings = GeoResourceSettings(auto, interval, GeoResourceType.entries.associateWith { type ->
            urls[type.name]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: error("Geo 地址缺失")
        }).validated()
        return ResourcePreferences(icons, filters, settings)
    }
    fun merge(existing: ResourcePreferences, incoming: ResourcePreferences): ResourcePreferences {
        val ids = incoming.icons.map { it.id }.toSet()
        val icons = existing.icons.filterNot { it.id in ids }.toMutableList()
        incoming.icons.forEach { row ->
            val base = row.name.take(118)
            val name = if (icons.none { it.name == row.name }) row.name else generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { name -> icons.none { it.name == name } }
            icons += row.copy(name = name)
        }
        val labels = incoming.filters.map { it.label }.toSet()
        return ResourcePreferences(icons, existing.filters.filterNot { it.label in labels } + incoming.filters, incoming.geo).also(::encode)
    }
}
