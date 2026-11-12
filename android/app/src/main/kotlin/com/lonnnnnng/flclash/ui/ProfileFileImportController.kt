package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ProfileFileImportUiState(
    val picking: Boolean = false,
    val reading: Boolean = false,
    val saving: Boolean = false,
    val fileName: String? = null,
    val size: Int = 0,
    val validYaml: Boolean = false,
    val encrypted: Boolean = false,
    val needsKey: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
    val validationNote: String? = null,
)

/** 作者 long：新增文件导入冻结本次选择的字节，预检、补钥及重试共用同一快照，确认前不写 Profile。 */
internal class ProfileFileImportController(
    private val store: ProfileStore,
    private val runtime: CoreRuntimeStatusProvider,
    private val reader: ProfileFileDocumentReader,
    private val validator: ProfileConfigValidator,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow(ProfileFileImportUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var bytes: ByteArray? = null
    private var job: Job? = null

    fun beginPick(): Long? {
        if (state.value.saving || state.value.picking) return null
        dismiss()
        if (isProfileMutationBlocked(runtime.state.value)) {
            mutableState.value = ProfileFileImportUiState(error = PROFILE_MUTATION_BLOCKED_MESSAGE)
            return null
        }
        mutableState.value = ProfileFileImportUiState(picking = true)
        return generation
    }

    fun acceptFile(token: Long?, uri: String?) {
        if (token != generation || !state.value.picking) return
        if (uri == null) { dismiss(); return }
        val active = generation
        mutableState.value = ProfileFileImportUiState(reading = true)
        job = scope.launch {
            try {
                val document = reader.read(uri)
                val frozen = document.bytes.copyOf()
                require(frozen.isNotEmpty() && frozen.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
                val encrypted = isAgeEncryptedProfile(frozen)
                var validationError: String? = null
                var validationNote: String? = null
                val valid = if (encrypted) false else withContext(dispatcher) {
                    try {
                        val summary = validator.validateFull(frozen)
                        if (summary.deferredExternalResources > 0) validationNote = "${summary.deferredExternalResources} 个远程资源或网络依赖将在核心加载时检查"
                        true
                    }
                    catch (failure: CancellationException) { throw failure }
                    catch (failure: com.lonnnnnng.flclash.validation.ConfigSemanticException) { validationError = failure.message; false }
                    catch (_: Exception) { false }
                }
                if (active == generation) {
                    bytes = frozen
                    mutableState.value = ProfileFileImportUiState(fileName = document.name, size = frozen.size, validYaml = valid, encrypted = encrypted, error = validationError, validationNote = validationNote)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                if (active == generation) mutableState.value = ProfileFileImportUiState(error = "无法读取配置文件，请确认文件非空且不超过 8 MiB 后重试")
            }
        }
    }

    fun confirm(key: String = "") {
        val current = state.value
        if (current.saving || current.reading || current.fileName == null) return
        if (isProfileMutationBlocked(runtime.state.value)) {
            mutableState.value = current.copy(error = PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        if (current.encrypted && key.isBlank()) {
            mutableState.value = current.copy(needsKey = true, error = null)
            return
        }
        if (!current.encrypted && !current.validYaml) return
        val input = bytes ?: return
        val active = generation
        val label = current.fileName.substringBeforeLast('.', current.fileName).trim().ifEmpty { "本地配置" }
        mutableState.value = current.copy(saving = true, error = null)
        job = scope.launch {
            try {
                store.importFileProfile(label, input, key.trim()) {
                    active == generation && !isProfileMutationBlocked(runtime.state.value)
                }
                if (active == generation) {
                    generation++; bytes = null
                    // 作者 long：保留本次文件名并等待用户确认完成，避免后台保存后配置列表突然变化却没有回执。
                    mutableState.value = ProfileFileImportUiState(fileName = current.fileName, completed = true)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: com.lonnnnnng.flclash.validation.ConfigSemanticException) {
                if (active == generation) mutableState.value = current.copy(saving = false, error = failure.message)
            }
            catch (_: Exception) {
                if (active == generation) mutableState.value = current.copy(saving = false,
                    error = if (current.encrypted) "age 解密失败，请检查密钥与配置文件" else "导入配置失败，原配置未修改，请重试")
            }
        }
    }

    fun dismiss() {
        generation++; job?.cancel(); job = null; bytes = null
        mutableState.value = ProfileFileImportUiState()
    }

    fun onBackground() {
        // 作者 long：系统选择器自身会遮住 Activity，保留其回调令牌；其余后台场景作废读取和密钥会话。
        if (!state.value.picking) dismiss()
    }
}
