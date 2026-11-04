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
        val file = context.cacheDir.resolve("menu-visual-preserved.sha256")
        val phase = InstrumentationRegistry.getArguments().getString("kclashVisualPreserve", "after")
        if (phase == "before") file.writeText(record) else { assertTrue(file.isFile); assertEquals(file.readText(), record) }
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
