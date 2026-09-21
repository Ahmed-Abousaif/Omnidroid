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
        <a href="https://ko-fi.com/abousaif"><img src="https://storage.ko-fi.com/cdn/brandasset/v2/support_me_on_kofi_blue.png" alt="Support me on Ko-fi" height="48" /></a>
        <br /><br />
      </td>
    </tr>
  </table>
</p>

Omnidroid is an open-source Libretro frontend for Android, forked from [Lemuroid](https://github.com/Swordfish90/Lemuroid).

It keeps the core that made Lemuroid solid — ROM scanning, strong Android integration, and a wide set of cores — and builds on top of it with a landscape-first launcher, full controller navigation, richer cloud saves, native Vulkan hardware acceleration, and extensive improvements to UI, functionality, and performance. All while keeping the release build lightweight (universal release APK under 10 MB, ~16% smaller than Lemuroid's baseline).

It started as a fork of Lemuroid (itself a descendant of [Retrograde](https://github.com/retrograde/retrograde-android)). Omnidroid elevates the emulation stack with its custom-engineered [omni-libretrodroid](https://github.com/Ahmed-Abousaif/omni-libretrodroid) native engine—introducing native Vulkan hardware acceleration, 16KB memory page alignment, low-latency audio, and adaptive dual-screen rendering.

## Newly added features

### Vulkan Hardware Acceleration & Graphics API Selection

- **Vulkan Rendering Engine:** Powered by our custom `omni-libretrodroid` engine, offering modern Vulkan hardware acceleration alongside OpenGL ES 2/3 and Software rendering pipelines.
- **Graphics API Selector:** Choose between **Auto**, **Vulkan**, **OpenGL**, and **Software** per console system or globally in Settings.
- **Peak Performance:** Delivers rock-solid 60 FPS hardware rendering with low draw-call overhead on supported cores (such as Azahar / Citra 3DS, PPSSPP, Beetle PSX HW, and Dolphin).

### A real game launcher

- Browse your whole library in landscape, like a handheld instead of a settings app.
- Jump between **All games**, **Favorites**, and each console from a sidebar.
- Set Omnidroid as your Android home screen.
- See battery, Wi-Fi or mobile data, and whether a controller is connected, without leaving the App.

### A library that is easier to live in

- Zoom game covers in or out to show more or fewer games.
- The library grid picks how many rows to show from the **available screen height**, so phones, tall devices, and tablets stay readable at every zoom level.
- Hit **Continue** to jump straight back into your last game.
- Search as you type, advanced search for all games and consoles.

### Play with a controller, not just in-game

- Plug in a gamepad and move through the whole app: library, search, consoles, settings, and game pages.
- See what each button does on a hint bar at the bottom.
- Highlighted items glow so you always know what you are about to select.

### Game pages

- Open a game to see its cover, description, genre, release date, publisher, rating, play time, and when you last played it.
- When RAWG metadata is enabled and a trailer is available, play it from the cover.
- Favorite a game, play it, or change that game’s settings from the same screen.
- Set a custom game image for any title.
- Give any game a custom name.
- Pick a per-game fast-forward speed, including 8x and 16x.

### Experimental RAWG game metadata

- Under **Settings → Advanced**, turn on **RAWG game metadata** (off by default).
- Enter your own free API key from [rawg.io/apidocs](https://rawg.io/apidocs); the key field appears under the toggle.
- On the next library sync, Omnidroid searches RAWG by title, caches description, genres, release date, publisher, rating, and backdrop image on device, and keeps Libretro box art for covers unless a portrait screenshot is available.
- Trailers come from RAWG’s movie files when present — no YouTube scraping.
- Independent of cloud login / save sync; leave it off and game pages show empty metadata fields.

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

### Touch D-Pad: 4-way or 8-way

- In **Edit Controls**, toggle **8-way D-Pad** on or off for any console with a D-Pad.
- On = diagonals (default); off = cardinal directions only.
- Saved per layout and orientation with your other touch control settings.

### NDS and 3DS dual-screen layouts & dynamic orientation flipping

- **melonDS DS (NDS):** Screens layout includes **Top Only** and **Bottom Only**, alongside Top–Bottom and Left–Right.
- **Azahar / Citra (3DS):** Screens layout includes **Single Screen**, plus **Displayed screen** (Top or Bottom) to choose which one shows.
- **Dynamic Orientation Flipping:** In dual-screen mode, Omnidroid automatically flips between **Top–Bottom** in portrait mode and **Side by Side (Left–Right)** in landscape mode as you rotate your device.
- **Interactive 3DS Touchscreen Overlay:** Accurate touch stylus input mapped to the 3DS bottom screen with precise aspect-ratio coordinate conversion.
- **Quick Screen Toggle:** On the virtual pad (melonDS DS / Azahar / Citra), tap the **screen layout** button to toggle dual ↔ **Top only** without opening the menu. The core option stays perfectly synchronized with in-game settings.

### Vibration intensity

- Under **Settings → Controllers** (and Advanced → Input), a **Vibration intensity** slider (0–100, default 50) controls strength for both touch feedback and rumble.
- Applies to on-screen control haptics and gamepad / device rumble together.

### NDS and 3DS touchscreen with virtual buttons

- You can use the emulated touchscreen while holding on-screen buttons — a second finger on the game screen still registers as the stylus.
- Tap the **hide pads** button on the DS/3DS virtual pad to slide the controls away and give the game more screen space (handy for stylus-only moments). A small edge tab at the bottom brings the pads back.

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
- Nintendo DS (NDS) ([melonDS DS](https://github.com/JesseTG/melonds-ds)/[melonDS](https://docs.libretro.com/library/melonds/)/[desmume](https://docs.libretro.com/library/desmume/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- Nintendo 3DS (3DS) ([azahar](https://github.com/azahar-emu/azahar)/[citra](https://docs.libretro.com/library/citra/))
- PlayStation 2 (PS2) (Beta) ([pcee2](https://github.com/WizzardSK/pcee2-libretro))
- Nintendo GameCube (GameCube) (Beta) ([dolphin](https://docs.libretro.com/library/dolphin/))
- Nintendo Wii (Wii) (Beta) ([dolphin](https://docs.libretro.com/library/dolphin/))

## Nintendo 3DS Emulation

Omnidroid includes full **Nintendo 3DS** emulation powered by the **Azahar** (modern Citra fork) and **Citra** libretro cores:

- **Target Architecture:** Optimized for 64-bit architectures (`arm64-v8a` and `x86_64`).
- **Hardware Acceleration:** Native support for both **Vulkan** and **OpenGL ES 3.0+** rendering pipelines via `omni-libretrodroid`.
- **Supported ROM Formats:** `.3ds`, `.3dsx`, `.elf`, `.axf`, `.cci`, `.cxi`, `.app`, `.cia`.
- **Dynamic Dual-Screen Layouts:** Seamless automatic flipping between Top–Bottom (Portrait) and Side by Side (Landscape), with instant Top-only virtual button toggle.
- **Interactive Touchscreen:** Bottom screen touch stylus input with precise multi-touch coordinate translation while operating virtual face buttons.
- **Decrypted & System Data:** Automatic handling of decrypted ROMs, shared system archives, and DLC/update paths.

## GameCube & Wii Emulation (Beta)

Omnidroid includes experimental **Nintendo GameCube** and **Nintendo Wii** emulation powered by the **Dolphin** libretro core:

- **Target Architecture:** Optimized for 64-bit architectures (`arm64-v8a` and `x86_64`).
- **Core Assets Managed Automatically:** Dolphin's `Sys/` assets (game settings database, fonts, DSP engine) are automatically managed and downloaded to `system/dolphin-emu/Sys/` on first launch.
- **Supported Disc & ROM Formats:**
  - **GameCube:** `.iso`, `.gcm`, `.gcz`, `.rvz`, `.ciso`, `.tgc`, `.m3u`, `.dol`, `.elf`.
  - **Wii:** `.iso`, `.wbfs`, `.gcz`, `.rvz`, `.ciso`, `.wad`, `.m3u`, `.dol`, `.elf`.
- **Multi-Disc Support:** Multi-disc titles are supported through `.m3u` playlists.
- **Custom Touch Layouts:** Dedicated on-screen touch layouts for GameCube (Main Stick, C-Stick, A/B/X/Y, Z trigger, L/R) and Wii (Classic Controller and Wii Remote + Nunchuk) with full physical gamepad mapping and rumble support.
- **Save States & Memory Cards:** Native support for libretro save states and per-system virtual Memory Cards / NAND.

## PlayStation 2 Emulation (Beta)

Omnidroid includes experimental **PlayStation 2 (PS2)** emulation powered by the **PCEE2** libretro core (PCSX2-based):

- **Target Architecture:** Optimized for 64-bit architectures (`arm64-v8a` and `x86_64`).
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
- Customizable touch controls (size, position, and 4-way / 8-way D-Pad)
- HD mode

## Bug fixes

These are issues from Lemuroid’s original flow, or regressions found while building Omnidroid, that are now fixed:

- Tapping a library game no longer launches it by accident. Only **Continue** starts play immediately.
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

### Build system & Toolchain

- Android Gradle Plugin: **8.4.0 → 9.0.0**
- Kotlin: **2.0.21 → 2.2.10**
- Annotation processing: **kapt → KSP** (`2.2.10-2.0.2`) for Room and Hilt
- Dependency injection: **Dagger-Android 2.19 → Hilt 2.60.1**
- Jetifier: **removed** (`android.enableJetifier` off)
- Global Play Services force to `17.0.0`: **removed**
- Pre-AndroidX `android.arch.lifecycle:reactivestreams`: **removed**
- Optional ABI filtering via `-PabiFilters=` (default still ships all four ABIs)
- Android SDK & Build Tools: **minSdk 23**, **targetSdk / compileSdk 36**, Java **17**, **buildTools 36.0.0**
- Android 15+ readiness: **16KB memory page alignment** across native C++ modules and stripped release libraries

### Native Emulation Engine

- Custom engine fork: **[omni-libretrodroid](https://github.com/Ahmed-Abousaif/omni-libretrodroid)** (migrated from stock LibretroDroid 0.13.2)
- Added native **Vulkan Hardware Acceleration** pipeline (`VulkanRenderer`, `VulkanContext`, `VulkanRetroView`) alongside GLES and Software renderers
- C++ symbol visibility minimization (`-fvisibility=hidden`) and `-O3` compilation flags for peak performance and compact binary footprint (<10 MB release universal APK)
- Dual-mode build support (live local source module integration or standalone pre-built multi-ABI release AAR fallback)

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

- PadKit **1.0.0-beta1**
- Leanback **1.1.0-rc01**
