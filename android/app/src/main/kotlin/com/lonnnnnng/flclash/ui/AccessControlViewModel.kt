package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.common.AccessControlMode
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
import kotlinx.coroutines.launch

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

data class AccessControlUiState(
    val settings: AccessControlProps = AccessControlDefaults.value,
    val apps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val visibleApps: List<InstalledAppInfo>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return apps
            return apps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }

    val selectedCount: Int
        get() = currentPackageList(settings).size
}

object AccessControlDefaults {
    val value = AccessControlProps(
        enable = false,
        mode = AccessControlMode.REJECT_SELECTED,
        acceptList = emptyList(),
        rejectList = emptyList(),
    )
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

@HiltViewModel
class AccessControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsProvider: AccessControlSettingsProvider,
    private val runtimeManager: CoreRuntimeStatusProvider,
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
                mutableState.value = mutableState.value.copy(
                    settings = settings.sanitize(),
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

    fun save() {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            runCatching { settingsProvider.save(mutableState.value.settings.sanitize()) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        settings = mutableState.value.settings.sanitize(),
                        isSaving = false,
                    )
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

    private fun updateSettings(update: AccessControlProps.() -> AccessControlProps) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再修改按应用代理")
            return
        }
        mutableState.value = mutableState.value.copy(settings = mutableState.value.settings.update())
    }

    private fun AccessControlProps.sanitize(): AccessControlProps = copy(
        acceptList = acceptList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
        rejectList = rejectList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
    )
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
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}
