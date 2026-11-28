# RetroAchievements Testing & Verification

## 1. Native `omni_ra` Unit & ELF Verification (`omnidroid-cores`)

Run host CMake tests, ABI staging build, and Python verification tests in `omnidroid-cores`:

```sh
cmake -S omnidroid-cores/modules/omni_ra -B omnidroid-cores/build/host-ra -DOMNI_RA_BUILD_TESTS=ON
cmake --build omnidroid-cores/build/host-ra
ctest --test-dir omnidroid-cores/build/host-ra --output-on-failure
python omnidroid-cores/modules/omni_ra/build_omni_ra.py
python -m unittest discover -s omnidroid-cores/modules/omni_ra/tests -p "test_*.py"
```

### Android NDK Build & ELF Verification (All 4 ABIs)

`modules/omni_ra/scripts/verify_elf.py` validates for every target ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`):
- `DT_NEEDED` only contains `{libm.so, libdl.so, libc.so}` (zero `libc++_shared.so`).
- Exported dynamic symbols match `omni_ra_*` only.
- `PT_LOAD` segment alignment is `>= 0x4000` (16 KB Android 15+ page-size compliance).

## 2. JVM Unit & Failure-Injection Tests (`Omnidroid`)

Run shared and app JVM tests:

```powershell
.\gradlew :retrograde-app-shared:testDebugUnitTest :omnidroid-app:testFreeDynamicDebugUnitTest --console=plain
```

### Coverage Breakdown

- `RetroAchievementsCoreTest` (`retrograde-app-shared`):
  - `RaCoreCatalog`: verifies Switch (`Eden`) & PS3 (`ARMSX3`) exclusions plus Libretro console/core mappings.
  - `HardcorePolicy`: verifies Hardcore gating for save-state load, launch auto-save skip, rewind, slow-motion (`<1.0x`), fast-forward allowed, cheats, memory editing, disallowed core options, pause cooldowns (`Cooldown`), Softcore demotion, and `NoopRetroAchievementsEngine` fallback.
  - `RaSaveStateProgressCodec`: verifies exact core-state round-trip preservation, empty/null RA progress passthrough, legacy state compatibility, and corrupt trailer resilience.
  - `RaCredentialStore`: verifies AES-256 encrypted token round-trip and confirms raw passwords are never written.
- `RaModuleDeliveryTest` (`retrograde-app-shared`):
  - Verifies raw GitHub URL resolution matching `GithubCoreDownloader` conventions (`modules/omni_ra/src/main/jniLibs/<abi>/libomnidroid_ra.so`).
  - Verifies Play channel policy (`allowRemoteBinaryDownload = false` makes zero network requests).
  - Verifies non-ELF / corrupt response rejection, valid atomic ELF promotion, cached reload, and failed-load cleanup.
- `InGameMenuCommandTest` (`omnidroid-app`):
  - Verifies `InGameMenuCommand.SetHardcore` extras mapping without aliasing existing slot/speed extras.

## 3. Code Style & Static Guardrails

Run ktlint across changed modules:

```powershell
.\gradlew :retrograde-app-shared:ktlintCheck :omnidroid-ra:ktlintCheck :omnidroid-app:ktlintTestSourceSetCheck --console=plain
```

## 4. On-Device Memory-Map & Gameplay Verification Checklist

Once signed into RetroAchievements in **Settings -> RetroAchievements**:
1. Launch a game on each test system (**NES / FCEUmm**, **SNES / Snes9x**, **Genesis / Genesis Plus GX**, **GBA / mGBA**, **PS1 / PCSX-ReARMed**, and **PC Engine / Mednafen PCE Fast**).
2. Open the in-game menu -> **Achievements** -> **Memory Regions Diagnostics** and verify `omni_ra_dump_memory_regions` lists non-zero `rc_libretro_memory_regions` backed by live core RAM pointers.
3. Verify `.chd` and SAF `.cue`/`.bin`/`.7z` ROMs resolve positive RA game IDs without extracting entire `.chd` archives to disk.
4. Verify Hardcore blocks save-state load while permitting Fast-Forward (`2x`/`4x`), skips auto-save resume on launch, and honors `rc_client_can_pause` during pause cooldowns.
5. Verify **GameDetails -> Achievements** displays cached game badge, unlocked points/counts, and achievement list after closing `:game`.
