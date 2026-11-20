package com.lonnnnnng.flclash.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage { SYSTEM, CHINESE, ENGLISH;
    fun english(systemLanguage: String) = this == ENGLISH || this == SYSTEM && systemLanguage != "zh"
}
enum class AppTheme { SYSTEM, LIGHT, DARK;
    fun dark(systemDark: Boolean) = this == DARK || this == SYSTEM && systemDark
}
data class AppearanceSettings(val language: AppLanguage = AppLanguage.SYSTEM, val theme: AppTheme = AppTheme.SYSTEM)

/** 作者 long：显示偏好独立于代理配置，保存失败不发布新状态，切换外观不触发核心重载。 */
@Singleton
class AppearanceSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context, "appearance")
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    fun read() = AppearanceSettings(
        AppLanguage.entries.firstOrNull { it.name == preferences.getString("language", null) } ?: AppLanguage.SYSTEM,
        AppTheme.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: AppTheme.SYSTEM,
    )
    @Synchronized fun write(value: AppearanceSettings) {
        check(preferences.edit().putString("language", value.language.name).putString("theme", value.theme.name).commit()) {
            "Appearance settings could not be saved"
        }
        mutableState.value = value
    }
}
