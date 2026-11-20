package subscription

import (
	"encoding/base64"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	geodata "github.com/metacubex/mihomo/component/geodata"
	coreconfig "github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	_ "github.com/metacubex/mihomo/hub"
)

const validSS = "ss://YWVzLTEyOC1nY206c2VjcmV0@node.example:443#Example"
const validYAML = "proxies:\n  - name: Example\n    type: ss\n    server: node.example\n    port: 443\n    cipher: aes-128-gcm\n    password: secret\n"

func TestFormatsAndIsolatedTemplate(t *testing.T) {
	cases := []struct{ name, text, format string }{
		{"yaml", validYAML, "yaml"},
		{"URI", validSS, "uri"},
		{"Base64", base64.StdEncoding.EncodeToString([]byte(validSS)), "base64"},
		{"URL-safe Base64", base64.RawURLEncoding.EncodeToString([]byte(validSS)), "base64"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			p := Inspect(tc.text, "auto")
			if p.Format != tc.format || len(p.Nodes) != 1 || len(p.Diagnostics) != 0 {
				t.Fatalf("unexpected preview: %+v", p)
			}
			output, err := Template([]map[string]any{p.Nodes[0].Data})
			if err != nil {
				t.Fatal(err)
			}
			checked := Inspect(string(output), "auto")
			if !checked.CompleteValid {
				t.Fatalf("generated config rejected: %+v", checked.Diagnostics)
			}
		})
	}
}

func TestPartialDiagnosticsAreRedactedAndLineMapped(t *testing.T) {
	p := Inspect(validSS+"\nnot-a-node-secret\nnowhere://private-credential@private-host\n", "auto")
	if len(p.Nodes) != 1 || len(p.Diagnostics) != 2 {
		t.Fatalf("unexpected counts: %+v", p)
	}
	if p.Diagnostics[0].Line != 2 || p.Diagnostics[1].Line != 3 || p.Diagnostics[1].Code != "uri" {
		t.Fatal(p.Diagnostics)
	}
	output, _ := json.Marshal(p.Diagnostics)
	for _, secret := range []string{"private-credential", "private-host", "not-a-node-secret"} {
		if strings.Contains(string(output), secret) {
			t.Fatalf("diagnostic leaked %s", secret)
		}
	}
}

func TestNativeFieldsAndChainPreserved(t *testing.T) {
	p := Inspect(validYAML+"    dialer-proxy: parent\n    udp: true\n    skip-cert-verify: false\n", "auto")
	if len(p.Nodes) != 1 {
		t.Fatal(p.Diagnostics)
	}
	if p.Nodes[0].Data["dialer-proxy"] != "parent" {
		t.Fatal("chain dependency lost")
	}
	if p.Nodes[0].Data["skip-cert-verify"] != false {
		t.Fatal("TLS changed")
	}
}

func TestUnsupportedFieldsNeverSilentlyDisappear(t *testing.T) {
	cases := []string{
		"vless://00000000-0000-0000-0000-000000000001@node.example:443?security=reality&pbk=key&unsupported-secret=token",
		"trojan://password@node.example:443?type=xhttp",
		"socks5://user:password@node.example:1080?skip-cert-verify=true",
		"nowhere://secret@node.example:443",
		"outbounds: [{type: vless}]",
	}
	for _, input := range cases {
		p := Inspect(input, "auto")
		if len(p.Nodes) != 0 || len(p.Diagnostics) == 0 {
			t.Fatalf("unexpectedly accepted %q", input)
		}
	}
}

func TestHTTPSCertificateValidationAndEscapedAuth(t *testing.T) {
	p := Inspect("https://u%3Aer:p%3Aass@node.example:443#HTTPS", "proxy")
	if len(p.Nodes) != 1 {
		t.Fatal(p.Diagnostics)
	}
	n := p.Nodes[0].Data
	if n["skip-cert-verify"] != false || n["username"] != "u:er" || n["password"] != "p:ass" {
		t.Fatalf("unsafe conversion: %+v", n)
	}
}

func TestEmptyHtmlSizeAndBrokenConfig(t *testing.T) {
	for _, input := range []string{"", "<!doctype html><html>login</html>", strings.Repeat("x", MaxBytes+1), "proxies: [invalid]", validYAML + "proxy-groups: [{name: broken, type: select, proxies: [missing]}]\n"} {
		p := Inspect(input, "auto")
		if p.CompleteValid || len(p.Diagnostics) == 0 {
			t.Fatalf("invalid input accepted: %d bytes", len(input))
		}
	}
}

func TestTLSRealityAndWSFields(t *testing.T) {
	input := "vless://00000000-0000-0000-0000-000000000001@node.example:443?security=tls&sni=sni.example&type=ws&host=ws.example&path=%2Fws&fp=chrome#VLESS"
	p := Inspect(input, "auto")
	if len(p.Nodes) != 1 {
		t.Fatal(p.Diagnostics)
	}
	n := p.Nodes[0].Data
	if n["servername"] != "sni.example" || n["tls"] != true || n["network"] != "ws" {
		t.Fatalf("lost TLS: %+v", n)
	}
	opts := n["ws-opts"].(map[string]any)
	if opts["path"] != "/ws" || opts["headers"].(map[string]any)["Host"] != "ws.example" {
		t.Fatal("lost WS")
	}
	if _, random := opts["headers"].(map[string]any)["User-Agent"]; random {
		t.Fatal("unstable synthetic header")
	}
}

func TestBundledOfflineGeoRules(t *testing.T) {
	assets := os.Getenv("ARCA_TEST_ASSETS")
	if assets == "" {
		t.Skip("set ARCA_TEST_ASSETS to the APK asset directory")
	}
	C.SetHomeDir("/arca-offline-inspection")
	site, err := os.ReadFile(filepath.Join(assets, "geosite.dat"))
	if err != nil {
		t.Fatal(err)
	}
	mmdb, err := os.ReadFile(filepath.Join(assets, "Country.mmdb"))
	if err != nil {
		t.Fatal(err)
	}
	geodata.SetInspectionAssets(site, mmdb)
	for _, rule := range []string{"GEOSITE,cn,PROXY", "GEOIP,CN,PROXY"} {
		t.Run(rule, func(t *testing.T) {
			text := validYAML + "geodata-mode: false\nproxy-groups: [{name: PROXY, type: select, proxies: [Example]}]\nrules: [\"" + rule + "\", \"MATCH,DIRECT\"]\n"
			// Fixture-only diagnostics; never expose parser errors from user input.
			cfg, err := coreconfig.Parse([]byte(text))
			if err != nil {
				t.Fatal(err)
			}
			for _, proxy := range cfg.Proxies {
				_ = proxy.Close()
			}
			if p := Inspect(text, "yaml"); !p.CompleteValid {
				t.Fatal(p.Diagnostics)
			}
		})
	}
}
