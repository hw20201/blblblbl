package config

import (
	"reflect"
	"runtime"
	"testing"
)

// The processor order is load-bearing: patchExternalController must run
// before patchOverride so only override settings can set ExternalController,
// and patchGeneral reads the result. Pin the order so a future reorder
// fails loudly instead of silently changing config semantics.
func TestProcessorsOrder(t *testing.T) {
	index := func(target processor) int {
		want := reflect.ValueOf(target).Pointer()
		for i, p := range processors {
			if reflect.ValueOf(p).Pointer() == want {
				return i
			}
		}
		return -1
	}

	external := index(patchExternalController)
	override := index(patchOverride)
	general := index(patchGeneral)

	if external < 0 || override < 0 || general < 0 {
		t.Fatalf("processors missing entries: external=%d override=%d general=%d", external, override, general)
	}

	if !(external < override && override < general) {
		names := make([]string, 0, len(processors))
		for _, p := range processors {
			names = append(names, runtime.FuncForPC(reflect.ValueOf(p).Pointer()).Name())
		}
		t.Fatalf("processors out of order: external=%d override=%d general=%d order=%v", external, override, general, names)
	}
}
