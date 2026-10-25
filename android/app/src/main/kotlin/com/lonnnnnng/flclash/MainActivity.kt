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
import com.lonnnnnng.flclash.data.DeepLinkProfileParser
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

    private var initialDeepLinkHandled = false

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

    override fun onResume() {
        super.onResume()
        if (!initialDeepLinkHandled) {
            initialDeepLinkHandled = true
            // 作者 long：等 Compose 建立错误收集器后再消费首个链接，避免无效 Deep Link 的提示在首帧前丢失。
            window.decorView.post { handleDeepLink(intent) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        window.decorView.post { handleDeepLink(intent) }
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

    private fun handleDeepLink(intent: Intent?) {
        val raw = intent?.dataString?.trim().orEmpty()
        if (raw.isEmpty() || !DeepLinkProfileParser.isSupported(raw)) return
        val imported = DeepLinkProfileParser.parse(raw)
        if (imported == null) {
            profilesViewModel.reportError("配置 Deep Link 无效")
            return
        }
        profilesViewModel.addRemote(imported.url, imported.label.orEmpty())
    }
}
