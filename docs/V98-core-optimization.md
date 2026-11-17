# Arca V98 核心集成优化

本版基于 V97 的独立副本，界面不改版。调研参考 CMFA、sing-box Android、v2rayNG、FlClash 与 Clash Verge Rev 的工程设计，具体实现适配本项目的 Kotlin/C JNI/Go Mihomo 架构。原 V97 源码与交付目录保持不变。

## 本版实现

- 健康检查异步任务在 Go 工作完成时 defer 释放 JNI 全局引用。Kotlin 等待超时不抢先释放仍由 native 使用的引用。增加 `nativeGlobalReferenceCount` 与 `nativePendingHealthChecks` 诊断符号，用于测试清理是否回到基线。
- 原生日志回调改为短路径入队：512 条 / 512 KiB，单条 UTF-8 最多 16 KiB。后台单消费者按最多 50 ms 或 64 条解析和发布；日志及请求历史各保留最多 1,000 条，改用 ArrayDeque。警告/错误优先保留属于有界、尽力策略，溢出和截断都有累计计数。
- 监控开关按接收时刻记录；清空日志/请求使用代次失效，旧队列或已取出的旧批次不能回灌。默认后台请求留存继续启用。同步 addLog/addRawLogcat 保留立即可见语义，native 接入异步批量路径；同步消息可能先于更早排队的消息展示。
- VPN 服务持有物理网络观察器：INTERNET + NOT_VPN 候选、默认网络变更提示、后台串行快照与事件合并。平台回调不直接调用 JNI，10,000 个重复失效事件不会各自排一个工作任务。停止、启动失败及服务销毁都注销观察。
- 核心所有 DNS 入口共享去重：同网络且有序 IPv4 DNS 不变不再刷新；网络身份变化时同 DNS 仍通知。排除 VPN 自身虚拟 DNS；失网/暂空 DNS 保留已接受值，仅首次无有效 IPv4 DNS 使用既有公共回退。手动清缓存显式 force，仍可重复触发。
- 原生停止放入 IO + NonCancellable，原始借用 FD 在 native listener 停止后关闭。进程级生命周期 Mutex、服务实例 owner token、启动/停止 Job 等待防止旧销毁清理误停新核心；旧实例不得覆盖新实例状态。停止使用 startId，避免旧停止结束 Android 已接受、尚待送达的新 START。
- 前台服务命令先满足 foreground acknowledgement，再进入 no-op 或停止分支；闲置 RESTART 不启动 VPN 并及时结束临时服务。修复快速启停测试中暴露的前台服务时序异常。
- 测速等待保留 5.5 秒超时后返回现有快照，外部协程取消继续抛出。底层已执行的 Go 探测暂不真正取消；本版不宣称完成全局测速预算或请求代际系统。
- ARM64 与 x86_64 都重建真实 Go/JNI 配对库，校验 SHA-256 与所有 ELF LOAD 段的 16 KiB 对齐。通过 `-Parca.nativeAbi=x86_64` 打包单架构模拟器版本；默认 ARM64。

## 已保留的合同

Mihomo v1.19.33、现有两份 Mihomo 补丁、MIPS/mixed/system/gVisor、MTU9000默认、MIPS拥塞控制、IPv6开关、借用FD、代理流量不含DIRECT的统计口径、QuickJS/Rhino、原有UI与连接页生命周期采样均保留。

TUN契约仍是2；新增诊断接口为独立符号。Kotlin/JNI/Go源码和两库应成对使用。统计原始64位接口、持久化检查点、protect失败传播、JNI全部异常出口、Go回调原子上下文、Go GC策略、CPU开关、gomobile与独立进程均留待后续，不把计划写成已实施。

## 构建

V98原生构建使用Go1.27.4、NDK28.2.13676358、API26；原V97文档记录Go1.26.8。工具链变化是独立变量，不能将二进制差异全归因于本版代码优化。

```bash
export ANDROID_NDK_HOME=/your/android-sdk/ndk/28.2.13676358
bash scripts/build-native.sh --abi arm64-v8a
./gradlew :app:assembleDebug

# x86_64真实核心模拟器
bash scripts/build-native.sh --abi x86_64
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Parca.nativeAbi=x86_64

# 原生TUN测试
bash scripts/build-native.sh --prepare-only
cd native/golang
go test -mod=readonly -modfile=../build/native.go.mod -tags 'foss with_gvisor cmfa' ./tun -v
```

