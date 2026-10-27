# External Game Launching Contract for Omnidroid

Omnidroid (`com.omnidroid`) exposes an exported trampoline activity that allows external Android game frontends and Android TV system channels to launch games directly by file path or URI without querying internal database IDs.

---

## 1. Canonical Invocation

The official public entry point is `com.omnidroid.app.shared.game.ExternalGameLauncherActivity`:

```bash
am start -n com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity \
  -a android.intent.action.VIEW \
  -c android.intent.category.DEFAULT \
  -d {file.uri} \
  -t application/octet-stream
```

### Manifest Filters

The activity is declared in `AndroidManifest.xml` with `android:exported="true"` and two intent-filters to ensure compatibility across Android 12, 13, 14, and 15:

* **Filter A (No MIME Type):** Catches frontends that omit `-t` (e.g. Daijishō defaults, certain file managers).
  * Action: `android.intent.action.VIEW`
  * Category: `android.intent.category.DEFAULT`
  * Schemes: `content`, `file`
* **Filter B (Explicit MIME Type):** Catches frontends specifying octet-stream (PPSSPP/ES-DE style).
  * Action: `android.intent.action.VIEW`
  * Category: `android.intent.category.DEFAULT`
  * Schemes: `content`, `file`
  * MIME type: `application/octet-stream`

### Activity Attributes

* `android:exported="true"`: Required for external caller visibility.
* `android:taskAffinity=""`: Ensures the trampoline runs in its own task affinity rather than polluting the app's main task stack.
* `android:launchMode="singleTask"`: Re-uses the launch instance if triggered in rapid succession.
* `android:excludeFromRecents="true"`: Prevents the trampoline launcher from appearing as an independent tile in Android Overview / Recents.
* `android:noHistory="true"`: Ensures the trampoline activity is immediately cleared from the backstack.
* `android:theme="@style/OmnidroidMaterialTheme.Invisible"`: Prevents screen flicker or blank window animations while transitioning to the emulator.

---

## 2. Input Resolution Order

When `ExternalGameLauncherActivity` receives an intent, it inspects the data and extras in the following strict order of precedence:

1. **`intent.data`**: Standard `content://` or `file://` URI.
2. **Intent String Extras** (checked in order):
   * `ROM` (DIG, Daijishō DuckStation / AetherSX2 conventions)
   * `bootPath` (Citra / Dolphin conventions)
   * `GamePath`
   * `PATH` (DIG alternative)
   * `AutoStartFile`
   * `uri`
3. **Normalization**:
   * Raw filesystem paths (e.g. `/storage/emulated/0/...`) are converted to `file://` URIs.
   * Path segments are canonicalized (resolving `.` and `..`, eliminating redundant slashes).
   * URL-encoded characters and SAF document IDs are parsed into uniform path representations.
4. **Disambiguation Extra (`SYSTEM`)**:
   * Optional string extra passed to select the target core/console when a file extension is shared across multiple platforms (e.g. `.chd`, `.iso`, `.bin`, `.zip`).

If no valid URI or path can be resolved, the activity displays a short error toast, writes a logcat warning, and calls `finish()` without crashing.

---

## 3. Disambiguating Systems with the `SYSTEM` Extra

For extensions unique to a single console (e.g., `.sfc`, `.nes`, `.gba`, `.z64`), Omnidroid automatically identifies the target system.

When using multi-system file formats (such as `.chd`, `.iso`, `.bin`, `.zip`, `.cue`, `.pbp`), provide the `--es SYSTEM <id>` extra.

### Supported `SYSTEM` Codes and Aliases

