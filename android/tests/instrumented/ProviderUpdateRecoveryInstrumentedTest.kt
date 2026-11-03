package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreExternalProvider
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 作者 long：故障只注入运行边界，正式VM和页面必须自行结束进度并允许重试，不写入真实配置。 */
class ProviderUpdateRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleUpdateTimeoutStopsSpinnerAndRetrySucceedsInSamePage() {
        assertPortraitDefaultFontConfiguration()
        prepareTouchVisualCapture()
        var attempts = 0
        val runtime = object : ProfileStatisticsRuntime {
            override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
            override val profileStatisticsRevision = MutableStateFlow(0L)
            override suspend fun profileStatistics(profileId: Long) = ProfileStatistics(profileId, 0, 3, 0, 1)
            override suspend fun profileProviders(profileId: Long) = listOf(CoreExternalProvider("测试资源", "Proxy", "HTTP", 3, null))
            override suspend fun updateProfileProvider(profileId: Long, name: String) {
                attempts++
                if (attempts == 1) withTimeout(1_000) { awaitCancellation() }
            }
        }
        lateinit var model: ProfileStatisticsViewModel
        compose.runOnUiThread { model = ProfileStatisticsViewModel(runtime); model.show(1); model.setForeground(true) }
        try {
            compose.setContent { KClashTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by model.providers.collectAsState()
                    ProfileProvidersPanel(state, model::retryProviders, model::updateProvider,
                        onRetryFailed = { model.updateAllProviders(onlyFailed = true) })
                }
            } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile_provider_update_0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_provider_update_0").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("更新超时，请重试").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_provider_updating_0").assertDoesNotExist()
            compose.onNodeWithTag("profile_provider_update_0").assertIsEnabled()
            compose.onNodeWithTag("profile_providers_retry_failed").assertIsEnabled()
            capture("provider_update_timeout")
            compose.onNodeWithTag("profile_provider_update_0").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Provider 已更新").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_provider_updating_0").assertDoesNotExist()
            compose.runOnIdle { assertEquals(2, attempts) }
            capture("provider_update_recovered")
        } finally { compose.runOnUiThread { model.viewModelScope.cancel() } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
