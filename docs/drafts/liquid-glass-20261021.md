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

## Launch-crash investigation (vivo Android 16 report)

- Confirmed source defect: the dashboard title lenses were descendants of the scrolling Column recorded into `titleScene`, while sampling that same scene. Moved the header to a sibling so the recorded content excludes its own lenses. This also restores the title’s pinned layout.
- The original hierarchy failed a launcher visibility regression; the corrected hierarchy passes. Two focused API 35 Robolectric NATIVE tests pass: dashboard composition/RecordingCanvas path plus title-outside-scroll assertion, and RuntimeShader uniform bindings. RecordingCanvas does not execute the vivo GPU driver, native VPN initialization or an Android 16 installation.
- The exact reported vivo crash is NOT reproduced or conclusively diagnosed. APK zip alignment and native ELF LOAD alignment are 16 KB compatible. No USB Android device is connected; the host lacks an Android 16 hardware-backed runtime.
- No replacement APK is being delivered as a verified fix. The next required evidence is the installed draft’s `files/last_crash.txt` (Java exception logger), or an adb crash-buffer capture if that file is absent/native termination occurred. Existing liquid-glass effects and VPN/native libraries are retained.

## Candidate 2 after original was uninstalled

The original device-private crash file may have been removed by uninstall. Do not
ask the owner to reinstall the old crashing APK to retrieve it. Candidate 2 contains
the corrected dashboard scene hierarchy from 5466fa4; this remains a candidate, not
a confirmed fix for the Android 16 vivo launch failure.

A debug-only launcher (`DraftDiagnosticsActivity`, separate `:draft_diagnostics`
process, software-rendered native Android views) now checks this app's saved Java
exception and its own Android process-exit history. A fresh install proceeds directly
to the normal UI. If a new crash is recorded, the next launch displays its report
instead of immediately repeating the failing UI launch. Copy/share actions require
a tap and never upload automatically; retry retains evidence. Native/system crashes
may provide only exit information, not a complete native stack. This mechanism itself
has not been checked on a vivo device. Normal app startup and all glass/VPN behavior
are retained; only the diagnostics process bypasses normal native/Compose startup.

Build: `assembleDebug` and the two focused classes `DraftLaunchRegressionTest`
and `DraftDiagnosticsTest` succeeded (4 tests, zero failures/errors/skips) on the
host's API 35 Robolectric runtime. MainActivity RecordingCanvas/pinned-heading and
shader uniform checks passed; diagnostics tests cover fresh launch, report viewing,
explicit sharing and evidence-preserving retry. These are not hardware Android 16
or vivo GPU tests. Full unit suite was not rerun for this candidate.

The first build daemon disappeared at dexing. A bounded-memory retry using one
worker, in-process Kotlin compilation, and a 1500 MiB Gradle heap completed in 1m35s.
APK ZIP integrity, v2 debug signature, 16-KiB archive alignment, arm64-only ABI,
separate application ID, version suffix and diagnostics launcher manifest checked.

Package remains `com.github.mihomo.android.draftglass`, label `Nexus Glass DRAFT`,
versionCode 37, versionName `1.0.32-with-suspended-draft-glass-candidate2`.
No merge, release or production package replacement is authorized.
