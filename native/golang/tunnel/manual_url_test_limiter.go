package tunnel


const manualURLTestLimit = 10

type manualURLTestLimiter struct {
	slots chan struct{}
}

func newManualURLTestLimiter(limit int) *manualURLTestLimiter {
	if limit < 1 {
		panic("manual URL test limit must be positive")
	}
	return &manualURLTestLimiter{slots: make(chan struct{}, limit)}
}

func (l *manualURLTestLimiter) run(test func() error) error {
	l.slots <- struct{}{}
	defer func() { <-l.slots }()
	return test()
}

var manualURLTests = newManualURLTestLimiter(manualURLTestLimit)
