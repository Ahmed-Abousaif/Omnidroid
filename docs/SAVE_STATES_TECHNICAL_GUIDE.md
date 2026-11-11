# Omnidroid save states — reference guide

Current reference: 11 November 2026. Attach this file to future prompts about save states, autosave, save slots, or enabling state support for a console/core. Read the current source before making changes; the support list below is a snapshot of configuration, not a claim that every game has been tested.

This guide describes the implementation after adding direct autosave loading, extra save slots, and visible save/load errors.

## 1. The four kinds of saves

**In-game saves** are saves made through the game's own menu, such as a battery save or memory card. The shared Libretro path persists SRAM through `SavesManager`. Some engines also manage their own save files. Turning off Omnidroid's autosave setting does not turn off a game's own saving.

**Manual save states** capture the emulator's current position. Players select a slot in the in-game Save State menu and restore it from Load State. These are files that survive closing the game.

**Autosave** is a separate emulator state used to resume a game. There is one autosave per ROM filename and core. It does not occupy a manual slot. Players can also select it explicitly from Load State.

**Quick Save / Quick Load** are controller shortcuts that use one snapshot held in memory for the current session. That snapshot disappears when the game process ends. Quick Save is not the on-disk autosave, despite some older internal names such as `quickSaveData` and `restoreQuickSave` referring to autosave.

## 2. Manual slots and adding more

- Every supported game/core starts with four displayed manual slots.
- Save State allows writing to any displayed slot. Selecting an existing slot replaces it without an overwrite confirmation.
- Load State disables empty slots and shows available timestamps and screenshot previews.
- **Save to new slot** captures the current state and writes a new numbered slot. The first added slot is 5, even if slots 1–4 are empty.
- The next slot number is one above the highest existing slot, with the original four reserved. Adding a slot does not replace an existing slot or fill a lower-numbered gap.
- Extra slots are discovered from that game's files in the selected core's directory. There is no global slot-count setting or per-game database counter.
- Imported higher-numbered slots appear automatically. Sparse numbering is preserved: importing slot 13 does not create empty rows for slots 5–12.
- Adding slots for one game/core does not add slots for other games/cores.
- There is no separate add-empty-slot, delete-slot, or rename-slot action in the current menu. A new slot becomes persistent when its state file is written.

Slot indices in Kotlin and Intent commands are zero-based. The UI and filenames are one-based: index `4` means slot 5. Use the explicit `SlotInfo.index`; a row's position in the list is not necessarily its slot index.

A successful manual save attempts to capture a JPEG preview. A preview failure is logged separately and does not make a successfully written state count as a failed save.

## 3. When autosave is written

The global **Autosave state** setting defaults to enabled. Automatic capture requires `systemCoreConfig.statesSupported && settingsManager.autoSave()`.

For the shared state-saveable session path, the app captures and writes progress when:

- The player quits from the game menu.
- The player selects Save & Quit from the running-game notification.
- The game Activity enters `onStop`, such as when it goes into the background.

The background path skips saves while the game is loading, when the session does not implement `StateSaveableSession`, and when the Activity is finishing or changing configuration. There is no periodic autosave timer in this flow. A crash or force-stop is not a guaranteed save trigger.

The snapshot contains SRAM, when available, and an emulator state when autosave is enabled. SRAM and autosave writes are attempted independently so a failure in one does not prevent attempting the other. A successful autosave replaces the previous autosave; there is no autosave history or rotating set of autosave slots.

Manual slots are not changed by automatic saving. After restarting or loading an older manual state, a later automatic save normally replaces autosave with the current position. To keep a position permanently, save it to a manual slot.

## 4. How autosave is loaded

Normal Play and the library's Resume action request automatic restoration. The library's Restart action launches without restoring autosave. Restart inside a running game resets the emulator. Neither action deletes the saved files.

Automatic restoration requires all of the following:

1. The selected core configuration supports states.
2. The launch requested restoration.
3. The global autosave setting is enabled.
4. An autosave can be read and is not skipped by the SRAM freshness check.
5. Its metadata version matches the current configuration, and the core accepts the state.

`SavesCoherencyEngine` skips automatic restoration if SRAM exists and is more than 30 seconds newer than autosave. This protects newer in-game progress, such as progress made while autosave was disabled. The check skips loading; it does not delete autosave.

Automatic restoration waits for the first rendered-frame event because some cores initialize state during rendering. Restoration then attempts up to 10 loads, with 200 ms between unsuccessful attempts. Exhausted attempts produce an error. The initial wait for a rendered-frame event currently has no explicit timeout.

