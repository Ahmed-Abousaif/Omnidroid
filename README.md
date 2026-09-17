# Omnidroid

<p align="center">
  <table>
    <tr>
      <td align="center" width="560">
        <br />
        <strong>Support Omnidroid</strong>
        <br />
        <sub>If the app helps you play, you can fuel continued development here</sub>
        <br /><br />
        <a href="https://www.buymeacoffee.com/abousaif"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="48" /></a>
        &nbsp;&nbsp;
        <a href="https://ko-fi.com/abousaif"><img src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Support me on Ko-fi" height="48" /></a>
        <br /><br />
      </td>
    </tr>
  </table>
</p>

Everything you loved about [Lemuroid](https://github.com/Swordfish90/Lemuroid), rebuilt into a faster, cleaner, more capable Android game launcher.

Omnidroid is an open-source Libretro frontend for Android. It keeps the parts that made Lemuroid great — simple ROM scanning, automatic saves, strong Android integration, and a wide set of cores — then layers on a landscape-first launcher, full controller navigation, richer cloud saves, and a game page that actually tells you something about what you are about to play.

It started as a fork of Lemuroid (itself a descendant of [Retrograde](https://github.com/retrograde/retrograde-android) with [LibretroDroid](https://github.com/Swordfish90/LibretroDroid)). The emulation stack is the same. The product around it is not.

## Newly added features

### A real game launcher

- Browse your whole library in landscape, like a handheld instead of a settings app.
- Jump between **All games**, **Favorites**, and each console from a sidebar.
- Set Omnidroid as your Android home screen.
- See battery, Wi-Fi or mobile data, and whether a controller is connected, without leaving the App.

### A library that is easier to live in

- Zoom game covers in or out to show more or fewer games.
- Hit **Continue** to jump straight back into your last game
- Search as you type, advanced search for all games and consoles.

### Play with a controller, not just in-game

- Plug in a gamepad and move through the whole app: library, search, consoles, settings, and game pages.
- See what each button does on a hint bar at the bottom.
- Highlighted items glow so you always know what you are about to select.

### Game pages

- Open a game to see its cover, description, genre, release date, play time, and when you last played it.
- Play game trailers and see ratings.
- Favorite a game, play it, or change that game’s settings from the same screen.
- Set a custom game image for any title.
- Give any game a custom name.
- Pick a per-game fast-forward speed, including 8x and 16x.

### Add consoles

- Flip through consoles and install the ones you want, with touch or a controller.

### Revamped settings

- Landscape settings with a sidebar for Library, Display, Controllers, Saves, Advanced, and About.
- Big cards on the settings home so you can jump straight to what you need.
- Same look as the rest of the app, and fully usable with a controller.
- About covers privacy policy, donation channels, and developer info.

### Cast to a TV

- Send games to a TV or monitor over Cast, wireless display, or HDMI.
- Pick a game on your phone; it appears on the big screen.

### Cloud saves (Play build)

- Sync saves with **Google Drive**, **Dropbox**, or **OneDrive**.
- Pull down the latest save before a game starts, or play anyway if you do not want to wait.
- Turn cloud save on or off for a single game.
- Pick how often auto-sync runs.
- Resolve conflicts when local and cloud saves disagree.
- Export or import a backup zip of your saves.

### Smarter HD mode

- HD mode turns itself off when the battery drops below 15%, then comes back when you have charge again.

### Your profile

- Open **Profile** from the library top bar. A gamer tag and robot avatar are created on first launch; change either anytime.
- Earn XP from play sessions (~1 XP per minute), with a daily streak bonus that scales up to ×1.7 for consecutive days.
- Level up on a smooth curve from total XP; see current level, progress to the next, and total playtime.
- Track current and best play streaks (counted at local midnight).
- Unlock consoles as you install them or scan games, and earn collection badges (Collector → Enthusiast → Historian → Omnidroid Master).
- Browse recent sessions with XP earned per session.
- When cloud saves are enabled, your profile syncs as `profile.json` alongside game saves.

### Device profile and benchmarking

- Under **Settings → Advanced → Device profile**, see manufacturer, SoC, RAM, CPU cores and frequencies, GPU / OpenGL ES, and Vulkan support.
- A quiet quick CPU baseline (~0.5s) runs the first time you open the app.
- Optionally run a 30-second CPU benchmark (welcome prompt on first launch, or re-run anytime from Device profile) to measure throughput in ops/s and help gauge how well the device can run emulators.

## Description

Omnidroid is built to feel like a dedicated handheld, not a settings-heavy emulator. Scan a folder of ROMs, pick a game, play. When a controller is connected, the whole UI is navigable without touching the screen. When a TV is connected, games can launch on that display.

The original Lemuroid goals still apply: ease of use, good Android integration, and a great emulation experience. Omnidroid just takes those further.

## Supported systems

- Atari 2600 (A26) ([stella](https://docs.libretro.com/library/stella/))
- Atari 7800 (A78) ([prosystem](https://docs.libretro.com/library/prosystem/))
- Atari Lynx (Lynx) ([handy](https://docs.libretro.com/library/handy/))
- Nintendo (NES) ([fceumm](https://docs.libretro.com/library/fceumm/))
- Super Nintendo (SNES) ([snes9x](https://docs.libretro.com/library/snes9x/))
- Game Boy (GB) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Color (GBC) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Advance (GBA) ([mgba](https://docs.libretro.com/library/mgba/))
- Sega Genesis (aka Megadrive) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega CD (aka Mega CD) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Master System (SMS) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Game Gear (GG) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Nintendo 64 (N64) ([mupen64plus](https://docs.libretro.com/library/mupen64plus/))
- PlayStation (PSX) ([PCSX-ReARMed](https://docs.libretro.com/library/pcsx_rearmed/))
- PlayStation Portable (PSP) ([ppsspp](https://docs.libretro.com/library/ppsspp/))
- FinalBurn Neo (Arcade) ([fbneo](https://github.com/libretro/FBNeo/))
- Nintendo DS (NDS) ([desmume](https://docs.libretro.com/library/desmume/)/[MelonDS](https://docs.libretro.com/library/melonds/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- Nintendo 3DS (3DS) ([citra](https://docs.libretro.com/library/citra/))
- PlayStation 2 (PS2) (Beta) ([pcee2](https://github.com/WizzardSK/pcee2-libretro))

## PlayStation 2 Emulation (Beta)

Omnidroid includes experimental **PlayStation 2 (PS2)** emulation powered by the **PCEE2** libretro core (PCSX2-based):

- **Target Architecture:** Optimized and restricted to 64-bit ARM (`arm64-v8a`) devices.
- **BIOS Required:** A legally dumped PS2 BIOS is required (e.g. `scph39001.bin`, `scph70012.bin`, `scph77001.bin`). Omnidroid automatically verifies the BIOS and syncs it into the core's `system/pcsx2/bios/` directory.
- **Supported Disc & ROM Formats:** `.iso`, `.chd`, `.cue`, `.m3u`, `.cso`, `.zso`, `.gz`, `.bin`, `.mdf`, `.nrg`, `.elf`, `.irx`.
- **Multi-Disc Support:** Multi-disc titles are supported through `.m3u` playlists.
- **Custom DualShock 2 Layout:** Dedicated on-screen touch layout with dual analog sticks, D-Pad, face buttons, shoulder triggers (L1/L2/R1/R2), Select, Start, and full physical gamepad mapping with rumble support.
- **Shaders & State Management:** Supports quick save/load states and CRT / modern display enhancement shaders.

## Features from Lemuroid, still here

- Android TV support
- Automatically save and restore game states
- ROM scanning and indexing
- Quick save/load
- Support for zipped ROMs
- Display simulation (LCD/CRT)
- Gamepad support
- Local multiplayer
- Tilt input
- Customizable touch controls (size and position)
- HD mode

## Bug fixes

These are issues from Lemuroid’s original flow, or regressions found while building Omnidroid, that are now fixed:

- Notification permission no longer just tells you to enable it — the app actually prompts, and can open system settings if needed.
- Rescan used to do work with no UI feedback. The top bar now shows a scanning state.
- Tapping a library game no longer launches it by accident. Only **Continue** starts play immediately.
- Game descriptions and trailers failed to load; Wikipedia / Wikidata / YouTube lookup now works.
- Back from the launcher could leave you on the stock Android home screen. It now stays inside Omnidroid.
- System status and navigation bars leaked over the launcher; both are hidden.
- HD mode could keep burning battery at low charge. It now auto-disables under 15%.
- Core updates re-downloaded cores that were already on disk. Missing cores are detected and fetched instead.
- Play-flavor core load errors pointed people at the Play Store even when that was not the problem.
- Cloud sync could wipe a newer local save when a remote file disappeared. Deletes are no longer propagated.
- Simultaneous local and cloud changes had no resolution UI. Conflicts can now be kept, replaced, or duplicated.
- There was no way to back up saves without a cloud account. Export/import zip covers that.

## Languages

You can help translate the original Lemuroid strings here: https://crowdin.com/project/lemuroid

Omnidroid-specific strings (library, cast, cloud providers, controller hints, game details) are currently English-first.

## System changes and package upgrades

Toolchain and library upgrades applied on top of the Lemuroid baseline.

### Build system

- Android Gradle Plugin: **8.4.0 → 9.0.0**
- Kotlin: **2.0.21 → 2.2.10**
- Annotation processing: **kapt → KSP** (`2.2.10-2.0.2`) for Room and Hilt
- Dependency injection: **Dagger-Android 2.19 → Hilt 2.60.1**
- Jetifier: **removed** (`android.enableJetifier` off)
- Global Play Services force to `17.0.0`: **removed**
- Pre-AndroidX `android.arch.lifecycle:reactivestreams`: **removed**
- Optional ABI filtering via `-PabiFilters=` (default still ships all four ABIs)
- Android support unchanged: **minSdk 23**, **targetSdk / compileSdk 35**, Java **17**

### UI and AndroidX

- Compose BOM: **2024.02.02 → 2026.08.00**
- Navigation: **2.5.2 → 2.8.8**
- Lifecycle: **2.6.1 → 2.9.0** (ViewModel Compose aligned to the same version)
- Room: **2.6.1 → 2.8.4**
- WorkManager: **2.9.0 → 2.10.0**
- Paging: **3.2.1 → 3.3.6**
- Core KTX: **1.8.0 → 1.16.0**
- AppCompat: **1.4.2 → 1.7.0**
- Fragment: **1.5.1 → 1.8.6**
- Activity: **1.7.2 → 1.10.1**
- Material Components: **1.6.1 → 1.12.0**
- ProfileInstaller: **1.3.1 → 1.4.1**
- Startup runtime: **1.1.1 → 1.2.0**
- DocumentFile: **1.0.1 → 1.1.0**
- Preference KTX: **1.1.1 → 1.2.1**
- RecyclerView: **1.2.1 → 1.4.0**
- ConstraintLayout: **2.1.4 → 2.2.1**
- ConstraintLayout Compose: **1.1.0 → 1.1.1**
- Collection KTX: **1.1.0 → 1.4.5**
- Accompanist: **0.34.0 → 0.36.0** (still used; APIs remain deprecated)

### Networking and utilities

- OkHttp: **4.9.1 → 5.5.0**
- Okio: **2.10.0 → 3.18.2**
- Retrofit: **2.9.0 → 3.0.0**
- Kotlin coroutines: **1.6.4 → 1.10.2**
- kotlinx-serialization: **1.2.2 → 1.8.1**
- Coil: **2.6.0 → 2.7.0**
- Guava: **30.1.1-android → 33.4.0-android**

### Play / cloud

- Play Services Auth: **17.0.0 → 21.6.0**
- Play In-App Review: **2.0.0 → 2.0.2**
- Play Feature Delivery: **2.1.0** (unchanged)
- Google API Client / Drive: **1.32.1** (still pending a newer client)

### Unchanged pins

- LibretroDroid **0.13.2**
- PadKit **1.0.0-beta1**
- Leanback **1.1.0-rc01**
- Build tools **34.0.0**
