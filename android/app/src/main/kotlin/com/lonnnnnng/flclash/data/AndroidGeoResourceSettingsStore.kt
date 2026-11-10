package com.lonnnnnng.flclash.data

import android.content.Context
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceSettingsStore
import com.lonnnnnng.flclash.service.GeoResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidGeoResourceSettingsStore @Inject constructor(@ApplicationContext context: Context) : GeoResourceSettingsStore {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context, "resource_settings")
    private val mutableChanges = kotlinx.coroutines.flow.MutableStateFlow(read())
    override val changes = mutableChanges.asStateFlow()
    override fun read(): GeoResourceSettings = runCatching {
        GeoResourceSettings(preferences.getBoolean("auto_update", false), preferences.getInt("interval_hours", 24),
            GeoResourceType.entries.associateWith { preferences.getString(it.name, it.defaultUrl) ?: it.defaultUrl }).validated()
    }.getOrDefault(GeoResourceSettings())

    @Synchronized override fun write(settings: GeoResourceSettings) {
        val value = settings.validated()
        val editor = preferences.edit().putBoolean("auto_update", value.autoUpdate).putInt("interval_hours", value.intervalHours)
        value.urls.forEach { (type, url) -> editor.putString(type.name, url) }
        // 作者 long：持久化失败要向调用者报错，不能让开关只在当前进程显示已保存。
        check(editor.commit()) { "资源设置保存失败" }
        mutableChanges.value = value
    }
}
