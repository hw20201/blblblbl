# 原生 MIPS 核心

MIPS 指 **Mihomo IP Stack**，不是 MIPS CPU 架构。应用仍为 Android ARM64。

## 固定来源

- Android Go adapter 与 JNI 桥来自 [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid/tree/3cc102aab3f251ef460a3b6e2835b6c1eb581e73/core/src/main)，提交 `3cc102aab3f251ef460a3b6e2835b6c1eb581e73`。本目录保留修改后的对应源码。
- [Mihomo v1.19.32](https://github.com/MetaCubeX/mihomo/tree/v1.19.32)，对应提交 `88dcbf7f1614a67c3b36b848ee3592dfa92ada36`。
- 该版本锁定 `mipstack v0.0.0-20260930071539-961d4b1c1983` 与 `sing-tun v0.4.27`。其余依赖见 `golang/go.mod` 与 `golang/go.sum`。
- GPL 许可文本见 `LICENSE.mihomo`、`LICENSE.upstream`。MIPS 本身的 MPL-2.0 许可不能替代整个应用的 GPL 合规义务；分发二进制时应同时提供本源码、补丁、构建脚本及依赖来源。

## 构建

需要 Linux、Go（本次使用 1.26.8）、Android NDK r28c、Python 3、patch。运行：

```sh
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/28.2.13676358"
bash scripts/build-native.sh
```

脚本取得固定 Mihomo 模块，在忽略的 `native/build/` 内应用 `patches/mihomo-borrowed-fd.patch`，不修改共享 Go 模块缓存。构建标签为 `foss with_gvisor cmfa`，最低原生 API 为 26。两库均成功且所有 ELF LOAD 段达到 16 KiB 对齐后才复制到应用，并生成 `native/SHA256SUMS`。Gradle 打包前检查两库哈希，防止混搭。

Go adapter 模块路径为 `cfa/native`，保留上游包导入路径。生成的 C 头文件留在构建目录。

## 本地修改

1. 严格识别 `system / mixed / gvisor / mips`；未知名称返回错误，不回退 System。
2. JNI 返回 Go 的真实启动状态。Kotlin 先要求 `nativeTunStackContract() == 1`，再检查返回值；失败不能报告 RUNNING。该版本号是这套源码与库的协议，不是对任意第三方核心的能力探测。
3. 输入 FD 为借用：Android 保留原 `ParcelFileDescriptor`；在 `sing_tun.tunNew` 即将接管时复制 native 专有 FD。前置校验失败不产生副本；创建后的失败和正常停止均由监听器清理副本。Go JNI 入口不关闭原 FD。
4. 记录选择的栈与原生监听器启动成功。启动成功不等于网络连通，也不能据此声称性能提升。

## 测试

```sh
bash scripts/build-native.sh --prepare-only
cd native/golang
go test -mod=readonly -modfile=../build/native.go.mod -tags 'foss with_gvisor cmfa' ./tun -v
```

Linux socketpair 测试不能代替 Android JNI 加载、系统 VPN 权限、Wi-Fi/蜂窝切换和真实节点测试；以本次实际测试结果为准。
