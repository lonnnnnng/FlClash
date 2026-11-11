package com.lonnnnnng.flclash.update

import com.lonnnnnng.flclash.BuildConfig
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class UpdatePhase { IDLE, CHECKING, LATEST, AVAILABLE, FAILED }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val release: AppRelease? = null)

/** 作者 long：应用只检查版本并交给浏览器下载，检查结果留在内存，不维护安装包或后台更新任务。 */
@Singleton
class AppUpdateRepository @Inject constructor(private val runtime: CoreRuntimeManager) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check() = withContext(Dispatchers.IO) {
        if (!lock.tryLock()) return@withContext
        val previous = mutable.value
        mutable.value = UpdateState(UpdatePhase.CHECKING)
        try {
            // 作者 long：检查请求仍沿用当前已应用的代理入站，避免核心已开启但应用自身无法访问GitHub。
            val json = UpdateTransport(runtime.downloadRoute()).use { it.releases() }
            mutable.value = ReleaseCatalog.check(json, BuildConfig.VERSION_CODE.toLong())
        } catch (cancelled: CancellationException) {
            mutable.value = previous
            throw cancelled
        } catch (_: Exception) {
            mutable.value = UpdateState(UpdatePhase.FAILED)
        } finally {
            lock.unlock()
        }
    }
}
