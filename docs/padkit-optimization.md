# PadKit optimization implementation

Implemented locally on 16 November 2026 in the active submodule at `modules/padkit/upstream/omnidroid-padkit`, on `codex/optimize-padkit-android7`, with matching parent integration changes. The standalone checkout was excluded. The parent records the published fork commit in its submodule pin. For future updates, publish the dependency commit before advancing the Omnidroid pin.

## Latest cleanup and performance follow-ups

The [PadKit README](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/0597c76737a5c335677d62288fe85420b88b3f77/README.md) now documents the complete fork changes. The initial routing and Android-only phase records below retain their original workload, test-count, source-count, and artifact-size snapshots; they are not the latest totals.

- The subsequent behavior-preserving cleanup removed redundant event wrappers and the replaced rectangle-merging helper, four unused imports, an unused internal default argument, and unnecessary reified type information. Layout collection passes and pointer-result branches were simplified. Five Compose test fixtures were hoisted outside composition, and the demo's redundant API 24 drawable qualifier was removed. Net reduction: 76 text lines, including 67 nonblank lines.
- The latest performance pass added reusable face pressed-key storage, cached immutable face contributions, singleton capture reuse, value-based capture ownership checks, and remembered face anchor/key lists. It keeps hit-test math, callback ordering, public options, immutable output snapshots, and UI-thread ownership intact.
- A temporary external JBR 21 replay measured median allocated bytes/event after 50,000 warmup events and nine batches of 20,000 events. One held face pointer fell from 528 to 184 bytes (65.2% fewer), three held pointers from 1,816 to 584 (67.8%), changing three-finger selections from 1,760 to 1,112 (36.8%), and mixed four-pointer routing through 12 controls from 1,288 to 920 (28.6%). Prebuilt touch inputs exclude touch-event construction. [Recorded evidence and limits](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/0597c76737a5c335677d62288fe85420b88b3f77/docs/performance-results.json) are part of the fork documentation; the probe itself remains outside the Android module.
- These results use a later baseline than the initial routing replay below. Percentages are not additive. Later timing comparisons are omitted because final measurements overlapped build work; Android frame timing, battery, GC pauses, and whole-app memory remain unmeasured.
- Current validation: 104 library tests plus five host tilt tests, zero failures/errors/skips, including 22 explicit API 24 cases. Debug/demo builds, release AAR packaging, full lint, ktlint, app compilation, and release R8 processing pass. The latest comparison preserved all 160 checked public members across 30 entry-point classes. Library lint remains at zero errors / 15 warnings, and demo lint at zero errors / five warnings.
- In the latest performance pass, the fork's unshrunk release AAR grew from 230,954 to 231,844 bytes (+890 bytes / 0.39%). This is a different build artifact from the earlier host-wrapper AAR below. The latest production code added ten net lines plus 114 regression-test lines; reusable storage trades a small amount of retained per-control memory for lower allocation churn.

## Android 7 compatibility and build gate

Minimum SDK remains **24 / Android 7.0**, and app target SDK remains **36**. Compile SDK is now **37**, AGP **9.1.1**, Gradle **9.3.1**, and Kotlin/Compose compiler **2.2.10**, with Java 17 bytecode. Both wrappers were regenerated and pinned to the official Gradle distribution SHA-256. Wrapper line endings are explicit for Windows and Unix.

PadKit's AAR metadata checks and writers are enabled, fixing release AAR packaging and validating the actual Compose dependency requirements. Other legacy modules retain their existing exceptions. Existing global legacy Android DSL flags remain for those other modules; PadKit itself uses Android-only built-in Kotlin support through its own build and the source wrapper.

Compose BOM constraints are exported with the library's public Compose dependencies, so transitive consumers resolve the same tested versions. The formatter plugin is 14.2.0, which detects built-in Kotlin source sets. Its Kotlin parser dependencies are excluded from the parent app's global Kotlin-version override. The checked source sets include both production Kotlin and tests, rather than just Gradle scripts.

`verifyAndroid7Compatibility` assembles the release AAR, runs Android lint including unsupported API usage, and executes the regression suites with explicit API 24 cases. Both the fork's CI and Omnidroid's public-submodule workflow use this gate.

## Runtime changes

