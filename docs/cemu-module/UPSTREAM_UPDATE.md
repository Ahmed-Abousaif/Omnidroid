# Upstream Cemu Update Guide for Omnidroid

This document describes how to monitor, update, and build the upstream [cemu-libretro-android](https://github.com/Ahmed-Abousaif/cemu-libretro-android) Wii U emulator core submodule used by Omnidroid.

---

## 1. Upstream Topology & Architecture

- **Submodule Path**: `modules/cemu/upstream/cemu-libretro-android`
- **Canonical Remote**: `https://github.com/Ahmed-Abousaif/cemu-libretro-android.git`
- **Default Branch**: `main`
- **Upstream Cemu Base**: Based on [SSimco/Cemu (`android-port`)](https://github.com/SSimco/Cemu.git) with libretro core integration.
- **Patch Directory**: `modules/cemu/patches/` (pending scanout snapshot, phone-keyboard, and stacked landscape/touch fixes; see its README for application and regression-test instructions).
- **Dynamic Feature Module**: `:omnidroid_core_cemu` (`omnidroid-cores/omnidroid_core_cemu/`)
- **Native Target**: Produces `libcemu_libretro_android.so` for `arm64-v8a`.

---

## 2. Invariants & Rules

1. **Upstream Source Pristine**:
   The `modules/cemu/upstream/cemu-libretro-android` submodule remains clean. Any patches or enhancements should be submitted upstream to the repository.
2. **Dynamic Feature Packaging**:
   `:omnidroid_core_cemu` packages `libcemu_libretro_android.so` in `src/main/jniLibs/arm64-v8a/` and provides 0-byte dummy `.so` files in `armeabi-v7a`, `x86`, and `x86_64` to fulfill Google Play multi-ABI consistency rules while enforcing ARM64-only runtime execution.
3. **No Proprietary Keys**:
   Never bundle Nintendo `keys.txt` or proprietary system firmware. The core reads `keys.txt` from the libretro system directory (`Cemu/keys.txt`).

---

## 3. How to Update Upstream

### Option A: Using the Automated Scripts (Recommended)

#### On Linux / macOS / WSL:
```bash
# Update to latest upstream commit on main
./scripts/cemu-update-upstream.sh

# Or update to a specific commit or tag
./scripts/cemu-update-upstream.sh <commit-hash-or-tag>
```

#### On Windows (PowerShell):
```powershell
# Update to latest upstream commit on main
.\scripts\cemu-update-upstream.ps1

# Or update to a specific commit or tag
.\scripts\cemu-update-upstream.ps1 -TargetRef "<commit-hash-or-tag>"
```

The updater script automatically:
1. Verifies that the submodule working tree is clean.
2. Fetches the latest remote tags and commits from `origin`.
3. Checks out the target ref.
4. Verifies structural integrity (`CMakeLists.txt`, `src/libretro/CemuLibretro.cpp`, `build_libretro_android.sh`).
5. Stages the updated submodule commit in git.

---

## 4. How to Build the Core Binary

To build the native `libcemu_libretro_android.so` library from source using the NDK:

```bash
cd modules/cemu/upstream/cemu-libretro-android
git submodule update --init --recursive
export ANDROID_NDK_HOME="/path/to/android-sdk/ndk/28.2.13676358"
./build_libretro_android.sh Release
```

Once built, the resulting binary is copied to:
```
omnidroid-cores/omnidroid_core_cemu/src/main/jniLibs/arm64-v8a/libcemu_libretro_android.so
```

