# 快捷卡片：混合玻璃实验

这是独立试验分支，不是待发布版本。基于 `fix/shortcut-frosted-depth` 的 `2c9b62e3a66ac35291b55f120f0e7fdead99fe29`；默认展示 Hybrid，保留可由测试切换的 Baseline。只改 Dashboard 快捷卡片，原有磨砂深度、随背景变化的双层边缘、网格间距、背景运动和底部透镜保持原有路径。

## 这次结合了什么

**结合的是两库的技术思路，不是把两套库叠起来。** 沿用现有的快捷卡片共享背景 `GraphicsLayer`，对应 Haze 的“明确区分源与效果、限定效果面积、复用配置”思路；采用 liquid-glass 的克制折射、轻微色彩提升、显式质量分级思路。没有新增外部依赖，没有全栈升级，也没有把 Haze/LiquidGlass 的实现代码复制进项目。

Hybrid 保留原有外侧亮面与内侧回折面；API 33+ 仅在 4.6dp 窄斜边内增加最大 1.25dp 位移，中央仍保持平整。色彩提升为浅色 1.045 倍、深色 1.025 倍，远低于 liquid-glass Full 默认的 1.4 倍；不增加色散、颗粒纹理、发光白边或额外高斯模糊。光学始终在文字和图标后面，连最低档的光泽也不会覆盖文字。

## 为什么不直接依赖两个库

项目现有 Compose UI `1.7.5`、Material3 `1.3.1`，根构建文件声明 Kotlin/Compose 插件 `2.1.0`、AGP `9.0.1`，Gradle `9.6.1`；AGP 内建 Kotlin 的实际 `kotlinCompilerClasspath` 解析为 `kotlin-compiler-embeddable:2.2.10`，不能把根文件声明的 2.1.0 误当成有效编译器。Android minSdk `26`、compileSdk `36`、targetSdk `35`，JVM target `11`。本分支保持这些文件不变，验证使用 JDK 21。

| 已检查的发布物 | 发布依赖及门槛 | 对本项目的含义 |
| --- | --- | --- |
| `dev.chrisbanes.haze:haze:2.0.1` / `haze-glass:2.0.1` / `haze-blur:2.0.1`，Android variants 为 `*-android` | Compose `1.12.1`、Kotlin stdlib `2.4.20`；核心/Glass/Blur AAR 均声明 `minCompileSdk=37` | 当前 compileSdk 36 不满足明确门槛；不能无升级直接引入 |
| `io.github.nadeemiqbal:liquid-glass:0.2.3`，Android variant 为 `liquid-glass-android` | Compose `1.10.3`、CMP Material3 `1.9.0`、Kotlin stdlib `2.3.21`；AAR `minCompileSdk=36`，minSdk 24 | SDK 数值满足，但发布依赖比当前 UI 栈新；未证明可以保持 Compose 1.7.5 安全兼容 |

以上是实际 `.module`/AAR 元数据，不只是仓库构建版本。生产方 AGP/Gradle 版本不是消费者必须使用相同版本的证明；stdlib 版本也不能直接当成 Kotlin compiler/plugin 最低版本字段。没有绕过 AAR 校验、强制旧依赖或用跳过 Kotlin 元数据检查来冒险集成。

