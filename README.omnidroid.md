# Omnidroid

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

- Landscape settings with a sidebar for Library, Display, Controllers, Saves, and Advanced.
- Big cards on the settings home so you can jump straight to what you need.
- Same look as the rest of the app, and fully usable with a controller.

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
