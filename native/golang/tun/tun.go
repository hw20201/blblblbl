package tun

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strings"

	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func ParseStack(stack string) (C.TUNStack, error) {
	value, ok := C.StackTypeMapping[strings.ToLower(stack)]
	if !ok {
		return 0, fmt.Errorf("unsupported tun stack %q", stack)
	}
	return value, nil
}

const (
	defaultMTU = 9000
	minMTU     = 1280
	maxMTU     = 65535
)

// startOptions mirrors the JSON that the Kotlin service passes through JNI.
type startOptions struct {
	MTU                  uint32 `json:"mtu"`
	CongestionController string `json:"congestion-controller"`
}

// These are the congestion controls mipstack registers; cubic is its default.
var supportedCongestionControllers = map[string]struct{}{
	"cubic": {},
	"reno":  {},
	"bbr":   {},
	"bbr3":  {},
}

func parseStartOptions(raw string) (uint32, string, error) {
	if strings.TrimSpace(raw) == "" {
		return defaultMTU, "", nil
	}
	var options startOptions
	if err := json.Unmarshal([]byte(raw), &options); err != nil {
		return 0, "", fmt.Errorf("invalid TUN options: %w", err)
	}
	mtu := options.MTU
	if mtu == 0 {
		mtu = defaultMTU
	}
	if mtu < minMTU || mtu > maxMTU {
		return 0, "", fmt.Errorf("TUN MTU %d out of range %d..%d", mtu, minMTU, maxMTU)
	}
	controller := strings.ToLower(strings.TrimSpace(options.CongestionController))
	if controller != "" {
		if _, ok := supportedCongestionControllers[controller]; !ok {
			return 0, "", fmt.Errorf("unsupported TUN congestion controller %q", controller)
		}
	}
	return mtu, controller, nil
}

// Start borrows fd. The patched Mihomo tunNew duplicates it at the ownership
// boundary; successful and failed listeners both close only their own duplicate.
func Start(fd int, stack, gateway, portal, dns, rawOptions string) (io.Closer, error) {
	if fd <= 0 {
		return nil, fmt.Errorf("invalid borrowed tun fd: %d", fd)
	}
	tunStack, err := ParseStack(stack)
	if err != nil {
		return nil, err
	}
	mtu, congestionController, err := parseStartOptions(rawOptions)
	if err != nil {
		return nil, err
	}
	var prefix4, prefix6 []netip.Prefix
	for _, raw := range strings.Split(gateway, ",") {
		if raw = strings.TrimSpace(raw); raw == "" {
			continue
		}
		prefix, err := netip.ParsePrefix(raw)
		if err != nil {
			return nil, err
		}
		if prefix.Addr().Is4() {
			prefix4 = append(prefix4, prefix)
		} else {
			prefix6 = append(prefix6, prefix)
		}
	}
	if len(prefix4)+len(prefix6) == 0 {
		return nil, fmt.Errorf("TUN requires at least one local address")
	}
	var dnsHijack []string
	for _, raw := range strings.Split(dns, ",") {
		if raw = strings.TrimSpace(raw); raw == "" {
			continue
		}
		addr, err := netip.ParseAddr(raw)
		if err != nil {
			return nil, fmt.Errorf("invalid TUN DNS address: %w", err)
		}
		dnsHijack = append(dnsHijack, net.JoinHostPort(addr.String(), "53"))
	}
	options := LC.Tun{
		Enable: true, Device: sing_tun.InterfaceName, Stack: tunStack,
		DNSHijack: dnsHijack, AutoRoute: false, AutoDetectInterface: false,
		Inet4Address: prefix4, Inet6Address: prefix6, MTU: mtu, FileDescriptor: fd,
	}
	if tunStack == C.TunMips {
		options.CongestionController = congestionController
	} else if congestionController != "" {
		log.Warnln("TUN congestion controller %q is ignored for stack %s", congestionController, tunStack.String())
	}
	serialized, _ := json.Marshal(options)
	log.Debugln("TUN requested stack=%s, options=%s", tunStack.String(), serialized)
	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		log.Errorln("TUN %s startup failed: %v", tunStack.String(), err)
		return nil, err
	}
	log.Infoln("TUN listener started with native stack %s", tunStack.String())
	return listener, nil
}
