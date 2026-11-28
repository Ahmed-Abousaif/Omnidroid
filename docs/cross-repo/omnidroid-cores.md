# Cross-Repo Summary: `omnidroid-cores`

RetroAchievements native module (`omni_ra`) merged into `Ahmed-Abousaif/OmnidroidCores` `master` via [PR #6](https://github.com/Ahmed-Abousaif/OmnidroidCores/pull/6) and [PR #7](https://github.com/Ahmed-Abousaif/OmnidroidCores/pull/7) (commit `779c8c3506a56f83d5a5be9237a1ade32ed34170`):

- **Pure-C ABI `1.0` (`modules/omni_ra`)**:
  - Vendored `RetroAchievements/rcheevos` release `v12.5.0` (`1433173220a7eaede6a9ed7a18e94117be1821e0`) under `modules/omni_ra/third_party/rcheevos`.
  - Added pure-C99 ABI `1.0` (`modules/omni_ra/include/omni_ra.h`, `src/omni_ra.c`, `exports.map`, `docs/abi.md`), host C unit test suite (`tests/test_omni_ra.c`), and `rc_hash_get_default_cdreader()` fallback for non-CHD disc images.
  - Configured pure-C NDK CMake build (`modules/omni_ra/CMakeLists.txt`) with `-fvisibility=hidden`, `--exclude-libs,ALL`, `-Wl,-z,max-page-size=16384`, linking only `libm` and `libdl` on Android.
- **Standard Core-Style Delivery**:
  - Added `modules/omni_ra/build_omni_ra.py` + `scripts/verify_elf.py` + `tests/test_omni_ra_tooling.py` and staged stripped binaries across all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) into `modules/omni_ra/src/main/jniLibs/<abi>/libomnidroid_ra.so` — matching how normal emulation cores live in `omnidroid-cores`.
  - Updated `bundled-cores/build.gradle.kts` so `bundle` builds automatically package `modules/omni_ra/src/main/jniLibs`.
  - Added `.github/workflows/omni-ra-ci.yml` (host C tests, Android ABI builds, ELF hygiene checks, Python unit tests) with zero signing secrets required.