- Added a captured-pointer owner index and reusable per-handler pointer buffers. Static relative pointers can be reused, unchanged handlers are skipped, and touch contributions merge only when their logical state changes.
- Cached usable-bounds validation when layout bounds change. New pointers use the complete circular hit predicate and the first eligible registration; captured drag-out remains supported.
- Preserved immutable published snapshots. Empty/no-op state updates reuse existing state, and unchanged simulation selections/state skip redundant copying and merging. Mutable caller-owned ID selections are still copied when changed.
- Replaced `Any?` gesture data and casts with typed internal handlers/registrations. Mutation remains confined to the Compose/UI thread, with synchronous queued delivery of reentrant transitions.
- Moved analog thumb-position reads into the offset placement lambda. Continuous and directional analog controls retain the same sizing, travel range, mapping, and release center.
- Shared allocation-light key/direction change iteration between events and haptics. Removed temporary difference/union collections and intermediate feedback lists. A reusable feedback buffer finishes classification before calling an adapter, preserving batch policy and callback behavior.
- Avoided directional-policy work on unrelated recomposition. Held policy changes still recompute output without losing capture or snapping the unquantized thumb position.

Press-before-release order, state/events/haptics callback order, shared-key ownership, zero-versus-unspecified directions, simulation restoration, live settings, feedback exclusions/coalescing, cancellation, disposal releases, and stick double-tap remain covered by tests.

## Edge cases fixed

- An ineligible corner of an earlier control's rectangle no longer blocks a valid circular hit on another control. Existing registration priority remains when both controls are eligible.
- Empty face-button layouts are passive and do not intercept an underlying control; the underlying handler is also safe with empty anchors.
- Collapsed/invalid bounds cancel ownership immediately and reset gesture data, so the next touch starts centered without a false double-tap.
- Public anchor/radial geometry rejects non-finite values and negative sizes at configuration boundaries. Zero sizes remain valid; custom anchor positions are not clamped to a narrower range.

## Cleanup and preserved features

The 68,778,062-byte checked-in formatter, duplicate hook machinery, unused desktop/iOS/Wasm targets and samples, publication/signing/versioning setup, and wiki deployment were removed. Their unchanged committed contents were verified and saved in the ignored `build/padkit-retired-source-backup.zip` before cleanup; Git also retains the original files. The first pass reduced source content to approximately **0.38 MB across 101 files**, versus **69.24 MB across 122 files** before this work. This excludes Git history, generated outputs, and the local recovery archive; it is not a claim about clone-history size or freed local disk space.

The optional Android demo remains, uses Material 3 and a ComponentActivity, supports API 24, and exercises all five control types. Its template identity was replaced with `gg.padkit.sample` / “PadKit controls”. The demo is only configured with `-PincludeSample=true`.

PadKit now depends on **icons-core**, rather than extended icons. The app and touch-input module independently use extended icons, so this does not remove that artifact from the whole application. Material 3, immutable collections 0.3.8, the public control defaults, and the reused cross-arrangement helper remain because they support the current API and integration. No unnecessary Kotlin/collections API migration or package rename was introduced.

All five control primitives, both face-button APIs, composite regions, radial/grid helpers, double-tap click, tilt simulation, haptic policies, and customization slots remain. Host preferences, skins, console placement, sensors, backend coordinate conventions, hardware input, touchscreen/stylus routing, and rumble retain their existing owners.

## Android-only second pass

The library now uses Android's conventional `lib/src/main/kotlin`, `lib/src/main/AndroidManifest.xml`, and `lib/src/test/kotlin`. Shared and Android production sources are merged into the same Android source set; engine and Robolectric tests are merged into the same local Android test source set. The fork no longer needs custom source-directory wiring. Omnidroid's wrapper points directly to these standard directories.

The optional Android demo is now a single `:sample` application with `sample/src/main` sources and resources. Its build, CI command, and contributor instructions use the new layout. Android haptics files have ordinary Android-library names; `@file:JvmName("HapticGenerator_androidKt")` preserves the existing public Android factory and Compose helper descriptors for compiled callers. The feedback adapter interface remains necessary for Omnidroid's own routing and feedback policy.

The HotSpot-specific allocation/replay benchmark and its `padkitBenchmark` build wiring were removed from the module and host wrapper. Its earlier results below are historical measurements of the first-pass runtime changes. Before moving sources and removing the harness, the current source trees were saved in the parent checkout's ignored `build/padkit-before-android-only-layout.zip`. All 78 file moves were checked against SHA-256 hashes before the intentional build/comment updates. Android unit tests, lint, wrappers, licenses, and the Android demo remain part of Android development and validation.

The second-pass fork compatibility/style gates, Android demo build/style checks, host compatibility/style gates, host tilt tests, and app compilation all passed. The host test total remains **105**, including **22 API 24 cases**, with no failures, errors, or skipped tests. All **158 existing public members across 30 entry-point classes** remain present. The library release dependency graph selects Android Compose artifacts and contains no desktop rendering or native-platform targets. The source tree is now **100 files / 377,494 bytes** under the same exclusions as the first-pass count.

