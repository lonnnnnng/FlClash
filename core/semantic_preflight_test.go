package core

import (
	"encoding/json"
	"errors"
	"os"
	"os/exec"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"
)

// 作者 long：预检全局故意不复用，每个case启动一次性子进程，与Android隔离服务的生命周期一致。
func TestSemanticPreflightIsolatedCases(t *testing.T) {
	cases := []string{"valid", "bad-node", "bad-group", "bad-rule", "bad-dns", "inline-rule", "missing-geo", "fakeip-memory", "bad-tls", "bad-tuic-port", "valid-tuic-addresses", "bad-geosite-data",
		"bad-anytls-security", "valid-anytls", "bad-anytls-padding", "bad-vless-security", "valid-vless", "bad-vless-xhttp", "bad-vless-range",
		"bad-ss-cipher", "valid-ss", "bad-ss-obfs", "bad-tun-dns", "bad-tun-range", "bad-tun-mac", "valid-tun", "bad-trojan-security", "valid-trojan"}
	for _, name := range cases {
		t.Run(name, func(t *testing.T) {
			cmd := exec.Command(os.Args[0], "-test.run=^TestSemanticPreflightChild$")
			cmd.Env = append(os.Environ(), "KCLASH_PREFLIGHT_CASE="+name)
			output, err := cmd.CombinedOutput()
			if err != nil {
				t.Fatalf("isolated %s failed: %v: %s", name, err, output)
			}
		})
	}
}
func TestSemanticPreflightChild(t *testing.T) {
	name := os.Getenv("KCLASH_PREFLIGHT_CASE")
	if name == "" {
		t.Skip("subprocess only")
	}
	valid := "mixed-port: 0\nproxies: [{name: sample, type: socks5, server: 127.0.0.1, port: 1080}]\nproxy-groups: [{name: choice, type: select, proxies: [sample]}]\nrules: ['MATCH,choice']\n"
	input := valid
	want := true
	switch name {
	case "bad-node":
		input = strings.Replace(input, "type: socks5", "type: invalid-protocol", 1)
		want = false
	case "bad-group":
		input = strings.Replace(input, "proxies: [sample]", "proxies: [missing]", 1)
		want = false
	case "bad-rule":
		input = strings.Replace(input, "MATCH,choice", "MATCH,missing", 1)
		want = false
	case "bad-dns":
		input += "dns: {enable: true, nameserver: ['invalid-protocol://bad']}\n"
		want = false
	case "inline-rule":
		input += "rule-providers: {local: {type: inline, behavior: classical, payload: ['UNSUPPORTED,secret.example']}}\n"
		want = false
	case "missing-geo", "bad-geosite-data":
		input = strings.Replace(input, "rules: ['MATCH,choice']", "rules: ['GEOSITE,cn,choice','MATCH,choice']", 1)
		want = false
	case "fakeip-memory":
		input += "profile: {store-fake-ip: true}\ndns: {enable: true, enhanced-mode: fake-ip, fake-ip-range: 198.18.0.1/16, nameserver: [1.1.1.1]}\n"
	case "bad-tuic-port":
		input += "tuic-server: {enable: true, listen: '127.0.0.1:99999'}\n"
		want = false
	case "valid-tuic-addresses":
		input += "tuic-server: {enable: true, listen: '127.0.0.1:0,[::1]:0'}\n"
	case "bad-tls":
		input += "tls: {certificate: invalid-certificate, private-key: invalid-private-key}\n"
		want = false
	case "bad-anytls-security":
		input += "listeners: [{name: inbound, type: anytls}]\n"
		want = false
	case "valid-anytls":
		input += "listeners: [{name: inbound, type: anytls, allow-insecure: true}]\n"
	case "bad-anytls-padding":
		input += "listeners: [{name: inbound, type: anytls, allow-insecure: true, padding-scheme: 'stop=garbage'}]\n"
		want = false
	case "bad-vless-security":
		input += "listeners: [{name: inbound, type: vless, users: []}]\n"
		want = false
	case "valid-vless":
		input += "listeners: [{name: inbound, type: vless, users: [], allow-insecure: true, xhttp-config: {mode: auto}}]\n"
	case "bad-vless-xhttp":
		input += "listeners: [{name: inbound, type: vless, users: [], allow-insecure: true, xhttp-config: {mode: invalid}}]\n"
		want = false
	case "bad-vless-range":
		input += "listeners: [{name: inbound, type: vless, users: [], allow-insecure: true, xhttp-config: {mode: auto, sc-max-buffered-posts: garbage}}]\n"
		want = false
	case "bad-ss-cipher":
		input += "listeners: [{name: inbound, type: shadowsocks, password: test, cipher: invalid}]\n"
		want = false
	case "valid-ss":
		input += "listeners: [{name: inbound, type: shadowsocks, password: test, cipher: none}]\n"
	case "bad-ss-obfs":
		input += "listeners: [{name: inbound, type: shadowsocks, password: test, cipher: none, simple-obfs: {enable: true, mode: invalid}}]\n"
		want = false
	case "bad-tun-dns":
		input += "tun: {enable: true, dns-mode: invalid}\n"
		want = false
	case "bad-tun-range":
		input += "tun: {enable: true, include-uid-range: ['1-20']}\n"
		want = false
	case "bad-tun-mac":
		input += "tun: {enable: true, include-mac-address: [invalid]}\n"
		want = false
	case "valid-tun":
		input += "tun: {enable: true, dns-mode: native, dns-hijack: ['any:53'], include-uid-range: ['1:20']}\n"
	case "bad-trojan-security":
		input += "listeners: [{name: inbound, type: trojan, users: []}]\n"
		want = false
	case "valid-trojan":
		input += "listeners: [{name: inbound, type: trojan, users: [], allow-insecure: true}]\n"
	}
	result := config.ParseReadOnly([]byte(input), func(string) ([]byte, error) {
		if name == "bad-geosite-data" {
			return []byte("invalid binary"), nil
		}
		return nil, errors.New("unprovided")
	})
	if result.Passed != want {
		safe, _ := json.Marshal(result)
		t.Fatalf("case %s result %s", name, safe)
	}
	if name == "bad-geosite-data" && result.Code != "resource_invalid" {
		t.Fatalf("corrupt resource not classified: %+v", result)
	}
	if name == "inline-rule" && result.Code != "invalid_inline_rule" {
		t.Fatalf("inline invalid rule was not preserved: %+v", result)
	}
	if name == "missing-geo" && result.Code != "resource_unavailable" {
		t.Fatalf("missing resource did not surface: %+v", result)
	}
	if strings.Contains(result.Code, "secret") || strings.Contains(result.Section, "secret") {
		t.Fatal("raw content in safe result")
	}
	if _, err := os.Stat("/kclash-validation/cache.db"); !os.IsNotExist(err) {
		t.Fatal("validation cache unexpectedly exists")
	}
}
