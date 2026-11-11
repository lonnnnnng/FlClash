package com.lonnnnnng.flclash

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.RuleSetInfo
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.validation.ConfigSemanticException
import com.lonnnnnng.flclash.validation.IsolatedConfigValidator
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：使用授权正文的内存投影和独占Room控制竞态，真正委派隔离核心；不靠生产测试开关或改正式数据。 */
class ConfigSemanticResourceRaceInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private class Gate(private val delegate: ProfileConfigValidator) : ProfileConfigValidator {
        var pauseAt = Int.MAX_VALUE
        var calls = 0
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun validate(bytes: ByteArray) = delegate.validate(bytes)
        override suspend fun validateFull(bytes: ByteArray): ProfileValidationSummary {
            val result = delegate.validateFull(bytes)
            if (++calls == pauseAt) { reached.complete(Unit); release.await() }
            return result
        }
    }
    private suspend fun original(): ByteArray {
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val row = entry.database().profiles().getAll().single { it.type == ProfileType.FILE }
        val bytes = entry.profileRepository().profileFile(row.id).readBytes()
        assertEquals(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return bytes
    }
    private suspend fun environment(block: suspend (Context, KClashDatabase, ProfileRepository, RuleResourceRepository, Gate, ByteArray) -> Unit) {
        val bytes = original()
        val root = File(context.cacheDir, "semantic-race-" + java.util.UUID.randomUUID()).apply { check(mkdir()) }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val validator = Gate(IsolatedConfigValidator(isolated))
        val client = object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("本轮禁止下载")
        }
        val compiler = object : RuleResourceCompiler {
            // 作者 long：只使用classical YAML，不生成伪MRS；核心预检仍解析真实payload字节。
            override suspend fun compile(name: String) = RuleSetInfo("classical", "yaml")
            override suspend fun preview(path: String): String = error("本轮不读取MRS")
        }
        val resources = RuleResourceRepository(isolated, database, client, compiler)
        val repository = ProfileRepository(isolated, database, client, validator, resources = resources, observationScope = scope)
        try { block(isolated, database, repository, resources, validator, bytes) }
        finally {
            validator.release.complete(Unit)
            scope.coroutineContext[Job]?.cancelAndJoin()
            database.close()
            check(root.parentFile == context.cacheDir && root.name.startsWith("semantic-race-"))
            check(root.deleteRecursively())
        }
    }
    private fun candidate(bytes: ByteArray, provider: JsonObject? = null): JsonObject {
        val root = JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
        root.remove("rule-providers")
        root.add("proxy-providers", JsonObject().apply { provider?.let { add("authorized", it) } })
        val group = root.getAsJsonArray("proxy-groups").first().asJsonObject.deepCopy()
        group.addProperty("type", "select")
        group.add("proxies", JsonArray().apply { add("DIRECT") })
        if (provider == null) group.remove("use") else group.add("use", JsonArray().apply { add("authorized") })
        root.add("proxy-groups", JsonArray().apply { add(group) })
        root.add("rules", JsonArray().apply { add("MATCH," + group.get("name").asString) })
        return root
    }
    private fun yaml(root: JsonObject) = YamlConfigCodec.toYaml(root.toString())
    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to
        Triple(it.length(), it.lastModified(), MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()) }
    private fun report(case: String) = ins.sendStatus(2, android.os.Bundle().apply {
        putString("kclash_semantic_resource_race", org.json.JSONObject().put("case", case).put("passed", true).toString())
    })

    @Test fun ruleDirectoryVersionChangedAfterInitialPreflightRejectsCommit() = runBlocking(Dispatchers.IO) {
        environment { isolated, db, repository, resources, gate, bytes ->
            val root = candidate(bytes)
            val host = root.getAsJsonArray("proxies").first().asJsonObject.get("server").asString
            val payload = yaml(JsonObject().apply { add("payload", JsonArray().apply { add("DOMAIN,$host") }) }).toByteArray()
            val first = resources.save("semantic-rule", "", payload)
            root.add("rule-providers", JsonObject().apply { add("semantic-rule", RuleResources.reference(first)) })
            val custom = repository.addCustomProfile("semantic-rule-race")
            val rows = db.profiles().getAll(); val oldContent = repository.profileFile(custom.id).readBytes()
            gate.pauseAt = gate.calls + 1
            val task = async { runCatching { repository.replaceCustomProfile(custom.id, yaml(root), custom, "") { true } } }
            withTimeout(35_000) { gate.reached.await() }
            val replacement = resources.save(first.label, "", payload, first)
            assertNotEquals(first.fileName, replacement.fileName)
            val afterOwnChange = files(isolated.filesDir)
            gate.release.complete(Unit)
            val failure = task.await().exceptionOrNull()
            assertTrue(failure?.javaClass?.simpleName, failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("预检期间规则资源已变化"))
            assertEquals(rows, db.profiles().getAll()); assertEquals(afterOwnChange, files(isolated.filesDir))
            assertArrayEquals(oldContent, repository.profileFile(custom.id).readBytes())
            assertEquals(listOf(replacement), resources.all())
            report("rule-directory-version")
        }
    }

    @Test fun proxyFileChangedAfterFinalPreflightRejectsCommit() = runBlocking(Dispatchers.IO) {
        environment { isolated, db, repository, _, gate, bytes ->
            val resource = repository.addFileProfile("semantic-source", bytes)
            val custom = repository.addCustomProfile("semantic-proxy-race")
            val root = candidate(bytes, ProfileProxyResources.reference(resource.id)).apply { remove("proxies") }
            val rows = db.profiles().getAll(); gate.pauseAt = gate.calls + 2
            val task = async { runCatching { repository.replaceCustomProfile(custom.id, yaml(root), custom, "") { true } } }
            withTimeout(70_000) { gate.reached.await() }
            repository.profileFile(resource.id).appendText("\n# semantic controlled newer source\n")
            val afterOwnChange = files(isolated.filesDir)
            gate.release.complete(Unit)
            val failure = task.await().exceptionOrNull()
            assertTrue(failure?.javaClass?.simpleName, failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("引用资源已变化"))
            assertEquals(rows, db.profiles().getAll()); assertEquals(afterOwnChange, files(isolated.filesDir))
            report("proxy-file-version")
        }
    }

    @Test fun reusedHttpResourceWithInvalidCachedNodeRejectsBeforeCustomCommit() = runBlocking(Dispatchers.IO) {
        environment { isolated, db, repository, _, _, bytes ->
            val resource = repository.addFileProfile("semantic-reused", bytes)
            val row = checkNotNull(db.profiles().getById(resource.id))
            val url = "https://example.test/semantic-never-downloaded"
            db.profiles().update(row.copy(type = ProfileType.URL, url = url))
            val invalid = JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
            invalid.getAsJsonArray("proxies").first().asJsonObject.addProperty("type", "semantic-invalid")
            repository.profileFile(resource.id).writeText(yaml(invalid))
            val custom = repository.addCustomProfile("semantic-reuse")
            val provider = JsonObject().apply { addProperty("type", "http"); addProperty("url", url); addProperty("path", "semantic-unused") }
            val root = candidate(bytes, provider).apply { remove("proxies") }
            val before = files(isolated.filesDir); val rows = db.profiles().getAll()
            val failure = runCatching { repository.replaceCustomProfile(custom.id, yaml(root), custom, "") { true } }.exceptionOrNull()
            assertTrue(failure?.javaClass?.simpleName, failure is ConfigSemanticException)
            assertEquals(before, files(isolated.filesDir)); assertEquals(rows, db.profiles().getAll())
            report("reused-http-cache-rejected")
        }
    }

    @Test fun unusedMissingStableProviderDoesNotBlockValidCustomImport() = runBlocking(Dispatchers.IO) {
        environment { _, db, repository, _, _, bytes ->
            val custom = repository.addCustomProfile("semantic-unused-reference")
            val root = candidate(bytes)
            root.add("proxy-providers", JsonObject().apply { add("unused", ProfileProxyResources.reference(Long.MAX_VALUE)) })
            val updated = repository.replaceCustomProfile(custom.id, yaml(root), custom, "") { true }
            assertEquals(custom.id, updated.id)
            assertEquals(1, db.profiles().getAll().size)
            assertFalse(File(checkNotNull(repository.profileById(custom.id)).filePath).readText().contains("kclash-profile:"))
            report("unused-missing-reference")
        }
    }
}
