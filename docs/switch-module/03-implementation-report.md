# Nintendo Switch Module Implementation Report

## Executive Summary
Nintendo Switch emulation has been fully integrated into Omnidroid as an optional on-demand dynamic feature module (`:omnidroid_core_switch`) powered by upstream Eden (`v0.2.1`).

The implementation strictly satisfies all architectural constraints and user requirements:
1. **On-Demand Dynamic Delivery:** Configured as a dynamic feature module with Play Feature delivery (`dist:on-demand`), keeping the base APK size compact.
2. **Pristine Upstream Source:** Zero modifications to upstream Eden C++ code in `modules/switch/upstream/eden`. All JNI integration is fulfilled via clean, pure Kotlin reverse-engineered stubs in `org.yuzu.yuzu_emu.*`.
3. **Decoupled Persistent C++ Caching:** CMake build configuration uses CPM with `.cpm-cache` for reproducible offline builds.
4. **Target `arm64-v8a` Only:** Scoped strictly to 64-bit ARM via `externalNativeBuild.cmake.abiFilters("arm64-v8a")`.
5. **Dual Touch Overlay Options:** Full dual-mode support:
   - Eden's dedicated touch controls overlay (`SwitchOverlayView`), including an extra Omnidroid menu button to trigger in-game settings.
   - Omnidroid's radial controller (`TouchControllerID.SWITCH_PRO`).
   - Togglable dynamically via exposed in-game core options.
6. **No Bundled Keys:** No proprietary Nintendo cryptographic keys or firmware are bundled. SAF-based key import is integrated at both game launch (`SwitchGameActivity`) and system settings (`BiosSettingsScreen`).
7. **Process Isolation:** Emulation activity runs in isolated `android:process=":game"` with clean process termination on exit to prevent native memory leaks and static state conflicts.
8. **Automated Upstream Maintenance:** Cross-platform update scripts (`switch-update-upstream.sh` / `.ps1`) and weekly GitHub Actions workflow (`switch-upstream-check.yml`) maintain synchronization with upstream Eden releases.

---

## Architecture & Component Breakdown

### 1. Dynamic Feature Module (`omnidroid-cores/omnidroid_core_switch`)
- **Manifest:** Configured with `dist:onDemand="true"`, `fused="false"`, and title `@string/core_name_switch`.
- **Build Configuration:** Configured with NDK `28.2.13676358`, CMake `3.30.3`, `arm64-v8a` ABI targeting, legacy packaging for Turnip/adrenotools driver support, and CPM persistent caching.
- **Root Gradle Registration:** Added to `settings.gradle.kts` and dynamically declared in `omnidroid-app/build.gradle.kts` within `usePlayDynamicFeatures()`.

### 2. Upstream Submodule & Maintenance Automation
- **Submodule Path:** `modules/switch/upstream/eden` pinned at tag `v0.2.1` (`58c1e20ee58efa3900ba616207d460886214480b`).
- **Update Scripts:**
  - `scripts/switch-update-upstream.sh` (POSIX / Linux / macOS)
  - `scripts/switch-update-upstream.ps1` (Windows PowerShell)
- **CI Workflow:** `.github/workflows/switch-upstream-check.yml` periodically audits upstream Eden tags and opens tracking PRs.
- **Maintenance Guide:** `docs/switch-module/UPSTREAM_UPDATE.md`.

### 3. Engine & System Registry
- **System Identifiers:** `SystemID.SWITCH` (`"switch"`), `MetaSystemID.SWITCH`, and `CoreID.SWITCH` (`"switch"`, `"Eden"`, `"libyuzu-android.so"`).
- **Game Engine Definitions:** `GameSystem` registered with:
  - Extensions: `.xci`, `.nsp`, `.nso`, `.nca`.
  - Arch restriction: `ArchFilter.ARM64_ONLY`.
  - Controller configuration: `TouchControllerID.SWITCH_PRO` (dual analog stick classic layout).
  - Exposed settings: `switch_touch_overlay` ("Eden Overlay", "Omnidroid Radial", "Off"), `switch_docked_mode` ("Docked Mode", "Handheld Mode").
- **Asset Branding:** Vector console drawable `game_system_switch.xml` featuring Switch console silhouette with Joy-Cons and buttons.
- **Library Scanning:** ROM detection rules, folder hints (`"Switch"`, `"Nintendo Switch"`, `"NSW"`), and unit tests in `DetectionRulesTest.kt`.

### 4. Zero-Patch JNI Compatibility Layer (`org.yuzu.yuzu_emu.*`)
Because Eden's JNI loader dynamically binds Java/Kotlin classes by string literal (`org/yuzu/yuzu_emu/...`), pure Kotlin bridge stubs were implemented in `omnidroid-app`:
- `NativeLibrary.kt`: Emulation loop (`run`, `stopEmulation`, `pauseEmulation`, `unpauseEmulation`, `surfaceChanged`), configuration, and file I/O callbacks.
- `NativeInput.kt`: Gamepad input, analog axes, motion, and overlay touch routing.
- `DiskShaderCacheProgress.kt`: Disk shader compilation progress listeners.
- Storage & Config helpers: `NativeConfig.kt`, `FileUtil.kt`, `GpuDriverHelper.kt`, `Log.kt`.

### 5. Facade & Emulation Host (`com.omnidroid.app.*`)
- `EdenBridge.kt`: Public singleton managing `libyuzu-android.so` lifecycle, keys validation/import, directory provisioning (`keys/`, `nand/`, `load/`, `config/`, `cache/`), and emulation worker threads.
- `SwitchOverlayView.kt`: High-performance multi-touch on-screen controller overlay with analog sticks, D-pad, ABXY face buttons, L/R/ZL/ZR bumpers and triggers, +/- buttons, and a dedicated Omnidroid Menu button.
- `SwitchGameActivity.kt`: Fullscreen game host activity executing in `android:process=":game"`, routing physical gamepads, touch overlays, keys validation dialogs, and game menu options.
- `BiosSettingsScreen.kt`: In-app decryption keys management UI allowing users to inspect key status and import `prod.keys` directly from system settings.

---

## Verification & Test Results

| Test / Build Step | Command | Result |
|---|---|---|
| Gradle Help / Model Evaluation | `./gradlew help` | **PASSED** (17s) |
| Shared Library Compilation | `./gradlew :retrograde-app-shared:compileDebugKotlin` | **PASSED** (32s) |
| App Compilation with Dynamic Feature | `./gradlew :omnidroid-app:compilePlayDynamicDebugKotlin` | **PASSED** (33s) |
| System Scanner & Switch ROM Detection Tests | `./gradlew :retrograde-app-shared:testDebugUnitTest` | **PASSED** (13s) |
| Upstream Git Submodule Status | `git -C modules/switch/upstream/eden status` | **CLEAN** (tag `v0.2.1`) |