**Load State → Autosave** is an explicit request. It works even with the global autosave setting disabled and bypasses the SRAM freshness check. It still checks the metadata version and whether the core accepts the state. The row is disabled when no nonempty autosave is found. There is currently no autosave screenshot preview.

## 5. Errors and recovery

- Missing or unreadable manual states, incompatible versions, core rejections, and save failures produce visible errors instead of being treated as success.
- A missing file returns `null` from `StatesManager`. Read/decompression errors, empty decoded state data, and malformed metadata throw; other invalid payloads may only be rejected by the core. Missing legacy metadata is accepted with default values.
- If autosave cannot be read at launch, the game can still open without it. The failure is reported to the session.
- A failed automatic or explicit autosave load sets `preserveAutoSave`. Automatic state writes are suppressed for the rest of that session, unless a later explicit autosave load succeeds. SRAM and manual saving remain available. This protection is a session flag, not a permanent backup or archive.
- Failed save-and-quit opens a dialog with Retry, Cancel, and Quit without saving. Cancel leaves the game running. Background save failure shows an error.
- Quick Save reports success only after capture succeeds; a failed capture keeps the previous in-memory snapshot. Quick Load reports a missing snapshot or failed load instead of reporting success.
- Failure to list states produces a Retry action in the menu.
- Cancellation is propagated through the app-level save/load handlers rather than shown as an ordinary save error.

Writes can partly succeed: for example, SRAM may be saved even if autosave fails. Choosing Quit without saving does not roll back writes that already succeeded. Individual state and metadata files use atomic writes, but the pair is not one transaction. Do not describe the system as guaranteeing recovery from every interruption or storage failure.

## 6. What decides whether a console has save states?

The main application setting is **`SystemCoreConfig.statesSupported`**, declared for each console/core combination in [GameSystem.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/GameSystem.kt). Its default in [SystemCoreConfig.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/SystemCoreConfig.kt) is `true`. Omitting the property therefore enables the feature in configuration.

The chosen core matters. [CoresSelection.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/core/CoresSelection.kt) selects the saved core preference for a system, with a fallback to its first configured core. One console can have different state support depending on the selected core.

`statesSupported` controls whether the main in-game menu displays Save and Load, and participates in automatic capture and launch restoration. It is a declaration maintained by this project, not an automatic reliability test of the installed emulator binary. There is no current per-game state-support allowlist or denylist.

### Current configuration

Explicitly disabled:

- MAME 2003-Plus — `MAME2003PLUS`.
- DOS — `DOSBOX_PURE`.
- Nintendo 3DS using Citra — `CITRA`.
- Wii U — `CEMU`.
- Nintendo Switch using Eden — `SWITCH`.

Enabled:

- Atari 2600 — `STELLA`; Atari 7800 — `PROSYSTEM`; Atari Lynx — `HANDY`.
- NES — `FCEUMM`; SNES — `SNES9X`.
- Master System, Game Gear, Genesis/Mega Drive, and Sega CD — `GENESIS_PLUS_GX`.
- Game Boy and Game Boy Color — `GAMBATTE`; Game Boy Advance — `MGBA`.
- Nintendo 64 — `MUPEN64_PLUS_NEXT`.
- PlayStation — `PCSX_REARMED`; PSP — `PPSSPP`; PlayStation 2 — `ARMSX2`.
- Arcade using FinalBurn Neo — `FBNEO`.
- Nintendo DS — `MELONDS_DS`.
- PC Engine — `MEDNAFEN_PCE_FAST`.
- Neo Geo Pocket and Color — `MEDNAFEN_NGP`.
- WonderSwan and Color — `MEDNAFEN_WSWAN`.
- Nintendo 3DS using Azahar — `AZAHAR`.
- GameCube and Wii — `DOLPHIN`.
- Dreamcast — `FLYCAST`.

Enabled means the current app configuration exposes support. It does not guarantee compatibility for every game, graphics mode, device, or core update. A disabled flag alone also does not explain the historical reason for disabling a core or prove that an upstream emulator can never support states. Investigate the actual shipped core before changing the flag.

### Backend capabilities are a separate layer

[BackendCapabilities.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/backend/BackendCapabilities.kt) also declares `supportsSaveStates`, but the current implementation is not fully unified:

