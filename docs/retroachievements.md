# RetroAchievements Integration Architecture

## Overview

Omnidroid integrates **RetroAchievements (RA)** using `RetroAchievements/rcheevos` (`v12.5.0`) isolated inside a standalone, pure-C shared library (`libomnidroid_ra.so`) hosted in **`omnidroid-cores`**. The main Android application loads and communicates with `libomnidroid_ra.so` dynamically at runtime via a thin JNI bridge (`:omnidroid-ra`), following the exact same distribution layout as emulation cores.

## Repository Boundaries

| Component | Repository | Role |
|---|---|---|
| `modules/omni_ra` | `omnidroid-cores` | Compiles `rcheevos` `v12.5.0` as `libomnidroid_ra.so` exporting pure-C ABI `1.0` (`omni_ra_*`), staged under `modules/omni_ra/src/main/jniLibs/<abi>/`. |
| Native lifecycle hooks | `omni-libretrodroid` | Captures `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` and invokes optional C function pointers (`on_after_frame`, `on_after_reset`, `on_memory_map_changed`, `on_before_unload`). |
| `:omnidroid-chd` | `Omnidroid` | Exports C symbols (`omnichd_cd_open_track_fd`, `omnichd_cd_open_track_path`, `omnichd_cd_read_sector`, `omnichd_cd_close_track`, `omnichd_cd_first_track_sector`) for direct `.chd` hashing without copying. |
| `:omnidroid-ra` | `Omnidroid` | App-owned JNI bridge (`ra_native_bridge.cpp` + `NativeRetroAchievementsEngine`) performing `dlopen`/`dlsym`, `RTLD_NOLOAD` resolution of LibretroDroid and `omnichd`, zero-JNI per-frame ticking on the emulation thread, and OkHttp/SAF bridges. |
| `retrograde-app-shared` | `Omnidroid` | Platform-independent models, `HardcorePolicy`, `RaCoreCatalog`, `RaModuleManager`, `RaModuleStore`, `RaCredentialStore`, `RaSaveStateProgressCodec`, and `RaGameCache` (Room v20). |
| `omnidroid-app` | `Omnidroid` | Hilt wiring, `RetroAchievementsCoordinator`, `RaModuleUpdateWork` (unmetered background fetch), Settings -> RetroAchievements, in-game overlays (`RaOverlayHost`), `GameMenuAchievementsScreen`, `GameDetailsScreen` tab, and Hardcore enforcement. |

## Distribution (`free`, `play`, `bundle`)

- **GitHub (`freeDynamic`, `BuildConfig.GITHUB_UPDATES = true`)**:
  - `RaModuleManager` downloads `libomnidroid_ra.so` directly from `https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/<CORES_VERSION>/modules/omni_ra/src/main/jniLibs/<abi>/libomnidroid_ra.so`, matching `GithubCoreDownloader` used by normal console cores.
  - `RaModuleStore` validates non-empty ELF magic (`\x7fELF`), stages atomically into `files/modules/omni_ra/<CORES_VERSION>/libomnidroid_ra.so`, and prunes old versions when `GameProcessLock.isHeldByAnotherProcess(context)` is false.
- **Bundled / Google Play (`freeBundle`, `playBundle`, `playDynamic`)**:
  - `:bundled-cores` includes `modules/omni_ra/src/main/jniLibs` for `bundle` variants, and `play` includes `modules/omni_ra/src/main/jniLibs` directly in `sourceSets.getByName("play").jniLibs`.
  - Remote GitHub binary downloads remain disabled on `play` (`BuildConfig.GITHUB_UPDATES = false`).
- **Unavailable Fallback**:
  - When `libomnidroid_ra.so` is absent or fails to load, `RetroAchievementsEngine` stays unloaded cleanly without failing game launches, and `HardcorePolicy.isHardcoreActive` remains `false`.

## Multi-Process Architecture & Credential Storage

Omnidroid runs `GameActivity`, `GameService`, and `GameMenuActivity` in `android:process=":game"`, separate from the main UI process:
- **Credentials (`RaCredentialStore`)**: Passwords are used once via `loginWithPassword` and **never persisted**. Resulting `{username, token}` pairs are encrypted via Android Keystore **AES-256-GCM** (`omnidroid_ra_token_aes_v1`) and stored in Harmony multi-process `SharedPreferences` so both processes share session state safely.
- **Game Cache (`RaGameCacheDao`, Room v20)**: Active RA game summaries, achievements, leaderboards, and Rich Presence snapshots are persisted in SQLite (`ra_game_cache`) from `:game` so `GameDetailsScreen` in the main process renders cached RA data immediately offline.

## Hardcore Mode Rules

Hardcore Mode defaults to **`true`** (`pref_key_ra_hardcore`) when RetroAchievements is enabled:
- **Auto-Save on Launch**: Skipped with a user toast notice when Hardcore is active (`HardcoreBlockReason.LOAD_AUTO_SAVE_ON_LAUNCH`), while battery/SRAM saves (`saveRAMState`) still load normally.
- **Save States**: Saving states remains allowed; **loading** save states (manual slots, quick-load hotkeys, autosave manual load, `GameMenuStatesScreen` load mode, `LibretroBackendSession.loadState`) is blocked.
- **Rewind / Slow-Motion / Cheats / Disallowed Core Options**: Blocked via `HardcorePolicy` across `BaseGameScreenViewModel`, `GameViewModelRetroGameView`, `GameViewModelSaves`, `LibretroBackendSession`, and `GameMenuCoreOptionsScreen`. Fast-Forward (`>= 1x`) remains allowed.
- **Pause Cooldown**: User-initiated pause/menu requests consult `rc_client_can_pause` (`HardcorePolicy.canPauseGame()`) and defer menu opening with a countdown notice during cooldowns. Android OS lifecycle `onPause`/`onStop` is never blocked.
- **Softcore State Progress**: In Softcore Mode (`!hardcorePolicy.isHardcoreActive`), `RaSaveStateProgressCodec` appends a versioned `"OMRA"` progress trailer after the serialized libretro state blob and strips it before `retro_unserialize` so cores checking exact state sizes remain compatible.
- **Excluded Backends**: Non-Libretro backends (`Switch` / `Eden`, `PS3` / `ARMSX3`) are excluded via `RaCoreCatalog`.

## Content Hashing & Optical Discs

`RaContentResolverDelegate` supports:
- Standard extracted/local files (`RomFiles.Standard`, including `.7z` and `.zip` cache extractions).
- Scoped Storage / SAF virtual FDs (`RomFiles.Virtual`, opening independent `ParcelFileDescriptor`s via `ContentResolver` without consuming LibretroDroid's own FDs).
- `.chd` disc images streamed sector-by-sector directly through `omnichd_cd_*`.
- `.cue`/`.bin`/`.iso`/`.m3u` multi-file and multi-disc games resolved against `DataFileDao` entries and `.m3u` playlists, triggering `identifyAndChangeMedia` on disc swaps.

## Remaining Production TODO

- **RetroAchievements User-Agent Whitelisting**: Production User-Agent strings follow `Omnidroid/<appVersion> (Android) omnidroid_ra/<moduleVersion> rcheevos/<rcheevosVersion>` (with `; <core>` inside the platform clause during gameplay). Coordinate official client identification/User-Agent approval with RetroAchievements before public release.
