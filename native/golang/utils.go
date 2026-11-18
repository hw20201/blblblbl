package main

import "C"

import (
	"encoding/json"

	"github.com/metacubex/mihomo/log"
)

// marshalJson encodes obj for a JNI return value. On failure it logs and
// returns an error instead of panicking: a Go panic crossing cgo into JNI
// kills the process and Kotlin cannot catch it. Callers return nil, which
// every Kotlin call site already handles (null check or runCatching default).
func marshalJson(obj any) (*C.char, error) {
	res, err := json.Marshal(obj)
	if err != nil {
		log.Warnln("marshalJson: %s", err.Error())

		return nil, err
	}

	return C.CString(string(res)), nil
}

func marshalString(obj any) *C.char {
	if obj == nil {
		return nil
	}

	switch o := obj.(type) {
	case error:
		return C.CString(o.Error())
	case string:
		return C.CString(o)
	}

	// An unexpected type used to panic. Never crash the process over a JNI
	// boundary: nil reads as "no error" to complete/fetch_complete.
	return nil
}
