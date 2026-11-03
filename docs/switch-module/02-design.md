# Phase 2 Design: Nintendo Switch Emulation Module for Omnidroid

**Document:** `docs/switch-module/02-design.md`  
**Author:** Antigravity  
**Status:** Approved for Implementation (based on Phase 1 Findings & User Directives)  
**Target Upstream:** [Eden](https://git.eden-emu.dev/eden-emu/eden.git) (pinned tag `v0.2.1`)  
**Target ABI:** `arm64-v8a` only  

---

## 1. Overview & Architectural Goals

The goal is to add Nintendo Switch emulation to Omnidroid by embedding Eden's native C++ library (`libyuzu-android.so`) without a libretro core, while satisfying all hard requirements:

1. **Upstream Eden stays pristine**: Submoduled at `modules/switch/upstream/eden`. Zero code modifications to upstream C++ source; bridge package `org.yuzu.yuzu_emu` used internally in the wrapper to fulfill JNI lookups.
2. **On-Demand Dynamic Feature Module**: Built as `:omnidroid_core_switch` in `omnidroid-cores/omnidroid_core_switch` matching Cemu and Dolphin conventions, supporting Play Feature Delivery, APK bundling, and standalone download.
3. **Dedicated Process Isolation**: Emulation runs strictly in `android:process=":game"`, guaranteeing 100% memory reclamation on session termination and preventing GPU driver or JIT crashes from impacting Omnidroid's library.
4. **Dual Overlay Support**: Supports both Eden's touch overlay (default, enhanced with a dedicated Omnidroid Menu button) and Omnidroid's radial touch controller, toggled via in-game settings.
5. **Strict Copyright Compliance**: No bundled Nintendo keys or firmware. User imports their own `prod.keys` and firmware via Omnidroid's settings UI.

---

## 2. Module Layout & Project Structure

The Switch implementation is organized across two cooperating layers: the core delivery module and the frontend host bridge.

```
Omnidroid/
├── modules/switch/
│   ├── upstream/eden/              # Pristine Git submodule (git.eden-emu.dev/eden-emu/eden.git)
│   ├── patches/                    # Series of git patches (currently 0 patches)
│   ├── .cpm-cache/                 # Persistent local cache for CPM-fetched C++ dependencies
│   └── CMakeLists.txt              # CMake wrapper invoking upstream Eden's build
│
├── omnidroid-cores/
│   └── omnidroid_core_switch/      # Dynamic Feature Module (:omnidroid_core_switch)
│       ├── build.gradle.kts        # com.android.dynamic-feature, NDK r28b, CMake integration
│       └── src/main/
│           ├── AndroidManifest.xml # <dist:module>, <dist:delivery>, on-demand config
│           ├── jniLibs/arm64-v8a/  # Built libyuzu-android.so (and libc++_shared.so)
│           └── assets/libretro-db/ # Metadata slice & switch database
│
├── omnidroid-app/src/main/java/com/omnidroid/app/
│   └── shared/switch/              # Switch Host & UI Layer (in :game process)
│       ├── SwitchGameActivity.kt   # Extends BaseGameActivity, hosts SurfaceView
│       ├── SwitchGameViewModel.kt  # Lifecycle observer & state management
│       ├── bridge/
│       │   ├── EdenBridge.kt       # Clean public Kotlin API wrapping NativeLibrary
│       │   └── org/yuzu/yuzu_emu/  # Internal JNI bridge stubs matching id_cache.cpp
│       │       ├── NativeLibrary.kt
│       │       ├── DiskShaderCacheProgress.kt
│       │       └── features/input/
│       │           ├── NativeInput.kt
│       │           └── YuzuInputDevice.kt
│       ├── overlay/
│       │   ├── EdenTouchOverlay.kt # Eden touch overlay + Omnidroid Menu button
│       │   └── SwitchTouchMode.kt  # Enum: EDEN_OVERLAY, RADIAL_OVERLAY, DISABLED
│       └── settings/
│           ├── SwitchSettingsManager.kt
│           └── SwitchKeysImportHelper.kt
│
├── scripts/
│   ├── switch-update-upstream.sh   # Upstream update & patch test script (POSIX)
│   └── switch-update-upstream.ps1  # Upstream update & patch test script (PowerShell)
└── docs/switch-module/
    ├── 01-findings.md
    └── 02-design.md
```

---

## 3. Build System & Packaging Integration

### 3.1 Dynamic Feature Module Configuration
File: `omnidroid-cores/omnidroid_core_switch/build.gradle.kts`:
- Plugin: `id("com.android.dynamic-feature")`
- Target ABI: `arm64-v8a`
- NDK: `28.2.13676358`
- CMake arguments:
  ```kotlin
  externalNativeBuild {
      cmake {
          path = file("../../modules/switch/CMakeLists.txt")
          version = "3.30.3"
          arguments.addAll(
              listOf(
                  "-DENABLE_QT=0",
                  "-DENABLE_WEB_SERVICE=0",
                  "-DANDROID_ARM_NEON=true",
                  "-DYUZU_USE_CPM=ON",
                  "-DCPMUTIL_FORCE_BUNDLED=ON",
                  "-DYUZU_USE_BUNDLED_FFMPEG=ON",
                  "-DCPM_SOURCE_CACHE=${rootDir}/modules/switch/.cpm-cache",
                  "-DBUILD_TESTING=OFF",
                  "-DYUZU_TESTS=OFF",
                  "-DDYNARMIC_TESTS=OFF",
                  "-DCMAKE_BUILD_TYPE=Release"
              )
          )
          abiFilters("arm64-v8a")
      }
  }
  ```
- Packaging: `jniLibs.useLegacyPackaging = true` (mandatory for `adrenotools` custom driver dlopen).

### 3.2 Dynamic Delivery Manifest
File: `omnidroid-cores/omnidroid_core_switch/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:dist="http://schemas.android.com/apk/distribution"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:hasCode="false"
        android:extractNativeLibs="true" />

    <dist:module dist:title="@string/core_name_switch">
        <dist:delivery>
            <dist:on-demand />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
</manifest>
```

### 3.3 System Registration & Discovery
1. **`settings.gradle.kts`**:
   Add `:omnidroid_core_switch` to `usePlayDynamicFeatures()` block.
2. **`omnidroid-app/build.gradle.kts`**:
   Add `:omnidroid_core_switch` to `dynamicFeatures` set.
3. **`CoreID.kt`**:
   Add `SWITCH("switch", "Eden", "libyuzu-android.so")`.
4. **`SystemID.kt`**:
   Add `SWITCH("switch")`.
5. **`GameSystem.kt`**:
   Define `GameSystem(SystemID.SWITCH, "Nintendo - Switch", R.string.game_system_title_switch, ...)`:
   - Aspect ratio: `16f / 9f`
   - Extensions: `listOf("xci", "nsp", "nso", "nca")`
   - Exposed settings: Docked mode, CPU accuracy, GPU driver, VSync mode, Touch overlay style.
6. **Core Download & Fallback**:
   - Play flavor: Downloaded via `SplitInstallManager` on demand.
   - Bundle flavor: Native `.so` fused into APK by `bundled-cores`.
   - Free flavor: Fallback download via `GithubCoreDownloader` into `filesDir`.

---

## 4. JNI Wrapper & Bridge Architecture

To satisfy upstream Eden's `id_cache.cpp` lookups with zero upstream source modifications, internal Kotlin classes will implement the exact class signatures expected by JNI under package `org.yuzu.yuzu_emu`.

```
Omnidroid UI & Launcher
          │
          ▼
┌──────────────────────────────────────────────┐
│        SwitchGameActivity (:game)            │
│  - Hosts SurfaceView                         │
│  - Receives Intents & Returns Result         │
│  - Owns Input & Audio Lifecycle              │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│            EdenBridge (Kotlin)               │
│  - High-level, stable facade                 │
│  - User directory & keys configuration       │
│  - Driver injection setup (AdrenoTools)      │
│  - Threading & state synchronization         │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│     org.yuzu.yuzu_emu.NativeLibrary (JNI)    │
│  - surfaceChanged(Surface)                   │
│  - surfaceDestroyed()                        │
│  - run(filePath, 0, true)                    │
│  - pauseEmulation() / unpauseEmulation()     │
│  - stopEmulation()                           │
│  - onGamePadButtonEvent / onTouchPressed     │
└──────────────────────┬───────────────────────┘
                       │ JNI
                       ▼
┌──────────────────────────────────────────────┐
│      Pristine Eden C++ (libyuzu-android.so)   │
│  - EmulationSession / Core::System           │
│  - Dynarmic JIT (ARM64)                      │
│  - VideoCore (Vulkan Swapchain / Turnip)     │
│  - AudioCore (Cubeb / AAudio)                │
└──────────────────────────────────────────────┘
```

### 4.1 Narrow Facade API (`EdenBridge`)
The wrapper exposes a minimal, clean Kotlin interface so Omnidroid code never directly references `org.yuzu.yuzu_emu`:

```kotlin
interface SwitchEmulationBridge {
    fun initializeEnvironment(userDir: File, keysDir: File)
    fun loadCustomDriver(hookDir: String?, driverDir: String?, driverName: String?)
    fun areKeysInstalled(): Boolean
    fun installKeys(sourceUri: Uri): Boolean
    fun setSurface(surface: Surface)
    fun clearSurface()
    fun startEmulation(gamePath: String, onStarted: () -> Unit, onStopped: (Int) -> Unit)
    fun pauseEmulation()
    fun resumeEmulation()
    fun stopEmulation()
    fun sendKeyEvent(keyCode: Int, isDown: Boolean): Boolean
    fun sendMotionEvent(event: MotionEvent): Boolean
    fun sendTouchEvent(action: Int, pointerId: Int, x: Float, y: Float)
}
```

---

## 5. Subsystems Data Flow

### 5.1 Video Pipeline & Surface Handling
1. **Surface Creation**:
   - `SwitchGameActivity` contains a hardware-accelerated `SurfaceView`.
   - `SurfaceHolder.Callback.surfaceCreated(holder)`: Sets frame rate hint (60Hz / 120Hz) on Android 11+.
   - `surfaceChanged(holder, format, width, height)`:
     - Passes `holder.surface` to `NativeLibrary.surfaceChanged(surface)`.
     - C++ layer invokes `ANativeWindow_fromSurface(env, surface)` and calls `EmuWindow_Android::OnSurfaceChanged(native_window)`.
     - Vulkan presenter allocates or resizes the `VkSwapchainKHR` to match native window dimensions.
2. **Surface Loss & Restoration**:
   - `surfaceDestroyed(holder)`:
     - Calls `NativeLibrary.surfaceDestroyed()`.
     - Releases `ANativeWindow`, marks `render_surface = nullptr`.
     - Pauses emulation loop to prevent swapchain presentation on a destroyed surface.
   - When user returns to activity: `surfaceChanged` delivers the fresh `Surface`; Vulkan presenter catches `VK_ERROR_SURFACE_LOST_KHR` and recreates the swapchain transparently.

### 5.2 Audio Pipeline
- Eden utilizes `cubeb` with AAudio backend on modern Android (falling back to OpenSL ES).
- Audio runs asynchronously inside Eden's native audio thread pool (`audio_core`).
- Audio volume and mute toggles are mapped to `Settings::values.volume` via `NativeConfig`.

### 5.3 Input Pipeline & Dual Touch Overlay

```
                    User Input
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
    [Touch Screen]             [Physical Gamepad]
         │                             │
    Overlay Check                      │
    ┌────┴────┐                        │
    ▼         ▼                        ▼
[Eden]    [Radial]            onKeyDown / onGenericMotionEvent
    │         │                        │
    └────┬────┘                        │
         ▼                             ▼
  EdenBridge API               EdenBridge API
         │                             │
         ▼                             ▼
 NativeInput.onTouch*        NativeInput.onGamePad*
         │                             │
         └──────────────┬──────────────┘
                        │
                        ▼
            Eden HID Subsystem (Core)
```

1. **Eden Overlay (Default)**:
   - Renders Nintendo Switch layout (+, -, Home, Capture, A/B/X/Y, D-Pad, L/R/ZL/ZR, Dual Analog Sticks).
   - **Extra Omnidroid Menu Button**: An overlay button with Omnidroid's icon located at top-center/top-left. Pressing it pauses emulation and triggers Omnidroid's `GameMenuActivity` options dialog.
2. **Radial Overlay (Omnidroid Native)**:
   - Uses Omnidroid's [`omnidroid-touchinput`](file:///d:/Work/Omnidroid/omnidroid-touchinput) module with `TouchControllerID.SWITCH_PRO`.
   - Touch events dispatch to `EdenBridge.sendKeyEvent` and `sendAxisEvent`.
3. **Physical Controller**:
   - Events in `SwitchGameActivity.onKeyDown`, `onKeyUp`, and `onGenericMotionEvent` forward directly to `NativeInput.onGamePadButtonEvent` and `NativeInput.onGamePadAxisEvent`.
4. **Haptics / Rumble**:
   - Register `YuzuInputDevice` with `NativeInput.registerController()`.
   - Native `vibration_thread` calls `YuzuInputDevice.vibrate(intensity)`, routing to Android's `Vibrator` / `VibratorManager`.

### 5.4 Saves & Configuration
- **Root Storage**: `context.getExternalFilesDir(null)/switch/` (or `context.filesDir/switch/`).
- **Save Location**:
  `switch/nand/user/save/0000000000000000/<user_id>/<title_id>/`.
- **Save Synchronization**:
  - `SwitchGameActivity` integrates with `SavesManager` and `SaveSyncWork`.
  - On activity finish, `SaveSyncWork.enqueueGameWork(context, game.fileName)` syncs Switch saves with Google Drive / WebDAV if cloud sync is enabled.

---

## 6. Lifecycle & Process Management

`SwitchGameActivity` is declared in `AndroidManifest.xml` with `android:process=":game"`:

```xml
<activity
    android:name="com.omnidroid.app.shared.switch.SwitchGameActivity"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|keyboard|navigation|smallestScreenSize"
    android:process=":game"
    android:screenOrientation="sensorLandscape"
    android:theme="@style/OmnidroidMaterialTheme.Game" />
```

### State Machine:
1. **Launch**:
   - `GameLauncher.launchGameAsync()` starts `SwitchGameActivity` with `EXTRA_GAME` and `REQUEST_PLAY_GAME`.
   - Checks `areKeysInstalled()`. If missing, shows import dialog and halts.
   - Configures AdrenoTools driver if customized in settings.
   - Initializes user directory (`setAppDirectory`) and system (`initializeSystem`).
2. **Playing**:
   - `NativeLibrary.surfaceChanged(surface)` prepares window.
   - Background thread `NativeEmulation` calls `NativeLibrary.run(gamePath, 0, true)`.
3. **Pausing (Home button / App switch / Menu)**:
   - `NativeLibrary.pauseEmulation()`.
4. **Resuming**:
   - `NativeLibrary.unpauseEmulation()`.
5. **Exit**:
   - `NativeLibrary.stopEmulation()`.
   - Waits for `NativeEmulation` thread to join.
   - Packages result `Intent` (`PLAY_GAME_RESULT_SESSION_DURATION`, `PLAY_GAME_RESULT_GAME`).
   - Calls `finishAndExitProcess()`:
     - `setResult(RESULT_OK, resultIntent)`.
     - `finish()`.
     - Terminates `:game` process, purging all native memory, singletons, and GPU context.
   - `MainActivity.onActivityResult` receives the result, updating play streaks and cloud saves.

---

## 7. Exposed Settings in Omnidroid UI

Switch-specific options will be exposed in Omnidroid's game details and in-game pause menu:

| Setting Key | Display Name | Options | Default |
| :--- | :--- | :--- | :--- |
| `switch_docked_mode` | Console Mode | Handheld, Docked | Handheld |
| `switch_cpu_accuracy` | CPU Accuracy | Auto, Accurate, Unsafe | Auto |
| `switch_graphics_api` | Graphics API | Vulkan | Vulkan |
| `switch_custom_driver` | GPU Driver | System Default, Custom (AdrenoTools ZIP) | System Default |
| `switch_resolution_scale`| Resolution Scale | 0.5x, 0.75x, 1.0x (720p/1080p), 1.5x | 1.0x |
| `switch_vsync_mode` | VSync Mode | FIFO (V-Sync On), Mailbox (Unlocked), Immediate | FIFO |
| `switch_touch_overlay` | Touch Controls | Eden Overlay, Radial Overlay, Off | Eden Overlay |
| `switch_keys_management`| Manage Keys/Firmware | Import `prod.keys`, Import Firmware ZIP | - |

---

## 8. Upstream Update Strategy

### 8.1 Workflow
Upstream Eden stays a pristine Git submodule pinned to specific release tags.
- Update script: `scripts/switch-update-upstream.sh` (and `.ps1`).
  1. Checks out requested ref (e.g., `git checkout v0.2.2`).
  2. Runs `git apply --check modules/switch/patches/*.patch`.
  3. If patch conflicts occur, reports the offending patch and aborts before building.
  4. Runs `./gradlew :omnidroid_core_switch:assembleDebug`.
- Documentation in `docs/switch-module/UPSTREAM_UPDATE.md` will provide a step-by-step conflict resolution guide.

### 8.2 CI Verification
A dedicated GitHub Actions workflow (`.github/workflows/switch-upstream-check.yml`) runs on a weekly schedule:
1. Clones repo with `--recurse-submodules`.
2. Tests if latest upstream Eden release tag compiles with existing patches.
3. Opens an automated issue/notification if a patch conflict or compilation break is detected.

---

## 9. Implementation Phases (Roadmap for Phase 3)

1. **Step 1: Submodule & Native Build Pipeline**:
   - Add Eden submodule at `modules/switch/upstream/eden` pinned at `v0.2.1`.
   - Setup `modules/switch/CMakeLists.txt` and `.cpm-cache`.
   - Create `:omnidroid_core_switch` dynamic feature module with Gradle build producing `libyuzu-android.so`.
2. **Step 2: JNI Bridge & Debug Surface Boot**:
   - Implement `org.yuzu.yuzu_emu` internal bridge stubs and `EdenBridge.kt`.
   - Create basic `SwitchGameActivity` that initializes the engine on a `SurfaceView` with homebrew/test ROM.
3. **Step 3: Dual Overlay Input, Audio & Lifecycle**:
   - Implement Eden's touch overlay with the dedicated Omnidroid Menu button.
   - Implement Omnidroid radial touch controller toggle.
   - Wire gamepad buttons, sticks, sensors/gyro, and haptic rumble.
   - Implement pause/resume, surface lost/recreated, and clean exit.
4. **Step 4: Library Integration & Keys/Firmware Management**:
   - Register `SystemID.SWITCH` and `CoreID.SWITCH` in Omnidroid library.
   - Implement `prod.keys` and firmware import UI in settings.
   - Hook `GameLauncher` to launch `SwitchGameActivity`.
5. **Step 5: Upstream Update Automation & CI**:
   - Create `scripts/switch-update-upstream.sh` and `switch-update-upstream.ps1`.
   - Create `.github/workflows/switch-upstream-check.yml`.

