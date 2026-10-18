# Nexus 图标

应用启动图标采用用户提供的黑底白猫图，原图保存在 `nexus-launcher.webp`。保留猫脸、眼睛、嘴角和胡须，通过去掉原图外围白角、提取透明前景、居中缩放和增加安全留白适配 Android；不重新绘制猫脸。

`scripts/generate-icons.py` 只生成启动图标：五组密度的普通/圆形 mipmap、108dp 透明前景、Android 13 单色图层引用和 512px web 图标。自适应图标使用纯黑背景，前景落在安全圆内，由系统决定最终圆形或圆角外形。

```sh
python3 -m pip install pillow numpy opencv-python-headless
python3 scripts/generate-icons.py
```

通知与快捷设置继续保留原 N 图案：`nexus-icon.svg` 留作原品牌源；`ic_notification_brand` 及其背景/前景为原通知大图案的独立副本，`ic_zz_notification_nexus_v107`、`ic_stat_nexus` 和 `ic_qs_tile` 不变。生成脚本不写入通知或快捷设置资源，也不改变强调色、通知行为、VPN 或 MIPS 功能。
