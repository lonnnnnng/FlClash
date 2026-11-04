package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：排序手势只更新父草稿；失败、取消和后台中断不能变成未确认的Room写入。 */
class ProfileClosureUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun orderPageStagesReconcilesAndRetainsFailedSaveInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var profiles by mutableStateOf(listOf(Profile.custom(1, "配置一"), Profile.custom(2, "配置二")))
        var revision by mutableLongStateOf(0)
        var submitted = emptyList<Long>()
        var dismissals = 0
        var reject = true
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            ProfileReorderDialog(profiles, false, { dismissals++ }, { ids ->
                submitted = ids
                if (reject) error = "排序保存失败，请重试" else { error = null; revision++ }
            }, error, revision)
        } } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; error = null; submitted = emptyList(); reject = true; profiles = profiles.take(2) }
            compose.onNodeWithTag("profiles_order_1").performOrderTouchMove(1)
            compose.runOnIdle { assertTrue(submitted.isEmpty()); profiles = profiles + Profile.custom(3, "新增配置") }
            compose.onNodeWithTag("profiles_order_2").assertIsDisplayed()
            compose.onNodeWithTag("profiles_order_save").performClick()
            compose.onNodeWithTag("profiles_order_error").assertTextContains("排序保存失败", substring = true)
            compose.runOnIdle { assertEquals(listOf(2L, 1L, 3L), submitted); reject = false }
            compose.onNodeWithTag("profiles_order_save").performClick()
            compose.waitForIdle()
            capture("profile_order_$theme")
        }
        assertEquals(2, dismissals)
    }

    @Test fun applyFailureProvidesIndependentRetryWithoutDownloadRows() {
        assertPortraitDefaultFontConfiguration()
        var retries = 0
        compose.setContent { KClashTheme { ProfileSyncResults(ProfileSyncUiState(applyError = "配置已保存，应用失败"), {}, { retries++ }) } }
        compose.onNodeWithText("配置已保存，应用失败").assertIsDisplayed()
        compose.onNodeWithTag("profile_sync_retry").assertTextEquals("重试应用").performClick()
        assertEquals(1, retries)
        capture("profile_apply_retry")
    }

    private fun capture(name: String) {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashCaptureScreenshots") == "true") {
            compose.mainClock.advanceTimeBy(450); compose.waitForIdle()
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("kclash_png_name", name); putString("kclash_png_base64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            })
            bitmap.recycle()
        }
    }
}
