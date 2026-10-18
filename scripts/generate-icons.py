#!/usr/bin/env python3
"""Generate source-controlled Android icons from assets/nexus-icon.svg.
Requires Python 3 and CairoSVG (python3 -m pip install cairosvg).
"""
from copy import deepcopy
from pathlib import Path
import xml.etree.ElementTree as ET
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SVG_NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG_NS)
source = ET.parse(ROOT / "assets/nexus-icon.svg").getroot()
mark = source.find(f".//{{{SVG_NS}}}path[@id='nexus-mark']")
background = source.find(f"{{{SVG_NS}}}rect[@id='nexus-background']").attrib["fill"]
path = mark.attrib["d"]
ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def write(relative, content):
    target = RES / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")

def vector(size, body, extra=""):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector {ns}{extra}
    android:width="{size}dp" android:height="{size}dp"
    android:viewportWidth="{size}" android:viewportHeight="{size}">
{body}
</vector>'''

write("drawable/ic_launcher_background.xml", vector(108, f'    <path android:fillColor="{background}" android:pathData="M0,0h108v108h-108z" />'))
colored = f'''    <group android:scaleX="2.9" android:scaleY="2.9" android:translateX="19.2" android:translateY="19.2">
        <path android:pathData="{path}">
            <aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="4" android:startY="6"
                    android:endX="20" android:endY="19" android:startColor="#38E1E8" android:endColor="#596AF8" />
            </aapt:attr>
        </path>
    </group>'''
write("drawable/ic_launcher_foreground.xml", vector(108, colored, ' xmlns:aapt="http://schemas.android.com/aapt"'))
mono = f'''    <group android:scaleX="2.9" android:scaleY="2.9" android:translateX="19.2" android:translateY="19.2">
        <path android:fillColor="#FFFFFFFF" android:pathData="{path}" />
    </group>'''
write("drawable/ic_launcher_monochrome.xml", vector(108, mono))
status = vector(24, f'    <path android:fillColor="#FFFFFFFF" android:pathData="{path}" />')
write("drawable/ic_stat_nexus.xml", status)
write("drawable/ic_qs_tile.xml", status)
# Keep the old resource name as a compatibility alias, not the obsolete key art.
write("drawable/ic_vpn_key.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<inset {ns} android:drawable="@drawable/ic_stat_nexus" />''')
for version in (26, 33):
    themed = '\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />' if version == 33 else ''
    adaptive = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon {ns}>
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />{themed}
</adaptive-icon>'''
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        write(f"mipmap-anydpi-v{version}/{name}", adaptive)
write("values/nexus_brand.xml", '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="nexus_brand_accent">#38E1E8</color>
</resources>''')

def png(tree, target, pixels):
    target.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=ET.tostring(tree), write_to=str(target), output_width=pixels, output_height=pixels)

# Android expands 108dp layers around a 72dp visible icon viewport.
legacy = deepcopy(source)
legacy.set("viewBox", "18 18 72 72")
foreground = deepcopy(source)
foreground.remove(foreground.find(f"{{{SVG_NS}}}rect[@id='nexus-background']"))
round_icon = deepcopy(legacy)
clip_defs = ET.SubElement(round_icon, f"{{{SVG_NS}}}defs")
clip = ET.SubElement(clip_defs, f"{{{SVG_NS}}}clipPath", {"id": "round-mask"})
ET.SubElement(clip, f"{{{SVG_NS}}}circle", {"cx": "54", "cy": "54", "r": "36"})
clip_group = ET.Element(f"{{{SVG_NS}}}g", {"clip-path": "url(#round-mask)"})
for element in list(round_icon):
    if element.tag != f"{{{SVG_NS}}}defs":
        round_icon.remove(element)
        clip_group.append(element)
round_icon.append(clip_group)
for density, launcher_px, foreground_px in (
    ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
):
    folder = RES / f"mipmap-{density}"
    png(legacy, folder / "ic_launcher.png", launcher_px)
    png(round_icon, folder / "ic_launcher_round.png", launcher_px)
    png(foreground, folder / "ic_launcher_foreground.png", foreground_px)
png(legacy, ROOT / "app/src/main/ic_launcher-web.png", 512)
print("Generated launcher, round, adaptive, themed, notification and Quick Settings icons.")
