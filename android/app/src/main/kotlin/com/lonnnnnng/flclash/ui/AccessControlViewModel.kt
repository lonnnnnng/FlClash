package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.common.AccessSortType
import com.lonnnnnng.flclash.data.ClipboardGateway
import com.lonnnnnng.flclash.service.AccessControlSettingsProvider
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.models.AccessControlProps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val hasInternetPermission: Boolean = true,
    val lastUpdateTime: Long = 0L,
)

data class AccessControlUiState(
    val settings: AccessControlProps = AccessControlDefaults.value,
    val persistedSettings: AccessControlProps = AccessControlDefaults.value,
    val apps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSmartSelecting: Boolean = false,
    val error: String? = null,
) {
    val visibleApps: List<InstalledAppInfo>
        get() {
            val query = searchQuery.trim()
            val selected = currentPackageList(settings).toSet()
            val filtered = apps.filter { app ->
                (!settings.isFilterSystemApp || !app.isSystem) &&
                    (!settings.isFilterNonInternetApp || app.hasInternetPermission) &&
                    (query.isEmpty() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true))
            }
            return sortVisibleApps(filtered, selected, settings.sort)
        }

    val selectedCount: Int
        get() {
            val selected = currentPackageList(settings).toSet()
            return visibleApps.count { it.packageName in selected }
        }

    val hasUnsavedChanges: Boolean
        get() = normalizeAccessControlSettings(settings) != normalizeAccessControlSettings(persistedSettings)
}

object AccessControlDefaults {
    val value = AccessControlProps(
        enable = false,
        mode = AccessControlMode.REJECT_SELECTED,
        acceptList = emptyList(),
        rejectList = emptyList(),
    )
}

fun sortVisibleApps(
    apps: List<InstalledAppInfo>,
    selectedPackages: Set<String>,
    sort: AccessSortType,
): List<InstalledAppInfo> {
    val comparator = compareByDescending<InstalledAppInfo> { it.packageName in selectedPackages }
        .let { base ->
            when (sort) {
                AccessSortType.NONE -> base
                AccessSortType.NAME -> base.thenBy { it.label.lowercase() }.thenBy { it.packageName }
                AccessSortType.TIME -> base.thenByDescending { it.lastUpdateTime }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName }
            }
        }
    return apps.sortedWith(comparator)
}

fun currentPackageList(settings: AccessControlProps): List<String> = when (settings.mode) {
    AccessControlMode.ACCEPT_SELECTED -> settings.acceptList
    AccessControlMode.REJECT_SELECTED -> settings.rejectList
}

fun AccessControlProps.withCurrentPackageList(packages: Collection<String>): AccessControlProps {
    val normalized = packages.filter(String::isNotBlank).distinct().sorted()
    return when (mode) {
        AccessControlMode.ACCEPT_SELECTED -> copy(acceptList = normalized)
        AccessControlMode.REJECT_SELECTED -> copy(rejectList = normalized)
    }
}

fun normalizeAccessControlSettings(settings: AccessControlProps): AccessControlProps = settings.copy(
    acceptList = settings.acceptList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
    rejectList = settings.rejectList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
)

/**
 * 作者 long：剪贴板导入要保持 Flutter 的原始文本语义，不能复用页面勾选时的排序和空值清洗，
 * 否则用户从其他客户端复制的换行列表会在导入瞬间被改写；保存时再进入现有设置清洗边界。
 */
fun parseAccessClipboardList(text: String): List<String> = text.split('\n').distinct()

fun serializeAccessClipboardList(settings: AccessControlProps): String =
    currentPackageList(settings).joinToString("\n")

fun AccessControlProps.withImportedCurrentPackageList(packages: Collection<String>): AccessControlProps {
    val imported = packages.toList().distinct()
    return when (mode) {
        AccessControlMode.ACCEPT_SELECTED -> copy(acceptList = imported)
        AccessControlMode.REJECT_SELECTED -> copy(rejectList = imported)
    }
}

data class SmartSelectionResult(
    val acceptList: List<String>,
    val rejectList: List<String>,
)

fun buildSmartSelection(
    apps: List<InstalledAppInfo>,
    chinaPackages: Set<String>,
): SmartSelectionResult {
    val packageNames = apps.map { it.packageName }.toSet()
    return SmartSelectionResult(
        acceptList = (packageNames - chinaPackages).sorted(),
        rejectList = (packageNames intersect chinaPackages).sorted(),
    )
}

