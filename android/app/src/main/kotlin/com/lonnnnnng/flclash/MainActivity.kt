package com.lonnnnnng.flclash

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lonnnnnng.flclash.ui.KClashApp
import com.lonnnnnng.flclash.ui.MainViewModel
import com.lonnnnnng.flclash.ui.ProfilesViewModel
import com.lonnnnnng.flclash.ui.ProxyGroupsViewModel
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val profilesViewModel: ProfilesViewModel by viewModels()
    private val proxyGroupsViewModel: ProxyGroupsViewModel by viewModels()
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startRuntime()
        } else {
            viewModel.reportError("VPN 权限未授予")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KClashTheme {
                KClashApp(
                    runtimeViewModel = viewModel,
                    profilesViewModel = profilesViewModel,
                    proxyGroupsViewModel = proxyGroupsViewModel,
                    onStartRuntime = ::requestRuntimeStart,
                    onStopRuntime = viewModel::stopRuntime,
                )
            }
        }
    }

    private fun requestRuntimeStart() {
        // 作者 long：Android 要求每次启动前重新检查授权，不能把上一次同意结果当成永久权限。
        val consentIntent = runCatching { VpnService.prepare(this) }.getOrElse { error ->
            viewModel.reportError(error.message ?: "无法申请 VPN 权限")
            return
        }
        if (consentIntent == null) {
            viewModel.startRuntime()
        } else {
            vpnConsentLauncher.launch(consentIntent)
        }
    }
}
