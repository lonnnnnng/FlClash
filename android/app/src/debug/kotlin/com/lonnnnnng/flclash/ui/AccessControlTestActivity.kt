package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：截图矩阵需要固定的应用清单和初始状态，使用 Debug 专用宿主隔离 Room、系统已安装应用和核心运行态，
 * 让横竖屏像素差异只反映窗口约束与 Access 卡片布局。
 */
class AccessControlTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = emptyList(),
                        ),
                        apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
                        isLoading = false,
                    ),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = {},
                    onModeChange = {},
                    onSortChange = {},
                    onFilterSystemAppsChange = {},
                    onFilterNonInternetAppsChange = {},
                    onSearchChange = {},
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onToggleVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }
    }
}
