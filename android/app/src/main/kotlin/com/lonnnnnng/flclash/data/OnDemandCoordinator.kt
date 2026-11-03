package com.lonnnnnng.flclash.data

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class OnDemandNetworkState(
    val ssid: String? = null,
    val foregroundGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    val locationEnabled: Boolean = false,
) {
    val authorized get() = foregroundGranted && backgroundGranted
    override fun toString() = "OnDemandNetworkState(ssid=[REDACTED], authorized=$authorized)"
}

/** 作者 long：应用级监听独立于VPN/页面，暂停转发后仍能收到离开排除Wi-Fi的事件；不扫描周边网络。 */
@Singleton
class OnDemandCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: OnDemandSettingsStore,
    private val manager: CoreRuntimeManager,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val mutableNetwork = MutableStateFlow(OnDemandNetworkState())
    val network = mutableNetwork.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var foreground = false
    private var generation = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var started = false
    private var scope: CoroutineScope? = null
    private var initialReadTimeout: Job? = null
    private val tracker = OnDemandSsidTracker<Network>()
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    @Synchronized fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        ContextCompat.registerReceiver(context, locationReceiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            store.changes?.collect { refresh() }
        }
        scope.launch {
            combine(store.changes ?: flowOf(store.read()), network) { ssids, current -> OnDemandSettings.matches(ssids, current.ssid) }
                .distinctUntilChanged().collect { excluded ->
                    val result = try { manager.setOnDemandExcluded(excluded) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { Result.failure(failure) }
                    mutableError.value = if (result.isFailure) "按需连接切换失败，请重新启动服务" else null
                }
        }
        refresh()
    }

    @Synchronized fun setForeground(visible: Boolean) { foreground = visible; refresh() }

    @Synchronized fun refresh() {
        generation++
        initialReadTimeout?.cancel()
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        val foregroundPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundPermission = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val location = context.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled else
            location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val canRead = foregroundPermission && locationEnabled && (foreground || backgroundPermission)
        val observe = store.read().isNotEmpty() && canRead
        // 作者 long：只为仍存在的同一底层Wi-Fi保留已知名称；全断网时立即清空，不能等待永远不会来的新回调。
        val existing = if (observe) runCatching { connectivity.allNetworks.mapNotNull { network ->
            connectivity.getNetworkCapabilities(network)?.takeIf {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }?.let { network to it }
        }.toMap() }.getOrDefault(emptyMap()) else emptyMap()
        tracker.refresh(existing.filterValues { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }.keys)
        mutableNetwork.value = OnDemandNetworkState(tracker.current, foregroundPermission, backgroundPermission, locationEnabled)
        if (!observe) return
        val token = generation
        val observer = if (Build.VERSION.SDK_INT >= 31) LocationCallback(token) else LegacyCallback(token)
        callback = observer
        runCatching { connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), observer) }
            .onFailure { callback = null; tracker.refresh(emptySet()); mutableNetwork.value = mutableNetwork.value.copy(ssid = null) }
        // 作者 long：系统未回传带位置信息的能力时，最多等待上游同等的3秒窗口，过期按未知处理，禁止永久使用旧名称。
        initialReadTimeout = scope?.launch {
            delay(3_000)
            synchronized(this@OnDemandCoordinator) {
                if (generation == token) {
                    tracker.expireInitialRead()
                    publishSsid()
                }
            }
        }
    }

    @Synchronized private fun changed(token: Long, network: Network, capabilities: NetworkCapabilities?) {
        if (generation != token) return
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ssid = if (wifi) OnDemandSettings.normalizeSsid(runCatching {
            if (Build.VERSION.SDK_INT >= 31) (capabilities.transportInfo as? WifiInfo)?.ssid
            else {
                @Suppress("DEPRECATION")
                context.getSystemService(WifiManager::class.java).connectionInfo?.ssid
            }
        }.getOrNull()) else null
        tracker.changed(network, wifi, ssid)
        publishSsid()
    }

    private fun publishSsid() {
        mutableNetwork.value = mutableNetwork.value.copy(ssid = tracker.current)
    }

    @androidx.annotation.RequiresApi(31)
    private inner class LocationCallback(private val token: Long) : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { changed(token, network, capabilities) }
        override fun onLost(network: Network) { changed(token, network, null) }
    }
    private inner class LegacyCallback(private val token: Long) : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { changed(token, network, capabilities) }
        override fun onLost(network: Network) { changed(token, network, null) }
    }
}
