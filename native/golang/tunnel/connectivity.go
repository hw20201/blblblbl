package tunnel

import (
	"sync"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// healthCheckSem caps concurrently running health checks. Each check fans
// out one goroutine per provider and dials them, so an unbounded burst of
// taps (or a test-all sweep) would stack network checks. 8 is plenty for
// human-rate taps; excess checks are skipped and logged, and the UI keeps
// the last known delays.
var healthCheckSem = make(chan struct{}, 8)

func HealthCheck(name string) {
	select {
	case healthCheckSem <- struct{}{}:
		defer func() { <-healthCheckSem }()
	default:
		log.Warnln("Request health check for `%s`: skipped, too many running", name)

		return
	}

	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	for _, pr := range g.Providers() {
		wg.Add(1)

		go func(provider provider.ProxyProvider) {
			provider.HealthCheck()

			wg.Done()
		}(pr)
	}

	wg.Wait()
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}
