package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.validation.IsolatedConfigValidator
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：跨两次instrumentation保留真实Room和Keystore草稿，在不同PID回读；不把runner退出称为保存窗口杀进程，也不碰正式库。 */
class ProfileDocumentLimitStoreInstrumentedTest {
    @Test fun encryptedUpperLimitDraftCrossesProcessAndCommitsOnlyAfterParentSave() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("kclashDocumentLimit") == "true")
        val phase = requireNotNull(args.getString("kclashDocumentLimitPhase"))
        require(phase in listOf("prepare", "verify"))
        val context = ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val root = File(context.cacheDir, "document-limit-acceptance")
        if (phase == "prepare") check(!root.exists() && root.mkdir()) else check(root.isDirectory)
        val scoped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.databaseBuilder(context, KClashDatabase::class.java, File(root, "limit.db").absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val validator = IsolatedConfigValidator(context)
        val repo = ProfileRepository(scoped, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("大正文本地编辑不得联网下载")
        }, validator, observationScope = scope)
        val runtime = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java).runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val store = AndroidProfileDraftStore(scoped)
        val vmStore = ViewModelStore()
        val vm = withContext(Dispatchers.Main) {
            ProfilesViewModel(repo, runtime, validator, overviewDispatcher = Dispatchers.Default, draftStore = store).also { vmStore.put("limit", it) }
        }
        var completed = false
        val since = SystemClock.elapsedRealtime()
        try {
            repo.profilesLoaded.first { it }
            vm.awaitEditorDraftRestoration()
            val id = 40040L
            if (phase == "prepare") {
                val original = eightMiBAuthorizedDocument()
                val profile = Profile.custom(id, "limit-local").copy(type = ProfileType.FILE)
                db.profiles().insert(profile.toEntity())
                repo.profileFile(id).apply { parentFile!!.mkdirs(); writeText(original); setLastModified(1_600_000_000_000) }
                withContext(Dispatchers.Main) { vm.openEditor(profile) }
                withTimeout(30_000) { vm.editorState.first { it.profile?.id == id && !it.isLoading } }
                withContext(Dispatchers.Main) {
                    vm.openProfileDocument(false)
                    vm.changeProfileDocument(original.dropLast(1) + "Y")
                }
                vm.persistGroupPickerParent()
                assertTrue(repo.profileFile(id).readText() == original)
                assertTrue(File(scoped.noBackupFilesDir, "profile-editor-draft.v1").length() > 8 * 1024 * 1024)
                File(root, "prepare-pid").writeText(Process.myPid().toString())
            } else {
                assertNotEquals(File(root, "prepare-pid").readText().toInt(), Process.myPid())
                val draft = vm.documentEditorState.value
                assertEquals(id, draft.ownerId)
                assertTrue(draft.content.endsWith("end-Y")); assertTrue(draft.original.endsWith("end-X"))
                assertEquals(MAX_PROFILE_FILE_BYTES, draft.content.toByteArray().size)
                assertTrue(repo.profileFile(id).readText().endsWith("end-X"))
                withContext(Dispatchers.Main) { vm.acceptProfileDocument() }
                withTimeout(60_000) { vm.documentEditorState.first { !it.saving } }
                assertNull("上限正文必须通过真实核心语义预检", vm.documentEditorState.value.error)
                assertNull(vm.documentEditorState.value.ownerId)
                assertTrue(repo.profileFile(id).readText().endsWith("end-X"))
                withContext(Dispatchers.Main) { vm.saveEditor(requireNotNull(vm.editorState.value.draftContent)) }
                withTimeout(60_000) { vm.editorState.first { it.profile == null || it.error != null } }
                assertNull(vm.editorState.value.error)
                assertNull(vm.editorState.value.profile)
                val saved = repo.readEditableProfileDocument(id)
                assertTrue(saved.content.endsWith("end-Y"))
                assertEquals(MAX_PROFILE_FILE_BYTES, repo.profileFile(id).length().toInt())
                withTimeout(15_000) { while (store.read() != null) delay(100) }
            }
            ins.sendStatus(2, Bundle().apply {
                putString("kclash_limit_phase", phase)
                putInt("pid", Process.myPid())
                putLong("elapsed_ms", SystemClock.elapsedRealtime() - since)
                putLong("java_used_bytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
            })
            completed = true
        } finally {
            withContext(Dispatchers.Main) { vmStore.clear() }
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
            if (phase == "verify" && completed) {
                check(root.parentFile == context.cacheDir && root.name == "document-limit-acceptance")
                check(root.deleteRecursively())
            }
        }
    }
}
