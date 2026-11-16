# Omnidroid PadKit optimization review

This is the baseline assessment. The subsequent implementation, compatibility checks, and performance results are recorded in [padkit-optimization.md](padkit-optimization.md).

Reviewed 16 November 2026. Scope: `modules/padkit/upstream/omnidroid-padkit` at **8614b313641b936ceee5cff06a8f0a6771692fd6**, its Android source wrapper, and the Omnidroid consumers. The submodule was clean and detached at the parent project's pinned revision. The standalone PadKit checkout is outside this assessment.

## Assessment

The current input design fits Omnidroid. Preserve its typed IDs, immutable published state, per-handler ownership, synchronous ordered transitions, cancellation/disposal releases, simulation overrides, and replaceable visuals. The recent lifecycle and capability work is present and covered by tests; it should be the foundation for optimization.

The best opportunities are build reliability, less allocation and repeated state processing per touch event, and moving thumb positioning out of composition. Most removable material belongs to repository tooling, unused platform targets, samples, and publishing infrastructure. Those removals chiefly improve checkout size and maintenance; they do not automatically improve Android gameplay or APK size.

No device performance measurements were made. CPU, allocation, latency, frame-time, energy, and APK improvements below are hypotheses to measure, not established savings. Optimizing every metric simultaneously is not a realistic guarantee: reducing allocations may retain reusable buffers, and adding specialized indexes increases implementation complexity. Preserve behavior and establish evidence for each tradeoff.

## Verified baseline

- **68 existing tests passed in a fresh run**, with zero failures, errors, or skipped cases: 34 shared engine/event/feedback/geometry tests, 16 Compose interaction tests, 13 Android haptic cases, and five host tilt tests. The two Gradle test tasks were explicitly rerun; compilation dependencies were reused.
- **Android release lint: no issues found.** This uses Omnidroid's existing lint policy, including its global disabled checks; it is not a security or accessibility certification.
- **Omnidroid app Kotlin compile check passed using existing compiled outputs.** `:omnidroid-app:compileFreeDynamicDebugKotlin` was up-to-date. This is an integration check, not a clean rebuild, APK assembly, or native emulator build.
- **PadKit release AAR assembly failed** because `bundleReleaseAar` requires an AAR metadata-check directory whose producer is globally disabled. Successful Kotlin compilation and unit tests do not establish that release packaging works.
- A temporary init script re-enabled only PadKit's metadata tasks, exposing **18 dependency compatibility messages**. In particular, resolved Compose **1.12.0** UI/foundation artifacts require **compile SDK 37+ and AGP 9.1.0+**, while the host uses SDK 36 / AGP 9.0.0. This is a confirmed compatibility mismatch hidden by the blanket suppression.
- Two isolated diagnostic tests confirmed the overlap-hit and empty-anchor behaviors described below. These probes were injected from ignored build output, not added to the committed test suite.
- Inventory: **122 tracked files; 69,236,805 bytes of tracked content**, excluding Git history and generated output. The formatter alone is **68,778,062 bytes (99.34%)**.
- Library production: **49 Kotlin files / 3,397 physical lines**, including comments and blanks. Omnidroid compiles the 45 common production files plus the Android adapter: **46 files / 3,289 lines**. The three other platform adapters total just 108 lines. Shared tests occupy five files / 541 lines.

Local evidence was captured in `build/padkit-review-release.log`, `build/padkit-review-metadata.log`, `omnidroid-padkit/build/reports/lint-results-release.txt`, and `build/padkit-review-probes.log`. These ignored build outputs are not published and may be overwritten. Current implementation results and measurement limits are recorded in [padkit-optimization.md](padkit-optimization.md).

## Package review

Counts below describe `lib/src/commonMain/kotlin/gg/padkit`; line counts include documentation and license headers.

