package com.lonnnnnng.flclash

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** 作者 long：重装后只经正式仓库恢复两份获准配置，避免验收入口改写其他 Room 行或启动代理服务。 */
@RunWith(AndroidJUnit4::class)
class ProfilePreparationInstrumentedTest {
    @Test
    fun importsAuthorizedRemoteAndWorkspaceLocalProfilesWithoutStartingRuntime() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashProfilePreparation") == "true")
        val inputPath = args.getString("kclashPreparationInput").orEmpty()
        require(Regex("/data/local/tmp/kclash-profile-prepare-[a-f0-9]{32}\\.json").matches(inputPath)) { "临时输入路径无效" }
        val payload = JSONObject(shellText("cat $inputPath"))
        val url = payload.getString("authorizedUrl")
        require(url.startsWith("https://")) { "授权来源格式无效" }
        val bytes = Base64.decode(payload.getString("configBase64"), Base64.DEFAULT)
        val localSha = sha(bytes)
        assertEquals("本地配置必须与工作区 1.yaml 一致", payload.getString("configSha256"), localSha)

        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        // 作者 long：准备入口只等待仓库首读，不主动刷新/校正当前选择，避免在保护快照之前额外写入偏好。
        repository.profilesLoaded.first { it }
        val beforeRows = entry.database().profiles().getAll()
        val beforeFiles = beforeRows.associate { row -> row.id to repository.profileFile(row.id).let { file ->
            file.takeIf { it.isFile }?.let { sha(it.readBytes()) }
        } }
        val beforeSelected = entry.database().appPreferences().get("current_profile_id")
        val gson = Gson()
        val beforeRowDigests = beforeRows.associate { row -> row.id to sha(gson.toJson(row).toByteArray()) }

        val localExisting = beforeRows.firstOrNull { row ->
            row.type == ProfileType.FILE && repository.profileFile(row.id).isFile &&
                sha(repository.profileFile(row.id).readBytes()) == localSha
        }
        val localId = localExisting?.id ?: repository.addFileProfile("1.yaml", bytes).id

        val remoteExisting = entry.database().profiles().getAll().firstOrNull { it.url == url }
        // 作者 long：下载失败只暴露异常类别，JUnit堆栈不能展开订阅地址；已有来源只复用，不静默更新用户正文。
        val remoteId = remoteExisting?.id ?: try {
            repository.addRemoteProfile(url).id
        } catch (failure: Exception) {
            throw AssertionError("远程配置准备失败：${failure.javaClass.simpleName}")
        }
        val afterRows = entry.database().profiles().getAll()
        assertEquals(ProfileType.FILE, afterRows.first { it.id == localId }.type)
        assertEquals(ProfileType.URL, afterRows.first { it.id == remoteId }.type)
        assertTrue("远程配置必须对应唯一授权来源", afterRows.first { it.id == remoteId }.url == url)
        assertEquals(localSha, sha(repository.profileFile(localId).readBytes()))
        assertTrue("远程配置正文必须成功保存", repository.profileFile(remoteId).isFile && repository.profileFile(remoteId).length() > 0L)
        beforeRows.forEach { row ->
            val after = afterRows.firstOrNull { it.id == row.id }
            assertNotNull("导入不得删除既有配置", after)
            assertEquals("导入不得改写既有配置元数据", beforeRowDigests.getValue(row.id), sha(gson.toJson(after).toByteArray()))
            assertEquals("导入不得改写既有配置正文", beforeFiles.getValue(row.id),
                repository.profileFile(row.id).takeIf { it.isFile }?.let { sha(it.readBytes()) })
        }
        if (beforeSelected != null) assertEquals("导入不得切换既有当前配置", beforeSelected, entry.database().appPreferences().get("current_profile_id"))
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)

        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("kclash_profile_prepare_result", "local=${if (localExisting == null) "added" else "reused"};remote=${if (remoteExisting == null) "added" else "reused"};count=${afterRows.size};localSha256=$localSha")
        })
    }

    @Test
    fun reportsProfilePreservationSnapshotWithoutWritingUserData() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kclashProfileInspection") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val rows = entry.database().profiles().getAll().sortedBy { it.id }
        val files = org.json.JSONArray()
        rows.forEach { row ->
            val file = entry.profileRepository().profileFile(row.id)
            assertTrue("正式配置正文必须存在", file.isFile && file.length() > 0L)
            files.put(JSONObject().put("id", row.id).put("type", row.type.name).put("bytes", file.length())
                .put("mtime", file.lastModified()).put("sha256", sha(file.readBytes())))
        }
        val preferences = entry.database().appPreferences().getByPrefix("").sortedBy { it.key }.associate { it.key to it.value }
        val snapshot = JSONObject().put("count", rows.size).put("files", files)
            .put("rowsSha256", sha(Gson().toJson(rows).toByteArray()))
            .put("preferencesSha256", sha(Gson().toJson(preferences).toByteArray()))
            // 作者 long：逐键摘要用于定位后台缓存写入，不把订阅、密钥或网络缓存原值写入验收日志。
            .put("preferenceDigests", JSONObject(preferences.mapKeys { sha(it.key.toByteArray()) }.mapValues { sha(it.value.toByteArray()) }))
            .put("selected", preferences["current_profile_id"] ?: JSONObject.NULL)
            .put("runtime", entry.runtimeManager().state.value.name)
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("kclash_profile_snapshot", snapshot.toString()) })
    }

    private fun shellText(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
