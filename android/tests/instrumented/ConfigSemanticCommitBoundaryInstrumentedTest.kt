package com.lonnnnnng.flclash

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.ui.*
import com.lonnnnnng.flclash.validation.ConfigSemanticException
import com.lonnnnnng.flclash.validation.IsolatedConfigValidator
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：只读授权原文，候选仅在内存修改；独占目录/Room验证提交边界，真实运行用独立motion包。 */
class ConfigSemanticCommitBoundaryInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private suspend fun original(): ByteArray {
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val row = entry.database().profiles().getAll().single { it.type == ProfileType.FILE }
        return entry.profileRepository().profileFile(row.id).readBytes().also {
            assertEquals(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"), sha(it))
        }
    }
    private fun invalid(bytes: ByteArray): ByteArray {
        val root = JsonParser.parseString(YamlConfigCodec.toJson(bytes.decodeToString())).asJsonObject
        root.getAsJsonArray("proxies").first().asJsonObject.addProperty("type", "semantic-invalid-protocol")
        return YamlConfigCodec.toYaml(root.toString()).toByteArray()
    }
    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to "${it.length()}:${it.lastModified()}:${sha(it.readBytes())}"
    }
    private fun draft(owner: Long, bytes: ByteArray) = Gson().toJson(ProfileEditorDraftSnapshot(
        document = ProfileDocumentEditorState(ownerId = owner, original = bytes.decodeToString(),
            content = bytes.decodeToString() + "\n# semantic uncommitted draft\n"), fileBytes = bytes,
    )).toByteArray()
    private fun report(case: String, vararg values: Pair<String, Any>) {
        val json = JSONObject().put("case", case).put("passed", true)
        values.forEach { (key, value) -> json.put(key, value) }
        ins.sendStatus(2, Bundle().apply { putString("kclash_semantic_boundary", json.toString()) })
    }
    private suspend fun isolated(block: suspend (Context, KClashDatabase, CoroutineScope, ByteArray) -> Unit) {
        val bytes = original()
        val root = File(context.cacheDir, "semantic-commit-${java.util.UUID.randomUUID()}").apply { check(mkdir()) }
        val scoped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try { block(scoped, db, scope, bytes) }
        finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
            check(root.parentFile == context.cacheDir && root.name.startsWith("semantic-commit-"))
            check(root.deleteRecursively())
        }
    }

    @Test fun mixedProfilesAndEncryptedPendingDraftSurviveSemanticRejectionAndValidImport() = runBlocking(Dispatchers.IO) {
        isolated { scoped, db, scope, bytes ->
            val client = object : SubscriptionClient {
                // 作者 long：此例隔离来源分支而不声称实网下载；所有正文都来自用户本地原文件。
                override suspend fun download(url: String) = SubscriptionDownload(bytes, null, SubscriptionInfo())
            }
            val validator = IsolatedConfigValidator(scoped)
            val repo = ProfileRepository(scoped, db, client, validator, observationScope = scope)
            repo.profilesLoaded.first { it }
            val file = repo.addFileProfile("semantic-file", bytes)
            val remote = repo.addRemoteProfile("https://example.test/semantic-memory-source", "semantic-url")
            val custom = repo.addCustomProfile("semantic-custom")
            repo.replaceCustomProfile(custom.id, bytes.decodeToString(), custom, "") { true }
            withTimeout(5_000) { repo.profiles.first { it.size == 3 } }
            val pending = draft(file.id, bytes)
            val store = AndroidProfileDraftStore(scoped)
            store.write(pending)
            val draftFile = File(scoped.noBackupFilesDir, "profile-editor-draft.v1")
            assertFalse(draftFile.readBytes().toString(Charsets.ISO_8859_1).contains("semantic uncommitted draft"))
            val baseline = files(scoped.filesDir); val encrypted = files(scoped.noBackupFilesDir)
            val rows = db.profiles().getAll(); val preferences = db.appPreferences().getByPrefix("")
            assertEquals(setOf(ProfileType.FILE, ProfileType.URL, ProfileType.CUSTOM), rows.map { it.type }.toSet())
            assertTrue(validator.checkFull(bytes).passed)
            val failure = runCatching { repo.importFileProfile("semantic-invalid", invalid(bytes), "") { true } }.exceptionOrNull()
            assertTrue(failure?.javaClass?.simpleName, failure is ConfigSemanticException)
            assertEquals(rows, db.profiles().getAll()); assertEquals(preferences, db.appPreferences().getByPrefix(""))
            assertEquals(baseline, files(scoped.filesDir)); assertEquals(encrypted, files(scoped.noBackupFilesDir))
            assertArrayEquals(pending, AndroidProfileDraftStore(scoped).read())
            val imported = repo.importFileProfile("semantic-valid", bytes, "") { true }
            assertEquals(rows, db.profiles().getAll().filter { it.id != imported.id })
            assertEquals(preferences, db.appPreferences().getByPrefix(""))
            assertEquals(baseline, files(scoped.filesDir).filterKeys { it != repo.profileFile(imported.id).relativeTo(scoped.filesDir).path })
            assertEquals(encrypted, files(scoped.noBackupFilesDir)); assertArrayEquals(pending, store.read())
            assertArrayEquals(bytes, repo.profileFile(remote.id).readBytes())
            report("mixed-and-draft", "profileTypes" to 3, "draftCiphertextAndMtimeHeld" to true, "existingFilesHeld" to true)
        }
    }

    @Test fun managedSemanticRejectionPrecedesCommitAndApplyFailureRetriesWithoutDownload() = runBlocking(Dispatchers.IO) {
        isolated { scoped, db, scope, bytes ->
            var downloaded = invalid(bytes); var downloads = 0; var commits = 0; var failApply = true
            val client = object : SubscriptionClient {
                override suspend fun download(url: String): SubscriptionDownload {
                    downloads++; return SubscriptionDownload(downloaded, null, SubscriptionInfo())
                }
            }
            val committer = RemoteProfileCommitter { _, write ->
                commits++
                val saved = write()
                // 作者 long：明确注入提交后的应用边界错误，验证待应用回执；不冒充实际内核reload故障。
                if (failApply) throw ProfileApplyAfterSaveException(saved.id)
                saved
            }
            fun repository() = ProfileRepository(scoped, db, client, IsolatedConfigValidator(scoped),
                automaticCommitter = committer, observationScope = scope)
            val repo = repository(); repo.profilesLoaded.first { it }
            val file = repo.addFileProfile("semantic-managed", bytes)
            db.profiles().update(checkNotNull(db.profiles().getById(file.id)).copy(type = ProfileType.URL,
                url = "https://example.test/semantic-memory-source", autoUpdate = true, lastUpdateAt = null))
            val before = files(scoped.filesDir)
            val rejected = repo.updateDueProfiles()
            assertEquals(1, rejected.failures.size); assertTrue(rejected.applyFailures.isEmpty()); assertEquals(0, commits)
            assertEquals(before, files(scoped.filesDir)); assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
            downloaded = bytes
            val failed = repo.updateDueProfiles()
            assertEquals(1, failed.succeeded); assertEquals(1, failed.applyFailures.size); assertTrue(failed.failures.isEmpty())
            assertEquals(1, commits); assertEquals(2, downloads)
            assertEquals(1, db.appPreferences().getByPrefix("subscription-pending-apply:").size)
            val rows = db.profiles().getAll(); val saved = files(scoped.filesDir)
            failApply = false
            val recreated = repository(); recreated.profilesLoaded.first { it }
            val retried = recreated.updateDueProfiles()
            assertEquals(listOf(file.id), retried.appliedSourceIds); assertEquals(0, retried.attempted)
            assertEquals(2, commits); assertEquals(2, downloads)
            assertEquals(rows, db.profiles().getAll()); assertEquals(saved, files(scoped.filesDir))
            assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
            assertTrue(recreated.updateDueProfiles().appliedSourceIds.isEmpty()); assertEquals(2, commits)
            report("managed-apply-boundary", "semanticRejectBeforeCommit" to true, "applyFaultInjected" to true,
                "retryDownloads" to 0, "retryFileAndMtimeHeld" to true, "noReplay" to true)
        }
    }

    @Test fun runningProxyRejectsImportAndPreservesDraftUntilSamePreviewRetriesAfterStop() = running(false)
    @Test fun runningVpnKeepsTunnelAndDraftAcrossRejectedImportAndStoppedRetry() = running(true)

    @Test fun realRuntimeManagedApplyFailureKeepsSavedBodyAndRetriesWithoutDownloading(): Unit = runBlocking(Dispatchers.IO) {
        val bytes = original(); val repo = entry.profileRepository(); val db = entry.database(); val runtime = entry.runtimeManager()
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val oldVpn = runtime.vpnSettings.value.enabled; val selected = repo.currentProfileId.value
        val rows = db.profiles().getAll(); val sources = rows.associate { it.id to repoFileStamp(it.id) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var ownId: Long? = null; var scriptId: Long? = null; var downloads = 0
        val next = bytes + "\n# semantic owned managed update\n".toByteArray()
        val client = object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload {
                downloads++; return SubscriptionDownload(next, null, SubscriptionInfo())
            }
        }
        val managed = ProfileRepository(context, db, client, IsolatedConfigValidator(context),
            automaticCommitter = RemoteProfileCommitter { id, write -> runtime.withProfileSourceWrite(id, write) }, observationScope = scope)
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use {
            // 作者 long：阻止Hilt定时器抢走本例到期任务；故障经过真实生命周期锁与脚本应用，不用抛异常替身代替管理器。
            repo.withAutomaticUpdateBarrier {
                try {
                    val own = repo.addFileProfile("semantic-managed-${java.util.UUID.randomUUID()}", bytes); ownId = own.id
                    db.profiles().update(checkNotNull(db.profiles().getById(own.id)).copy(type = ProfileType.URL,
                        url = "https://example.test/semantic-memory-source", autoUpdate = true, lastUpdateAt = null))
                    runtime.setVpnEnabled(false).getOrThrow(); runtime.selectProfile(own.id, repo::selectProfile); runtime.startProxy().getOrThrow()
                    request()
                    val script = entry.scriptRepository().create("semantic-owned-apply-fault", "const main = config => { throw new Error('semantic-owned-apply-fault'); };")
                    scriptId = script.id; repo.updateProfileScript(own.id, script.id)
                    val since = runtime.runningSinceElapsedMillis.value
                    val failed = managed.updateDueProfiles()
                    assertEquals(1, failed.succeeded); assertEquals(1, failed.applyFailures.size); assertTrue(failed.failures.isEmpty())
                    assertEquals(1, downloads); assertArrayEquals(next, repo.profileFile(own.id).readBytes())
                    assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value); assertEquals(since, runtime.runningSinceElapsedMillis.value)
                    assertEquals(1, db.appPreferences().getByPrefix("subscription-pending-apply:").size)
                    val saved = repoFileStamp(own.id)
                    repo.updateProfileScript(own.id, null)
                    val retry = managed.updateDueProfiles()
                    assertEquals(listOf(own.id), retry.appliedSourceIds); assertEquals(0, retry.attempted); assertEquals(1, downloads)
                    assertEquals(saved, repoFileStamp(own.id)); assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
                    request()
                    assertTrue(managed.updateDueProfiles().appliedSourceIds.isEmpty()); assertEquals(1, downloads)
                    report("real-runtime-managed-apply", "actualRuntimeManager" to true, "scriptApplyFault" to true,
                        "runningSessionRecovered" to true, "savedBodyAndMtimeHeldOnRetry" to true, "retryDownloads" to 0,
                        "realHttpsRequests" to 2, "noReplay" to true)
                } finally {
                    withContext(NonCancellable) {
                        scope.coroutineContext[Job]?.cancelAndJoin(); runtime.stop().getOrThrow()
                        if (selected != null) runtime.selectProfile(selected, repo::selectProfile) else repo.selectProfile(null)
                        ownId?.let { repo.deleteProfile(it) }; scriptId?.let { entry.scriptRepository().delete(it) }
                        runtime.setVpnEnabled(oldVpn).getOrThrow()
                    }
                }
            }
        }
        assertEquals(rows, db.profiles().getAll()); assertEquals(sources, rows.associate { it.id to repoFileStamp(it.id) })
    }

    @Test fun authorizedExternalRulesDownloadAndValidateGrantedBodiesWithoutChangingSources(): Unit = runBlocking(Dispatchers.IO) {
        val runtime = entry.runtimeManager()
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val input = JSONObject(checkNotNull(stabilityInput(context).read()).decodeToString())
        val url = input.getString("authorizedUrl")
        assertEquals(InstrumentationRegistry.getArguments().getString("kclashAuthorizedSourceSha256"), sha(url.toByteArray()))
        val oldVpn = runtime.vpnSettings.value.enabled
        val rows = entry.database().profiles().getAll()
        val originals = rows.associate { row -> row.id to repoFileStamp(row.id) }
        var remoteId: Long? = null
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use {
            try {
                // 作者 long：远程来源也经过生产Repository和隔离校验器导入；只增加本例副本，原本地选择和正文保持。
                val remote = entry.profileRepository().addRemoteProfile(url, "semantic-network-${java.util.UUID.randomUUID()}")
                remoteId = remote.id
                val source = entry.profileRepository().profileFile(remote.id).readBytes()
                InstrumentationRegistry.getArguments().getString("kclashSemanticSourceSha256")?.let { assertEquals(it, sha(source)) }
                runtime.setVpnEnabled(false).getOrThrow(); runtime.startProxy().getOrThrow()
                val epoch = entry.coreBridge().ipRouteStamp().epoch
                val root = JsonParser.parseString(YamlConfigCodec.toJson(source.decodeToString())).asJsonObject
                val definitions = checkNotNull(root.getAsJsonObject("rule-providers"))
                assertEquals("本例只覆盖授权源原有90个HTTP规则资源", 90, definitions.size())
                assertTrue(definitions.entrySet().all { it.value.asJsonObject["type"].asString == "http" })
                assertEquals(90, IsolatedConfigValidator(context).checkFull(source).deferredResources)
                val requested = InstrumentationRegistry.getArguments().getString("kclashSemanticResourceHashes").orEmpty()
                    .split(',').filter { it.isNotBlank() }.toSet()
                val targets = definitions.entrySet().withIndex().filter { requested.isEmpty() || sha(it.value.key.toByteArray()) in requested }
                if (requested.isNotEmpty()) assertEquals("补验只接受本授权源已有资源", requested.size, targets.size)
                val route = runtime.downloadRoute()
                assertNotNull("实网资源必须通过当前核心的真实HTTP入站", route.port)
                val http = okhttp3.OkHttpClient.Builder()
                    .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", checkNotNull(route.port))))
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).retryOnConnectionFailure(false)
                    .proxyAuthenticator { _, response ->
                        val username = route.username; val password = route.password
                        if (username == null || password == null || response.request.header("Proxy-Authorization") != null) null
                        else response.request.newBuilder().header("Proxy-Authorization", okhttp3.Credentials.basic(username, password)).build()
                    }.build()
                try {
                isolated { scoped, _, _, _ ->
                    val home = File(scoped.filesDir, "mihomo").apply { mkdirs() }
                    val semaphore = Semaphore(4)
                    // 作者 long：每个远端正文都真实下载后授予只读FD；不把HTTP定义解析或旧缓存当作完整资源覆盖。
                    val fetched = coroutineScope {
                        targets.map { (index, item) -> val (name, value) = item; async {
                            semaphore.withPermit {
                                val provider = value.asJsonObject
                                val result = runCatching {
                                    // 作者 long：VPN拥有者的普通下载可能绕过自身，资源实网显式走实际核心入站；不改原URL、不切加速源、不自动重试。
                                    val request = okhttp3.Request.Builder().url(provider["url"].asString)
                                        .header("User-Agent", "KClash-Semantic-Acceptance clash-verge Platform/android").build()
                                    val body = http.newCall(request).execute().use { response ->
                                        check(response.isSuccessful) { "资源HTTP失败" }
                                        checkNotNull(response.body).byteStream().use { readProfileFileBytes(it) }
                                    }
                                    val path = "semantic-rule-$index.${provider["format"]?.asString ?: "yaml"}"
                                    File(home, path).writeBytes(body)
                                    Triple(path, body.size, sha(body))
                                }
                                ins.sendStatus(2, Bundle().apply { putString("kclash_semantic_resource", JSONObject()
                                    .put("index", index).put("nameSha256", sha(name.toByteArray())).put("passed", result.isSuccess)
                                    .put("bytes", result.getOrNull()?.second ?: 0).put("bodySha256", result.getOrNull()?.third ?: JSONObject.NULL)
                                    .put("failureType", result.exceptionOrNull()?.javaClass?.simpleName ?: JSONObject.NULL).toString()) })
                                name to result
                            }
                        } }.awaitAll()
                    }
                    val successful = fetched.filter { it.second.isSuccess }
                    val baseline = files(scoped.filesDir)
                    // 作者 long：隔离入口最多接收64个FD（含Geo），按30项分批授予；保留其余HTTP定义，不能虚称90份一次全解析。
                    successful.chunked(30).forEach { batch ->
                        val candidate = root.deepCopy()
                        batch.forEach { (name, result) ->
                            candidate.getAsJsonObject("rule-providers").getAsJsonObject(name).apply {
                                addProperty("type", "file"); addProperty("path", result.getOrThrow().first); remove("url")
                            }
                        }
                        val complete = IsolatedConfigValidator(scoped).checkFull(YamlConfigCodec.toYaml(candidate.toString()).toByteArray())
                        assertTrue(complete.passed); assertEquals(90 - batch.size, complete.deferredResources)
                    }
                    assertEquals(baseline, files(scoped.filesDir))
                    report("external-rule-bodies", "passed" to (successful.size == targets.size), "resources" to successful.size, "networkAttempts" to targets.size,
                        "fullBodySemanticParse" to true, "validationBatches" to successful.chunked(30).size,
                        "singlePassAllBodies" to false, "realUrlRepositoryImport" to true,
                        "scope" to if (requested.isEmpty()) "all" else "targeted",
                        "explicitCoreHttpRoute" to true, "sourceSha256" to sha(source))
                    assertEquals("资源首轮下载失败，成功正文已解析且逐项失败保留", targets.size, successful.size)
                }
                } finally { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
                assertEquals(epoch, entry.coreBridge().ipRouteStamp().epoch)
                assertEquals(rows, entry.database().profiles().getAll().filter { it.id != remote.id })
                assertEquals(originals, rows.associate { row -> row.id to repoFileStamp(row.id) })
            } finally { withContext(NonCancellable) {
                runtime.stop().getOrThrow(); remoteId?.let { entry.profileRepository().deleteProfile(it) }
                runtime.setVpnEnabled(oldVpn).getOrThrow()
            } }
        }
        assertEquals(rows, entry.database().profiles().getAll())
    }

    private fun repoFileStamp(id: Long) = entry.profileRepository().profileFile(id).let { Triple(it.length(), it.lastModified(), sha(it.readBytes())) }

    private fun running(vpn: Boolean): Unit = runBlocking(Dispatchers.IO) {
        val bytes = original(); val repo = entry.profileRepository(); val runtime = entry.runtimeManager()
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val oldVpn = runtime.vpnSettings.value.enabled
        val draftStore = entry.profileDraftStore(); assertNull("不覆盖已有草稿", draftStore.read())
        val owner = entry.database().profiles().getAll().single { it.type == ProfileType.FILE }.id
        val pending = draft(owner, bytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val validator = IsolatedConfigValidator(context)
        val controller = ProfileFileImportController(repo, runtime, ProfileFileDocumentReader {
            ProfileFileDocument("authorized.yaml", bytes)
        }, validator, scope)
        var imported: Long? = null
        val initialRows = entry.database().profiles().getAll()
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
            try {
                if (vpn) VpnService.prepare(context)?.let { permission ->
                    assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashSemanticVpnGrant"))
                    // 作者 long：系统VPN确认依赖callingPackage，必须由Activity带结果启动；application直接启动会被系统立即关闭。
                    scenario.onActivity { it.startActivityForResult(permission, 8126) }
                    withTimeout(15_000) {
                        while (VpnService.prepare(context) != null) {
                            val root = ins.uiAutomation.rootInActiveWindow
                            if (root?.packageName?.toString() == "com.android.vpndialogs") {
                                root.findAccessibilityNodeInfosByViewId("android:id/button1").singleOrNull()
                                    ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            delay(100)
                        }
                    }
                }
                draftStore.write(pending)
                controller.acceptFile(checkNotNull(controller.beginPick()), "content://semantic-authorized-snapshot")
                withTimeout(35_000) { controller.state.first { !it.reading && it.validYaml } }
                runtime.setVpnEnabled(vpn).getOrThrow(); runtime.startProxy().getOrThrow()
                withTimeout(45_000) { runtime.state.first { it == CoreRuntimeStatus.RUNNING } }
                val tunnel = if (vpn) tunnelStamp() else null
                // 作者 long：此例验证运行中VPN的提交保护，业务连通性明确走本地SOCKS；不能将它记成TUN流量通过。
                request()
                val epoch = entry.coreBridge().ipRouteStamp().epoch; val since = runtime.runningSinceElapsedMillis.value
                val rows = entry.database().profiles().getAll(); val selected = repo.currentProfileId.value
                val sourceFiles = rows.associate { row -> row.id to repo.profileFile(row.id).let { f -> Triple(f.length(), f.lastModified(), sha(f.readBytes())) } }
                val encrypted = files(context.noBackupFilesDir)
                controller.confirm()
                assertEquals(PROFILE_MUTATION_BLOCKED_MESSAGE, controller.state.value.error)
                val rejected = runCatching { repo.importFileProfile("semantic-blocked", bytes, "") { !isProfileMutationBlocked(runtime.state.value) } }.exceptionOrNull()
                assertTrue(rejected?.javaClass?.simpleName, rejected is IllegalStateException)
                assertEquals(rows, entry.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
                sourceFiles.forEach { (id, stamp) -> assertEquals(stamp, repo.profileFile(id).let { f -> Triple(f.length(), f.lastModified(), sha(f.readBytes())) }) }
                assertEquals(encrypted, files(context.noBackupFilesDir)); assertArrayEquals(pending, draftStore.read())
                assertEquals(epoch, entry.coreBridge().ipRouteStamp().epoch); assertEquals(since, runtime.runningSinceElapsedMillis.value)
                if (vpn) assertEquals(tunnel, tunnelStamp())
                request()
                runtime.stop().getOrThrow()
                controller.confirm()
                withTimeout(35_000) { controller.state.first { !it.saving && it.fileName == null } }
                val added = entry.database().profiles().getAll().filter { row -> initialRows.none { it.id == row.id } }.single()
                imported = added.id
                assertArrayEquals(bytes, repo.profileFile(added.id).readBytes())
                assertEquals(selected, repo.currentProfileId.value); assertEquals(encrypted, files(context.noBackupFilesDir))
                report(if (vpn) "running-vpn" else "running-proxy", "realHttpsRequests" to 2,
                    "epochAndRunningSinceHeld" to true, "draftCiphertextAndMtimeHeld" to true,
                    "samePreviewImportedAfterStop" to true, "tunnelBoundRequests" to false,
                    "trafficRoute" to "explicit-local-SOCKS", "vpnInterfaceAndNetworkHeld" to vpn)
            } finally {
                withContext(NonCancellable) {
                    controller.dismiss(); scope.coroutineContext[Job]?.cancelAndJoin()
                    runtime.stop().getOrThrow(); runtime.setVpnEnabled(oldVpn).getOrThrow()
                    imported?.let { repo.deleteProfile(it) }
                    // 作者 long：仅删除本例写入的草稿；不把意外变化的未知会话当作测试残留清除。
                    if (draftStore.read()?.contentEquals(pending) == true) draftStore.write(null)
                }
            }
        }
        assertEquals(initialRows, entry.database().profiles().getAll()); assertNull(draftStore.read())
    }

    private suspend fun tunnelStamp(): Triple<Long, String, String> {
        withTimeout(20_000) {
            com.lonnnnnng.flclash.service.ServiceConfig.vpnState.first { it == com.lonnnnnng.flclash.service.VpnRuntimeState.CONNECTED }
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return withTimeout(10_000) {
            while (true) {
                val network = connectivity.allNetworks.singleOrNull {
                    connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                        !connectivity.getLinkProperties(it)?.interfaceName.isNullOrBlank()
                }
                if (network != null) {
                    val link = checkNotNull(connectivity.getLinkProperties(network))
                    return@withTimeout Triple(network.networkHandle, checkNotNull(link.interfaceName), sha(link.toString().toByteArray()))
                }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("VPN网络未出现")
        }
    }

    private suspend fun request() {
        val target = URI(checkNotNull(InstrumentationRegistry.getArguments().getString("kclashAcceptanceUrl1")))
        val result = acceptedProxyRequest(entry, target) { _, _ -> }
        assertTrue(result.toString(), result.getBoolean("passed"))
    }
}