## Initial verification snapshot

The final parent gate passed **105 tests, zero failures/errors/skips**:

- 46 engine, event, feedback, quantization, and geometry cases.
- 38 Compose interaction cases, split across API 24 and API 35, using the current v2 Compose test rule.
- 16 Android haptic cases across API 24/25/26/28/35.
- Five host tilt cases.

There are **22 explicit API 24 cases**. New coverage includes overlap priority/fallback, empty controls, collapsed bounds, immutable delivered snapshots, mutable simulation selections, mixed event ordering, adapter mutation during feedback delivery, geometry validation, and an independent seeded 2,000-frame multi-finger key/override model. UI cases also assert the merged minimum SDK, empty-control pass-through, thumb size/travel/release center, and existing held-policy behavior.

The independent fork's compatibility/style gates also passed. The Android demo APK built and `aapt2` reported minimum SDK **24**, target SDK **36**, and compile SDK **37**. The main app merged manifest likewise reports minimum **24** / target **36**.

Omnidroid debug Kotlin compilation passed with the new library. Release R8/D8 generation passed with API 24 configuration and produced a DEX with version `037`. A one-time `javap` descriptor comparison checked **158 existing members across 30 public entry-point classes**, with no missing public members; module-internal implementation members were excluded. The release AAR contains no benchmark, JUnit, Robolectric, or review-probe classes.

Lint has **zero errors** in both builds. The parent wrapper reports one dependency-update warning for Robolectric; the independent fork reports 15 warnings about available dependency/tool versions and using version-catalog aliases. There are no unsupported-Android-API errors. Existing unrelated app deprecation warnings remain. Neither remote CI execution nor a full signed application APK/native-emulator rebuild is claimed.

## Initial measured performance and tradeoffs

The first-pass host JVM replay used the same workload, machine, JBR 21.0.10, 12 registered controls, event generation, and Omnidroid feedback policy before/after. Each scenario warmed up for 20,000 events and measured nine batches of 10,000 events. Time is the **median of batch-mean nanoseconds per event**, not a per-event p95/p99 latency distribution. Allocations are bytes per event on the benchmark thread. The Android-only module no longer includes this HotSpot-specific harness.

- **Two pointers:** 1,299.93 → 1,054.93 ns/event (**18.8% less time**); 4,152 → 1,160 bytes/event (**72.1% fewer allocations**).
- **Five pointers:** 1,789.92 → 1,099.81 ns/event (**38.6% less time**); 7,176 → 2,016 bytes/event (**71.9% fewer allocations**).
- **Ten pointers:** 3,378.62 → 2,240.06 ns/event (**33.7% less time**); 11,344 → 3,840 bytes/event (**66.1% fewer allocations**).
- **Unchanged simulation:** 123.83 → 29.01 ns/update (**76.6% less time**); 536 → below 0.01 measured bytes/update. The tiny measured remainder includes the allocation-counter call overhead.

Reusable routing buffers retain a small amount of per-control scratch storage. The unshrunk host release AAR grew from **246,804 to 250,501 bytes** (**+3,697 bytes / 1.50%**) with the new guards, types, caching implementation, and Android-only source cleanup. No whole-app APK size reduction is claimed. These are deliberate, visible tradeoffs for lower hot-path work and robustness, not an assertion that every metric improved simultaneously.

After the first-pass checks, a Free Dynamic arm64 debug APK was built, installed, and launched successfully on the connected Samsung SM-S928B. The user confirmed that it works well. Real-device frame timing, energy/thermal behavior, and touch-to-display latency remain unmeasured. Startup/library baseline-profile journeys were not presented as gameplay benchmarks.

## Reproducing checks

From the Omnidroid root:

```powershell
.\gradlew.bat :omnidroid-padkit:verifyAndroid7Compatibility :omnidroid-padkit:ktlintCheck :omnidroid-touchinput:testDebugUnitTest
.\gradlew.bat :omnidroid-app:compileFreeDynamicDebugKotlin :omnidroid-app:processFreeDynamicDebugMainManifest
.\gradlew.bat :omnidroid-app:minifyFreeDynamicReleaseWithR8
```

From the active PadKit submodule:

```powershell
.\gradlew.bat :lib:verifyAndroid7Compatibility :lib:ktlintCheck
.\gradlew.bat -PincludeSample=true :sample:assembleDebug :sample:ktlintCheck
```

Local logs and historical benchmark XML live under the respective ignored `build/` directories.
