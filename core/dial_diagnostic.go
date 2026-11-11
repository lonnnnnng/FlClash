package core

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/resolver"
)

type dialDiagnostic struct {
	Kind              string     `json:"kind,omitempty"`
	AtMillis          int64      `json:"atMillis"`
	Failed            bool       `json:"failed"`
	Categories        []string   `json:"categories"`
	ErrorChain        [][]string `json:"errorChain"`
	EndpointHashes    []string   `json:"endpointHashes"`
	LookupHostHash    string     `json:"lookupHostHash,omitempty"`
	LookupNetwork     string     `json:"lookupNetwork,omitempty"`
	ResolverRole      string     `json:"resolverRole,omitempty"`
	IPv6Disabled      bool       `json:"ipv6Disabled"`
	Addresses         int        `json:"addresses"`
	IPv4Addresses     int        `json:"ipv4Addresses"`
	IPv6Addresses     int        `json:"ipv6Addresses"`
	DurationMillis    int64      `json:"durationMillis,omitempty"`
	ContextCategories []string   `json:"contextCategories,omitempty"`
}

type dialDiagnosticCapture struct {
	port       atomic.Int64
	mu         sync.Mutex
	sourcePort int
	records    []dialDiagnostic
}

var activeDialDiagnostic dialDiagnosticCapture
var diagnosticHostPattern = regexp.MustCompile(`(?:[A-Za-z0-9-]+\.)+[A-Za-z0-9-]+`)

func diagnosticCategories(text string) []string {
	text = strings.ToLower(text)
	result := []string{}
	for _, item := range []struct{ word, category string }{
		{"dns resolve failed", "node-dns"}, {"no such host", "no-host"},
		{"all dns requests failed", "dns-all-failed"}, {"couldn't find ip", "ip-not-found"},
		{"no ip address", "no-ip"}, {"ipv6 disabled", "ipv6-disabled"}, {"ip version error", "ip-version"},
		{"context deadline exceeded", "deadline"}, {"i/o timeout", "io-timeout"},
		{"read udp", "read-udp"}, {"read tcp", "read-tcp"},
		{"dial udp", "dial-udp"}, {"dial tcp", "dial-tcp"},
		{"context canceled", "canceled"}, {"connection refused", "refused"},
		{"network is unreachable", "unreachable"}, {"socket protection rejected", "protect-rejected"},
		{"socket protection unavailable", "protect-unavailable"}, {"loop", "loop"},
	} {
		if strings.Contains(text, item.word) {
			result = append(result, item.category)
		}
	}
	return result
}

func diagnosticErrorCategories(err error) []string {
	if err == nil {
		return []string{}
	}
	result := diagnosticCategories(err.Error())
	for _, item := range []struct {
		target   error
		category string
	}{
		{context.DeadlineExceeded, "deadline"}, {context.Canceled, "canceled"},
		{io.EOF, "transport-eof"}, {io.ErrUnexpectedEOF, "transport-unexpected-eof"},
		{resolver.ErrIPNotFound, "ip-not-found"}, {resolver.ErrIPv6Disabled, "ipv6-disabled"}, {resolver.ErrIPVersion, "ip-version"},
	} {
		if errors.Is(err, item.target) {
			present := false
			for _, existing := range result {
				present = present || existing == item.category
			}
			if !present {
				result = append(result, item.category)
			}
		}
	}
	var timeout net.Error
	if errors.As(err, &timeout) && timeout.Timeout() {
		result = append(result, "timeout")
	}
	if len(result) == 0 {
		result = append(result, "unclassified")
	}
	return result
}

