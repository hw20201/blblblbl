package tun

import (
	"net/netip"
	"testing"

	"github.com/metacubex/mihomo/listener/sing_tun"
)

func TestDNSHijackTargetsAllPort53Destinations(t *testing.T) {
	var targets []netip.AddrPort
	for _, raw := range dnsHijackTargets() {
		target, err := netip.ParseAddrPort(raw)
		if err != nil {
			t.Fatal(err)
		}
		targets = append(targets, target)
	}
	handler := sing_tun.ListenerHandler{DnsAddrPorts: targets}
	for _, raw := range []string{"117.135.171.81:53", "[2001:db8::53]:53"} {
		target := netip.MustParseAddrPort(raw)
		if !handler.ShouldHijackDns(target) {
			t.Errorf("expected DNS destination %s to be hijacked", target)
		}
	}
	if handler.ShouldHijackDns(netip.MustParseAddrPort("117.135.171.81:5353")) {
		t.Fatal("non-DNS destination port was hijacked")
	}
}
