package core

import (
	"io"
	"net"
	"testing"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/constant"
	cp "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

type resettableTestOutbound struct {
	constant.ProxyAdapter
	resets int
}

func (o *resettableTestOutbound) ResetNetwork() { o.resets++ }

func resettableTestProxy(name string) (*resettableTestOutbound, constant.Proxy) {
	outbound := &resettableTestOutbound{ProxyAdapter: outbound.NewRejectWithOption(outbound.RejectOption{Name: name})}
	return outbound, adapter.NewProxy(outbound)
}

func compatibleProxyProvider(t *testing.T, name string, proxies []constant.Proxy) cp.ProxyProvider {
	t.Helper()
	healthCheck := provider.NewHealthCheck(proxies, "", 0, 0, true, nil)
	value, err := provider.NewCompatibleProvider(name, proxies, healthCheck)
	if err != nil {
		t.Fatal(err)
	}
	return value
}

func TestProxyOnlyTrafficLeavesOutADirectProxyOfAnyName(t *testing.T) {
	direct := outbound.NewDirectWithOption(outbound.DirectOption{Name: "直连"})
	reject := outbound.NewRejectWithOption(outbound.RejectOption{Name: "HK"})
	tunnel.UpdateProxies(map[string]constant.Proxy{
		"直连": adapter.NewProxy(direct),
		"HK": adapter.NewProxy(reject),
	}, nil)
	t.Cleanup(func() { tunnel.UpdateProxies(nil, nil) })

	for _, test := range []struct {
		outbound constant.ProxyAdapter
		proxied  int64
	}{
		{direct, 0},
		{reject, 5},
	} {
		statistic.DefaultManager.ResetStatistic()
		local, remote := net.Pipe()
		go func() { _, _ = io.Copy(io.Discard, remote) }()
		tracker := statistic.NewTCPTracker(
			outbound.NewConn(local, test.outbound),
			statistic.DefaultManager,
			&constant.Metadata{},
			nil,
			0,
			0,
			true,
		)

		if _, err := tracker.Write([]byte("hello")); err != nil {
			t.Fatalf("write through %s: %v", test.outbound.Name(), err)
		}
		_ = tracker.Close()
		_ = remote.Close()

		all, _ := statistic.DefaultManager.TotalTraffic(false)
		proxied, _ := statistic.DefaultManager.TotalTraffic(true)
		if all != 5 || proxied != test.proxied {
			t.Errorf("%s: total %d, proxied %d; want 5 and %d", test.outbound.Name(), all, proxied, test.proxied)
		}
	}
	statistic.DefaultManager.ResetStatistic()
}

func TestResetNetworkVisitsConfiguredAndProviderOutboundsOnce(t *testing.T) {
	shared, sharedProxy := resettableTestProxy("shared")
	first, firstProxy := resettableTestProxy("duplicate")
	second, secondProxy := resettableTestProxy("duplicate")
	providers := map[string]cp.ProxyProvider{
		"A": compatibleProxyProvider(t, "A", []constant.Proxy{sharedProxy, firstProxy}),
		"B": compatibleProxyProvider(t, "B", []constant.Proxy{secondProxy}),
	}
	tunnel.UpdateProxies(map[string]constant.Proxy{"shared": sharedProxy}, providers)
	t.Cleanup(func() { tunnel.UpdateProxies(nil, nil) })

	handleResetNetwork()

	if shared.resets != 1 || first.resets != 1 || second.resets != 1 {
		t.Fatalf("reset calls: shared=%d providerA=%d providerB=%d; want 1 each", shared.resets, first.resets, second.resets)
	}
}
