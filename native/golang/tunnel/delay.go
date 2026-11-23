package tunnel

import (
	"context"
	"time"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// Per-proxy delay test, mirroring FlClash's handleTestDelay and BettBox's
// testProxyDelay: measure one proxy by name through the core's URL test.
// The group HealthCheck cannot test a single node (it only accepts groups),
// which is why the old speed test left single-node taps and missing groups
// blank. Every call returns a result: the delay in ms, or -2 when the proxy
// is missing, the test times out, or it fails.
const (
	delayTestConcurrency      = 10
	defaultDelayTestTimeoutMs = 5000
)

var delayTestSlots = make(chan struct{}, delayTestConcurrency)

func TestProxyDelay(name, testURL string, timeoutMs int64) int32 {
	if testURL == "" {
		testURL = constant.DefaultTestURL
	}
	if timeoutMs <= 0 {
		timeoutMs = defaultDelayTestTimeoutMs
	}
	proxy := tunnel.Proxies()[name]
	if proxy == nil {
		log.Warnln("Delay test for `%s`: not found", name)
		return -2
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	select {
	case delayTestSlots <- struct{}{}:
		defer func() { <-delayTestSlots }()
	case <-time.After(timeout):
		return -2
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	// Empty expected status: any HTTP status counts (mihomo treats a nil
	// range as satisfied), same as FlClash and BettBox.
	expectedStatus, _ := utils.NewUnsignedRanges[uint16]("")
	delay, err := proxy.URLTest(ctx, testURL, expectedStatus)
	if err != nil || delay == 0 {
		return -2
	}
	return int32(delay)
}
