# Console Backend Layer: Final Sweep & Traceability Verification

**Document:** `docs/console-backends/03-final-sweep.md`  
**Date:** 2026-11-02  
**Status:** Completed & Verified  

---

## 1. Executive Summary

With the completion of Stages (a) through (f) of the Console Backend architecture, all game execution paths now route through the unified backend abstraction:
- **Universal Host**: `BaseGameActivity` / `GameActivity` is the single host in `android:process=":game"` for all backends.
- **Unified Input**: Switch emulation uses Omnidroid's standard input pipeline (`GameViewModelInput`, `TouchControllerID.SWITCH_PRO`, physical gamepads). Dedicated `SwitchOverlayView` is completely removed.
- **Modular Capabilities**: Host and menus query capability interfaces (`StateSaveableSession`, `DiscSwappableSession`, `capabilities.supportsCustomTouchLayout`), eliminating all `isSwitch` or backend type checks.
- **Dynamic Declarative Requirements**: Key/BIOS requirements are declared via descriptors and fulfilled generically through `RequirementPickerLauncherActivity` and dynamic `BiosSettingsScreen` sections.
- **Allowlist Ratchet Guardrail**: Verified by Gradle task `:omnidroid-app:verifyConsoleBackendRatchet`.

---

## 2. Re-Running Phase 1 Search Queries

| Search Query | Phase 1 Result | Final Sweep Result | Notes |
| :--- | :--- | :--- | :--- |
| `CoreID.SWITCH` in `omnidroid-app` | 8 occurrences across activities, views, viewmodels | **0 occurrences** | Completely removed from UI and shared host code. |
| `CoreID.SWITCH` across entire repo | 14 occurrences | **2 occurrences** | Only `GameSystem.kt:2359` (default core registration) and `DetectionRulesTest.kt:497` (unit test). |
| `when (coreId)` / `when (coreID)` | 5 occurrences | **3 occurrences** | 100% confined to allowlisted files (`TouchScreenLayoutController.kt`, `SavesMigrator.kt`, `CoreID.kt`). |
| `if (...system.id == ...)` / `if (...systemId == ...)` | 6 occurrences | **1 occurrence** | Only allowlisted `GameLoader.kt:93` (PCSX2 asset manager retrieval). |
| `SwitchGameActivity` | Main emulation activity | **DELETED** | Removed from code and `AndroidManifest.xml`. |
| `SwitchOverlayView` | Standalone touch overlay | **DELETED** | Replaced by standard `SWITCH_PRO` radial controls. |
| `SwitchKeyPickerLauncher` | Dedicated keys activity | **DELETED** | Replaced by generic `RequirementPickerLauncherActivity`. |
| `EXTRA_EDIT_TOUCH_CONTROLS_SUPPORTED` | Intent flag in `GameMenuContract` | **Confined/Unused** | Custom touch controls supported automatically via capability flag. |

---

## 3. Retained Allowlist Items (With Explicit Architectural Rationale)

The allowlist enforced by `:omnidroid-app:verifyConsoleBackendRatchet` contains strictly the following items:

1. **PS2 Asset Staging & BIOS Quirks** (Phase 1 rows 15, 16, 24, 25):
   - `GameLoader.kt`: PCSX2 asset staging.
   - `BiosManager.kt`: PS2 multi-region BIOS folder management.
   - `BiosSettingsScreen.kt`: Regional BIOS selector.
   - `BiosSettingsViewModel.kt`: PCSX2 asset server downloads.
   - *Rationale*: Explicitly allowlisted per user scope decision; PS2 regional choice is an out-of-scope libretro-core quirk.

2. **Libretro Per-Core Screen & Touch Quirks** (Phase 1 rows 18, 20, 21, 23, 27, 35):
   - `TouchScreenLayoutController.kt`: Nintendo DS / 3DS dual-screen toggling and core widescreen hacks.
   - `GameViewModelTouchControls.kt`: MelonDS DS lid-close key sequence simulation.
   - `SavesMigrator.kt`: MelonDS save file format migration.
   - `GraphicsApiSelectionViewModel.kt`: Core-specific graphics API options (Vulkan vs GLES).
   - `CoreID.kt`: Asset manager factories (`AssetsManager`).
   - *Rationale*: Core-specific libretro quirks that do not affect the backend abstraction boundary.

3. **Static Metadata Tables & ROM Scanner Mappings** (Phase 1 rows 17, 22, 28, 30–34):
   - `GameDatabasesScreen.kt`: Database slice title strings (`when (sliceId)`).
   - `TouchScreenLayoutController.kt`: Static 16:9 aspect ratio lookup for Switch.
   - `GameSystem.kt`: Static system definitions.
   - *Rationale*: Retained console metadata required by catalog, scanner, and database indexing.

4. **Dynamic Feature Packaging & JNI Keep Rules** (Phase 1 rows 3, 4, 5, 6):
   - `omnidroid-app/build.gradle.kts`: Dynamic feature split registration (`:omnidroid_core_switch`).
   - `settings.gradle.kts`: Dynamic feature include paths.
   - `omnidroid-app/proguard-rules.pro`: Keep rules for Eden JNI classes (`org.yuzu.yuzu_emu.**`).
   - *Rationale*: Build system configuration for packaging native dynamic feature splits.

---

## 4. Verification Milestone Results

- **Ratchet Task**:
  `./gradlew :omnidroid-app:verifyConsoleBackendRatchet` $\rightarrow$ `BUILD SUCCESSFUL` (0 violations).
- **Unit Tests**:
  `./gradlew :retrograde-app-shared:testDebugUnitTest` $\rightarrow$ `BUILD SUCCESSFUL` (50 tasks).
- **Free Bundle Build**:
  `./gradlew :omnidroid-app:compileFreeBundleDebugKotlin` $\rightarrow$ `BUILD SUCCESSFUL`.
- **Play Dynamic Build**:
  `./gradlew :omnidroid-app:compilePlayDynamicDebugKotlin` $\rightarrow$ `BUILD SUCCESSFUL` (132 tasks, dynamic feature modules bundled).

