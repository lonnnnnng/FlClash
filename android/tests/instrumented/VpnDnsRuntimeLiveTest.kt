package com.lonnnnnng.flclash.ui

import android.net.VpnService
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.EntryPointAccessors
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 作者 long：核心自身的 HTTP 探测绕过 Android DNS，必须另用真实 VPN 内的应用 socket 验证 DNS 接管。 */
class VpnDnsRuntimeLiveTest {
    @Test fun importedProfileAnswersAndroidUdpAndSystemDns() = runBlocking(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashVpnDnsAcceptance") == "true")
        val context = instrumentation.targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val rows = entry.database().profiles().getAll()
        val active = repository.activeProfile()
        val profile = rows.single { it.id == active.id }
        val digest = MessageDigest.getInstance("SHA-256").digest(profile.url.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("只复用已授权配置", args.getString("kclashAuthorizedSourceSha256"), digest)
        val metadata = rows.associate { it.id to repository.profileFile(it.id).let { file -> file.length() to file.lastModified() } }
        assertNull("先通过正式入口授予 VPN 权限", VpnService.prepare(context))
        val manager = entry.runtimeManager()
        val originalVpn = entry.vpnSettingsStore().read()
        try {
            manager.setVpnEnabled(true).getOrThrow()
            manager.startProxy().getOrThrow()
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            val config = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_vpn_config", "dns-enabled=" + config.getAsJsonObject("dns")?.get("enable") +
                    " stack=" + com.lonnnnnng.flclash.service.ServiceConfig.vpnOptions?.stack)
            })
            kotlinx.coroutines.delay(1_500)
            val query = byteArrayOf(0x13, 0x57, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 119, 119, 119,
                5, 98, 97, 105, 100, 117, 3, 99, 111, 109, 0, 0, 1, 0, 1)
            val dns = InetAddress.getByAddress(byteArrayOf(198.toByte(), 18, 0, 2))
            val results = linkedMapOf<String, Boolean>()
            results["udp53"] = runCatching {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 10_000
                    socket.send(DatagramPacket(query, query.size, dns, 53))
                    val reply = DatagramPacket(ByteArray(4096), 4096)
                    socket.receive(reply)
                    reply.length >= 12 && reply.data[0] == query[0] && reply.data[1] == query[1] &&
                        (reply.data[3].toInt() and 15) == 0 && (reply.data[7].toInt() and 255) > 0
                }
            }.getOrDefault(false)
            var tcpStage = "connect"
            var tcpError = ""
            results["tcp53"] = runCatching {
                // 作者 long：VPN 提供者进程的普通 TCP socket 可绕过自身 VPN，必须绑定 VPN 网络才是在验证 TUN。
                val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                val vpn = connectivity.allNetworks.first { network ->
                    connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                }
                vpn.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(dns, 53), 10_000)
                    socket.soTimeout = 10_000
                    tcpStage = "read"
                    val output = java.io.DataOutputStream(socket.getOutputStream())
                    output.writeShort(query.size)
                    output.write(query)
                    output.flush()
                    val input = java.io.DataInputStream(socket.getInputStream())
                    val reply = ByteArray(input.readUnsignedShort())
                    input.readFully(reply)
                    reply.size >= 12 && (reply[3].toInt() and 15) == 0 && (reply[7].toInt() and 255) > 0
                }
            }.onFailure { tcpError = it.javaClass.simpleName + ":" + it.message }.getOrDefault(false)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_tcp_stage", tcpStage + " " + tcpError) })
            results["system"] = runCatching { InetAddress.getAllByName("www.baidu.com").isNotEmpty() }.getOrDefault(false)
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_vpn_dns", results.toString()) })
            assertTrue("应用必须能通过 VPN 完成 UDP/TCP 和系统域名解析：$results", results.values.all { it })
        } finally {
            manager.stop().getOrThrow()
            manager.setVpnEnabled(originalVpn).getOrThrow()
            assertEquals(metadata.keys, entry.database().profiles().getAll().map { it.id }.toSet())
            metadata.forEach { (id, stamp) -> assertEquals(stamp, repository.profileFile(id).let { it.length() to it.lastModified() }) }
        }
    }
}
