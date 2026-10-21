#!/usr/bin/env python3
"""CPU/source regression checks only; not Android compilation or GPU acceptance."""
import math
import pathlib
import re
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = 'c4451affba509e18289c7ddcf11095f4a201d03b'
P = ROOT / 'app/src/main/java/com/github/mihomo/android'

def read(relative):
    return (P / relative).read_text()

S = read('ui/components/ShortcutSuspension.kt')
M = read('ui/components/ShortcutTouchMotion.kt')
C = read('ui/components/LoonComponents.kt')
D = read('ui/screens/DashboardScreen.kt')
G = read('ui/glass/ShortcutDockGlass.kt')
L = read('ui/glass/ShortcutSurfaceLighting.kt')

def constant(name):
    return float(re.search(r'const val ' + name + r' = ([\d.]+)f', S)[1])

MAX_TILT = constant('SHORTCUT_SUSPENDED_MAX_TILT')

def tilt(x, y):
    def weight(v):
        n = max(-1, min(1, v)) if math.isfinite(v) else 0
        return n * (1.8 - .8 * abs(n))
    wx, wy = weight(x), weight(y)
    divisor = max(1, math.hypot(wx, wy))
    return -wy / divisor * MAX_TILT, wx / divisor * MAX_TILT

def light(pitch, roll):
    def angle(v):
        return max(-6, min(6, v)) / 6 if math.isfinite(v) else 0
    x, y = -.35 + angle(roll) * .95, -.85 - angle(pitch) * .60
    length = math.hypot(x, y)
    return x / length, y / length

