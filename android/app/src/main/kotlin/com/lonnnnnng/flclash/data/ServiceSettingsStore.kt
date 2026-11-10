package com.lonnnnnng.flclash.data

import android.content.Context
import com.lonnnnnng.flclash.core.ServiceTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class ServiceSettings(
    val order: List<ServiceTarget> = ServiceTarget.entries.toList(),
    val disabled: Set<ServiceTarget> = emptySet(),
) {
    val enabled get() = order.filterNot(disabled::contains)
    fun normalized(): ServiceSettings {
        val normalizedOrder = (order + ServiceTarget.entries).distinct()
        // 作者 long：旧偏好或损坏数据不能让服务列表变空，至少保留一个可以检测的目标。
        return copy(order = normalizedOrder, disabled = disabled.takeIf { it.size < normalizedOrder.size }.orEmpty())
    }
}

interface ServiceSettingsStore {
    fun read(): ServiceSettings
    fun write(settings: ServiceSettings)
}

class AndroidServiceSettingsStore @Inject constructor(@ApplicationContext context: Context) : ServiceSettingsStore {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, "service_status")
    override fun read(): ServiceSettings {
        fun targets(key: String) = preferences.getString(key, null).orEmpty().split(',').mapNotNull { id -> ServiceTarget.entries.firstOrNull { it.id == id } }
        return ServiceSettings(targets("order"), targets("disabled").toSet()).normalized()
    }
    override fun write(settings: ServiceSettings) {
        check(preferences.edit().putString("order", settings.order.joinToString(",") { it.id })
            .putString("disabled", settings.disabled.joinToString(",") { it.id }).commit()) { "无法保存服务设置" }
    }
}
