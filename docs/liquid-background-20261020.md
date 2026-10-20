# Nexus：真实快捷卡片液态玻璃与背景

## 基准与范围

- 远端默认分支 main：`94caae7b7f9078409be440d84f6633dc3876c5e5`。
- 实际基准：`build/contact-follow-c91a27b-20261020T082424Z`，`c91a27b22ee43aeb2dad36c2f41c225b2cf2fcab`。main 是该基准的祖先；选择它是为了保留已有取景透视修复、拾起/竖滑优先手势及触点清晰区，而非退回 main 的旧效果。
- 独立工作分支 `work/liquid-background`。不覆盖旧 Demo，不推送、不运行 CI、不安装 JDK/SDK。
- 仅修改三个生产文件：ShortcutRefractiveOptics.kt、GlassBackground.kt、StructuredMeshWallpaper.kt。VPN、安全、业务、图标、布局、手势文件均未修改。

## 实现

沿用真实应用的背景 GraphicsLayer 单次录制/卡片区域重放，使用已有单应矩阵逆向取景及 24dp overscan，AGSL 移动的是实际背景采样坐标，不是画假轮廓。只过滤卡片背景，文字和图标是后绘制的独立层。

移除厚度全范围的白色 Fresnel 混合、额外 relief 对比增强和颗粒。将 Fresnel 限在 12 CSS px 对应的窄边内，混合附近真实场景颜色并按法线方向衰减，避免白雾或彩虹粗描边。中央无位移时省掉两个重复色散 gather；最多 28 次局部输入采样，中央模糊为 9 次、清晰中央为 1 次。未增加全屏模糊、截图读回、卡片时钟或录制层。

背景保留 15 个缓存渐变色块及现有生命周期时钟，默认浅色只用冰蓝/淡紫/青色，不再混入预设橙色等强调色；深色使用低亮度冷色，custom 保留用户颜色。调整渐变过渡并做 1.22×0.82 椭圆拉伸，得到中尺度色带，让位移有可辨认的参照，不引入网格/纹理噪声。

API 33+ 且质量允许时启用实际 AGSL 折射；API 31/32、Shared/低内存档及更旧系统继续沿用既有降级，不声称这些路径具有同等折射能力。

## HTML 映射与刻意偏离

已读取 lab-edge-tuning.html 的 SDF、textureLod、色散、tilt 与 CSS 尺寸实现，并查看两张截图。参考卡片高度为 190 CSS px，应用标准快捷卡片约 118dp，因此长度采用已有比例 118/190=0.62105263，而非把所有数字直接当 dp。

| 参数 | 应用映射 |
| --- | --- |
| 圆角 60 | 37.2632dp，实际半径仍由卡片半短边约束 |
| 超椭圆 2 | 圆角矩形，裁剪与 SDF 一致 |
| 厚度 68 | 42.2316dp，仅为折射剖面尺度，非实体高度 |
| 边宽 12 CSS px | 7.4526dp |
| IOR 2 / 距离 .100 | 无量纲；距离进入 `distance*80*scale` |
| 色散 15.5 | `clamp(15.5*.02,0,.35)=.31`，非像素位移 |
| Fresnel .64 | 保留强度参数，但改为窄边场景反射，不复制 HTML 的白色混合 |
| edge gain 1 / clarity .75 | 无量纲 |
| blur 1.5 | HTML mip LOD，非 dp；采用已有校准 `3*2^(LOD-3)`，约 1.06066dp 的有限九点核；不是像素等同浏览器 mipmap |
| saturation .86 / tint 0 / grain 0 | 保留截图设定；不套用独立 Demo 的 saturation=1/tint=.025/.008 |
| lift 2 / scale1.004 / tilt9 | 保留原映射和当前接触/拾起策略，不覆盖用户已有竖滑与手势响应 |

HTML 的位移上限 `2.5+blur*4` 在 LOD1.5 为 8.5 CSS px，即 5.27895dp。使用连续指数限幅替代硬 min，避免剖面出现突变平台；边缘附加位移沿用平滑限幅。两轴最大倾斜、最大拾起、模糊和凹陷合并保守位移界为 19.1904dp，小于 24dp 采样余量。无需再翻转 Android 背景 Y 轴。

## 测试与限制

- `python3 scripts/check_liquid_optics.py`：6 项通过。覆盖映射、uniform 声明/绑定一致、不同密度位移尺度、60006 个剖面点有限/单调、overscan 保守界、常量中性场景无凭空白边、业务/手势/布局修改范围。
- `git diff --check` 通过。
- 更新已有 ShortcutRefractiveOpticsPolicyTest 参数断言；增加 StructuredMeshWallpaperTest 深色低亮度冷色断言。
- 已检查现有 JUnit4/Robolectric4.16.1/Compose UI 测试，包括折射渲染、触点、布局和业务回归。没有运行这些 Android 测试。
- 环境虽有不完整 Java17 运行时目录，但无 javac/可用完整 Android 构建工具链；未安装、不启动 Gradle 下载，不生成 APK。
- Python 是公式镜像与源码静态检查，不执行 Kotlin/AGSL；未编译、不声称 AGSL 驱动验证、帧率、真实可读性对比或真机验证。最终视觉效果仍须在支持 API33+ 的设备上验收。

## 应用与回退

完整源码 zip 可直接解压审阅，里面是上述实验基准加本次改动，不是独立 Demo。

在拥有基准提交的仓库：

```sh
git switch -c review/liquid-background c91a27b22ee43aeb2dad36c2f41c225b2cf2fcab
git am /path/to/nexus-liquid-background.patch
python3 scripts/check_liquid_optics.py
```

不要把单提交补丁直接当作 main 补丁应用：main 缺少本基准已有的折射/触点文件。需要先切到指定基准，或使用完整源码包。应用中出错可 `git am --abort`；已应用需回退时用 `git revert HEAD`（确保 HEAD 正是本补丁提交），避免破坏已有修改。未修改版本号或发布签名。

后续设备验收：浅色/深色和自定义色；静止/触点/拾起/竖滑；观察色带跨过卡片边缘时的连续偏折而非白色轮廓；核对文字和图标清晰、两列布局与 VPN 动作不变；覆盖 API33+ 与31/32降级及低内存档。
