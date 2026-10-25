package com.lonnnnnng.flclash

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import com.lonnnnnng.flclash.common.QuickAction
import com.lonnnnnng.flclash.common.quickIntent
import com.lonnnnnng.flclash.common.toPendingIntent
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 作者 long：Quick Settings 只负责展示统一运行状态和发出切换请求，核心、VPN 与 TUN 生命周期仍由 Runtime Manager 收口。
 */
@AndroidEntryPoint
class KClashTileService : android.service.quicksettings.TileService() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                runtimeManager.state.collect(::updateTile)
            }
        }
    }

    // 作者 long：通过系统面板启动 Activity 会自动折叠快捷设置，避免点击后面板仍遮挡应用状态变化。
    override fun onClick() {
        super.onClick()
        openQuickAction()
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningScope?.cancel()
        listeningScope = null
        super.onDestroy()
    }

    private fun updateTile(status: CoreRuntimeStatus) {
        qsTile?.apply {
            state = when (status) {
                CoreRuntimeStatus.RUNNING -> Tile.STATE_ACTIVE
                CoreRuntimeStatus.STARTING,
                CoreRuntimeStatus.STOPPING,
                -> Tile.STATE_UNAVAILABLE
                CoreRuntimeStatus.STOPPED,
                CoreRuntimeStatus.FAILED,
                -> Tile.STATE_INACTIVE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when (status) {
                    CoreRuntimeStatus.RUNNING -> "运行中"
                    CoreRuntimeStatus.STARTING -> "启动中"
                    CoreRuntimeStatus.STOPPING -> "停止中"
                    CoreRuntimeStatus.STOPPED -> "已停止"
                    CoreRuntimeStatus.FAILED -> "启动失败"
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                contentDescription = "KClash：${status.label}"
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openQuickAction() {
        val intent = QuickAction.TOGGLE.quickIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(intent.toPendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
