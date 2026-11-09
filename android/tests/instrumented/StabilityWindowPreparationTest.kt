package com.lonnnnnng.flclash

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StabilityWindowPreparationTest {
    @Test fun importsOnlyAuthorizedLocalIntoEmptyMotionDatabase() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation(); val context = ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val path = InstrumentationRegistry.getArguments().getString("kclashStabilityInput").orEmpty()
        require(path.matches(Regex("/data/local/tmp/kclash-stability-[a-f0-9]{32}\\.json")))
        val raw = ins.uiAutomation.executeShellCommand("cat $path").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val input = JSONObject(raw.decodeToString()); val bytes = android.util.Base64.decode(input.getString("configBase64"), android.util.Base64.DEFAULT)
        check(stabilitySha(bytes) == input.getString("localSha256"))
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        entry.profileRepository().profilesLoaded.first { it }
        assertTrue(entry.database().profiles().getAll().isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        assertNull(stabilityInput(context).read())
        val profile = entry.profileRepository().addFileProfile("stability-authorized-local", bytes)
        input.remove("configBase64")
        stabilityInput(context).write(input.toString().toByteArray())
        assertEquals(1, entry.database().profiles().getAll().size)
        assertEquals(input.getString("localSha256"), stabilitySha(entry.profileRepository().profileFile(profile.id).readBytes()))
        ins.sendStatus(2, Bundle().apply { putString("kclash_stability_prepared", JSONObject().put("id", profile.id)
            .put("localSha256", input.getString("localSha256")).put("remoteProfiles", 0).toString()) })
    }
}