@HiltViewModel
class AccessControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsProvider: AccessControlSettingsProvider,
    private val runtimeManager: CoreRuntimeStatusProvider,
    private val clipboardGateway: ClipboardGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccessControlUiState())
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val state: StateFlow<AccessControlUiState> = mutableState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccessControlUiState())
    val errors = mutableErrors.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            runCatching {
                settingsProvider.current() to loadInstalledApps(context)
            }.onSuccess { (settings, apps) ->
                val persisted = normalizeAccessControlSettings(settings)
                mutableState.value = mutableState.value.copy(
                    settings = persisted,
                    persistedSettings = persisted,
                    apps = apps,
                    isLoading = false,
                    error = null,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.message ?: "读取按应用代理设置失败",
                )
                mutableErrors.emit(error.message ?: "读取按应用代理设置失败")
            }
        }
    }

    fun setSearchQuery(value: String) {
        mutableState.value = mutableState.value.copy(searchQuery = value)
    }

    fun setEnabled(enabled: Boolean) {
        updateSettings { copy(enable = enabled) }
    }

    fun setMode(mode: AccessControlMode) {
        updateSettings { copy(mode = mode) }
    }

    fun setSort(sort: AccessSortType) {
        updateSettings { copy(sort = sort) }
    }

    fun setFilterSystemApps(enabled: Boolean) {
        updateSettings { copy(isFilterSystemApp = enabled) }
    }

    fun setFilterNonInternetApps(enabled: Boolean) {
        updateSettings { copy(isFilterNonInternetApp = enabled) }
    }

    fun togglePackage(packageName: String) {
        updateSettings {
            val selected = currentPackageList(this).toMutableSet()
            if (!selected.add(packageName)) selected.remove(packageName)
            withCurrentPackageList(selected)
        }
    }

    fun selectVisibleApps() {
        updateSettings {
            withCurrentPackageList(currentPackageList(this).toSet() + mutableState.value.visibleApps.map { it.packageName })
        }
    }

    fun clearVisibleApps() {
        updateSettings {
            withCurrentPackageList(currentPackageList(this).toSet() - mutableState.value.visibleApps.map { it.packageName }.toSet())
        }
    }

    fun smartSelectApps() {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return
        }
        if (mutableState.value.isSmartSelecting) return
        viewModelScope.launch {
            val apps = mutableState.value.apps
            mutableState.value = mutableState.value.copy(isSmartSelecting = true, error = null)
            runCatching {
                withContext(Dispatchers.Default) {
                    val chinaPackages = apps.asSequence()
                        .map { it.packageName }
                        .filter(ChinaPackageMatcher::isChinaPackage)
                        .toSet()
                    buildSmartSelection(apps, chinaPackages)
                }
            }.onSuccess { selection ->
                mutableState.value = mutableState.value.copy(
                    settings = mutableState.value.settings.copy(
                        acceptList = selection.acceptList,
                        rejectList = selection.rejectList,
                    ),
                    isSmartSelecting = false,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isSmartSelecting = false,
                    error = error.message ?: "智能选择应用失败",
                )
                mutableErrors.emit(error.message ?: "智能选择应用失败")
            }
        }
    }

    fun exportToClipboard() {
        if (!canEditClipboard()) return
        runCatching {
            clipboardGateway.writeText(serializeAccessClipboardList(mutableState.value.settings))
        }.onFailure { error ->
            reportClipboardError("导出剪贴板失败", error)
        }
    }

    fun importFromClipboard() {
        if (!canEditClipboard()) return
        runCatching { clipboardGateway.readText() }
            .onSuccess { text ->
                if (text == null) return@onSuccess
                mutableState.value = mutableState.value.copy(
                    settings = mutableState.value.settings.withImportedCurrentPackageList(
                        parseAccessClipboardList(text),
                    ),
                    error = null,
                )
            }
            .onFailure { error ->
                reportClipboardError("从剪贴板导入失败", error)
            }
    }

    fun save(onSuccess: () -> Unit = {}) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            val normalized = normalizeAccessControlSettings(mutableState.value.settings)
            runCatching { settingsProvider.save(normalized) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        settings = normalized,
                        persistedSettings = normalized,
                        isSaving = false,
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        error = error.message ?: "保存按应用代理设置失败",
                    )
                    mutableErrors.emit(error.message ?: "保存按应用代理设置失败")
                }
        }
    }

    fun discardUnsavedChanges() {
        mutableState.value = mutableState.value.copy(
            settings = mutableState.value.persistedSettings,
            error = null,
        )
    }

    private fun updateSettings(update: AccessControlProps.() -> AccessControlProps) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return
        }
        mutableState.value = mutableState.value.copy(settings = mutableState.value.settings.update())
    }

    private fun canEditClipboard(): Boolean {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return false
        }
        return !mutableState.value.isLoading &&
            !mutableState.value.isSaving &&
            !mutableState.value.isSmartSelecting
    }

    private fun reportClipboardError(prefix: String, error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() }?.let { "$prefix：$it" } ?: prefix
        mutableState.value = mutableState.value.copy(error = message)
        mutableErrors.tryEmit(message)
    }

}

private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager
        .queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            InstalledAppInfo(
                packageName = packageName,
                label = resolveInfo.loadLabel(context.packageManager).toString()
                    .trim()
                    .ifEmpty { packageName },
                isSystem = resolveInfo.activityInfo.applicationInfo?.let {
                    it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                } == true,
                hasInternetPermission = context.packageManager.checkPermission(
                    Manifest.permission.INTERNET,
                    packageName,
                ) == PackageManager.PERMISSION_GRANTED,
                lastUpdateTime = runCatching {
                    context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                }.getOrDefault(0L),
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}
