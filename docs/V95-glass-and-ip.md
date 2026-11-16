# V95 玻璃面板与内置 IP 检测

本版基于 V94（测试 579 个、578 通过、0 失败、1 跳过），只做两处改动：面板统一换玻璃、IP 检测内置化。

## 一、所有按钮展开的面板改成玻璃效果

原则：直接复用应用里已有的 Glasmio 毛玻璃实现（`ui/glass/GlassSurface`，走设置页“玻璃效果”里的同一套雾化半径、折射高度、折射强度、染色强度、材质浓度参数）。没有新写玻璃 shader，没有引新库。

此前面板外壳（morph 动画壳、`GlassModalBottomSheet` 底单壳、设置详情选项行）已经是这套玻璃，但面板里面的卡片和行还是实色深色底（`glassPalette.panelStart` 半透明填充、`LoonCard` 实色、模式选择器 alpha 1 的不透明渐变），整块面板看起来仍是实色深色底。本版把这些内层实色换成同一套 `GlassSurface`：

- `ui/screens/OutboundIpPanel.kt`：`IpPanelCard` 两张卡改成 `GlassSurface`（14dp，`SHORTCUT_CARDS`）。
- `ui/screens/DashboardShortcutActionPanel.kt`：`QuickInfoCard`、`QuickHint`、`QuickRow`、`QuickAppRow` 改成 `GlassSurface`（12/14dp，`SHORTCUT_CARDS`；选中行用 `accent = IosBlue` 保留选中染色）。
- `ui/screens/DashboardScreen.kt`：模式选择器三处不透明面改成玻璃——外壳渐变、展开时的已选面（`accent = panelAccent`）、选项行；添加快捷方式底单里的实色行改成玻璃。测试 tag `mode-picker-opaque-surface` 随之改名 `mode-picker-glass-surface`，其余 tag 不变。
- `ui/screens/RulesScreen.kt`、`ui/screens/RequestLogsScreen.kt`：规则详情与请求详情底单里的实色代码块（`LoonBg`）改成 `GlassSurface`。
- 设置详情选项行（`SettingsDetailMorphPage.kt` 的 `SettingsDetailOption`）本来已是玻璃，不动。

没动的部分：底栏玻璃的要求不变（内容滑到底栏后面透过玻璃看得见，不靠改不透明解决）；设置页“玻璃效果”本身的参数与默认值不动；`GlassSurface`、`GlasmioGlassRenderer`、`GlassSettings` 等玻璃实现文件一个字没改。仪表盘上的“添加快捷方式”按钮、空状态图标 tile、 segmented 控件选中态等不是面板的控件，保持原样。

## 二、IP 检测内置，不用配凭据（照 BettBox v1.19.8 的路子）

入口不变：仪表盘 → IP 测试 → 手动点“检测当前 IP”。打开面板不发起查询。

- 出口 IP：免费接口并行查、先成功为准。主源 `http://ip-api.com/json/?lang=zh-CN`，备用 `https://get.geojs.io/v1/ip/geo.json`，各 8 秒超时（沿用既有 `newClient` 的超时），输的那个被取消。两路都失败才报错。显示出口 IP、国家 / 城市、组织 / ASN。
- 风险：默认不再要求凭据。直接 GET 公开网页 `https://scamalytics.com/ip/<ip>`（10 秒超时，不跟随重定向），OkHttp 自动解 gzip，正则解析 HTML 里的 Fraud Score（0–100）和检查项（数据中心 / Hosting、VPN、Tor、服务器、公开代理、网页代理、住宅代理；黑名单 FireHOL、IP2Proxy Lite、IPsum、Spamhaus、X4Bnet Spambot），以及 ISP 与组织。分数 0 显示为 1（存的值仍是 0）。详情卡底部注明“数据来源：Scamalytics”。网页无分数且无检查项时按失败处理，保留本次出口 IP 并显示错误，不拿旧数据充数。
- 流量走向不变：VPN 开时检测请求走本地代理端口（127.0.0.1:7890），没开时直连，沿用既有 `lookupClient`，没有硬编码绕过。
- 凭据仍是可选项：已配 Scamalytics 账号和 Key 的用户，检测优先走官方 API（api11/api12 不变，测试模式不变）；没配的走上面这套内置检测。API 失败时不静默回落到网页，照旧显示错误。
- 配置入口位置（2026-11-16 主人定）：IP 测试面板右下角固定、低调——小字（10.5sp）、弱对比（`LoonTextMuted`）、无按钮样式，文字“Scamalytics API 配置”。它画在滚动容器之外，不会随页面滚动或面板内容活动而移动：morph 面板里是面板底部的一行，独立底单里是覆盖在右下角的浮层（滚动内容留了 40dp 底部间距，不会被盖住）。凭据未读完或正在检测时不可点；点开是原来的 `ScamalyticsSettingsSheet`（加密保存、导入 JSON、删除都不变）。设置页下端原有的模糊效果不动。

## 三、改动文件

主源码（7 个）：

- `app/src/main/java/com/github/arca/android/data/OutboundIpRepository.kt`——出口 IP 双源并行、Scamalytics 网页解析、内置/凭据双路。
- `app/src/main/java/com/github/arca/android/ui/screens/OutboundIpPanel.kt`——玻璃卡片、内置检测文案与字段、配置入口组件 `ScamalyticsConfigLink`。
- `app/src/main/java/com/github/arca/android/ui/screens/DashboardShortcutActionPanel.kt`——玻璃行卡、morph 面板右下角固定入口。
- `app/src/main/java/com/github/arca/android/ui/screens/OutboundIpSheet.kt`——底单右下角固定入口。
- `app/src/main/java/com/github/arca/android/ui/screens/DashboardScreen.kt`——模式选择器与添加快捷方式底单行改玻璃。
- `app/src/main/java/com/github/arca/android/ui/screens/RulesScreen.kt`、`app/src/main/java/com/github/arca/android/ui/screens/RequestLogsScreen.kt`——详情代码块改玻璃。

测试（5 个，新增 1 个）：

- `app/src/test/java/com/github/arca/android/data/OutboundIpRepositoryTest.kt`——新双源与竞速断言。
- `app/src/test/java/com/github/arca/android/data/ScamalyticsWebTest.kt`（新增）——网页解析、分数 0→1、内置/凭据两路、失败保留 IP。
- `app/src/test/java/com/github/arca/android/data/ScamalyticsTransportTest.kt`——旧源 host 断言换成新源。
- `app/src/test/java/com/github/arca/android/ui/screens/OutboundIpSheetTest.kt`、`DashboardShortcutMaintenanceTest.kt`——入口 tag 与文案跟随改动。

## 四、已知边界

- 网页解析依赖 scamalytics.com 公开页的现有结构（Fraud Score 横幅、内嵌 score/risk JSON、Yes/No 表格）；对方改版后解析失败会显示错误、保留出口 IP，不会静默给假分数。
- ip-api.com 免费接口有请求频率限制（每分钟 45 次），面板是手动检测，正常使用碰不到。
- 模拟器验证只覆盖界面与流程；真机 VPN 下的检测走向与分数以主人真机为准。
