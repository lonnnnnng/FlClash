package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.CoreExternalProvider
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 作者 long：应用规则资源在核心表现为文件Provider，使用稳定引用与实际文件指纹共同确认归属，避免同名脚本资源被误同步。 */
class ManagedProfileProviders @Inject constructor(
    private val profiles: ProfileStore,
    private val resources: RuleResourceRepository,
    private val runtime: CoreRuntimeManager,
) {
    suspend fun bindings(profileId: Long, providers: List<CoreExternalProvider>): Map<String, RuleResource> = withContext(Dispatchers.IO) {
        val profile = profiles.profiles.value.firstOrNull { it.id == profileId } ?: return@withContext emptyMap()
        if (profile.type != ProfileType.CUSTOM) return@withContext emptyMap()
        val definitions = RuleResources.document(profiles.readProfileSourceContent(profileId)).getAsJsonObject("rule-providers")
            ?: return@withContext emptyMap()
        val rows = resources.all().associateBy { it.id }
        providers.mapNotNull { provider ->
            val resource = RuleResources.idOf(definitions.get(provider.name))?.let(rows::get) ?: return@mapNotNull null
            val path = java.io.File(resources.file(resource).parentFile, resource.coreFileName).path
            val identity = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
            if (provider.type == "Rule" && provider.fileIdentity == identity && resource.url.isNotBlank()) provider.name to resource else null
        }.toMap()
    }

    suspend fun sync(resource: RuleResource) { resources.sync(resource) }

    suspend fun apply(profileId: Long) {
        // 作者 long：只重新应用发起同步的当前配置，不能在用户切配置后将旧详情重新选中。
        if (profiles.currentProfileId.value == profileId) runtime.applyCurrentProfile(profileId)
    }
}
