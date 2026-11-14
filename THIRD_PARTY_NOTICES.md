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
