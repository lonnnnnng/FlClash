package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreProviderCounts
import kotlinx.coroutines.flow.StateFlow

data class ProfileStatistics(val profileId: Long, val groups: Long, val proxies: Long, val rules: Long, val providers: Long)

interface ProfileStatisticsRuntime {
    val state: StateFlow<CoreRuntimeStatus>
    val profileStatisticsRevision: StateFlow<Long>
    suspend fun profileStatistics(profileId: Long): ProfileStatistics
    suspend fun profileProviders(profileId: Long): List<com.lonnnnnng.flclash.core.CoreExternalProvider> = error("Provider 明细不可用")
    suspend fun updateProfileProvider(profileId: Long, name: String): Unit = error("Provider 更新不可用")
    suspend fun readProfileProvider(profileId: Long, name: String): ProfileProviderDocument = error("Provider 预览不可用")
    suspend fun restoreProfileProvider(profileId: Long, name: String, recoveryKey: String, digest: String): ProfileProviderDocument {
        return recoverProfileProviderDocument(profileId, name, recoveryKey, digest, null)
    }
    suspend fun recoverProfileProviderDocument(profileId: Long, name: String, recoveryKey: String, digest: String,
        intendedContent: String?): ProfileProviderDocument {
        val loaded = readProfileProvider(profileId, name)
        // 作者 long：只有同一输入且仍为原版本或已提交的确切正文才能重绑；任意第三方新版本都不能成为旧草稿的新基线。
        check(loaded.profileId == profileId && loaded.document.name == name && recoveryKey.isNotBlank() && loaded.recoveryKey == recoveryKey)
        check(loaded.document.digest == digest || intendedContent != null && loaded.document.content == intendedContent)
        return loaded
    }
    suspend fun saveProfileProvider(document: ProfileProviderDocument, content: String): Unit = error("Provider 侧载不可用")
    suspend fun updateProfileProviders(profileId: Long, names: List<String>): List<com.lonnnnnng.flclash.core.CoreProviderUpdateResult> = error("Provider 批量更新不可用")
}

/** 作者 long：窗口只使用正文和可展示元数据；绑定令牌由生命周期管理器持有，不暴露配置路径。 */
class ProfileProviderDocument(val profileId: Long, val document: com.lonnnnnng.flclash.core.CoreProviderDocument,
    internal val binding: Any? = null, val recoveryKey: String = "")

internal fun profileStatisticsOf(profileId: Long, yaml: String, providers: CoreProviderCounts): ProfileStatistics {
    require(providers.proxies >= 0 && providers.providers >= 0)
    val config = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
    fun count(key: String) = config.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.size()?.toLong() ?: 0L
    // 作者 long：以已应用输入为准，内置 GLOBAL/DIRECT 和展开后的 RULE-SET 不重复计入配置数量。
    return ProfileStatistics(profileId, count("proxy-groups"), Math.addExact(count("proxies"), providers.proxies),
        count("rules"), providers.providers)
}

/** 作者 long：仅安全分类文案可以跨入界面，不携带核心原始错误、URL 或文件路径。 */
class ProviderOperationException(message: String, val committed: Boolean = false) : IllegalStateException(message)
