package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest

/** 作者 long：正式宿主保护直接读Repository，不依赖可被用户隐藏的首页统计卡；日志只输出数量与摘要。 */
class MenuVisualHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun formalProfilesAndFilesArePreserved() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.application
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val rows = entry.database().profiles().getAll().sortedBy { it.id }
        assertTrue("不能将空库记为配置保持", rows.isNotEmpty())
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val record = buildString {
            appendLine(sha(Gson().toJson(rows).toByteArray()))
            appendLine(entry.profileRepository().currentProfileId.value)
            for (row in rows) {
                val file = entry.profileRepository().profileFile(row.id)
                assertTrue(file.isFile && file.length() > 0)
                appendLine("${row.id}:${file.name}:${file.length()}:${file.lastModified()}:${sha(file.readBytes())}")
            }
        }
        val session = InstrumentationRegistry.getArguments().getString("kclashVisualPreserveSession", "")
        require(session.isEmpty() || session.matches(Regex("[a-z0-9-]{1,64}")))
        val suffix = session.takeIf { it.isNotEmpty() }?.let { "-$it" }.orEmpty()
        val file = context.cacheDir.resolve("menu-visual-preserved$suffix.sha256")
        val phase = InstrumentationRegistry.getArguments().getString("kclashVisualPreserve", "after")
        val fieldFile = context.cacheDir.resolve("menu-visual-preserved$suffix-fields.json")
        val gson = Gson()
        val fields = rows.associate { row ->
            row.id.toString() to gson.toJsonTree(row).asJsonObject.entrySet().associate { (name, value) ->
                name to sha(value.toString().toByteArray())
            }.toSortedMap()
        }
        if (phase == "before") {
            file.writeText(record)
            // 作者 long：全行摘要只能发现变化；逐字段摘要使后续验收能定位元数据差异，又不落盘订阅与密钥原文。
            fieldFile.writeText(gson.toJson(fields))
        } else {
            assertTrue(file.isFile)
            if (phase == "inspect") {
                // 作者 long：诊断只报告字段摘要与文件保护结果，不能用当前状态覆盖旧基线或将检查模式记为保护通过。
                InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putString("kclash_visual_metadata_inspect", gson.toJson(mapOf("fields" to fields,
                        "filesAndSelectionMatch" to (file.readLines().drop(1) == record.lines().dropLast(1).drop(1)),
                        "autoUpdateEnabled" to rows.any { it.autoUpdate }, "hasUpdateError" to rows.any { it.lastUpdateError != null })))
                })
                return@runBlocking
            }
            assertEquals(file.readText(), record)
            if (fieldFile.isFile) assertEquals(fieldFile.readText(), gson.toJson(fields))
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("kclash_visual_preserved", "$phase count=${rows.size} sha=${sha(record.toByteArray())}")
        })
    }

    @Test fun actualSourceMenuOpensRemoteFormAndCancelsWithoutAddingProfile() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val before = entry.database().profiles().getAll()
        compose.onNodeWithTag("nav_profiles").performClick()
        compose.onNodeWithTag("profiles_add_fab").performClick()
        compose.onNodeWithTag("profile_add_source_remote").assertIsDisplayed().performClick()
        compose.onNodeWithTag("profile_add_remote_url").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("profile_add_remote_url").assertDoesNotExist()
        compose.onNodeWithTag("profiles_add_fab").assertIsDisplayed()
        assertEquals(before, entry.database().profiles().getAll())
    }
}
