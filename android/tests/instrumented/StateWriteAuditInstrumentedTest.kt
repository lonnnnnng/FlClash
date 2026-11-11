package com.lonnnnnng.flclash

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.common.StateWriteAudit
import com.lonnnnnng.flclash.common.auditedPreferences
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.UUID

/** 作者 long：真实SQLite验证审计不会把内部DAO正常返回、外层回滚或apply受理误记为已持久提交。 */
class StateWriteAuditInstrumentedTest {
    @Test fun largeSnapshotTransportKeepsExactBytesOutsideLogcatChunks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val token = UUID.randomUUID().toString().replace("-", "")
        val command = "snapshot-transport-test"
        val result = org.json.JSONObject().put("padding", "x".repeat(70_000))
        stabilityReport(context, token, command, result)
        val bytes = result.toString().toByteArray()
        val directory = java.io.File(checkNotNull(context.getExternalFilesDir(null)), "stability-reports")
        val expected = java.io.File(directory, "$token-$command-${stabilitySha(bytes)}.json")
        try {
            assertTrue(expected.isFile)
            assertArrayEquals(bytes, expected.readBytes())
            assertEquals(token, org.json.JSONObject(expected.readText()).getString("token"))
        } finally {
            // 作者 long：只删除本用例以token和正文SHA标识的文件，失败窗口的其他证据必须保留。
            if (expected.isFile) { assertEquals(stabilitySha(bytes), stabilitySha(expected.readBytes())); assertTrue(expected.delete()) }
        }
    }

    @Test fun perKeySnapshotsMatchCommittedWritesAndKeepRollbackDistinct() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val prior = StateWriteAudit.sink
        val events = Collections.synchronizedList(mutableListOf<Map<String, Any?>>())
        val secret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        StateWriteAudit.install(object : StateWriteAudit.Sink {
            override fun digest(bytes: ByteArray): String = javax.crypto.Mac.getInstance("HmacSHA256").apply {
                init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"))
            }.doFinal(bytes).joinToString("") { "%02x".format(it) }
            override fun append(record: Map<String, Any?>) { events += record }
            override fun dropped() { error("本用例不允许丢审计记录") }
        })
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java)
            .openHelperFactory(StateAuditPlatform.factory()).build()
        val store = "state-audit-key-test-${UUID.randomUUID()}"
        val gson = Gson()
        try {
            val row = ProxyDelayEntity(1, "private-group", "private-node", "private-url", 23, 100)
            val otherUrl = row.copy(testUrl = "private-url-2", value = 47)
            StateWriteAudit.operation("test.delay.first") { db.proxyDelayResults().upsert(row) }
            StateWriteAudit.operation("test.delay.second") { db.proxyDelayResults().upsert(otherUrl) }
            val snapshot = StateAuditSnapshots.delays(db, listOf(1))
            val profile = snapshot.getJSONObject("profiles").getJSONObject("1")
            val fields = profile.getJSONObject("rows")
            assertEquals(2, fields.length())
            assertTrue(profile.getLong("finishedElapsedMs") >= profile.getLong("startedElapsedMs"))
            for (value in listOf(row, otherUrl)) {
                val key = checkNotNull(StateWriteAudit.digest(gson.toJson(listOf(value.profileId, value.groupName, value.proxyName, value.testUrl)).toByteArray()))
                val entity = events.single { it["kind"] == "entity_write" &&
                    gson.toJsonTree(it["fields"]) == com.google.gson.JsonParser.parseString(fields.getJSONObject(key).toString()) }
                assertTrue(events.any { it["kind"] == "db_committed" && it["operation"] == entity["operation"] })
            }
            // 作者 long：回滚时受理过的值不能匹配到读回状态，更不能仅凭DAO受理把它归为有效写者。
            StateWriteAudit.operation("test.delay.rollback") {
                try { db.withTransaction { db.proxyDelayResults().upsert(row.copy(value = 999)); error("rollback") } }
                catch (_: IllegalStateException) { }
            }
            val afterRollback = StateAuditSnapshots.delays(db, listOf(1)).getJSONObject("profiles").getJSONObject("1").getJSONObject("rows")
            assertEquals(com.google.gson.JsonParser.parseString(fields.toString()), com.google.gson.JsonParser.parseString(afterRollback.toString()))
            assertTrue(events.any { it["source"] == "test.delay.rollback" && it["kind"] == "db_rolled_back" })
            assertFalse(events.any { it["source"] == "test.delay.rollback" && it["kind"] == "db_committed" })
            StateWriteAudit.operation("test.delay.delete") { db.proxyDelayResults().delete(row.profileId, row.groupName, row.proxyName, row.testUrl) }
            val afterDelete = StateAuditSnapshots.delays(db, listOf(1)).getJSONObject("profiles").getJSONObject("1").getJSONObject("rows")
            assertEquals(1, afterDelete.length())
            val deleted = events.single { it["source"] == "test.delay.delete" && it["kind"] == "entity_write" }
            val expected = com.google.gson.JsonParser.parseString(StateAuditSnapshots.entityFields(row).toString()).asJsonObject
                .apply { remove("value"); remove("updatedAtMillis") }
            assertEquals(expected, gson.toJsonTree(deleted["fields"]))
            assertTrue(events.any { it["source"] == "test.delay.delete" && it["kind"] == "db_committed" })

            val preferences = auditedPreferences(context, store)
            assertTrue(preferences.edit().putString("text", "private-value").putBoolean("flag", true)
                .putLong("counter", 7L).putStringSet("set", linkedSetOf("private-b", "private-a")).commit())
            val shared = StateAuditSnapshots.preferences(context, listOf(store)).getJSONObject("stores").getJSONObject(store)
            val committed = events.single { it["kind"] == "preference_committed" }
            assertEquals(gson.toJsonTree(committed["after"]), com.google.gson.JsonParser.parseString(shared.getJSONObject("values").toString()))
            preferences.edit().putString("text", "private-next").apply()
            val applied = events.single { it["kind"] == "preference_apply_accepted" }
            val textKey = checkNotNull(StateWriteAudit.digest("text".toByteArray()))
            val current = StateAuditSnapshots.preferences(context, listOf(store)).getJSONObject("stores").getJSONObject(store).getJSONObject("values")
            assertEquals(gson.toJsonTree((applied["after"] as Map<*, *>)[textKey]), com.google.gson.JsonParser.parseString(current.getJSONObject(textKey).toString()))
            assertEquals(1, events.count { it["kind"] == "preference_committed" })
            assertTrue(preferences.edit().remove("text").commit())
            assertFalse(StateAuditSnapshots.preferences(context, listOf(store)).getJSONObject("stores").getJSONObject(store).getJSONObject("values").has(textKey))
            assertFalse(snapshot.toString().contains("private-"))
            assertFalse(shared.toString().contains("private-value"))
            ins.sendStatus(2, android.os.Bundle().apply { putString("kclash_audit_key_join", org.json.JSONObject()
                .put("delayCompositeKeys", 2).put("committedValuesMatch", true).put("rollbackExcluded", true)
                .put("deleteKeyMatched", true).put("sharedTypedValuesMatch", true).put("applyNotDiskCommit", true)
                .put("readIntervalsRecorded", true).toString()) })
        } finally {
            db.close()
            context.deleteSharedPreferences(store)
            StateWriteAudit.install(prior)
        }
    }

    @Test fun compressedRecordsKeepCompleteMembersAndResolvableStackDictionary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = UUID.randomUUID().toString().replace("-", "")
        StateAuditPlatform.enable(context, token)
        val directory = java.io.File(context.noBackupFilesDir, "state-audit/$token")
        var verified = false
        try {
            StateWriteAudit.operation("test.compressed") {
                repeat(20) { StateWriteAudit.event("compression_marker", mapOf("n" to it)) }
            }
            val files = StateAuditPlatform.records(context, token)
            assertEquals(1, files.size)
            val rows = java.util.zip.GZIPInputStream(files.single().inputStream()).bufferedReader().use { reader ->
                reader.readLines().map { org.json.JSONObject(it) }
            }
            assertEquals(20, rows.count { it.getString("kind") == "compression_marker" })
            val dictionaries = rows.filter { it.getString("kind") == "stack_dictionary" }.map { it.getInt("stackId") }.toSet()
            rows.filter { it.has("sitesId") }.forEach { assertTrue(it.getInt("sitesId") in dictionaries) }
            rows.filter { it.has("originSitesId") }.forEach { assertTrue(it.getInt("originSitesId") in dictionaries) }
            assertEquals(rows.indices.map { it.toLong() + 1 }, rows.map { it.getLong("sequence") })
            assertTrue(rows.any { it.getString("kind") == "operation_completed" && it.getString("source") == "test.compressed" })
            assertEquals(0L, StateWriteAudit.sink?.status()?.get("dropped"))
            verified = true
        } finally {
            StateAuditPlatform.disable(context, token)
            if (verified) {
                directory.listFiles().orEmpty().forEach { check(it.isFile && it.delete()) }
                check(directory.delete())
            }
        }
    }

    @Test fun overflowingSessionStillDisablesWithoutDiscardingEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = UUID.randomUUID().toString().replace("-", "")
        StateAuditPlatform.enable(context, token)
        val directory = java.io.File(context.noBackupFilesDir, "state-audit/$token")
        var verified = false
        try {
            checkNotNull(StateWriteAudit.sink).append(mapOf("kind" to "test_overflow", "padding" to "x".repeat(65537)))
            assertTrue(runCatching { StateAuditPlatform.records(context, token) }.isFailure)
            StateAuditPlatform.disable(context, token)
            assertFalse(StateWriteAudit.enabled)
            assertTrue(directory.listFiles().orEmpty().any { it.name.endsWith(".overflow") })
            verified = true
        } finally {
            StateWriteAudit.install(null)
            if (verified) {
                check(directory.parentFile == java.io.File(context.noBackupFilesDir, "state-audit") && directory.name == token)
                directory.listFiles().orEmpty().forEach { check(it.isFile && it.delete()) }
                check(directory.delete())
            }
        }
    }

    @Test fun recordsRealDaoCommitsRollbacksAndPreferenceAcknowledgements() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val events = Collections.synchronizedList(mutableListOf<Map<String, Any?>>())
        val prior = StateWriteAudit.sink
        StateWriteAudit.install(object : StateWriteAudit.Sink {
            override fun digest(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            override fun append(record: Map<String, Any?>) { events += record }
            override fun dropped() = Unit
        })
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).openHelperFactory(StateAuditPlatform.factory()).build()
        val preferenceName = "state-audit-test-${UUID.randomUUID()}"
        try {
            StateWriteAudit.operation("test.outer.rollback") {
                try { db.withTransaction {
                    db.appPreferences().put(AppPreferenceEntity("audit-private-key", "audit-private-value", 1))
                    assertFalse(events.any { it["kind"] == "db_committed" })
                    error("rollback")
                } } catch (_: IllegalStateException) { }
            }
            assertNull(db.appPreferences().get("audit-private-key"))
            assertTrue(events.any { it["kind"] == "db_rolled_back" })
            events.clear()
            StateWriteAudit.operation("test.outer.commit") {
                db.withTransaction {
                    db.appPreferences().put(AppPreferenceEntity("audit-private-key", "audit-private-value", 2))
                    db.profiles().insert(Profile.custom(1, "audit-private-profile").toEntity())
                    db.profiles().update(checkNotNull(db.profiles().getById(1)).copy(currentGroupName = "audit-private-changed"))
                    db.proxyDelayResults().upsert(ProxyDelayEntity(1, "private-group", "private-node", "private-url", 23, 4))
                    assertFalse(events.any { it["kind"] == "db_committed" })
                }
            }
            assertEquals("audit-private-value", db.appPreferences().get("audit-private-key"))
            assertEquals(1, events.count { it["kind"] == "db_committed" })
            assertTrue(events.filter { it["kind"] == "db_write_executed" }.all { it["source"] == "test.outer.commit" })
            assertEquals(setOf("profiles", "app_preferences", "proxy_delay_results"), events.filter { it["kind"] == "db_write_executed" }.map { it["table"] }.toSet())
            assertTrue(events.any { it["kind"] == "db_write_executed" && it["table"] == "profiles" && it["verb"] == "UPDATE" })
            assertFalse(Gson().toJson(events).contains("audit-private-value"))
            events.clear()
            try { StateWriteAudit.operation("test.cancel") { db.withTransaction {
                db.appPreferences().delete("audit-private-key"); throw CancellationException("cancel")
            } } } catch (_: CancellationException) { }
            assertNotNull(db.appPreferences().get("audit-private-key"))
            assertTrue(events.any { it["kind"] == "db_rolled_back" })
            assertFalse(events.any { it["kind"] == "db_committed" })
            events.clear()
            val sql = db.openHelper.writableDatabase
            StateWriteAudit.operation("test.nested.rollback") {
                sql.beginTransaction()
                try {
                    sql.beginTransaction()
                    try { sql.execSQL("UPDATE app_preferences SET value=? WHERE `key`=?", arrayOf("private-rolled-back", "audit-private-key")) }
                    finally { sql.endTransaction() }
                    sql.setTransactionSuccessful()
                } finally { sql.endTransaction() }
            }
            assertEquals("audit-private-value", db.appPreferences().get("audit-private-key"))
            assertTrue(events.any { it["kind"] == "db_nested_end" })
            assertTrue(events.any { it["kind"] == "db_rolled_back" })
            assertFalse(events.any { it["kind"] == "db_committed" })
            events.clear()
            StateWriteAudit.operation("test.only.failed.statement") {
                sql.beginTransaction()
                try {
                    // 作者 long：真实NOT NULL约束拒绝事务中的唯一写入，仍须有最终回滚，不能只留下语句失败。
                    val failure = runCatching {
                        sql.execSQL("INSERT INTO app_preferences (`key`,value,updatedAt) VALUES (NULL,?,?)", arrayOf<Any?>("not-written", 1L))
                    }.exceptionOrNull()
                    assertTrue(failure is android.database.sqlite.SQLiteConstraintException)
                } finally { sql.endTransaction() }
            }
            assertEquals(1, events.count { it["kind"] == "db_write_failed" })
            assertTrue(events.any { it["kind"] == "db_rolled_back" && it["writes"] == 0 && it["failedWrites"] == 1 })
            assertFalse(events.any { it["kind"] == "db_committed" })
            events.clear()
            val preferences = auditedPreferences(context, preferenceName)
            assertTrue(preferences.edit().putString("key", "private-preference").commit())
            preferences.edit().putBoolean("flag", true).apply()
            assertEquals(1, events.count { it["kind"] == "preference_committed" })
            assertEquals(1, events.count { it["kind"] == "preference_apply_accepted" })
            assertFalse(Gson().toJson(events).contains("private-preference"))
            events.clear()
            val file = java.io.File.createTempFile("audit-own-", ".bin", context.cacheDir)
            try {
                file.writeBytes(byteArrayOf(1, 2, 3)); val original = file.readBytes(); val modified = file.lastModified()
                try { StateWriteAudit.operation("test.file.rollback") {
                    replaceLocalProfileFile(file, byteArrayOf(4, 5, 6), { commit ->
                        db.withTransaction { commit(); db.appPreferences().delete("audit-private-key"); error("rollback") }
                    })
                } } catch (_: IllegalStateException) { }
                assertArrayEquals(original, file.readBytes()); assertEquals(modified, file.lastModified())
                assertNotNull(db.appPreferences().get("audit-private-key"))
                assertEquals(listOf("profile.atomic.replace", "profile.atomic.rollback"), events.filter { it["kind"] == "file_completed" }.map { it["reason"] })
                assertTrue(events.any { it["kind"] == "db_rolled_back" })
                assertFalse(events.any { it["kind"] == "db_committed" })
            } finally { assertTrue(file.delete()) }
        } finally {
            db.close(); context.deleteSharedPreferences(preferenceName); StateWriteAudit.install(prior)
        }
    }

    @Test fun enablesExplicitSessionBeforeColdProcess() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("kclashStateAudit") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = args.getString("kclashStateAuditToken").orEmpty()
        val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        assertEquals(com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        StateAuditPlatform.enable(context, token)
        assertTrue(StateWriteAudit.enabled)
    }

    @Test fun exportsExactSessionWithoutDeletingEvidence() {
        val args = InstrumentationRegistry.getArguments(); check(args.getString("kclashStateAudit") == "true")
        val ins = InstrumentationRegistry.getInstrumentation(); val token = args.getString("kclashStateAuditToken").orEmpty()
        val prior = StateWriteAudit.sink; StateWriteAudit.install(null)
        try {
            val allowIncomplete = args.getString("kclashAuditAllowIncomplete") == "true"
            StateAuditPlatform.records(ins.targetContext, token, allowIncomplete).forEach { file ->
                fun hash(target:java.io.File):String {
                    val digest=java.security.MessageDigest.getInstance("SHA-256")
                    target.inputStream().use { input ->val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)} }
                    return digest.digest().joinToString(""){"%02x".format(it)}
                }
                val temporary=if(file.name.endsWith(".gz"))null else java.io.File.createTempFile("audit-export-own-",".gz",ins.targetContext.cacheDir).also { target ->
                    java.util.zip.GZIPOutputStream(target.outputStream()).use { zip ->file.inputStream().use { it.copyTo(zip) } }
                }
                try {
                    val payload=temporary?:file;val sha=hash(payload);val rawSha=if(temporary==null)sha else hash(file)
                    val bytes=payload.length();val chunkBytes=45000;val count=((bytes+chunkBytes-1)/chunkBytes).toInt()
                    // 作者 long：日志可能超过应用堆，固定块分片；完整块大小为3的倍数，拼接Base64仍能按整份SHA校验。
                    payload.inputStream().use { input ->repeat(count){i ->
                        val needed=minOf(chunkBytes.toLong(),bytes-i.toLong()*chunkBytes).toInt();val buffer=ByteArray(needed);var read=0
                        while(read<needed){val n=input.read(buffer,read,needed-read);check(n>0);read+=n}
                        val part=android.util.Base64.encodeToString(buffer,android.util.Base64.NO_WRAP)
                        ins.sendStatus(2,android.os.Bundle().apply {putString("kclash_state_audit",org.json.JSONObject()
                            .put("name",file.name).put("sha256",sha).put("bytes",bytes).put("rawSha256",rawSha).put("rawBytes",file.length())
                            .put("transport",if(temporary==null)"identity" else "gzip").put("allowIncomplete",allowIncomplete)
                            .put("index",i).put("count",count).put("base64",part).toString())})
                    } }
                } finally {temporary?.let {check(it.isFile&&it.delete())}}
            }
            ins.sendStatus(2, android.os.Bundle().apply { putString("kclash_state_audit_status", Gson().toJson(prior?.status())) })
        } finally { StateWriteAudit.install(prior) }
    }

    @Test fun disablesOnlyExactStoppedSession() {
        val args = InstrumentationRegistry.getArguments(); check(args.getString("kclashStateAudit") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        assertEquals(com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        StateAuditPlatform.disable(context, args.getString("kclashStateAuditToken").orEmpty())
        assertFalse(StateWriteAudit.enabled)
    }
}