Haze 的公开用法是 `rememberHazeState()`、`Modifier.hazeSource(state)`、`HazeInput.Sources(state)`，配合 `hazeBlur(style = HazeBlurStyle { blurRadius(...) })` 或 `hazeGlass(style = GlassStyle.regular)`；内建效果提供 `HazePerformanceMode.Default/Balanced/Performance/Quality/Fixed(...)`。**Haze 2 本身已有 Glass 与性能分档**，不采信 liquid-glass README 中“Haze 不提供分档”的旧式对照说法。[Haze 2.0.1 性能文档](https://chrisbanes.github.io/haze/2.0.1/performance/)也明确要求真机测量，不能将其参考设备上的收益直接套到本项目。

LiquidGlass 的公开入口是 `rememberLiquidGlassState(quality)`、`liquidGlassSource(state)`、`liquidGlass(state, shape, blurRadius, saturation, tint, borderHighlight, grain, grainSeed, refraction)` 与 `GlassCard`，质量为 `Full/Medium/Fallback`。`rememberDynamicSheen` 会周期性读取背景颜色；本原型不引入此类 bitmap readback/轮询，因为现有 shader 可直接在渲染中响应局部背景。

两套库的状态和 source 管线不互换，也没有开箱即用的公开跨库 source 适配器。LiquidGlass 的 state 虽公开只读 `backdrop`，构造器却是 internal，自己的 source 负责记录；Haze 使用自己的 source nodes/state。给同一背景同时挂两种 source modifier 不能自动合成一次捕获。若将来真正引入其中一个库，应只保留一个源系统，再以其自定义效果 API 实现剩余视觉，而不是双重捕获和双重模糊。

## 每张卡片与共享源的成本

| 路径 | 原基线 | Hybrid |
| --- | --- | --- |
| 快捷卡片背景源 | 一份全屏共享 display list，所有快捷卡片复用 | 不变；新增源为 0 |
| 底部导航场景 | 单独的既有 recorder | 不变 |
| API 33+ 普通设备，每卡 | 一个既有背景效果层/RuntimeShader；中央 1 次采样、斜边 2 次 | 层数不变；中央 1 次、斜边 3 次，斜边新增一次折射采样；同一 shader 内少量色彩算术 |
| API 31/32 | 背景重放与缓存 soft-light 边缘 | Shared：沿用此路径，无 AGSL，不增加模糊 |
| 低 RAM 或 API <31 | 原有基础材质；低 RAM 本身原先不参与分档 | Flat：快捷卡片不重放背景、不创建卡片 RuntimeShader/背景效果 target，仅绘制底色与静态光泽 |
| 高斯滤波 | 快捷卡片 0 次 | 仍为 0 次（共享 mesh 本来已经柔化） |
| 每卡时钟、轮询、CPU 背景读回 | 无 | 无 |

Flat **不是全局零分配**：屏幕和底部导航现有 recorder 未修改，圆角裁剪及普通 Compose 绘制也仍存在。未引入全分辨率的第二背景缓存。`RenderEffect` 按尺寸、密度、圆角、主题和设计切换缓存，纯背景运动不会逐帧重建。GPU 实际是否融合、缓存几份 render target 由平台决定，不能把“一个 RenderEffect”误说成实测物理 GPU pass 数。

## 怎样看 A/B

使用真实 `DashboardScreen`、实际卡片与底部 dock，在 Robolectric `NATIVE` 模式通过 `PixelCopy` 导出；不是手绘示意图。场景固定为同一 seed `734921`、同一几何尺寸，分别取背景时钟 0 秒与 24 秒。测试对浅色/深色分别运行 Baseline 与 Hybrid，另取 API31 Shared、API26 Flat、API35 强制 Flat 的回退截图。

输出位于 `app/build/outputs/shortcut-hybrid/`。成对图名为 `api35-{light|dark}-{baseline|hybrid}-{t00|t24}.png`。查看拼接图时：**左列基线、右列实验；上排 0 秒、下排 24 秒**。整页图检查布局和导航，卡片裁剪图检查边缘厚度、背景颜色响应、标题清晰度。拼接只复制原始截图像素，不重绘、增强或修饰效果。

## 代码与验证

生产代码只涉及 `ui/glass/GlassSurface.kt`、`CardBackdrop.kt`、`ShortcutMeshOptics.kt` 和新增 `ShortcutGlassDesign.kt`；测试新增 `ShortcutHybridRenderingTest.kt`、`ShortcutHybridPolicyTest.kt`，截图对比脚本为 `scripts/compare_shortcut_hybrid_previews.py`。`NavigationLens.kt`、背景生成和配色网格文件不改。

可复现命令（不生成 APK）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/ubuntu/Android/Sdk
./gradlew :app:testDebugUnitTest --tests '*ShortcutHybrid*' --no-configuration-cache --console=plain
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileReleaseKotlin :app:lintDebug :app:lintRelease :app:detekt --no-configuration-cache --console=plain
./gradlew :app:lintRelease :app:detekt --continue --no-configuration-cache --console=plain
python3 scripts/compare_shortcut_hybrid_previews.py
```

`--no-configuration-cache` 是本次命令行设置：基线自带的 `verifyNativeLibraries` task 捕获了 Gradle script 对象，首次缓存配置会失败。未为此改写用户的原生校验任务或 `gradle.properties`。

验证结果：新增的 9 项测试全部通过；完整 Debug 单元测试为 **231 项、63 个测试类，0 失败、0 错误、0 跳过**。Debug/Release Kotlin 编译和 Detekt 均通过。Debug Lint 为 1 error / 81 warnings，Release Lint 为 1 error / 80 warnings，二者都因原有 `MihomoTileService.kt:63` 的 `StartActivityAndCollapseDeprecated` 报错未通过；该文件相对基线未改，原基线留存的 Lint XML 也包含同一错误，本次改动文件没有新增 Lint 条目。未通过过滤规则或新增 Lint baseline 隐藏此问题。

共导出 14 张原生截图：A/B 八张，加三个降级场景各两个时刻。A/B 左列四张基线截图与原基线 `2c9b62e` 已有 native 渲染逐像素一致，最大通道差为 0；四组 A/B 的快捷卡片外区域和底部导航最大通道差也为 0。背景从 0 秒到 24 秒，Hybrid 浅色卡片中心/边缘 RGB-sum 平均变化为 18.686 / 20.524，深色为 24.882 / 41.341；标题不透明核心像素浅色 91/91、深色 96/96 保持稳定，Dashboard 不随纯背景运动重组。这些数值用于检验响应和字形，不是画质评分或性能指标。深色 A/B 差异较小，属于刻意克制的增量，而非明显改换材质。

真实设备的首次 shader 创建耗时、滑动/拖拽帧时间、GPU 内存、热量和功耗尚未测量；模拟渲染只验证视觉和行为，**不证明省电或真机性能改善**。本分支不 push、不 merge、不改 1.0.22 Draft APK，也不执行 Release APK 打包。

## 上游与许可

参考的固定源为 [Haze 2.0.1](https://github.com/chrisbanes/haze/tree/2.0.1)（`347c67a1f190b71f439f3b9ca7ffb326d0044629`）及 [liquid-glass v0.2.3](https://github.com/NadeemIqbal/liquid-glass/tree/v0.2.3)（`f3a5b5047f693432b338f5e34efa9b287f0ee62b`），两者均为 Apache-2.0。发布元数据分别见 [Haze Android 核心](https://repo.maven.apache.org/maven2/dev/chrisbanes/haze/haze-android/2.0.1/haze-android-2.0.1.module)、[Haze Glass](https://repo.maven.apache.org/maven2/dev/chrisbanes/haze/haze-glass-android/2.0.1/haze-glass-android-2.0.1.module)、[LiquidGlass Android](https://repo.maven.apache.org/maven2/io/github/nadeemiqbal/liquid-glass-android/0.2.3/liquid-glass-android-0.2.3.module)。本实现为独立编写，无上游源文件或代码片段再分发，因此未改项目许可证；若后续复制上游源码或分发二进制依赖，必须保留适用许可证、版权与 NOTICE 并标注修改。
