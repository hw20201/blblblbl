# Disposable liquid-glass UI draft (2026-10-21)

This work is isolated on `wabi/draft-liquid-glass-20261021`, based on main. It is not a release and must not be merged without the owner's later explicit approval. No existing draft branch is replaced.

## Scope

- Pinned glass title capsules on Dashboard, Policy and Settings, with content scrolling underneath instead of clipping at a header-height plane.
- Removed Policy's full-width top colour/scrim strip; policy groups, selection, testing and node controls are retained.
- Shortcut glass uses the existing bottom navigation lens's live recorded backdrop kernel, extended with a contact-local refraction normal for B. It does not stretch a static HTML background into the cards.
- B tilts about the card centre in response to the finger position, with the touched edge receding and the opposite edge advancing. Ordinary presses no longer translate the complete card; explicit drag handling remains. A/B/C selection and spring response are retained.
- Broader outline-only suspension shadows and a calmer three-field default light backdrop. Existing custom palettes and dark palette selection remain intact.

## Draft package

`assembleDebug` produces a debug-signed arm64 APK, with application ID `com.github.mihomo.android.draftglass`, version suffix `-draft-glass` and launcher label `Nexus Glass DRAFT`. It installs separately from the production app and has its own settings/subscription state. No production signing key, tag, PR or release is required. Removing the draft app does not remove the production app.

## Verification and limits

Use JDK 17+, Android SDK 36 and the existing native-library pair. The branch does not change native VPN/routing libraries.

```
./gradlew :app:assembleDebug --no-configuration-cache --max-workers=1
```

The repository's native-library verification task currently captures a Gradle script object, which is incompatible with Gradle 9.6's configuration cache. Disable the cache for this local build; do not remove native-library verification.

API 33+ supports the RuntimeShader refraction path; older Android / reduced-quality modes retain the existing blur or flat fallback. GPU cost and the final tactile impression require testing on the owner's actual Android device. Robolectric native rendering is not a physical-device frame-rate or VPN connection test.

### Local results for this draft

- Debug APK assembly, focused UI/optics/gesture/regression checks and `git diff --check` passed.
- Robolectric policy scroll/render screenshots were inspected; these are not device tests.
- The broader unit-suite attempt terminated with a native SIGSEGV (exit 139). Partial XML recorded 202 tests, zero recorded assertion failures/errors and one skip; the full suite has NOT passed.
- No physical Android VPN, frame-rate or tactile testing was performed.
- Delivered arm64 draft: versionCode 37, versionName `1.0.32-with-suspended-draft-glass`; debug-signed, separate app data.
