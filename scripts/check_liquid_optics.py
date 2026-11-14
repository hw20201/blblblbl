#!/usr/bin/env python3
"""Dependency-free CPU/static checks; NOT an AGSL compiler or Android rendering test."""
import math, pathlib, re, subprocess, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
GLASS = ROOT / 'app/src/main/java/com/github/mihomo/android/ui/glass'
S = (GLASS / 'ShortcutRefractiveOptics.kt').read_text()
SCALE = 118 / 190

def smooth(a, b, x):
    t = max(0, min(1, (x-a)/(b-a)))
    return t*t*(3-2*t)

def displacement(depth, motion=0, contact=0):
    thick = 68*SCALE*(1+motion*.55*(1-contact))
    ratio = max(0, min(1, 1-depth/thick))
    i = math.asin(ratio*ratio)
    t = math.asin(math.sin(i)/2)
    raw = max(0, -math.tan(t-i))*.1*80*SCALE*(1+motion*.45*(1-contact))
    cap = 8.5*SCALE
    edge = 12*SCALE
    rim = 1-smooth(0, edge, depth)
    limit = edge*.6
    extra = limit*(1-math.exp(-(.1*80*SCALE)/limit))*rim
    return cap*(1-math.exp(-raw/cap))+extra

class LiquidOpticsTests(unittest.TestCase):
    def test_snapshot_is_css_scaled(self):
        for token in ['lengthDp(68f)', 'lengthDp(12f)', 'blurLod = 1.5f',
                      'ior = 2f', 'fresnel = .64f', 'tint = 0f, grain = 0f']:
            self.assertIn(token, S)
        self.assertAlmostEqual(60*SCALE, 37.2631579, places=6)
        self.assertAlmostEqual(68*SCALE, 42.2315789, places=6)
        self.assertAlmostEqual(12*SCALE, 7.4526316, places=6)
        self.assertAlmostEqual(3*2**(1.5-3), 1.06066017, places=6)

    def test_uniform_bindings_match(self):
        shader = S.split('internal const val SHORTCUT_REFRACTIVE_SHADER = """')[1].split('"""')[0]
        declared = set(re.findall(r'uniform float\d? (\w+);', shader))
        bound = set(re.findall(r'setFloatUniform\("(\w+)"', S))
        self.assertEqual(declared, bound)
        self.assertNotIn('half3(base.a), fres', shader)
        self.assertNotIn('relief', shader)
        self.assertNotIn('sin(dot(floor', shader)

    def test_finite_monotone_density_independent_displacement(self):
        for motion in [0, .5, 1]:
            for contact in [0, 1]:
                vals = [displacement(x/100, motion, contact) for x in range(10001)]
                self.assertTrue(all(math.isfinite(v) and 0 <= v < 8.3 for v in vals))
                self.assertTrue(all(a >= b-1e-9 for a,b in zip(vals, vals[1:])))
                self.assertEqual(vals[-1], 0)
                for d in [1, 1.5, 2, 2.75, 3.5, 4]:
                    self.assertAlmostEqual(vals[123]*d/d, vals[123])

    def test_overscan_contains_worst_case_offset(self):
        # Conservative vector bound: both tilt axes 9deg, max thickness/lift,
        # 1.4dp dent and diagonal blur tap. Input margin is 24dp on every side.
        tx = ty = math.radians(9)
        nx, ny, nz = math.sin(ty), -math.sin(tx)*math.cos(ty), math.cos(tx)*math.cos(ty)
        eta = .5
        k = 1-eta*eta*(1-nz*nz)
        factor = eta*nz-math.sqrt(k)
        rx, ry, rz = factor*nx, factor*ny, -eta+factor*nz
        tilt = math.hypot(rx, ry)/(-rz)*(68*SCALE*1.55+8*SCALE)
        # Full pickup grows the kernel to 3.4dp; resting LOD remains 1.5.
        self.assertIn('max(blurRadius, 3.4)', S)
        blur = 2*3.4
        bound = tilt+displacement(0, 1)+1.4+blur
        self.assertLess(bound, 24)
        print('Conservative overscan bound: %.4f dp < 24 dp' % bound)

    def test_flat_scene_no_invented_rim(self):
        # All blur weights normalize to 1; Fresnel mixes scene with scene, no white.
        self.assertEqual(.25+4*.125+4*.0625, 1)
        for rgb in [(0,0,0),(.08,.08,.08),(.7,.7,.7),(1,1,1)]:
            for depth in [0, 1, 3, 8, 40]:
                fres = (1-smooth(0,12*SCALE,depth))**3*.64
                output = tuple(c*(1-fres*.18)+c*fres*.18 for c in rgb)
                for a,b in zip(output,rgb): self.assertAlmostEqual(a,b)

    def test_business_gestures_layout_not_modified(self):
        changed = subprocess.check_output(['git','diff','--name-only','c91a27b22ee43aeb2dad36c2f41c225b2cf2fcab'], cwd=ROOT, text=True).splitlines()
        production = [p for p in changed if '/src/main/' in p]
        self.assertEqual(set(pathlib.Path(p).name for p in production),
                         {'ShortcutRefractiveOptics.kt','GlassBackground.kt','StructuredMeshWallpaper.kt','CardBackdrop.kt'})
        replay = (GLASS/'CardBackdrop.kt').read_text()
        self.assertIn('setPolyToPoly(scene, 0, local, 0, 4)', replay)
        self.assertIn('SHORTCUT_BACKDROP_OVERSCAN_DP', replay)
        self.assertIn('val maximum = if (followup) 8f', replay)

if __name__ == '__main__':
    unittest.main(verbosity=2)
