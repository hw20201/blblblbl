package tun

import (
	"bytes"
	"fmt"
	"net/netip"
	"os"
	"testing"

	C "github.com/metacubex/mihomo/constant"
	_ "github.com/metacubex/mihomo/dns" // Registers resolver.SystemResolver, as the app config does.
	"github.com/metacubex/mipstack"
	"golang.org/x/sys/unix"
)

func TestParseStackStrictMapping(t *testing.T) {
	tests := []struct {
		name string
		want C.TUNStack
		ok   bool
	}{
		{"system", C.TunSystem, true}, {"gvisor", C.TunGvisor, true},
		{"mixed", C.TunMixed, true}, {"mips", C.TunMips, true},
		{"MIPS", C.TunMips, true}, {"unknown", 0, false}, {"", 0, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseStack(tt.name)
			if (err == nil) != tt.ok {
				t.Fatalf("ParseStack(%q) error = %v", tt.name, err)
			}
			if tt.ok && got != tt.want {
				t.Fatalf("ParseStack(%q) = %v, want %v", tt.name, got, tt.want)
			}
		})
	}
}

// socketpair provides a valid packet-oriented file descriptor without requiring
// root, a /dev/net/tun device, or an external network.
func newPacketSocketpair(t *testing.T) (int, int) {
	t.Helper()
	fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM, 0)
	if err != nil {
		t.Fatalf("Socketpair: %v", err)
	}
	if err := unix.SetNonblock(fds[0], true); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = unix.Close(fds[0]) })
	t.Cleanup(func() { _ = unix.Close(fds[1]) })
	return fds[0], fds[1]
}

func assertFDOpen(t *testing.T, fd int) {
	t.Helper()
	if _, err := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); err != nil {
		t.Fatalf("borrowed fd %d is no longer valid: %v", fd, err)
	}
}

func countFDAliases(t *testing.T, fd int) int {
	t.Helper()
	target, err := os.Readlink(fmt.Sprintf("/proc/self/fd/%d", fd))
	if err != nil {
		t.Fatal(err)
	}
	entries, err := os.ReadDir("/proc/self/fd")
	if err != nil {
		t.Fatal(err)
	}
	count := 0
	for _, entry := range entries {
		if link, err := os.Readlink("/proc/self/fd/" + entry.Name()); err == nil && link == target {
			count++
		}
	}
	return count
}

func TestMipsStartCloseBorrowsFD(t *testing.T) {
	fd, _ := newPacketSocketpair(t)
	before := countFDAliases(t, fd)
	listener, err := Start(fd, "mips", "10.0.0.1/30,fd00::1/126", "", "1.1.1.1,2606:4700:4700::1111")
	if err != nil {
		t.Fatalf("Start(mips) failed: %v", err)
	}
	assertFDOpen(t, fd)
	if aliases := countFDAliases(t, fd); aliases != before+1 {
		t.Fatalf("native must own exactly one duplicate: before=%d after=%d", before, aliases)
	}
	if err := listener.Close(); err != nil {
		t.Errorf("Close: %v", err)
	}
	assertFDOpen(t, fd)
	if aliases := countFDAliases(t, fd); aliases != before {
		t.Fatalf("native FD leaked after close: before=%d after=%d", before, aliases)
	}
}

func TestMipsIPv4AndIPv6PacketRoundTrip(t *testing.T) {
	fd, peer := newPacketSocketpair(t)
	listener, err := Start(fd, "mips", "10.0.0.1/30,fd00::1/126", "", "10.0.0.2,fd00::2")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	for _, family := range []struct {
		name, source, gateway string
		protocol              int
	}{
		{"IPv4", "10.0.0.2", "10.0.0.1", mipstack.ProtocolICMPv4},
		{"IPv6", "fd00::2", "fd00::1", mipstack.ProtocolICMPv6},
	} {
		t.Run(family.name, func(t *testing.T) {
			source := netip.MustParseAddr(family.source)
			gateway := netip.MustParseAddr(family.gateway)
			payload := []byte("native-mips-packet-roundtrip")
			message := mipstack.ICMPMessage{Source: source, Destination: gateway}
			if err := message.SetEchoRequest(123, 1, payload); err != nil {
				t.Fatal(err)
			}
			body, err := message.MarshalBinary()
			if err != nil {
				t.Fatal(err)
			}
			packet := mipstack.IPPacket{Source: source, Destination: gateway, Protocol: family.protocol, HopLimit: 64, Payload: body}
			wire, err := packet.MarshalBinary()
			if err != nil {
				t.Fatal(err)
			}
			if _, err = unix.Write(peer, wire); err != nil {
				t.Fatal(err)
			}
			ready, err := unix.Poll([]unix.PollFd{{Fd: int32(peer), Events: unix.POLLIN}}, 2000)
			if err != nil || ready == 0 {
				t.Fatalf("no MIPS reply: ready=%d err=%v", ready, err)
			}
			buffer := make([]byte, 9000)
			n, err := unix.Read(peer, buffer)
			if err != nil {
				t.Fatal(err)
			}
			reply, err := mipstack.ParseIPPacket(buffer[:n])
			if err != nil {
				t.Fatal(err)
			}
			icmp, err := reply.ICMPMessage()
			if err != nil {
				t.Fatal(err)
			}
			id, sequence, received, ok := icmp.Echo()
			if !icmp.IsEchoReply() || !ok || id != 123 || sequence != 1 || !bytes.Equal(payload, received) || reply.Source != gateway || reply.Destination != source {
				t.Fatalf("invalid %s MIPS echo reply: %+v", family.name, reply)
			}
		})
	}
}

func TestMipsStartFailurePreservesBorrowedFD(t *testing.T) {
	t.Run("invalid stack", func(t *testing.T) {
		fd, _ := newPacketSocketpair(t)
		if listener, err := Start(fd, "not-a-stack", "10.0.0.1/30,fd00::1/126", "", "1.1.1.1"); err == nil {
			if listener != nil {
				_ = listener.Close()
			}
			t.Fatal("Start with invalid stack unexpectedly succeeded")
		}
		assertFDOpen(t, fd)
	})
	t.Run("invalid gateway", func(t *testing.T) {
		fd, _ := newPacketSocketpair(t)
		if listener, err := Start(fd, "mips", "not-a-prefix", "", "1.1.1.1"); err == nil {
			if listener != nil {
				_ = listener.Close()
			}
			t.Fatal("Start with invalid gateway unexpectedly succeeded")
		}
		assertFDOpen(t, fd)
	})
}
