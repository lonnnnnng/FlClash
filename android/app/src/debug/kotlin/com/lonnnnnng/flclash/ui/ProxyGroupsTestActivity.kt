package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.data.encodeSelectedMap
import com.lonnnnnng.flclash.data.decodeUnfoldSet
import com.lonnnnnng.flclash.data.encodeUnfoldSet
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
    private var visualState by mutableStateOf<ProxyGroupsUiState?>(null)
    private var darkTheme by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 作者 long：Debug UI 用例必须从 Flutter 对齐的默认代理展示偏好开始，避免设备上一次用例的菜单选择污染截图。
        ProxyPageOptionsStore.clear(this)
        runtime = ActivityProxyGroupsRuntime()
        profileStore = ActivityProxyProfileStore()
        viewModel = ProxyGroupsViewModel(runtime, profileStore)
        setContent {
            val state by viewModel.state.collectAsState()
            val profiles by profileStore.profiles.collectAsState()
            val currentProfileId by profileStore.currentProfileId.collectAsState()
            val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
            KClashTheme(darkTheme = darkTheme ?: androidx.compose.foundation.isSystemInDarkTheme()) {
                ProxyGroupsCard(
                    modifier = Modifier.fillMaxSize(),
                    state = visualState ?: state,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    currentProfileId = currentProfileId,
                    currentGroupName = currentProfile?.currentGroupName,
                    onRefresh = viewModel::refresh,
                    onSelectGroup = viewModel::selectGroup,
                    onToggleGroup = viewModel::toggleGroupExpanded,
                    onSelect = viewModel::selectProxy,
                    onTestDelay = viewModel::testDelay,
                    onTestGroupDelay = viewModel::testDelayGroup,
                )
            }
        }
    }

    fun showResultsScenario(dark: Boolean, testing: Boolean = false) {
        darkTheme = dark
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-a",
            all = listOf("node-a", "node-b", "node-timeout", "nested"),
            hidden = false,
            testUrl = "https://example.com",
        )
        // 作者 long：稳定注入边界延迟和未采用的优选，独立验证显示状态，不调用测试网址或真实核心。
        visualState = ProxyGroupsUiState(
            groups = listOf(group, ProxyGroupInfo("nested", "Fallback", "leaf", listOf("leaf"), false)),
            selectedMap = mapOf("Auto" to "node-b"),
            delayResults = listOf(100, 600, CoreBridge.DELAY_TIMED_OUT, CoreBridge.DELAY_FAILED)
                .mapIndexed { index, value ->
                    ProxyDelayKey(group.name, group.all[index], requireNotNull(group.testUrl)) to
                        ProxyDelayUiState(isTesting = testing && index == 1, value = value)
                }.toMap(),
        )
    }

    fun showLongSelectionScenario() {
        val members = (0..19).map { index -> "node-${index.toString().padStart(2, '0')}" }
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = members.last(),
            all = members,
            hidden = false,
            testUrl = "https://example.com",
        )
        visualState = ProxyGroupsUiState(
            groups = listOf(group),
            selectedMap = mapOf(group.name to members.last()),
        )
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

    override suspend fun updateProfileCurrentGroupName(id: Long, groupName: String): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(currentGroupName = groupName)
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateProfileGroupViewState(
        id: Long,
        groupName: String,
        unfoldSet: Set<String>,
    ): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(
            currentGroupName = groupName,
            unfoldSetJson = encodeUnfoldSet(unfoldSet),
        )
        mutableProfiles.value = listOf(updated)
        return updated
    }

    fun selectedMap(): Map<String, String> = decodeSelectedMap(mutableProfiles.value.single().selectedMapJson)

    fun unfoldSet(): Set<String> = decodeUnfoldSet(mutableProfiles.value.single().unfoldSetJson)
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
    private val snapshot = ProxyGroupsSnapshot(
        listOf(group, ProxyGroupInfo("nested", "Fallback", "leaf", listOf("leaf"), false)),
    )
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.RUNNING)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val events: Flow<CoreEvent> = emptyFlow()
    var lastTarget: String? = null
    var lastDelayTarget: String? = null

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = Result.success(snapshot)

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> {
        lastTarget = proxyName
        return Result.success(snapshot)
    }

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): Result<ProxyDelay?> {
        lastDelayTarget = proxyName
        return Result.success(ProxyDelay(testUrl, proxyName, 100))
    }

    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<ProxyDelayBatchResult>> = Result.success(emptyList())
}
