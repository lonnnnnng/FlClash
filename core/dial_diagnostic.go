package core

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
)

type dialDiagnostic struct {
	AtMillis       int64      `json:"atMillis"`
	Failed         bool       `json:"failed"`
	Categories     []string   `json:"categories"`
	ErrorChain     [][]string `json:"errorChain"`
	EndpointHashes []string   `json:"endpointHashes"`
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
