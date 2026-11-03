package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalRulesControllerTest {
    @Test fun globalRulesNeedNoProfileAndRejectConcurrentStaleWrites() = runTest {
        val store = Store().apply { currentProfileId.value = null; profiles.value = emptyList() }
        val runtime = Runtime()
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); runCurrent()
        assertEquals(next, store.global); assertEquals(0, runtime.applies)
        store.global = listOf(ExtensionRule("external", "GEOIP,CN,DIRECT"))
        model.save(emptyList()); runCurrent()
        assertEquals("external", store.global.single().id)
        assertNotNull(model.state.value.error)
        assertEquals(next, model.state.value.rules)
    }

    @Test fun savedRulesSurviveApplyFailureAndRetryDoesNotWriteAgain() = runTest {
        val store = Store(); val runtime = Runtime().apply { fail = true }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,MATCH"))
        model.save(next); model.save(emptyList()); runCurrent()
        assertEquals(next, store.global); assertEquals(next, model.state.value.rules)
        assertNotNull(model.state.value.error)
        runtime.fail = false; model.retryApply(); runCurrent()
        assertNull(model.state.value.error); assertEquals(2, runtime.applies)
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
    }

    @Test fun profileSwitchDuringSaveDoesNotApplyToNewSelection() = runTest {
        val store = Store(); val runtime = Runtime { store.currentProfileId.value }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.saveGate = gate
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); runCurrent()
        store.currentProfileId.value = 2
        gate.complete(Unit); runCurrent()
        assertEquals(next, store.global); assertEquals(0, runtime.applies)
    }

    private class Runtime(private val selected: () -> Long? = { 1 }) : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
        var applies = 0; var fail = false
        override suspend fun applyCurrentProfile(id: Long) { applies++; if (fail) error("apply failed") }
        override suspend fun <T> withExtensionWrite(targetId: Long?, write: suspend () -> T): T {
            val result = write()
            if (targetId != null && targetId == selected()) {
                try { applyCurrentProfile(targetId) } catch (_: Exception) { throw ProfileApplyAfterSaveException(targetId) }
            }
            return result
        }
    }
    private class Store : ProfileStore {
        override val profiles = MutableStateFlow(listOf(Profile.remote(1, "https://example.test/sub", "test")))
        override val currentProfileId = MutableStateFlow<Long?>(1)
        var global = emptyList<ExtensionRule>()
        var readGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun readLocalProfileContent(id: Long): String { readGate?.await(); return "proxy-groups: [{name: Main, type: select}]\nrules: ['MATCH,Main']\n" }
        override suspend fun globalExtensionRules() = global
        override suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) { saveGate?.await(); global = rules }
        override suspend fun saveExtension(id: Long, mode: String, scriptId: Long?, matchTarget: String?, extension: ProfileExtensionOverrides): Profile {
            val profile = profiles.value.single { it.id == id }
            val updated = profile.copy(extendType = mode, scriptId = scriptId, matchTarget = matchTarget,
                overridesJson = ProfileProviderOverrides.fromJsonString(profile.overridesJson).copy(extension = extension.copy(configured = true)).toJsonString())
            profiles.value = profiles.value.map { if (it.id == id) updated else it }
            return updated
        }
        override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("unused")
        override suspend fun selectProfile(id: Long?) { error("不应切换配置") }
        override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
        override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
        override suspend fun addCustomProfile(label: String): Profile = error("unused")
        override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("不应重写正文")
        override suspend fun updateRemoteProfile(id: Long): Profile = error("不应下载订阅")
        override suspend fun deleteProfile(id: Long) { error("unused") }
        override suspend fun reorder(ids: List<Long>) { error("unused") }
    }
}
