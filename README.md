# Nexus

基于 Mihomo (Clash.Meta) 内核的 Android 代理客户端。

## 特性

- **协议支持**：Shadowsocks、VMess、VLESS、Trojan、Hysteria 2、TUIC、WireGuard
- **分流控制**：域名/IP 规则分流、分应用代理（全局/绕过/仅代理）
- **实用功能**：Fake-IP DNS 增强、局域网代理共享、配置 JS 脚本复写、下拉快捷磁贴

## 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需签名配置）
./gradlew assembleRelease
```

## 说明

- 支持架构：`arm64-v8a`
- 订阅支持：`HTTP` 与 `HTTPS` 链接导入
