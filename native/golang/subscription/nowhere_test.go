package subscription

import (
	"strings"
	"testing"
)

// Pasted links carry display artifacts. These must be accepted and normalized
// to the canonical lowercase form, not rejected as malformed.
func TestParseNowhereLinkAcceptsPastedFormats(t *testing.T) {
	key := strings.Repeat("a", 32)
	upper := strings.ToUpper(key)
	hyphenated := key[:8] + "-" + key[8:16] + "-" + key[16:24] + "-" + key[24:]
	pin := strings.Repeat("b", 64)
	upperPin := strings.ToUpper(pin)
	cases := []struct{ name, link, wantKey, wantPin string }{
		{"lowercase", "nowhere://" + key + "@test.example.com:12345", key, ""},
		{"uppercase key", "nowhere://" + upper + "@test.example.com:12345", key, ""},
		{"hyphenated key", "nowhere://" + hyphenated + "@test.example.com:12345", key, ""},
		{"trailing slash", "nowhere://" + key + "@test.example.com:12345/", key, ""},
		{"trailing slashes with query", "nowhere://" + key + "@test.example.com:12345///?net=udp", key, ""},
		{"uppercase pin", "nowhere://" + key + "@test.example.com:12345?pin=" + upperPin, key, pin},
		{"hyphenated pin", "nowhere://" + key + "@test.example.com:12345?pin=" + pin[:16] + "-" + pin[16:], key, pin},
		{"uppercase scheme", "NOWHERE://" + key + "@test.example.com:12345", key, ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			m, err := parseNowhereLink(tc.link)
			if err != nil {
				t.Fatalf("pasted format rejected: %v", err)
			}
			if m["password"] != tc.wantKey {
				t.Fatalf("key not normalized: got %v want %v", m["password"], tc.wantKey)
			}
			if m["pin"] != tc.wantPin {
				t.Fatalf("pin not normalized: got %v want %v", m["pin"], tc.wantPin)
			}
			if m["type"] != "nowhere" || m["server"] != "test.example.com" || m["port"] != 12345 {
				t.Fatalf("fields changed: %+v", m)
			}
		})
	}
}

func TestParseNowhereLinkStillRejectsMalformed(t *testing.T) {
	key := strings.Repeat("a", 32)
	bad := []string{
		"nowhere://" + strings.Repeat("a", 31) + "@test.example.com:12345",
		"nowhere://" + strings.Repeat("a", 33) + "@test.example.com:12345",
		"nowhere://" + strings.Repeat("g", 32) + "@test.example.com:12345",
		"nowhere://" + key + "@test.example.com:12345/path",
		"nowhere://" + key + "@test.example.com:0",
		"nowhere://" + key + "@test.example.com:99999",
		"nowhere://" + key + "@test.example.com:12345?net=sctp",
		"nowhere://" + key + "@test.example.com:12345?morph=2",
		"nowhere://" + key + ":secret@test.example.com:12345",
	}
	for _, link := range bad {
		if _, err := parseNowhereLink(link); err == nil {
			t.Fatalf("malformed link accepted: %s", link)
		}
	}
}
