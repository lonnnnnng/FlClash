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
    /** 作者 long：用户更新授权本地文件后只导入新副本，旧两份验收配置/正文和原加密输入均保留，禁止清空库或覆盖旧文件。 */
    @Test fun loadsUpdatedAuthorizedLocalWithoutReplacingExistingProfiles() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation(); val context = ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val path = InstrumentationRegistry.getArguments().getString("kclashStabilityInput").orEmpty()
        require(path.matches(Regex("/data/local/tmp/kclash-stability-[a-f0-9]{32}\\.json")))
        val raw = ins.uiAutomation.executeShellCommand("cat $path").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val input = JSONObject(raw.decodeToString())
        val bytes = android.util.Base64.decode(input.getString("configBase64"), android.util.Base64.DEFAULT)
        check(stabilitySha(bytes) == input.getString("localSha256"))
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository(); repo.profilesLoaded.first { it }
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val oldBytes = checkNotNull(stabilityInput(context).read()); val oldInput = JSONObject(oldBytes.decodeToString())
        check(!oldInput.has("endpointControl") && !oldInput.has("previousInputBase64"))
        check(oldInput.getString("localSha256") == input.getString("previousLocalSha256"))
        check(oldInput.getString("authorizedUrl") == input.getString("authorizedUrl"))
        val rows = entry.database().profiles().getAll().sortedBy { it.id }
        check(rows.size == 2 && rows.count { it.type == com.lonnnnnng.flclash.data.ProfileType.FILE } == 1)
        fun files() = rows.associate { row -> row.id to repo.profileFile(row.id).let { Triple(it.length(), it.lastModified(), stabilitySha(it.readBytes())) } }
        val originalFiles = files()
        val imported = repo.addFileProfile("authorized-new-local-${input.getString("token").take(8)}", bytes)
        check(entry.database().profiles().getAll().filter { it.id != imported.id }.sortedBy { it.id } == rows && files() == originalFiles)
        input.remove("configBase64")
        input.put("baselineProfileIds", org.json.JSONArray(rows.map { it.id })).put("localProfileId", imported.id)
            .put("previousInputBase64", android.util.Base64.encodeToString(oldBytes, android.util.Base64.NO_WRAP))
            .put("previousInputSha256", stabilitySha(oldBytes))
        stabilityInput(context).write(input.toString().toByteArray())
        check(stabilitySha(repo.profileFile(imported.id).readBytes()) == input.getString("localSha256"))
        ins.sendStatus(2, Bundle().apply { putString("kclash_stability_prepared", JSONObject().put("id", imported.id)
            .put("localSha256", input.getString("localSha256")).put("existingProfilesPreserved", rows.size).put("totalProfiles", rows.size + 1).toString()) })
    }

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
