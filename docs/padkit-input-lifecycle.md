# PadKit input lifecycle fixes

Implementation plan and acceptance criteria for the six input issues identified in the PadKit assessment. All six changes are implemented.

1. Keep one input contribution per registered handler. Rebuild the merged touch state when a handler leaves. Deliver transitions synchronously and in order, including reentrant changes and final releases during disposal. Cancelled pointer input resets capture and analog gesture data; disposing PadKit also releases simulated input.
2. Derive output from touch contributions plus the current simulated state and selected IDs. Removing an override restores the held touch contribution immediately. Disabled tilt emits an empty state, and changing tilt modes starts neutral before consuming sensor values.
3. Key handlers and derived visual state by the IDs/configuration they capture. Replacing a mapping releases its old contribution and creates fresh gesture state.
4. Apply the saved diagonal policy when converting an analog-style D-pad. Four-way mode chooses one dominant axis; eight-way mode retains the existing sectors. Recompute a held direction when that policy changes.
5. Remove the Android haptics thread/dispatcher. Vibration submission uses the platform service directly. Do not construct a platform haptics generator for `NONE`.
6. Add common engine tests, Android Compose tests under Robolectric, and host tests for analog-D-pad and tilt transitions. Run them in a CI job that initializes the public PadKit submodule and does not require private emulator source repositories.

## Regression coverage

- Independent fingers and shared digital keys remain active when another control is removed.
- Stick capture survives dragging outside bounds; cancellation releases and clears double-tap/drag state.
- Removing a held control and disposing an entire pad produce releases without another pointer event.
- Simulation ID-only changes, disabling simulation, and switching between digital/continuous/discrete overrides preserve correct ownership.
- Quick press/release and reentrant callback changes preserve event order.
- Dynamic button, stick, cross, face-button and composite mappings update both output and pressed visuals.
- Four-way/eight-way D-pads cover cardinal directions, diagonals, dead zone, invalid input, and policy changes while held.
- Disabled tilt and enabled-mode changes emit neutral state, cancel old readings, and stop sensor ownership.

## Verification

All 39 regression tests passed: 17 shared engine/event tests, 12 Android Compose tests under Robolectric, and 10 host D-pad/tilt tests. The Free Dynamic debug app's Kotlin compilation also passed.

The same 12 Compose tests were run against the original PadKit pin `90b3621`: 10 failed. All 12 pass against the fixed pin `13cfc0b`, confirming that the tests reproduce the original removal, disposal, mapping, override, and disabled-haptics issues.

```bash
./gradlew :omnidroid-padkit:testDebugUnitTest :omnidroid-touchinput:testDebugUnitTest
./gradlew :omnidroid-app:compileFreeDynamicDebugKotlin
```

The dedicated `padkit-tests.yml` workflow runs the regression suites with only the public PadKit submodule initialized. App compilation additionally needs the SDL Java sources from the pinned OmnidroidCores checkout; those sources were restored from the local cache for verification.

## Device checks

No Android device was connected at the start of this work. Robolectric interaction tests cover the Compose lifecycle, but hardware vibration and OS gesture cancellation still need a physical-device smoke test: hold a stick while hiding pads or backgrounding; switch tilt while holding a control; change diagonal policy while held; and repeat a multi-finger stick/button/stylus gesture.
