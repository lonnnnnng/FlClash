package com.lonnnnnng.flclash.data

import android.content.Context
import android.net.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class LocalAddressState(val address: String? = null, val isLoading: Boolean = true, val error: String? = null)
data class LocalNetworkCandidate(val id: String, val priority: Int, val addresses: List<InetAddress>, val isVpn: Boolean = false)

fun selectLocalAddress(networks: List<LocalNetworkCandidate>): String? = networks
    .filterNot { it.isVpn }
    .sortedWith(compareBy<LocalNetworkCandidate> { it.priority }.thenBy { it.id })
    .firstNotNullOfOrNull { network ->
        network.addresses.filterNot { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isMulticastAddress }
            .sortedWith(compareBy<InetAddress> { if (it is Inet4Address) 0 else 1 }.thenBy { it.hostAddress })
            .firstOrNull()?.hostAddress
    }

interface LocalNetworkSource {
    fun observe(): Flow<LocalAddressState>
}

class AndroidLocalNetworkSource @Inject constructor(@ApplicationContext context: Context) : LocalNetworkSource {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    override fun observe(): Flow<LocalAddressState> = callbackFlow {
        val networks = linkedMapOf<Network, Pair<NetworkCapabilities?, LinkProperties?>>()
        val lock = Any()
        fun publish() {
            val candidates = networks.mapNotNull { (network, properties) ->
                val caps = properties.first ?: return@mapNotNull null
                val links = properties.second ?: return@mapNotNull null
                LocalNetworkCandidate(network.toString(), when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 3
                    else -> 2
                }, links.linkAddresses.map { it.address }, caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            }
            val address = selectLocalAddress(candidates)
            trySend(LocalAddressState(address, isLoading = address == null && networks.values.any { it.first == null || it.second == null }))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = synchronized(lock) {
                networks.putIfAbsent(network, null to null)
                publish()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = synchronized(lock) {
                networks[network] = capabilities to networks[network]?.second
                publish()
            }
            override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) = synchronized(lock) {
                networks[network] = networks[network]?.first to links
                publish()
            }
            override fun onLost(network: Network) = synchronized(lock) { networks.remove(network); publish() }
        }
        var registered = false
        try {
            // 作者 long：不要求 INTERNET，局域网即使无法上网也可共享；排除 VPN，避免显示 TUN 虚拟地址。
            connectivity.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
            registered = true
            launch { delay(300); synchronized(lock) { publish() } }
        } catch (_: Exception) {
            trySend(LocalAddressState(isLoading = false, error = "无法读取内网地址，请重试"))
        }
        awaitClose {
            if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
            synchronized(lock) { networks.clear() }
        }
    }
}
