#!/usr/bin/env python3
"""CPU/source checks, NOT Android shader compilation or visual/power acceptance."""
import math,pathlib,re,subprocess,unittest
import xml.etree.ElementTree as ET
R=pathlib.Path(__file__).resolve().parents[1]
B=R/'app/src/main/java/com/github/mihomo/android'
def text(p): return (B/p).read_text()
M=text('ui/components/ShortcutTouchMotion.kt')
D=text('ui/glass/ShortcutDockGlass.kt')
N=text('ui/glass/NavigationLens.kt')
G=text('ui/glass/LiquidGlassSurface.kt')
L=text('ui/glass/ShortcutGlassLevel.kt')
C=text('ui/components/LoonComponents.kt')

def generated_shader():
    shader=N.split('internal const val NAVIGATION_LENS_SHADER = """')[1].split('"""')[0]
    for a,b in [
        ('uniform float2 viewport;','uniform float2 viewport;\nuniform float2 inputOrigin;\nuniform float2 inputViewport;'),
        ('uniform float aberration;','uniform float aberration;\nuniform float chromaticSamples;'),
        ('float2 q = p - viewport * 0.5;','float2 q = (p - inputOrigin) - viewport * 0.5;'),
        ('float2 hi = viewport - float2(0.5);','float2 hi = inputViewport - float2(0.5);')]:
        assert shader.count(a)==1
        shader=shader.replace(a,b)
    before='''        half4 cr = scene.eval(clamp(base + normal * ca, lo, hi));
        half4 cb = scene.eval(clamp(base - normal * ca, lo, hi));
        half4 c = half4(cr.r, cg.g, cb.b, cg.a);'''
    after='''        half4 c = cg;
        // Keep the flat centre cheap; colour separation is only paid for on the bevel.
        if (edge > 0.001 && aberration > 0.001) {
            c.r = scene.eval(clamp(base + normal * ca, lo, hi)).r;
            if (chromaticSamples > 1.5) {
                c.b = scene.eval(clamp(base - normal * ca, lo, hi)).b;
            }
        }'''
    assert shader.count(before)==1
    assert before in N and after in G
    return shader.replace(before,after)

