package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimePaths
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class ResourceKind { GEO, ICONS, RULE, SCRIPT }
data class ResourceUpdateRow(val kind: ResourceKind, val id: String, val label: String, val url: String,
    val updatedAt: Long? = null, val size: Long? = null, val count: Int? = null) {
    val key get() = "${kind.name}:$id"
}
data class ResourceUpdateFailure(val label: String, val message: String)
data class ResourceUpdatesState(
    val loading: Boolean = true,
    val settings: GeoResourceSettings = GeoResourceSettings(),
    val rows: List<ResourceUpdateRow> = emptyList(),
    val iconSets: List<ResourceIconSet> = emptyList(),
    val updating: Set<String> = emptySet(),
    val settingsBusy: Boolean = false,
    val failures: List<ResourceUpdateFailure> = emptyList(),
    val message: String? = null,
)

@Singleton
class ResourceUpdatesRepository @Inject constructor(
    private val runtime: CoreRuntimeManager,
    private val paths: CoreRuntimePaths,
    private val icons: IconSetRepository,
    private val rules: RuleResourceRepository,
    private val scripts: ScriptRepository,
    private val profiles: ProfileStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val refreshLock = Mutex()
    private val mutable = MutableStateFlow(ResourceUpdatesState(settings = runtime.geoSettings.value))
    val state = mutable.asStateFlow()
    private val manual = mutableSetOf<String>()

    init {
        scope.launch { merge(icons.changes.map { Unit }, rules.changes.map { Unit }, scripts.scripts.map { Unit },
            runtime.geoSettings.map { Unit }, runtime.geoUpdates.map { Unit }).collect { refresh() } }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshLock.withLock {
        try {
            val sets = icons.all()
            val settings = runtime.geoSettings.value
            val rows = buildList {
                GeoResourceType.entries.forEach { type ->
                    val file = File(paths.homeDir, type.fileName)
                    add(ResourceUpdateRow(ResourceKind.GEO, type.name, type.name, settings.urls.getValue(type),
                        file.takeIf(File::isFile)?.lastModified(), file.takeIf(File::isFile)?.length()))
                }
                sets.filter { it.url.isNotEmpty() }.forEach { add(ResourceUpdateRow(ResourceKind.ICONS, it.id, it.name, it.url, it.updatedAt, count = it.icons.size)) }
                rules.all().filter { it.url.isNotEmpty() }.forEach {
                    val file = rules.file(it)
                    add(ResourceUpdateRow(ResourceKind.RULE, it.id, it.label, it.url,
                        file.takeIf(File::isFile)?.lastModified(), file.takeIf(File::isFile)?.length()))
                }
                scripts.scripts.value.filter { it.url.isNotEmpty() }.forEach {
                    add(ResourceUpdateRow(ResourceKind.SCRIPT, it.id.toString(), it.label, it.url,
                        it.lastUpdateAt, scripts.scriptFile(it.id).takeIf(File::isFile)?.length()))
                }
            }
            lock.withLock {
                val coreUpdating = runtime.geoUpdates.value.values.filter { it.updating }.map { "GEO:${it.type}" }.toSet()
                mutable.update { it.copy(loading = false, rows = rows, settings = settings, iconSets = sets, updating = manual + coreUpdating) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(loading = false, failures = listOf(ResourceUpdateFailure("资源列表", "读取失败，请重试"))) } }
    } }

    suspend fun saveSettings(settings: GeoResourceSettings) {
        if (mutable.value.settingsBusy) return
        mutable.update { it.copy(settingsBusy = true, failures = emptyList(), message = null) }
        try { runtime.setGeoSettings(settings); refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(failures = listOf(ResourceUpdateFailure("资源设置", "保存失败，请等待更新完成并检查地址和周期后重试"))) } }
        finally { mutable.update { it.copy(settingsBusy = false) } }
    }

    suspend fun update(row: ResourceUpdateRow): ResourceUpdateFailure? {
        val claimed = lock.withLock {
            if (row.key in mutable.value.updating) false else {
                manual.add(row.key); mutable.update { it.copy(updating = it.updating + row.key) }; true
            }
        }
        if (!claimed) return null
        return try {
            when (row.kind) {
                ResourceKind.GEO -> {
                    runtime.requestGeoUpdate(GeoResourceType.valueOf(row.id))
                    // 作者 long：等待核心下载完成，不能把已受理当成成功；超时后仍由核心状态保留更新标记。
                    val completion = withTimeout(180_000L) { runtime.geoUpdates.first { it[row.id]?.updating == false }.getValue(row.id) }
                    check(completion.error.isEmpty()) { "Geo 下载失败" }
                }
                ResourceKind.ICONS -> icons.sync(icons.all().first { it.id == row.id && it.url == row.url })
                ResourceKind.RULE -> {
                    val expected = rules.all().first { it.id == row.id && it.url == row.url }
                    val saved = rules.sync(expected)
                    val currentId = profiles.currentProfileId.value
                    if (currentId != null && rules.isUsedBy(currentId, saved)) {
                        try { runtime.applyCurrentProfile(currentId) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { return ResourceUpdateFailure(row.label, "资源已保存，但当前配置应用失败，请在配置页重试应用") }
                    }
                }
                // 作者 long：脚本与上游一样只同步资源，绑定配置在下一次显式应用时执行，下载不能隐式运行远程代码。
                ResourceKind.SCRIPT -> scripts.sync(row.id.toLong())
            }
            null
        } catch (_: TimeoutCancellationException) { ResourceUpdateFailure(row.label, "等待超时；下载可能仍在后台进行，请稍后查看") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ResourceUpdateFailure(row.label, "更新失败，请检查网络或资源地址后重试") }
        finally {
            lock.withLock { manual.remove(row.key) }
            refresh()
        }
    }

    suspend fun updateRows(rows: List<ResourceUpdateRow>) {
        mutable.update { it.copy(failures = emptyList(), message = null) }
        val semaphore = Semaphore(4)
        val failures = coroutineScope { rows.map { row -> async { semaphore.withPermit { update(row) } } }.awaitAll().filterNotNull() }
        mutable.update { it.copy(failures = failures, message = if (failures.isEmpty()) "资源更新完成" else null) }
    }

    suspend fun updateDue(now: Long = System.currentTimeMillis()): Boolean {
        refresh()
        val current = mutable.value
        if (!current.settings.autoUpdate) return true
        val interval = current.settings.intervalHours * 3_600_000L
        val due = current.rows.filter { it.kind != ResourceKind.GEO &&
            (it.kind != ResourceKind.RULE || runtime.state.value.isRunning) &&
            (it.updatedAt == null || now - it.updatedAt >= interval) }
        if (due.isNotEmpty()) updateRows(due)
        return mutable.value.failures.isEmpty()
    }
    fun clearFeedback() { mutable.update { it.copy(failures = emptyList(), message = null) } }
}