- `LibretroBackendDescriptor.capabilities()` returns `supportsSaveStates = true` for all its cores, including configurations whose `statesSupported` is false.
- `SwitchBackendDescriptor` returns false.
- `LibretroBackendSession` implements `StateSaveableSession` for the adapter generally. Interface membership alone does not prove that a particular core supports serialization.
- The working manual slot/file path runs through `GameViewModelSaves` and `StatesManager`. The adapter's `saveState(slot)` only captures bytes, and `loadState(slot)` is currently a stub returning true. Do not route file loading through these methods and assume it is implemented.
- `SwitchBackendSession` does not implement `StateSaveableSession`; its `IRetroGameView` state methods return null/false. Flipping its configuration flag cannot create native state support.
- Controller Quick Save/Load dispatch does not have the same `statesSupported` gate as the menu. Unsupported captures/loads can therefore reach the error handlers through shortcuts.

For a future capability cleanup, inspect and align the configuration, backend descriptor, session interfaces, menu, shortcuts, and actual serialization path together. These are current limitations to account for, not changes already implemented by this guide.

## 7. What the emulator must implement

For Libretro cores, [libretrodroid.cpp](../omni-libretrodroid/src/main/cpp/libretrodroid.cpp) calls:

- `retro_serialize_size()` to determine the required state size. Zero means no state is captured.
- `retro_serialize()` to fill the state buffer. False means capture failed.
- `retro_unserialize()` to restore the state. False means restoration failed.

Kotlin reaches these functions through `IRetroGameView`, the OpenGL wrapper or Vulkan view, `LibretroDroid`, and JNI. Both a nonempty capture and a successful restore are needed. A screenshot, saved SRAM file, or a standalone emulator's Save State button does not demonstrate that the specific Android core/backend shipped here implements those operations correctly.

When proposing support for a currently disabled core:

1. Identify the console, selected core, shipped core revision, backend, device architecture, and graphics mode.
2. Inspect that core/backend's actual serialization and restoration implementations. Implement missing engine support before enabling the UI.
3. Test capture and restoration in a running session and after closing and reopening the game. Compare the restored gameplay position, not just the return value.
4. Test representative games, relevant graphics modes, disc changes, and any core settings that affect state compatibility.
5. Check state versioning and existing saves before changing `statesSupported`. Set the flag explicitly for new or uncertain integrations rather than relying on the default.
6. Confirm menu visibility, manual slots, extra slots, autosave, shortcuts, and error recovery through the real session path.
7. Update this support snapshot and add focused regression coverage for the behavior being changed.

Disabling states should hide state features and stop automatic emulator-state capture/restoration. It should preserve existing state files and the game's normal saves.

## 8. Storage and compatibility

[DirectoriesManager.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/storage/DirectoriesManager.kt) puts these folders inside the app's external-files directory:

- Manual state: `states/<coreName>/<game.fileName>.slot<N>`.
- Autosave: `states/<coreName>/<game.fileName>.state`.
- Metadata: the state filename plus `.metadata`.
- Manual screenshot: `state-previews/<coreName>/<game.fileName>.slot<N>.jpg`.
- Shared SRAM: `saves/<ROM filename without its final extension>.srm`; legacy `.sav` is accepted by `SavesManager`.

For `Example.nes` with FCEUmm, slot 5 is `states/fceumm/Example.nes.slot5`. Its metadata is `Example.nes.slot5.metadata`. The state data is GZIP-compressed; reading also accepts older uncompressed data. The state filename itself has no `.gz` suffix.

State identity uses the ROM filename and core name, not the database game ID or full ROM path. Two ROMs with identical filenames using the same core can therefore collide. Different core directories separate their states. The shared SRAM naming is broader and is not separated by core.

`SaveState.Metadata` contains `diskIndex` and `version`. Loading checks exact equality with `SystemCoreConfig.statesVersion` before restoring. Multi-disc systems also switch to the saved disc index when applicable.

The default metadata/configuration version is `0`; Nintendo DS with `MELONDS_DS` currently uses `2`. This is a project-maintained compatibility number, not the app version or an automatically detected core version. A core update can still break states while this number stays unchanged. Raising it rejects older states using that configuration; it does not convert or delete them.

Changing naming, compression, core folders, or metadata needs a compatibility plan. Preserve existing `.slot1`–`.slot4` and `.state` files when changing UI or slot limits. `SaveFileRenamer` handles filename-based artifact moves; backup and sync code enumerate state directories, and game matching accepts the numbered state files. Recheck those paths when changing the format. Syncing a state file does not make it compatible with another core or core version.

## 9. File map for future prompts