class SuspendedChecks(unittest.TestCase):
    def test_b_has_no_pickup_or_resize(self):
        b=M.split('ShortcutTouchVariant.FingerTilt -> if (tuned) ShortcutTouchPose(')[1].split(') else ShortcutTouchPose(')[0]
        self.assertIn('scale = 1f',b)
        self.assertNotIn('offsetYDp',b); self.assertNotIn('offsetXDp',b)
        self.assertIn('rotationX = -ny * 6f',b); self.assertIn('rotationY = nx * 6f',b)
        self.assertIn('ShortcutTouchVariant.FingerTilt -> 1f',M)
        self.assertIn('snap<Float>() else spec',M)

    def test_fixed_slot_separate_from_plane_and_shadow(self):
        self.assertIn('Box(slotModifier)',C)
        self.assertIn('planeSize.then(touchMotion.visualModifier)',C)
        self.assertNotIn('shortcutSuspensionShadow',C)
        self.assertIn('shortcutJellyTravel',M)

    def test_dock_is_adapter_for_liquid_glass(self):
        self.assertIn('internal val SHORTCUT_DOCK_LENS_SHADER = LIQUID_GLASS_LENS_SHADER',D)
        self.assertIn('LIQUID_GLASS_LENS_SHADER = NAVIGATION_LENS_SHADER',G)
        self.assertIn('fun ShortcutDockGlassSurface(',D)
        self.assertIn('LiquidGlassSurface(',D)
        self.assertIn('style = liquidGlassStyle(level)',D)
        self.assertNotIn('RuntimeShader(',D)
        self.assertNotIn('createRuntimeShaderEffect',D)

    def test_kernel_and_effect_live_in_liquid_glass(self):
        s=generated_shader()
        self.assertIn('float2 q = (p - inputOrigin)',s)
        self.assertIn('edge * edge * displacement',s)
        self.assertIn('anchored = true',G)
        self.assertNotIn('createBlurEffect',G)
        self.assertNotIn('sampleBlur',s)
        self.assertNotIn('projected - c',s)

    def test_liquid_glass_has_no_manual_red_dye(self):
        for forbidden in ['Color.Red', 'Color(0xFFFF0000)', '0xFFFF0000', 'half3(1.0, 0.0, 0.0)']:
            self.assertNotIn(forbidden, G)

    def test_overscan_coordinates_are_not_double_shifted(self):
        # RenderEffect p is padded-layer space; only SDF geometry uses card space.
        # Source stays padded: at bend=0, eval(p) must reproduce the input at p.
        shader=generated_shader()
        self.assertIn('float2 base = clamp(p - normal * bend, lo, hi);',shader)
        for w,h in [(190,118),(240,120)]:
            # New reusable surface matches the bottom-bar STRONG bend and keeps a 10dp overscan.
            margin_dp=10.0
            for density in [1,2,3.5]:
                margin=round(margin_dp*density)
                viewport=(w*density,h*density)
                for x,y in [(0,h/2),(w/2,h/2),(w,h/2),(w/2,0),(w/2,h)]:
                    p=(x*density+margin,y*density+margin)
                    q=(p[0]-margin-viewport[0]/2,p[1]-margin-viewport[1]/2)
                    self.assertEqual(q,((x-w/2)*density,(y-h/2)*density))
                    self.assertEqual(p,(x*density+margin,y*density+margin))
                self.assertIn('Box(modifier.clip(shape))',G)
                self.assertIn('child.place(-margin, -margin)',G)

    def test_shader_uniforms_are_all_bound(self):
        shader=generated_shader()
        declared=set(re.findall(r'uniform float\d? (\w+);',shader))
        bound=set(re.findall(r'setFloatUniform\("(\w+)"',G))
        self.assertEqual(declared,bound)

    def test_level_parameters_separated_and_normal_is_lighter(self):
        p=[(1.2,7,.24,.030),(2.2,9,.682,.045),(4,12,1.6,.060)]
        for a,b in zip(p,p[1:]):
            self.assertGreater(b[0]/a[0],1.7)
            self.assertGreater(b[1],a[1]); self.assertGreater(b[3],a[3])
        self.assertIn('Normal(LensStrength.SUBTLE, 10f, 1',L)
        self.assertIn('Obvious(LensStrength.STANDARD, 15.5f, 2',L)
        self.assertIn('Extreme(LensStrength.STRONG, 20f, 2',L)
        self.assertIn('dispersion * .02f',L)
        self.assertIn('if (chromaticSamples > 1.5)',G)
        self.assertIn('if (edge > 0.001 && aberration > 0.001)',generated_shader())
        self.assertIn('staticCompositionLocalOf { ShortcutGlassLevel.Normal }',L)

    def test_background_sample_bounds_and_zero_centre_bend(self):
        for disp,bevel,ca in [(4,12,.24),(5,14,.682),(6,16,1.6)]:
            for depth in [0,.2,1,4,7,9,12,25,60]:
                t=min(1,max(0,depth/bevel)); edge=1-t*t*(3-2*t)
                footprint=edge*edge*disp+edge*ca+2
                self.assertLessEqual(footprint,10)
                if depth>=bevel: self.assertEqual(edge,0)
        # At 190x118dp the explicit input is 210x138, still smaller than the old 24dp overscan.
        self.assertLess((190+20)*(118+20),(190+48)*(118+48))

    def test_touch_arbitration_preserves_scroll_and_single_pickup(self):
        self.assertIn('if (!horizontallyLocked && jellyIntent)',M)
        self.assertIn('heldForDrag && delta.getDistance()',M)
        self.assertIn('viewConfiguration.longPressTimeoutMillis',M)
        self.assertIn('else if (!horizontallyLocked && abs(delta.y)',M)
        self.assertIn('if (horizontallyLocked) pickupCallback(false)',M)
        self.assertIn('if (event.changes.count { it.pressed } > 1)',M)
        self.assertIn('event.changes.forEach { it.consume() }',M)
        self.assertIn('finally { input.reset() }',M)
        self.assertIn('plane.localPositionOf(slot, position)',M)
        self.assertLess(M.index('fun visiblePitch()'),M.index('val visualModifier ='))
        self.assertLess(M.index('fun visibleRoll()'),M.index('val visualModifier ='))
        self.assertEqual(M.count('fun visiblePitch()'),1)
        self.assertEqual(M.count('fun visibleRoll()'),1)
        self.assertIn('if (jelly && frozenPose == null) directJellyLayer else visualModifier',M)

    def test_radial_travel_and_small_rebound_are_bounded(self):
        self.assertIn('raw = delta * .09f',M)
        self.assertIn('raw * (limit / length)',M)
        self.assertIn('if (tuned) 0.72f',M)
        for x in range(-160,161,8):
            for y in range(-160,161,8):
                dx,dy=x*.09,y*.09
                n=math.hypot(dx,dy)
                if n>4: dx,dy=dx*4/n,dy*4/n
                self.assertLessEqual(math.hypot(dx,dy),4.000001)
                if x==0:self.assertEqual(dx,0)
                if y==0:self.assertEqual(dy,0)
        z=.72
        overshoot=math.exp(-math.pi*z/math.sqrt(1-z*z))
        self.assertLess(4*overshoot,.16)

    def test_no_idle_clock_or_filter_recreation_on_touch(self):
        self.assertIn('if (effectCache.key != key)',G)
        self.assertIn('size.height, density, style.displacementDp',G)
        for forbidden in ['withFrameNanos','infiniteTransition','Handler(', 'Bitmap','Thread.sleep','rememberInfinite']:
            self.assertNotIn(forbidden,G)

    def test_settings_reach_renderer(self):
        main=text('ui/MainActivity.kt'); settings=text('data/SettingsManager.kt'); screen=text('ui/screens/SettingsScreen.kt')
        for s in ['settingsManager.shortcutGlassLevel','LocalShortcutGlassLevel provides shortcutGlassLevel','settingsManager.shortcutGlassLevel = level']:
            self.assertIn(s,main)
        self.assertIn('shortcut_glass_level',settings)
        self.assertIn('ShortcutGlassLevel.fromPreference',settings)
        for label in ['普通','明显','极致']: self.assertIn(label,screen)
        self.assertIn('val level = LocalShortcutGlassLevel.current',D)

    def test_no_navigation_vpn_or_background_modification(self):
        inside=subprocess.run(['git','rev-parse','--is-inside-work-tree'],cwd=R,text=True,capture_output=True)
        self.assertEqual(inside.returncode,0,'Scope validation requires the experiment Git repository')
        baseline='c4451affba509e18289c7ddcf11095f4a201d03b'
        base=subprocess.run(['git','rev-parse','--verify',baseline+'^{commit}'],cwd=R,text=True,capture_output=True)
        self.assertEqual(base.returncode,0,'Required main baseline is missing; fetch its history before validation')
        paths=set(subprocess.check_output(['git','diff','--name-only',baseline],cwd=R,text=True).splitlines())
        paths.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=R,text=True).splitlines())
        # Everything outside this explicit experiment scope, including ALL native/core, VPN,
        # accessibility, config-centre, navigation and background, must stay unchanged.
        # The separately approved test package may change ONLY these two exact version fields.
        allowed={
            '.github/workflows/shortcut-glass-debug.yml',
            'SHORTCUT_GLASS_ACTIONS_README.md',
            'app/build.gradle.kts',
            'app/src/main/java/com/github/mihomo/android/ui/glass/LiquidGlassSurface.kt',
            'app/src/main/java/com/github/mihomo/android/ui/glass/ShortcutDockGlass.kt',
            'app/src/test/java/com/github/mihomo/android/ui/glass/ShortcutGlassLevelPolicyTest.kt',
            'scripts/check_shortcut_suspended.py',
        }
        self.assertFalse(paths-allowed,'Out-of-scope modifications: '+', '.join(sorted(paths-allowed)))
        original=subprocess.check_output(['git','show',baseline+':app/build.gradle.kts'],cwd=R,text=True)
        expected=original.replace('versionCode = 37','versionCode = 39').replace(
            'versionName = "1.0.32-with-suspended"','versionName = "1.0.34"')
        self.assertEqual((R/'app/build.gradle.kts').read_text(),expected,'Only approved 1.0.34/code39 version bump is allowed')

    def test_lint_baseline_remains_single_authorized_issue(self):
        baseline=ET.parse(R/'app/lint-baseline.xml').getroot().findall('issue')
        self.assertEqual(len(baseline),1)
        self.assertEqual(baseline[0].get('id'),'StartActivityAndCollapseDeprecated')
        location=baseline[0].find('location')
        self.assertEqual(location.get('file'),'src/main/java/com/github/mihomo/android/service/MihomoTileService.kt')
        self.assertEqual(location.get('line'),'63')

if __name__=='__main__': unittest.main(verbosity=2)
