package com.lonnnnnng.flclash.ui

import com.google.gson.JsonObject
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 作者 long：目录、原生校验与运行态应用保持正式实现；窄接口让恢复/提交中断能够由确定性时序测试覆盖。 */
internal interface LocalProxyEditorOperations {
    val changes: Flow<Unit>
    val selectedId: Long?
    suspend fun rows(): List<LocalProxy>
    suspend fun save(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>? = null)
    suspend fun groups(): Set<String>
    suspend fun users(names: Set<String>): List<String>
    suspend fun validate(nodes: List<JsonObject>): List<String>
    suspend fun encode(nodes: List<JsonObject>): List<String>
    suspend fun decode(links: List<String>): List<List<JsonObject>>
    suspend fun definition(profileId: Long, name: String): JsonObject
    suspend fun applyIfReferenced(names: Set<String>)
}

internal class RepositoryLocalProxyEditorOperations(
    private val profiles: ProfileRepository, private val bridge: CoreBridge, private val runtime: CoreRuntimeManager,
) : LocalProxyEditorOperations {
    override val changes = profiles.localProxyChanges.map { Unit }
    override val selectedId get() = profiles.currentProfileId.value
    override suspend fun rows() = profiles.localProxies()
    override suspend fun save(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>?) {
        LocalProxyOperationProbe.checkpoint(LocalProxyOperationWindow.BEFORE_SAVE)
        try { profiles.saveLocalProxies(expected, next, rename) }
        catch (error: Exception) { LocalProxyOperationProbe.saveFailed(error); throw error }
    }
    override suspend fun groups() = profiles.profiles.value.filter { it.type == ProfileType.CUSTOM }.flatMap {
        ProxyGroupDrafts.read(profiles.readLocalProfileContent(it.id)).map { group -> group.name }
    }.toSet()
    override suspend fun users(names: Set<String>) = profiles.localProxyUsers(names)
    override suspend fun validate(nodes: List<JsonObject>): List<String> {
        LocalProxyOperationProbe.checkpoint(LocalProxyOperationWindow.BEFORE_VALIDATE)
        return bridge.validateProxies(nodes)
    }
    override suspend fun encode(nodes: List<JsonObject>) = bridge.encodeShareLinks(nodes)
    override suspend fun decode(links: List<String>) = bridge.decodeShareLinks(links)
    override suspend fun definition(profileId: Long, name: String) = runtime.getProxyDefinition(profileId, name)
    override suspend fun applyIfReferenced(names: Set<String>) {
        val id = selectedId ?: return
        val profile = profiles.profiles.value.firstOrNull { it.id == id } ?: return
        if (profile.type == ProfileType.CUSTOM && LocalProxies.referenced(profiles.readLocalProfileContent(id)).any(names::contains)) {
            EditorCommitProbe.checkpoint(EditorCommitWindow.LOCAL_CLOSE_APPLYING)
            runtime.applyCurrentProfile(id)
            // 作者 long：此检查点只在实际引用并完成核心应用后命中，跳过应用分支不能冒充已应用的死亡窗口。
            EditorCommitProbe.checkpoint(EditorCommitWindow.LOCAL_CLOSE_APPLIED)
        }
    }
}
