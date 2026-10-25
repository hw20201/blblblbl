# Nexus

基于 Mihomo (Clash.Meta) 内核的 Android 代理客户端。

## 特性

- **协议支持**：Shadowsocks、VMess、VLESS、Trojan、Hysteria 2、TUIC、WireGuard
- **分流控制**：域名/IP 规则分流、分应用代理（全局/绕过/仅代理）
- **TUN 堆栈**：设置中可选 `system`、`mixed`、`gvisor` 或 `mips`；新安装默认 `mips`，未知旧值回退到 `mixed`
- **TUN 调优**：MTU 1280–65535（默认 9000）；MIPS 栈可选 `cubic`、`reno`、`bbr`、`bbr3` 拥塞控制
- **IPv6 路由**：单个开关同时控制 TUN 的 IPv6 地址/`::/0`/DNS 与内核 `ipv6`、`dns.ipv6`；默认关闭；关闭时明确允许 IPv6 绕过 VPN（不是 IPv6 防泄漏模式）
- **实用功能**：Fake-IP DNS 增强、局域网代理共享、配置 JS 脚本复写、下拉快捷磁贴

## 构建

预置核心为 Mihomo v1.19.32；MIPS 指其原生 Go IP 栈，不是 CPU 架构。核心源码、补丁和许可见 [native/UPSTREAM.md](native/UPSTREAM.md)。修改原生部分后先运行 `bash scripts/build-native.sh`；Gradle 会校验核心与 JNI 两库的配对哈希。本版原生契约为 2（新增 MTU 与拥塞控制参数），只构建 APK 而不重建 native 时会退回契约 1 的行为并忽略这两项设置。

## 原生库的二进制与源码归属

`app/src/main/jniLibs/arm64-v8a/` 中的 `libbridge.so` 和 `libclash.so` 是此 fork 从仓库源码构建的 ARM64 产物，不是从 kanwox/Nexus 复制来的预编译 `.so`。

- `libbridge.so` 是本仓库的 JNI 桥接层，由 `scripts/build-native.sh` 编译仓库内的 C/JNI 源文件生成。维护者说明桥接实现由其编写；它负责把 Android 调用接到 Go 核心，不是代理引擎本身。
- `libclash.so` 由 Go 工具链和 Android NDK 从本仓库的 Go 适配/桥接代码及固定版本的 Mihomo 源码构建。桥接和 MIPS 集成是本 fork 的工作；代理引擎源自 MetaCubeX 的 Mihomo，不是本 fork 原创。

精确来源、补丁、依赖和重建步骤见 [native/UPSTREAM.md](native/UPSTREAM.md)；第三方许可见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

图标采用深灰底青蓝 N，通知与快捷设置使用同形单色 N，并支持 Android 13 主题图标。可编辑 SVG 及生成方法见 [assets/README.md](assets/README.md)。

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需签名配置）
./gradlew assembleRelease
```

## 说明

- 支持架构：`arm64-v8a`
- 界面：仅保留 Cupertino；旧版保存的 Miuix 偏好会自动按 Cupertino 读取。保留 Liquid Glass 开关与强度。
- 订阅与远程脚本：远程地址必须使用 `HTTPS`；`HTTP` 仅允许精确的 `127.0.0.1` 与 `localhost` 回环地址，远程 HTTP 链接需更换为 HTTPS。
- 局域网共享：其他设备需使用共享页显示的用户名和随机密码；可复制、轮换。应用本机回环访问免认证。
- 订阅脚本：保留 QuickJS 主引擎与 Rhino 1.7.15 兜底，不换引擎。新增 256 KiB UTF-8 源文件上限、慢执行日志，以及逐 runtime 的 QuickJS 原生 3 秒 deadline、64 MiB 堆与 512 KiB 栈限制。创建、守卫、执行和清理均使用同一调用线程；守卫不可用时拒绝无界 QuickJS，进入既有 Rhino 兜底（不影响应用启动）。Rhino 保留指令限时，并使用单调时钟和不可被脚本捕获的宿主超时信号。Android 实际中断、内存边界与设备性能仍需真机验收。
- 配置安全：仅接受可解析的 YAML 映射；非法 YAML 或序列/标量根直接拒绝，不把未过滤的文本拼入运行时配置。
- R8：默认关闭；`-Pnexus.r8=true` 仅供实验构建，需独立真机验收。普通 Release 草稿不启用。

## 许可证

本项目以 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

Mihomo 内核与上游 Android 适配层各自的许可证见 [native/LICENSE.mihomo](native/LICENSE.mihomo) 与
[native/LICENSE.upstream](native/LICENSE.upstream)。分发二进制时需要同时提供本仓库源码、
`native/patches/` 补丁与构建脚本，以满足 GPL 合规义务。

界面与交互参考的上游项目：[kanwox/Nexus](https://github.com/kanwox/Nexus)。
