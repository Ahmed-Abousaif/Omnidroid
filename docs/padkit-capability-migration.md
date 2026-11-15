# PadKit capability migration

The migration builds on the input-lifecycle fixes. PadKit owns reusable control mechanics and feedback; Omnidroid supplies saved preferences, skins, console placement, and emulator routing.

## Haptics

`PadKit` now accepts `HapticFeedbackSettings` and an optional `HapticFeedback` output adapter. Settings include normalized intensity (0–1), discrete-direction activation/change policy, analog activation feedback, effect coalescing, and excluded typed control IDs. Mode and settings updates apply to subsequent transitions without replaying held presses. Muted transitions still update the tracker. Feedback follows merged logical input, including simulations.

Android uses adjustable 30 ms press / 15 ms release one-shots, with press/release amplitudes matching Omnidroid's previous implementation. It checks amplitude capability from API 26 and falls back to the device's default amplitude when unsupported. Older Android versions retain the duration-only fallback. Zero intensity and `NONE` do not initialize a platform adapter in `PadKit`. iOS scales the existing press/release strengths by the normalized request. Desktop and browser adapters remain silent.

Omnidroid passes its saved mode and shared vibration intensity to PadKit. Its policy preserves activation-only D-pad feedback, silent ordinary analog sticks, and independent effects for simultaneous controls. Preference changes refresh the live values. The app's `TouchHapticPlayer` and generic event-feedback bookkeeping are removed.

Screen-layout, hide-pad, and lid actions are excluded from automatic library feedback. Omnidroid still decides whether these actions actually ran and requests feedback through PadKit's Android adapter. Hardware/controller routing and emulator rumble remain app-owned.

Compatibility note: Android's default library effects now use adjustable one-shots rather than predefined click/tick effects. Nonzero strengths cannot be distinguished on hardware without amplitude control. This migration preserves Omnidroid's former timing and amplitude profile, with the API 26 capability check corrected.

## Analog-style D-pad

`ControlDirectionalAnalog` accepts an `Id.DiscreteDirection`, diagonal policy, and normalized dead zone (default 0.25). It emits discrete directions directly, with positive Y pointing up. Four-way ties favor the horizontal axis. A held policy/dead-zone change recomputes output without another pointer event or loss of capture.

Both analog control types share gesture/capture code and rendering. The directional variant keeps its unquantized touch position for smooth thumb movement. Simulations use the same discrete ID and override both output and visual position; removing the override restores the held touch. Remapping, cancellation, and disposal release the old contribution.

Omnidroid's cross wrapper selects the new control for its analog appearance. `AnalogDpadState`, its host-side conversion, and the screen-to-view-model policy relay are removed. Android motion-source mapping, dual D-pad/stick routing, and backend Y inversion stay in the host.

## Anchors

PadKit's typed `buildGridAnchors` builds regular, staggered, or slanted layouts using an origin and row/column vectors, with clockwise rotation around the control origin. It supports ragged rows and rejects non-finite geometry. Omnidroid's six-button helper now supplies console-specific spacing and IDs to this builder; labels, console assembly, and normalized preference conversion remain in Omnidroid. Anchor order and placement are preserved.

## Verification

Run the checked-in common engine/feedback/geometry tests, Android Compose and haptics tests, and host tilt tests through the pinned source wrapper:

```sh
./gradlew :omnidroid-padkit:testDebugUnitTest :omnidroid-touchinput:testDebugUnitTest
./gradlew :omnidroid-app:compileFreeDynamicDebugKotlin
```

Android feedback tests exercise API 25, 26, 28, and 35, including silent intensity, absent hardware, legacy/default-amplitude fallback, durations, and live amplitude changes. Compose coverage checks live settings/adapter changes, discrete-only output, held diagonal/dead-zone changes, smooth visuals, mapping changes, simulation, and cancellation/disposal. Shared tests cover feedback transition policy, filtering, batching, mute tracking, directional quantization, capture, and generic anchor geometry.

Local verification passed all 67 tests: 33 shared engine/feedback/geometry tests, 16 Compose tests, 13 Android haptics cases, and five host tilt tests. `:omnidroid-app:compileFreeDynamicDebugKotlin` also passed.

Physical vibration feel still needs an Android device smoke test. Windows verification does not compile the iOS target.
