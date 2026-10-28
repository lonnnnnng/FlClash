package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：按应用代理的 Compose 宿主用固定应用清单验证白名单/黑名单和搜索选择，
 * 不依赖设备当前安装包，也不启动 VPN；真实包名读取和 Room 保存由后续真机流程覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AccessControlPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchAndSelectPackageUpdatesBlacklist() {
        val apps = listOf(
            InstalledAppInfo("com.example.mail", "示例邮件"),
            InstalledAppInfo("com.example.video", "示例视频"),
        )
        var state by mutableStateOf(
            AccessControlUiState(
                settings = AccessControlProps(
                    enable = true,
                    mode = AccessControlMode.REJECT_SELECTED,
                    acceptList = emptyList(),
                    rejectList = emptyList(),
                ),
                apps = apps,
                isLoading = false,
            ),
        )
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = { state = state.copy(settings = state.settings.copy(enable = it)) },
                    onModeChange = { state = state.copy(settings = state.settings.copy(mode = it)) },
                    onSearchChange = { state = state.copy(searchQuery = it) },
                    onTogglePackage = { packageName ->
                        val selected = currentPackageList(state.settings).toMutableSet()
                        if (!selected.add(packageName)) selected.remove(packageName)
                        state = state.copy(settings = state.settings.withCurrentPackageList(selected))
                    },
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_search").performTextInput("视频")
        composeRule.onNodeWithText("示例视频").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_app_com.example.video").performClick()

        assertEquals(listOf("com.example.video"), state.settings.rejectList)
    }

    @Test
    fun runningCoreLocksAccessControlEdits() {
        val profile = AccessControlUiState(
            settings = AccessControlProps(
                enable = false,
                mode = AccessControlMode.REJECT_SELECTED,
                acceptList = emptyList(),
                rejectList = emptyList(),
            ),
            apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
        )
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = profile,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onEnabledChange = {},
                    onModeChange = {},
                    onSearchChange = {},
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_enabled").assertIsDisplayed()
        composeRule.onNodeWithText("核心运行中，按应用代理设置已锁定；停止核心后才能修改。").assertIsDisplayed()
    }
}
