package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProfileExtensionUiState(
    val profile: Profile? = null,
    val settings: ProfileExtensionOverrides = ProfileExtensionOverrides(),
    val globalRules: List<ExtensionRule> = emptyList(),
    val targets: List<String> = emptyList(),
    val mode: String = "standard",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val applying: Boolean = false,
    val isCurrent: Boolean = false,
    val applied: Boolean = false,
    val error: String? = null,
    // 作者 long：相同错误文本也可能来自新的保存尝试；递增结果版本让确认窗口准确结束等待并允许重试。
    val saveResultVersion: Long = 0,
)

/** 作者 long：保存队列与应用队列共用一把锁，退出页必须等全部元数据写完，再把最新输入交给统一核心管理器。 */
internal class ProfileExtensionController(
    private val repository: ProfileStore,
    private val runtime: CoreRuntimeStatusProvider,
    private val scope: CoroutineScope,
    private val parserDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()
    private var generation = 0L
    private var openedProfileId: Long? = null
    private val mutableState = MutableStateFlow(ProfileExtensionUiState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch { repository.currentProfileId.collect { refreshApplied() } }
        scope.launch { runtime.state.collect { refreshApplied() } }
        scope.launch { repository.profiles.collect { refreshApplied() } }
    }

    fun open(id: Long, reload: Boolean = false) {
        // 作者 long：Activity重建重连原会话，不能清空列表/子页或作废已受理保存；读取失败由显式重试重新发起。
        if (!reload && openedProfileId == id) return
        openedProfileId = id
        val token = ++generation
        mutableState.value = ProfileExtensionUiState(loading = true)
        scope.launch {
            lock.withLock {
                try {
                    val profile = requireNotNull(repository.extensionProfile(id)) { "配置不存在" }
                    val settings = ProfileProviderOverrides.fromJsonString(profile.overridesJson).extension
                    val global = repository.globalExtensionRules()
                    val source = repository.readProfileSourceContent(id)
                    val targets = withContext(parserDispatcher) { ProfileExtensionApplier.targets(source) }
                    if (token != generation) return@withLock
                    val mode = if (profile.extendType == "script" || !settings.configured && profile.scriptId != null) "script" else "standard"
                    mutableState.value = ProfileExtensionUiState(profile, settings, global, targets, mode,
                        isCurrent = repository.currentProfileId.value == id, applied = runtime.profileInputsApplied(id))
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (token == generation) mutableState.value = mutableState.value.copy(loading = false, error = "读取扩展失败，请重试")
                }
            }
        }
    }

    fun refreshApplied() {
        val token = generation
        scope.launch {
            lock.withLock {
                val current = mutableState.value
                val id = current.profile?.id ?: return@withLock
                try {
                    val profile = repository.extensionProfile(id)
                    if (profile == null) {
                        if (generation == token) mutableState.value = ProfileExtensionUiState(error = "配置已删除，请返回")
                        return@withLock
                    }
                    val settings = ProfileProviderOverrides.fromJsonString(profile.overridesJson).extension
                    val global = repository.globalExtensionRules()
                    val applied = runtime.profileInputsApplied(id)
                    val mode = if (profile.extendType == "script" || !settings.configured && profile.scriptId != null) "script" else "standard"
                    if (generation == token) mutableState.value = current.copy(profile = profile, settings = settings, mode = mode, globalRules = global,
                        isCurrent = repository.currentProfileId.value == id, applied = applied)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (generation == token) mutableState.value = current.copy(applied = false)
                }
            }
        }
    }

    fun mode(value: String) = save { it.copy(mode = value) }
    fun script(id: Long?) = save { it.copy(profile = it.profile?.copy(scriptId = id)) }
    fun target(value: String?) = save { it.copy(profile = it.profile?.copy(matchTarget = value)) }
    fun rules(value: List<ExtensionRule>) = save { it.copy(settings = it.settings.copy(rules = value)) }
    fun disabled(ids: Set<String>) = save { it.copy(settings = it.settings.copy(disabledGlobalRuleIds = ids)) }

    private fun save(transform: (ProfileExtensionUiState) -> ProfileExtensionUiState) {
        val token = generation
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                val before = mutableState.value
                if (before.profile == null) return@withLock
                mutableState.value = before.copy(saving = true, error = null)
                try {
                    val next = transform(before)
                    val profile = checkNotNull(next.profile)
                    val saved = repository.saveExtension(profile.id, next.mode, profile.scriptId, profile.matchTarget, next.settings)
                    if (token == generation) mutableState.value = next.copy(profile = saved, settings = next.settings.copy(configured = true), saving = false,
                        isCurrent = repository.currentProfileId.value == saved.id, applied = false, error = null,
                        saveResultVersion = before.saveResultVersion + 1)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (token == generation) mutableState.value = before.copy(error = failure.message ?: "扩展保存失败，请重试",
                        saveResultVersion = before.saveResultVersion + 1)
                }
            }
        }
    }

    fun globalRules(rules: List<ExtensionRule>) {
        val token = generation
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                val before = mutableState.value
                mutableState.value = before.copy(saving = true, error = null)
                try {
                    repository.compareAndSaveGlobalExtensionRules(before.globalRules, rules)
                    if (token == generation) mutableState.value = before.copy(globalRules = rules, applied = false, error = null,
                        saveResultVersion = before.saveResultVersion + 1)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (token == generation) mutableState.value = before.copy(error = failure.message ?: "全局规则保存失败，请重试",
                        saveResultVersion = before.saveResultVersion + 1)
                }
            }
        }
    }

    fun apply() = applyOrClose(null)
    fun close(onClosed: () -> Unit) = applyOrClose(onClosed)

    private fun applyOrClose(onClosed: (() -> Unit)?) {
        val token = generation
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                val current = mutableState.value
                val id = current.profile?.id
                try {
                    if (id != null && repository.currentProfileId.value == id && !runtime.profileInputsApplied(id)) {
                        mutableState.value = current.copy(applying = true, error = null)
                        coroutineScope {
                            // 作者 long：快速应用也保留可辨认的进行中状态；等待与核心应用并发，不额外拖延慢应用。
                            val visible = async { delay(600) }
                            try { runtime.applyCurrentProfile(id) } finally { visible.await() }
                        }
                    }
                    if (token == generation) {
                        mutableState.value = mutableState.value.copy(applying = false, applied = id != null && runtime.profileInputsApplied(id), error = null)
                        if (onClosed != null) { generation++; openedProfileId = null; mutableState.value = ProfileExtensionUiState(); onClosed() }
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    // 作者 long：核心应用失败不撤销已保存编辑；原服务由管理器恢复，页面保留重试入口。
                    if (token == generation) mutableState.value = current.copy(applying = false, applied = false,
                        error = failure.message ?: "应用失败，扩展已保存，请重试")
                }
            }
        }
    }

    fun closeWithoutApplying(onClosed: () -> Unit) {
        if (!mutableState.value.saving && !mutableState.value.applying) {
            generation++
            openedProfileId = null
            mutableState.value = ProfileExtensionUiState()
            onClosed()
        }
    }
}
