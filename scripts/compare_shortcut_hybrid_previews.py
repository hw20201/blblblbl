#!/usr/bin/env python3
"""Compare native Compose captures without redrawing or retouching their pixels.

Run after ShortcutHybridRenderingTest. Requires Pillow and numpy.
Columns are baseline/hybrid; rows are 0s/24s. No text is drawn onto the captures.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/build/outputs/shortcut-hybrid'
OUTPUT = SOURCE / 'comparison'
OUTPUT.mkdir(exist_ok=True)
results = {}
for mode in ('light', 'dark'):
    full = Image.new('RGB', (800, 1600))
    cards = Image.new('RGB', (720, 496))
    for row, phase in enumerate(('t00', 't24')):
        frames = []
        for col, design in enumerate(('baseline', 'hybrid')):
            image = Image.open(SOURCE / f'api35-{mode}-{design}-{phase}.png').convert('RGB')
            assert image.size == (400, 800), image.size
            full.paste(image, (col * 400, row * 800))
            cards.paste(image.crop((20, 262, 380, 510)), (col * 360, row * 248))
            frames.append(np.asarray(image).astype(np.int16))
        delta = np.abs(frames[0] - frames[1])
        outside = np.ones((800, 400), dtype=bool)
        outside[262:510, 20:380] = False
        values = {
            'card_rgb_sum_mean': float(delta[262:510, 20:380].sum(axis=2).mean()),
            'outside_card_max_channel_delta': int(delta[outside].max()),
            'dock_max_channel_delta': int(delta[708:790].max()),
            'changed_pixels_in_card_grid': int((delta[262:510, 20:380].sum(axis=2) > 0).sum()),
        }
        assert values['outside_card_max_channel_delta'] == 0, values
        assert values['dock_max_channel_delta'] == 0, values
        assert values['changed_pixels_in_card_grid'] > 100, values
        results[f'{mode}-{phase}'] = values
    full.save(OUTPUT / f'{mode}-dashboard-AB-t00-t24.png')
    cards.save(OUTPUT / f'{mode}-cards-AB-t00-t24.png')
(OUTPUT / 'comparison-metrics.json').write_text(json.dumps(results, indent=2) + '\n')
print(json.dumps(results, indent=2))
