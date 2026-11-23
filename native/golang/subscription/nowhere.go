package subscription

import (
	"encoding/hex"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"unicode/utf8"
)

// parseNowhereLink parses a nowhere:// share link into a mihomo proxy map.
// Upstream mihomo's ConvertsV2Ray does not know the nowhere scheme, so the
// subscription inspector cannot rely on it. This mirrors the NW3 fork's
// ParseShareLink rules, with one deliberate leniency: pasted links are
// normalized first (uppercase hex keys and pins are lowercased, hyphen
// groups are stripped, and a trailing-slash-only path is ignored), because
// those are display and paste artifacts, not a different key.
func parseNowhereLink(raw string) (map[string]any, error) {
	line := strings.TrimSpace(raw)
	if line == "" {
		return nil, fmt.Errorf("nowhere: empty share link")
	}
	u, err := url.Parse(line)
	if err != nil {
		return nil, fmt.Errorf("nowhere: invalid share URL")
	}
	if !strings.EqualFold(u.Scheme, "nowhere") || u.User == nil || u.Hostname() == "" || u.Opaque != "" {
		return nil, fmt.Errorf("nowhere: share URL requires KEY@HOST:PORT without a path")
	}
	// A path made only of slashes is a paste artifact; any other path is malformed.
	if u.Path != "" && strings.Trim(u.Path, "/") != "" {
		return nil, fmt.Errorf("nowhere: share URL requires KEY@HOST:PORT without a path")
	}
	if _, present := u.User.Password(); present {
		return nil, fmt.Errorf("nowhere: share URL must not contain password userinfo")
	}
	key := normalizeHexKey(u.User.Username())
	if err := validateNowhereKey(key); err != nil {
		return nil, err
	}
	port64, err := strconv.ParseUint(u.Port(), 10, 16)
	if err != nil || port64 == 0 {
		return nil, fmt.Errorf("nowhere: invalid share port")
	}
	server := u.Hostname()
	if strings.Contains(server, ":") && !strings.HasPrefix(u.Host, "[") {
		return nil, fmt.Errorf("nowhere: IPv6 share hosts require brackets")
	}
	if !validNowhereHost(server) {
		return nil, fmt.Errorf("nowhere: invalid share host")
	}
	values := make(map[string]string)
	if u.RawQuery != "" {
		for _, pair := range strings.Split(u.RawQuery, "&") {
			rawKey, rawValue, _ := strings.Cut(pair, "=")
			k, err := url.PathUnescape(rawKey)
			if err != nil {
				continue
			}
			if k != "net" && k != "morph" && k != "sni" && k != "pin" {
				continue
			}
			if _, exists := values[k]; exists {
				continue
			}
			value, err := url.PathUnescape(rawValue)
			if err != nil || !utf8.ValidString(value) {
				return nil, fmt.Errorf("nowhere: malformed share query value")
			}
			values[k] = value
		}
	}
	network := "tcp"
	if v, ok := values["net"]; ok {
		if v != "tcp" && v != "udp" {
			return nil, fmt.Errorf("nowhere: net must be tcp or udp")
		}
		network = v
	}
	morph := true
	if v, ok := values["morph"]; ok {
		if v != "0" && v != "1" {
			return nil, fmt.Errorf("nowhere: morph must be 0 or 1")
		}
		morph = v == "1"
	}
	sni := values["sni"]
	if sni == "none" {
		sni = ""
	}
	if sni != "" && !validNowhereHost(sni) {
		return nil, fmt.Errorf("nowhere: invalid SNI")
	}
	pin := values["pin"]
	if pin == "none" {
		pin = ""
	}
	if pin != "" {
		pin = normalizeHexKey(pin)
		if err := validateNowherePin(pin); err != nil {
			return nil, err
		}
	}
	name := u.Fragment
	if !utf8.ValidString(name) {
		return nil, fmt.Errorf("nowhere: invalid display name")
	}
	if name == "" {
		name = server
	}
	return map[string]any{
		"name": name, "type": "nowhere",
		"server": server, "port": int(port64), "password": key,
		"network": network, "morph": morph, "sni": sni, "pin": pin, "udp": true,
	}, nil
}

// normalizeHexKey lowercases a hex key or pin and strips hyphen grouping so
// pasted display forms (uppercase, "abcd-ef01-...") match the canonical
// lowercase form the core expects.
func normalizeHexKey(s string) string {
	return strings.ToLower(strings.ReplaceAll(s, "-", ""))
}

func validateNowhereKey(key string) error {
	if len(key) != 32 {
		return fmt.Errorf("nowhere: shared key must be 32 lowercase hexadecimal characters")
	}
	for i := 0; i < len(key); i++ {
		c := key[i]
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return fmt.Errorf("nowhere: shared key must be 32 lowercase hexadecimal characters")
		}
	}
	return nil
}

func validateNowherePin(pin string) error {
	decoded, err := hex.DecodeString(pin)
	if err != nil || len(decoded) != 32 {
		return fmt.Errorf("nowhere: invalid certificate pin")
	}
	return nil
}

func validNowhereHost(host string) bool {
	if host == "" {
		return false
	}
	if strings.Contains(host, ":") {
		for _, c := range host {
			if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c == ':' || c == '.') {
				return false
			}
		}
		return true
	}
	for _, label := range strings.Split(host, ".") {
		if label == "" || len(label) > 63 {
			return false
		}
		for i, c := range label {
			if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-') {
				return false
			}
			if c == '-' && (i == 0 || i == len(label)-1) {
				return false
			}
		}
	}
	return true
}
