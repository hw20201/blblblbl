# Nexus 图标

采用深炭灰底 `#101823`，原 Nexus 斜体 N 轮廓，青色 `#38E1E8` 至靛蓝 `#596AF8` 的轻量渐变。不增加微小网络节点或钥匙细节，保证小尺寸辨识度。

`nexus-icon.svg` 是可编辑主源。`scripts/generate-icons.py` 生成 Android VectorDrawable、普通/圆形 mipmap、108dp 透明前景、Android 13 单色图层，以及通知栏/快捷设置的 24dp 白色 N。资源均纳入源码，不需要构建 Android 时联网生成。

```sh
python3 -m pip install cairosvg
python3 scripts/generate-icons.py
```

自适应图标由完整深色背景与透明前景分层，未把圆角或阴影烘焙进图层。通知小图标仅有白色前景与透明背景，实际状态栏颜色由系统决定；通知卡片保留系统样式、停止按钮及低打扰设置，只统一 N 图标和强调色。

[Android 自适应图标规范](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)；[通知构建与小图标](https://developer.android.com/develop/ui/views/notifications/build-notification)。系统另行显示的 VPN 钥匙/盾牌提示由 Android 控制，本次更改不隐藏或替换该系统安全提示。