- **Root package — 2 files / 381 lines.** `PadKit` connects Compose pointer input, simulation, event callbacks, and haptics. `PadKitScope` registers controls, owns capture and contributions, merges output, and queues reentrant transitions. Keep both responsibilities, but separate pure routing/aggregation from Compose state internally when measurement or testing benefits. Do not introduce asynchronous delivery or frame-based conflation.
- **anchors — 4 / 198.** Radial primary/composite anchors and typed grid generation. Omnidroid uses both radial controls and `buildGridAnchors` for its console-specific six-button geometry. Keep all three generation paths. Cache configuration-derived anchor construction where worthwhile; validate public geometry at the boundary.
- **config — 2 / 67.** Haptic mode and normalized feedback settings. Used directly by the game screen. Keep intensity, direction policy, exclusion IDs, and coalescing configuration; Omnidroid intentionally chooses activation-only D-pad feedback, silent continuous sticks, and independent effects.
- **controls — 5 / 547.** Buttons, cross, continuous stick, directional analog stick, and face clusters. All five are integrated. Keep both face-button overloads: standard radial and custom-anchor layouts serve different console arrangements. The shared analog visual layout is the best Compose optimization target.
- **handlers — 9 / 444.** Control-specific gesture processing, captured pointers, state results, and shared analog gesture data. All handler types support active Omnidroid features. Replace unchecked `Any?` data with typed internal registration if restructuring; preserve mapping changes and reset behavior. Optimize routing and state contribution updates before rewriting gesture math.
- **haptics — 3 / 174, plus platform files.** Feedback abstraction, effect enum, and transition policy. Keep the Android adapter and settings-aware policy. The Android adapter submits directly through the platform service; the earlier thread/dispatcher issue is already fixed. Repeated collection diffing is the remaining performance candidate.
- **ids — 1 / 55.** Sealed typed IDs with inline value classes. Keep. Avoid changing public IDs to strings or introducing enum ordinals tied to a particular console.
- **inputevents — 2 / 155.** Immutable events and state-to-event conversion. Omnidroid consumes `onInputEvents`. Keep event types, release sentinels, ordering, and synchronous delivery; remove temporary set unions/differences where benchmarks show value.
- **inputstate — 2 / 171.** Immutable persistent sets/maps and contribution merging. Keep immutable published snapshots. Use local builders or no-op returns internally rather than exposing mutable state or a single global key bitset; IDs include host action and Android key values, and multiple controls can contribute the same key.
- **layouts.anchors — 1 / 76.** Places face buttons and cross foregrounds. Used indirectly by active controls and Omnidroid's customized cross rendering. Keep; test anchor/measurable-count and geometry boundaries if expanding the public anchor API.
- **layouts.radial — 1 / 191; secondarydials — 5 / 199.** Radial placement, scale/position modifiers, and bounds calculations. `BaseLayout` uses these throughout Omnidroid. Keep. A modifier-node migration is a maintainability option, not an established performance fix; layout geometry is lower priority than the touch path.
- **ui — 4 / 335.** `DefaultCrossForeground` is reused by both Omnidroid cross skins with custom dial renderers. Keep that arrangement helper. The stock button/background/composite drawings are largely replaced in production and are candidates for removal from an Omnidroid-specific API or an optional demo layer after changing default arguments and tests. Removing the whole package would break the current integration.
- **utils — 4 / 227.** Geometry mapping, normalization, time constant, and quantization. Keep the circle/square mapping and current thresholds unless deliberately changing user feel. `GeometryUtils.Float.toDegrees()` has no source call sites and is a safe small cleanup candidate. The other similarly small helpers have live callers; their size alone is not a reason to delete them.

## Dependencies and build

The Android wrapper reads the pinned submodule's common and Android **source directories**. It does not apply the submodule's `lib/build.gradle.kts`, build its samples, or use its Compose Multiplatform plugin. Editing the submodule's version catalog alone will not change Omnidroid's resolved dependencies.

Current integrated resolution, verified with `releaseRuntimeClasspath`:

