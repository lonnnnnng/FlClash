package com.lonnnnnng.flclash.data

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ScriptClient
import com.lonnnnnng.flclash.data.ScriptDownload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：脚本正文必须和 Room 元数据一起验证，确保编辑、排序和删除不会留下孤立文件。
 */
@RunWith(AndroidJUnit4::class)
class ScriptsRepositoryInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryPersistsScriptFilesAndOrder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = ScriptRepository(context, database)

        val first = runBlocking { repository.create("第一个脚本", "old") }
        val second = runBlocking { repository.create("第二个脚本", "two") }
        composeRule.waitUntil(5_000) { repository.scripts.value.size == 2 }
        assertTrue(repository.scriptFile(first.id).isFile)
        assertEquals("old", runBlocking { repository.readContent(first.id) })

        runBlocking { repository.update(first.id, "更新后的脚本", "new") }
        assertEquals("new", runBlocking { repository.readContent(first.id) })
        assertEquals(
            "更新后的脚本",
            runBlocking { database.scripts().getById(first.id)?.label },
        )

        runBlocking { repository.reorder(listOf(second.id, first.id)) }
        composeRule.waitUntil(5_000) {
            repository.scripts.value.map { it.id } == listOf(second.id, first.id)
        }
        assertEquals(
            listOf(second.id, first.id),
            runBlocking { database.scripts().getAll().map { it.id } },
        )

        val boundProfile = Profile.custom(801L, "已绑定配置").copy(scriptId = first.id)
        runBlocking { database.profiles().insert(boundProfile.toEntity()) }

        runBlocking { repository.delete(first.id) }
        composeRule.waitUntil(5_000) { repository.scripts.value.map { it.id } == listOf(second.id) }
        assertFalse(repository.scriptFile(first.id).exists())
        assertTrue(repository.scriptFile(second.id).isFile)
        assertEquals(null, runBlocking { database.profiles().getById(boundProfile.id)?.scriptId })
        database.close()
    }

    @Test
    fun repositoryImportsAndSyncsRemoteScriptAtomically() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
        val client = FakeScriptClient()
        val repository = ScriptRepository(context, database, client)

        val script = runBlocking {
            repository.createRemote("远程脚本", "http://127.0.0.1/script.js")
        }
        assertEquals("const main = (config) => config;", runBlocking { repository.readContent(script.id) })
        assertEquals(
            "http://127.0.0.1/script.js",
            runBlocking { database.scripts().getById(script.id)?.url },
        )

        val synced = runBlocking { repository.sync(script.id) }
        // 作者 long：先等 Room 观察流处理完同步写入，再关闭内存库，避免 collector 仍在读连接池时被测试清理关闭。
        composeRule.waitUntil(5_000) {
            repository.scripts.value.singleOrNull()?.lastUpdateAt == synced.lastUpdateAt
        }
        assertEquals("const main = (config) => ({...config, synced: true});", runBlocking {
            repository.readContent(script.id)
        })
        database.close()
    }
}

private class FakeScriptClient : ScriptClient {
    private var count = 0

    override suspend fun download(url: String): ScriptDownload {
        count += 1
        val content = if (count == 1) {
            "const main = (config) => config;"
        } else {
            "const main = (config) => ({...config, synced: true});"
        }
        return ScriptDownload(content.toByteArray(), "远程脚本")
    }
}
