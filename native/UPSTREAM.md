# 原生 MIPS 核心

MIPS 指 **Mihomo IP Stack**，不是 MIPS CPU 架构。应用仍为 Android ARM64。

## 二进制与源码的两层归属

仓库中的 `app/src/main/jniLibs/arm64-v8a/libbridge.so` 与 `libclash.so` 是此 fork 通过 `scripts/build-native.sh` 从仓库源码构建的产物，不是从 kanwox/Nexus 搬来的预编译二进制。这里说“独立构建”只说明二进制由本 fork 构建，不代表其中的 Mihomo 引擎是原创。

- `libbridge.so` 由仓库内的 `native/cpp/` JNI/C 实现和辅助代码编译生成，负责 Android JNI 调用与 Go 核心之间的衔接。维护者说明该桥接实现由其编写；它不是 Mihomo 代理引擎。
- `libclash.so` 由 Go 工具链与 Android NDK 从 `native/golang/` 中的适配/桥接代码、固定版本的 Mihomo 源码及其依赖构建。Go 桥接与 MIPS 集成属于本 fork 的实现；代理规则、分流和 TUN 栈等引擎功能来自 MetaCubeX Mihomo，不能表述为本 fork 原创。

## 固定来源

- Android Go adapter 的上游来源为 [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid/tree/3cc102aab3f251ef460a3b6e2835b6c1eb581e73/core/src/main)，提交 `3cc102aab3f251ef460a3b6e2835b6c1eb581e73`；本 fork 保留了修改后的对应源码。该来源说明的是适配层的上游代码脉络，不表示仓库中的 `.so` 是从该项目或 kanwox/Nexus 复制的预编译二进制；桥接二进制的构建与维护者说明见上节。
- [Mihomo v1.19.32](https://github.com/MetaCubeX/mihomo/tree/v1.19.32)，对应提交 `88dcbf7f1614a67c3b36b848ee3592dfa92ada36`。
- 该版本锁定 `mipstack v0.0.0-20260930071539-961d4b1c1983` 与 `sing-tun v0.4.27`。其余依赖见 `golang/go.mod` 与 `golang/go.sum`。
- GPL 许可文本见 `LICENSE.mihomo`、`LICENSE.upstream`。MIPS 本身的 MPL-2.0 许可不能替代整个应用的 GPL 合规义务；分发二进制时应同时提供本源码、补丁、构建脚本及依赖来源。

## 构建

需要 Linux、Go（本次使用 1.26.8）、Android NDK r28c、Python 3、patch。运行：

```sh
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/28.2.13676358"
bash scripts/build-native.sh
```

脚本取得固定 Mihomo 模块，在忽略的 `native/build/` 内依次应用 `patches/mihomo-borrowed-fd.patch` 与 `patches/mihomo-proxy-only-traffic.patch`，不修改共享 Go 模块缓存。构建标签为 `foss with_gvisor cmfa`，最低原生 API 为 26。两库均成功且所有 ELF LOAD 段达到 16 KiB 对齐后才复制到应用，并生成 `native/SHA256SUMS`。Gradle 打包前检查两库哈希，防止混搭。

Go adapter 模块路径为 `cfa/native`，保留上游包导入路径。生成的 C 头文件留在构建目录。

## 本地修改

1. 严格识别 `system / mixed / gvisor / mips`；未知名称返回错误，不回退 System。
2. JNI 返回 Go 的真实启动状态。Kotlin 先要求 `nativeTunStackContract()` 为已支持的 `1` 或 `2`（拒绝未知版本），再检查返回值；失败不能报告 RUNNING。该版本号是这套源码与库的协议，不是对任意第三方核心的能力探测。
3. 输入 FD 为借用：Android 保留原 `ParcelFileDescriptor`；在 `sing_tun.tunNew` 即将接管时复制 native 专有 FD。前置校验失败不产生副本；创建后的失败和正常停止均由监听器清理副本。Go JNI 入口不关闭原 FD。
4. 记录选择的栈与原生监听器启动成功。启动成功不等于网络连通，也不能据此声称性能提升。
5. 契约版本 2 增加 `nativeStartTunV2`：Kotlin 传入 `{"mtu":9000,"congestion-controller":"cubic"}`，Go 侧校验 MTU 1280–65535 与 `cubic/reno/bbr/bbr3`，写入 `LC.Tun.MTU` 与 `CongestionController`。拥塞控制只对 MIPS 栈生效；旧库仍走版本 1 的入口并忽略这两个选项。
6. IPv6 由 Kotlin 决定：关闭时不添加 IPv6 地址、`::/0` 与 IPv6 DNS，并且不把 IPv6 网关/portal 传给 native；开启时三处一起生效；Builder 任一 IPv6 操作失败则整个启动失败，不继续报告 IPv4-only。关闭时 Android 显式 `allowFamily(AF_INET6)`，允许 IPv6 在 VPN 外通信（不提供 IPv6 防泄漏）。
7. 设置变更仅重建活动隧道；服务控制在 Main 串行执行，耗时启动和流量查询仍使用 Default。STOP 后的迟到 RESTART 不会重新启动，只有显式 START 可清除停止意图。

## 测试

```sh
bash scripts/build-native.sh --prepare-only
cd native/golang
go test -mod=readonly -modfile=../build/native.go.mod -tags 'foss with_gvisor cmfa' ./tun -v
```

Linux socketpair 测试不能代替 Android JNI 加载、系统 VPN 权限、Wi-Fi/蜂窝切换和真实节点测试；以本次实际测试结果为准。
