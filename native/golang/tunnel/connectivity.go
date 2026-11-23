package tunnel

import (
	"context"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func HealthCheck(name string) {
	p := tunnel.Proxies()[name]
	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)
		return
	}

	if group, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
		leaves := make([]C.Proxy, 0)
		collectManualHealthTargets(group, make(map[string]struct{}), make(map[string]struct{}), &leaves)
		var wg sync.WaitGroup
		for _, leaf := range leaves {
			wg.Add(1)
			go func(proxy C.Proxy) {
				defer wg.Done()
				runManualURLTest(proxy)
			}(leaf)
		}
		wg.Wait()
		return
	}

	runManualURLTest(p)
}

func collectManualHealthTargets(
	group outboundgroup.ProxyGroup,
	visitedGroups map[string]struct{},
	visitedProxies map[string]struct{},
	result *[]C.Proxy,
) {
	groupName := group.Name()
	if _, visited := visitedGroups[groupName]; visited {
		return
	}
	visitedGroups[groupName] = struct{}{}

	for _, proxy := range group.Proxies() {
		if child, ok := proxy.Adapter().(outboundgroup.ProxyGroup); ok {
			collectManualHealthTargets(child, visitedGroups, visitedProxies, result)
			continue
		}
		if proxy.Type() == C.Direct || proxy.Type() == C.Reject || proxy.Type() == C.Compatible || proxy.Type() == C.Pass {
			continue
		}
		if _, visited := visitedProxies[proxy.Name()]; visited {
			continue
		}
		visitedProxies[proxy.Name()] = struct{}{}
		*result = append(*result, proxy)
	}
}

func runManualURLTest(proxy C.Proxy) {
	err := manualURLTests.run(func() error {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_, err := proxy.URLTest(ctx, C.DefaultTestURL, nil)
		return err
	})
	if err != nil {
		log.Debugln("Manual URL test failed for %s: %s", proxy.Name(), err)
	}
}

func HealthCheckAll() {
	for _, group := range QueryProxyGroupNames(false) {
		go HealthCheck(group)
	}
}