| System / Console | Canonical ID | Recognized Aliases (case-insensitive) | Default for Colliding Extensions |
| :--- | :--- | :--- | :--- |
| **Sony PlayStation (PS1 / PSX)** | `psx` | `psx`, `ps1`, `playstation`, `ps` | Default for `.chd`, `.iso`, `.cue`, `.pbp` |
| **Sony PlayStation 2** | `ps2` | `ps2`, `playstation2` | — |
| **Sony PlayStation Portable** | `psp` | `psp` | — |
| **Sega Genesis / Mega Drive** | `genesis` | `genesis`, `megadrive`, `mega_drive`, `md` | Default for `.bin` |
| **Sega Dreamcast** | `dreamcast`| `dreamcast`, `dc` | — |
| **Sega CD** | `segacd` | `segacd`, `scd`, `mega_cd` | — |
| **Sega Master System** | `sms` | `sms` | — |
| **Sega Game Gear** | `gg` | `gg` | — |
| **Sega Saturn** | `saturn` | `saturn` | — |
| **Nintendo Entertainment System**| `nes` | `nes`, `famicom` | — |
| **Super Nintendo (SNES)** | `snes` | `snes`, `sfc`, `super_nintendo` | — |
| **Nintendo 64** | `n64` | `n64` | — |
| **Game Boy** | `gb` | `gb`, `gameboy` | — |
| **Game Boy Color** | `gbc` | `gbc`, `gameboy_color` | — |
| **Game Boy Advance** | `gba` | `gba`, `gameboy_advance` | — |
| **Nintendo DS** | `nds` | `nds`, `ds` | — |
| **Nintendo 3DS** | `3ds` | `3ds`, `nintendo_3ds` | — |
| **Nintendo GameCube** | `gamecube` | `gamecube`, `gc` | — |
| **Nintendo Wii** | `wii` | `wii` | — |
| **Arcade / FinalBurn Neo** | `fbneo` | `fbneo`, `arcade` | Default for `.zip` |
| **Arcade / MAME** | `mame2003plus`| `mame`, `mame2003plus` | — |
| **PC Engine / TurboGrafx-16** | `pc_engine`| `pce`, `pc_engine`, `turbografx` | — |
| **Atari 2600** | `atari2600` | `atari2600` | — |
| **Atari 7800** | `atari7800` | `atari7800` | — |
| **Atari Lynx** | `lynx` | `lynx` | — |
| **WonderSwan** | `ws` | `ws` | — |
| **WonderSwan Color** | `wsc` | `wsc` | — |
| **Neo Geo Pocket** | `ngp` | `ngp` | — |
| **MS-DOS** | `dos` | `dos` | — |

---

## 4. Frontend Launch Configurations

> **Note on Verification**: Configuration strings below adhere to each frontend's official parameter specification and have been validated against Omnidroid's manifest and intent resolver. Marked as **[Untested on physical device]** where tested via unit test suites rather than interactive APK automation.

### 4.1 Daijishō

#### Standard (Direct Data URI)
* **Player JSON**:
```json
{
  "name": "Omnidroid - General",
  "description": "Omnidroid multi-system emulator",
  "acceptedFilenameRegex": "^(.*)\\.(?:zip|7z|nes|sfc|smc|gba|gbc|gb|z64|n64|chd|iso|cue|bin|pbp)$",
  "amStartArguments": "-n com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity -a android.intent.action.VIEW -c android.intent.category.DEFAULT -d {file.uri} -t application/octet-stream",
  "killPackageProcesses": true,
  "extra": ""
}
```

#### Disambiguated (e.g. PSX CHD / PBP)
```json
{
  "name": "Omnidroid - Sony PlayStation",
  "acceptedFilenameRegex": "^(.*)\\.(?:chd|cue|pbp|iso)$",
  "amStartArguments": "-n com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity -a android.intent.action.VIEW -c android.intent.category.DEFAULT -d {file.uri} -t application/octet-stream --es SYSTEM psx",
  "killPackageProcesses": true
}
```

---

### 4.2 ES-DE (EmulationStation Desktop Edition - Android)

*(Untested on physical device)*

Add to `ES-DE/custom_systems/es_systems.xml` or `es_find_rules.xml`:

```xml
<!-- In es_find_rules.xml -->
<rule host="android">
    <entry>
        <label>Omnidroid</label>
        <package>com.omnidroid</package>
        <activity>com.omnidroid.app.shared.game.ExternalGameLauncherActivity</activity>
        <action>android.intent.action.VIEW</action>
        <data>%ROMSAF%</data>
        <mimetype>application/octet-stream</mimetype>
        <extra name="SYSTEM" type="string">%SYSTEM%</extra>
    </entry>
</rule>
```

Alternatively, direct command syntax in `es_systems.xml`:
```xml
<command label="Omnidroid">%EMULATOR_OMNIDROID% %ACTION% android.intent.action.VIEW %COMPONENT% com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity %DATA% %ROMSAF% %MIMETYPE% application/octet-stream --es SYSTEM %SYSTEM%</command>
```

---

### 4.3 Pegasus Frontend

*(Untested on physical device)*

Add to `metadata.pegasus.txt`:

```ini
collection: Super Nintendo Entertainment System
shortname: snes
extensions: sfc, smc, zip
launch: am start
  -n com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity
  -a android.intent.action.VIEW
  -c android.intent.category.DEFAULT
  -d {file.uri}
  -t application/octet-stream
  --es SYSTEM snes
```

---

### 4.4 Beacon Game Launcher

*(Untested on physical device)*

