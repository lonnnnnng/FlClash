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
class ProfileExtensionControllerTest {
    @Test fun runningSaveIsIndependentFromApplyAndCloseUsesLatestSavedInputs() = runTest {
        val store = Store()
        val runtime = Runtime()
        val model = ProfileExtensionController(store, runtime, backgroundScope, StandardTestDispatcher(testScheduler))
        model.open(1); runCurrent()
        model.mode("script"); model.script(9); model.target("Main"); runCurrent()
        assertEquals(0, runtime.applies)
        assertEquals("script", model.state.value.mode)
        assertEquals(9L, store.profiles.value.single().scriptId)
        assertEquals("Main", store.profiles.value.single().matchTarget)
        assertTrue(ProfileProviderOverrides.fromJsonString(store.profiles.value.single().overridesJson).extension.configured)
        var closed = false
        model.close { closed = true }; runCurrent()
        assertTrue(model.state.value.applying); assertFalse(closed)
        advanceTimeBy(600); runCurrent()
        assertTrue(closed); assertEquals(1, runtime.applies)
        assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
    }

    @Test fun noncurrentEditsNeverSelectOrApplyAndProfileSwitchBeforeExitIsRespected() = runTest {
        val store = Store(); val runtime = Runtime()
        val model = ProfileExtensionController(store, runtime, backgroundScope, StandardTestDispatcher(testScheduler))
        model.open(1); runCurrent()
        model.rules(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
        store.currentProfileId.value = 2
        var closed = false
        model.close { closed = true }; runCurrent(); advanceTimeBy(600); runCurrent()
        assertTrue(closed); assertEquals(0, runtime.applies); assertEquals(2L, store.currentProfileId.value)
    }

    @Test fun applyFailureKeepsSavedRulesAndPageForRetry() = runTest {
        val store = Store(); val runtime = Runtime().apply { fail = true }
        val model = ProfileExtensionController(store, runtime, backgroundScope, StandardTestDispatcher(testScheduler))
        model.open(1); runCurrent()
        val rules = listOf(ExtensionRule("r", "DOMAIN,example.test,MATCH"))
        model.rules(rules); runCurrent()
        var closed = false
        model.close { closed = true }; runCurrent(); advanceTimeBy(600); runCurrent()
        assertFalse(closed); assertNotNull(model.state.value.error)
        assertEquals(rules, model.state.value.settings.rules)
        assertEquals(rules, ProfileProviderOverrides.fromJsonString(store.profiles.value.single().overridesJson).extension.rules)
        runtime.fail = false
        model.close { closed = true }; runCurrent(); advanceTimeBy(600); runCurrent()
        assertTrue(closed); assertEquals(2, runtime.applies)
    }

    @Test fun globalSaveAndDisabledIdsRemainDistinctAndStopStateDoesNotStartService() = runTest {
        val store = Store(); val runtime = Runtime().apply { state.value = CoreRuntimeStatus.STOPPED }
        val model = ProfileExtensionController(store, runtime, backgroundScope, StandardTestDispatcher(testScheduler))
        model.open(1); runCurrent()
        val rules = listOf(ExtensionRule("global", "GEOIP,CN,DIRECT,no-resolve"))
        model.globalRules(rules); model.disabled(setOf("global")); runCurrent()
        assertEquals(rules, model.state.value.globalRules)
        assertEquals(setOf("global"), model.state.value.settings.disabledGlobalRuleIds)
        model.apply(); runCurrent(); advanceTimeBy(600); runCurrent()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertTrue(model.state.value.applied)
    }

    @Test fun lateOpenCannotReplaceNewTargetAndSavedModeKeepsLegacyScriptCompatibility() = runTest {
        val store = Store()
        store.profiles.value = listOf(store.profiles.value.single().copy(scriptId = 9))
        val runtime = Runtime()
        val model = ProfileExtensionController(store, runtime, backgroundScope, StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>(); store.readGate = gate
        model.open(1); runCurrent()
        store.profiles.value = store.profiles.value + Profile.remote(2, "https://example.test/second", "second")
        model.open(2); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(2L, model.state.value.profile?.id)
        store.readGate = null
        model.open(1); runCurrent()
        assertEquals("script", model.state.value.mode)
        model.mode("standard"); runCurrent()
        assertEquals(9L, model.state.value.profile?.scriptId)
        assertEquals("standard", model.state.value.mode)
    }

    private class Runtime : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.RUNNING)
        var applies = 0; var fail = false; var applied = false
        override suspend fun profileInputsApplied(id: Long) = applied
        override suspend fun applyCurrentProfile(id: Long) { applies++; if (fail) error("配置切换失败，已恢复原配置"); applied = true }
    }
    private class Store : ProfileStore {
        override val profiles = MutableStateFlow(listOf(Profile.remote(1, "https://example.test/sub", "test")))
        override val currentProfileId = MutableStateFlow<Long?>(1)
        var global = emptyList<ExtensionRule>()
        var readGate: CompletableDeferred<Unit>? = null
        override suspend fun readLocalProfileContent(id: Long): String { readGate?.await(); return "proxy-groups: [{name: Main, type: select}]\nrules: ['MATCH,Main']\n" }
        override suspend fun globalExtensionRules() = global
        override suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) { global = rules }
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
