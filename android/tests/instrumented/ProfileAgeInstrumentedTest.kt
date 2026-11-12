package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProfileAgeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun remoteEditorMasksAndTrimsAgeKeyInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var savedKey: String? = null
        val profile = Profile.remote(810, "https://example.test/sub", "加密订阅").copy(ageSecretKey = "fixture-identity")
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                RemoteProfileEditorDialog(profile, false, {}, { _, _, _, _ -> error("必须传递密钥") },
                    onSaveWithAgeKey = { _, _, _, _, key -> savedKey = key })
            }
        }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            val field = compose.onNodeWithTag("remote_profile_editor_age_key")
            field.performScrollTo().assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            compose.runOnUiThread {
                compose.activity.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
            }
            val bitmap = compose.onNodeWithTag("remote_profile_editor_form").captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            recordVisualPng("profile_age_editor_$theme", png)
        }
        compose.onNodeWithTag("remote_profile_editor_age_key").performTextClearance()
        compose.onNodeWithTag("remote_profile_editor_age_key").performTextInput(" updated-fixture ")
        compose.onNodeWithText("保存").performClick()
        assertTrue(savedKey == "updated-fixture")
    }

    @Test fun singleImportForwardsKeyAndBatchModeDoesNotOfferSharedKey() {
        assertPortraitDefaultFontConfiguration()
        var savedKey: String? = null
        var feedback by mutableStateOf(ProfileAddFeedback())
        var session by mutableIntStateOf(0)
        compose.setContent {
            KClashTheme {
                key(session) {
                    AddProfileDialog(
                        busy = false,
                        feedback = feedback,
                        onDismiss = {},
                        onPickFile = {},
                        onPickQrCode = {},
                        onAddRemote = { _, _ -> error("必须传递密钥") },
                        onAddRemoteWithAgeKey = { _, _, key ->
                            savedKey = key
                            feedback = feedback.copy(phase = ProfileAddPhase.RUNNING, message = "正在下载、解密并校验订阅…")
                        },
                        onAddCustom = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("profile_add_source_remote").performClick()
        compose.onNodeWithTag("profile_add_remote_url").performTextInput("https://example.test/sub")
        compose.onNodeWithTag("profile_add_remote_age_key").performScrollTo().performTextInput(" fixture-identity ")
        compose.onNodeWithTag("profile_add_submit").performClick()
        assertTrue(savedKey == "fixture-identity")
        compose.onNodeWithTag("profile_add_progress").assertIsDisplayed()
        compose.runOnIdle { feedback = feedback.copy(phase = ProfileAddPhase.SUCCEEDED, message = "订阅已添加") }
        compose.onNodeWithTag("profile_add_result").assertIsDisplayed()
        compose.runOnIdle { feedback = ProfileAddFeedback(); session++ }
        compose.onNodeWithTag("profile_add_source_remote").performClick()
        compose.onNodeWithTag("profile_add_mode_batch").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").assertDoesNotExist()
    }

    @Test fun missingKeyDialogDisablesEmptySubmitAndClearsWhenBackgrounded() {
        assertPortraitDefaultFontConfiguration()
        var open by mutableStateOf(true)
        var submitted = false
        compose.setContent { KClashTheme { if (open) AgeSecretKeyDialog({ submitted = true }, { open = false }) } }
        compose.onNodeWithText("解密并导入").assertIsNotEnabled()
        compose.onNodeWithTag("profile_age_required_key").performTextInput("fixture-identity")
        compose.onNodeWithText("解密并导入").assertIsEnabled()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(open)
        assertFalse(submitted)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("profile_age_required_key").assertDoesNotExist()
    }

    @Test fun backgroundPreservesExistingEditorAndClearsHiddenNewImportKey() {
        assertPortraitDefaultFontConfiguration()
        var edit by mutableStateOf(true)
        val profile = Profile.remote(811, "https://example.test/sub", "已有配置").copy(ageSecretKey = "persisted-fixture")
        var saved = false
        var savedKey: String? = null
        compose.setContent {
            KClashTheme {
                if (edit) RemoteProfileEditorDialog(profile, false, { edit = false }, { _, _, _, _ -> error("必须传递密钥") },
                    onSaveWithAgeKey = { _, _, _, _, key -> saved = true; savedKey = key; edit = false })
                else AddProfileDialog(false, {}, {}, {}, { _, _ -> }, onAddCustom = {})
            }
        }
        compose.onNodeWithTag("remote_profile_editor_age_key").assertExists()
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // 作者 long：已有订阅编辑现由VM加密保留会话；新建导入的临时密钥仍必须清除，两种入口不能套用早期的统一关闭断言。
        assertTrue(edit)
        assertFalse(saved)
        assertTrue(profile.ageSecretKey == "persisted-fixture")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("remote_profile_editor_age_key").performScrollTo().assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        // 作者 long：掩码语义不是密钥原文，通过隔离回调验证后台后保存仍传递原密钥，不读取密码显示字符来推断内容。
        compose.onNodeWithTag("remote_profile_editor_save").performClick()
        assertTrue(saved)
        assertTrue(savedKey == "persisted-fixture")
        assertFalse(edit)
        compose.onNodeWithTag("profile_add_source_remote").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").performScrollTo().performTextInput("hidden-fixture")
        compose.onNodeWithTag("profile_add_mode_batch").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("profile_add_mode_single").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").performScrollTo().assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")),
        )
    }

    @Test fun encryptedFilePreviewRequiresKeyInsteadOfReportingInvalidYaml() {
        assertPortraitDefaultFontConfiguration()
        var confirmed = false
        compose.setContent {
            KClashTheme {
                ProfileFileImportPreviewDialog("1.yaml", 34814, false, false, { confirmed = true }, {}, isEncrypted = true)
            }
        }
        compose.onNodeWithText("age 加密配置，导入时输入密钥并校验").assertIsDisplayed()
        compose.onNodeWithTag("profile_file_import_confirm").assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test fun repositoryUsesPersistedKeyForUpdatesAndKeepsOldCacheAfterFailure() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("只运行获准的 1.yaml 验收", args.getString("kclashAgeLocal") == "true")
        assertPortraitDefaultFontConfiguration()
        val bytes = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat /data/local/tmp/kclash-profile-preview-1.yaml").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashAgeSha256"), hash)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "profile-age-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val observationJob = SupervisorJob()
        var fail = false
        val keys = mutableListOf<String>()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String) = SubscriptionDownload(bytes, null, SubscriptionInfo())
        }, contentDecoder = ProfileContentDecoder { content, key ->
            keys += key
            if (fail) throw IllegalArgumentException("age 解密失败，请检查密钥与配置文件")
            content
        }, observationScope = CoroutineScope(observationJob + Dispatchers.IO))
        try {
            runBlocking {
                // 作者 long：真机配置正文只使用 1.yaml 原字节；Go 方法的明文透传与仓库密钥接线分开核验，不假称密文 E2E。
                val input = File(directory, "authorized-input.yaml").apply { writeBytes(bytes) }
                assertTrue(bytes.contentEquals(CoreBridge().decryptConfig(input.absolutePath, "unused-fixture").toByteArray()))
                val profile = repository.addRemoteProfile("https://example.test/not-networked", "age 接线验收", " fixture-identity ")
                assertTrue(database.profiles().getById(profile.id)!!.ageSecretKey == "fixture-identity")
                val cache = repository.profileFile(profile.id)
                assertTrue(bytes.contentEquals(cache.readBytes()))
                val lastUpdate = database.profiles().getById(profile.id)!!.lastUpdateAt
                val modified = cache.lastModified()
                fail = true
                val beforeNames = cache.parentFile!!.list().orEmpty().toSet()
                assertTrue(runCatching { repository.addRemoteProfile("https://example.test/rejected", "", "fixture-identity") }.isFailure)
                assertTrue(runCatching { repository.addFileProfile("rejected", bytes, "fixture-identity") }.isFailure)
                assertEquals(1, database.profiles().getAll().size)
                assertEquals(beforeNames, cache.parentFile!!.list().orEmpty().toSet())
                val failed = runCatching { repository.updateRemoteProfile(profile.id) }
                assertTrue(failed.isFailure)
                assertTrue(bytes.contentEquals(cache.readBytes()))
                assertEquals(modified, cache.lastModified())
                assertEquals(lastUpdate, database.profiles().getById(profile.id)!!.lastUpdateAt)
                // 作者 long：仓库只持久化统一脱敏错误，原始下载/解密异常不能借回归测试重新泄漏到配置记录。
                assertEquals("订阅更新失败，请检查网络、订阅地址或解密密钥", database.profiles().getById(profile.id)!!.lastUpdateError)
                fail = false
                repository.updateDueProfiles(Long.MAX_VALUE).also { assertEquals(1, it.succeeded) }
                assertNull(database.profiles().getById(profile.id)!!.lastUpdateError)
                val fileProfile = repository.addFileProfile("本地 age 接线", bytes, " fixture-identity ")
                assertTrue(bytes.contentEquals(repository.profileFile(fileProfile.id).readBytes()))
                assertTrue(database.profiles().getById(fileProfile.id)!!.ageSecretKey == "fixture-identity")
                assertTrue(keys.all { it.trim() == "fixture-identity" })
                assertEquals(2, database.profiles().getAll().size)
            }
        } finally {
            // 作者 long：先结束仓库持有的Room观察，再关闭临时数据库，避免迟到读取干扰同批后续用例。
            runBlocking { observationJob.cancelAndJoin() }
            database.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("profile-age-"))
            directory.deleteRecursively()
        }
    }
}
