# 策略小卡片：轻量玻璃实验

截图中的双列 `GROUP` 卡片现在有更通透的底色、克制的外侧亮面和内侧回折边缘。标题、GROUP 标签、延迟和选中蓝框保持原有布局与交互。密集网格没有直接复制快捷卡片较重的宽边折射，而是采用约 2.4dp 内缩的缓存双层边缘；不新增折射着色器。

## 看前后效果

[浅色对比](/workspace/nexus-proxy-group-glass-evidence/comparison/light-top-scroll-before-after.png)与[深色对比](/workspace/nexus-proxy-group-glass-evidence/comparison/dark-top-scroll-before-after.png)：**左侧修改前，右侧修改后；上排页首，下排滚动状态**。拼图仅复制原始截图像素，没有重绘或增强。

这些是实际 `ProxiesScreen`、实际卡片和原有底部导航，通过 Compose / Robolectric NATIVE / PixelCopy 导出的模拟预览，不是手绘示意，也不是手机实拍。固定 400×900 mdpi、seed 734921，并额外捕获背景 0/24 秒。共保留 24 张[原生截图](/workspace/nexus-proxy-group-glass-evidence/native)，包括 API31、API26、强制低内存档和关闭玻璃的回退画面。

## 只动了哪里

只在标签模式、双列、`proxy.isGroup` 为真的小卡片启用；横向组选择器、普通节点、单列/三列、列表模式和经典材质保持原路径。MainActivity 仅多传入既有 `cardScene`，没有新增背景源。已认可的 Dashboard 快捷卡片、底部透镜/导航、配色网格和全局 mesh 文件相对 `f1f7ad8` 均无改动。

卡片重放已经存在的背景 display list，并在前景之外绘制缓存 tint 和 soft-light 双层边缘。**新增背景捕获、逐卡动画/时钟、RuntimeShader、RenderEffect、显式 graphicsLayer 和高斯模糊均为 0**。原有圆角裁剪和屏幕/导航 recorder 仍在；这不等于零分配或零绘制成本。支持设备上，每张可见目标卡片仍增加一次受裁剪的背景重放及静态修饰。API<31 或低内存档跳过卡片重放，保留静态底色与边缘。

## 验证结果

完整 Debug 单元测试 **242 项、65 个测试类通过，0 失败、错误或跳过**。其中新增 11 项覆盖范围判断、降级策略及真实明暗/滚动/回退预览；新材质实际进入路径有单独断言。组选择、卡片选择、卡片内测速点击和普通节点选择均通过。Debug / Release Kotlin 编译和 Detekt 通过；没有生成任何 APK。本轮未重跑 Lint。

[像素校验结果](/workspace/nexus-proxy-group-glass-evidence/comparison/pixel-checks.json)显示，四组成对图在卡片之外且底部导航区域以上的最大通道差均为 0；滚动图中 Tokyo、Osaka 普通节点最大通道差为 0。抽取到的不透明标题核心稳定数分别为浅色页首 666/666、浅色滚动 666/666、深色页首 798/798、深色滚动 799/799。这是已知标题区域的颜色检查，不是 OCR，也不代表每个抗锯齿像素都不变。背景时钟变化没有触发策略内容外层重组。

导航实现未变，但它原有的透镜会看到后面已经变化的小卡片，因此底部导航区域未纳入“卡片外像素不变”断言。最初原样基线与测试中禁用新材质的可复跑基线，8 张截图逐像素一致。

**没有真机帧时间、GPU 内存、温度或电量测量；不能据此宣称省电或性能提升。**

## 本地状态与复现

已先将认可的 `f1f7ad8` 快进合入本地 `fix/shortcut-frosted-depth`，再从该基线建立 `experiment/proxy-group-glass`。仅本地实验；未改 main 或 release 分支，未 push、发布或合入发布分支。

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/ubuntu/Android/Sdk
# 预览专用开关；不进入应用设置。强制执行以免 Gradle 缓存忽略环境变量变化。
PROXY_GLASS_STAGE=before ./gradlew :app:testDebugUnitTest --tests '*ProxyGroupGlassPreviewTest' --rerun-tasks --no-configuration-cache --console=plain
PROXY_GLASS_STAGE=after ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileReleaseKotlin :app:detekt --no-configuration-cache --console=plain
python3 scripts/compare_proxy_group_previews.py --input app/build/outputs/proxy-group-glass --output /workspace/nexus-proxy-group-glass-evidence/comparison
```

完整测试统计、XML、日志与预览清单保存在 `/workspace/nexus-proxy-group-glass-evidence/`。
