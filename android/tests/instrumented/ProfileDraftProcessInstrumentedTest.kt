package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：两个方法必须分次运行，中间由外部驱动杀死后台应用并记录PID；Activity重建不能代替进程恢复。 */
class ProfileDraftProcessInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "profile-closure-process.json")
    private fun gate() {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashProfileClosureAcceptance"))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("kclash_profile_process_pid", android.os.Process.myPid().toString())
        })
    }

    @Test fun preparePendingFileAndBodyDraftWithoutCommitting(): Unit = runBlocking {
        gate()
        val e = entry(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("不能覆盖未收尾的进程验收", marker().exists())
        assertNull(e.profileDraftStore().read())
        val before = e.database().profiles().getAll()
        val id = System.nanoTime().and(Long.MAX_VALUE)
        val bytes = readAuthorizedProxyProfile()
        val record = JsonObject().apply {
            addProperty("id", id); addProperty("selected", repo.currentProfileId.value)
            addProperty("preparedPid", android.os.Process.myPid())
            addProperty("rows", digest(Gson().toJson(before).toByteArray()))
            addProperty("files", before.joinToString("|") { row -> repo.profileFile(row.id).let { "${row.id}:${it.length()}:${it.lastModified()}:${digest(it.readBytes())}" } })
        }
        e.database().profiles().insert(Profile.custom(id, "process-original").copy(type = ProfileType.FILE, orderIndex = before.size).toEntity())
        repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        marker().writeText(record.toString())
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(Profile.custom(id, "process-original").copy(type = ProfileType.FILE)) }
        compose.waitUntil(15_000) { model.editorState.value.document != null }
        compose.onNodeWithTag("file_profile_editor_label").performTextReplacement("process-pending-name")
        compose.onNodeWithTag("file_profile_editor_age_key").performTextReplacement("process-pending-key")
        compose.runOnIdle { model.openProfileDocument(false) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_document_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_document_editor").assertExists()
        withTimeout(15_000) {
            while (e.profileDraftStore().read()?.toString(Charsets.UTF_8)?.let { it.contains("process-pending-name") && it.contains("process-pending-key") && it.contains("ownerId") } != true) delay(100)
        }
        assertEquals("process-original", e.database().profiles().getById(id)?.label)
        assertArrayEquals(bytes, repo.profileFile(id).readBytes())
        val encrypted = File(compose.activity.noBackupFilesDir, "profile-editor-draft.v1").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("process-pending-name")); assertFalse(encrypted.contains("process-pending-key"))
    }

    @Test fun verifyColdProcessRestoresDraftAndDiscardLeavesOriginalsUntouched(): Unit = runBlocking {
        gate()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        val id = record.get("id").asLong
        assertNotEquals("必须由新应用进程恢复草稿", record.get("preparedPid").asInt, android.os.Process.myPid())
        val e = entry(); val repo = e.profileRepository()
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
        try {
            compose.waitUntil(20_000) { model.documentEditorState.value.ownerId == id }
            compose.onNodeWithTag("profile_document_editor").assertExists()
            assertEquals("process-pending-name", model.editorState.value.draftLabel)
            assertEquals("process-pending-key", model.editorState.value.draftAgeKey)
            assertArrayEquals(readAuthorizedProxyProfile(), model.documentEditorState.value.content.toByteArray())
            assertEquals("process-original", e.database().profiles().getById(id)?.label)
            compose.runOnIdle { model.closeProfileDocument() }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("file_profile_editor_label").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("file_profile_editor_label").assertTextContains("process-pending-name")
        } finally {
            compose.runOnIdle { model.closeEditor() }
            withTimeout(15_000) { while (e.profileDraftStore().read() != null) delay(100) }
            repo.deleteProfile(id)
            val after = e.database().profiles().getAll()
            assertEquals(record.get("rows").asString, digest(Gson().toJson(after).toByteArray()))
            assertEquals(record.get("files").asString, after.joinToString("|") { row -> repo.profileFile(row.id).let { "${row.id}:${it.length()}:${it.lastModified()}:${digest(it.readBytes())}" } })
            assertEquals(record.get("selected").asLong, repo.currentProfileId.value)
            assertTrue(marker().delete())
        }
    }

    private fun digest(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
