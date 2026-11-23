package com.lonnnnnng.flclash.update

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 作者 long：检查更新只用一个弹框，等待时不能重复提交，结果明确关闭或交给浏览器。 */
class AppUpdateDialogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingLatestAvailableAndFailureUseOnlyRelevantActions() {
        val state = mutableStateOf(UpdateState(UpdatePhase.CHECKING))
        var dismissals = 0
        var browserOpens = 0
        var retries = 0
        compose.setContent { KClashTheme {
            UpdateDialog(state.value, "0.1.17", null, { dismissals++ }, { retries++ }, { browserOpens++ })
        } }
        compose.onNodeWithTag("app_update_loading").assertExists()
        compose.onNodeWithTag("app_update_github").assertDoesNotExist()
        compose.onNodeWithTag("app_update_check").assertDoesNotExist()
        compose.onNodeWithTag("app_update_cancel").performClick()
        assertEquals(1, dismissals)
        compose.runOnIdle { state.value = UpdateState(UpdatePhase.LATEST) }
        compose.onNodeWithTag("app_update_loading").assertDoesNotExist()
        compose.onNodeWithTag("app_update_done").performClick()
        assertEquals(2, dismissals)
        compose.runOnIdle { state.value = UpdateState(UpdatePhase.AVAILABLE,
            AppRelease("0.1.18", 1018, "https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.18")) }
        compose.onNodeWithTag("app_update_github").performClick()
        assertEquals(1, browserOpens)
        compose.runOnIdle { state.value = UpdateState(UpdatePhase.FAILED) }
        compose.onNodeWithTag("app_update_github").assertDoesNotExist()
        compose.onNodeWithTag("app_update_check").performClick()
        assertEquals(1, retries)
    }
}
