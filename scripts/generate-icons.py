#!/usr/bin/env python3
"""Adapt assets/nexus-launcher.webp to Android launcher resources.

Requires Pillow, numpy and opencv-python-headless. Only launcher resources are written; notification,
Quick Settings and the original N artwork remain independent and unchanged.
"""
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def write(relative, content):
    target = RES / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


# Keep the original grayscale artwork while removing only bright regions
# connected to the outer page edge (the supplied image's four white corners).
source = Image.open(ROOT / "assets/nexus-launcher.webp").convert("RGB")
gray = np.asarray(source.convert("L")).copy()
height, width = gray.shape
components, labels, stats, _ = cv2.connectedComponentsWithStats((gray > 8).astype("uint8"))
border_labels = np.unique(np.concatenate((labels[0], labels[-1], labels[:, 0], labels[:, -1])))
alpha = gray.copy()
alpha[np.isin(labels, border_labels)] = 0
# Ignore isolated compression specks, not the connected cat or its eye highlights.
alpha[stats[labels, cv2.CC_STAT_AREA] < 16] = 0
alpha[alpha < 8] = 0
mark = Image.new("RGBA", source.size, "white")
mark.putalpha(Image.fromarray(alpha))
mark = mark.crop(mark.getbbox())

# Keep every non-transparent mark pixel inside the 66dp adaptive safe circle,
# with additional breathing room. No pre-rounded background in adaptive layers.
a = np.asarray(mark.getchannel("A"))
y, x = np.where(a > 8)
cx, cy = mark.width / 2, mark.height / 2
radius = np.sqrt((x - cx) ** 2 + (y - cy) ** 2).max()
scale = 31.0 / radius


def foreground(pixels):
    factor = pixels / 108
    size = (round(mark.width * scale * factor), round(mark.height * scale * factor))
    result = Image.new("RGBA", (pixels, pixels))
    resized = mark.resize(size, Image.Resampling.LANCZOS)
    result.alpha_composite(resized, ((pixels - size[0]) // 2, (pixels - size[1]) // 2))
    return result


def legacy(pixels, rounded=False):
    # Match Android's 72dp visible viewport of the 108dp adaptive layer.
    full = foreground(pixels * 3)
    inset = full.width // 6
    visible = full.crop((inset, inset, full.width - inset, full.height - inset))
    result = Image.new("RGBA", visible.size, "black")
    result.alpha_composite(visible)
    result = result.resize((pixels, pixels), Image.Resampling.LANCZOS)
    mask = Image.new("L", (pixels * 4, pixels * 4))
    draw = ImageDraw.Draw(mask)
    bounds = (0, 0, pixels * 4 - 1, pixels * 4 - 1)
    if rounded:
        draw.ellipse(bounds, fill=255)
    else:
        draw.rounded_rectangle(bounds, radius=pixels * 0.18 * 4, fill=255)
    result.putalpha(mask.resize((pixels, pixels), Image.Resampling.LANCZOS))
    return result


write("drawable/ic_launcher_background.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS} android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#000000" android:pathData="M0,0h108v108h-108z" />
</vector>''')
for name in ("foreground", "monochrome"):
    write(f"drawable/ic_launcher_{name}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<bitmap {NS} android:src="@mipmap/ic_launcher_foreground"
    android:gravity="fill" android:filter="true" />''')

for density, launcher_px, foreground_px in (
    ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
):
    folder = RES / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    legacy(launcher_px).save(folder / "ic_launcher.png")
    legacy(launcher_px, rounded=True).save(folder / "ic_launcher_round.png")
    foreground(foreground_px).save(folder / "ic_launcher_foreground.png")
legacy(512).save(ROOT / "app/src/main/ic_launcher-web.png")
print("Generated cat launcher, round, adaptive and themed icons; notification and QS untouched.")
