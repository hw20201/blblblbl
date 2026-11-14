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

## Candidate 3: blank first/second tab report

The owner supplied a vivo Android 16 screenshot showing a surviving bottom dock
but no Policy title/controls/content, and reported the Dashboard behaved likewise.
This is a visible-content failure, not evidence of another process crash.

Source inspection identified an unbounded-recording defect. All three pager pages
are kept composed. Each local scene draws the full-window background after translating
by `underlay.origin - page.origin`. That translation deliberately cancels the page's
offset to sample the correct screen location, but without a clip it also lets an
off-screen page replay the opaque background across the active viewport. A later
page can consequently cover the earlier page's foreground. The screenshot is
consistent with this mechanism; the exact vivo GPU output cannot be reproduced on
this host.

The correction clips each recorded GraphicsLayer to its local dimensions, clips
recording commands before drawing the translated underlay, and bounds each pager
page. The normal record-once/draw-layer path is retained (no double foreground draw).
Live lens sampling, Mode B, pinned headings, separate draft identity, diagnostic
launcher and VPN/native libraries are retained. This is a corrected candidate,
not a claim that the vivo hardware path has been validated.

New tests exercise the actual MainActivity pager in light and dark: Dashboard
heading and shortcut content, Policy heading and real empty state, Settings heading
and TUN controls, then return to Dashboard. PixelCopy images must contain sharp
foreground content above the dock; semantics alone cannot pass the assertion.
A separate RecordingCanvas contract check requires a bounded 100x100 source even
when a full-window underlay is recorded. These host checks do not execute a vivo GPU.

Candidate 3 verification completed:

- `assembleDebug`: successful.
- Targeted suite: 12 tests passed, zero failures/errors/skips. Includes both
  full-pager theme tests, source-bounds contract, existing launcher/diagnostics,
  populated Policy glass rendering and navigation lens rendering checks.
- Inspected final `dark-dashboard.png`, `dark-policy.png` and `light-settings.png`
  under `app/build/reports/draft-pages/`: Dashboard title/cards, Policy heading/
  empty state/actions, and Settings heading/control rows are visibly present.
  No real subscription was added to the host test account.
- APK ZIP integrity, v2 signature and 16-KB ZIP alignment passed.
- APK: `Nexus-Glass-DRAFT-arm64-candidate3-20261021.apk`, 53,253,864 bytes;
  SHA-256 `ea7c2e9c7448a8226b50828fefb4dc9bbfec15c15ec23b5e41aef5b1bf7258bf`.
- Package `com.github.mihomo.android.draftglass`, label `Nexus Glass DRAFT`,
  versionCode 37, versionName `1.0.32-with-suspended-draft-glass-candidate3`;
  arm64 only, debug signed. Diagnostic launcher retained.

These are host API35 checks and inspected captures, not a confirmed vivo Android16
fix, real-device shader/performance validation, or VPN connection test.


## Candidate 4: conservative scroll-allocation polish

The owner confirmed Candidate 3 displays all content on the Android 16 vivo X200
mini and meets the visual requirements, with slight scroll stutter remaining.
This pass preserves the working clipping/recording hierarchy and appearance.
It does not change A/B/C motion parameters; a separately requested follow-up on
shortcut press/motion behavior is intentionally outside this candidate.

Two small hot-path changes avoid repeated native/managed allocation:

- Navigation/title glass caches its native effect graph while geometry and optical
  uniforms are unchanged. A new recorded frame remains a live input; it does not
  need a new RenderEffect. Every geometry, density, expansion/refraction, strength,
  radius or frost-mode change still refreshes the uniform snapshot. The Gaussian
  filter node is also retained while its density/mode is unchanged.
- Each shortcut/card replay owns reusable corner arrays and a native Matrix instead
  of creating two arrays and a Matrix per anchored draw. Projected corners and the
  inverse mapping are still recomputed for every draw, so Mode B tilt/press and
  moving-scene alignment are not frozen or quantized.

No shader equation, glass quality, blur radius, animation cadence, page layout,
mesh background, native VPN library or diagnostic behavior was reduced/changed.
These reduce specific allocation work, not a measured guarantee of smoothness.
No physical vivo frame trace, FPS or jank percentage is available on this host.

