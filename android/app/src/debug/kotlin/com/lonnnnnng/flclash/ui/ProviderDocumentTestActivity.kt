package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.flclash.core.CoreExternalProvider
import com.lonnnnnng.flclash.core.CoreProviderDocument
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** 作者 long：真实Activity保留正式编辑ViewModel；固定运行时只存内存，避免生命周期验收写入用户Provider缓存。 */
class ProviderDocumentTestActivity : ComponentActivity() {
    lateinit var model: ProfileStatisticsViewModel
        private set
    lateinit var fixture: ProviderDocumentFixture
        private set
    var savedBytes: ByteArray = byteArrayOf()
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fixture = ViewModelProvider(this)[ProviderDocumentFixture::class.java]
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileStatisticsViewModel(fixture) as T
        })[ProfileStatisticsViewModel::class.java]
        model.show(1)
        setContent {
            val providers by model.providers.collectAsState()
            KClashTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp)) {
                    ProfileProvidersPanel(providers, model::retryProviders, model::updateProvider,
                        onPreview = { model.openProvider(it) }, onEdit = { model.openProvider(it, edit = true) })
                }
                ProfileProviderDocumentHost(model)
            } }
        }
    }

    override fun onStart() { super.onStart(); model.setForeground(true) }
    override fun onStop() { model.setForeground(false); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val parcel = Parcel.obtain()
        try { parcel.writeBundle(outState); savedBytes = parcel.marshall() } finally { parcel.recycle() }
    }
}

class ProviderDocumentFixture : ViewModel(), ProfileStatisticsRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    override val profileStatisticsRevision = MutableStateFlow(0L)
    var content = "example.com"
    var failSave = false
    var saveGate: CompletableDeferred<Unit>? = null
    var readGate: CompletableDeferred<Unit>? = null
    var saves = 0
    var polls = 0
    override suspend fun profileStatistics(profileId: Long) = ProfileStatistics(profileId, 0, 0, 1, 1)
    override suspend fun profileProviders(profileId: Long): List<CoreExternalProvider> {
        polls++
        return listOf(CoreExternalProvider("测试规则", "Rule", "File", 1, null))
    }
    override suspend fun readProfileProvider(profileId: Long, name: String): ProfileProviderDocument {
        readGate?.await()
        return ProfileProviderDocument(profileId, CoreProviderDocument(name, "text", content, "fixture-digest"), recoveryKey = "memory-only")
    }
    override suspend fun saveProfileProvider(document: ProfileProviderDocument, content: String) {
        saves++
        saveGate?.await()
        if (failSave) throw ProviderOperationException("测试保存失败，输入已保留")
        this.content = content
    }
}
