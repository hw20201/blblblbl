#!/usr/bin/env python3
"""Assemble real Compose captures. No generated artwork, pixel effects or added labels.

Dashboard sheet order: idle, contact, lift, release (left-to-right, top-to-bottom).
Material proof order: contact | lifted; crop retains both touched and untouched cards.
A/B/C sheet: each row one motion choice; columns old/new light, old/new dark.
"""
from argparse import ArgumentParser
from pathlib import Path
from PIL import Image
import shutil


def grid(paths, columns, output, crop=None):
    images = [Image.open(path).convert("RGB") for path in paths]
    if crop:
        images = [image.crop(crop) for image in images]
    width, height = images[0].size
    assert all(image.size == (width, height) for image in images)
    sheet = Image.new("RGB", (width * columns, height * ((len(images) + columns - 1) // columns)))
    for index, image in enumerate(images):
        sheet.paste(image, ((index % columns) * width, (index // columns) * height))
    sheet.save(output)


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    captures = root / "app/build/outputs"
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    dashboard = captures / "contact-frost-dashboard"
    material = captures / "contact-frost"
    for mode in ("light", "dark"):
        grid([dashboard / f"GentleFloat-{mode}-{state}.png" for state in
              ("0-idle", "1-contact", "3-lift", "4-release")], 2,
             out / f"dashboard-{mode}-states.png")
        grid([material / f"api35-{mode}-{state}.png" for state in ("1-contact", "3-lift")], 2,
             out / f"material-proof-{mode}.png", (0, 88, 400, 525))
    paths = []
    for variant in ("GentleFloat", "FingerTilt", "BuoyantSpring"):
        for mode in ("light", "dark"):
            for state in ("5-old-material-lift", "6-new-material-lift"):
                paths.append(dashboard / f"{variant}-{mode}-{state}.png")
    grid(paths, 4, out / "abc-old-new-comparison.png", (0, 240, 400, 515))
    shutil.copytree(dashboard, out / "raw-dashboard", dirs_exist_ok=True)
    shutil.copytree(material, out / "raw-material", dirs_exist_ok=True)
    print(out)


if __name__ == "__main__":
    main()
