package core

import (
	"context"
	"encoding/json"
	"fmt"
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

func TestDialDiagnosticIsBoundedAndNewRequestReplacesOldCapture(t *testing.T) {
	var capture dialDiagnosticCapture
	capture.begin(43210)
	for range 100 {
		capture.record("[TCP] 198.18.0.1:43210 --> target:443 match DIRECT")
	}
	if len(capture.snapshot(43210, false)) != 16 {
		t.Fatal("unbounded diagnostics")
	}
	capture.begin(43211)
	if len(capture.snapshot(43210, false)) != 0 || len(capture.snapshot(43211, false)) != 0 {
		t.Fatal("old diagnostics leaked into new request")
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
