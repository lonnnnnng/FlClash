package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.core.RuleSetInfo
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourceOrderRepositoryInstrumentedTest {
    @Test fun fullOrderPreservesLatestMetadataAndFilesAndRejectsStaleOrDuplicateIds() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "resource-order-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val iconClient = object : ScriptClient { override suspend fun download(url: String): ScriptDownload = error("排序不允许下载") }
        val icons = IconSetRepository(db, iconClient)
        val rules = RuleResourceRepository(isolated, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("排序不允许下载")
        }, object : RuleResourceCompiler {
            override suspend fun compile(name: String) = RuleSetInfo("domain", "text")
            override suspend fun preview(path: String): String = error("排序不允许读预览")
        })
        try {
            val galleries = (1..3).map { icons.importContent("""{"name":"图库$it","icons":[{"name":"YouTube.png","url":"https://example.org/$it.png"}]}""") }
            icons.rename(galleries[0].id, "最新名称")
            val latest = icons.all().associateBy { it.id }
            icons.reorder(galleries.reversed().map { it.id })
            assertEquals(galleries.reversed().map { latest.getValue(it.id) }, IconSetRepository(db, iconClient).all())
            val beforeIcons = db.appPreferences().get(ICON_SETS_KEY)
            for (ids in listOf(galleries.drop(1).map { it.id }, listOf(galleries[0].id, galleries[0].id, galleries[1].id), galleries.map { it.id } + "missing")) {
                assertTrue(runCatching { icons.reorder(ids) }.isFailure)
                assertEquals(beforeIcons, db.appPreferences().get(ICON_SETS_KEY))
            }
            val entries = (1..3).map { rules.save("规则$it", "", "+.example$it.org\n".toByteArray()) }
            val renamed = rules.updateOptions(entries[0], "最新规则名", "")
            val files = entries.associate { row -> row.id to (rules.file(row).readBytes() to rules.file(row).lastModified()) }
            val original = rules.all().associateBy { it.id }
            rules.reorder(entries.reversed().map { it.id })
            assertEquals(entries.reversed().map { original.getValue(it.id) }, rules.all())
            assertEquals(renamed, rules.all().last())
            entries.forEach { row -> assertArrayEquals(files.getValue(row.id).first, rules.file(row).readBytes()); assertEquals(files.getValue(row.id).second, rules.file(row).lastModified()) }
            val beforeRules = db.appPreferences().get(RULE_RESOURCES_KEY)
            for (ids in listOf(entries.drop(1).map { it.id }, listOf(entries[0].id, entries[0].id, entries[1].id), entries.map { it.id } + "missing")) {
                assertTrue(runCatching { rules.reorder(ids) }.isFailure)
                assertEquals(beforeRules, db.appPreferences().get(RULE_RESOURCES_KEY))
            }
            assertTrue(db.profiles().getAll().isEmpty())
        } finally {
            db.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("resource-order-"))
            directory.deleteRecursively()
        }
    }
}
