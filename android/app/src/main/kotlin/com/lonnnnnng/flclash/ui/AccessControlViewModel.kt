package com.lonnnnnng.flclash.ui

import android.content.Context
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
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

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
    val appsError: String? = null,
    val appsPermissionRequired: Boolean = false,
    val settingsReady: Boolean = true,
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
            // 作者 long：状态数代表全部已安装且已选应用，搜索与来源过滤只改变视图，不能使总数跳变。
            return apps.count { it.packageName in selected }
        }

    // 作者 long：顶栏全选图标必须只反映当前搜索、排序和来源过滤后的可见集合，避免把隐藏应用误判为已全选。
    val allVisibleAppsSelected: Boolean
        get() {
            if (visibleApps.isEmpty()) return false
            val selected = currentPackageList(settings).toSet()
            return visibleApps.all { it.packageName in selected }
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

private val accessPackageName = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

// 作者 long：剪贴板可能混入说明、空行或CRLF；只采纳合法包名，零有效项交由入口拒绝，不能清空已有名单。
fun parseAccessClipboardList(text: String): List<String> = text.lineSequence().map(String::trim)
    .filter { accessPackageName.matches(it) }.distinct().toList()

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
    settings: AccessControlProps = AccessControlDefaults.value,
): SmartSelectionResult {
    // 作者 long：智能选择沿Flutter只处理系统/联网过滤范围，搜索不参与；隐藏和暂未安装的既有选择须保留。
    val packageNames = apps.filter {
        (!settings.isFilterSystemApp || !it.isSystem) && (!settings.isFilterNonInternetApp || it.hasInternetPermission)
    }.map { it.packageName }.toSet()
    return SmartSelectionResult(
        acceptList = (settings.acceptList.toSet() - packageNames + (packageNames - chinaPackages)).sorted(),
        rejectList = (settings.rejectList.toSet() - packageNames + (packageNames intersect chinaPackages)).sorted(),
    )
}

internal data class InstalledAppsSnapshot(val apps: List<InstalledAppInfo>, val permissionRequired: Boolean = false)

