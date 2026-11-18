package tunnel

import (
	"fmt"
	"testing"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	C "github.com/metacubex/mihomo/constant"
)

// benchmarkProxies builds n real proxies (Direct adapters, no network use)
// with names shaped like the ones users see, so the subtitle regex matches.
func benchmarkProxies(n int) []C.Proxy {
	proxies := make([]C.Proxy, 0, n)
	for i := 0; i < n; i++ {
		name := fmt.Sprintf("node-%03d [HK x1.5]", i)
		proxies = append(proxies, adapter.NewProxy(outbound.NewDirectWithOption(outbound.DirectOption{Name: name})))
	}
	return proxies
}

func BenchmarkConvertProxies(b *testing.B) {
	proxies := benchmarkProxies(200)
	pattern, err := regexp2.Compile(`\[[^\]]+\]`, regexp2.IgnoreCase|regexp2.Compiled)
	if err != nil {
		b.Fatal(err)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_ = convertProxies(proxies, pattern)
	}
}

func BenchmarkConvertProxiesNoPattern(b *testing.B) {
	proxies := benchmarkProxies(200)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_ = convertProxies(proxies, nil)
	}
}
