# Third-party notices

## FlClash navigation lens motion

`app/src/main/java/com/github/mihomo/android/ui/glass/NavigationLensBar.kt`
contains a Kotlin/Jetpack Compose adaptation of the spring motion, `_wobble`,
`_lensRect`, and lift/release implementation from FlClash's `lib/ui/navigation_dock.dart`.

- Upstream: https://github.com/chen08209/FlClash
- Source revision: `8d4f9744948751f1273fe871e1d05276fac09b0e` (v0.9.1)
- Source: https://github.com/chen08209/FlClash/blob/8d4f9744948751f1273fe871e1d05276fac09b0e/lib/ui/navigation_dock.dart
- Authors: FlClash contributors (see upstream Git history).
- License: GNU General Public License, version 3. The complete license text is
  provided in this repository's `LICENSE`, which is also Nexus's license.

Modifications: ported Flutter's continuously retargetable spring to Compose's
spring simulation, preserved velocity across interruption, and adapted the
selection-triggered lift and lens bounds to Nexus's existing three-slot navigation
without redistributing or replacing its icons. The glass material remains Nexus's existing renderer.

## Backdrop liquid-glass lens profile

`app/src/main/java/com/github/mihomo/android/ui/glass/ShortcutRefractiveOptics.kt`
adapts the normalised `circleMap(x) = 1 - sqrt(1 - x*x)` lens-edge profile from
Backdrop's `RoundedRectRefractionShaderString` and its normal/light-dot highlight concept.

- Upstream: https://github.com/Kyant0/AndroidLiquidGlass
- Revision: `65ab177e90e5c1d8c62e70cf7755841982da65f6` (2.0.1)
- Source: `backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt`
- Copyright: 2025 Kyant
- License: Apache License 2.0; full text in `third_party/backdrop/LICENSE`.

Modifications: integrated the curve into Nexus's existing bounded single-pass AGSL
refraction, keeping its shared scene capture, contact-clear region and colour dispersion.
The glint is limited to a narrow rim and follows finite pickup; it does not wash the
card interior white. No Maven binary dependency is added: Backdrop 2.0.1 was built
with Kotlin 2.4.10 / Compose 1.12.0, while Nexus uses Kotlin 2.1.0 / Compose 1.7.5.
This is a small attributed source adaptation, not an integration of the complete library.
