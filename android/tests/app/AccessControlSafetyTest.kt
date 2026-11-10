package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModelStore
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.data.ClipboardGateway
import com.lonnnnnng.flclash.service.AccessControlSettingsProvider
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.models.AccessControlProps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：直接驱动真实VM的挂起边界，证明刷新、扫描和写入不会用迟到结果覆盖名单。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessControlSafetyTest {
    private val owners = mutableListOf<ViewModelStore>()
    @After fun cleanup() { owners.forEach(ViewModelStore::clear); Dispatchers.resetMain() }
    private val apps = listOf(
        InstalledAppInfo("com.tencent.mm", "Domestic"), InstalledAppInfo("org.example.browser", "Browser"),
        InstalledAppInfo("org.example.system", "Service", isSystem = true),
        InstalledAppInfo("org.example.offline", "Offline", hasInternetPermission = false),
    )
    private val original = AccessControlDefaults.value.copy(enable = true,
        acceptList = listOf("com.tencent.mm", "org.example.system", "org.example.missing"),
        rejectList = listOf("org.example.browser", "org.example.offline", "org.example.missing"))
    private fun model(store: Store = Store(original), runtime: Runtime = Runtime(), clipboard: Clipboard = Clipboard(),
        loader: suspend () -> InstalledAppsSnapshot = { InstalledAppsSnapshot(apps) },
        matcher: suspend (List<InstalledAppInfo>) -> Set<String> = { setOf("com.tencent.mm") }): AccessControlViewModel {
        val model = AccessControlViewModel(store, runtime, clipboard, loader, matcher)
        owners += ViewModelStore().also { it.put("access", model) }
        return model
    }

    @Test fun smartSelectionKeepsHiddenAndMissingListsRegardlessOfSearch() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val model = model(); model.setSearchQuery("unmatched")
        assertTrue(model.state.value.visibleApps.isEmpty())
        model.smartSelectApps()
        assertEquals(listOf("org.example.browser", "org.example.missing", "org.example.system"), model.state.value.settings.acceptList)
        assertEquals(listOf("com.tencent.mm", "org.example.missing", "org.example.offline"), model.state.value.settings.rejectList)
        assertEquals(2, model.state.value.selectedCount)
        assertEquals(SmartSelectionResult(original.acceptList.sorted(), original.rejectList.sorted()), buildSmartSelection(emptyList(), emptySet(), original))
    }

    @Test fun invalidClipboardNeverReplacesEitherListAndMixedInputKeepsOnlyValidNames() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clipboard = Clipboard(); val model = model(clipboard = clipboard)
        for (value in listOf("", "\n  \r\n", "android\ninvalid\n1abc.def\ncom..a\ncom._abc\ncom.节点")) {
            clipboard.text = value; model.importFromClipboard()
            assertEquals(normalizeAccessControlSettings(original), model.state.value.settings)
            assertTrue(model.state.value.error.orEmpty().contains("没有有效包名"))
        }
        clipboard.text = " org.example.browser\r\nBAD\norg.example.browser\n com.test_1.app \n"
        model.importFromClipboard()
        assertEquals(listOf("org.example.browser", "com.test_1.app"), model.state.value.settings.rejectList)
        assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList)
        assertNull(model.state.value.error)
    }

    @Test fun refreshFailureAndPermissionDenialKeepDraftAndLastCompleteCatalog() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var mode = 0
        val model = model(loader = { when (mode) { 1 -> error("query failed"); 2 -> InstalledAppsSnapshot(emptyList(), true); else -> InstalledAppsSnapshot(apps) } })
        model.togglePackage("org.example.browser"); val draft = model.state.value.settings
        mode = 1; model.refreshApps()
        assertEquals(draft, model.state.value.settings); assertEquals(apps, model.state.value.apps); assertNotNull(model.state.value.appsError)
        mode = 2; model.refreshApps(); assertTrue(model.state.value.appsPermissionRequired)
        assertEquals(draft, model.state.value.settings); assertEquals(apps, model.state.value.apps)
        mode = 0; model.refreshApps(); assertNull(model.state.value.appsError); assertFalse(model.state.value.appsPermissionRequired)
        assertEquals(draft, model.state.value.settings); assertTrue(model.state.value.hasUnsavedChanges)
    }

    @Test fun discardAndRuntimeTransitionsInvalidateSuspendedSmartSelection() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var gate = CompletableDeferred<Set<String>>(); val runtime = Runtime()
        val model = model(runtime = runtime, matcher = { withContext(NonCancellable) { gate.await() } })
        model.smartSelectApps(); assertTrue(model.state.value.isSmartSelecting)
        model.discardUnsavedChanges(); val oldGate = gate; gate = CompletableDeferred(); oldGate.complete(setOf("com.tencent.mm"))
        assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList)
        model.smartSelectApps(); runtime.state.value = CoreRuntimeStatus.STARTING; runtime.state.value = CoreRuntimeStatus.STOPPED
        gate.complete(setOf("com.tencent.mm"))
        assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList); assertFalse(model.state.value.isSmartSelecting)
    }

    @Test fun repeatedSaveClaimsOnceAndRuntimeChangeWhileWaitingRejectsWrite() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = Store(original); val runtime = Runtime(); runtime.gate = CompletableDeferred()
        val model = model(store, runtime); model.setEnabled(false); model.save(); model.save()
        assertEquals(1, runtime.claims); assertTrue(model.state.value.isSaving)
        runtime.state.value = CoreRuntimeStatus.STARTING; runtime.gate!!.complete(Unit)
        assertEquals(0, store.writes); assertFalse(model.state.value.isSaving); assertFalse(model.state.value.settings.enable)
        runtime.state.value = CoreRuntimeStatus.STOPPED; runtime.gate = null; model.save()
        assertEquals(1, store.writes); assertFalse(model.state.value.hasUnsavedChanges)
    }

    @Test fun conflatedRuntimeWindowStillRejectsOldScanByRevision() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Set<String>>(); val runtime = Runtime()
        val model = model(runtime = runtime, matcher = { gate.await() }); runCurrent()
        model.smartSelectApps(); runCurrent()
        runtime.state.value = CoreRuntimeStatus.STARTING; runtime.editRevision++
        runtime.state.value = CoreRuntimeStatus.STOPPED; runtime.editRevision++
        gate.complete(setOf("com.tencent.mm")); runCurrent()
        assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList)
        assertFalse(model.state.value.isSmartSelecting); assertNotNull(model.state.value.error)
    }

    @Test fun cancelledSaveWaitingForCoreLockDoesNotWriteOrCallSuccess() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = Store(original); val runtime = Runtime(); runtime.gate = CompletableDeferred()
        val model = model(store, runtime); model.setEnabled(false)
        var successes = 0; model.save { successes++ }
        owners.last().clear(); runtime.gate!!.complete(Unit); runCurrent()
        assertEquals(0, store.writes); assertEquals(0, successes); assertNull(model.state.value.error)
    }

    @Test fun initialSettingsFailureCannotSaveDefaultsAndRetryRecovers() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = Store(original); store.failRead = true
        val model = model(store); model.setEnabled(false); model.save()
        assertEquals(0, store.writes); assertFalse(model.state.value.settingsReady)
        store.failRead = false; model.retryLoad()
        assertTrue(model.state.value.settingsReady); assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList)
        store.failRead = true; model.reload(); model.save()
        assertFalse(model.state.value.settingsReady); assertEquals(0, store.writes)
        assertEquals(original.acceptList.sorted(), model.state.value.settings.acceptList)
    }

    private class Store(var value: AccessControlProps) : AccessControlSettingsProvider {
        var writes = 0; var failRead = false
        override suspend fun current(): AccessControlProps { check(!failRead); return value }
        override suspend fun save(value: AccessControlProps) { writes++; this.value = value }
    }
    private class Runtime : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
        override var editRevision = 0L
        var gate: CompletableDeferred<Unit>? = null; var claims = 0
        override suspend fun <T> withProfileWrite(applyCurrentId: Long?, write: suspend () -> T): T { claims++; gate?.await(); return write() }
    }
    private class Clipboard : ClipboardGateway {
        var text: String? = null
        override fun readText() = text
        override fun writeText(text: String) { this.text = text }
    }
}
