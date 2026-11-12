package com.lonnnnnng.flclash;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** 作者 long：测试APK也作为独立普通应用启动；不通过Instrumentation共享目标UID，不绑物理网络或指定SOCKS。 */
public class NetworkSwitchProbeActivity extends Activity {
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private static JSONObject networkSnapshot(ConnectivityManager manager, Network network) throws Exception {
        JSONObject result = new JSONObject();
        if (network == null) return result.put("present", false);
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        LinkProperties links = manager.getLinkProperties(network);
        JSONArray dns = new JSONArray();
        if (links != null) for (InetAddress address : links.getDnsServers()) dns.put(hash(address.getAddress()));
        return result.put("present", caps != null).put("handle", network.getNetworkHandle())
            .put("vpn", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            .put("wifi", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .put("cellular", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .put("validated", caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .put("dnsHashes", dns);
    }
    /** 作者 long：同一绑定代码分别在VPN提供者和普通应用UID执行，不能凭测试包名推定socket的真实身份。 */
    public static JSONObject bindingSnapshot(android.content.Context context) throws Exception {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        Network vpn = null; int count = 0;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpn = network; count++; }
        }
        JSONObject result = new JSONObject().put("uid", Process.myUid()).put("pid", Process.myPid())
            .put("startedElapsedMs", SystemClock.elapsedRealtime())
            .put("vpnNetworkCount", count).put("vpnNetwork", networkSnapshot(manager, vpn))
            .put("defaultNetwork", networkSnapshot(manager, manager.getActiveNetwork()))
            .put("boundProcessNetwork", networkSnapshot(manager, manager.getBoundNetworkForProcess()));
        if (count != 1) return result;
        JSONObject attempts = new JSONObject();
        for (String method : new String[] {"socket-factory", "tcp-bind", "udp-bind"}) {
            JSONObject attempt = new JSONObject().put("startedElapsedMs", SystemClock.elapsedRealtime());
            try {
                if (method.equals("socket-factory")) {
                    try (Socket socket = vpn.getSocketFactory().createSocket()) { attempt.put("bound", true); }
                } else if (method.equals("tcp-bind")) {
                    try (Socket socket = new Socket()) { vpn.bindSocket(socket); attempt.put("bound", true); }
                } else {
                    try (DatagramSocket socket = new DatagramSocket(null)) { vpn.bindSocket(socket); attempt.put("bound", true); }
                }
            } catch (Exception failure) {
                // 作者 long：只记录系统errno和类型链，保留权限拒绝证据而不输出可能含地址的异常message。
                JSONArray types = new JSONArray(); JSONArray errnos = new JSONArray(); Throwable cause = failure;
                for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause()) {
                    types.put(cause.getClass().getSimpleName());
                    if (cause instanceof android.system.ErrnoException) errnos.put(((android.system.ErrnoException) cause).errno);
                }
                attempt.put("bound", false).put("failureTypes", types).put("errnos", errnos);
            }
            attempts.put(method, attempt.put("finishedElapsedMs", SystemClock.elapsedRealtime()));
        }
        return result.put("attempts", attempts).put("finishedElapsedMs", SystemClock.elapsedRealtime());
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getPackageName().equals("com.lonnnnnng.flclash.motion.test")) throw new IllegalStateException("test package required");
        TextView label = new TextView(this); label.setText("KClash 独立UID网络切换验收"); setContentView(label);
        final String token = getIntent().getStringExtra("token");
        final String command = getIntent().getStringExtra("command");
        if (token == null || !token.matches("[a-f0-9]{32}") || command == null || !command.matches("[a-z0-9-]+")) throw new IllegalArgumentException("invalid acceptance command");
        File directory = getExternalFilesDir(null);
        if ("init".equals(command)) { finish(); return; }
        final int port = getIntent().getIntExtra("sourcePort", 0);
        if (port < 30000 || port > 60000) throw new IllegalArgumentException("source port required");
        new Thread(() -> request(directory, token, command, port), "network-switch-probe").start();
    }
    private void request(File directory, String token, String command, int port) {
        JSONObject report = new JSONObject();
        String step = "input";
        long started = SystemClock.elapsedRealtime();
        try {
            // 作者 long：首请求必须量出相对核心READY的真实间隔，不能把人工操作后的热身请求算作立即联网。
            report.put("requestStartedElapsedMs", started);
            byte[] inputBytes;
            try (java.io.FileInputStream stream = new java.io.FileInputStream(new File(directory, "switch-" + token + ".json"))) {
                ByteArrayOutputStream content = new ByteArrayOutputStream(); byte[] chunk = new byte[1024]; int count;
                while ((count = stream.read(chunk)) >= 0) { content.write(chunk, 0, count); if (content.size() > 8192) throw new IllegalStateException("input budget exceeded"); }
                inputBytes = content.toByteArray();
            }
            JSONObject input = new JSONObject(new String(inputBytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!token.equals(input.getString("token"))) throw new IllegalStateException("wrong input token");
            URI target = URI.create(input.getString("authorizedUrl"));
            if (!"https".equals(target.getScheme()) || target.getHost() == null || target.getRawUserInfo() != null) throw new IllegalStateException("HTTPS target required");
            if (getIntent().getBooleanExtra("bindingOnly", false)) {
                // 作者 long：诊断完成与绑定成功分开记录；这里只复现系统绑定条件，不把它计成DNS/TLS/HTTP通过。
                report.put("diagnosticOnly", true).put("binding", bindingSnapshot(this)).put("passed", true);
                emit(token, command, report, started);
                return;
            }
            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            Network active = connectivity.getActiveNetwork();
            report.put("networkBefore", networkSnapshot(connectivity, active)).put("uid", Process.myUid()).put("pid", Process.myPid())
                .put("sourcePort", port).put("boundProcessNetwork", networkSnapshot(connectivity, connectivity.getBoundNetworkForProcess())).put("targetHostHash", hash(target.getHost().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (active == null || !connectivity.getNetworkCapabilities(active).hasTransport(NetworkCapabilities.TRANSPORT_VPN)) throw new IllegalStateException("default VPN required");
            step = "dns";
            long dnsStart = SystemClock.elapsedRealtime();
            // 作者 long：直接向系统给本应用的VPN DNS发真实A查询，避免进程DNS缓存让切网验收绕过解析；不打印域名或地址。
            InetAddress dns = connectivity.getLinkProperties(active).getDnsServers().get(0);
            final boolean bindVpn = getIntent().getBooleanExtra("bindVpn", false);
            report.put("explicitVpnSocketBinding", bindVpn);
            InetAddress address;
            try {
                address = resolve(dns, target.getHost(), port, bindVpn ? active : null);
                report.put("dnsMethod", "vpn-dns-wire").put("wireDnsPassed", true);
            } catch (Exception wireFailure) {
                // 作者 long：保留UDP原始失败，再独立检查普通应用系统解析路径；成功不能回写为UDP查询通过。
                report.put("wireDnsPassed", false).put("wireDnsFailureType", wireFailure.getClass().getSimpleName());
                address = active.getAllByName(target.getHost())[0];
                report.put("dnsMethod", "vpn-system-resolver");
            }
            report.put("dnsPassed", true).put("dnsMillis", SystemClock.elapsedRealtime() - dnsStart)
                .put("dnsServerHash", hash(dns.getAddress())).put("answerHash", hash(address.getAddress()));
            step = "tcp";
            try (Socket raw = new Socket()) {
                raw.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), port)); raw.setSoTimeout(12000);
                if (bindVpn) active.bindSocket(raw);
                raw.connect(new InetSocketAddress(address, 443), 12000);
                report.put("tcpConnectedElapsedMs", SystemClock.elapsedRealtime());
                step = "tls";
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, target.getHost(), 443, true)) {
                    javax.net.ssl.SSLParameters parameters = tls.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                    tls.setSoTimeout(16000);
                    report.put("tlsStartedElapsedMs", SystemClock.elapsedRealtime());
                    tls.startHandshake();
                    report.put("tlsCompletedElapsedMs", SystemClock.elapsedRealtime());
                    step = "http";
                    String path = target.getRawPath().isEmpty() ? "/" : target.getRawPath();
                    if (target.getRawQuery() != null) path += "?" + target.getRawQuery();
                    tls.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + target.getHost() + "\r\nUser-Agent: clash-verge/Platform/android\r\nConnection: keep-alive\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    tls.getOutputStream().flush();
                    java.io.BufferedInputStream stream = new java.io.BufferedInputStream(tls.getInputStream());
                    String status = line(stream); int code = Integer.parseInt(status.split(" ")[1]); report.put("http", code);
                    if (code != 200) throw new IllegalStateException("HTTP rejected");
                    int headers = 0; while (!line(stream).isEmpty()) if (++headers > 200) throw new IllegalStateException("headers budget exceeded");
                    if (stream.read() < 0) throw new IllegalStateException("missing body");
                    report.put("passed", true).put("networkAfter", networkSnapshot(connectivity, connectivity.getActiveNetwork()));
                    emit(token, command, report, started);
                    // 作者 long：保留同一个成功业务socket短窗口，核心观察端才能精确核对源端口和双向正流量，正文不落盘。
                    Thread.sleep(4000);
                    return;
                }
            }
        } catch (Exception error) {
            try {
                // 作者 long：只保存异常类型链，区分EOF、超时和证书错误，不把可能包含订阅URL的message写进报告。
                JSONArray causes = new JSONArray(); Throwable cause = error;
                for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause()) causes.put(cause.getClass().getSimpleName());
                report.put("passed", false).put("failedStep", step).put("failureType", error.getClass().getSimpleName()).put("failureChain", causes);
            } catch (Exception ignored) { }
            emit(token, command, report, started);
        } finally { runOnUiThread(this::finish); }
    }
    private void emit(String token, String command, JSONObject report, long started) {
        try { Log.i("KClashSwitchProbe", report.put("token", token).put("command", command).put("elapsedMillis", SystemClock.elapsedRealtime() - started).toString()); }
        catch (Exception ignored) { Log.e("KClashSwitchProbe", "acceptance report unavailable"); }
    }
    private static String line(java.io.InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 32768; i++) { int value = stream.read(); if (value < 0) throw new java.io.EOFException(); if (value == 10) return result.toString(); if (value != 13) result.append((char) value); }
        throw new IllegalStateException("line budget exceeded");
    }
    private static int u16(byte[] bytes, int offset) { return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); }
    private static int skipName(byte[] bytes, int offset) {
        while (offset < bytes.length) { int size = bytes[offset++] & 255; if (size == 0) return offset; if ((size & 192) == 192) return offset + 1; offset += size; }
        throw new IllegalStateException("invalid DNS name");
    }
    private static InetAddress resolve(InetAddress server, String host, int id, Network network) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
        data.writeShort(id); data.writeShort(0x100); data.writeShort(1); data.writeShort(0); data.writeShort(0); data.writeShort(0);
        for (String label : host.split("\\.")) { byte[] name = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII); data.writeByte(name.length); data.write(name); }
        data.writeByte(0); data.writeShort(1); data.writeShort(1);
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0)); if (network != null) network.bindSocket(socket); socket.connect(server, 53); socket.setSoTimeout(8000);
            byte[] query = bytes.toByteArray(); socket.send(new DatagramPacket(query, query.length));
            byte[] buffer = new byte[4096]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length); socket.receive(packet);
            byte[] reply = java.util.Arrays.copyOf(buffer, packet.getLength());
            if (reply.length < 12 || u16(reply, 0) != id || (u16(reply, 2) & 15) != 0) throw new IllegalStateException("DNS response rejected");
            int offset = 12;
            for (int i = 0; i < u16(reply, 4); i++) offset = skipName(reply, offset) + 4;
            for (int i = 0; i < u16(reply, 6); i++) {
                offset = skipName(reply, offset); int type = u16(reply, offset); int size = u16(reply, offset + 8); offset += 10;
                if (type == 1 && size == 4 && offset + 4 <= reply.length) return InetAddress.getByAddress(java.util.Arrays.copyOfRange(reply, offset, offset + 4));
                offset += size;
            }
            throw new IllegalStateException("DNS A answer missing");
        }
    }
}
