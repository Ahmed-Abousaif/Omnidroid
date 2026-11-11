# Upstream Eden Update Guide

Omnidroid builds the Switch native backend from Eden source. Updates follow the latest commit on `master`, not a release APK. Each update resolves `master` once and checks out that exact SHA, so the packaged native libraries have a reproducible source pin.

- Source: `modules/switch/upstream/eden` (`https://git.eden-emu.dev/eden-emu/eden.git`).
- Current pin: `cf654052c416f9e7c80cef0a7d321476b1c52f99` (2026-11-10).
- Builder: `omnidroid-cores/build_eden.py`.
- Output: `omnidroid-cores/omnidroid_core_switch/src/main/jniLibs/arm64-v8a/`.
- Provenance: `omnidroid-cores/omnidroid_core_switch/src/main/eden-build.json` records the source SHA, NDK, and SHA-256 of each library.

## Toolchain

Install Git, Python 3, the Android SDK, and a host `glslangValidator` (Vulkan SDK on Windows, `glslang-tools` on Linux) available on `PATH`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`). The builder reads the NDK and CMake versions from the selected Eden commit's Android Gradle configuration. The current pin requires:

```sh
sdkmanager "ndk;30.0.16248370" "cmake;3.31.6"
```

CMake uses the NDK toolchain and Ninja to build the Android native targets in Release mode. Building the standalone Eden APK, signing it, and extracting release assets are unnecessary. Eden's CPM dependency downloads require network access. Build trees are isolated by commit and NDK under `omnidroid-cores/.cache/eden/`.

## Update and build

From the Omnidroid root, on Windows, Linux, or macOS:

```sh
# Fetch latest master, detach at its SHA, build, and package the Switch core.
python omnidroid-cores/update_cores.py --cores switch

# Build the current source pin without fetching or changing the checkout.
python omnidroid-cores/update_cores.py --cores switch --eden-ref pinned

# Build an explicit upstream branch, tag, or SHA.
python omnidroid-cores/update_cores.py --cores switch --eden-ref <ref>

# Limit compiler memory use (default: two compiler jobs).
python omnidroid-cores/update_cores.py --cores switch --eden-jobs 2
```

To update only the source pin, use `scripts/switch-update-upstream.sh [ref]` or `scripts/switch-update-upstream.ps1 -TargetRef <ref>`. Both default to `master`; neither stages changes automatically. `-TargetTag` remains a PowerShell alias. Always rebuild after changing the pin.

The four hook libraries are also refreshed in `omnidroid-app/src/main/jniLibs/arm64-v8a/`, so the base APK and Switch module use the same build. The builder requires a clean Eden checkout and never patches upstream. It compiles `libyuzu-android.so` and all four Adrenotools hooks, links the matching bundled SDL3 statically, and checks that every library is an AArch64 ELF shared object before replacing packaged files. A failed fetch, configure, build, or packaging step exits nonzero; old release APKs are never used as a fallback. Other ABIs retain their zero-byte bundletool placeholders.

SDL's matching Java sources are copied into the core's `src/main/sdl/java/` directory and compiled into the base app through its source set. Keep these files with the native update. `NativeLibrary.load()` sets up SDL JNI and calls Eden's explicit `initJvm()` entry point before use. R8 preserves both JNI packages.

## Verify and record an update

```sh
python -m unittest discover -s omnidroid-cores -p 'test_*.py'
python omnidroid-cores/update_cores.py --verify
./gradlew :omnidroid-app:compilePlayDynamicDebugKotlin
```

Review changes to Eden's JNI contracts, especially `src/common/android/id_cache.cpp`, Android applets, filesystem callbacks, and native entry points. Add any required bridge changes under `omnidroid-app/src/main/java/org/yuzu/yuzu_emu/`. Validate loading and game startup on an ARM64 device before shipping; Kotlin compilation cannot validate JNI at runtime.

The cores and Eden source directories are separate submodules. Record builder, binary, metadata, and SDL Java changes in the OmnidroidCores repository, then record its gitlink and Eden's source gitlink in Omnidroid. Leave Eden's upstream source pristine. Do not commit Nintendo keys, firmware, or games.

## CI

- `cores-update.yml` resolves latest `master`, installs its toolchain, builds its native libraries, verifies core health and Kotlin compilation, and includes the source pin in the weekly update. The `eden-native` artifact contains native libraries, SDL Java sources, and build metadata. Binary changes inside the cores submodule still need to be recorded in that repository.
- `switch-upstream-check.yml` checks `master` weekly, compiles changed source pins, uploads `eden-native`, and verifies Kotlin compilation.
- `checkstyle.yml` and `publish.yml` rebuild the checked-in source pin before app assembly. Release packaging therefore uses libraries from the pinned source even if the prebuilt core binaries have not been refreshed.
- `.github/actions/setup-eden` reads Eden's toolchain versions, rather than relying on the app's older NDK installation.
