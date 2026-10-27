package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyDelayBatchResult
import com.lonnnnnng.flclash.service.ProxyDelayRequest
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 作者 long：该 Activity 只存在于 Debug 构建，作为代理页的 Activity 生命周期宿主；它不进入 Release，
 * 测试替身只验证页面状态流，不改变正式 MainActivity 的核心和 VPN 依赖边界。
 */
class ProxyGroupsTestActivity : ComponentActivity() {
    lateinit var runtime: ActivityProxyGroupsRuntime
    lateinit var profileStore: ActivityProxyProfileStore
    lateinit var viewModel: ProxyGroupsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = ActivityProxyGroupsRuntime()
        profileStore = ActivityProxyProfileStore()
        viewModel = ProxyGroupsViewModel(runtime, profileStore)
        setContent {
            val state by viewModel.state.collectAsState()
            KClashTheme {
                ProxyGroupsCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onRefresh = viewModel::refresh,
                    onSelect = viewModel::selectProxy,
                    onTestDelay = viewModel::testDelay,
                    onTestGroupDelay = viewModel::testDelayGroup,
                )
            }
        }
    }
}

class ActivityProxyProfileStore : ProfileStore {
    private val initial = Profile.custom(501L, "Activity 代理选择")
    private val mutableProfiles = MutableStateFlow(listOf(initial))
    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = MutableStateFlow<Long?>(initial.id).asStateFlow()

    override suspend fun selectProfile(id: Long?) = Unit
    override suspend fun addRemoteProfile(url: String, label: String): Profile = error("unused")
    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")
    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile {
        val updated = mutableProfiles.value.single { it.id == id }
            .copy(selectedMapJson = encodeSelectedMap(selectedMap))
        mutableProfiles.value = listOf(updated)
        return updated
    }

    fun selectedMap(): Map<String, String> = decodeSelectedMap(mutableProfiles.value.single().selectedMapJson)
}

class ActivityProxyGroupsRuntime : ProxyGroupsRuntime {
    private val group = ProxyGroupInfo(
        name = "Auto",
        type = "URLTest",
        now = "node-a",
        all = listOf("node-a", "node-b"),
        hidden = false,
        testUrl = "https://example.com",
    )
    private val snapshot = ProxyGroupsSnapshot(listOf(group))
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.RUNNING)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val events: Flow<CoreEvent> = emptyFlow()
    var lastTarget: String? = null

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = Result.success(snapshot)

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> {
        lastTarget = proxyName
        return Result.success(snapshot)
    }

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): Result<ProxyDelay?> =
        Result.success(ProxyDelay(testUrl, proxyName, 100))

    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<ProxyDelayBatchResult>> = Result.success(emptyList())
}
