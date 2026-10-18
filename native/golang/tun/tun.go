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

// Start borrows fd. The patched Mihomo tunNew duplicates it at the ownership
// boundary; successful and failed listeners both close only their own duplicate.
func Start(fd int, stack, gateway, portal, dns string) (io.Closer, error) {
	if fd <= 0 {
		return nil, fmt.Errorf("invalid borrowed tun fd: %d", fd)
	}
	tunStack, err := ParseStack(stack)
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
		Inet4Address: prefix4, Inet6Address: prefix6, MTU: 9000, FileDescriptor: fd,
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
