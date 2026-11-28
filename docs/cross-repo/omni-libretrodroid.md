# Cross-Repo Summary: `omni-libretrodroid`

RetroAchievements-agnostic lifecycle and memory-map hooks merged into `Ahmed-Abousaif/omni-libretrodroid` `main` via [PR #4](https://github.com/Ahmed-Abousaif/omni-libretrodroid/pull/4):

- **Merged commit `d4d4948` (`43f6623` on PR branch)**: `Add SET_MEMORY_MAPS and RA-agnostic native lifecycle hooks`
  - Added `src/main/cpp/nativehooks.h` and `src/main/cpp/nativehooks.cpp` defining `LibretroDroidNativeHooks` (`struct_size`, `on_after_frame`, `on_after_reset`, `on_memory_map_changed`, `on_before_unload`).
  - Updated `src/main/cpp/environment.h` and `src/main/cpp/environment.cpp` to capture `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` descriptors and store `coreLibraryName` / `coreLibraryVersion`.
  - Updated `src/main/cpp/libretrodroid.h` and `src/main/cpp/libretrodroid.cpp` to export `libretrodroid_set_native_hooks`, `libretrodroid_get_memory_descriptors`, `libretrodroid_get_memory_data`, `libretrodroid_get_memory_size`, `libretrodroid_get_library_name`, and `libretrodroid_get_library_version`, invoking lifecycle hooks from `afterGameLoad()`, `step()`, `reset()`, and `destroy()` without adding any RetroAchievements dependency to `omni-libretrodroid`.
- **Rebuilt & Pinned AAR**:
  - Updated `omnidroid-app/libs/omni-libretrodroid-release.aar` and `omnidroid-app/libs/omni-libretrodroid-release.json` (`commit = d4d49481f0059f4d0464b6307756fa4196b6a65a`, `sha256 = 0f3435b26734edb879f75a72d220c20e23db341c89c4c4f82eb6622dc77c8bf2`).
