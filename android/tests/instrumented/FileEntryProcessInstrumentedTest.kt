package com.lonnnnnng.flclash.ui

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：不创建Activity；外部驱动必须在真实DocumentsUI前台回收宿主并返回文件后，才能运行只读核验阶段。 */
class FileEntryProcessInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val entry get() = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    private val marker get() = File(context.noBackupFilesDir, "file-entry-process.json")
    private val gson = Gson()

    private fun gate() {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashFileEntryAcceptance"))
    }

    @Test fun prepareLocalFileDraft(): Unit = runBlocking { prepare("file") }
    @Test fun prepareRemoteFileDraft(): Unit = runBlocking { prepare("remote") }
    @Test fun prepareScriptImport(): Unit = runBlocking { prepare("script") }
    @Test fun prepareProviderSideLoad(): Unit = runBlocking { prepare("provider") }

    private suspend fun prepare(kind: String) {
        gate(); check(!marker.exists()) { "前一轮文件验收尚未回收" }
        val e = entry; val repo = e.profileRepository(); val database = e.database()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertNull(e.profileDraftStore().read())
        val before = database.profiles().getAll()
        val scripts = database.scripts().getAll()
        val selected = withTimeout(10_000) { repo.currentProfileId.first { it != null } }
        val record = JsonObject().apply {
            addProperty("kind", kind); addProperty("id", System.nanoTime().and(Long.MAX_VALUE))
            addProperty("selected", selected)
            addProperty("rows", hash(gson.toJson(before).toByteArray()))
            addProperty("files", profileFiles())
            addProperty("scripts", hash(gson.toJson(scripts).toByteArray()))
            add("scriptIds", gson.toJsonTree(scripts.map { it.id }))
        }
        marker.writeText(record.toString())
        if (kind == "script") {
            val name = InstrumentationRegistry.getArguments().getString("kclashScriptFixtureName") ?: error("缺少脚本夹具名")
            check(name.matches(Regex("kclash-picker-[0-9]+\\.js")))
            record.addProperty("scriptName", name.removeSuffix(".js"))
            val seed = e.scriptRepository().create(name.removeSuffix(".js"), "// existing fixture\nconst main = config => config;")
            record.addProperty("seedScript", seed.id); marker.writeText(record.toString())
            return
        }
        val id = record.get("id").asLong
        val bytes = readAuthorizedProxyProfile()
        var target = if (kind == "remote") Profile.remote(id, "https://example.test/local-picker-only", "picker-process-original").copy(autoUpdate = false)
            else Profile.custom(id, "picker-process-original").copy(type = ProfileType.FILE)
        if (kind == "provider") {
            val cache = File(e.paths().homeDir, "picker-provider-$id.yaml")
            check(!cache.exists()); cache.parentFile!!.mkdirs(); cache.writeBytes(bytes)
            cache.setLastModified(1_600_000_000_000L)
            record.addProperty("providerFile", cache.path)
            val script = e.scriptRepository().create("picker-provider-$id", "const main = config => { config['proxy-providers'] = {'picker-provider': {type: 'file', path: ${gson.toJson(cache.path)}}}; return config; };")
            record.addProperty("providerScript", script.id)
            target = target.copy(extendType = "script", scriptId = script.id)
        }
        database.profiles().insert(target.copy(orderIndex = before.size).toEntity())
        repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        record.addProperty("targetMtime", repo.profileFile(id).lastModified())
        marker.writeText(record.toString())
        if (kind == "provider") {
            e.runtimeManager().selectProfile(id, repo::selectProfile)
            e.runtimeManager().getProxyGroups().getOrThrow()
            assertTrue(e.runtimeManager().profileProviders(id).any { it.name == "picker-provider" })
            val cache = File(record.get("providerFile").asString)
            assertArrayEquals(bytes, cache.readBytes())
            record.addProperty("providerMtime", cache.lastModified()); marker.writeText(record.toString())
            return
        }
        val owner = ViewModelStore()
        lateinit var model: ProfilesViewModel
        instrumentation.runOnMainSync {
            model = ProfilesViewModel(repo, e.runtimeManager(), fileReader = AndroidProfileFileDocumentReader(context), draftStore = e.profileDraftStore())
            owner.put("file-entry", model); model.openEditor(target)
        }
        try {
            withTimeout(15_000) {
                while (if (kind == "remote") model.remoteEditorState.value.document == null else model.editorState.value.document == null) delay(100)
            }
            instrumentation.runOnMainSync {
                if (kind == "remote") model.updateRemoteDraft(model.remoteEditorState.value.draft.copy(label = "picker-pending-name", ageKey = "picker-pending-key"))
                else model.updateFileMetadataDraft("picker-pending-name", "picker-pending-key")
            }
            withTimeout(15_000) {
                while (e.profileDraftStore().read()?.toString(Charsets.UTF_8)?.contains("picker-pending-name") != true) delay(100)
            }
        } finally { instrumentation.runOnMainSync { owner.clear() } }
    }

    @Test fun verifyReturnedProfileFileIsOnlyStaged(): Unit = runBlocking {
        gate(); val record = record(); val id = record.get("id").asLong
        check(record.get("kind").asString in setOf("file", "remote"))
        val saved = withTimeout(15_000) {
            while (true) {
                val draft = entry.profileDraftStore().read()?.let { JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject }
                val parent = draft?.getAsJsonObject(if (record.get("kind").asString == "remote") "remote" else "editor")
                if (parent?.get("replacement")?.isJsonObject == true) return@withTimeout requireNotNull(draft)
                delay(100)
            }
            error("unreachable")
        }
        val remote = record.get("kind").asString == "remote"
        val parent = saved.getAsJsonObject(if (remote) "remote" else "editor")
        assertEquals(id, parent.getAsJsonObject("profile").get("id").asLong)
        assertEquals(34814, parent.getAsJsonObject("replacement").get("sizeBytes").asInt)
        assertFalse(parent.get("isPickingFile").asBoolean)
        val bytes = gson.fromJson(saved.get(if (remote) "remoteBytes" else "fileBytes"), ByteArray::class.java)
        assertArrayEquals(readAuthorizedProxyProfile(), bytes)
        assertEquals("picker-process-original", entry.database().profiles().getById(id)?.label)
        assertEquals(record.get("targetMtime").asLong, entry.profileRepository().profileFile(id).lastModified())
        assertArrayEquals(readAuthorizedProxyProfile(), entry.profileRepository().profileFile(id).readBytes())
        verifyOriginals(record)
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_file_entry_phase", "returned-staged-no-commit") })
    }

    @Test fun verifyProfileOuterSaveAndCleanup(): Unit = runBlocking {
        gate(); val record = record(); val id = record.get("id").asLong
        assertEquals("picker-pending-name", entry.database().profiles().getById(id)?.label)
        assertEquals("picker-pending-key", entry.database().profiles().getById(id)?.ageSecretKey)
        assertArrayEquals(readAuthorizedProxyProfile(), entry.profileRepository().profileFile(id).readBytes())
        assertNull(entry.profileDraftStore().read())
        cleanup(record)
    }

    @Test fun verifyScriptImportedWithoutOverwritingAndCleanup(): Unit = runBlocking {
        gate(); val record = record(); assertEquals("script", record.get("kind").asString)
        val seed = record.get("seedScript").asLong
        val name = record.get("scriptName").asString
        val oldIds = record.getAsJsonArray("scriptIds").map { it.asLong }.toSet()
        val created = entry.database().scripts().getAll().filter { it.id !in oldIds && it.id != seed }
        assertEquals(1, created.size)
        assertEquals("$name-01", created.single().label)
        assertEquals("const main = config => config;\n", entry.scriptRepository().readContent(created.single().id))
        assertEquals("// existing fixture\nconst main = config => config;", entry.scriptRepository().readContent(seed))
        assertNull(entry.scriptPickerStore().read())
        cleanup(record)
    }

    @Test fun verifyProviderSideLoadReadbackAndCleanup(): Unit = runBlocking {
        gate(); val record = record(); assertEquals("provider", record.get("kind").asString)
        val cache = File(record.get("providerFile").asString)
        assertArrayEquals(readAuthorizedProxyProfile(), cache.readBytes())
        assertTrue("真实侧载应写入Provider缓存", cache.lastModified() > record.get("providerMtime").asLong)
        entry.runtimeManager().getProxyGroups().getOrThrow()
        val document = entry.runtimeManager().readProfileProvider(record.get("id").asLong, "picker-provider")
        assertArrayEquals(readAuthorizedProxyProfile(), document.document.content.toByteArray())
        assertNull(entry.providerPickerStore().read())
        cleanup(record)
    }

    @Test fun verifyCancelledPickerAndCleanup(): Unit = runBlocking {
        gate(); val record = record()
        when (record.get("kind").asString) {
            "file", "remote" -> {
                val remote = record.get("kind").asString == "remote"
                val saved = JsonParser.parseString(requireNotNull(entry.profileDraftStore().read()).toString(Charsets.UTF_8)).asJsonObject
                val parent = saved.getAsJsonObject(if (remote) "remote" else "editor")
                assertFalse(parent.get("isPickingFile").asBoolean)
                assertFalse(parent.get("replacement")?.isJsonObject == true)
                assertEquals("picker-process-original", entry.database().profiles().getById(record.get("id").asLong)?.label)
                assertEquals(record.get("targetMtime").asLong, entry.profileRepository().profileFile(record.get("id").asLong).lastModified())
            }
            "script" -> {
                val oldIds = record.getAsJsonArray("scriptIds").map { it.asLong }.toSet() + record.get("seedScript").asLong
                assertEquals(oldIds, entry.database().scripts().getAll().map { it.id }.toSet())
                assertNull(entry.scriptPickerStore().read())
            }
            "provider" -> {
                val cache = File(record.get("providerFile").asString)
                assertEquals(record.get("providerMtime").asLong, cache.lastModified())
                assertArrayEquals(readAuthorizedProxyProfile(), cache.readBytes())
                assertNull(entry.providerPickerStore().read())
            }
        }
        cleanup(record)
    }

    @Test fun cleanupOnlyRecordedFixtureAfterFailure(): Unit = runBlocking { gate(); cleanup(record()) }

    private suspend fun cleanup(record: JsonObject) {
        val e = entry; val id = record.get("id").asLong
        e.runtimeManager().stop().getOrThrow()
        e.profileDraftStore().write(null)
        e.runtimeManager().selectProfile(record.get("selected").asLong, e.profileRepository()::selectProfile)
        e.database().profiles().getById(id)?.let { check(it.label in setOf("picker-process-original", "picker-pending-name")); e.profileRepository().deleteProfile(id) }
        val oldIds = record.getAsJsonArray("scriptIds").map { it.asLong }.toSet()
        e.database().scripts().getAll().filter { it.id !in oldIds }.forEach { script ->
            check(script.label.startsWith("kclash-picker-") || script.label == "picker-provider-$id")
            e.scriptRepository().delete(script.id)
        }
        record.get("providerFile")?.asString?.let { path ->
            val cache = File(path); check(cache.parentFile == e.paths().homeDir && cache.name == "picker-provider-$id.yaml")
            check(cache.delete())
        }
        verifyOriginals(record)
        assertEquals(record.get("scripts").asString, hash(gson.toJson(e.database().scripts().getAll()).toByteArray()))
        assertTrue(marker.delete())
    }

    private suspend fun verifyOriginals(record: JsonObject) {
        val id = record.get("id").asLong
        val rows = entry.database().profiles().getAll().filter { it.id != id }
        assertEquals(record.get("rows").asString, hash(gson.toJson(rows).toByteArray()))
        assertEquals(record.get("files").asString, profileFiles(id))
        if (record.get("kind").asString != "provider") assertEquals(record.get("selected").asLong, entry.profileRepository().currentProfileId.value)
    }

    private suspend fun profileFiles(exclude: Long? = null) = entry.database().profiles().getAll().filter { it.id != exclude }.joinToString("|") {
        entry.profileRepository().profileFile(it.id).let { file -> "${it.id}:${file.length()}:${file.lastModified()}:${hash(file.readBytes())}" }
    }
    private fun record() = JsonParser.parseString(marker.readText()).asJsonObject
    private fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
