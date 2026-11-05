package core

import (
	"encoding/json"
	"strings"
	"testing"
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