- [SystemCoreConfig.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/SystemCoreConfig.kt), [GameSystem.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/GameSystem.kt), and [CoresSelection.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/core/CoresSelection.kt): support declaration, compatibility version, and selected core.
- [GameMenuHomeScreen.kt](../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuHomeScreen.kt): Save/Load visibility.
- [GameMenuStatesViewModel.kt](../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/states/GameMenuStatesViewModel.kt) and [GameMenuStatesScreen.kt](../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/states/GameMenuStatesScreen.kt): slot rows, autosave entry, add-new action, previews, and listing errors.
- [InGameMenuCommand.kt](../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/InGameMenuCommand.kt) and [GameMenuContract.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/GameMenuContract.kt): `Save(index)`, `Load(index)`, `SaveNew`, and `LoadAutoSave` transport. New-slot and autosave commands use separate keys rather than special manual-slot indices.
- [GameMenuActivity.kt](../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuActivity.kt) and [BaseGameActivity.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/BaseGameActivity.kt): return commands after closing the menu, apply them to the running game, background triggers, and the failed-quit dialog.
- [BaseGameScreenViewModel.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/BaseGameScreenViewModel.kt): loading guard, active-session gate, save/quit/background orchestration, and startup restoration.
- [GameViewModelSaves.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/GameViewModelSaves.kt): capture, persistence, version/disc checks, in-memory Quick Save, and autosave preservation.
- [SaveStateRestorer.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/SaveStateRestorer.kt): bounded restoration retries and exhausted-retry error.
- [StatesManager.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/StatesManager.kt), [StatesPreviewManager.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/StatesPreviewManager.kt), and [SaveState.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/SaveState.kt): file storage, slot discovery/allocation, previews, and metadata.
- [SavesManager.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/SavesManager.kt) and [SavesCoherencyEngine.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/SavesCoherencyEngine.kt): SRAM and the freshness check.
- [GameLoader.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/game/GameLoader.kt), [GameViewModelRetroGameView.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/GameViewModelRetroGameView.kt), and [GameInteractor.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/GameInteractor.kt): launch restoration request, disk reading, and passing startup errors to the session.
- [LibretroBackendDescriptor.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/backend/libretro/LibretroBackendDescriptor.kt), [LibretroBackendSession.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/backend/libretro/LibretroBackendSession.kt), and [BackendSession.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/backend/BackendSession.kt): backend declarations and the current adapter limitations.
- [SwitchBackendDescriptor.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/backend/switch_backend/SwitchBackendDescriptor.kt) and [SwitchBackendSession.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/game/backend/switch_backend/SwitchBackendSession.kt): separate native backend and its unsupported state methods.
- [SaveFileRenamer.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/SaveFileRenamer.kt), [SaveBackupManager.kt](../omnidroid-app/src/main/java/com/omnidroid/app/shared/savesync/SaveBackupManager.kt), and [SaveSyncFileMatcher.kt](../retrograde-app-shared/src/main/java/com/omnidroid/lib/savesync/SaveSyncFileMatcher.kt): rename, backup/import, and game-file matching.

## 10. Verification and useful future prompts

Run the focused tests from the repository root:

```powershell
.\gradlew.bat :retrograde-app-shared:testDebugUnitTest --tests com.omnidroid.lib.saves.StatesManagerTest
.\gradlew.bat :omnidroid-app:testFreeBundleDebugUnitTest --tests com.omnidroid.app.mobile.feature.gamemenu.InGameMenuCommandTest --tests com.omnidroid.app.shared.game.viewmodel.SaveStateRestorerTest
```

Storage tests cover extra slots, isolation by game/core, imported sparse numbering, concurrent allocation, metadata, corrupt data, empty captures, and write failures. Command tests cover distinct autosave/new-slot commands and indices beyond four. Restore tests cover successful retries, exhaustion, incompatibility, and cancellation. These tests do not prove that a real native core can restore a game; device verification remains necessary.

On a device, check slots 1–4 and 5+, overwrite behavior, previews, exit/reopen, explicit autosave loading with autosave disabled, newer SRAM, failed/incompatible restoration, failed quit, Quick Save lifetime, and an unsupported core's hidden menu entries. Keep backup copies of valuable saves when testing compatibility changes.

Useful prompt starting points:

- "Use docs/SAVE_STATES_TECHNICAL_GUIDE.md and current source. Investigate whether [console/core] can reliably support save states in the shipped Android build. Explain engine work and compatibility implications before changing its support flag."
- "Use the save-state guide. Change [slot/menu behavior], preserve existing saves and filenames, and check manual slots, autosave, shortcuts, and failure recovery."
- "Use the save-state guide. Diagnose why [game/core] fails to [save/load/resume]. Trace the selected configuration, file/metadata, runtime serializer, and visible error rather than only changing menu visibility."