Debug包名为 `com.github.arca.android.coretest.v98`，由本次构建机的Debug签名签署，和V97的 `.glasstest.fusion` 分开安装，不继承旧包数据；Android versionCode/versionName沿用87/1.1.0并带 `-v98-core-test` 后缀。Release/R8不属于本次交付。

## 模拟器测试复现

设备：既有x86构建机，Android15/API35、x86_64模拟器。模拟器包有真实libclash与libbridge，覆盖的是实际Android核心调用，不能据此证明ARM64真机性能。

`CoreOptimizationInstrumentedTest`包括：1,000次真实健康检查及JNI引用回落；DNS重复/跨网络/显式刷新；200条日志与请求排空；本机HTTP代理实际转发；10轮VPN启停与迟到RESTART；保持VPN时另一个UID的HTTP传输。

运行前只对一次性测试包授权VPN。持续TUN测试需在模拟器宿主运行回环HTTP端点，并把探针脚本推入测试设备；服务只绑定宿主127.0.0.1，不对外开放。

```bash
python3 scripts/tun-http-fixture.py --port 18798
adb push scripts/tun-probe.sh /data/local/tmp/arca-v98-tun-probe.sh
adb shell appops set com.github.arca.android.coretest.v98 ACTIVATE_VPN allow
adb shell am instrument -w -r -e tunFixturePort 18798 \
  -e class com.github.arca.android.CoreOptimizationInstrumentedTest \
  com.github.arca.android.coretest.v98.test/androidx.test.runner.AndroidJUnitRunner
```

Android instrument 命令可能在测试失败时仍以shell退出码0结束；必须检查报告的 `OK (...)`、failure、crash，不能仅看SSH状态。完整最终计数与原始日志见交付目录的验证报告。

## 日志合成基准

执行 `python3 scripts/log-buffer-benchmark/run.py` 可使用现有Gradle缓存的Kotlin编译器与依赖复现，不安装依赖。路径支持以脚本参数为准；完整方法、源文件哈希与每轮样本见基准说明。

五个独立JVM、12,800条无丢弃输入的机制对照中，64条批量路径将核心快照12,800次降为200次、请求快照9,600次降为200次；调用线程分配中位数约101.7MB降至21.0MB。计数稳定，耗时样本波动大。当前同步兼容入口分配高于旧算法；收益主要针对native已接入的批量路径。50,000条四线程突发测试有约31.754%的丢弃率中位数，属于明确的队列过载策略，不能拿丢弃后的耗时当无损吞吐提升。低负载256条五轮零丢弃。

以上是Linux aarch64/OpenJDK21的JVM合成指标，不是Android耗电、Compose帧率、真实代理吞吐或ARM64手机实测结论。

## 仍需验收与边界

- 未用用户订阅、机场节点或个人凭据做网络性能测试。真实节点协议、蜂窝/Wi-Fi切换、Doze推送、24小时稳定性和真机耗电仍需独立验收。
- API26的公开接口在VPN隐藏物理默认网络时不能精确复刻系统私有评分。当前候选优先未暂停/已验证，平级保留当前，不任意固定Wi-Fi优先；这是明确的退化选择规则。
- DNS输入本轮保持IPv4兼容策略；IPv6-only环境不在本轮扩展，保留最后DNS值不保证跨网络的旧私有DNS仍可达。
- 50ms是目标flush窗口，线程调度/GC可延迟。warn/error优先识别只扫描有界头部，有限内存下仍可能丢日志；超长JSON截断后可能失去请求识别。
- 独立native日志订阅仍为进程生命周期。原生日志到Android logcat保留一次，移除Kotlin重复输出。
- 非协作的同步native停止不可由Kotlin超时强行结束。本版把它移出Main并串行保护资源，没有声称已证明所有Go内部锁在任意故障下都不会等待。
- 原生工具链升级、本版代码、Debug构建和模拟器测试的影响需分别解释，性能结论以同环境实测为准。
