package com.lonnnnnng.flclash.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import androidx.room.withTransaction
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ResourceIcon(val name: String, val url: String)
data class ResourceIconSet(val id: String, val name: String, val url: String = "", val icons: List<ResourceIcon>, val updatedAt: Long)
const val ICON_SETS_KEY = "global_icon_sets"

object IconSetCodec {
    fun parse(content: String): Pair<String, List<ResourceIcon>> {
        require(content.toByteArray().size <= 2 * 1024 * 1024) { "图标集超过 2 MiB" }
        val root = JsonParser.parseString(content).asJsonObject
        val array = root.get("icons")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("图标集缺少 icons 列表")
        require(array.size() <= 8192) { "图标集最多包含 8192 项" }
        val seen = mutableSetOf<String>()
        val icons = array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val url = item.get("url")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return@mapNotNull null
            if (!validUrl(url) || !seen.add(url)) return@mapNotNull null
            val name = item.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
                ?.takeIf { it.isNotEmpty() } ?: URI(url).path.substringAfterLast('/').ifBlank { "图标" }
            ResourceIcon(name.take(256), url)
        }
        require(icons.isNotEmpty()) { "图标集没有有效图标" }
        val name = root.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim().orEmpty()
        return name.take(128) to icons
    }

    fun validUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        url.length <= 8192 && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun decode(value: String?): List<ResourceIconSet> {
        if (value == null) return emptyList()
        require(value.toByteArray().size <= 4 * 1024 * 1024) { "图标集目录超过容量限制" }
        val root = JsonParser.parseString(value)
        require(root.isJsonArray && root.asJsonArray.size() <= 64) { "图标集目录无效" }
        fun string(item: com.google.gson.JsonObject, key: String): String = item[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: error("图标集字段无效")
        return root.asJsonArray.map { element ->
            val item = element.asJsonObject
            val icons = item["icons"]?.takeIf { it.isJsonArray }?.asJsonArray ?: error("图标集缺少 icons")
            require(icons.size() <= 8192) { "图标集过大" }
            val updated = item["updatedAt"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toLongOrNull() ?: error("图标集时间无效")
            ResourceIconSet(string(item, "id"), string(item, "name"), string(item, "url"), icons.map { icon ->
                ResourceIcon(string(icon.asJsonObject, "name"), string(icon.asJsonObject, "url"))
            }, updated)
        }.also(::encode)
    }
    fun encode(rows: List<ResourceIconSet>): String = Gson().toJson(rows).also {
        require(rows.size <= 64 && it.toByteArray().size <= 4 * 1024 * 1024) { "图标集目录超过容量限制" }
        require(rows.map { it.id }.distinct().size == rows.size) { "图标集 ID 重复" }
        rows.forEach { row ->
            require(row.id.isNotBlank() && row.id.length <= 80 && row.name.isNotBlank() && row.name.length <= 128 && row.updatedAt >= 0) { "图标集元数据无效" }
            require(row.url.isEmpty() || validUrl(row.url)) { "图标集地址无效" }
            require(row.icons.isNotEmpty() && row.icons.size <= 8192 && row.icons.map { icon -> icon.url }.distinct().size == row.icons.size) { "图标列表无效" }
            row.icons.forEach { icon -> require(icon.name.isNotBlank() && icon.name.length <= 256 && validUrl(icon.url)) { "图标条目无效" } }
        }
    }
}

@Singleton
class IconSetRepository @Inject constructor(private val database: KClashDatabase, private val client: ScriptClient) {
    private val lock = Mutex()
    private val preferences = database.appPreferences()
    val changes = preferences.observe(ICON_SETS_KEY).map(IconSetCodec::decode)
    suspend fun all() = IconSetCodec.decode(preferences.get(ICON_SETS_KEY))
    private suspend fun save(rows: List<ResourceIconSet>) = preferences.put(AppPreferenceEntity(ICON_SETS_KEY, IconSetCodec.encode(rows), System.currentTimeMillis()))

    suspend fun fileImportCommitted(token: String) = preferences.get(resourceImportReceiptKey("icons", token)) != null
    suspend fun forgetFileImport(token: String) { preferences.delete(resourceImportReceiptKey("icons", token)) }
    suspend fun importFileOnce(token: String, content: String, fallbackName: String): ResourceIconSet? = withContext(Dispatchers.IO) {
        val key = resourceImportReceiptKey("icons", token)
        preferences.get(key)?.let { id -> return@withContext all().firstOrNull { it.id == id } }
        val (name, icons) = IconSetCodec.parse(content)
        lock.withLock { database.withTransaction {
            preferences.get(key)?.let { id -> return@withTransaction all().firstOrNull { it.id == id } }
            val rows = all()
            require(rows.none { it.id == token }) { "图标集已变化，请重新选择" }
            val item = ResourceIconSet(token, name.ifBlank { fallbackName }.ifBlank { "图标集" }.take(128), icons = icons, updatedAt = System.currentTimeMillis())
            // 作者 long：图库选中即导入，但同一系统请求只能新增一次；回执和目录必须一起提交或一起回滚。
            save(rows + item)
            preferences.put(AppPreferenceEntity(key, item.id, System.currentTimeMillis()))
            item
        } }
    }

    suspend fun importContent(content: String, url: String = "", fallbackName: String = "图标集", preferredName: String = ""): ResourceIconSet = withContext(Dispatchers.IO) {
        require(url.isEmpty() || IconSetCodec.validUrl(url)) { "请输入有效 HTTP/HTTPS 地址" }
        val (name, icons) = IconSetCodec.parse(content)
        lock.withLock {
            val rows = all()
            val existing = rows.firstOrNull { url.isNotEmpty() && it.url == url }
            val resource = ResourceIconSet(existing?.id ?: UUID.randomUUID().toString(),
                existing?.name ?: preferredName.trim().ifBlank { name.ifBlank { fallbackName }.ifBlank { "图标集" } }.take(128), url, icons, System.currentTimeMillis())
            save(rows.filterNot { it.id == resource.id } + resource)
            resource
        }
    }
    suspend fun importUrl(url: String, name: String = ""): ResourceIconSet = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(IconSetCodec.validUrl(normalized)) { "请输入有效 HTTP/HTTPS 地址" }
        val existing = all().firstOrNull { it.url == normalized }
        if (existing != null) {
            val synced = sync(existing)
            if (name.isNotBlank()) { options(synced, name, normalized); all().first { it.id == synced.id } } else synced
        } else importContent(client.download(normalized).bytes.toString(Charsets.UTF_8), normalized,
            fallbackName = URI(normalized).path.substringAfterLast('/').substringBeforeLast('.'), preferredName = name)
    }
    suspend fun sync(expected: ResourceIconSet): ResourceIconSet = withContext(Dispatchers.IO) {
        require(expected.url.isNotEmpty()) { "本地图标集没有远程地址" }
        val (_, icons) = IconSetCodec.parse(client.download(expected.url).bytes.toString(Charsets.UTF_8))
        lock.withLock {
            val rows = all()
            val latest = rows.firstOrNull { it.id == expected.id } ?: error("图标集已删除")
            // 作者 long：下载期间可重命名，但改源/再次同步后的旧下载不能覆盖新版本或复活已删除图标集。
            check(latest.url == expected.url && latest.updatedAt == expected.updatedAt && latest.icons == expected.icons) { "图标集已变化，请重试" }
            latest.copy(icons = icons, updatedAt = maxOf(System.currentTimeMillis(), latest.updatedAt + 1)).also { next -> save(rows.map { if (it.id == next.id) next else it }) }
        }
    }
    suspend fun importUrlIfNew(input: NamedResourceUrl): ResourceImportResult = withContext(Dispatchers.IO) {
        val url = input.url.trim()
        require(IconSetCodec.validUrl(url)) { "请输入有效 HTTP/HTTPS 地址" }
        if (all().any { it.url == url }) return@withContext ResourceImportResult.SKIPPED
        val (_, content) = IconSetCodec.parse(client.download(url).bytes.toString(Charsets.UTF_8))
        lock.withLock {
            val rows = all()
            if (rows.any { it.url == url }) return@withLock ResourceImportResult.SKIPPED
            if (input.label.isNotBlank()) require(rows.none { it.name == input.label }) { "图标集名称已存在" }
            val base = input.suggestedName("图标集").take(118)
            val name = if (rows.none { it.name == base }) base else generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { next -> rows.none { it.name == next } }
            save(rows + ResourceIconSet(UUID.randomUUID().toString(), name, url, content, System.currentTimeMillis()))
            ResourceImportResult.IMPORTED
        }
    }
    suspend fun options(expected: ResourceIconSet, name: String, url: String) = withContext(Dispatchers.IO) {
        val label = name.trim(); val link = url.trim()
        require(label.isNotEmpty() && label.length <= 128) { "名称须为 1 至 128 字符" }
        require(link.isEmpty() || IconSetCodec.validUrl(link)) { "请输入有效 HTTP/HTTPS 地址" }
        val downloaded = if (link != expected.url && link.isNotEmpty()) IconSetCodec.parse(client.download(link).bytes.toString(Charsets.UTF_8)).second else null
        lock.withLock {
            val rows = all(); check(rows.firstOrNull { it.id == expected.id } == expected) { "图标集已变化，请重试" }
            val next = expected.copy(name = label, url = link, icons = downloaded ?: expected.icons,
                updatedAt = if (downloaded == null) expected.updatedAt else maxOf(System.currentTimeMillis(), expected.updatedAt + 1))
            save(rows.map { if (it.id == next.id) next else it })
        }
    }
    suspend fun rename(id: String, name: String) = lock.withLock {
        val value = name.trim(); require(value.isNotEmpty() && value.length <= 128) { "名称须为 1 至 128 字符" }
        save(all().map { if (it.id == id) it.copy(name = value) else it })
    }
    suspend fun delete(id: String) = lock.withLock { save(all().filterNot { it.id == id }) }
    suspend fun reorder(ids: List<String>) = lock.withLock {
        val rows = all()
        // 作者 long：只接受当前目录的完整排列，过期页面不能丢弃并发导入项，也不能复活已删除的图库。
        require(ids.size == rows.size && ids.toSet().size == ids.size && ids.toSet() == rows.map { it.id }.toSet()) { "图标集列表已变化，请重新排序" }
        val byId = rows.associateBy { it.id }
        save(ids.map { byId.getValue(it) })
    }
    suspend fun move(id: String, offset: Int) = lock.withLock {
        val rows = all().toMutableList(); val from = rows.indexOfFirst { it.id == id }; val to = from + offset
        if (from >= 0 && to in rows.indices) { rows.add(to, rows.removeAt(from)); save(rows) }
    }
}