@HiltViewModel
class AccessControlViewModel internal constructor(
    private val settingsProvider: AccessControlSettingsProvider,
    private val runtimeManager: CoreRuntimeStatusProvider,
    private val clipboardGateway: ClipboardGateway,
    private val appsLoader: suspend () -> InstalledAppsSnapshot,
    private val chinaMatcher: suspend (List<InstalledAppInfo>) -> Set<String>,
) : ViewModel() {
    @Inject constructor(@ApplicationContext context: Context, settingsProvider: AccessControlSettingsProvider,
        runtimeManager: CoreRuntimeStatusProvider, clipboardGateway: ClipboardGateway) : this(
        settingsProvider, runtimeManager, clipboardGateway,
        { withContext(Dispatchers.IO) { loadInstalledApps(context) } },
        { apps -> withContext(Dispatchers.Default) { apps.map { it.packageName }.filter(ChinaPackageMatcher::isChinaPackage).toSet() } },
    )

    private val mutableState = MutableStateFlow(AccessControlUiState(settingsReady = false))
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var loadJob: Job? = null
    private var smartJob: Job? = null
    private var revision = 0L
    private var settingsLoaded = false

    val state: StateFlow<AccessControlUiState> = mutableState.asStateFlow()
    val errors = mutableErrors.asSharedFlow()

    init {
        viewModelScope.launch {
            // 作者 long：即使启停在扫描期间又回到STOPPED，也不能采纳跨过该运行窗口的旧扫描结果。
            runtimeManager.state.collect { status ->
                if (status !in listOf(CoreRuntimeStatus.STOPPED, CoreRuntimeStatus.FAILED)) cancelSmartSelection()
            }
        }
        reload()
    }

    fun reload() {
        if (mutableState.value.isSaving) return
        cancelSmartSelection()
        loadJob?.cancel()
        settingsLoaded = false
        mutableState.value = mutableState.value.copy(isLoading = true, settingsReady = false, error = null)
        loadJob = viewModelScope.launch {
            try {
                val settings = settingsProvider.current()
                currentCoroutineContext().ensureActive()
                val persisted = normalizeAccessControlSettings(settings)
                settingsLoaded = true
                mutableState.value = mutableState.value.copy(
                    settings = persisted, persistedSettings = persisted, settingsReady = true,
                )
                readApps()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isLoading = false, error = "读取按应用代理设置失败，请重试")
                mutableErrors.emit("读取按应用代理设置失败，请重试")
            }
        }
    }

    fun refreshApps() {
        // 作者 long：设置页往返只重查安装信息和OEM授权，不能用磁盘旧值覆盖未保存的名单草稿。
        if (!settingsLoaded || mutableState.value.isLoading || mutableState.value.isSaving) return
        cancelSmartSelection()
        mutableState.value = mutableState.value.copy(isLoading = true)
        loadJob = viewModelScope.launch { readApps() }
    }

    fun retryLoad() { if (settingsLoaded) refreshApps() else reload() }

    fun reportAppsSettingsUnavailable() {
        mutableState.value = mutableState.value.copy(appsError = "无法打开应用设置，请在系统设置中找到KClash并允许读取应用列表")
    }

    private suspend fun readApps() {
        try {
            val result = appsLoader()
            currentCoroutineContext().ensureActive()
            mutableState.value = mutableState.value.copy(isLoading = false, appsError = null,
                apps = if (result.permissionRequired) mutableState.value.apps else result.apps,
                appsPermissionRequired = result.permissionRequired)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // 作者 long：包查询失败不代表应用被卸载，保留上次完整清单及已保存/未保存选择，等待显式重试。
            mutableState.value = mutableState.value.copy(isLoading = false, appsError = "读取应用列表失败，已保留原列表和选择，请重试")
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

    // 作者 long：Flutter 的顶栏批量选择只修改当前可见应用，保留过滤条件之外的既有选择。
    fun toggleVisibleApps() {
        updateSettings {
            val visiblePackages = mutableState.value.visibleApps.map { it.packageName }.toSet()
            if (visiblePackages.isEmpty()) return@updateSettings this
            val selected = currentPackageList(this).toMutableSet()
            if (selected.containsAll(visiblePackages)) {
                selected.removeAll(visiblePackages)
            } else {
                selected.addAll(visiblePackages)
            }
            withCurrentPackageList(selected)
        }
    }

    fun smartSelectApps() {
        if (!canEditClipboard() || mutableState.value.apps.isEmpty() || mutableState.value.appsPermissionRequired || mutableState.value.appsError != null) return
        val start = mutableState.value
        val expectedRevision = ++revision
        val expectedRuntimeRevision = runtimeManager.editRevision
        mutableState.value = start.copy(isSmartSelecting = true, error = null)
        smartJob = viewModelScope.launch {
            try {
                val selection = buildSmartSelection(start.apps, chinaMatcher(start.apps), start.settings)
                if (expectedRevision != revision) return@launch
                check(runtimeEditable() && runtimeManager.editRevision == expectedRuntimeRevision) { "核心状态已变化，请停止核心后重试智能选择" }
                mutableState.value = mutableState.value.copy(
                    settings = start.settings.copy(acceptList = selection.acceptList, rejectList = selection.rejectList),
                    isSmartSelecting = false,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (expectedRevision == revision) {
                    mutableState.value = mutableState.value.copy(isSmartSelecting = false, error = "智能选择未完成，原名单已保留，请在核心停止后重试")
                }
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
        runCatching {
            clipboardGateway.readText()?.let { text ->
                parseAccessClipboardList(text).also {
                    require(it.isNotEmpty()) { "剪贴板中没有有效包名，请每行填写一个包名" }
                }
            }
        }.onSuccess { packages ->
            if (packages == null) return@onSuccess
            revision++
            mutableState.value = mutableState.value.copy(
                settings = mutableState.value.settings.withImportedCurrentPackageList(packages), error = null,
            )
        }.onFailure { error -> reportClipboardError("从剪贴板导入失败", error) }
    }

    fun save(onSuccess: () -> Unit = {}) {
        if (!canEditClipboard()) return
        val normalized = normalizeAccessControlSettings(mutableState.value.settings)
        mutableState.value = mutableState.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                runtimeManager.withProfileWrite {
                    // 作者 long：保存与启停共用核心串行锁；等待锁期间若已启动，不写入只对下次TUN生效的旧名单。
                    check(runtimeEditable()) { "核心状态已变化，请停止核心后保存" }
                    settingsProvider.save(normalized)
                }
                mutableState.value = mutableState.value.copy(
                    settings = normalized, persistedSettings = normalized, isSaving = false,
                )
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = mutableState.value.copy(isSaving = false, error = error.message ?: "保存按应用代理设置失败")
                mutableErrors.emit(error.message ?: "保存按应用代理设置失败")
            } finally { mutableState.value = mutableState.value.copy(isSaving = false) }
        }
    }

    fun discardUnsavedChanges() {
        if (mutableState.value.isSaving) return
        cancelSmartSelection()
        mutableState.value = mutableState.value.copy(
            settings = mutableState.value.persistedSettings,
            error = null,
        )
    }

    private fun updateSettings(update: AccessControlProps.() -> AccessControlProps) {
        if (!canEditClipboard()) return
        revision++
        mutableState.value = mutableState.value.copy(settings = mutableState.value.settings.update(), error = null)
    }

    private fun canEditClipboard(): Boolean {
        if (!runtimeEditable()) {
            mutableErrors.tryEmit("核心正在运行或启停，请先停止核心后再修改按应用代理")
            return false
        }
        return settingsLoaded && !mutableState.value.isLoading &&
            !mutableState.value.isSaving &&
            !mutableState.value.isSmartSelecting
    }

    private fun runtimeEditable() = runtimeManager.state.value in listOf(CoreRuntimeStatus.STOPPED, CoreRuntimeStatus.FAILED)

    private fun cancelSmartSelection() {
        revision++
        smartJob?.cancel()
        mutableState.value = mutableState.value.copy(isSmartSelecting = false)
    }

    private fun reportClipboardError(prefix: String, error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() }?.let { "$prefix：$it" } ?: prefix
        mutableState.value = mutableState.value.copy(error = message)
        mutableErrors.tryEmit(message)
    }

}

private fun loadInstalledApps(context: Context): InstalledAppsSnapshot {
    val manager = context.packageManager
    val oemPermission = "com.android.permission.GET_INSTALLED_APPS"
    val permissionExists = try { manager.getPermissionInfo(oemPermission, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    if (permissionExists && manager.checkPermission(oemPermission, context.packageName) != PackageManager.PERMISSION_GRANTED)
        return InstalledAppsSnapshot(emptyList(), permissionRequired = true)
    // 作者 long：VPN包过滤同样作用于后台服务包；仅查Launcher会让用户无法选择无桌面入口的已安装应用。
    val packages = if (android.os.Build.VERSION.SDK_INT >= 33)
        manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    else {
        @Suppress("DEPRECATION")
        manager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    }
    return InstalledAppsSnapshot(packages
        .asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.packageName
            if (packageName == context.packageName || packageName == "android") return@mapNotNull null
            InstalledAppInfo(
                packageName = packageName,
                label = runCatching { resolveInfo.applicationInfo?.loadLabel(manager)?.toString() }.getOrNull().orEmpty()
                    .trim()
                    .ifEmpty { packageName },
                isSystem = resolveInfo.applicationInfo?.let {
                    it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                } == true,
                hasInternetPermission = Manifest.permission.INTERNET in resolveInfo.requestedPermissions.orEmpty(),
                lastUpdateTime = resolveInfo.lastUpdateTime,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName })
        .toList())
}