Candidate 4 validation:

- assembleDebug and the focused API35 native suite succeeded with configuration
  cache disabled (the existing verifyNativeLibraries task is not cache-serializable).
- 27 targeted tests passed; zero failures/errors/skips. Includes light/dark full
  pager visibility, return-to-dashboard, launcher/diagnostics, glass rendering,
  gestures, bounds, and the three new allocation/reusable-geometry contracts.
- In the cache test, 240 unchanged-optics scene updates reuse the same one native
  effect; changes to each relevant optical/geometry property invalidate it.
- Inspected the eight generated light/dark pager captures; page content remains
  visible. These host renders do not certify the vivo GPU's smoothness.
- APK ZIP CRC, v2 debug signature and 16-KB ZIP alignment passed.
- APK size: 53,257,452 bytes; SHA-256:
  `6202a7843af281b45ecae2079fdc61d068ce09efc784b47028e8f94a2f8a7fc5`.
- Package remains `com.github.mihomo.android.draftglass`; label `Nexus Glass DRAFT`;
  versionCode 37; versionName `1.0.32-with-suspended-draft-glass-candidate4`;
  arm64 only, debug signed. Native library content is unchanged from Candidate 3.

## Candidate5 performance — 2026-10-22
- Direct MainActivity icon launch; diagnostics retained through launcher long-press shortcut.
- Non-debuggable, same-package/debug-certificate performance build; no VPN feature/data stripping.
- Bounded synchronous dashboard overscroll and spring return, fixed header/navigation; reduced eager pager composition, isolated background layers, paused decorative mesh motion during scroll.
- 23 focused API35 Robolectric tests pass; light/dark page renders inspected; APK ZIP, signature and 16-KB alignment checked. No physical vivo Android16 startup/FPS measurements; device improvement remains to be tested.
- APK SHA256: 8bddf409dbed7f9675a40286ff1cd4b0d31a2a37a9ec854e42d142ed53d6215a (44,279,652 bytes).
- A/B/C motion refinement remains separate in /home/user/workspace/nexus-motion-wip/ and is not included.

### Candidate6 shortcut touch motion — 2026-10-22
- Refined the live dock-card contact response instead of changing the settings dialog: A is a restrained 1.004× / 1.2dp float; B follows normalized finger position with a center dead zone and a capped 6° radial tilt (contact edge recedes, opposite edge lifts), without global zoom/lift; C is a distinct 1.014× / 3.2dp buoyant lift.
- Release is variant-specific; C uses a lower-damping spring, while cancellation is critically damped. Held B angle follows direct touch and maintains a stable gesture hit slot.
- Focused Compose/Robolectric test suite passed (23 tests) covering light/dark live glass, fixed hit geometry, center jitter, angle limits, opposite-edge geometry, cancel/click, and measured rendered lift/rebound. Saved screenshots were inspected; they are software-rendered API35 evidence only, not vivo GPU or tactile verification.
- Candidate6 versionCode 38 keeps the candidate5 performance build type, isolated `.draftglass` app identity, same signing setup, and retained native VPN binaries. No production branch/release is touched.
- Built performance APK: `Nexus-Glass-DRAFT-arm64-candidate6-motion-performance-20261022.apk`, 44,280,000 bytes; SHA-256 `2c46088be6de19643f2a7d6dc304ccfcd34ec66c1dddfd2f29e859068710c922`. Gradle output metadata identifies package `com.github.mihomo.android.draftglass`, versionCode 38, arm64-v8a native libraries are present, and ZIP integrity check passes.

### Candidate7 interaction and glass geometry — 2026-10-22
- Removed the redundant outer stroke from unselected two-column proxy group glass cards; retained the group surface's existing inner/bevel edge and selected-state blue outline. Non-glass and other card borders are unchanged.
- Replaced the dashboard's mode bottom sheet with a frosted glass panel that expands/collapses in place below the mode control using a spring and fade, with Back-to-dismiss and immediate selection. Other independent bottom sheets remain.
- Kept candidate6 A/B/C touch-follow, contact-side sink/opposite lift, and settle behavior unchanged; added/retained focused regression coverage alongside new picker and border-policy tests.
- Pending: focused test/build and APK metadata verification; no physical vivo touch/visual assessment claimed.
