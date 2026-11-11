package core

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/component/dialer"
)

func TestDialDiagnosticPreservesOnlyRequestedPortAndSafeErrorChain(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	capture.record("[TCP] dial secret-node 198.18.0.1:43211 --> secret-target.example:443 error: dns resolve failed")
	capture.record("[TCP] dial secret-node 198.18.0.1:43210 --> secret-target.example:443 error: dns resolve failed: secret-node.example: read udp 10.0.0.2:1234->1.1.1.1:53: i/o timeout")
	got := capture.snapshot(43210, true)
	if len(got) != 1 || !got[0].Failed || len(got[0].ErrorChain) != 3 || len(got[0].EndpointHashes) != 3 {
		t.Fatalf("diagnostics=%+v", got)
	}
	encoded, _ := json.Marshal(got)
	for _, secret := range []string{"secret-node", "secret-target", "10.0.0.2", "1.1.1.1", "43210"} {
		if strings.Contains(string(encoded), secret) {
			t.Fatalf("secret leaked: %s", secret)
		}
	}
	capture.record("[TCP] dial 198.18.0.1:43210 --> target:443 error: connection refused")
	if len(capture.snapshot(43210, false)) != 1 {
		t.Fatal("capture continued after finish")
	}
}

func TestLookupDiagnosticUsesStructuredPortAndNeverKeepsEndpointOrRawError(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	value := dialer.LookupDiagnostic{SourcePort: 43211, Host: "secret-node.example", Network: "tcp4", ResolverRole: "proxy",
		IPv6Disabled: true, Duration: 5 * time.Second, Err: fmt.Errorf("all DNS requests failed at secret-upstream.example: %w", context.DeadlineExceeded), ContextErr: context.DeadlineExceeded}
	capture.recordLookup(value)
	if len(capture.snapshot(43210, false)) != 0 {
		t.Fatal("wrong source port retained")
	}
	value.SourcePort = 43210
	capture.recordLookup(value)
	got := capture.snapshot(43210, true)
	if len(got) != 1 || got[0].Kind != "lookup" || got[0].LookupNetwork != "tcp4" || !got[0].IPv6Disabled || got[0].DurationMillis != 5000 {
		t.Fatal("structured lookup missing")
	}
	encoded, _ := json.Marshal(got)
	for _, secret := range []string{"secret-node", "secret-upstream", "43210", "all DNS requests failed"} {
		if strings.Contains(string(encoded), secret) {
			t.Fatal("raw lookup data leaked")
		}
	}
	if !strings.Contains(string(encoded), "dns-all-failed") || !strings.Contains(string(encoded), "deadline") {
		t.Fatal("safe error categories lost")
	}
	capture.recordLookup(value)
	if len(capture.snapshot(43210, false)) != 1 {
		t.Fatal("finished lookup still captured")
	}
}

func TestNodeDialDiagnosticHashesExactEndpointAndDoesNotClaimWireTraffic(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	capture.recordLookup(dialer.LookupDiagnostic{Kind: "node-dial", SourcePort: 43210, Network: "tcp",
		DialEndpoint: "192.0.2.1:443", DialFamily: "ipv4", Duration: 3 * time.Millisecond})
	got := capture.snapshot(43210, true)
	if len(got) != 1 || got[0].Kind != "node-dial" || got[0].DialEndpointHash == "" || got[0].DurationMillis != 3 || got[0].Failed {
		t.Fatal("node dial summary missing")
	}
	data, _ := json.Marshal(got)
	if strings.Contains(string(data), "192.0.2.1") || strings.Contains(string(data), ":443") {
		t.Fatal("node endpoint leaked")
	}
}

func TestDialDiagnosticIsBoundedAndNewRequestReplacesOldCapture(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	for range 100 {
		capture.record("[TCP] 198.18.0.1:43210 --> target:443 match DIRECT")
	}
	if len(capture.snapshot(43210, false)) != 32 {
		t.Fatal("unbounded diagnostics")
	}
	capture.begin(43211)
	if len(capture.snapshot(43210, false)) != 0 || len(capture.snapshot(43211, false)) != 0 {
		t.Fatal("old diagnostics leaked into new request")
	}
}

// 作者 long：多地址并发时先到的connect成功未必被选中；缓存满后也必须留下最终端点并公开省略数量。
func TestDialDiagnosticRetainsWinnerAfterCandidateAndRecordLimits(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	for range 25 {
		capture.recordLookup(dialer.LookupDiagnostic{Kind: "node-dial", SourcePort: 43210, DialEndpoint: "192.0.2.1:443", Network: "tcp4", DialFamily: "ipv4"})
	}
	for range 26 {
		capture.record("[TCP] 198.18.0.1:43210 --> target:443 match DIRECT")
	}
	capture.recordLookup(dialer.LookupDiagnostic{Kind: "node-selected", SourcePort: 43210, DialEndpoint: "192.0.2.2:443", Network: "tcp4", DialFamily: "ipv4"})
	got := capture.snapshot(43210, true)
	if len(got) != 32 || got[31].Kind != "node-selected" || got[31].OmittedDialAttempts != 17 || got[31].DroppedRecords != 3 {
		t.Fatalf("winner or accounting missing: %+v", got)
	}
	if got[31].DialEndpointHash == got[0].DialEndpointHash {
		t.Fatal("candidate substituted for selected endpoint")
	}
	capture.recordLookup(dialer.LookupDiagnostic{Kind: "node-selected", SourcePort: 43210})
	if len(capture.snapshot(43210, false)) != 32 {
		t.Fatal("finished capture changed")
	}
	capture.begin(43211)
	capture.recordLookup(dialer.LookupDiagnostic{Kind: "node-selected", SourcePort: 43210})
	if len(capture.snapshot(43211, false)) != 0 {
		t.Fatal("previous port leaked")
	}
}

func TestDialDiagnosticBoundsWorkForOneHugeError(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	capture.record("[TCP] 198.18.0.1:43210 --> target:443 error: " + strings.Repeat("read udp: node.example: i/o timeout: ", 10000))
	got := capture.snapshot(43210, true)
	if len(got) != 1 || len(got[0].ErrorChain) > 8 || len(got[0].EndpointHashes) > 8 {
		t.Fatalf("unbounded entry: %+v", got)
	}
}

// 作者 long：协议诊断只保留固定阶段、计数和错误分类，不让上游错误文本/任意阶段名进入宿主缓存。
func TestProtocolDiagnosticKeepsPartialResponseCountsAndRejectsPrivateFields(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	value := dialer.LookupDiagnostic{Kind: "protocol", SourcePort: 43210, ProtocolStage: "private-stage", ProtocolState: "end"}
	capture.recordLookup(value)
	if len(capture.snapshot(43210, false)) != 0 {
		t.Fatal("unlisted stage retained")
	}
	value.ProtocolStage = "vless-response"
	value.StreamBytes = 3
	value.ExpectedBytes = 6
	value.Err = fmt.Errorf("private-node.example: %w", io.EOF)
	capture.recordLookup(value)
	got := capture.snapshot(43210, true)
	if len(got) != 1 || !got[0].Failed || got[0].StreamBytes != 3 || got[0].ExpectedBytes != 6 {
		t.Fatal("partial response missing")
	}
	data, _ := json.Marshal(got)
	if strings.Contains(string(data), "private-") || !strings.Contains(string(data), "transport-eof") {
		t.Fatal("unsafe or missing error category")
	}
}
