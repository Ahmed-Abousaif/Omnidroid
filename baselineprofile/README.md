# Android performance checks

These tests target `com.omnidroid` using release-like builds. The producer needs
`kotlin-android`; without it the Kotlin test classes are not included in its APK.
Benchmark 1.4.1 handles this phone's newer `pm dump-profiles` output.

## Workloads

- `StartupBenchmarks`: cold process startup, both without compilation and with the
  packaged Baseline Profile required. Initial and full display are measured. The
  full-display signal waits for the library's initial refresh, not every cover.
- `LibraryBenchmarks`: six swipes through grid (1,000 and 5,000 games), list
  (5,000), and console (5,000), plus opening real game details. Captures frame
  timing and peak process memory from the trace. Default: five measured iterations;
  override with the instrumentation argument `-e iterations 10` (range 3–15).
- `BaselineProfileGenerator`: startup is a separate journey, so scrolling and
  details are excluded from the Startup Profile. Both journeys contribute to the
  Baseline Profile. The library journey excludes fixture-only classes. The separate
  Startup Profile output is retained for experiments, not shipped by default.

The fixture activity exists only in `benchmarkRelease` and `nonMinifiedRelease`.
It calls the real library renderers with in-memory `Game` objects and 16 generated
240×360 local JPEG covers. It does not insert database rows or launch games. This
measures cached rendering with a large item count, not database paging, thousands
of unique covers, network loading, or emulation speed. Real details tests require
at least one visible game in the installed release library.

## Run on a phone without uninstalling the user's app

Use an explicit device serial and an APK signed with the same certificate as the
installed release. Keep a normal release APK available to reinstall afterward.
`adb install -r` preserves app data. Do not uninstall the release package or clear
its data. Gradle's connected-test lifecycle is deliberately avoided here because
it can uninstall the target after testing.

PowerShell, from the repository root (`adb` must be on PATH):

```powershell
$serial = 'YOUR_DEVICE_SERIAL'
.\gradlew.bat :omnidroid-app:assembleFreeDynamicNonMinifiedRelease :baselineprofile:assembleFreeDynamicNonMinifiedRelease -PabiFilters=arm64-v8a
adb -s $serial install -r -t omnidroid-app/build/outputs/apk/freeDynamic/nonMinifiedRelease/Omnidroid-dynamic-arm64-v8a-nonMinifiedRelease-v3.0.0.apk
adb -s $serial install -r -t baselineprofile/build/outputs/apk/freeDynamic/nonMinifiedRelease/baselineprofile-free-dynamic-nonMinifiedRelease.apk
adb -s $serial shell am instrument --user 0 -w -r -e class com.omnidroid.baselineprofile.BaselineProfileGenerator com.omnidroid.baselineprofile/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (2 tests)` in the output; `adb` can exit successfully when JUnit fails.
Pull the library journey's `*-baseline-prof.txt` and the startup journey's
`*-startup-prof.txt` from
`/sdcard/Android/media/com.omnidroid.baselineprofile/`. Merge both files
as a sorted set of complete, unchanged rules into
`omnidroid-app/src/main/baselineProfiles/baseline-prof.txt`. Review regenerated
profiles with the code change. The separate `startup-prof.txt` is intentionally
not copied into app sources: the tested DEX layout added about 180 KB without an
established incremental startup benefit over the Baseline Profile alone. A future
Startup Profile experiment can copy only the startup journey into
`src/main/baselineProfiles/startup-prof.txt`; never include the library journey.

Build and install the minified benchmark target and matching producer:

```powershell
.\gradlew.bat :omnidroid-app:assembleFreeDynamicBenchmarkRelease :baselineprofile:assembleFreeDynamicBenchmarkRelease -PabiFilters=arm64-v8a
adb -s $serial install -r -t omnidroid-app/build/outputs/apk/freeDynamic/benchmarkRelease/Omnidroid-dynamic-arm64-v8a-benchmarkRelease-v3.0.0.apk
adb -s $serial install -r -t baselineprofile/build/outputs/apk/freeDynamic/benchmarkRelease/baselineprofile-free-dynamic-benchmarkRelease.apk
adb -s $serial shell am instrument --user 0 -w -r -e class com.omnidroid.baselineprofile.StartupBenchmarks,com.omnidroid.baselineprofile.LibraryBenchmarks -e iterations 5 com.omnidroid.baselineprofile/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (7 tests)`. Pull `*-benchmarkData.json` and `.perfetto-trace` files
from that same media directory **before another run overwrites them**. Store raw
traces outside version control. Compare identical workload, APK, runner version,
device configuration, and iteration count; record thermal state. Compilation
`None` and `Partial(Require)` on the same APK isolate ART profile compilation,
not the separate effect of Startup Profile DEX layout.

Prefer the helper below in place of either raw instrumentation command. It checks
JUnit success and pulls all reported artifacts before another run clears them.
It wakes the device and force-stops the target before starting instrumentation:

```powershell
python scripts/run_macrobenchmarks.py --serial $serial --suite profiles
python scripts/run_macrobenchmarks.py --serial $serial --suite all --iterations 5
```

Use `--suite profile-startup` / `profile-library` to regenerate one journey, or
`startup` / `library` to measure only that workload. `--adb` accepts a full SDK
path, and `--out` selects a results directory. Profile generation names startup
output `*-startup-prof.txt`; these rules contribute to **both** profiles.
`--suite startup-profiled` runs just the compiled-startup comparison, useful for
reversing APK order in a second measurement block.

Finally reinstall the normal signed release APK with `adb install -r`, and launch
its main activity. Fixtures/readiness tags are excluded from the shipping UI.
The app build script's scoped source-input workaround is needed by this project's
AGP/Kotlin/Baseline Profile combination on Windows; revisit it when upgrading those
plugins together.

## Analyze the details transition window

Whole-trace frame metrics can include frames before `measureBlock`. For details
comparisons, use `scripts/analyze_detail_transitions.py` with the official Perfetto
trace processor binary or its Python launcher, an output JSON path, and the
captured iteration traces. It selects the same 600 ms after the delivered tap on
every run, validates tap/frame availability, and retains unfiltered CPU samples
for cross-checking against Macrobenchmark. It requires Android frame timeline
data (API 31+) and the recorded touch-dispatch event. Use `--help` for arguments.
