# Omnidroid: Community Feedback & Roadmap

Community requests, feature milestones, and bug reports from the community, organized by status. Usernames credit the community members who raised each item.

---

## Completed

### v2.5.0

- Added customizable startup screen (launch directly into All Games, Favorites, or a chosen console)
- Added analog stick to D-pad directional routing on retro consoles
- Added N64 16:9 widescreen support (_u/SideEffect07_)
- Added compact adaptive layout & square screen scaling for 3.5" handhelds and 1:1 displays (_u/keithitreal_)
- Added in-game screen positioning (_u/GhiStale_)
- Fixed portrait startup flicker
- Add PS1/SNES aspect ratio & scaling
- Add real-time in-game refresh
- remove DeSmuME
- Added in-app "Fix Match / Search Box Art" dialog
- Smarter game matching with token-fuzzy search and franchise alias resolution & smart punctuation recovery
- Added "Pre-cache All Covers" in Advanced Settings for offline library artwork browsing
- Overhauled console database slices: ~47% smaller file sizes, deduplicated entries, and faster on-device lookups
- Added virtual controller opacity setting for on-screen pads
- Kept on-screen pads, sidebars, and top chrome clear of display cutouts (camera, sensors, etc.) in landscape (_u/-BMX-_)
- Modernized the Vulkan renderer for smoother frame pacing, reduced thermal throttling, and stutter-free shader execution on mobile GPUs.
- Fixed Immersive mode dynamic background coloring across OpenGL ES 3 mobile GPUs ([#8](https://github.com/Ahmed-Abousaif/Omnidroid/issues/8)) (_u/-BMX-_)
- Extended vertical gamepad positioning range in portrait mode to allow centered controls ([#11](https://github.com/Ahmed-Abousaif/Omnidroid/issues/11)) (_uiro1_)
- Added 3DS .zcci/.cxi scanning ([#16](https://github.com/Ahmed-Abousaif/Omnidroid/issues/16)) (_Deus0_)
- Added GameCube/Wii OpenGL stability default ([#19](https://github.com/Ahmed-Abousaif/Omnidroid/issues/19)) (_Deus0_)
- Added PS2 regional BIOS fallback ([#18](https://github.com/Ahmed-Abousaif/Omnidroid/issues/18)) (_Deus0_)
- Added safe zip ROM extraction ([#14](https://github.com/Ahmed-Abousaif/Omnidroid/issues/14)) (_Deus0_)
- Fixed PSP .chd disc booting by disabling virtual file system routing for PPSSPP ([#20](https://github.com/Ahmed-Abousaif/Omnidroid/issues/20)) (_Deus0_)
- Fixed Dreamcast (Flycast) black screen under OpenGL ES by isolating frontend context state ([#15](https://github.com/Ahmed-Abousaif/Omnidroid/issues/15)) (_yurix_)
- Fixed Nintendo 3DS touch screen input by routing secondary pointer gestures to the lower display ([#9](https://github.com/Ahmed-Abousaif/Omnidroid/issues/9)) (_vermahardik735_)
- Added landscape dual-screen layout options (Side-by-Side, Top-Bottom, Single Screen) for Nintendo 3DS ([#10](https://github.com/Ahmed-Abousaif/Omnidroid/issues/10)) (_vermahardik735_)
- Fixed arcade detection failure by resolving renamed archives via title and member CRC matching and aliasing driver names at launch ([#23](https://github.com/Ahmed-Abousaif/Omnidroid/issues/23)) (_Deus0_)

### v2.4.1

- Added Portrait layout mode and compact List view alongside Grid view (_u/Kwametoure1_, _u/AfroKenTheAfroDog_, _u/Fat_Stacks1_, _u/JungleRollers_)
- Migrated settings screen to portrait view and enhanced list view navigation

### v2.4.0

- Revised ROM scraping with content-based header scanning and modular title database slices (_u/keithitreal_)
- Added unrecognized files inspector and database slice management UI

### v2.2.0

- Added Nintendo Wii U support via Cemu
- Upgraded PS2 emulation core to ARMSX2 with OpenGL, Vulkan, and Software renderers (_u/RobinRelique_, _u/MatheusWillder_)
- Added Sega Dreamcast support via Flycast (_u/dogwater80085_, _u/milosmisic89_)

### v2.1.0

- Custom native engine fork (`omni-libretrodroid`) with Vulkan hardware acceleration
- Fixed MSU-1 ROM support (_u/Rent_Careless_)
- Fixed PSP CHD file detection (_u/Sans_2093_)
- Fixed menus that required touchscreen when using a controller (_u/Sans_2093_)
- Fixed missing automatic cover art on some consoles (_u/Sans_2093_)
- Fixed settings sidebar scroll (_u/Shitandroidowner_)

### v2.0.x

- Added Nintendo 3DS (Azahar / Citra) and GameCube / Wii (Dolphin) emulation
- Added quick dual-to-single screen layout toggle and hide virtual buttons for DS and 3DS
- Fixed 3DS touch button positioning (_u/RiverSorry2643_)
- Fixed screen dimming when leaving a game (_u/pulin_o_burrin_)

---

## In progress

- Two-way launcher & intent integration: launch Omnidroid games from external frontends, and launch standalone emulator apps (such as Winlator for PC emulation) from Omnidroid for unsupported systems (_u/Jeno_Jodi_, _u/Ok_Cartoonist_1737_)
- Google Play Store release (closed testing) (_u/Kwametoure1_)

---

## Planned

### Emulation & cores

- Upscaling / higher resolution with finer control, plus more shader options (upscaling already works for Wii / GameCube / 3DS) (_u/Mr2Sexy_, _u/Abdallah_player1_, _u/bboy_3431_)
- Custom shaders at a RetroArch-like level (_u/Due-Car-6521_)
- Cheat codes (_u/madzleng_)
- RetroAchievements (planned after full release) (_u/Fein_shit_, _u/Abdallah_player1_, _u/Repulsive_Cow_2470_)
- DOS (DOSBox Pure), Pico-8, and Sega Saturn (_u/milosmisic89_, _u/Kwametoure1_)
- PSP custom texture support (needed for some English patches) (_u/DragonBane52_)
- SNES widescreen patches (_u/Le_Sairo_)
- Rewind (fast-forward already available) (_u/bboy_3431_)

### Controls & display

- Better touchscreen customization overall (_u/Repulsive_Cow_2470_)
- In-game screen size (_u/GhiStale_)

### Library & UI

- Bulk import of RetroArch game covers (migration feature planned) (_u/JungleRollers_)
- Switch-style UI layout (consoles/favorites at the bottom; search and system indicators at the top) (_u/calm_drink_)

### Platform & distribution

- Transfer saves from Lemuroid (simpler for GitHub installs; Play Store path still unclear) (_u/Kwametoure1_)
- Donation option in the Play Store build (support links already exist on GitHub and in About) (_u/Tall-Average5330_)

---

## Known issues

- Immersive mode (dynamic background coloring based on game visuals) not working on some devices ([#8](https://github.com/Ahmed-Abousaif/Omnidroid/issues/8)) (_u/-BMX-_)