class FloatingFixChecks(unittest.TestCase):
    def test_models_are_tied_to_actual_source(self):
        for expression in ['n * (1.8f - .8f * kotlin.math.abs(n))',
                           'maxOf(1f, length)', 'rotationX = -wy / divisor',
                           'rotationY = wx / divisor', 'maxOf(160f, widthDp * .84f)']:
            self.assertIn(expression, S)
        for expression in ['-.35f + angle(rollDegrees) * .95f',
                           '-.85f - angle(pitchDegrees) * .60f',
                           'value.coerceIn(-6f, 6f) / 6f']:
            self.assertIn(expression, L)

    def test_center_edges_and_diagonal_limits(self):
        self.assertEqual(tilt(0, 0), (0, 0))
        self.assertGreater(tilt(0, -1)[0], 0)
        self.assertLess(tilt(0, 1)[0], 0)
        self.assertLess(tilt(-1, 0)[1], 0)
        self.assertGreater(tilt(1, 0)[1], 0)
        self.assertGreater(abs(tilt(.5, -.5)[0]), 3)
        for ix in range(-100, 101):
            for iy in range(-100, 101):
                pitch, roll = tilt(ix / 100, iy / 100)
                self.assertLessEqual(math.hypot(pitch, roll), MAX_TILT + 1e-6)
        self.assertEqual(tilt(float('nan'), float('inf')), (0, 0))
        self.assertEqual(tilt(100, -100), tilt(1, -1))

    def test_continuous_tilt_no_dead_zone(self):
        previous = 0
        for i in range(1, 1001):
            roll = tilt(i / 1000, 0)[1]
            self.assertGreater(roll, previous)
            self.assertLess(roll - previous, .011)
            previous = roll
        self.assertLess(abs(tilt(.0001, 0)[1]), .002)

    def test_camera_is_safe_under_all_corner_poses(self):
        # Pinhole bound: conservative |z| <= half-diagonal * sin(total angle).
        # Density cancels: perspective distances and panel coordinates scale together.
        for w in [120, 174, 190, 240, 320, 600]:
            camera = max(160, w * .84)
            z_bound = math.hypot(w, 118) * .5 * math.sin(math.radians(MAX_TILT))
            self.assertLess(z_bound / camera, .09)
            self.assertLess(camera / (camera - z_bound), 1.10)
        self.assertIn('shortcutSuspendedCameraDp(size.width / density) * density', M)

    def test_one_real_outline_not_rectangle_or_painted_duplicate(self):
        self.assertIn('shadowElevation = 0f', D)
        self.assertNotIn('shadowElevation = if (isBeingDragged)', D)
        self.assertIn('isReordering = isBeingDragged', D)
        self.assertIn('this.shape = shape', S)
        self.assertIn('clip = false', S)
        self.assertIn('shape = cardShape', C)
        self.assertIn('resolveShortcutCardShape', read('ui/glass/GlassSurface.kt'))
        self.assertIn('useGlassSurface && (LocalShortcutDockLensEnabled.current || !experimental)', C)
        self.assertNotIn('shortcutSuspensionShadow', G)
        for forbidden in ['drawRect', 'Bitmap', 'createBlurEffect', 'drawOutline', 'withFrameNanos']:
            self.assertNotIn(forbidden, S)
        self.assertEqual([constant('SHORTCUT_' + n + '_ELEVATION_DP') for n in ['REST','PRESS','REORDER']], [3,5,8])

    def test_actual_angles_drive_edge_and_recover_to_rest(self):
        self.assertIn('pitchDegrees = visiblePitch(), rollDegrees = visibleRoll()', M)
        optics = read('ui/glass/ShortcutMeshOptics.kt')
        self.assertIn('pitchDegrees: Float = 0f, val rollDegrees: Float = 0f', optics)
        self.assertIn('shortcutSurfaceLightDirection(pose.pitchDegrees, pose.rollDegrees)', G)
        self.assertIn('drawOutline(outline, lighting, style = edgeStroke)', G)
        self.assertLess(G.index('drawOutline(outline, lighting'), G.index('Box(Modifier.matchParentSize(), content = content)'))
        self.assertNotIn('if (kotlin.math.abs(pose.pitchDegrees)', G)
        for p in range(-60, 61):
            for r in range(-60, 61):
                x, y = light(p / 10, r / 10)
                self.assertAlmostEqual(math.hypot(x, y), 1)
                self.assertLess(y, 0)  # No normalization zero / 180-degree flip seam.
        self.assertLess(light(0,-6)[0], 0)
        self.assertGreater(light(0,6)[0], 0)
        self.assertGreater(light(-6,0)[1], light(6,0)[1] + .15)
        self.assertEqual(light(float('nan'), float('inf')), light(0,0))
        for t in [i / 1000 for i in range(1001)]:
            a, b = light(6*t, -6*t), light(6*(t+.001), -6*(t+.001))
            self.assertLess(math.dist(a,b), .02)

    def test_shader_cost_and_glass_levels_preserved(self):
        baseline = subprocess.check_output(['git','show', BASE+':app/src/main/java/com/github/mihomo/android/ui/glass/ShortcutDockGlass.kt'], cwd=ROOT, text=True)
        def shader_part(text):
            return text.split('internal val SHORTCUT_DOCK_LENS_SHADER =')[1].split('private class ShortcutDockEffectCache')[0]
        self.assertEqual(shader_part(baseline), shader_part(G))
        self.assertIn('effectCache.key != key', G)
        for forbidden in ['rememberInfinite', 'withFrameNanos', 'createBlurEffect', 'Bitmap']:
            self.assertNotIn(forbidden, G + L)
        for relative in ['ui/glass/ShortcutGlassLevel.kt', 'ui/glass/NavigationLens.kt',
                         'ui/glass/NavigationLensBar.kt', 'ui/glass/CardBackdrop.kt']:
            before = subprocess.check_output(['git','show',BASE+':app/src/main/java/com/github/mihomo/android/'+relative], cwd=ROOT)
            self.assertEqual(before, (P/relative).read_bytes(), relative)

    def test_normal_gesture_arbitration_not_rewritten(self):
        path = 'app/src/main/java/com/github/mihomo/android/ui/components/ShortcutTouchMotion.kt'
        baseline = subprocess.check_output(['git','show',BASE+':'+path], cwd=ROOT, text=True)
        def gesture(s):
            return s.split('val gestureModifier =')[1].split('fun visiblePitch()')[0]
        self.assertEqual(gesture(baseline), gesture(M))
        self.assertIn('Box(slotModifier)', C)
        self.assertIn('planeSize.then(touchMotion.visualModifier)', C)
        self.assertIn('if (jelly) shortcutSuspendedTilt(x, y)', M)

    def test_manifest_permissions_build_and_native_untouched(self):
        paths = ['app/src/main/AndroidManifest.xml', 'app/build.gradle.kts', 'build.gradle.kts',
                 'native/SHA256SUMS', 'app/src/main/java/com/github/mihomo/android/service/MihomoVpnService.kt']
        for path in paths:
            before = subprocess.check_output(['git','show',BASE+':'+path], cwd=ROOT)
            self.assertEqual(before, (ROOT/path).read_bytes(), path)

if __name__ == '__main__':
    unittest.main(verbosity=2)