Configure in custom launcher settings:
* **Package**: `com.omnidroid`
* **Activity**: `com.omnidroid.app.shared.game.ExternalGameLauncherActivity`
* **Action**: `android.intent.action.VIEW`
* **Category**: `android.intent.category.DEFAULT`
* **Data**: `{file_uri}` (or `{file_path}`)
* **MIME Type**: `application/octet-stream`
* **Extras**:
  * Key: `SYSTEM`
  * Value: `snes` (or corresponding system shortname)

---

### 4.5 Arc Browser

*(Untested on physical device)*

Configure under **Settings > More... > Emulators > Custom Emulator**:
* **Component**: `com.omnidroid/com.omnidroid.app.shared.game.ExternalGameLauncherActivity`
* **Action**: `android.intent.action.VIEW`
* **Category**: `android.intent.category.DEFAULT`
* **Data URI**: `%rom_uri%`
* **MIME type**: `application/octet-stream`
* **Extra parameters**: `--es SYSTEM %system%`

---

### 4.6 DIG

*(Untested on physical device)*

Configure in DIG emulator configuration:
* **Package**: `com.omnidroid`
* **Activity**: `com.omnidroid.app.shared.game.ExternalGameLauncherActivity`
* **Action**: `android.intent.action.VIEW`
* **Data**: `%ROM%`
* **Key extra**: `ROM` = `%ROM%`
* **System extra**: `SYSTEM` = `%SYSTEM%`

---

### 4.7 Reset Collection

*(Untested on physical device)*

Configure custom emulator:
* **Package**: `com.omnidroid`
* **Activity**: `com.omnidroid.app.shared.game.ExternalGameLauncherActivity`
* **Action**: `android.intent.action.VIEW`
* **Category**: `android.intent.category.DEFAULT`
* **Data**: `{file.uri}`
* **Type**: `application/octet-stream`

---

## 5. Library Lookup & Temporary Game Fallback

When a URI is received:
1. Omnidroid queries the Room database using `fileUri`.
2. If not immediately found (e.g. SAF tree URI vs document URI formatting differences), `FileUriNormalizer` extracts normalized document paths and compares against the database.
3. If still not found, Omnidroid checks for an exact filename match within the library.
4. **Temporary Game Fallback**: If the ROM has never been scanned into the Room database, Omnidroid identifies the target console using `SystemDetector` (using extension and `SYSTEM` extra) and creates a **temporary unpersisted `Game` instance** (`id = 0`). The game boots immediately without requiring a prior full-library scan.

---

## 6. Exit Behavior & Clean Caller Return

When launched via `ExternalGameLauncherActivity`:
* The internal intent marks the session with `EXTRA_EXTERNAL_LAUNCH = true`.
* The trampoline activity finishes immediately after passing intent execution to the `:game` process.
* When the user presses the exit hotkey, selects "Quit" from the in-game pause menu, or presses the hardware Back button:
  * Omnidroid executes `finishAndRemoveTask()` on `BaseGameActivity`.
  * Omnidroid **does not** navigate to `MainActivity` or open the internal library UI.
  * Focus returns directly to the calling frontend.

---

## 7. Storage & URI Permissions

* **`content://` URIs**: Frontends must grant read permissions (`FLAG_GRANT_READ_URI_PERMISSION`). Omnidroid automatically attempts to call `takePersistableUriPermission()` if offered.
* **`file://` and Raw Paths**: Omnidroid requires standard filesystem read access. If access is restricted by Android Scoped Storage and the URI is inaccessible, a descriptive toast is displayed and execution stops safely. Broad storage permissions are not requested on the fly.

---

## 8. Android TV Recents Row Integration

Android TV channels utilize the identical contract:
* `ChannelHandler` builds intent URIs using `ExternalLaunchIntentBuilder.forFile(appContext, game.fileUri)`.
* Before publishing preview program tiles, Omnidroid verifies that the stored URI has valid read permissions (`ContentResolver.persistedUriPermissions`).
* If read permission is lost or revoked, the stale program tile is pruned automatically without crashing the launcher.
* The main channel banner continues to link to `MAIN` / `LEANBACK_LAUNCHER`.

---

## 9. Known Limitations

1. **Foreign Key Guard for Temporary Games**:
   * Temporary non-persisted games (`id = 0`) bypass the SQLite `game_sessions` table because `game_sessions` requires a valid foreign key referencing an existing `games` row. Playtime and session history are only tracked for games indexed in the library.
2. **Multi-Disc Games**:
   * For multi-disc games (e.g. PSX, Saturn), point the frontend to the master `.m3u` playlist rather than individual `.cue` or `.bin` disc files.
3. **Archive Extensions (.zip / .7z)**:
   * When launching compressed ROMs with multi-system ambiguity, always include `--es SYSTEM <system>` (e.g. `--es SYSTEM snes`) so the correct emulator core is selected instead of defaulting to arcade.
