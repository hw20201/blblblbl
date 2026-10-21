# 1.0.28（34）快捷玻璃抬升对比版

## 与另一个 28 的关系

本分支 `review/liquid-pickup-76b5907` 独立基于 1.0.27 的精确提交 `76b59078963f8ef8de976c2ed9d5018654de4653`，应用用户提供的 `shortcut-liquid-pickup.patch`，再追加下面的审查修正。它不基于已有的 1.0.28（32）或 1.0.29（33），不修改那些分支、Draft 或 main，也不创建 PR。

应用内仍显示 **1.0.28**；内部 `versionCode` 为 **34**，高于已有的 32 和 33，以允许使用相同发布签名覆盖安装。不同对比包使用同一包名，不能并排安装；系统通常不允许直接降级安装低版本号，请勿为对比而贸然卸载含有配置的应用。

## 这版改了什么

B（FingerTilt）在浅色和深色下都上移 8dp。接触跟手时放大至 1.015 倍；关闭接触跟随的调校路径为 1.018 倍。横向最大倾角 3.2°、纵向 2.8°，持按倾角重定向 48ms、抬升 95ms，回落阻尼 0.86。A/C 的动作参数和 85ms 按压时长保留。

原位地面投影与移动玻璃本体分层。手势、点击和测试标记位于固定外层槽位；内层只执行视觉变换，保留两列布局、编辑删除、纵向滚动优先和横向锁定逻辑。

背景继续使用共享显示列表和单条 AGSL 折射链，保留四角 homography 世界锚定，不做二次投影或第二套背景捕获。镜片曲线与窄方向性边缘反光随按压响应；文字和图标不进入背景模糊或折射。

API33+ 使用完整 AGSL 路径，API31/32 保留原有模糊降级，更低版本或轻量环境保留已有静态降级。实际上移不依赖 AGSL。

## 审查后追加的修正

原补丁将新的姿态角与 `contactMode` 共用开关，存在两个实际问题：A/C 的接触路径会丢失原触点法线；非接触跟随的 B 路径可能把实际姿态和旧九度触点估计叠加。

现在通过默认关闭的 `poseFollow` / `poseMode` 独立选择：只有启用动态效果的 tuned B + optical followup 使用实际动画姿态；A/C 和旧路径保留原触点法线，不混算。pitch 采用 `-Compose rotationX` 的光学存储约定，shader 再取负，净方向与原补丁实际 B 姿态一致；不是未经验证地反转法线。

新增四组真实 Compose 回归测试，覆盖四角触点、48ms 重定向、释放重按连续性、其他路径、减少动态效果与冻结预览。原有浅色/深色实际前景上移、槽位固定、复位及点击测试保留。

## 第三方适配的准确含义

[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 2.0.1，提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6`，Apache-2.0。只适配其 `Shaders.kt` 中的 `circleMap(x) = 1 - sqrt(1 - x*x)` 镜片边缘曲线和法线/光照方向高光思路，放入现有有界单通道折射。

这**不是完整接入 Backdrop Maven 库**。上游版本目录为 Kotlin 2.4.10 / Compose 1.12.0，本项目现有配置为 Kotlin 2.1.0 / Compose 1.7.5；本次不升级全项目工具链、不添加二进制依赖。上游曲线、版本目录与完整许可证已核对，许可证与仓库内副本逐字节一致。署名见 `THIRD_PARTY_NOTICES.md`，完整文本见 `third_party/backdrop/LICENSE`。

## 构建和验收边界

原始候选补丁交付时，另一环境缺 AGP/SDK，只有静态和语法检查；该限制不是本次环境状态。本次已找到完整 Android SDK 36、build-tools 36.0.0、AGP 9.0.1 缓存和 JDK21，使用实际 Android 构建与 Robolectric Native Graphics 测试。

本次构建门禁为静态/数值检查、`detekt`、完整 `testDebugUnitTest`、`assembleRelease`；签名通过 main 上现有、未修改的 workflow，以精确 source SHA 检出本分支。仅在这些门禁及独立 APK 身份/签名/ABI/native 校验通过后创建新的 GitHub Draft。具体结果以该 Draft 的 CI 链接、验证记录和 APK 哈希为准，本文不预先宣称执行成功。

验证命令（使用正确 SDK 路径）：

```sh
python3 scripts/check_shortcut_pickup.py
ANDROID_HOME=/home/ubuntu/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew detekt testDebugUnitTest assembleRelease --no-configuration-cache
```

本地没有发布签名凭据时 `assembleRelease` 产物为未签名候选，不能当成最终交付包；最终交付必须来自现有 Release-key CI。

不修改 VPN、订阅、安全、原生库、背景配色、依赖或签名工作流。仅版本元数据从 1.0.27（31）改为 1.0.28（34），静态门禁精确限制只允许这两个构建字段变化。

Robolectric 渲染/交互检查不能替代真机 GPU、帧率、发热、电量或主观视觉验收；本版用于用户继续比较观感与手感，不宣称视觉效果已获满意验收。
