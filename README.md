# Nexus

基于 Mihomo (Clash.Meta) 内核的 Android 代理客户端。

## 特性

- **协议支持**：Shadowsocks、VMess、VLESS、Trojan、Hysteria 2、TUIC、WireGuard
- **分流控制**：域名/IP 规则分流、分应用代理（全局/绕过/仅代理）
- **TUN 堆栈**：设置中可选 `system`、`mixed`、`gvisor` 或 `mips`；新安装默认 `mips`，未知旧值回退到 `mixed`
- **实用功能**：Fake-IP DNS 增强、局域网代理共享、配置 JS 脚本复写、下拉快捷磁贴

## 构建

预置核心为 Mihomo v1.19.32；MIPS 指其原生 Go IP 栈，不是 CPU 架构。核心源码、补丁和许可见 [native/UPSTREAM.md](native/UPSTREAM.md)。修改原生部分后先运行 `bash scripts/build-native.sh`；Gradle 会校验核心与 JNI 两库的配对哈希。

图标采用深灰底青蓝 N，通知与快捷设置使用同形单色 N，并支持 Android 13 主题图标。可编辑 SVG 及生成方法见 [assets/README.md](assets/README.md)。

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需签名配置）
./gradlew assembleRelease
```

## 说明

- 支持架构：`arm64-v8a`
- 订阅支持：`HTTP` 与 `HTTPS` 链接导入
