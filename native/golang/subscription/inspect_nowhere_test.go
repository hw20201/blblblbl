package subscription

import (
	"encoding/base64"
	"reflect"
	"strings"
	"testing"
)

// Public, deliberately non-routable fixture. Never substitute a user's endpoint here.
const validNowhere = "nowhere://11111111111111111111111111111111@test.example.com:12345?net=udp&morph=1#Test%20Nowhere"

func TestNowhereImportFormatsAndNativeRoundTrip(t *testing.T) {
	cases := []struct{ name, text, format string }{
		{"URI", validNowhere, "uri"},
		{"Base64", base64.StdEncoding.EncodeToString([]byte(validNowhere)), "base64"},
		{"URL-safe Base64", base64.RawURLEncoding.EncodeToString([]byte(validNowhere)), "base64"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			p := Inspect(tc.text, "auto")
			if p.Format != tc.format || len(p.Nodes) != 1 || len(p.Diagnostics) != 0 {
				t.Fatalf("fixture did not yield one valid node: %+v", p)
			}
			n := p.Nodes[0]
			want := map[string]any{
				"name": "Test Nowhere", "type": "nowhere", "server": "test.example.com", "port": 12345,
				"password": strings.Repeat("1", 32), "network": "udp", "morph": true,
				"sni": "", "pin": "", "udp": true,
			}
			if n.Type != "nowhere" || !reflect.DeepEqual(n.Data, want) {
				t.Fatalf("native fields changed: %+v", n)
			}
			output, err := Template([]map[string]any{n.Data})
			if err != nil {
				t.Fatal(err)
			}
			checked := Inspect(string(output), "yaml")
			if !checked.CompleteValid || len(checked.Nodes) != 1 || len(checked.Diagnostics) != 0 {
				t.Fatalf("generated Nowhere config failed native validation: %+v", checked)
			}
			if !reflect.DeepEqual(checked.Nodes[0].Data, want) {
				t.Fatalf("YAML round trip lost Nowhere fields: %+v", checked.Nodes[0].Data)
			}
		})
	}
}

func TestNowhereImportUsesV99ShareSemantics(t *testing.T) {
	key := strings.Repeat("1", 32)
	pin := strings.Repeat("2", 64)
	cases := []struct {
		name, uri, server, display, network, sni, pin string
		morph                                         bool
	}{
		{"defaults", "nowhere://" + key + "@test.example.com:12345", "test.example.com", "test.example.com", "tcp", "", "", true},
		{"TCP trust", "nowhere://" + key + "@test.example.com:12345?net=tcp&morph=0&sni=tls.example.com&pin=" + pin + "#TCP", "test.example.com", "TCP", "tcp", "tls.example.com", pin, false},
		{"IPv6 escaped name", "nowhere://%31" + key[1:] + "@[2001:db8::1]:12345?net=udp&morph=0&sni=none&pin=none#%E6%B5%8B%E8%AF%95%20A%2BB", "2001:db8::1", "测试 A+B", "udp", "", "", false},
		{"first known parameter", "NOWHERE://" + key + "@test.example.com:12345?net=udp&net=tcp&morph=0&morph=1&future=extension#First", "test.example.com", "First", "udp", "", "", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			p := Inspect(tc.uri, "uri")
			if len(p.Nodes) != 1 || len(p.Diagnostics) != 0 {
				t.Fatalf("native-valid fixture rejected: %+v", p.Diagnostics)
			}
			n := p.Nodes[0].Data
			if n["server"] != tc.server || n["name"] != tc.display || n["network"] != tc.network ||
				n["morph"] != tc.morph || n["sni"] != tc.sni || n["pin"] != tc.pin || n["password"] != key {
				t.Fatalf("v99 share semantics changed: %+v", n)
			}
		})
	}
}

func TestNowhereImportInvalidLinksRemainPartialAndRedacted(t *testing.T) {
	prefix := "nowhere://" + strings.Repeat("1", 32) + "@test.example.com:12345"
	cases := map[string]string{
		"key":           "nowhere://private-key@private-host:12345",
		"password":      "nowhere://" + strings.Repeat("1", 32) + ":private-password@test.example.com:12345",
		"missing port":  "nowhere://" + strings.Repeat("1", 32) + "@private-host",
		"zero port":     "nowhere://" + strings.Repeat("1", 32) + "@test.example.com:0",
		"overflow port": "nowhere://" + strings.Repeat("1", 32) + "@test.example.com:65536",
		"network":       prefix + "?net=private-invalid",
		"morph":         prefix + "?morph=true",
		"pin":           prefix + "?pin=private-pin",
		"escape":        prefix + "?sni=%GG",
		"path":          prefix + "/private-path",
	}
	for name, input := range cases {
		t.Run(name, func(t *testing.T) {
			p := Inspect(validSS+"\n"+input, "uri")
			if len(p.Nodes) != 1 || len(p.Diagnostics) != 1 {
				t.Fatalf("invalid line was accepted or valid line was lost: %+v", p)
			}
			d := p.Diagnostics[0]
			if d.Line != 2 || d.Index != 2 || d.Code != "uri" || strings.Contains(d.Message, "private") || strings.Contains(d.Message, "nowhere://") || strings.Contains(d.Message, "任务一") {
				t.Fatalf("wrong or unredacted diagnostic: %+v", d)
			}
		})
	}
}

func TestNowhereImportLenientSpellings(t *testing.T) {
	hexKey := "abcdef0123456789abcdef0123456789"
	upperKey := strings.ToUpper(hexKey)
	uuidKey := "abcdef01-2345-6789-abcd-ef0123456789"
	pin := strings.Repeat("ab", 32)
	upperPin := strings.ToUpper(pin)
	cases := []struct {
		name, uri, password, pin string
	}{
		{"uppercase key", "nowhere://" + upperKey + "@test.example.com:12345?net=udp&morph=1#Upper", hexKey, ""},
		{"UUID key", "nowhere://" + uuidKey + "@test.example.com:12345?net=udp&morph=1#UUID", hexKey, ""},
		{"trailing slash", "nowhere://" + hexKey + "@test.example.com:12345/?net=udp&morph=1#Slash", hexKey, ""},
		{"uppercase pin", "nowhere://" + hexKey + "@test.example.com:12345?net=udp&morph=1&pin=" + upperPin + "#Pin", hexKey, pin},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			p := Inspect(tc.uri, "uri")
			if len(p.Nodes) != 1 || len(p.Diagnostics) != 0 {
				t.Fatalf("lenient Nowhere spelling rejected: %+v", p.Diagnostics)
			}
			n := p.Nodes[0].Data
			if n["password"] != tc.password || n["pin"] != tc.pin {
				t.Fatalf("lenient Nowhere spelling not normalized: %+v", n)
			}
		})
	}
}
