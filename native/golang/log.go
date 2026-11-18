package main

//#include "bridge.h"
import "C"

import (
	"context"
	"reflect"
	"strings"
	"sync"
	"time"
	"unsafe"

	"github.com/metacubex/mihomo/log"
)

type message struct {
	Level   string `json:"level"`
	Message string `json:"message"`
	Time    int64  `json:"time"`
}

func init() {
	go func() {
		sub := log.Subscribe()
		defer log.UnSubscribe(sub)

		for msg := range sub {
			cPayload := C.CString(msg.Payload)

			switch msg.LogLevel {
			case log.INFO:
				C.log_info(cPayload)
			case log.ERROR:
				C.log_error(cPayload)
			case log.WARNING:
				C.log_warn(cPayload)
			case log.DEBUG:
				C.log_debug(cPayload)
			case log.SILENT:
				C.log_verbose(cPayload)
			}
		}
	}()
}

// Active logcat subscriptions, keyed by the JNI global reference Kotlin
// passed in. The C side only ends a subscription by returning non-zero from
// logcat_received (which happens when the Kotlin callback throws), so
// unsubscribeLogcat is the explicit way for Kotlin to stop one, e.g. when
// the log viewer goes away. The JNI reference is released exactly once.
var (
	logcatMu   sync.Mutex
	logcatSubs = map[unsafe.Pointer]context.CancelFunc{}
)

//export subscribeLogcat
func subscribeLogcat(remote unsafe.Pointer) {
	ctx, cancel := context.WithCancel(context.Background())

	logcatMu.Lock()
	if old, ok := logcatSubs[remote]; ok {
		// A second subscribe for the same reference replaces the first;
		// stop the old one instead of fanning out twice.
		old()
	}
	logcatSubs[remote] = cancel
	logcatMu.Unlock()

	go func(remote unsafe.Pointer) {
		var releaseOnce sync.Once
		release := func() {
			releaseOnce.Do(func() { C.release_object(remote) })
		}
		defer func() {
			// Only remove the entry if it is still this subscription:
			// a re-subscribe for the same reference must not lose its
			// entry to this goroutine's cleanup.
			logcatMu.Lock()
			if c, ok := logcatSubs[remote]; ok && reflect.ValueOf(c).Pointer() == reflect.ValueOf(cancel).Pointer() {
				delete(logcatSubs, remote)
			}
			logcatMu.Unlock()
			release()
		}()

		sub := log.Subscribe()
		defer log.UnSubscribe(sub)

		for {
			select {
			case <-ctx.Done():
				log.Debugln("Logcat subscriber cancelled")

				return
			case msg, ok := <-sub:
				if !ok {
					return
				}

				if msg.LogLevel < log.Level() && !strings.HasPrefix(msg.Payload, "[APP]") {
					continue
				}

				rMsg := &message{
					Level:   msg.LogLevel.String(),
					Message: msg.Payload,
					Time:    time.Now().UnixNano() / 1000 / 1000,
				}

				payload, err := marshalJson(rMsg)
				if err != nil {
					continue
				}

				if C.logcat_received(remote, payload) != 0 {
					log.Debugln("Logcat subscriber closed")

					return
				}
			}
		}
	}(remote)

	log.Infoln("[APP] Logcat level: %s", log.Level().String())
}

//export unsubscribeLogcat
func unsubscribeLogcat(remote unsafe.Pointer) {
	logcatMu.Lock()
	cancel, ok := logcatSubs[remote]
	logcatMu.Unlock()

	if ok {
		cancel()
	}
}
