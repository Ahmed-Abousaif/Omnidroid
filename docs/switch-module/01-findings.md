# Phase 1 Investigation Findings: Nintendo Switch Emulation via Eden for Omnidroid

**Date:** 2026-11-01  
**Target Upstream:** [Eden](https://git.eden-emu.dev/eden-emu/eden.git) (pinned tag `v0.2.1`, commit `58c1e20ee58efa3900ba616207d460886214480b`)  
**Target Architecture:** `arm64-v8a`  
**Licensing:** Omnidroid (GNU GPLv3) | Eden (GPLv3-or-later) -> Fully Compatible  

---

## 1. Executive Summary

This investigation examines embedding the native emulation library of **Eden** (open-source Yuzu fork, C++, GPLv3-or-later) into **Omnidroid** as an optional module without using a libretro core.

### Key Conclusions:
1. **Upstream Eden is Pristine**: Upstream Eden builds a single shared library for Android (`libyuzu-android.so`) using standard modern CMake and NDK r28b (`28.2.13676358`, already present on the development machine). Because Eden's JNI layer expects helper classes under package `org.yuzu.yuzu_emu`, we can provide a small internal bridge in our Switch module under that package name and require **zero source code patches to Eden**.
2. **Omnidroid Already Has the Ideal Process Model**: Omnidroid runs games inside an isolated separate process: `android:process=":game"`. On exit, Omnidroid terminates this process (`finishAndExitProcess()`), completely purging native state, memory maps, and singletons. This eliminates the #1 risk of Switch emulators (memory leaks, static singleton pollution, and driver crashes).
3. **Module Architecture Follows Omnidroid Conventions**: Omnidroid packages emulators either as bundled libraries, dynamic feature modules, or downloaded `.so` files. For Switch, a dedicated module (`modules/switch` or dynamic feature `:omnidroid_core_switch`) containing `libyuzu-android.so` and a dedicated `SwitchGameActivity` (extending `BaseGameActivity` or conforming to its intent contract) cleanly isolates the Switch implementation.

---

## 2. Omnidroid Side Analysis

### 2.1 Project Structure & Build Environment
- **Build System**: Android Gradle Plugin (AGP) `9.0.0` with Gradle `9.1.0`.
- **Kotlin**: `2.2.10`, JVM Target `JVM_17`.
- **SDK Targets**:
  - `minSdkVersion`: 24
  - `targetSdkVersion`: 36
  - `compileSdkVersion`: 36
  - `buildToolsVersion`: 36.0.0
- **NDK**: NDK r26 / r28 / r29 present on system; `omnidroid-chd` builds with CMake and `c++_shared`.
- **ABIs**: Omnidroid supports `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. For Switch emulation, target is strictly `arm64-v8a`.

### 2.2 Core Discovery, Packaging, and Loading Mechanisms
Omnidroid uses a multi-tiered architecture depending on build flavor:
1. **Bundle Flavor (`bundle`)**:
   - Subproject `:bundled-cores` (`omnidroid-cores/bundled-cores/build.gradle.kts`) acts as an Android library.
   - It aggregates native `.so` files from `omnidroid-cores/omnidroid_core_*/src/main/jniLibs` directly into the final APK.
2. **Play Store Flavor (`play`) with Dynamic Delivery**:
   - Cores are defined as dynamic feature modules (`com.android.dynamic-feature`), such as `:omnidroid_core_cemu`, `:omnidroid_core_dolphin`, etc.
   - Packaged with `<dist:module>` for on-demand installation via Google Play Feature Delivery (`SplitInstallManager`).
3. **Free Flavor (`free`) / Fallback Download**:
   - `GithubCoreDownloader` retrieves missing cores from GitHub release assets into `context.filesDir`.
   - `CoreLibraryLocator.find(context, coreID)` checks:
     1. `findBundled`: searches `context.applicationInfo.nativeLibraryDir` (where APK and dynamic feature splits extract native libraries).
     2. `findDownloaded`: searches `context.filesDir`.

### 2.3 System Registration & Core Abstraction
- **`SystemID`** (`retrograde-app-shared/.../SystemID.kt`): Enum defining all supported platforms (`NES`, `SNES`, `NINTENDO_3DS`, `WII_U`, etc.). Adding Switch requires adding `SWITCH("switch")`.
- **`MetaSystemID`** (`MetaSystemID.kt`): Maps user-facing consoles to system IDs, resource titles, and icons.
- **`GameSystem`** (`GameSystem.kt`): Contains metadata per system:
  - Aspect ratio (e.g., 16:9 for Switch)
  - Supported file extensions (`xci`, `nsp`, `nso`, `nca`)
  - List of `SystemCoreConfig`: associated `CoreID`, controller configs, default settings, exposed UI settings.
- **`CoreID`** (`CoreID.kt`): Enum representing the core engine, displayName, `libretroFileName`, and optional `AssetsManager`.

### 2.4 Game Launch & Lifecycle Pipeline
- **Launcher**: `GameLauncher.launchGameAsync()` prepares the launch, verifies cloud saves, and calls `BaseGameActivity.launchGame(...)`.
- **Process Model**:
  - `GameActivity` and `GameMenuActivity` are configured with `android:process=":game"` in `AndroidManifest.xml`.
  - Emulation executes in this background worker process, completely isolated from `MainActivity`.
- **Game Loading Pipeline**:
  - `GameLoader.load(...)` emits `LoadingCore` -> `LoadingGame` -> `Ready(GameData)`.
  - `GameData` carries `Game`, resolved core library path, ROM files / content URIs, saves directory, system directory, and core variables.
- **Presentation Layer**:
  - Omnidroid has both `GLRetroGameViewWrapper` (OpenGL ES via `GLRetroView`) and `VulkanRetroView` (`SurfaceView` + `SurfaceHolder.Callback` driving a native Vulkan render loop).
- **Session Termination & Results**:
  - On exit or error, `BaseGameActivity` invokes `finishAndExitProcess()`.
  - Sets activity result `RESULT_OK` with `PLAY_GAME_RESULT_SESSION_DURATION` and `PLAY_GAME_RESULT_GAME` (or `RESULT_ERROR` with error string).
  - Tells `GameService` to request termination, finishing the activity and terminating the `:game` process.
  - `MainActivity.onActivityResult` receives the result, logs session duration, updates streaks, and enqueues cloud save sync.

---

## 3. Eden Side Analysis

### 3.1 Upstream Repository & Integrity
- **Canonical Git URL**: `https://git.eden-emu.dev/eden-emu/eden.git` (Verified active and accessible).
- **Official GitHub Mirror**: `https://github.com/eden-emulator/mirror.git` (Available as a fallback if self-hosted git is unreachable).
- **Latest Stable Tag**: `v0.2.1` (`58c1e20ee58efa3900ba616207d460886214480b`).

### 3.2 Native Architecture & JNI Layer
Eden's Android native bridge is located in `src/android/app/src/main/jni/`:
- **Core Session**: `EmulationSession` (`native.h`, `native.cpp`)
  - Central singleton (`EmulationSession::GetInstance()`).
  - Owns `Core::System m_system`, `EmuWindow_Android m_window`, `InputSubsystem`, and VFS.
  - Lifecycle methods:
    - `InitializeSystem(bool reload)`: Configures core subsystems and profiles.
    - `InitializeEmulation(filepath, program_index, frontend_initiated)`: Prepares the loader, parses metadata, configures virtual memory.
    - `RunEmulation()`: Enters the main execution loop (`m_system.Run()`) and waits on `m_cv` condition variable. Blocks the calling thread until emulation halts.
    - `PauseEmulation()` / `UnPauseEmulation()`: Pauses and resumes CPU/GPU execution.
    - `HaltEmulation()` / `ShutdownEmulation()`: Signals `m_is_running = false`, awakens `m_cv`, unbinds context, and cleans up rasterizer.
- **Surface & Window Management**:
  - `NativeLibrary.surfaceChanged(Surface surface)`: Converts Android `Surface` to `ANativeWindow` (`ANativeWindow_fromSurface`) and passes it to `EmuWindow_Android::OnSurfaceChanged`.
  - Sets `window_info.render_surface = reinterpret_cast<void*>(surface)`.
  - `NativeLibrary.surfaceDestroyed()`: Releases `ANativeWindow` and resets `render_surface = nullptr`.
  - `vk_present_manager.cpp` detects `VK_ERROR_SURFACE_LOST_KHR` during present and automatically calls `CreateSurface(instance, window_info)` to recreate the Vulkan surface and swapchain.

### 3.3 File Access, Keys, Firmware, and Configurations
- **File & ROM Access**:
  - Eden supports both POSIX paths and Android SAF Content URIs (`content://`).
  - If a path is a content URI, Eden's VFS calls JNI method `NativeLibrary.openContentUri(path, openmode)`.
  - Java opens the URI with `ContentResolver.openFileDescriptor(...)` and returns the file descriptor (`pfd.detachFd()`).
- **Keys & Firmware Storage**:
  - Managed by `Common::FS::PathManager` via `NativeLibrary.setAppDirectory(userPath)`.
  - Expected layout relative to `userPath`:
    - `userPath/keys/`: `prod.keys` and `title.keys`.
    - `userPath/nand/`: Switch NAND filesystem (firmware registered archives, system save data).
    - `userPath/nand/user/save/`: Game save data.
    - `userPath/load/`: Mods, game updates, and DLC.
    - `userPath/config/`: `config.ini` (emulator configuration).
    - `userPath/cache/`: Disk shader caches (`cache/shaders/<title_id>/...`).
- **Keys & Firmware APIs**:
  - `NativeLibrary.areKeysPresent()`: Returns boolean indicating whether decryption keys are available.
  - `NativeLibrary.reloadKeys()`: Re-scans `keys/` directory and parses keys into memory.
  - `NativeLibrary.isFirmwareAvailable()` / `firmwareVersion()`: Validates registered system archives in NAND.
- **Settings System**:
  - Managed by `AndroidConfig` (`android_config.cpp`), which reads/writes `config.ini`.
  - JNI functions: `NativeConfig.initializeGlobalConfig()`, `NativeLibrary.applySettings()`.

### 3.4 Vulkan Requirements & Custom Driver Injection
- **Custom Drivers (`libadrenotools`)**:
  - Eden integrates `libadrenotools` for Qualcomm Snapdragon Adreno GPUs.
  - `NativeLibrary.initializeGpuDriver(hookLibDir, customDriverDir, customDriverName, fileRedirectDir)`.
  - Calls `adrenotools_open_libvulkan(...)` to load custom Turnip/Mesa drivers before Vulkan instance creation.
  - Requires legacy JNI packaging in Gradle (`jniLibs.useLegacyPackaging = true`).
- **Vulkan Extensions & Features**:
  - Minimum Vulkan API: Vulkan 1.1 (1.2+ strongly recommended).
  - Mandatory Extensions:
    - `VK_EXT_vertex_attribute_divisor`
    - `VK_KHR_driver_properties`
    - `VK_KHR_sampler_mirror_clamp_to_edge`
    - `VK_KHR_shader_float_controls`
    - `VK_KHR_swapchain`
    - `VK_EXT_descriptor_indexing` (if API < 1.2)
  - Key Required Features:
    - Geometry shaders, tessellation, dual source blending, sample rate shading, multi-viewport, robust buffer access, variable pointers.
  - Note: Standard manufacturer Qualcomm Adreno drivers often fail these checks or have shader compiler bugs; loading custom Turnip drivers via `adrenotools` is standard practice for playable Switch emulation on Android.

### 3.5 Input & Rumble Architecture
- **Input Channels**:
  - Physical Gamepads: `NativeInput.onGamePadButtonEvent(...)`, `NativeInput.onGamePadAxisEvent(...)`.
  - Touch Screen: `NativeInput.onTouchPressed(...)`, `NativeInput.onTouchMoved(...)`, `NativeInput.onTouchReleased(...)`.
  - Virtual Controller / Overlay: `NativeInput.onOverlayButtonEvent(...)`, `NativeInput.onOverlayJoystickEvent(...)`.
  - Motion / Gyro: `NativeInput.onGamePadMotionEvent(...)` / `onDeviceMotionEvent(...)`.
- **Rumble / Haptics**:
  - Eden uses a bidirectional channel: Java registers input devices via `NativeInput.registerController(device: YuzuInputDevice)`.
  - Native C++ (`input_common/drivers/android.cpp`) runs an `Android_Vibration` thread that pops rumble requests and calls `YuzuInputDevice.vibrate(intensity)` on the Java object.

### 3.6 Native Build System & Dependencies
- **CMake Structure**: Root `CMakeLists.txt` builds subprojects (`common`, `core`, `video_core`, `audio_core`, `input_common`, `frontend_common`, and `yuzu-android`).
- **Dependencies Management (CPM)**:
  - Eden does not use git submodules for external libraries; it uses **CPM** (`cpmfile.json`).
  - At CMake configure time, CPM downloads dependencies from GitHub/web (`boost`, `fmt`, `zstd`, `ffmpeg`, `libadrenotools`, `openssl`, `opus`, `cubeb`, etc.) into `.cache/cpm`.
- **Target Library**:
  - Produces a single shared library: `libyuzu-android.so`.
  - Stripped size for `arm64-v8a`: approximately **30 MB - 45 MB**.
  - Compiler requirements: C++20, Clang (NDK r28b).

### 3.7 Separation: UI vs Native Code
- **Do NOT Reuse (Discard)**:
  - Eden's Android app activities: `MainActivity`, `SetupActivity`, `InstallableFragment`, etc.
  - Eden's game library, ROM list adapter, cover caching, and document tree scanners.
  - Eden's in-game navigation drawer / menu dialogs.
- **Reuse**:
  - The native C++ library (`libyuzu-android.so`).
  - The JNI callback contract (`org.yuzu.yuzu_emu.NativeLibrary`, `YuzuInputDevice`, `PlayerInput`).
  - The `adrenotools` driver injection pipeline.

### 3.8 Embedding Challenges & Blockers
1. **Hardcoded JNI Package**: In `common/android/id_cache.cpp`, all class names (`org/yuzu/yuzu_emu/NativeLibrary`, `DiskShaderCacheProgress`, `PlayerInput`, `YuzuInputDevice`, etc.) are hardcoded as string literals.
2. **Global Singletons**: `EmulationSession` and `Settings::values` are singletons. Re-running a game without exiting the process risks memory corruption and leaks.
3. **Blocking Emulation Loop**: `NativeLibrary.run(...)` blocks until the game finishes or is stopped; it must run on a dedicated background thread.

---

## 4. Integration Seams & Tradeoff Analysis

| Seam | Candidate Designs | Tradeoffs & Evaluation | Recommendation |
| :--- | :--- | :--- | :--- |
| **1. Process Isolation** | **A:** In-process (Main Omnidroid process)<br>**B:** Dedicated Process (`android:process=":game"` or `:switch`) | In-process sharing is fragile: Switch emulators allocate multi-gigabyte heaps, retain global native state, and can crash the GPU driver. Process `:game` guarantees total crash isolation and 100% memory reclaim on exit. Omnidroid already uses `:game` for `GameActivity`. | **Candidate B (Separate `:game` process)** |
| **2. Host Component** | **A:** Refactor `GameActivity` to support both Libretro and Eden<br>**B:** Dedicated `SwitchGameActivity` in Switch module | Refactoring `GameActivity` introduces unnecessary conditionals and couples LibretroDroid with Switch logic. A dedicated `SwitchGameActivity` extending `BaseGameActivity` keeps the Switch module self-contained and modular. | **Candidate B (`SwitchGameActivity`)** |
| **3. JNI Boundary & Package** | **A:** Patch Eden's C++ source to rename `org.yuzu.yuzu_emu` to `com.omnidroid.switch`<br>**B:** Keep Eden's C++ pristine; implement bridge stubs under `org.yuzu.yuzu_emu` in Switch module | Option A violates Hard Requirement #1 by creating dozens of patch lines in `id_cache.cpp` and `native.cpp` that will conflict on upstream updates. Option B keeps Eden 100% untouched and wraps it with a clean Omnidroid Kotlin API. | **Candidate B (Pristine Eden + internal bridge stubs)** |
| **4. Surface Delivery** | **A:** Custom TextureView / OpenGL Surface<br>**B:** Dedicated `SurfaceView` with `ANativeWindow` passed to `NativeLibrary.surfaceChanged` | TextureView adds copy overhead and latency. `SurfaceView` gives Eden direct swapchain presentation to hardware with zero copy overhead, matching Eden's upstream design. | **Candidate B (`SurfaceView`)** |
| **5. Packaging & Shipping** | **A:** Core-style dynamic feature module `:omnidroid_core_switch`<br>**B:** Standalone library module `:modules:switch`<br>**C:** Downloadable `.so` via `GithubCoreDownloader` | Dynamic feature modules require Google Play distribution infrastructure. A modular Gradle library `modules/switch` that can produce both a bundled flavor and dynamic feature conforms to Omnidroid conventions. | **Candidate A/B Hybrid (Gradle module `modules/switch` configurable as dynamic or bundled)** |
| **6. Game Handover & Exit** | **A:** Custom IPC / AIDL service<br>**B:** Standard Android Intent contract (`REQUEST_PLAY_GAME`, `EXTRA_GAME`, `RESULT_OK`) | Standard Intent matches Omnidroid's `GameLauncher` and `MainActivity.onActivityResult` pipeline, reusing existing streak tracking, XP awards, and save syncing without new IPC code. | **Candidate B (Standard Intent Contract)** |

---

## 5. Licensing & Legal Verification

1. **Omnidroid License**: GNU General Public License v3.0 (GPLv3).
2. **Eden License**: GNU General Public License v3.0 or later (GPLv3-or-later).
   - Compatibility: **100% Compatible**. Both permit linking and distribution under GPLv3.
3. **Eden Third-Party Dependencies**:
   - `adrenotools`: MIT / BSD-2-Clause
   - `boost`: Boost Software License 1.0
   - `fmt`: MIT
   - `zstd`: BSD / GPLv2
   - `ffmpeg`: LGPLv2.1+ / GPLv2+
   - `cubeb`: ISC
   - `opus`: BSD-3-Clause
   - `cpp-jwt`: MIT
   - All third-party licenses are permissive or copyleft-compatible with GPLv3.
4. **Third-Party Notices**: A `NOTICES-SWITCH.md` or entry in Omnidroid's licenses dialog will preserve all Eden and dependency copyrights.
5. **No Bundled Assets**: Zero proprietary Nintendo keys (`prod.keys`), firmware files, title certificates, or Switch ROMs will be bundled or committed.

---

## 6. Minimal Patch Set Recommendation

By placing our internal JNI bridge classes under the `org.yuzu.yuzu_emu` package inside `modules/switch`, **upstream Eden requires ZERO code patches**.

### Proposed Upstream Integration:
- **Submodule**: Pinned at `v0.2.1` (`58c1e20ee58efa3900ba616207d460886214480b`) at `modules/switch/upstream/eden`.
- **Patches in `modules/switch/patches/`**: **0 patches initially**.
  - If a patch becomes necessary (e.g. build fix for specific NDK header changes), it will be added as a numbered patch file (e.g. `0001-cmake-build-fixes.patch`) applied via `git apply`.

---

## 7. Open Risks & Mitigations

1. **CPM Dependency Download on First CMake Run**:
   - *Risk*: Eden's CMake uses CPM to download C++ dependencies over the network.
   - *Mitigation*: We will seek explicit user approval before triggering the first CMake configure step, and document cache persistence in `modules/switch/.cpm-cache`.
2. **Vulkan Driver Crashes on Adreno GPUs**:
   - *Risk*: Stock Qualcomm drivers frequently fault on complex Switch shaders.
   - *Mitigation*: Include `libadrenotools` driver selection in the Switch settings screen so users can supply custom Turnip driver archives (.zip).
3. **RAM and Out-Of-Memory (OOM)**:
   - *Risk*: Devices with < 6GB RAM may have the `:game` process killed by Android LMK.
   - *Mitigation*: Running in `:game` process ensures the frontend never crashes. A warning will be displayed if available RAM is low.
4. **Missing Decryption Keys**:
   - *Risk*: Users attempting to boot games without `prod.keys` will receive cryptic loader errors.
   - *Mitigation*: Before launching `SwitchGameActivity`, verify `areKeysPresent()`; if missing, present a clear, user-friendly prompt directing the user to import their legally dumped keys in settings.

---

## 8. Summary of What Was Verified & Phase 1 Conclusion

### Verified against code:
- Omnidroid's core packaging, dynamic feature setup, process isolation (`:game`), and `BaseGameActivity` launch pipeline.
- Upstream Eden's git repository responsiveness, tags, CMake configuration, and JNI entry points.
- `ANativeWindow` surface handover, surface destruction, and Vulkan swapchain recreation logic.
- Input injection (`onGamePadButtonEvent`, `onGamePadAxisEvent`, `onTouchPressed`) and rumble callback pipeline.
- Custom driver loading mechanism via `adrenotools`.
- CPM dependency management structure and NDK r28b compatibility.

Phase 1 is complete. No project code modifications have been made.
