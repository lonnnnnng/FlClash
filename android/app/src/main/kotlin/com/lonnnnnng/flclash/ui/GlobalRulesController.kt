package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class GlobalRulesUiState(val rules: List<ExtensionRule> = emptyList(), val loading: Boolean = false,
    val saving: Boolean = false, val applying: Boolean = false, val loaded: Boolean = false, val error: String? = null)

/** 作者 long：全局规则不绑定某个配置的编辑窗口，保存后只尝试应用受理时的当前配置，切换配置不会串目标。 */
internal class GlobalRulesController(private val store: ProfileStore, private val runtime: CoreRuntimeStatusProvider,
    private val scope: CoroutineScope) {
    private val lock = Mutex()
    private var generation = 0L
    private val mutableState = MutableStateFlow(GlobalRulesUiState())
    val state = mutableState.asStateFlow()

    fun open() {
        val token = ++generation
        mutableState.value = GlobalRulesUiState(loading = true)
        scope.launch {
            lock.withLock {
                try {
                    val rules = store.globalExtensionRules()
                    if (token == generation) mutableState.value = GlobalRulesUiState(rules = rules, loaded = true)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (token == generation) mutableState.value = GlobalRulesUiState(error = "读取全局规则失败，请重试")
                }
            }
        }
    }

    fun save(value: List<ExtensionRule>) {
        val before = mutableState.value
        if (!before.loaded || before.loading || before.saving || before.applying) return
        val token = generation
        val target = store.currentProfileId.value
        mutableState.value = before.copy(saving = true, error = null)
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                // 作者 long：受理后的写盘和应用必须完成，页面销毁不应留下“已写盘但尚未报告”的半次保存。
                withContext(NonCancellable) {
                    try {
                        ExtensionRules.validate(value)
                        runtime.withExtensionWrite(target?.takeIf { store.currentProfileId.value == it }) {
                            store.compareAndSaveGlobalExtensionRules(before.rules, value)
                            mutableState.value = before.copy(rules = value, applying = true)
                        }
                        mutableState.value = mutableState.value.copy(applying = false)
                    } catch (_: ProfileApplyAfterSaveException) {
                        mutableState.value = before.copy(rules = value, error = "规则已保存，当前配置应用失败；可重试应用")
                    } catch (_: Exception) {
                        mutableState.value = before.copy(error = "保存失败或规则已变化，输入已保留；可重试或重新读取")
                    }
                }
            }
        }
    }

    fun retryApply() {
        val current = mutableState.value
        if (!current.loaded || current.saving || current.applying) return
        val token = generation
        val target = store.currentProfileId.value
        mutableState.value = current.copy(applying = true, error = null)
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                try {
                    if (target != null && store.currentProfileId.value == target) runtime.applyCurrentProfile(target)
                    mutableState.value = mutableState.value.copy(applying = false)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    mutableState.value = mutableState.value.copy(applying = false, error = "规则已保存，应用失败，请重试")
                }
            }
        }
    }
}