func (capture *dialDiagnosticCapture) recordLookup(value dialer.LookupDiagnostic) {
	if value.SourcePort <= 0 || capture.port.Load() != int64(value.SourcePort) {
		return
	}
	capture.mu.Lock()
	defer capture.mu.Unlock()
	if capture.sourcePort != value.SourcePort || capture.port.Load() != int64(value.SourcePort) || len(capture.records) >= 16 {
		return
	}
	sum := sha256.Sum256([]byte(strings.TrimSuffix(strings.ToLower(value.Host), ".")))
	network := value.Network
	if network != "tcp" && network != "tcp4" && network != "tcp6" && network != "udp" && network != "udp4" && network != "udp6" {
		network = "unknown"
	}
	role := value.ResolverRole
	if role != "proxy" && role != "custom" {
		role = "unknown"
	}
	// 作者 long：结构化hook和源端口context绑定后才能认定为本次lookup；不从日志中的任意域名猜叶节点。
	capture.records = append(capture.records, dialDiagnostic{Kind: "lookup", AtMillis: bootstrapMillis(), Failed: value.Err != nil,
		Categories: diagnosticErrorCategories(value.Err), ErrorChain: [][]string{}, EndpointHashes: []string{},
		LookupHostHash: hex.EncodeToString(sum[:]), LookupNetwork: network, ResolverRole: role, IPv6Disabled: value.IPv6Disabled,
		Addresses: value.Addresses, IPv4Addresses: value.IPv4Addresses, IPv6Addresses: value.IPv6Addresses,
		DurationMillis: value.Duration.Milliseconds(), ContextCategories: diagnosticErrorCategories(value.ContextErr)})
}

func init() {
	dialer.SetLookupDiagnosticObserver(func(port int) bool { return port > 0 && activeDialDiagnostic.port.Load() == int64(port) }, activeDialDiagnostic.recordLookup)
}

func (capture *dialDiagnosticCapture) begin(port int) {
	capture.mu.Lock()
	defer capture.mu.Unlock()
	capture.sourcePort, capture.records = port, nil
	capture.port.Store(int64(port))
}

func (capture *dialDiagnosticCapture) record(payload string) {
	port := capture.port.Load()
	// 作者 long：条数限制之外还要限制单条工作量，避免巨大远端错误占用日志泵或把哈希数组扩成无界缓存。
	if len(payload) > 8192 {
		payload = payload[:8192]
	}
	if port == 0 || !strings.Contains(payload, "[TCP]") || !strings.Contains(payload, ":"+strconv.FormatInt(port, 10)+" --> ") {
		return
	}
	capture.mu.Lock()
	defer capture.mu.Unlock()
	if capture.sourcePort != int(port) || capture.port.Load() != port || len(capture.records) >= 16 {
		return
	}
	// 作者 long：在核心事件进入可丢弃队列之前，只保留指定源端口的类别和哈希；原始URL、节点名及嵌套错误文本绝不进入缓存。
	errorText := ""
	if index := strings.Index(payload, " error: "); index >= 0 {
		errorText = payload[index+8:]
	}
	record := dialDiagnostic{AtMillis: bootstrapMillis(), Failed: errorText != "", Categories: diagnosticCategories(errorText), ErrorChain: [][]string{}, EndpointHashes: []string{}}
	for _, part := range strings.SplitN(errorText, ": ", 9) {
		if len(record.ErrorChain) >= 8 {
			break
		}
		if categories := diagnosticCategories(part); len(categories) > 0 {
			record.ErrorChain = append(record.ErrorChain, categories)
		}
	}
	seen := map[string]bool{}
	for _, host := range diagnosticHostPattern.FindAllString(errorText, 8) {
		sum := sha256.Sum256([]byte(strings.ToLower(host)))
		hash := hex.EncodeToString(sum[:])
		if !seen[hash] {
			seen[hash] = true
			record.EndpointHashes = append(record.EndpointHashes, hash)
		}
	}
	capture.records = append(capture.records, record)
}

func (capture *dialDiagnosticCapture) snapshot(port int, finish bool) []dialDiagnostic {
	capture.mu.Lock()
	defer capture.mu.Unlock()
	if capture.sourcePort != port {
		return []dialDiagnostic{}
	}
	if finish {
		capture.port.Store(0)
	}
	return append([]dialDiagnostic{}, capture.records...)
}

func BeginDialDiagnostic(sourcePort int) {
	if sourcePort > 0 && sourcePort <= 65535 {
		activeDialDiagnostic.begin(sourcePort)
	}
}

func DialDiagnosticSnapshot(sourcePort int, finish bool) string {
	data, _ := json.Marshal(activeDialDiagnostic.snapshot(sourcePort, finish))
	return string(data)
}
