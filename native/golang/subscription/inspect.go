package subscription

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/convert"
	"github.com/metacubex/mihomo/common/yaml"
	coreconfig "github.com/metacubex/mihomo/config"
)

const MaxBytes = 10 * 1024 * 1024
const MaxNodes = 10000

// Inspect runs only in Arca's disposable validation process, never in the VPN process.
// Raw parser errors can contain credentials; only fixed, actionable codes cross JNI.
type Diagnostic struct {
	Index   int    `json:"index"`
	Line    int    `json:"line"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

type Node struct {
	Index int            `json:"index"`
	Line  int            `json:"line"`
	Name  string         `json:"name"`
	Type  string         `json:"type"`
	Data  map[string]any `json:"data"`
}

type Preview struct {
	Contract      int          `json:"contract"`
	Format        string       `json:"format"`
	Nodes         []Node       `json:"nodes"`
	Diagnostics   []Diagnostic `json:"diagnostics"`
	Warnings      []string     `json:"warnings"`
	Complete      string       `json:"complete"`
	CompleteValid bool         `json:"completeValid"`
}

func InspectJSON(text, mode string) (result string) {
	defer func() {
		if recover() != nil {
			p := emptyPreview()
			p.fail(0, 0, "invalid", "内容或节点参数无效；请检查原始格式")
			b, _ := json.Marshal(p)
			result = string(b)
		}
	}()
	p := Inspect(text, mode)
	b, _ := json.Marshal(p)
	return string(b)
}

func emptyPreview() Preview {
	return Preview{Contract: 1, Format: "unknown", Nodes: []Node{}, Diagnostics: []Diagnostic{}, Warnings: []string{}}
}

func (p *Preview) fail(index, line int, code, message string) {
	p.Diagnostics = append(p.Diagnostics, Diagnostic{index, line, code, message})
}

func Inspect(text, mode string) Preview {
	p := emptyPreview()
	if len(text) > MaxBytes {
		p.fail(0, 0, "limit", "内容超过 10 MiB 限制")
		return p
	}
	if !oneOf(mode, "auto", "yaml", "uri", "base64", "proxy") || !utf8.ValidString(text) || strings.ContainsRune(text, 0) {
		p.fail(0, 0, "format", "内容不是有效文本或指定格式无效")
		return p
	}
	text = strings.TrimSpace(strings.TrimPrefix(text, "\ufeff"))
	if text == "" {
		p.fail(0, 0, "empty", "内容为空")
		return p
	}
	lower := strings.ToLower(text)
	if strings.HasPrefix(lower, "<!doctype html") || strings.HasPrefix(lower, "<html") {
		p.fail(0, 0, "html", "来源返回了网页；请使用订阅下载地址")
		return p
	}
	if mode == "base64" || (mode == "auto" && !strings.Contains(text, "://") && !strings.ContainsAny(text, ":{[")) {
		decoded, err := decode64(text)
		if err != nil || len(decoded) > MaxBytes || !utf8.Valid(decoded) {
			p.fail(0, 0, "base64", "Base64 内容无效或解码后超过限制")
			return p
		}
		text = strings.TrimSpace(string(decoded))
		if strings.ContainsRune(text, 0) {
			p.fail(0, 0, "format", "解码后不是有效文本")
			return p
		}
		p.Format = "base64"
	}
	if mode == "uri" || mode == "proxy" || (mode != "yaml" && looksLikeURIList(text)) {
		if p.Format != "base64" {
			p.Format = "uri"
		}
		for line, raw := range strings.Split(text, "\n") {
			raw = strings.TrimSpace(raw)
			if raw == "" || strings.HasPrefix(raw, "#") {
				continue
			}
			if len(p.Nodes)+len(p.Diagnostics) >= MaxNodes {
				p.Nodes = []Node{}
				p.fail(0, line+1, "limit", "节点超过 10,000 条限制")
				return p
			}
			mappings, code, message := convertLine(raw)
			if code != "" {
				p.fail(line+1, line+1, code, message)
				continue
			}
			for _, mapping := range mappings {
				p.addNode(mapping, line+1, line+1, true)
			}
		}
		return p
	}
	var root any
	if err := yaml.Unmarshal([]byte(text), &root); err != nil {
		p.fail(0, 0, "format", "无法识别内容；支持 Mihomo YAML、节点链接和 Base64 订阅")
		return p
	}
	var nodes []any
	var doc map[string]any
	switch v := root.(type) {
	case map[string]any:
		doc = v
		if v["outbounds"] != nil || v["inbounds"] != nil {
			p.fail(0, 0, "foreign_config", "这是其他核心的完整配置；请从来源导出 Mihomo 格式")
			return p
		}
		if _, ok := v["type"]; ok {
			nodes = []any{v}
		} else if list, ok := v["proxies"].([]any); ok {
			nodes = list
		}
	case []any:
		nodes = v
	}
	p.Format = "yaml"
	if doc != nil && (doc["proxy-groups"] != nil || doc["rules"] != nil || doc["proxy-providers"] != nil) {
		p.Format = "mihomo"
		p.Complete = text
	}
	if len(nodes) > MaxNodes {
		p.fail(0, 0, "limit", "节点超过 10,000 条限制")
		return p
	}
	for i, raw := range nodes {
		mapping, ok := raw.(map[string]any)
		if !ok {
			p.fail(i+1, 0, "node", "节点必须是 YAML 映射")
			continue
		}
		p.addNode(mapping, i+1, 0, false)
	}
	if p.Complete != "" {
		// The caller places this operation in a separate Android process. Parse may temporarily
		// alter core globals or construct provider timers; process teardown also cleans error paths.
		// Arca owns this runtime setting and always uses its bundled MMDB, including
		// when the source requests protobuf GeoIP. Validate the same effective mode.
		validationDoc := make(map[string]any, len(doc))
		for key, value := range doc {
			validationDoc[key] = value
		}
		validationDoc["geodata-mode"] = false
		validationText, marshalErr := yaml.Marshal(validationDoc)
		var cfg *coreconfig.Config
		var err error
		if marshalErr != nil {
			err = marshalErr
		} else {
			cfg, err = coreconfig.Parse(validationText)
		}
		if cfg != nil {
			for _, provider := range cfg.Providers {
				if closer, ok := provider.(interface{ Close() error }); ok {
					_ = closer.Close()
				}
			}
			for _, provider := range cfg.RuleProviders {
				if closer, ok := provider.(interface{ Close() error }); ok {
					_ = closer.Close()
				}
			}
			for _, proxy := range cfg.Proxies {
				if closer, ok := proxy.(interface{ Close() error }); ok {
					_ = closer.Close()
				}
			}
		}
		if err == nil {
			p.CompleteValid = true
			if doc["proxy-providers"] != nil || doc["rule-providers"] != nil {
				p.Warnings = append(p.Warnings, "完整配置含远程 provider；预览不下载它们，启用时仍需联网验证")
			}
		} else {
			p.fail(0, 0, "config", "完整配置未通过核心校验；请检查分组引用、规则、provider 与节点参数")
		}
	}
	if len(nodes) == 0 && p.Complete == "" {
		p.fail(0, 0, "format", "未发现 proxies 节点或完整 Mihomo 配置")
	}
	return p
}

func (p *Preview) addNode(mapping map[string]any, index, line int, fromURI bool) {
	name, _ := mapping["name"].(string)
	kind, _ := mapping["type"].(string)
	if name == "" {
		name = fmt.Sprintf("节点 %d", index)
		mapping["name"] = name
	}
	if fromURI {
		normalizeConversion(mapping)
	}
	proxy, err := adapter.ParseProxy(mapping)
	if err != nil {
		code := "parameters"
		message := "核心不接受此节点参数；请检查认证、端口、TLS 和传输选项"
		if strings.Contains(err.Error(), "unsupport proxy type") {
			code = "protocol"
			message = "当前核心不支持此协议；请从来源导出受支持的协议"
		}
		p.fail(index, line, code, message)
		return
	}
	_ = proxy.Close()
	if insecure, _ := mapping["skip-cert-verify"].(bool); insecure {
		p.Warnings = append(p.Warnings, fmt.Sprintf("第 %d 项显式关闭证书校验，请核对来源", index))
	}
	p.Nodes = append(p.Nodes, Node{index, line, name, kind, mapping})
}

func normalizeConversion(mapping map[string]any) {
	// Upstream generates a random User-Agent. Removing that synthetic default preserves stable
	// local fingerprints without changing any user supplied field in native YAML.
	for _, key := range []string{"ws-opts", "http-opts"} {
		if opts, ok := mapping[key].(map[string]any); ok {
			if headers, ok := opts["headers"].(map[string]any); ok {
				delete(headers, "User-Agent")
			}
		}
	}
	if value, ok := mapping["port"].(string); ok {
		if n, err := strconv.Atoi(value); err == nil {
			mapping["port"] = n
		}
	}
}

func looksLikeURIList(text string) bool {
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		// YAML values containing URI strings must remain YAML.
		return strings.Contains(strings.SplitN(line, "://", 2)[0], ":") == false && strings.Contains(line, "://") && !strings.HasPrefix(line, "-")
	}
	return false
}

func decode64(text string) ([]byte, error) {
	compact := strings.Join(strings.Fields(text), "")
	if len(compact) > MaxBytes*4/3+8 {
		return nil, fmt.Errorf("limit")
	}
	for _, encoding := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
		if data, err := encoding.DecodeString(compact); err == nil {
			return data, nil
		}
	}
	return nil, fmt.Errorf("base64")
}

// normalizeNowhereLink accepts the real-world spellings of a Nowhere share
// link that the strict upstream parser rejects, without changing any field
// the parser still validates. Scheme is lowercased, an uppercase-hex key is
// lowercased, a UUID-form key is de-dashed, a bare trailing "/" path is
// dropped, and an uppercase-hex pin is lowercased. Anything else is left
// untouched so the existing invalid-link cases still fail.
func normalizeNowhereLink(line string) string {
	scheme, rest, found := strings.Cut(line, "://")
	if !found || strings.ToLower(scheme) != "nowhere" {
		return line
	}
	beforeFrag, frag, hasFrag := strings.Cut(rest, "#")
	beforeQuery, query, hasQuery := strings.Cut(beforeFrag, "?")
	// Only a bare trailing "/" is dropped; a real path is left so the
	// strict parser still rejects it.
	if idx := strings.Index(beforeQuery, "/"); idx != -1 && idx == len(beforeQuery)-1 {
		beforeQuery = beforeQuery[:idx]
	}
	if at := strings.LastIndex(beforeQuery, "@"); at != -1 {
		if norm, ok := normalizeNowhereKey(beforeQuery[:at]); ok {
			beforeQuery = norm + beforeQuery[at:]
		}
	}
	if hasQuery {
		query = normalizeNowherePin(query)
	}
	out := "nowhere://" + beforeQuery
	if hasQuery {
		out += "?" + query
	}
	if hasFrag {
		out += "#" + frag
	}
	return out
}

func normalizeNowhereKey(key string) (string, bool) {
	if len(key) == 36 {
		parts := strings.Split(key, "-")
		if len(parts) == 5 && len(parts[0]) == 8 && len(parts[1]) == 4 && len(parts[2]) == 4 && len(parts[3]) == 4 && len(parts[4]) == 12 {
			joined := strings.Join(parts, "")
			if isHexString(joined) {
				return strings.ToLower(joined), true
			}
		}
		return "", false
	}
	if len(key) == 32 && isHexString(key) {
		lower := strings.ToLower(key)
		if lower != key {
			return lower, true
		}
	}
	return "", false
}

func isHexString(s string) bool {
	if len(s) == 0 {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F') {
			return false
		}
	}
	return true
}

func normalizeNowherePin(query string) string {
	pairs := strings.Split(query, "&")
	changed := false
	for i, pair := range pairs {
		k, v, found := strings.Cut(pair, "=")
		if !found || k != "pin" {
			continue
		}
		if len(v) == 64 && isHexString(v) {
			lower := strings.ToLower(v)
			if lower != v {
				pairs[i] = k + "=" + lower
				changed = true
			}
		}
	}
	if !changed {
		return query
	}
	return strings.Join(pairs, "&")
}

func convertLine(line string) ([]map[string]any, string, string) {
	scheme := strings.ToLower(strings.SplitN(line, "://", 2)[0])
	if scheme == "nowhere" {
		// Keep v99's query handling and defaults identical to native Nowhere imports.
		// Real-world links sometimes use an uppercase key, a UUID-form key, a
		// trailing slash, or an uppercase pin; normalize those spellings first.
		converted, err := convert.ConvertsV2Ray([]byte(normalizeNowhereLink(line)))
		if err != nil || len(converted) != 1 {
			return nil, "uri", "Nowhere 分享链接格式或参数无效"
		}
		return converted, "", ""
	}
	allowed := map[string]string{
		"ss": "plugin,udp-over-tcp,uot", "ssr": "",
		"vmess":     "security,fp,alpn,pcs,sni,pbk,sid,support-x25519mlkem768,packetEncoding,type,headerType,host,method,path,ed,eh,serviceName,encryption",
		"vless":     "security,fp,alpn,pcs,sni,pbk,sid,support-x25519mlkem768,packetEncoding,type,headerType,host,method,path,ed,eh,serviceName,encryption,flow",
		"trojan":    "allowInsecure,sni,alpn,type,path,serviceName,fp,pcs,host",
		"hysteria":  "peer,obfs,alpn,auth,protocol,up,down,upmbps,downmbps,insecure",
		"hy2":       "sni,obfs,obfs-password,insecure,alpn,pinSHA256,down,up",
		"hysteria2": "sni,obfs,obfs-password,insecure,alpn,pinSHA256,down,up",
		"tuic":      "congestion_control,alpn,sni,disable_sni,udp_relay_mode",
		"anytls":    "insecure,sni,hpkp", "mierus": "profile,multiplexing,handshake-mode,traffic-pattern,port,protocol",
		"http": "", "https": "", "socks": "", "socks5": "", "socks5h": "",
	}
	keys, supported := allowed[scheme]
	if !supported {
		return nil, "protocol", "分享链接协议或变体尚不支持；请使用同协议的 Mihomo YAML"
	}
	u, err := url.Parse(line)
	// Legacy VMess/SS/SSR encode the payload; the upstream parser handles those bounded forms.
	encoded := scheme == "ssr" || (scheme == "vmess" && !strings.Contains(line, "@")) || (scheme == "ss" && !strings.Contains(line, "@"))
	if err != nil && !encoded {
		return nil, "uri", "分享链接格式无效"
	}
	if u != nil && !encoded {
		if _, err := url.ParseQuery(u.RawQuery); err != nil {
			return nil, "uri", "分享链接查询参数编码无效"
		}
		allowedKeys := map[string]bool{}
		for _, key := range strings.Split(keys, ",") {
			allowedKeys[key] = true
		}
		for key, values := range u.Query() {
			if !allowedKeys[key] || (len(values) > 1 && scheme != "mierus") {
				return nil, "parameter", "包含尚未核验或重复的分享参数；请导出 Mihomo YAML 以完整保留参数"
			}
		}
		if (scheme == "vless" || scheme == "vmess") && (u.Hostname() == "" || u.Port() == "") {
			return nil, "uri", "分享链接缺少服务器或端口"
		}
		if value := u.Query().Get("ed"); value != "" {
			if _, err := strconv.Atoi(value); err != nil {
				return nil, "parameter", "WebSocket early data 参数必须为整数"
			}
		}
		if scheme == "vless" || scheme == "vmess" {
			network := u.Query().Get("type")
			if !oneOf(network, "", "tcp", "http", "h2", "ws", "httpupgrade", "grpc") {
				return nil, "transport", "此传输链接尚未核验；请使用 Mihomo YAML 保留全部选项"
			}
			if !oneOf(u.Query().Get("security"), "", "none", "tls", "reality") {
				return nil, "security", "TLS 模式尚不支持；请使用 Mihomo YAML"
			}
		}
		if scheme == "trojan" && !oneOf(u.Query().Get("type"), "", "tcp", "ws", "grpc") {
			return nil, "transport", "Trojan 传输格式尚未核验；请使用 Mihomo YAML"
		}
	}
	if scheme == "vmess" && encoded {
		data, err := decode64(strings.TrimPrefix(line, "vmess://"))
		var values map[string]any
		if err != nil || json.Unmarshal(data, &values) != nil {
			return nil, "uri", "VMess Base64 JSON 无效"
		}
		known := "v,ps,add,port,id,aid,scy,sni,net,type,host,path,tls,alpn"
		for key := range values {
			if !oneOf(key, strings.Split(known, ",")...) {
				return nil, "parameter", "VMess 含未核验扩展；请使用 Mihomo YAML 保留参数"
			}
		}
	}
	converted, err := convert.ConvertsV2Ray([]byte(line))
	if err != nil || len(converted) == 0 {
		return nil, "uri", "无法解析此分享链接；请检查格式或改用同协议 YAML"
	}
	if u != nil {
		for _, node := range converted {
			if oneOf(scheme, "http", "https", "socks", "socks5", "socks5h") {
				// Correct upstream's insecure default and colon splitting of percent-escaped auth.
				node["skip-cert-verify"] = false
				if u.User != nil {
					if password, ok := u.User.Password(); ok {
						node["username"] = u.User.Username()
						node["password"] = password
					}
				}
			}
			if scheme == "trojan" && u.Query().Get("host") != "" {
				if opts, ok := node["ws-opts"].(map[string]any); ok {
					headers, _ := opts["headers"].(map[string]any)
					headers["Host"] = u.Query().Get("host")
				} else {
					return nil, "parameter", "Host 参数只支持 WebSocket 传输；请使用 Mihomo YAML"
				}
			}
			if scheme == "hy2" || scheme == "hysteria2" {
				auth, _ := url.PathUnescape(u.User.String())
				if auth != "" {
					node["password"] = auth
				}
			}
		}
	}
	return converted, "", ""
}

func oneOf(value string, values ...string) bool {
	for _, item := range values {
		if value == item {
			return true
		}
	}
	return false
}

// A generated configuration never imports source routing, DNS or scripts.
func Template(nodes []map[string]any) ([]byte, error) {
	names := []string{}
	for _, node := range nodes {
		if name, ok := node["name"].(string); ok {
			names = append(names, name)
		}
	}
	return yaml.Marshal(map[string]any{"proxies": nodes, "proxy-groups": []any{map[string]any{"name": "节点选择", "type": "select", "proxies": names}}, "rules": []string{"MATCH,节点选择"}})
}
