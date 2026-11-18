package main

//#include "bridge.h"
import "C"

import (
	"sync/atomic"
	"unsafe"

	"cfa/native/app"
	"cfa/native/tunnel"
)

//export queryTunnelState
func queryTunnelState() *C.char {
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	res, err := marshalJson(response)
	if err != nil {
		return nil
	}

	return res
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryProxyTotal
func queryProxyTotal(upload, download *C.uint64_t) {
	up, down := tunnel.ProxyTotal()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	res, err := marshalJson(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
	if err != nil {
		return nil
	}

	return res
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	res, err := marshalJson(response)
	if err != nil {
		return nil
	}

	return res
}

// The asynchronous operation owns the JNI global reference until its native work
// finishes. A Kotlin timeout only stops waiting; it must not free this reference.
var pendingHealthChecks atomic.Int64

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	pendingHealthChecks.Add(1)
	go func(name string) {
		defer pendingHealthChecks.Add(-1)
		defer C.release_object(completable)
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}

//export queryPendingHealthChecks
func queryPendingHealthChecks() C.int64_t {
	return C.int64_t(pendingHealthChecks.Load())
}

//export healthCheckAll
func healthCheckAll() {
	tunnel.HealthCheckAll()
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export queryProviders
func queryProviders() *C.char {
	res, err := marshalJson(tunnel.QueryProviders())
	if err != nil {
		return nil
	}

	return res
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	go func(pType, name string) {
		C.complete(completable, marshalString(tunnel.UpdateProvider(pType, name)))

		C.release_object(completable)
	}(C.GoString(pType), C.GoString(name))
}

//export suspend
func suspend(suspended C.int) {
	tunnel.Suspend(suspended != 0)
}

//export queryConnections
func queryConnections() *C.char {
	res, err := marshalJson(tunnel.QueryConnections())
	if err != nil {
		return nil
	}

	return res
}

//export closeConnection
func closeConnection(id C.c_string) C.int {
	if tunnel.CloseConnection(C.GoString(id)) {
		return 1
	}

	return 0
}

//export closeAllConnections
func closeAllConnections() {
	tunnel.CloseAllConnections()
}
