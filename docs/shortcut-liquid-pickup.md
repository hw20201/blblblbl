# 快捷玻璃与 B 模式抬升补丁

## 基准与范围

- 仓库：https://github.com/hw20201/blblblbl
- 精确基准：`76b59078963f8ef8de976c2ed9d5018654de4653`（main / 1.0.27）。
- 这是源码候选补丁，不是已通过真机验收的 APK；不推送 GitHub、不发布版本。
- 目标是白天、黑夜都能看出实际抬升，不是只给黑夜模式加描边。

## 改动

1. **B 模式真正上移**：调校版从 0dp 改为 -8dp；接触跟手时由 0.998 倍收缩改为 1.015 倍轻微放大。关闭接触跟随的对照路径为 1.018 倍。
2. **跟手更易辨认**：横向最大倾角 3.2°、纵向 2.8°，持按倾角重定向 48ms，抬升 95ms；回落阻尼 0.86。A/C 的参数及 85ms 按压时长保留。
3. **离地层次**：独立的原位地面投影在外层槽位绘制，玻璃本体单独上移；平坦投影层阴影高度从很弱的约 0.2dp 提高到按压时约 10.2dp，避免把倾斜顶点直接当投影高度产生长影。
4. **点击不跟着漂**：手势与 clickable 放到固定外层槽位；子卡片仅执行视觉变换，原有两列布局、编辑排序、竖滑优先和横向锁定逻辑不改。
5. **玻璃材质**：保留共享背景显示列表与单条 AGSL 折射链，适配 Backdrop 的镜片边缘曲线；补充窄方向性镜面反光，反光随倾角和抬升响应，黑夜补一条克制的冷色回光。中部不覆盖白色蒙层，文字和图标不进入模糊/折射。
6. **折射同步**：实际 pitch/roll 传入 shader，跟手材质不再使用与卡片姿态不一致的九度触点估计；夹紧到 ±9° 光学范围。保留已有四角 homography 世界锚定，避免二次投影。
7. **低版本**：API33+ 使用真实 AGSL 背景折射；API31/32 保留模糊降级，API26–30/低内存保留已有静态材质降级。B 的实际上移不依赖 AGSL。

## 网上选型与“引入”的准确含义

比较了 Kyant0/AndroidLiquidGlass（Backdrop）、PrismalAGSL、Cloudy 和 QWEA0/Liquid-Glass-Android。

首选参考：[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)，版本 2.0.1 / `65ab177e90e5c1d8c62e70cf7755841982da65f6`，Apache-2.0。

**本补丁不是完整接入 Backdrop Maven 依赖。** Nexus 使用 Kotlin 2.1.0 / Compose 1.7.5，而 Backdrop 2.0.1 上游构建使用 Kotlin 2.4.10 / Compose 1.12.0；直接添加最新版会引入编译器/Compose 迁移风险。此版只引入明确署名的镜片曲线源码适配，将其放进现有真实背景折射管线，不为几个快捷块升级全项目 UI 工具链、不重复捕获或叠加第二套 shader。完整的库接入仍需单独迁移与构建验收。

其他项目：
- https://github.com/styropyr0/PrismalAGSL ：Compose/AGSL，但同样需要核对 Kotlin metadata。
- https://github.com/skydoves/Cloudy ：含玻璃和 blur；当前版本工具链更高，不能把旧 blur 版本当成已有 liquidGlass API。
- https://github.com/QWEA0/Liquid-Glass-Android ：View/XML 优先，不适合为现有 Compose 卡片额外包 AndroidView。

授权：`THIRD_PARTY_NOTICES.md`、`third_party/backdrop/LICENSE`，保留上游来源与修改说明。

## 验证边界

已执行：
- `git diff --check`。
- `python3 scripts/check_shortcut_pickup.py`：9 组静态/数值检查，包括 B 位移、手势代码不变、uniform 成对绑定、独立投影、镜片曲线单调/有界、24dp 采样余量、授权及业务改动边界。
- 使用 Gradle 内置 Kotlin 2.3.21 PSI 解析器检查修改的 Kotlin 文件：这是语法检查，不是类型检查/Android 编译。
- 干净基准上的 `git am` 重放与最终文件树一致性检查（见交付验证结果）。

新增 Android 回归用例：`ShortcutVisiblePickupTest` 的浅色/深色实际快捷组合测试，检查实际前景 >=6dp 上移、释放复位、槽位不移、点击有效；同步修改 B 数值和原先“所有模式不竖移/都收缩”的过期断言。

**未通过本地 Android 构建或 Robolectric 测试。** 执行 `./gradlew --offline --no-daemon -Dorg.gradle.jvmargs=-Xmx384m testDebugUnitTest` 时，离线环境缺 AGP 9.0.1，项目配置阶段失败；不是测试通过，也不是已证实代码编译失败。此环境也未准备 Android SDK。不安装重型 SDK、不触发远端签名构建。

尚需：在正常构建机执行 detekt/testDebugUnitTest/assembleDebug，并在真机浅色/深色检验折射、抬升、触控清晰区、GPU 兼容及帧率。CPU/语法检查不能证明视觉效果已经满意。

## 应用与撤销

```sh
git switch -c ui/shortcut-liquid-pickup 76b59078963f8ef8de976c2ed9d5018654de4653
git am /path/to/shortcut-liquid-pickup.patch
python3 scripts/check_shortcut_pickup.py
./gradlew detekt testDebugUnitTest assembleDebug
```

若中断应用：`git am --abort`。成功合入后如需撤销，使用 `git revert <此补丁提交>`。

不修改 VPN、订阅、安全、原生库、背景配色或版本号；后续其他 UI 提交不能盲目重复应用此补丁。
