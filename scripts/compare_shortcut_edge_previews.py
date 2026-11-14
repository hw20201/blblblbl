#!/usr/bin/env python3
"""Compare two native Compose runs, preserving exact pixels and animation phases.

Requires Pillow and numpy. Columns: before/after. Rows: 0s/24s.
Checks known-layout interiors containing the title and icons; does not use OCR.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


DEFAULT_REGIONS = {
    'Nodes': [20, 262, 194, 380], 'Profiles': [206, 262, 380, 380],
    'Rules': [20, 392, 194, 510], 'Requests': [206, 392, 380, 510],
}


def load(directory, mode, phase):
    with Image.open(directory / f'api35-{mode}-hybrid-{phase}.png') as image:
        assert image.size == (400, 800), image.size
        return np.array(image.convert('RGB'), dtype=np.int16)


def maximum(delta):
    return int(delta.max(initial=0))


def card_metrics(delta, bounds):
    left, top, right, bottom = bounds
    local = delta[top:bottom, left:right]
    changed = np.any(local != 0, axis=2)
    height, width = changed.shape
    yy, xx = np.mgrid[0:height, 0:width]
    qx = xx + .5 - width / 2
    qy = yy + .5 - height / 2
    # Match the production shader's rounded-rectangle distance field (18dp corner).
    nx = qx - np.clip(qx, -width / 2 + 18, width / 2 - 18)
    ny = qy - np.clip(qy, -height / 2 + 18, height / 2 - 18)
    depth = 18 - np.hypot(nx, ny)
    extended_band = (depth > 4.6) & (depth <= 7.2)
    interior = local[12:-12, 12:-12]
    values = {
        'bounds': bounds,
        'changed_pixels': int(changed.sum()),
        'max_channel_delta': maximum(local),
        'changed_pixels_depth_4_6_to_7_2dp': int((changed & extended_band).sum()),
        'inset_12px_title_icon_and_center_max_delta': maximum(interior),
    }
    assert values['changed_pixels'] > 100, values
    assert values['inset_12px_title_icon_and_center_max_delta'] == 0, values
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', required=True, type=Path)
    parser.add_argument('--after', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--regions', type=Path, help='JSON file exported by Compose semantics')
    args = parser.parse_args()
    regions = json.loads(args.regions.read_text()) if args.regions else DEFAULT_REGIONS
    assert regions and all(len(b) == 4 and 0 <= b[0] < b[2] <= 400 and
                           0 <= b[1] < b[3] <= 800 for b in regions.values())
    args.output.mkdir(parents=True, exist_ok=True)
    checks = {}
    for mode in ('light', 'dark'):
        full = Image.new('RGB', (800, 1600))
        cards = Image.new('RGB', (720, 496))
        for row, phase in enumerate(('t00', 't24')):
            before, after = load(args.before, mode, phase), load(args.after, mode, phase)
            for col, pixels in enumerate((before, after)):
                image = Image.fromarray(pixels.astype(np.uint8))
                full.paste(image, (col * 400, row * 800))
                cards.paste(image.crop((20, 262, 380, 510)), (col * 360, row * 248))
            delta = np.abs(before - after)
            outside = np.ones((800, 400), dtype=bool)
            for left, top, right, bottom in regions.values():
                outside[top:bottom, left:right] = False
            values = {
                'outside_all_cards_max_delta': maximum(delta[outside]),
                'dock_max_delta': maximum(delta[708:790]),
                'cards': {name: card_metrics(delta, bounds) for name, bounds in regions.items()},
            }
            assert values['outside_all_cards_max_delta'] == 0, values
            assert values['dock_max_delta'] == 0, values
            checks[f'{mode}-{phase}'] = values
        full.save(args.output / f'{mode}-dashboard-before-after-t00-t24.png')
        cards.save(args.output / f'{mode}-cards-before-after-t00-t24.png')
    report = {
        'layout': 'columns=before/after; rows=t00/t24; separate light/dark files',
        'before': str(args.before), 'after': str(args.after), 'regions': regions,
        'note': 'Unmodified native screenshot pixels. Interior checks cover the known title/icon layout, not OCR.',
        'checks': checks,
    }
    (args.output / 'comparison-metrics.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
