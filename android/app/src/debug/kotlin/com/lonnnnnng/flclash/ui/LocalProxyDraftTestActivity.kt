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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalProxiesViewModel(fixture.repository, CoreBridge(), fixture.runtime) as T
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
    private val database = Room.inMemoryDatabaseBuilder(application, KClashDatabase::class.java).build()
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
    override fun onCleared() { database.close() }
}