- Kotlin stdlib/compiler selection **2.2.10**; Android Gradle plugin **9.0.0**; Gradle **9.1.0**; Java bytecode target **17**; local Gradle runtime **JBR 21.0.10**; compile/target SDK **36**, minimum SDK **24**.
- Compose BOM **2026.08.00** resolves runtime/foundation/UI to **1.12.0**, Material 3 to **1.4.0**, and Material icons to **1.7.8**. Different component versions within a BOM are expected; do not force them all to the same version.
- Immutable collections resolves to **0.3.8**. It is used throughout public parameters and state. Keep the dependency. Evaluate an upgrade with the host Kotlin version and API migration; current upstream 0.5.x release notes include Kotlin 2.3 and renamed copy-returning methods. A blind version bump is not a performance plan. [Upstream release notes](https://github.com/Kotlin/kotlinx.collections.immutable/releases).
- Runtime/foundation are essential. Material 3 currently supports the stock default visuals. Omnidroid itself already uses Material 3, so removing this declaration alone will not remove it from the app.
- `material-icons-extended` is excessive for PadKit's four default keyboard arrows. Prefer icons-core or four local vectors. Omnidroid app and touch-input modules independently request extended icons, so a PadKit-only dependency edit will not eliminate the app's resolved extended-icons artifact. Android explicitly warns that this library is large and can affect APK size and build/preview time; measure the final shrunk app rather than assuming an APK reduction. [Android resource guidance](https://developer.android.com/develop/ui/compose/resources).
- Robolectric **4.14.1**, Compose UI test/JUnit dependencies, and the debug UI-test manifest support valuable lifecycle/haptics coverage. Retain their test roles; check the final shipping artifact for test-only material if changing variants. Haptic cases run on SDKs 25/26/28/35; the exact API 24 app minimum still needs a device check. Host tilt tests are coroutine unit tests, not device API coverage.

The submodule's independent build declares Kotlin **2.3.20**, Compose Multiplatform **1.11.1**, AGP **8.2.2**, Gradle **8.2.1**, collections **0.3.7**, Dokka **1.9.0**, ktlint plugin **11.6.1** / engine **1.0.1**, and Maven publish plugin **0.28.0**. Sample-only dependencies include Activity Compose **1.7.2**, AppCompat **1.6.1**, Core KTX **1.10.1**, Material 2, resources, and desktop runtime. These are a separate maintenance stack, not Omnidroid runtime packages.

### P1: restore release packaging and dependency validation

[Omnidroid's root build](https://github.com/Ahmed-Abousaif/Omnidroid/blob/08e9d42e/build.gradle.kts#L36) disables tasks whose names contain `AarMetadata`, including PadKit's check and metadata writer. `bundleReleaseAar` expects `build/intermediates/aar_metadata_check/release/checkReleaseAarMetadata`; it fails because that directory is absent. Fix the producing tasks and their dependency compatibility rather than creating an empty directory. Keep the repair narrowly scoped until other modules' reasons for suppression have been evaluated.

Re-enabling the checks in an ignored diagnostic init script exposes 18 dependency compatibility messages, including Compose 1.12.0 requirements for compile SDK 37+ and AGP 9.1.0+. First choose either a Compose BOM/dependency set supported by SDK 36 and AGP 9.0, or a coordinated compile SDK/AGP/Gradle/Kotlin upgrade meeting all resolved artifact requirements. Raising compile SDK does not inherently require raising minimum SDK or target SDK. Restore validation after that alignment and reproduce a clean PadKit release assembly; do not simply remove the suppression and assume the build is fixed.

The parent also explicitly opts into legacy Kotlin/Android DSL behavior with `android.builtInKotlin=false` and `android.newDsl=false`. Builds warn those switches go away in AGP 10. Choose a coordinated direction: for an Android-only fork, a normal Android library using supported Kotlin integration is simplest; if retaining multiplatform sources as an active product commitment, migrate to `com.android.kotlin.multiplatform.library` and a supported Kotlin/AGP pair. The new KMP plugin has one variant and opt-in test configuration, so port Robolectric resources, test source wiring, and CI task names deliberately. Do not toggle those global flags before all dependent modules are ready. [AGP migration guidance](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin), [Kotlin compatibility ranges](https://kotlinlang.org/docs/gradle-configure-project.html).

### P1: make fork CI protect the actual integration

The fork workflow calls `assemble`, which does not establish that regression tests run. Common tests live in the fork, while Compose/Android feedback tests live in the parent wrapper and tilt tests live in the host module. Move library-specific Android tests into the fork and wire them into both supported build paths, or run a pinned parent integration job for fork PRs. Retain the lightweight public-submodule workflow in Omnidroid. A change must pass library tests before its parent pin is advanced.

The fork build matrix includes retired `ubuntu-20.04` and `macos-12` runners; its `gradle: [8.3]` axis is unused by the command, which actually runs the wrapper. GitHub retired those OS images in April 2025 and December 2024. Update the workflow to maintained runners, execute the required tests, and keep only OS jobs that cover retained targets. [Ubuntu runner retirement](https://github.blog/changelog/2025-01-15-github-actions-ubuntu-20-runner-image-brownout-dates-and-other-breaking-changes/), [macOS runner retirement](https://github.blog/changelog/2024-08-19-notice-of-upcoming-deprecations-and-breaking-changes-in-github-actions-runners/).

The release workflow selects JDK 21 while its wrapper is Gradle 8.2.1. Running Gradle on Java 21 is supported from Gradle 8.5. Retire the unused publishing path or align its runtime and wrapper before relying on it. This conclusion comes from the checked-in files and official compatibility documentation; no remote workflow execution was inspected. [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).

## Runtime findings and enhancements

### P2: reduce touch routing and state-allocation work

[PadKitScope](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/8614b313641b936ceee5cff06a8f0a6771692fd6/lib/src/commonMain/kotlin/gg/padkit/PadKitScope.kt#L98) rebuilds tracked-ID collections, scans handlers for captured-pointer ownership, groups pointers, allocates relative-pointer lists, runs every handler, and folds fresh immutable contribution state for each event. Work scales with both handlers and pointers. This is acceptable for a small pad, but is the most direct CPU/allocation optimization candidate at high touch-report rates.

Use an internal pointer-owner index, bounded reusable scratch storage, cheap no-change returns, and contributions that are recomputed only when their pointer set or configuration changes. Reuse the previous merged touch snapshot when no contribution changed. Do not skip a handler that needs a release when its last pointer disappears. Keep shared-key union, current analog precedence, captured drag-out, simulation precedence, and disposal release tests. Start with cheap no-op checks before a more complicated incremental aggregator.

`PadKit` calls `updateSimulatedInput` from `SideEffect`, and the directional analog control republishes its current contribution from another `SideEffect`. Both can rebuild merged state on unrelated recomposition. Guard unchanged inputs/configuration while preserving ID-only override changes, newly registered handlers, held-policy changes, and live settings changes.

### P2: move thumb displacement into placement

[AnalogControlLayout](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/8614b313641b936ceee5cff06a8f0a6771692fd6/lib/src/commonMain/kotlin/gg/padkit/controls/ControlAnalog.kt#L98) reads `positionState.value` during composition, transforms it, and passes Dp values to `Modifier.offset`. A drag therefore invalidates composition just to move a thumb. Read position inside a placement lambda or a focused layout modifier; retain fixed thumb sizing and the same mapping/sign conventions. Keep `BoxWithConstraints` only where its constraints are useful. Both continuous and directional analog controls benefit from this shared change. Android recommends deferred state reads and lambda offset modifiers for this pattern. [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

### P2: eliminate duplicate diffing and intermediate effect lists

[InputEventsGenerator](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/8614b313641b936ceee5cff06a8f0a6771692fd6/lib/src/commonMain/kotlin/gg/padkit/inputevents/InputEventsGenerator.kt#L27) and [InputHapticGenerator](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/8614b313641b936ceee5cff06a8f0a6771692fd6/lib/src/commonMain/kotlin/gg/padkit/haptics/InputHapticGenerator.kt#L31) independently compare states and allocate differences, unions, and lists. An analog movement can trigger this even when the host has disabled continuous-direction feedback.

Introduce a shared internal transition description or allocation-light category diffing. Preserve the state callback, event callback, then haptic order; press-before-release ordering; activation-versus-change policy; excluded typed IDs; independent versus coalesced effects; and muted-state tracking. Updating mode/intensity/adapter must not replay held input. The Android service adapter is already direct and uses application context; adding a thread, coroutine per effect, or forced asynchronous batching would add complexity and can alter behavior.

### P2: test and correct overlapping hit regions

The engine selects the first handler whose **rectangle** contains a pointer, then applies a circular eligibility check later. A rejected rectangular corner does not fall through to another control that has a valid circular hit. Registration order also determines overlap priority rather than drawn z-order.

An isolated engine probe reproduced this with first bounds `(0,0)-(100,100)`, second bounds `(0,0)-(50,50)`, pointer `(25,5)`: neither key activates; removing the first handler makes the second key activate. This confirms an engine behavior, not a demonstrated bug in a particular shipped console layout. Choose an explicit priority policy and apply the complete hit predicate before accepting a new owner. Keep capture unchanged. Cover compact layouts, circles, drag transfer, and simultaneous controls before changing hit behavior.

### P3: harden public geometry boundaries

`ControlFaceButtons` accepts empty anchor lists; `FaceButtonsPointerHandler` calls `minBy` and throws on the first pointer. A second isolated probe confirmed `NoSuchElementException`. Shipped layouts inspected supply nonempty anchors, so this is a robustness gap rather than an established user-facing crash. Define empty layouts as inert or reject them immediately with a useful validation message. Also reject non-finite/invalid radial sizes and guard zero-sized bounds when restructuring. Do not scatter expensive validation through every pointer sample.

### P3: adopt useful code standards without a rewrite

Keep inline typed IDs and immutable snapshots; the code already uses modern Kotlin features. Prefer explicit internal data types over `Any?` casts; specify main-thread confinement for mutation; document overlap/duplicate-ID policies; use ABI/API checks when public contracts change; and generate Compose compiler reports only for profiling. Use compiler reports to decide whether a stability annotation is needed rather than adding `@Stable`/`@Immutable` speculatively. Keep zero/unspecified direction distinctions and event delivery semantics documented and tested.

## Removal decisions for an Omnidroid-focused fork

1. **Remove the checked-in formatter binary and its duplicate hook path.** It is 68.78 MB, versus about 459 KB for every other tracked file combined. Use the existing Gradle ktlint task and a reproducible cached download. Removing it from the current tree does not erase old Git blobs; avoid rewriting published history just for this review. It does not affect APK size.
2. **Retire unused Maven publishing, signing, git-derived publication versioning, and wiki deployment from the default path** if this fork remains a pinned source dependency. Publication coordinates still identify upstream `io.github.swordfish90:padkit`, and POM links identify Swordfish90. Keep attribution; correct ownership before any future publishing. Dokka can remain an opt-in documentation task if useful.
3. **Remove iOS, Wasm, and desktop sample/platform targets from the Omnidroid-focused maintenance scope.** They are not compiled by the app today, so their removal saves build/configuration/CI obligations, not Android runtime work. Retain a small Android touch demo for device checks. If cross-platform distribution becomes an explicit product requirement, retain those targets as opt-in and test them rather than carrying an unverified promise.
4. **Remove unused sample-only packages and template assets with the removed samples.** Activity/AppCompat/Core KTX versions in the sample catalog are not dependencies of PadKit's Android integration. Keep Gradle wrapper/formatting/CI needed for reproducible fork development; make optional targets opt-in during transition.
5. **Narrow icons, then consider stock renderers separately.** Default cross arrangement is in use; most stock paint is replaceable. Extract only if it produces a useful dependency/API simplification. Because the host already uses Material 3 and extended icons, source deletion alone proves neither a smaller app nor less work per frame.
6. **Remove `toDegrees()` and redundant configuration only after call-site checks.** Consolidating two license files is low value and must preserve all notices; it is not an optimization priority. Keep the `gg.padkit` package name unless there is a concrete collision problem; mass renaming adds churn without improving performance.

The optional pre-commit hook also has maintenance defects: `if ! formatter; then exit $?` exits with the inverted command's successful status on failure; whitespace-separated `xargs` is unsafe for filenames with spaces; and restaging entire files can absorb unstaged changes. `setUpGitHooks` assumes `.git` is a directory, which is false in this submodule. Prefer a documented Gradle check and CI rather than preserving this extra formatter path.

## Functionality that must remain

- Buttons, multi-finger face clusters, composite adjacent-button regions, custom anchors, four/eight-way cross, relative analog sticks, double-tap stick click, and analog-looking discrete D-pad with smooth unquantized thumb movement.
- Pointer capture outside bounds, uncaptured button slide behavior, shared-key contributions, live remapping, final releases, cancellation reset, and ordered reentrant transitions.
- Tilt/programmatic overrides by typed control ID, empty simulation state, restoration of held touch when an override is removed, and matching simulated visuals. Simulation is active functionality, not demo-only code.
- All radial placement/scale settings, grid layout helpers, custom skins, opacity, and current console mappings. Keep console geometry, saved preferences, host actions, physical-controller routing, emulator motion conventions, stylus/touchscreen forwarding, and emulator rumble in Omnidroid.
- Haptic strength/mode updates, D-pad activation policy, exclusions for host actions, silent continuous sticks, no initialization while disabled, and platform capability fallback down to the existing API 24 app minimum.

No new turbo/macro engine, remapping UI, networking layer, or native input rewrite is justified by this optimization assessment.

## Implementation order and acceptance gates

1. **Establish the build gate.** Repair AAR metadata task wiring; check actual minimum dependency requirements; make `assembleRelease`, lint, existing regressions, and the app compile check pass. Modernize fork CI and preserve pin discipline. Port library Android tests into the fork's supported validation path. Publish fork commits before updating the parent pin.
2. **Remove maintenance weight.** Delete the formatter binary/hook machinery, unused publishing/wiki path, and unsupported platform/sample obligations. Narrow icon dependencies. Align the remaining Android build and documented Java/Kotlin/Gradle settings. Record tracked bytes and task/configuration time before/after; do not claim gameplay improvements from this phase.
3. **Benchmark and optimize input in small changes.** First move thumb placement, then no-op/state-merge fast paths, then routing scratch/index work, then shared event/haptic diffing. Replay the same input traces against the old and new engines. Keep behavior changes such as overlap priority in a separate commit with focused tests.
4. **Validate on devices and release artifacts.** Test multi-touch plus stylus, held inputs during hide/background/disposal, remapping and policy changes, tilt toggle, and live haptics. Check API 24 fallback and capable/non-capable vibration hardware. Confirm screenshots/coordinates for all 33 host profile IDs and both orientations where supported. Build a minified release variant and inspect dependencies/classes/resources for actual size effects.

Measure input-event processing p50/p95/p99 in microseconds, allocated bytes/event and GC counts, thumb recompositions/measurements per drag, frame-time percentiles and missed frames at 60/120 Hz, idle CPU/wakeups, and device energy/thermal behavior over a fixed workload. Replay realistic and stress combinations: button taps/slides, analog drags, diagonal transitions, two/five/ten pointers, tilt, simulation changes, and layout removal. Count raw pointer changes, logical transitions, callback delivery, and backend handoff separately; an engine benchmark is not touch-to-photon latency.

Compare identical release-like builds, device, refresh rate, thermal conditions, input trace, and repetitions. Choose quantitative improvement thresholds only after establishing the baseline; require no lost/reordered logical transitions, unchanged coordinates within agreed floating-point tolerance, and no supported-device or functional regressions. Omnidroid's current baseline-profile journeys cover startup/library rendering, so they do not establish PadKit gameplay performance; add a deterministic touch-control journey if profiling demonstrates value.

## Validation commands

From the Omnidroid repository root:

```powershell
.\gradlew.bat :omnidroid-padkit:testDebugUnitTest --rerun :omnidroid-touchinput:testDebugUnitTest --rerun --offline --no-daemon
.\gradlew.bat :omnidroid-padkit:assembleRelease :omnidroid-padkit:lintRelease :omnidroid-padkit:dependencies --configuration releaseRuntimeClasspath --offline --no-daemon --continue
.\gradlew.bat :omnidroid-app:compileFreeDynamicDebugKotlin --offline --no-daemon
```

The offline checks used existing local dependency caches. No physical-device benchmark, fresh dependency download resolution, clean whole-app/native build, remote CI inspection, or iOS/Wasm/desktop compilation is claimed. This deliverable is a review and implementation assessment; product source and build configuration were not changed.
