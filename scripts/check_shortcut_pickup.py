#!/usr/bin/env python3
"""Static/numerical validation only: not an AGSL or Android runtime test."""
import json, math, pathlib, re, subprocess, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/com/github/mihomo/android'
MOTION = (BASE/'ui/components/ShortcutTouchMotion.kt').read_text()
OPTICS = (BASE/'ui/glass/ShortcutRefractiveOptics.kt').read_text()
CARD = (BASE/'ui/components/LoonComponents.kt').read_text()
SCALE = 118/190

def smooth(a,b,x):
    t = min(1,max(0,(x-a)/(b-a)))
    return t*t*(3-2*t)

def displacement(depth, lift=0, contact=0):
    thick = 68*SCALE*(1+lift*.55*(1-contact))
    ratio = min(1,max(0,1-depth/thick))
    angle = math.asin(ratio*ratio)
    refracted = math.asin(math.sin(angle)/2)
    raw = max(0,-math.tan(refracted-angle))*.1*80*SCALE*(1+lift*.45*(1-contact))
    cap = 8.5*SCALE
    edge = 12*SCALE
    rim = 1-smooth(0,edge,depth)
    t = min(1,max(0,1-depth/edge))
    curve = 1-math.sqrt(max(0,1-t*t))
    limit = edge*.6
    return cap*(1-math.exp(-raw/cap))*(.74+.26*curve)+limit*(1-math.exp(-.1*80*SCALE/limit))*rim

class PickupValidation(unittest.TestCase):
    def test_b_target_moves_in_both_themes(self):
        block=MOTION.split('ShortcutTouchVariant.FingerTilt -> if (tuned) ShortcutTouchPose(')[1].split(') else ShortcutTouchPose(')[0]
        self.assertIn('offsetYDp = -8f',block)
        self.assertIn('scale = 1.018f',block)
        self.assertIn('rotationX = -ny * 2.8f',block)
        self.assertIn('rotationY = nx * 3.2f',block)
        self.assertNotIn('dark',block.lower())
        self.assertIn('ShortcutTouchVariant.FingerTilt -> 1.015f', MOTION)

    def test_gesture_arbitration_preserved(self):
        previous = subprocess.check_output(['git','show','76b5907:app/src/main/java/com/github/mihomo/android/ui/components/ShortcutTouchMotion.kt'],cwd=ROOT,text=True)
        extract=lambda text: text.split('val gestureModifier = Modifier.pointerInput')[1].split('val visualModifier =')[0].split('// Draw separately')[0].strip()
        self.assertEqual(extract(previous),extract(MOTION))
        self.assertIn('input.dragY = 0f', MOTION)
        self.assertIn('val animate = (LocalShortcutMotionEnabled.current ?: true) && enabled',MOTION)

    def test_uniform_binding_complete(self):
        shader=OPTICS.split('internal const val SHORTCUT_REFRACTIVE_SHADER = """')[1].split('"""')[0]
        declared=set(re.findall(r'uniform float\d? (\w+);',shader))
        bound=set(re.findall(r'setFloatUniform\("(\w+)"',OPTICS))
        self.assertEqual(declared,bound)
        self.assertIn('pitch',declared); self.assertIn('roll',declared)
        self.assertNotIn('uv += (projected - c)',shader)

    def test_ground_is_independent_of_plane(self):
        self.assertIn('Box(modifier = slotModifier)',CARD)
        self.assertIn('planeSize.then(touchMotion.visualModifier)',CARD)
        self.assertIn('Box(Modifier.matchParentSize().then(touchMotion.groundShadowModifier))',CARD)
        self.assertIn('val visualModifier = Modifier.graphicsLayer',MOTION)
        self.assertNotIn('groundShadow.graphicsLayer',MOTION)
        self.assertIn('groundShadowModifier = groundShadow',MOTION)

    def test_lens_curve_finite_monotone_and_bounded(self):
        for lift in [0,.5,1]:
            for contact in [0,1]:
                values=[displacement(i/100,lift,contact) for i in range(10001)]
                self.assertTrue(all(math.isfinite(v) and 0<=v<8.3 for v in values))
                self.assertTrue(all(a>=b-1e-9 for a,b in zip(values,values[1:])))
                self.assertEqual(values[-1],0)

    def test_rim_preserves_centre(self):
        for dark in [False,True]:
            for lift in [0,.5,1]:
                for scene in [0,.08,.5,1]:
                    for depth in [0,1,4,8,30,60]:
                        rim=1-smooth(0,12*SCALE,depth)
                        glint=rim**6*(.065+lift*.17)
                        value=scene*(1-glint)+glint
                        if depth>=8: self.assertEqual(value,scene)
                        self.assertTrue(0<=value<=1)
                        if depth==0 and scene<1: self.assertGreater(value,scene)

    def test_overscan_still_contains_sampling_footprint(self):
        self.assertIn('-0.15707963, 0.15707963',OPTICS)
        tx=ty=math.radians(9)
        nx,ny,nz=math.sin(ty),-math.sin(tx)*math.cos(ty),math.cos(tx)*math.cos(ty)
        k=1-.25*(1-nz*nz)
        factor=.5*nz-math.sqrt(k)
        rx,ry,rz=factor*nx,factor*ny,-.5+factor*nz
        tilt=math.hypot(rx,ry)/(-rz)*(68*SCALE*1.55+8*SCALE)
        bound=tilt+displacement(0,1)+1.4+2*3.4
        self.assertLess(bound,24)
        print('Worst-case optical footprint: %.4fdp < 24dp' % bound)

    def test_backdrop_attribution_and_license(self):
        notes=(ROOT/'THIRD_PARTY_NOTICES.md').read_text()
        license=(ROOT/'third_party/backdrop/LICENSE').read_text()
        self.assertIn('65ab177e90e5c1d8c62e70cf7755841982da65f6',notes)
        self.assertIn('TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION',license)
        self.assertIn('lensCurve = 1.0 - sqrt',OPTICS)
        self.assertIn('Copyright 2025 Kyant',OPTICS)

    def test_no_vpn_native_or_dependency_changes(self):
        changed=subprocess.check_output(['git','diff','--name-only','76b59078963f8ef8de976c2ed9d5018654de4653'],cwd=ROOT,text=True).splitlines()
        self.assertFalse(any('/service/' in p or '/data/' in p or p.startswith('native/') or p.endswith('.gradle.kts') for p in changed))

if __name__=='__main__': unittest.main(verbosity=2)
