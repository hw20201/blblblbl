# 按压位置跟着手指走

这次只在独立本地实验里修改快捷卡片，基于 `6c5a4285d5685a47d9a9700118351ef4db97b45f`，分支 `experiment/shortcut-contact-follow-local`。不推送、不生成安装包、不修改草稿或主分支。

## 这次改的不是拖动距离

原先按压的位置只驱动整张卡片的倾斜和全卡霜化，缺少一个真正跟随触点移动的局部接触场；按下后全卡变模糊，也与“贴近后景更清楚”的方向相反。

现在把局部接触 `contact` 与整卡光学/柔化进度分开。卡片平时保持轻柔化和轻影；按下时稍微收缩、阴影减弱，指尖附近的背景更清楚，松开后局部区域以 0.78 阻尼恢复。保留最后触点，让释放时的清晰区原地淡回，不突然跳到卡片中央。

API 33+ 在原来的后景 AGSL 效果里增加圆润、有限半径的接触场。它只改变背景采样，略微减小局部光学厚度，加入小幅凹面方向的采样位移，减少局部模糊、微粒和白色遮盖；没有增加贴图、发光圈或固定亮边。文字图标仍在滤镜外。

## 参数和限制

保留最新网格壁纸、主题色与 `ShortcutFollowupParameters` 的材料原值。新增的是动态映射，而不是替换原参数：

- 静止柔化进度为 0.32；按原模糊参数与缓动曲线换算，API 33+ 平面内部内部滤波 sigma 参数约 1.03dp，触点核心降至 0。这里仍是原来的九点近似滤波，不宣称物理精确的高斯/光线追踪。
- 接触半径为卡片短边的 0.42 倍，限制在 12–64dp。190×118dp 卡片对应约 49.56dp 半径，中心约 17.35dp 完全清晰，外圈平滑过渡；全程使用 dp/实际密度换算。
- 按下用 85ms 进入接触，首帧提供至少 0.35 的反馈；释放/取消的局部接触弹簧阻尼固定 0.78。原有 A/C 整体动作的区别保留，B 的整体回弹仍为 0.78。
- A/B/C 按下缩放分别为 0.997/0.998/0.996，轻影从 0.8dp 降至 0.2dp。没有卡片 Y 向拖移。横向明确意图后的跟手仍最多 ±4dp。
- 按下/小抖动不锁页；卡片内竖滑先让页面滚动，只有明确横向意图跨过原门槛后才锁定这次手势。取消、释放、多指退出维持原来的清理路径，不改导航/VPN动作。
- 减少动态效果时接触状态即时切换和跟点，不增加持续动画。

API 31/32 和无法使用完整 AGSL 的 Shared 质量，只在活跃触点附近用有限大小的羽化层重放共享背景，提供局部变清的退化，不假装具备完整折射凹面。Flat、旧 API 和软件画布保留轻量策略。API 33+ 完整路径没有新增捕获、额外背景源或依赖；旧平台活跃接触会增加一个有界图层，未进行真机 GPU/耗电测量。

## 看预览时需要知道

真实 Dashboard 仍然只采样网格壁纸，没有改成采样邻近卡片。柔和网格本身细节少，因此局部清晰变化比详细纹理夹具含蓄。详细纹理只存在测试目录，不会进入产品代码。

预览取自实际 Compose/Robolectric Native PixelCopy，不是效果示意图。详细纹理夹具关闭折射位移来隔离局部清晰场，分别检查左/右触点的局部细节恢复、远端 ROI 逐像素不变、释放复原、接触核心内的细图标和圆角外见证点。真实触摸测试还会跨 A/B/C、四象限与中心取点，保留竖滑、点击和 VPN 动作验收。

## Android 13 模拟环境的已知限制

新增 SDK33 原生渲染检查遇到 `cannot swizzle value of type 'shader'`。最小探针确认，连标准 `uniform shader src; ... src.eval(p)` 和修改前的网格 shader 都同样失败，常量 shader 则通过；SDK35 的完整原生效果能编译和渲染。因此不是本次 `sampleBlur` 或局部接触独有的问题。

[Robolectric #9691](https://github.com/robolectric/robolectric/issues/9691) 记录了相同错误，维护者解释 SDK26–34 的模拟运行时使用来自 Android S 的 native 代码，切换 SDK35 后该问题消失；[对应源码](https://github.com/robolectric/robolectric/blob/master/shadows/framework/src/main/java/org/robolectric/shadows/ShadowNativeRuntimeShader.java)也保留了旧 native 路径的说明。[Android 官方 AGSL 文档](https://developer.android.com/develop/ui/views/graphics/agsl)与 [RuntimeShader API](https://developer.android.com/reference/android/graphics/RuntimeShader)支持函数及 shader uniform 输入，不能据此推断真机 Android 13 禁止 `child.eval` 或 helper。

保留生产 AGSL，不增加依赖、不为模拟后端改成旧语法。SDK33 的该项先编译最小标准语法；仅遇到已知 child-shader parser 限制时按测试环境受限跳过，并保留探针记录。它不是 Android 13 真机验证通过的证据；真实 API33 的驱动与跟手效果仍待装机补验。Native SDK31 的局部清晰退化与 SDK35 的完整路径分开测量。

## 最终验证结果

完整运行 `:app:testDebugUnitTest :app:detekt :app:verifyNativeLibraries` 成功：共 342 项，341 通过，0 失败、0 错误，1 项仅因上述 SDK33 Native 标准子着色器限制跳过。Kotlin/测试源码编译、静态检查与独立 `sha256sum -c native/SHA256SUMS` 校验通过。

新接触测试覆盖 A/B/C 同一按压扫过中心与四象限、横向限幅、无纵向卡片拖移、释放保留触点、竖滑取消时保留最后位置且不误点击、减少动态效果即时反馈。真实 Dashboard 浅/深色交互帧与原有页面滚动、正常点击、VPN/导航回归共同运行。

Native SDK35 的 mdpi/xhdpi 接触核心纹理方差分别从约 1292/1532 增至约 6813；SDK31 的 mdpi/xhdpi 分别从约 721/1000 增至约 7135。SDK35 Shared 退化也通过。隔离夹具中远端 ROI 改变像素数与释放复原差异均为 0；接触核心内细图标及圆角外见证点保持不变。这些是渲染/局部变化指标，不将方差倍率宣传成肉眼清晰度倍率。

未改构建依赖、版本、原生库、核心、安全、网络或配置中心代码；没有构建应用 APK、推送、创建/修改草稿或发布。前一工作区与其提交保持干净。下一步仍须真机检查触点时延、Android13 实际驱动、滚动主观手感与 GPU/电耗。
