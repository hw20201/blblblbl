#!/usr/bin/env python3
"""Compare native Compose screenshots; never redraw, enhance or simulate the UI."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results = {}
    ordinary = {'Tokyo', 'Osaka'}
    for mode in ('light', 'dark'):
        page_pairs = []
        for position in ('top', 'scroll'):
            key = f'{mode}-{position}'
            before = Image.open(args.input / f'before-{key}.png').convert('RGB')
            after = Image.open(args.input / f'after-{key}.png').convert('RGB')
            assert before.size == after.size
            a, b = np.asarray(before), np.asarray(after)
            delta = np.abs(a.astype(np.int16) - b.astype(np.int16))
            bounds = json.loads((args.input / f'after-{key}-bounds.json').read_text())
            mask = np.zeros(a.shape[:2], dtype=bool)
            ordinary_max = {}
            title_checks = {}
            for name, rect in bounds.items():
                x0, y0, x1, y1 = (round(rect[k]) for k in ('left', 'top', 'right', 'bottom'))
                x0, y0 = max(x0, 0), max(y0, 0)
                x1, y1 = min(x1, a.shape[1]), min(y1, a.shape[0] - 110)
                if x1 <= x0 or y1 <= y0:
                    continue
                if name in ordinary:
                    ordinary_max[name] = int(delta[y0:y1, x0:x1].max())
                else:
                    mask[y0:y1, x0:x1] = True
                    if name != 'YouTube' and y1 - y0 >= 40:
                        title_a = a[y0 + 8:y0 + 26, x0 + 10:x1 - 10]
                        title_b = b[y0 + 8:y0 + 26, x0 + 10:x1 - 10]
                        ink = np.array([16, 24, 40] if mode == 'light' else [245, 246, 248])
                        cores = np.max(np.abs(title_a.astype(np.int16) - ink), axis=2) <= 3
                        count = int(cores.sum())
                        stable = int(np.count_nonzero(cores & (np.max(np.abs(title_b.astype(np.int16) - ink), axis=2) <= 3)))
                        if count:
                            assert stable >= count * .98, (key, name, stable, count)
                            title_checks[name] = {'stable': stable, 'cores': count}
            # Dock optics are unchanged but naturally sample the newly changed tiles behind them.
            # Exclude that existing scene-dependent bottom region, not the header or selectors.
            mask[-110:] = True
            outside_max = int(delta[~mask].max())
            assert outside_max == 0, (key, 'pixels changed outside group cards', outside_max)
            assert all(value == 0 for value in ordinary_max.values()), (key, ordinary_max)
            changed = int(np.count_nonzero(np.any(delta > 0, axis=2)))
            assert changed > 0, f'{key}: experiment must visibly change the real rendered tiles'
            results[key] = {'outside_cards_above_dock_max_channel_delta': outside_max,
                            'ordinary_node_max_channel_delta': ordinary_max,
                            'opaque_title_core_checks': title_checks,
                            'changed_page_pixels': changed,
                            'dock_note': 'unchanged code; samples changed scene, excluded from isolation mask'}
            pair = Image.new('RGB', (before.width * 2, before.height))
            pair.paste(before, (0, 0))
            pair.paste(after, (before.width, 0))
            pair.save(args.output / f'{key}-before-after.png')
            page_pairs.append(pair)
        overview = Image.new('RGB', (page_pairs[0].width, sum(p.height for p in page_pairs)))
        y = 0
        for pair in page_pairs:
            overview.paste(pair, (0, y))
            y += pair.height
        overview.save(args.output / f'{mode}-top-scroll-before-after.png')
    (args.output / 'pixel-checks.json').write_text(json.dumps(results, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps(results, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
