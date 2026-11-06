package com.lonnnnnng.flclash.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/** 作者 long：真实Activity重建保留正式ViewModel，但仓库完全隔离；不把节点草稿写进Bundle或正式配置。 */
class LocalProxyDraftTestActivity : ComponentActivity() {
    lateinit var model: LocalProxiesViewModel
        private set
    lateinit var fixture: LocalProxyDraftFixture
        private set
    var savedBytes: ByteArray = byteArrayOf()
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fixture = ViewModelProvider(this)[LocalProxyDraftFixture::class.java]
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalProxiesViewModel(fixture.operations, NoopProfileDraftStore) as T
        })[LocalProxiesViewModel::class.java]
        setContent { val state by model.state.collectAsState(); KClashTheme {
            LocalProxiesPage(state, model::close, model::edit, model::save, model::dismissEditor, model::links,
                model::applyLinks, model::cancelLinks, model::reorder, model::askDelete, model::delete, model::cancelDelete,
                model::confirmReplacement, model::updateEditor, { model.updateUiDraft(state.session, it) },
                model::updateLinksUiDraft, model::reportDraftFailure, model::updateEditorUiDraft)
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val parcel = Parcel.obtain()
        try { parcel.writeBundle(outState); savedBytes = parcel.marshall() } finally { parcel.recycle() }
    }
}

class LocalProxyDraftFixture(application: Application) : AndroidViewModel(application) {
    internal val database = Room.inMemoryDatabaseBuilder(application, KClashDatabase::class.java).build()
    private val directory = File(application.cacheDir, "local-draft-${System.nanoTime()}").apply { mkdirs() }
    val repository = run {
        // 作者 long：隔离上下文仅包装Application，不把Activity引用留在跨重建的ViewModel中。
        val isolated = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("本测试禁止下载")
        })
    }
    val runtime = EntryPointAccessors.fromApplication(application, ConnectionAcceptanceEntryPoint::class.java).runtimeManager()
    internal var beforeSave: suspend () -> Unit = {}
    internal var beforeValidate: suspend () -> Unit = {}
    internal var nodeDefinition: com.google.gson.JsonObject? = null
    internal var referenceUsers: List<String>? = null
    internal var saveCalls = 0
    private val repositoryOperations = RepositoryLocalProxyEditorOperations(repository, CoreBridge(), runtime)
    // 作者 long：仅Debug隔离宿主提供可暂停边界；实际校验仍走Bridge、写入仍走隔离Room，不改正式目录或运行配置。
    internal val operations = object : LocalProxyEditorOperations by repositoryOperations {
        override suspend fun users(names: Set<String>) = referenceUsers ?: repositoryOperations.users(names)
        override suspend fun save(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>?) {
            saveCalls++; beforeSave(); repositoryOperations.save(expected, next, rename)
        }
        override suspend fun validate(nodes: List<com.google.gson.JsonObject>): List<String> {
            beforeValidate(); return repositoryOperations.validate(nodes)
        }
        override suspend fun definition(profileId: Long, name: String) = nodeDefinition?.deepCopy()
            ?: repositoryOperations.definition(profileId, name)
    }
    override fun onCleared() {
        database.close()
        // 作者 long：原配置的隔离副本也含凭据；仅回收本宿主独占缓存目录，Activity重建不会触发此清理。
        check(directory.canonicalFile.parentFile == getApplication<Application>().cacheDir.canonicalFile && directory.name.startsWith("local-draft-"))
        directory.deleteRecursively()
        check(!directory.exists()) { "本地代理验收缓存未清理" }
    }
}
