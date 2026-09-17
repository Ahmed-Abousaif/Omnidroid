# Add PlayStation 2 Core (PCEE2) to Omnidroid

Add PS2 system support using the **PCEE2** libretro core (PCSX2-based, from [WizzardSK/pcee2-libretro](https://github.com/WizzardSK/pcee2-libretro.git)), following the exact same patterns used by existing cores.

## Key Decisions (from your input)

- ✅ **arm64-v8a only** — restricted via `supportedOnlyArchitectures`
- ✅ **Marked as Beta** — title shows "PlayStation 2 (Beta)"
- ✅ **Dedicated PS2 DualShock 2 touch layout** — new `PS2Left`/`PS2Right` composables
- ✅ **BIOS required** — PCEE2 requires a legally dumped PS2 BIOS (e.g. `scph39001.bin`)
- ✅ **Save states enabled** — PCEE2 supports libretro save states (core-version-specific)
- ✅ **Rumble supported** — DualShock 2 rumble mapped through libretro
- ✅ **Vulkan by default** — with OpenGL/software fallback paths

## User Review Required

> [!WARNING]
> **BIOS handling:** PCEE2 expects the BIOS at `system/pcsx2/bios/`. Omnidroid's `BiosManager` checks for files directly in `system/`. We register common PS2 BIOS filenames so the scanner can detect and copy them, but the PCEE2 core itself may look in `system/pcsx2/bios/`. This may require runtime symlinking or the user manually placing the BIOS in the right subdirectory. Alternatively, PCEE2 may also search the flat `system/` directory — we should verify at runtime.

## Open Questions

1. **BIOS subdirectory:** Should we add logic to the `BiosManager` or a custom assets manager to create the `system/pcsx2/bios/` subdirectory and copy/symlink BIOS files there? Or do we expect users to manually place the BIOS? The simplest approach for now: register the BIOS filenames in `BiosManager` for scan/detection, and document that users should place BIOS in `system/pcsx2/bios/`.: we should do the best we can, which is the first option.

2. **Extensions list:** The Lemuroid fork registers `iso, chd, cue, m3u, cso, zso, gz, bin, mdf, nrg, elf, irx`. Several overlap with PSX/PSP. Should we include all of these, or stick to the most common: `iso, chd, cue, m3u, cso, bin, mdf`? I recommend the full list since path-based scanning disambiguates. go for the full list and we'll use path-based disambiguation, and if that fails, we'll ask the user.

---

## Proposed Changes

### 1. Core Module — Dynamic Feature Module

#### [NEW] `omnidroid-cores/omnidroid_core_pcee2/build.gradle.kts`

```kotlin

plugins {
    id("com.android.dynamic-feature")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "com.omnidroid.core.pcee2"
    defaultConfig {
        missingDimensionStrategy("opensource", "play")
        missingDimensionStrategy("cores", "dynamic")
    }
    packagingOptions {
        doNotStrip("*/*/*_libretro_android.so")
    }
}

dependencies {
    implementation(project(":omnidroid-app"))
    implementation(kotlin(deps.libs.kotlin.stdlib))
}
```

#### [NEW] `omnidroid-cores/omnidroid_core_pcee2/src/main/AndroidManifest.xml`

```xml

<manifest xmlns:dist="http://schemas.android.com/apk/distribution"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:hasCode="false"
        android:extractNativeLibs="true" />

    <dist:module dist:title="@string/core_name_pcee2">
        <dist:delivery>

<dist:on-demand />
<dist:install-time>
    <dist:conditions>
        <dist:device-feature dist:name="android.software.leanback"/>
    </dist:conditions>
</dist:install-time>

        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
</manifest>
```

#### [NEW] `omnidroid-cores/omnidroid_core_pcee2/src/main/jniLibs/arm64-v8a/libpcee2_libretro_android.so`

The compiled PCEE2 libretro core — must be built from source (see build section below).

---

### 2. Gradle Registration

#### [MODIFY] [`settings.gradle.kts`](file:///e:/Current%20Work/Omnidroid/settings.gradle.kts)

Add `:omnidroid_core_pcee2` to the dynamic feature includes and set its project directory.

```diff
     include(
         ":omnidroid_core_desmume",
         ...
-        ":omnidroid_core_citra"
+        ":omnidroid_core_citra",
+        ":omnidroid_core_pcee2"
     )
     ...
     project(":omnidroid_core_citra").projectDir = File("omnidroid-cores/omnidroid_core_citra")
+    project(":omnidroid_core_pcee2").projectDir = File("omnidroid-cores/omnidroid_core_pcee2")
```

---

### 3. Core Registration

#### [MODIFY] [`CoreID.kt`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/CoreID.kt)

Add `PCEE2` enum entry after `DOSBOX_PURE`:

```diff
     DOSBOX_PURE(
         "dosbox_pure",
         "DosBox Pure",
         "libdosbox_pure_libretro_android.so",
     ),
+    PCEE2(
+        "pcee2",
+        "PCEE2",
+        "libpcee2_libretro_android.so",
+    ),
     ;
```

---

### 4. System Registration

#### [MODIFY] [`SystemID.kt`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/SystemID.kt)

```diff
     DOS("dos"),
     NINTENDO_3DS("3ds"),
+    PS2("ps2"),
 }
```

#### [MODIFY] [`GameSystem.kt`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/GameSystem.kt)

Add PS2 `GameSystem` entry after the 3DS entry (before the closing parenthesis of the `SYSTEMS` list):

```kotlin
GameSystem(
    SystemID.PS2,
    "Sony - PlayStation 2",
    R.string.game_system_title_ps2,
    R.string.game_system_abbr_ps2,
    listOf(
        SystemCoreConfig(
            CoreID.PCEE2,
            controllerConfigs =
                hashMapOf(
                    0 to arrayListOf(ControllerConfigs.PS2_DUALSHOCK2),
                    1 to arrayListOf(ControllerConfigs.PS2_DUALSHOCK2),
                ),
            requiredBIOSFiles =
                listOf(
                    "scph39001.bin",
                ),
            rumbleSupported = true,
            statesSupported = true,
            supportsLibretroVFS = true,
            skipDuplicateFrames = false,
            supportedOnlyArchitectures = setOf("arm64-v8a"),
        ),
    ),
    uniqueExtensions = listOf(),
    supportedExtensions = listOf("iso", "chd", "cue", "m3u", "cso", "zso", "gz", "bin", "mdf", "nrg", "elf", "irx"),
    scanOptions =
        ScanOptions(
            scanByFilename = false,
            scanByUniqueExtension = false,
            scanByPathAndSupportedExtensions = true,
        ),
    hasMultiDiskSupport = true,
),
```

**Key decisions:**

- **Dedicated `PS2_DUALSHOCK2` controller** with own touch layout
- **`requiredBIOSFiles`** lists `scph39001.bin` — the most commonly used NTSC-U/C BIOS
- **`hasMultiDiskSupport = true`** — PS2 uses `.m3u` playlists for multi-disc
- **2 controller ports** (PS2 natively supports 2 controllers)

---

### 5. BIOS Registration

#### [MODIFY] [`BiosManager.kt`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/bios/BiosManager.kt)

Add PS2 BIOS entries to `SUPPORTED_BIOS` list. These are the most common PS2 BIOS dumps:

```diff
                 Bios(
                     "firmware.bin",
                     "E45033D9B0FA6B0DE071292BBA7C9D13",
                     "Nintendo DS Firmware",
                     SystemID.NDS,
                     "945F9DC9",
                     "nds_firmware.bin",
                 ),
+                Bios(
+                    "scph39001.bin",
+                    "D5CE2C7D119F563CE04BC04571DE9B9F",
+                    "PS2 NTSC-U/C v1.60",
+                    SystemID.PS2,
+                    "0220C2F9",
+                ),
+                Bios(
+                    "scph70012.bin",
+                    "D333558CC14561C1FDC334C0C34137A5",
+                    "PS2 Slim NTSC-U/C v2.00",
+                    SystemID.PS2,
+                    "1B6E631A",
+                ),
+                Bios(
+                    "scph77001.bin",
+                    "BF7E4EAF60459DB6182B11C865E9AECE",
+                    "PS2 Slim NTSC-U/C v2.20",
+                    SystemID.PS2,
+                    "0B27DB79",
+                ),
             )
```

> [!NOTE]
> The MD5 and CRC32 values above are well-known hashes for these specific BIOS versions. PCEE2 accepts any valid PS2 BIOS dump, but these are the most commonly used ones. Additional regional BIOS entries can be added later.

---

### 6. Touch Controller Layout — DualShock 2

#### [NEW] [`omnidroid-touchinput/.../layouts/PS2.kt`](file:///e:/Current%20Work/Omnidroid/omnidroid-touchinput/src/main/java/com/omnidroid/touchinput/radial/layouts/PS2.kt)

Dedicated PS2 DualShock 2 layout — functionally identical to PSXDualShock but as a separate composable for future PS2-specific customization:

```kotlin
package com.omnidroid.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.R
import com.omnidroid.touchinput.radial.controls.OmnidroidControlCross
import com.omnidroid.touchinput.radial.controls.OmnidroidControlFaceButtons
import com.omnidroid.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryAnalogLeft
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryAnalogRight
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonL1
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonL2
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonR1
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonR2
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.PS2Left(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { OmnidroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            SecondaryButtonL1()
            SecondaryButtonL2()
            SecondaryButtonSelect(position = 2)
            SecondaryButtonMenuPlaceholder(settings)
            SecondaryAnalogLeft()
        },
    )
}

@Composable
fun PadKitScope.PS2Right(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            OmnidroidControlFaceButtons(
                ids =
                    persistentListOf(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                    ),
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A) to {
                            OmnidroidButtonForeground(
                                pressed = it,
                                icon = R.drawable.psx_circle,
                            )
                        },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to {
                            OmnidroidButtonForeground(
                                pressed = it,
                                icon = R.drawable.psx_cross,
                            )
                        },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to {
                            OmnidroidButtonForeground(
                                pressed = it,
                                icon = R.drawable.psx_square,
                            )
                        },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X) to {
                            OmnidroidButtonForeground(
                                pressed = it,
                                icon = R.drawable.psx_triangle,
                            )
                        },
                    ),
            )
        },
        secondaryDials = {
            SecondaryButtonR1()
            SecondaryButtonR2()
            SecondaryButtonStart(position = 2)
            SecondaryAnalogRight()
            SecondaryButtonMenu(settings)
        },
    )
}
```

#### [MODIFY] [`TouchControllerID.kt`](file:///e:/Current%20Work/Omnidroid/omnidroid-touchinput/src/main/java/com/omnidroid/touchinput/radial/settings/TouchControllerID.kt)

Add `PS2` enum entry and its `Config` mapping:

```diff
     WS_PORTRAIT,
     NINTENDO_3DS,
+    PS2,
     ;
```

And in the `getConfig` `when` block:

```diff
             NINTENDO_3DS ->
                 Config(
                     { modifier, settings -> Nintendo3DSLeft(modifier, settings) },
                     { modifier, settings -> Nintendo3DSRight(modifier, settings) },
                 )
+
+            PS2 ->
+                Config(
+                    { modifier, settings -> PS2Left(modifier, settings) },
+                    { modifier, settings -> PS2Right(modifier, settings) },
+                )
         }
```

Imports to add:

```kotlin
import com.omnidroid.touchinput.radial.layouts.PS2Left
import com.omnidroid.touchinput.radial.layouts.PS2Right
```

---

### 7. Controller Configuration

#### [MODIFY] [`ControllerConfigs.kt`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/ControllerConfigs.kt)

Add `PS2_DUALSHOCK2` config after `NINTENDO_3DS`:

```diff
     val NINTENDO_3DS =
         ControllerConfig(
             "default",
             R.string.controller_default,
             TouchControllerID.NINTENDO_3DS,
             allowTouchOverlay = false,
             tiltConfigurations =
                 listOf(
                     TILT_CONFIGURATION_DISABLED,
                     TILT_CONFIGURATION_CROSS,
                     TILT_CONFIGURATION_ANALOG_LEFT,
                     TILT_CONFIGURATION_L_R,
                 ),
         )
+
+    val PS2_DUALSHOCK2 =
+        ControllerConfig(
+            "dualshock2",
+            R.string.controller_dualshock2,
+            TouchControllerID.PS2,
+            allowTouchRotation = true,
+            tiltConfigurations =
+                listOf(
+                    TILT_CONFIGURATION_DISABLED,
+                    TILT_CONFIGURATION_CROSS,
+                    TILT_CONFIGURATION_ANALOG_LEFT,
+                    TILT_CONFIGURATION_ANALOG_RIGHT,
+                    TILT_CONFIGURATION_L1_R1,
+                    TILT_CONFIGURATION_L2_R2,
+                ),
+        )
 }
```

---

### 8. String Resources

#### [MODIFY] [`strings-game-system.xml`](file:///e:/Current%20Work/Omnidroid/retrograde-app-shared/src/main/res/values/strings-game-system.xml)

```diff
     <string name="game_system_abbr_3ds">3DS</string>
+    <string name="game_system_abbr_ps2">PS2</string>
     ...
     <string name="game_system_title_3ds">Nintendo 3DS (Beta)</string>
+    <string name="game_system_title_ps2">PlayStation 2 (Beta)</string>
```

#### [MODIFY] [`core_names.xml`](file:///e:/Current%20Work/Omnidroid/omnidroid-app/src/main/res/values/core_names.xml)

```diff
     <string name="core_name_citra" translatable="false">citra</string>
+    <string name="core_name_pcee2" translatable="false">pcee2</string>
 </resources>
```

#### [MODIFY] Controller string resources

A new string for the DualShock 2 controller name needs to be added. Find the file containing the existing controller strings:

In `retrograde-app-shared/src/main/res/values/` (in the same strings file containing `controller_dualshock`):

```diff
     <string name="controller_dualshock">DualShock</string>
+    <string name="controller_dualshock2">DualShock 2</string>
```

---

### 9. Core Update Script

#### [MODIFY] [`update_cores.ipy`](file:///e:/Current%20Work/Omnidroid/omnidroid-cores/update_cores.ipy)

```diff
     #"mednafen_wswan"
     #"citra"
+    #"pcee2"
 ]
```

> [!NOTE]
> Since PCEE2 is NOT on the libretro buildbot, the standard `wget` download in the update script won't work for this core. It must be built from source and manually placed.

---

## Summary of All Files Changed

| File                                                                                           | Action  | Purpose                                 |
| ---------------------------------------------------------------------------------------------- | ------- | --------------------------------------- |
| `omnidroid-cores/omnidroid_core_pcee2/build.gradle.kts`                                        | **NEW** | Gradle dynamic feature module           |
| `omnidroid-cores/omnidroid_core_pcee2/src/main/AndroidManifest.xml`                            | **NEW** | Android manifest for core module        |
| `omnidroid-cores/omnidroid_core_pcee2/src/main/jniLibs/arm64-v8a/libpcee2_libretro_android.so` | **NEW** | Native library (compiled from source)   |
| `omnidroid-touchinput/.../layouts/PS2.kt`                                                      | **NEW** | PS2 DualShock 2 touch controller layout |
| `settings.gradle.kts`                                                                          | MODIFY  | Register new module                     |
| `CoreID.kt`                                                                                    | MODIFY  | Add `PCEE2` enum entry                  |
| `SystemID.kt`                                                                                  | MODIFY  | Add `PS2` enum entry                    |
| `GameSystem.kt`                                                                                | MODIFY  | Add PS2 system definition               |
| `BiosManager.kt`                                                                               | MODIFY  | Add PS2 BIOS entries                    |
| `TouchControllerID.kt`                                                                         | MODIFY  | Add `PS2` enum + config mapping         |
| `ControllerConfigs.kt`                                                                         | MODIFY  | Add `PS2_DUALSHOCK2` controller config  |
| `strings-game-system.xml`                                                                      | MODIFY  | Add PS2 title/abbreviation              |
| `core_names.xml`                                                                               | MODIFY  | Add PCEE2 core name string              |
| Controller strings XML                                                                         | MODIFY  | Add "DualShock 2" string                |
| `update_cores.ipy`                                                                             | MODIFY  | Add pcee2 to cores list                 |

---

## Verification Plan

### Build Verification

- Run `./gradlew assemblePlayDynamic` to ensure the project compiles
- Verify the `libpcee2_libretro_android.so` is packaged in the APK under `arm64-v8a`

### Manual Verification

- Install on an arm64 Android device
- Confirm PS2 system appears in the system list with "(Beta)" label
- Verify BIOS detection prompts when no BIOS is found
- Place a PS2 BIOS and `.iso` file and confirm scanning works
- Launch a PS2 game and verify PCEE2 core loads
- Test touch controls (DualShock 2 layout)
- Test rumble

---

## How to Compile the PCEE2 Core `.so`

The PCEE2 core must be compiled from source. Based on the Lemuroid-upgraded fork's build system:

### Prerequisites

- Linux environment (or WSL)
- Android NDK (latest recommended — older NDK causes clang toolchain failures)
- CMake and Ninja
- JDK 17

### Steps

```bash
# 1. Clone the PCEE2 repo
git clone --recurse-submodules https://github.com/WizzardSK/pcee2-libretro.git
cd pcee2-libretro

# 2. Set NDK path
export ANDROID_NDK_ROOT=/path/to/android-ndk

# 3. Build dependencies (shaderc, etc.) — this can take a while
#    JOBS caps parallelism to prevent OOM on CI (default 2)
export JOBS=2

# Create dependencies build directory
DEPS_DIR="$(pwd)/build/deps"
mkdir -p "$DEPS_DIR"

# Build shaderc and other deps
ANDROID_NDK="$ANDROID_NDK_ROOT" ANDROID_ABI=arm64-v8a ANDROID_API=24 \
    bash pcee2-libretro/scripts/build-deps-android.sh "$DEPS_DIR"

# 4. Configure and build the core
BUILD_DIR="$(pwd)/build/cmake"
cmake -S . -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_QT_UI=OFF \
    -DENABLE_TESTS=OFF \
    -DENABLE_LIBRETRO=ON \
    -DCMAKE_PREFIX_PATH="$DEPS_DIR" \
    -DCMAKE_FIND_ROOT_PATH="$DEPS_DIR" \
    -DSHADERC_STATIC=ON \
    "-DSHADERC_LIBRARY=$DEPS_DIR/lib/libshaderc_combined.a" \
    -DDISABLE_ADVANCE_SIMD=ON

cmake --build "$BUILD_DIR" --target pcee2_libretro --parallel "$JOBS"

# 5. Strip and stage
CORE="$BUILD_DIR/bin/pcee2_libretro.so"
"$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug "$CORE"

# 6. Copy to Omnidroid
cp "$CORE" /path/to/Omnidroid/omnidroid-cores/omnidroid_core_pcee2/src/main/jniLibs/arm64-v8a/libpcee2_libretro_android.so
```

### Important Notes

- The `JOBS=2` limit prevents OOM kills during shaderc compilation
- Use a **recent NDK** — pinned older NDKs cause deterministic clang toolchain failures
- The output is `pcee2_libretro.so` → renamed to `libpcee2_libretro_android.so` (Android convention)
- Minimum Android API is 24 (Android 7.0)
