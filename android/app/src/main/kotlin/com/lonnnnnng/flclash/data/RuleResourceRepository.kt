package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.core.RuleSetInfo
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RuleResourceCompiler {
    suspend fun compile(name: String): RuleSetInfo
    suspend fun preview(path: String): String
}

class NativeRuleResourceCompiler @Inject constructor(private val runtime: Provider<CoreRuntimeManager>) : RuleResourceCompiler {
    override suspend fun compile(name: String) = runtime.get().compileRuleResource(name)
    override suspend fun preview(path: String) = runtime.get().previewRuleResource(path)
}

@Singleton
class RuleResourceRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val database: KClashDatabase,
    private val client: SubscriptionClient,
    private val compiler: RuleResourceCompiler,
) {
    private val filesRoot = context.filesDir
    private val directory = File(filesRoot, "mihomo/providers/rules")
    private val preferences = database.appPreferences()
    // 作者 long：下载不持有目录锁，提交时再比较快照，避免慢同步覆盖已编辑、已删除的资源。
    private val lock = Mutex()
    val changes = preferences.observe(RULE_RESOURCES_KEY)
    suspend fun all(): List<RuleResource> = RuleResources.decode(preferences.get(RULE_RESOURCES_KEY))
    fun file(resource: RuleResource): File = File(directory, resource.fileName)
    suspend fun content(resource: RuleResource): String = withContext(Dispatchers.IO) {
        if (resource.format == "mrs") compiler.preview(file(resource).path) else file(resource).readText()
    }
    private suspend fun stage(label: String, url: String, bytes: ByteArray, id: String = UUID.randomUUID().toString(), fileToken: String = UUID.randomUUID().toString()): RuleResource {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "资源为空或超过 8 MiB" }
        require(UUID.fromString(fileToken).toString() == fileToken)
        val token = fileToken + ".rules"
        directory.mkdirs()
        val target = File(directory, token)
        target.writeBytes(bytes)
        try {
            val info = compiler.compile(token)
            return RuleResource(id, label.trim(), url, token, info.behavior, info.format, System.currentTimeMillis())
        } catch (error: Throwable) {
            discardFile(token)
            throw error
        }
    }
    private fun discardFile(name: String) {
        // 作者 long：这里只回收本仓库刚分配的 UUID 文件，不接受外部路径或配置中的 path。
        require(name.matches(Regex("[a-zA-Z0-9-]{1,80}\\.rules")))
        File(directory, name).delete(); File(directory, "$name.mrs").delete()
    }
    suspend fun save(label: String, url: String, bytes: ByteArray?, expected: RuleResource? = null): RuleResource = withContext(Dispatchers.IO) {
        val normalizedUrl = if (url.isBlank()) "" else RuleResources.normalizeUrl(url)
        require(label.trim().isNotBlank()) { "请输入资源名称" }
        val body = bytes ?: if (normalizedUrl.isNotEmpty()) client.download(normalizedUrl).bytes else error("请输入资源正文")
        val staged = stage(label, normalizedUrl, body, expected?.id ?: UUID.randomUUID().toString())
        var committed = false
        try {
            lock.withLock {
                val rows = all()
                require(expected == null || rows.firstOrNull { it.id == expected.id } == expected) { "资源已变化，请重新打开" }
                require(rows.none { it.id != staged.id && it.label == staged.label }) { "资源名称已经存在" }
                val next = if (expected == null) rows + staged else rows.map { if (it.id == staged.id) staged else it }
                withContext(NonCancellable) {
                    preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(next), System.currentTimeMillis()))
                    committed = true
                }
            }
            // 作者 long：旧文件可能仍被当前已应用配置读取，直到后续缓存回收前保留旧版本；失败只回收未提交版本。
            staged
        } finally { if (!committed) discardFile(staged.fileName) }
    }
    suspend fun updateOptions(expected: RuleResource, label: String, url: String): RuleResource = withContext(Dispatchers.IO) {
        if (url.trim() != expected.url) return@withContext save(label, url, if (url.isBlank()) file(expected).readBytes() else null, expected)
        lock.withLock {
            val rows = all()
            require(rows.firstOrNull { it.id == expected.id } == expected) { "资源已变化，请重新打开" }
            val next = expected.copy(label = label.trim())
            preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(rows.map { if (it.id == next.id) next else it }), System.currentTimeMillis()))
            next
        }
    }
    suspend fun importCommitted(token: String): Boolean = preferences.get(resourceImportReceiptKey("rule", token)) != null
    suspend fun forgetImport(token: String) { preferences.delete(resourceImportReceiptKey("rule", token)) }

    suspend fun abandonImport(token: String) = lock.withLock {
        resourceImportReceiptKey("rule", token)
        // 作者 long：只能回收本操作尚未登记的确定文件；有回执或目录引用时保留正式资源。
        if (!importCommitted(token) && all().none { it.fileName == "$token.rules" }) discardFile("$token.rules")
    }

    suspend fun commitImport(token: String, label: String, url: String, bytes: ByteArray?, expected: RuleResource?, options: Boolean): RuleResource? = withContext(Dispatchers.IO) {
        val key = resourceImportReceiptKey("rule", token)
        preferences.get(key)?.let { id -> return@withContext all().firstOrNull { it.id == id } }
        val normalized = if (url.isBlank()) "" else RuleResources.normalizeUrl(url)
        require(label.trim().isNotBlank()) { "请输入资源名称" }
        val metadataOnly = options && expected != null && normalized == expected.url
        val body = if (metadataOnly) null else bytes ?: if (normalized.isNotEmpty()) client.download(normalized).bytes
            else expected?.let { file(it).readBytes() } ?: error("请输入资源正文")
        lock.withLock {
            preferences.get(key)?.let { id -> return@withLock all().firstOrNull { it.id == id } }
            val rows = all()
            require(expected == null || rows.firstOrNull { it.id == expected.id } == expected) { "资源已变化，请重新打开" }
            require(rows.none { it.fileName == "$token.rules" }) { "资源已变化，请重新打开" }
            val staged = if (metadataOnly) checkNotNull(expected).copy(label = label.trim())
                else stage(label, normalized, checkNotNull(body), expected?.id ?: token, token)
            var committed = false
            try {
                require(rows.none { it.id != staged.id && it.label == staged.label }) { "资源名称已经存在" }
                val next = if (expected == null) rows + staged else rows.map { if (it.id == staged.id) staged else it }
                withContext(NonCancellable) { database.withTransaction {
                    // 作者 long：即使进程在写目录后消失，冷恢复也可用同事务回执判定完成，不能再登记第二份。
                    preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(next), System.currentTimeMillis()))
                    preferences.put(AppPreferenceEntity(key, staged.id, System.currentTimeMillis()))
                    committed = true
                } }
                staged
            } finally { if (!committed && !metadataOnly) discardFile(staged.fileName) }
        }
    }
    suspend fun importUrlIfNew(input: NamedResourceUrl): ResourceImportResult = withContext(Dispatchers.IO) {
        val url = RuleResources.normalizeUrl(input.url)
        if (all().any { it.url == url }) return@withContext ResourceImportResult.SKIPPED
        val staged = stage(input.suggestedName("规则资源"), url, client.download(url).bytes)
        var committed = false
        try {
            lock.withLock {
                val rows = all()
                // 作者 long：下载期间其他批次可能已登记同源资源；再次检查后跳过，回收仅本次生成的暂存文件。
                if (rows.any { it.url == url }) return@withLock ResourceImportResult.SKIPPED
                if (input.label.isNotBlank()) require(rows.none { it.label == input.label }) { "资源名称已存在" }
                val next = staged.copy(label = RuleResources.uniqueLabel(staged.label, rows))
                withContext(NonCancellable) {
                    preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(rows + next), System.currentTimeMillis()))
                    committed = true
                }
                ResourceImportResult.IMPORTED
            }
        } finally { if (!committed) discardFile(staged.fileName) }
    }
    suspend fun sync(expected: RuleResource): RuleResource {
        require(expected.url.isNotBlank()) { "本地资源没有远程地址" }
        return save(expected.label, expected.url, null, expected)
    }
    suspend fun delete(expected: RuleResource) = withContext(Dispatchers.IO) {
        lock.withLock {
            val rows = all()
            require(rows.firstOrNull { it.id == expected.id } == expected) { "资源已变化，请重新打开" }
            preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(rows.filterNot { it.id == expected.id }), System.currentTimeMillis()))
        }
    }
    suspend fun reorder(ids: List<String>) = lock.withLock {
        val rows = all()
        // 作者 long：排序不修改资源版本和内容；目录变化后拒绝旧排列，避免忽略丢失/重复ID而悄悄改序。
        require(ids.size == rows.size && ids.toSet().size == ids.size && ids.toSet() == rows.map { it.id }.toSet()) { "规则资源列表已变化，请重新排序" }
        val byId = rows.associateBy { it.id }
        preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(ids.map { byId.getValue(it) }), System.currentTimeMillis()))
    }
    suspend fun isUsedBy(id: Long, resource: RuleResource): Boolean = withContext(Dispatchers.IO) {
        runCatching { RuleResources.document(File(filesRoot, "profiles/$id.yaml").readText()).getAsJsonObject("rule-providers")?.entrySet()?.any { RuleResources.idOf(it.value) == resource.id } == true }.getOrDefault(false)
    }
    suspend fun profilesUsing(resource: RuleResource): List<String> = withContext(Dispatchers.IO) {
        database.profiles().getAll().map { it.toDomain() }.filter { profile ->
            if (profile.type != ProfileType.CUSTOM) false else {
                val source = File(filesRoot, "profiles/${profile.id}.yaml")
                runCatching { RuleResources.document(source.readText()).getAsJsonObject("rule-providers")?.entrySet()?.any { RuleResources.idOf(it.value) == resource.id } == true }.getOrDefault(false)
            }
        }.map { it.realLabel }
    }

    suspend fun <T> importResources(content: String, commit: suspend (String, suspend () -> Unit) -> T): T = withContext(Dispatchers.IO) {
        val document = RuleResources.document(content)
        val definitions = document.getAsJsonObject("rule-providers") ?: return@withContext commit(content) {}
        val before = all()
        val planned = before.toMutableList()
        val staged = mutableListOf<RuleResource>()
        val names = linkedMapOf<String, String>()
        val mapped = JsonObject()
        var committed = false
        try {
            for ((name, definition) in definitions.entrySet()) {
                if (RuleResources.idOf(definition) != null) { mapped.add(name, definition.deepCopy()); continue }
                require(definition.isJsonObject) { "规则资源定义无效" }
                val item = definition.asJsonObject
                val url = if (item.get("type")?.asString == "http") RuleResources.normalizeUrl(item.get("url")?.asString.orEmpty()) else ""
                val existing = planned.firstOrNull { url.isNotBlank() && it.url == url }
                val resource = existing ?: run {
                    val label = RuleResources.uniqueLabel(name, planned)
                    val bytes = if (url.isNotEmpty()) client.download(url).bytes else {
                        val payload = item.get("payload")?.takeIf { it.isJsonArray } ?: JsonArray()
                        YamlConfigCodec.toYaml(JsonObject().apply { add("payload", payload) }.toString()).toByteArray()
                    }
                    stage(label, url, bytes).also { planned += it; staged += it }
                }
                names[name] = resource.label
                mapped.add(resource.label, RuleResources.reference(resource))
            }
            document.add("rule-providers", mapped)
            RuleResources.renameRuleReferences(document, names)
            val mappedContent = YamlConfigCodec.toYaml(document.toString())
            lock.withLock {
                require(all() == before) { "全局资源已变化，请重新确认导入" }
                // 作者 long：资源登记与配置替换共享外层 Room 事务；失败不能留下已登记但未被导入的资源。
                withContext(NonCancellable) {
                    commit(mappedContent) {
                        preferences.put(AppPreferenceEntity(RULE_RESOURCES_KEY, RuleResources.encode(planned), System.currentTimeMillis()))
                    }.also { committed = true }
                }
            }
        } finally { if (!committed) staged.forEach { discardFile(it.fileName) } }
    }

    /** 作者 long：引用只在加载输入中变为当前资源文件，正文保存稳定 ID，资源改名或同步不会改写用户配置。 */
    suspend fun resolve(content: String): String = withContext(Dispatchers.IO) {
        if (!content.contains(RESOURCE_PATH_PREFIX)) return@withContext content
        val document = RuleResources.document(content)
        val rows = all().associateBy { it.id }
        document.getAsJsonObject("rule-providers")?.entrySet()?.forEach { (name, definition) ->
            RuleResources.idOf(definition)?.let { id ->
                val resource = rows[id] ?: error("规则资源缺失：$name，请重新选择资源")
                val coreFile = File(directory, resource.coreFileName)
                require(coreFile.isFile) { "规则资源缓存缺失，请同步或重新导入" }
                definition.asJsonObject.apply {
                    addProperty("type", "file"); addProperty("path", coreFile.path)
                    addProperty("behavior", resource.behavior); addProperty("format", if (resource.compiled) "mrs" else resource.format)
                    remove("url"); remove("payload")
                }
            }
        }
        YamlConfigCodec.toYaml(document.toString())
    }
}
