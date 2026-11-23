package tunnel

import (
	"sync"
	"sync/atomic"
	"testing"
)

func TestManualURLTestLimiterCapsConcurrentChecks(t *testing.T) {
	const limit = 10
	limiter := newManualURLTestLimiter(limit)
	release := make(chan struct{})
	started := make(chan struct{}, 30)
	var active atomic.Int32
	var peak atomic.Int32
	var wg sync.WaitGroup

	for i := 0; i < 30; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = limiter.run(func() error {
				current := active.Add(1)
				for previous := peak.Load(); current > previous && !peak.CompareAndSwap(previous, current); previous = peak.Load() {
				}
				started <- struct{}{}
				<-release
				active.Add(-1)
				return nil
			})
		}()
	}

	for i := 0; i < limit; i++ {
		<-started
	}
	close(release)
	wg.Wait()

	if got := peak.Load(); got != limit {
		t.Fatalf("peak concurrent checks = %d, want %d", got, limit)
	}
}
