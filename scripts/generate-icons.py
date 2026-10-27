#!/usr/bin/env python3
"""Build antialiased Android launcher and splash assets from the supplied Arca artwork."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "assets/arca-wave-source.jpg"
ANDROID_NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
NAVY = (7, 22, 41)
CREAM = (255, 249, 233)

source = Image.open(SOURCE).convert("RGB")
box = (60, 60, source.width - 60, source.height - 60)
tile = source.crop(box)

# Recover fractional edge coverage from the two observed ink colors. Unlike the
# previous binary brightness threshold, projection onto the navy-to-cream color
# line preserves antialias coverage. Reject pixels far from that line (the JPEG's
# near-white paper margin) to avoid turning the page into opaque foreground.
background = (7.0, 22.0, 41.0)
foreground = (254.0, 248.0, 232.0)
delta = tuple(foreground[i] - background[i] for i in range(3))
norm2 = sum(v * v for v in delta)
mark_alpha = Image.new("L", source.size, 0)
src_px = source.load()
alpha_px = mark_alpha.load()
# The supplied JPEG has a white page outside its navy rounded tile. Restrict
# color extraction to the known central artwork, safely inside the tile, so
# anti-aliased tile corners can never leak into the wave's transparent layer.
artwork_roi = (200, 200, 1300, 1300)
for y in range(artwork_roi[1], artwork_roi[3]):
    for x in range(artwork_roi[0], artwork_roi[2]):
        p = src_px[x, y]
        projection = sum((p[i] - background[i]) * delta[i] for i in range(3)) / norm2
        residual = sum(((p[i] - background[i]) - projection * delta[i]) ** 2 for i in range(3)) ** 0.5
        # The ROI is entirely inside the navy tile, so the paper margin is excluded
        # geometrically. Keep slightly off-line JPEG edge pixels: rejecting them with a
        # hard residual cutoff creates tiny transparent gaps along the high-contrast curve.
        if projection < 0.025 or projection > 1.08 or residual > 32:
            continue
        coverage = max(0.0, min(1.0, (projection - 0.025) / 0.95))
        if coverage > 0:
            alpha_px[x, y] = round(coverage * 255)

# The source is a 1500px JPEG, not vector artwork. Remove subpixel compression
# speckle before density resampling; this sub-source-pixel softening preserves the
# silhouette while preventing JPEG block noise from becoming a visibly rough alpha edge.
mark_alpha = mark_alpha.filter(ImageFilter.GaussianBlur(radius=0.65))
mark_bbox = mark_alpha.getbbox()
if mark_bbox is None:
    raise SystemExit("No cream mark found in supplied launcher source")
mark_alpha = mark_alpha.crop(mark_bbox)
cream_mark = Image.new("RGBA", mark_alpha.size, (*CREAM, 0))
cream_mark.putalpha(mark_alpha)


def centered_mark(canvas_size, max_fraction):
    max_dim = round(canvas_size * max_fraction)
    factor = min(max_dim / cream_mark.width, max_dim / cream_mark.height)
    wh = (max(1, round(cream_mark.width * factor)), max(1, round(cream_mark.height * factor)))
    logo = cream_mark.resize(wh, Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    result.alpha_composite(logo, ((canvas_size - wh[0]) // 2, (canvas_size - wh[1]) // 2))
    return result


def rounded_tile(size, round_icon=False):
    hi = Image.new("L", (size * 4, size * 4), 0)
    draw = ImageDraw.Draw(hi)
    bounds = (0, 0, size * 4 - 1, size * 4 - 1)
    if round_icon:
        draw.ellipse(bounds, fill=255)
    else:
        draw.rounded_rectangle(bounds, radius=size * 0.22 * 4, fill=255)
    return hi.resize((size, size), Image.Resampling.LANCZOS)


def legacy_icon(size, round_icon=False):
    result = Image.new("RGBA", (size, size), (*NAVY, 0))
    result.putalpha(rounded_tile(size, round_icon))
    result.alpha_composite(centered_mark(size, 0.80))
    return result


def splash_icon(size):
    # Density-matched images prevent Android 12+ scaling a small mdpi bitmap
    # across a large high-density splash viewport. Keep the previous safe inset.
    result = Image.new("RGBA", (size, size), (*NAVY, 255))
    result.putalpha(rounded_tile(size))
    result.alpha_composite(centered_mark(size, 0.80 * 0.62))
    return result

(RES / "drawable").mkdir(parents=True, exist_ok=True)
(RES / "drawable/ic_launcher_background.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector {ANDROID_NS} android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#071629" android:pathData="M0,0h108v108h-108z" />
</vector>\n''', encoding="utf-8")
(RES / "drawable/ic_launcher_foreground.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<bitmap {ANDROID_NS} android:src="@mipmap/ic_launcher_foreground"
    android:gravity="fill" android:filter="true" />\n''', encoding="utf-8")
(RES / "drawable/ic_launcher_monochrome.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<bitmap {ANDROID_NS} android:src="@mipmap/ic_launcher_monochrome"
    android:gravity="fill" android:filter="true" />\n''', encoding="utf-8")

# Include mdpi fallback plus a matching splash PNG for each Android density.
for density, factor in (("mdpi", 1), ("hdpi", 3/2), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    folder = RES / f"drawable-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    splash_icon(round(288 * factor)).save(folder / "ic_splash_logo.png", optimize=True)
splash_icon(288).save(RES / "drawable/ic_splash_logo.png", optimize=True)

for density, launcher_px, adaptive_px in (
    ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
):
    folder = RES / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    legacy_icon(launcher_px).save(folder / "ic_launcher.png", optimize=True)
    legacy_icon(launcher_px, True).save(folder / "ic_launcher_round.png", optimize=True)
    centered_mark(adaptive_px, 62 / 108).save(folder / "ic_launcher_foreground.png", optimize=True)
    centered_mark(adaptive_px, 62 / 108).save(folder / "ic_launcher_monochrome.png", optimize=True)

legacy_icon(512).save(ROOT / "app/src/main/ic_launcher-web.png", optimize=True)
print(f"Generated antialiased assets from {SOURCE.name}; crop={box}; extracted mark={mark_bbox}.")
