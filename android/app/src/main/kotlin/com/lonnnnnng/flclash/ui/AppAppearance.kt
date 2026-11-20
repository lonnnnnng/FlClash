package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.common.AppText
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 作者 long：读取快照使已打开的页面随语种重绘；不通过销毁组合或重启Activity清空未提交草稿。
internal object UiLanguage { var english by mutableStateOf(false) }
internal fun tr(source: String, vararg arguments: Any?): String {
    UiLanguage.english // 作者 long：语言偏好变化使实际读取文案的组合失效，草稿状态保持。
    return AppText.text(source, *arguments)
}

@Composable
internal fun AppAppearance(activity: ComponentActivity, settings: AppearanceSettings, content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val systemLanguage = if (android.os.Build.VERSION.SDK_INT >= 33)
        activity.getSystemService(android.app.LocaleManager::class.java).systemLocales[0].language else config.locales[0].language
    val english = settings.language.english(systemLanguage)
    val dark = settings.theme.dark(isSystemInDarkTheme())
    // 作者 long：界面与非Compose通知共享语种，但不修改系统Locale或用户其他应用的语言。
    UiLanguage.english = english
    AppText.useEnglish = english
    SideEffect {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    val localizedConfig = remember(config, english) { android.content.res.Configuration(config).apply {
        setLocale(if (english) java.util.Locale.ENGLISH else java.util.Locale.SIMPLIFIED_CHINESE)
    } }
    val localizedContext = remember(activity, localizedConfig) { android.view.ContextThemeWrapper(activity, 0).apply { applyOverrideConfiguration(localizedConfig) } }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig, LocalAppMenuGlass provides settings.glass) { KClashTheme(darkTheme = dark, content = content) }
}

@Composable
internal fun AppearancePicker(language: Boolean, store: AppearanceSettingsStore, onDismiss: () -> Unit) {
    val settings by store.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(value: AppearanceSettings, close: Boolean = true) {
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { store.write(value) } }
            busy = false
            if (result.isSuccess) { if (close) onDismiss() } else error = if (language) "语言设置保存失败，请重试" else "主题设置保存失败，请重试"
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag(if (language) "language_picker" else "theme_picker"),
        title = { Text(tr(if (language) "语言" else "主题")) }, text = {
            Column {
                val entries = if (language) AppLanguage.entries.map { it.name } else AppTheme.entries.map { it.name }
                entries.forEach { name ->
                    val selected = name == if (language) settings.language.name else settings.theme.name
                    val label = when (name) { "CHINESE" -> "中文"; "ENGLISH" -> "English"; "LIGHT" -> "浅色"; "DARK" -> "深色"; else -> "跟随系统" }
                    Surface(onClick = { save(if (language) settings.copy(language = AppLanguage.valueOf(name)) else settings.copy(theme = AppTheme.valueOf(name))) },
                        enabled = !busy, color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().testTag("appearance_${if (language) "language" else "theme"}_${name.lowercase()}")) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            RadioButton(selected, null)
                            Text(tr(label), modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                        }
                    }
                }
                if (!language) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(tr("玻璃效果"), Modifier.weight(1f))
                    // 作者 long：材质为独立可选项，首次安装仍关闭；不改变深浅/系统三态，也不重载代理核心。
                    Switch(settings.glass, { save(settings.copy(glass = it), close = false) }, enabled = !busy,
                        modifier = Modifier.testTag("appearance_glass"))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("取消")) } })
}
