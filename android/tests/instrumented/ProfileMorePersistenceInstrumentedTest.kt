package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import com.lonnnnnng.flclash.service.ProfileSniffProtocolOverrides
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：原配置只在本测试独占目录使用；验证逐项元数据保存不能改正文，也不能吞掉最新覆盖。 */
class ProfileMorePersistenceInstrumentedTest {
    @Test fun fieldPatchesPreserveOtherWritesAndRollbackLateGuardWithoutTouchingSource() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val original = readAuthorizedProxyProfile()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "more-persistence-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("离线验收不允许下载")
        })
        try {
            val custom = repository.addCustomProfile("更多设置持久化")
            repository.updateLocalProfileContent(custom.id, original.toString(Charsets.UTF_8))
            val file = repository.profileFile(custom.id)
            val modified = file.lastModified()
            val oldNtp = ProfileNtpOverrides(enable = true, port = 123)
            val oldSniffer = ProfileSnifferOverrides(sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("80"), false)))
            repository.updateProfileOverrides(custom.id, """{"ntp":{"enable":true,"port":124,"future":1},"sniffer":{"enable":true,"sniff":{"HTTP":{"ports":["80"],"override-destination":true,"future":"keep"},"TLS":{"ports":["443"]}}},"dns":{"enable":false},"future-root":1}""")
            val patch = ProfileMorePatch(oldNtp, oldNtp.copy(enable = false), oldSniffer,
                oldSniffer.copy(sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("8080"), false))))
            val saved = repository.saveMoreOptions(custom.id, patch) { true }
            val root = JsonParser.parseString(saved.overridesJson).asJsonObject
            assertFalse(root.getAsJsonObject("ntp")["enable"].asBoolean)
            assertEquals(124, root.getAsJsonObject("ntp")["port"].asInt)
            assertEquals(1, root.getAsJsonObject("ntp")["future"].asInt)
            val protocols = root.getAsJsonObject("sniffer").getAsJsonObject("sniff")
            assertTrue(protocols.getAsJsonObject("HTTP")["override-destination"].asBoolean)
            assertEquals("8080", protocols.getAsJsonObject("HTTP").getAsJsonArray("ports")[0].asString)
            assertEquals("keep", protocols.getAsJsonObject("HTTP")["future"].asString)
            assertTrue(protocols.has("TLS")); assertEquals(1, root["future-root"].asInt)
            assertFalse(root.getAsJsonObject("dns")["enable"].asBoolean)
            val row = database.profiles().getById(custom.id)
            var checks = 0
            val clear = ProfileMorePatch(oldNtp, ProfileNtpOverrides(), oldSniffer, ProfileSnifferOverrides())
            assertTrue(runCatching { repository.saveMoreOptions(custom.id, clear) { ++checks < 2 } }.isFailure)
            assertEquals(2, checks); assertEquals(row, database.profiles().getById(custom.id))
            assertTrue(runCatching { repository.saveMoreOptions(custom.id, clear) { false } }.isFailure)
            assertEquals(row, database.profiles().getById(custom.id))
            assertArrayEquals(original, file.readBytes()); assertEquals(modified, file.lastModified())
            val remote = repository.addFileProfile("只读来源", original)
            assertTrue(runCatching { repository.saveMoreOptions(remote.id, patch) { true } }.isFailure)
            assertArrayEquals(original, repository.profileFile(remote.id).readBytes())
        } finally {
            database.close()
            // 作者 long：只回收本次隔离目录，正式数据库和已有配置不在该路径下。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("more-persistence-"))
            directory.deleteRecursively()
        }
    }
}
