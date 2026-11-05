package core

import (
	"context"
	"sync/atomic"
	"time"

	"github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	delayTestConcurrency    = 50
	defaultTestURL          = "https://www.gstatic.com/generate_204"
	defaultDelayTestTimeout = 5 * time.Second
)

var (
	delayTestSlots     = make(slots, delayTestConcurrency)
	anyDelayTestStatus utils.IntRanges[uint16]

	testURL                 atomic.Pointer[string]
	missingDelayTestProxyAt atomic.Int64
)

func setTestURL(url string) {
	if url == "" {
		return
	}
	constant.DefaultTestURL = url
	testURL.Store(&url)
}

func currentTestURL() string {
	if url := testURL.Load(); url != nil && *url != "" {
		return *url
	}
	return defaultTestURL
}

const (
	delayTimedOut int32 = -1
	delayFailed   int32 = -2
)

func delayValue(delay uint16, timedOut bool) int32 {
	switch {
	case delay > 0:
		return int32(delay)
	case timedOut:
		return delayTimedOut
	default:
		return delayFailed
	}
}

// A name the tunnel does not know is what an apply that fell back to the
// default config looks like: every node of the profile fails its test. Say so
// once a second, not once per node, so the log names the real failure.
func reportMissingDelayTestProxy(name string) {
	now := time.Now().UnixNano()
	last := missingDelayTestProxyAt.Load()
	if last != 0 && now-last < int64(time.Second) {
		return
	}
	if !missingDelayTestProxyAt.CompareAndSwap(last, now) {
		return
	}
	logError("delay test: %q is not part of the applied config", name)
}

func configuredDelayTestTimeout(proxy constant.Proxy, url string, fallback time.Duration) time.Duration {
	providerName := proxy.ProxyInfo().ProviderName
	if providerName == "" {
		return fallback
	}
	if pp, ok := tunnel.ProvidersSnapshot()[providerName]; ok {
		if configured, ok := provider.ConfiguredTestTimeout(pp, url); ok && configured > fallback {
			return configured
		}
	}
	return fallback
}

// A nil answer means the test never got a slot, which says nothing about the proxy.
func handleTestDelay(params *TestDelayParams) *Delay {
	url := params.TestUrl
	if url == "" {
		url = currentTestURL()
	}
	result := &Delay{Name: params.ProxyName, Url: url, Value: delayFailed}

	proxy := lookupProxy(params.ProxyName)
	if proxy == nil {
		reportMissingDelayTestProxy(params.ProxyName)
		return result
	}

	timeout := timeoutFromMillis(params.Timeout, defaultDelayTestTimeout)
	timeout = configuredDelayTestTimeout(proxy, url, timeout)
	if !delayTestSlots.acquireWithin(context.Background(), timeout) {
		return nil
	}
	defer delayTestSlots.release()

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	delay, err := proxy.URLTest(ctx, url, anyDelayTestStatus)
	if err != nil {
		delay = 0
	}
	result.Value = delayValue(delay, ctx.Err() != nil)
	return result
}
