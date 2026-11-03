package core

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/component/resource"
	cp "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules"
	rp "github.com/metacubex/mihomo/rules/provider"
	"github.com/metacubex/mihomo/tunnel"
)

func editorRule(t *testing.T, format cp.RuleFormat, data []byte) (string, ScopedProviderUpdate) {
	t.Helper()
	recordRoutes(t)
	path := filepath.Join(t.TempDir(), "provider.rules")
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	rp.SetTunnel(tunnel.Tunnel)
	p := rp.NewRuleSetProvider("editor", cp.Domain, format, 0, resource.NewFileVehicle(path), nil, nil, rules.ParseRule).(*rp.RuleSetProvider)
	withTunnelProviders(t, nil, map[string]cp.RuleProvider{"editor": p})
	t.Cleanup(func() { p.Close() })
	epoch, version := routeStamp()
	return path, ScopedProviderUpdate{Name: "editor", CoreEpoch: epoch, RouteVersion: version}
}

func TestProviderEditorTextSaveRejectsStaleAndKeepsInvalidBytes(t *testing.T) {
	before := []byte("payload:\n  - example.com\n")
	path, scope := editorRule(t, cp.YamlRule, before)
	date := time.Unix(1600000000, 0)
	if err := os.Chtimes(path, date, date); err != nil {
		t.Fatal(err)
	}
	doc, err := handleReadExternalProviderScoped(scope)
	if err != nil || doc.Kind != "text" || doc.Content != string(before) {
		t.Fatalf("read: %v", err)
	}
	save := ScopedProviderSideLoad{ScopedProviderUpdate: scope, Digest: doc.Digest, Data: "payload: [invalid"}
	if err = handleSideLoadExternalProviderScoped(save); err == nil {
		t.Fatal("invalid content accepted")
	}
	data, _ := os.ReadFile(path)
	info, _ := os.Stat(path)
	if !bytes.Equal(data, before) || !info.ModTime().Equal(date) {
		t.Fatal("failed save changed original file")
	}
	save.Data = "payload:\n  - example.org\n"
	save.Digest = "missing"
	if err = handleSideLoadExternalProviderScoped(save); asMethodError(err).Code != "provider_stale" {
		t.Fatal("stale digest accepted")
	}
	save.Digest = doc.Digest
	if err = handleSideLoadExternalProviderScoped(save); err != nil {
		t.Fatal(err)
	}
	data, _ = os.ReadFile(path)
	if string(data) != save.Data {
		t.Fatal("save did not persist content")
	}
	scope.CoreEpoch++
	if _, err = handleReadExternalProviderScoped(scope); asMethodError(err).Code != "provider_stale" {
		t.Fatal("stale scope accepted")
	}
}

func TestProviderEditorMrsBinaryMissingAndBoundedReads(t *testing.T) {
	var encoded bytes.Buffer
	if err := rp.ConvertToMrs([]byte("example.com\n"), cp.Domain, cp.TextRule, &encoded); err != nil {
		t.Fatal(err)
	}
	path, scope := editorRule(t, cp.MrsRule, encoded.Bytes())
	doc, err := handleReadExternalProviderScoped(scope)
	if err != nil || doc.Kind != "mrs" || !strings.Contains(doc.Content, "example.com") {
		t.Fatalf("mrs: %v", err)
	}
	if err = os.WriteFile(path, []byte{1, 0, 255}, 0600); err != nil {
		t.Fatal(err)
	}
	doc, err = handleReadExternalProviderScoped(scope)
	if err != nil || doc.Kind != "binary" || doc.Content != "" {
		t.Fatalf("binary: %v", err)
	}
	if err = os.Remove(path); err != nil {
		t.Fatal(err)
	}
	doc, err = handleReadExternalProviderScoped(scope)
	if err != nil || doc.Kind != "text" || doc.Digest != "missing" {
		t.Fatalf("missing: %v", err)
	}
	if err = os.WriteFile(path, bytes.Repeat([]byte{'a'}, providerDocumentLimit+1), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err = handleReadExternalProviderScoped(scope); err == nil {
		t.Fatal("oversize file accepted")
	}
}

type editorUpdateProvider struct {
	fakeProxyProvider
	update func() error
}

func (p *editorUpdateProvider) Update() error { return p.update() }

func TestProviderBatchFreezesScopeAndDoesNotHoldConfigLock(t *testing.T) {
	recordRoutes(t)
	entered := make(chan struct{}, 2)
	release := make(chan struct{})
	makeProvider := func(name string, fail bool) *editorUpdateProvider {
		return &editorUpdateProvider{fakeProxyProvider: fakeProxyProvider{name: name, vehicle: cp.HTTP}, update: func() error {
			entered <- struct{}{}
			<-release
			if fail {
				return errors.New("fixture-secret")
			}
			return nil
		}}
	}
	withTunnelProviders(t, map[string]cp.ProxyProvider{"a": makeProvider("a", false), "b": makeProvider("b", true)}, nil)
	epoch, version := routeStamp()
	params := ScopedProvidersUpdate{ScopedProviderUpdate: ScopedProviderUpdate{CoreEpoch: epoch, RouteVersion: version}, Names: []string{"a", "b"}}
	done := make(chan []ProviderUpdateResult, 1)
	go func() {
		result, err := handleUpdateExternalProvidersScoped(params)
		if err != nil {
			done <- nil
		} else {
			done <- result
		}
	}()
	defer close(release)
	for range 2 {
		select {
		case <-entered:
		case <-time.After(3 * time.Second):
			t.Fatal("batch did not run concurrently")
		}
	}
	if !configMu.TryLock() {
		t.Fatal("network holds config lock")
	}
	configMu.Unlock()
	release <- struct{}{}
	release <- struct{}{}
	select {
	case result := <-done:
		if len(result) != 2 || result[0].Name != "a" || result[0].Code != "" || result[1].Code == "" || strings.Contains(result[1].Code, "secret") {
			t.Fatalf("bad result: %+v", result)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("batch did not finish")
	}
	params.Names = []string{"a", "a"}
	if _, err := handleUpdateExternalProvidersScoped(params); err == nil {
		t.Fatal("duplicate batch accepted")
	}
}
