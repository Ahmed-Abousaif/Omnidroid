# Omnidroid: Community Feedback & Roadmap

Community requests, feature milestones, and bug reports from the community, organized by status. Usernames credit the community members who raised each item.

---

## Completed

### v3.2.0

- Added RetroAchievements integration via a dedicated pure-C `rcheevos` engine (`omni_ra` / `omnidroid-ra`) with token-based login, Hardcore Mode enforcement, Encore/Spectator/Unofficial modes, leaderboards, Rich Presence, customizable unlock overlays with synthesized chimes, offline achievement caching, compact **Game Details -> Achievements** and in-game **Achievements** views, and a dedicated **Retro Achievements** tab in Profile ([#21](https://github.com/Ahmed-Abousaif/Omnidroid/issues/21)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@Deus0](https://github.com/Deus0))
- Added PlayStation 3 emulation via an integrated ARMSX3 engine (Android 13+, ARM64, Vulkan), supporting ISO and extracted-folder games, firmware/PKG/license imports, 15 graphics settings, manual snapshot slots, quick saves, and native save-folder archiving ([#88](https://github.com/Ahmed-Abousaif/Omnidroid/pull/88), [#83](https://github.com/Ahmed-Abousaif/Omnidroid/issues/83)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@Deus0](https://github.com/Deus0))
- Added Sega 32X emulation via PicoDrive (`.32x`, `.bin`, `.gen`, `.md`, `.smd`, and `.zip`) with two 6-button controller ports, fixed SMD bank checksums, and made PicoDrive selectable alongside Genesis Plus GX for Genesis and Sega CD ([#93](https://github.com/Ahmed-Abousaif/Omnidroid/pull/93), [#89](https://github.com/Ahmed-Abousaif/Omnidroid/issues/89)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@102hedgehog](https://github.com/102hedgehog))
- Automatically installs and verifies required Europe, Japan, and USA Sega CD BIOS files through core support assets so firmware survives rescans and repairs missing or corrupt copies from cache ([#92](https://github.com/Ahmed-Abousaif/Omnidroid/pull/92), [#91](https://github.com/Ahmed-Abousaif/Omnidroid/issues/91)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Added local-first metadata and artwork enrichment with IGDB fallback via `omnidroid.site` when local databases and Libretro thumbnails have no match, persisting accepted matches and cover art across rescans and restarts ([#90](https://github.com/Ahmed-Abousaif/Omnidroid/pull/90)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Always fall back to the official `libretro-thumbnails` GitHub repository when `thumbnails.libretro.com` fails or misses before consulting IGDB ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Preserved manual and curated game matches across library rescans ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Fixed a crash when choosing a custom save folder ([#79](https://github.com/Ahmed-Abousaif/Omnidroid/issues/79)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@raycke1234-spec](https://github.com/raycke1234-spec))
- Organized custom save folder saves (`saves/<Console>/...`), save states (`states/<Console>/<core>/...`), and state previews (`state-previews/<Console>/<core>/...`) into per-console subfolders while keeping existing legacy saves compatible and safely migrated ([#94](https://github.com/Ahmed-Abousaif/Omnidroid/pull/94), [#84](https://github.com/Ahmed-Abousaif/Omnidroid/issues/84)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@NinFanBoyFTW](https://github.com/NinFanBoyFTW))
- Enabled all four controller ports for Nintendo 64 games so multiplayer works with two or more physical controllers ([#86](https://github.com/Ahmed-Abousaif/Omnidroid/issues/86)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif), [@raycke1234-spec](https://github.com/raycke1234-spec))
- Added a "Reinstall app" option in the GitHub updater dialog when already on the latest release ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Added per-controller and per-orientation virtual pad opacity control in the touch-controls editor with global controller opacity fallback ([#85](https://github.com/Ahmed-Abousaif/Omnidroid/pull/85)) ([@SykikXO](https://github.com/SykikXO), [@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Highlighted controller-focused games in Library Grid and List views with the Console view's accent selection glow and outline, drawing above neighboring items ([#81](https://github.com/Ahmed-Abousaif/Omnidroid/pull/81)) ([@mlnchlc](https://github.com/mlnchlc))
- Moved controller focus into open game and library menus (including touch-opened menus on first D-pad press) and kept D-pad navigation inside until Back or Close ([#82](https://github.com/Ahmed-Abousaif/Omnidroid/pull/82)) ([@mlnchlc](https://github.com/mlnchlc))
- Prevented Settings slider rows from retaining a focus highlight in touch mode and kept focus on the row instead of the inner slider for clean D-pad navigation ([#80](https://github.com/Ahmed-Abousaif/Omnidroid/pull/80)) ([@mlnchlc](https://github.com/mlnchlc))

### v3.1.0

- Upgraded on-screen touch controls to Omnidroid PadKit for smoother, more reliable touch gameplay
- Fixed virtual buttons and analog sticks getting stuck when lifting fingers, cancelling touches, changing settings, or turning off tilt controls
- Improved touch vibration feedback with live intensity adjustment, instant muting, and broader compatibility across older devices
- Fixed analog-style D-pads to immediately follow 4-way, 8-way, and dead-zone settings while held, providing smoother and more accurate directional control
- Streamlined the touch control library for faster performance, lighter resource usage, and full compatibility with Android 7.0 and up
- Optimized touch input processing to eliminate input lag, prevent dropped presses, and improve battery life during gameplay
- Significantly reduced memory usage and stutter when holding multiple buttons or using multi-finger touch controls ([PadKit performance details](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/blob/da4a1c12a69eeb744217624c4ee7496b07a12700/README.md#performance-results))
- Improved on-screen button layouts and touch hit detection, preventing missed touches when sliding between overlapping buttons
- Added extensive automated testing for touch, tilt, and button inputs across supported Android versions for greater overall stability
- Redesigned the game loading screen with game cover art, console name, and a sleek animated progress bar
- Fixed core download progress bars glitching or resetting when rotating the device
- Gamepad shortcuts for Quick Save, Quick Load, and Fast Forward are now visible in Settings even when unassigned, making them easier to discover and configure ([#40](https://github.com/Ahmed-Abousaif/Omnidroid/pull/40)) ([@mlnchlc](https://github.com/mlnchlc))
- Added a 4-way screen layout toggle for Nintendo DS and 3DS (dual, swapped dual, top only, bottom only) with a configurable gamepad shortcut, keeping your layout intact across screen rotations ([#41](https://github.com/Ahmed-Abousaif/Omnidroid/pull/41)) ([@mlnchlc](https://github.com/mlnchlc))
- DS and 3DS single-screen modes now fill the screen in landscape, and the touchscreen stays responsive even while holding on-screen buttons ([#42](https://github.com/Ahmed-Abousaif/Omnidroid/pull/42)) ([@mlnchlc](https://github.com/mlnchlc))
- Removed the dark background panels behind DS and 3DS controls in landscape for an unobstructed view of the game ([#43](https://github.com/Ahmed-Abousaif/Omnidroid/pull/43)) ([@mlnchlc](https://github.com/mlnchlc))
- Fixed visual glitches and corrupted frames in Vulkan games when changing screen orientation or resolution ([#44](https://github.com/Ahmed-Abousaif/Omnidroid/pull/44)) ([@mlnchlc](https://github.com/mlnchlc))
- Changing the graphics renderer during gameplay now safely takes effect on the next launch with a notice, avoiding crashes and protecting your unsaved progress ([#45](https://github.com/Ahmed-Abousaif/Omnidroid/pull/45)) ([@mlnchlc](https://github.com/mlnchlc))
- Fixed build errors caused by duplicate runtime libraries and missing audio build templates ([#39](https://github.com/Ahmed-Abousaif/Omnidroid/pull/39), [#44](https://github.com/Ahmed-Abousaif/Omnidroid/pull/44)) ([@mlnchlc](https://github.com/mlnchlc))
- Fixed controller navigation issues in the game and library side menus ([#50](https://github.com/Ahmed-Abousaif/Omnidroid/pull/50)) ([@mlnchlc](https://github.com/mlnchlc))
- Stopped gamepad shortcut notifications repeatedly appearing after rotating the screen ([#51](https://github.com/Ahmed-Abousaif/Omnidroid/pull/51)) ([@mlnchlc](https://github.com/mlnchlc))
- Restored automated code quality checks in development builds ([#48](https://github.com/Ahmed-Abousaif/Omnidroid/pull/48)) ([@mlnchlc](https://github.com/mlnchlc))
- Added confirmation dialogs before restarting a game, loading a state, or overwriting a save, with empty slot indicators and an option to disable prompts in Saves settings ([#53](https://github.com/Ahmed-Abousaif/Omnidroid/pull/53)) ([@mlnchlc](https://github.com/mlnchlc))
- Enhanced gamepad mapping with button conflict options (Swap, Replace, Cancel), single-key shortcuts, clear buttons, reset confirmations, and disconnected controller notices ([#54](https://github.com/Ahmed-Abousaif/Omnidroid/pull/54)) ([@mlnchlc](https://github.com/mlnchlc))
- Improved game cover art display: custom covers keep their original proportions without cropping, with sharper images in the carousel and high-resolution art in Game Details ([#55](https://github.com/Ahmed-Abousaif/Omnidroid/pull/55)) ([@mlnchlc](https://github.com/mlnchlc))
- Added helpful empty-library screens with quick-action buttons when no games, folders, favorites, or search results are found, and added cover management directly to Game Details ([#56](https://github.com/Ahmed-Abousaif/Omnidroid/pull/56)) ([@mlnchlc](https://github.com/mlnchlc))
- Cleaned up Settings UI: clearer reset warnings, visible numbers on sliders (tilt sensitivity, graphics quality), and visual fixes for Light Mode ([#57](https://github.com/Ahmed-Abousaif/Omnidroid/pull/57)) ([@mlnchlc](https://github.com/mlnchlc))
- Improved readability and accessibility with higher-contrast text, cleaner Light Mode accents, TalkBack screen reader labels, and adaptive controller hint bars ([#58](https://github.com/Ahmed-Abousaif/Omnidroid/pull/58)) ([@mlnchlc](https://github.com/mlnchlc))
- Made library browsing noticeably faster and smoother by preloading cover art and reducing disk access while scrolling ([#59](https://github.com/Ahmed-Abousaif/Omnidroid/pull/59)) ([@mlnchlc](https://github.com/mlnchlc))
- Added custom save folder support in Saves settings to store in-game saves and save states in any local or network/cloud folder, with safe data migration and offline backup sync ([#65](https://github.com/Ahmed-Abousaif/Omnidroid/pull/65), [#61](https://github.com/Ahmed-Abousaif/Omnidroid/issues/61)) ([@Ahmed-Abousaif](https://github.com/Ahmed-Abousaif))
- Added an option in Library settings to disable automatic library scanning when launching the app or returning from a game, speeding up launch times for large collections ([#63](https://github.com/Ahmed-Abousaif/Omnidroid/pull/63)) ([@SykikXO](https://github.com/SykikXO))
- Improved physical controller responsiveness: analog stick tilts register cleanly one step at a time, holding shoulder buttons or sticks scrolls continuously, and menu focus remains stable ([#60](https://github.com/Ahmed-Abousaif/Omnidroid/pull/60)) ([@mlnchlc](https://github.com/mlnchlc))
- Expanded button scaling in Display settings from 80% up to 140%, letting you shrink buttons for more screen room or enlarge them for easier tapping ([#66](https://github.com/Ahmed-Abousaif/Omnidroid/pull/66)) ([@mlnchlc](https://github.com/mlnchlc))
- Added an option in Library settings to place the console bar at the bottom in landscape mode (Switch-style layout), with responsive adjustments to keep titles and view buttons clearly visible ([#67](https://github.com/Ahmed-Abousaif/Omnidroid/pull/67)) ([@mlnchlc](https://github.com/mlnchlc), _u/calm_drink_)
- Added an Accent Color setting in Display settings with 9 vibrant color choices that theme highlights, focus rings, switches, sliders, and battery indicators ([#68](https://github.com/Ahmed-Abousaif/Omnidroid/pull/68)) ([@mlnchlc](https://github.com/mlnchlc))
- Replaced multi-step popup dialogs with one-tap inline choices for quick settings like Theme, Display Filter, and Touch Feedback ([#70](https://github.com/Ahmed-Abousaif/Omnidroid/pull/70)) ([@mlnchlc](https://github.com/mlnchlc))
- Added an on-screen gamepad color option in Controls settings to match virtual controller buttons to your chosen accent color while keeping clear transparency ([#71](https://github.com/Ahmed-Abousaif/Omnidroid/pull/71)) ([@mlnchlc](https://github.com/mlnchlc))
- In-game menus and game detail menus now open from the bottom as convenient bottom sheets in portrait mode, keeping the game visible above ([#73](https://github.com/Ahmed-Abousaif/Omnidroid/pull/73)) ([@mlnchlc](https://github.com/mlnchlc))
- Refined controller focus rings to draw cleanly inside buttons and cards, preventing outlines from being cut off by screen edges or adjacent cards ([#74](https://github.com/Ahmed-Abousaif/Omnidroid/pull/74)) ([@mlnchlc](https://github.com/mlnchlc))
- Added TV safe-area margins on Android TV and Google TV to prevent screen edges, the top bar, and the console bar from being cut off by display overscan ([#75](https://github.com/Ahmed-Abousaif/Omnidroid/pull/75)) ([@mlnchlc](https://github.com/mlnchlc))
- Improved controller navigation in Settings: opening a section lands directly on the active tab, and Up/Down movement stays within the current page in landscape ([#77](https://github.com/Ahmed-Abousaif/Omnidroid/pull/77)) ([@mlnchlc](https://github.com/mlnchlc))
- Fixed controller focus highlights on slider rows in Settings so it's always clear which slider is currently selected ([#78](https://github.com/Ahmed-Abousaif/Omnidroid/pull/78)) ([@mlnchlc](https://github.com/mlnchlc))
- Added an official Discord community link to the About screen and README
- Polished landscape carousel card sizes and spacing to make game titles and details clearer

### v3.0.0

- Rebuilt extra-display detection and output for dual-screen handhelds and compatible TVs, with an Omnidroid welcome screen, per-display controls, safe lifecycle handling, and a lightweight single-screen path that starts the display coordinator only when needed
- Rebuild the Consoles page to be the hub for managing installed emulators and game databases and assets and to help manage/update them
- Updated Nintendo Switch emulation to include Eden's latest fixes and improvements
- Added more save slots per game and an Autosave option in the Load State menu, where save states are supported
- Redesigned Wii U (Cemu) dual view in landscape with the TV above a centered GamePad at half the width and height; the on-screen button toggles directly between TV and dual view, with touch mapped to the displayed GamePad
- Fixed duplicated TV/GamePad output for Wii U by preserving separate screen images, and added automatic phone keyboard support for in-game text entry, editing, and Done confirmation
- Added button UI scaling for the library interface in Settings > Display, featuring a live mock library preview that scales the console, library, and favorites buttons, the view mode toggle, search bar, and top utility buttons up to 25% larger across 5 discrete increments
- Added clearer save/load errors and options to retry or quit without saving if a save fails
- Fixed Library crashing (tested 100,000 games) by Rebuilding Grid/List with bounded paging and correctly sized covers; fixed paging-key collisions and Continue-cover layout loops
- Redesigned the profile with divider-based sections, a compact landscape rail, a denser console grid, and day-grouped session history with game-detail shortcuts
- Standardized Settings and both side menus with reusable components and nested navigation; removed duplicate code, simplified Appearance actions, and matched dark-mode menu backgrounds
- **Overhauled Vulkan Rendering Pipeline**:
  - **Asynchronous Presentation**: Decoupled presentation to a background worker thread, running CPU emulation and GPU presents in parallel for higher sustained FPS in heavy 3D titles (PS2, GameCube/Wii, Wii U, 3DS, PSP)
  - **Eliminated VSYNC Stalls**: Removed presentation lock contention, preventing background GPU worker threads (Dolphin Video, PS2 MTGS, Cemu Latte) from freezing while waiting on Android VSYNC
  - **Massive Gains for ARM Mali & Immortalis**: Drastically boosts performance on MediaTek Dimensity, Google Tensor, and Exynos devices by eliminating Mali's 30 FPS VSYNC lock, bypassing costly on-chip tile clears, and unlocking native ARM in-tile blending
  - **Hardware-Accelerated Core Features**: Enabled native GPU blending, queries, and texture extensions (`dualSrcBlend`, `logicOp`, `geometryShader`, `wideLines`, BC/ASTC compression), unlocking Dolphin TEV blending & bounding boxes, PPSSPP wide lines, and PS2 alpha effects
  - **Cooler Gaming & Battery Savings**: Suppressed duplicate frame draws on 30 FPS games running on 60Hz/120Hz screens, cutting up to 50% wasted GPU workloads and thermal throttling across all 3D consoles
  - **Mobile GPU Bandwidth Optimizations**: Added dynamic rendering with tile memory bypass and Qualcomm UBWC texture decompression for lower power draw on Adreno and Mali SoCs
  - **Stutter-Free Push Descriptors**: Switched to native Vulkan push descriptors, eliminating CPU memory allocations and micro-stutters during frequent render target and resolution switching
  - **VRR & LTPO Display Matching**: Dynamically syncs variable refresh rate screens (Samsung, Pixel, OnePlus) directly to native game refresh rates (50Hz PAL, 60Hz NTSC, 30Hz)

### v2.6.5

- Removed the library rotation lock button; the app now rotates automatically and supports full flipping
- Added in-app update checker: Play flexible updates for the Play build, and GitHub Releases download/install for the free build (_u/ClearWolverine7_)
- Reordered Settings for clearer mobile and TV/controller use: Library-first with maintenance tools, Emulation vs Advanced split, and Controls grouped for on-screen, rumble, and gamepads
- Fixed Wii U (Cemu) controls dropping under heavy 3D load by shipping Cemu libretro v0.1.1 with thread-safe input and non-blocking Vulkan present

### v2.6.0

- Added Nintendo Switch emulation support via Eden with native Vulkan acceleration, docked/handheld modes, resolution scaling, in-game asynchronous shaders toggle, touch/gamepad controls, and prod.keys & optional system firmware import
- Added multi-tier save and state migration from Lemuroid: automatic 1-tap migration when accessible on device (with live save/state count detection), uncompressed folder import via SAF, lenient ZIP backup import, and in-app migration guide (_u/Kwametoure1_)
- Unified Saves & Backup settings across all builds (GitHub free and Google Play), adding local storage calculation, conflict resolution, and SAF folder import
- Added frontend intent support to launch games directly from frontends like Daijishō, Pegasus, and Beacon (ES-DE WIP)
- Added "Open with" file manager support for ROM files and zip archives, with automatic console detection and instant booting without prior library scanning
- Added Android TV Leanback launcher integration and home screen preview channels for recently played games
- Added a console library view, with a cover carousel in portrait and the selected game's cover, title, and start actions in landscape ([#17](https://github.com/Ahmed-Abousaif/Omnidroid/issues/17)) (_Deus0_)
- Added library sorting by name, console, recently played, and favorites, plus a button to play a random game
- Added light mode with a cool gray chrome theme; switch under Display settings (library, settings, profile, and game menus follow the theme; in-game view and gamepad binding stay dark) ([#33](https://github.com/Ahmed-Abousaif/Omnidroid/issues/33)) (_Deus0_)
- Fixed GameCube and Wii `.rvz` games crashing on launch by opening them from a real file instead of the virtual filesystem ([#19](https://github.com/Ahmed-Abousaif/Omnidroid/issues/19)) (_Deus0_)
- Fixed PlayStation 2 BIOS downloads not installing, and a failed download is shown in BIOS settings
- Fixed the d-pad analog emulation up-down flipped issue (_u/Separate-Bluebird30_)
- The game settings sidebar shows the full title and the cover without cropping
- Library chrome and in-game controls sit closer to display cutouts
- Removed RAWG support given it's not a method that fits the goals of this app

### v2.5.4

- Fixed missing box art when a console database name does not match the Libretro thumbnail filename ([#26](https://github.com/Ahmed-Abousaif/Omnidroid/issues/26)) (_Deus0_)
- PlayStation 2 BIOS follows the game region, and USA, Europe, or Japan can be chosen in settings ([#25](https://github.com/Ahmed-Abousaif/Omnidroid/issues/25)) (_Deus0_)
- Fixed Nintendo DS and 3DS game screen size in landscape ([#28](https://github.com/Ahmed-Abousaif/Omnidroid/issues/28)) (_Deus0_)
- Fixed Atari 7800 and PC Engine games not being detected, because `.a78` and `.pce` files were skipped during scanning ([#29](https://github.com/Ahmed-Abousaif/Omnidroid/issues/29)) (_Deus0_)

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
- DOS (DOSBox Pure), Pico-8, and Sega Saturn (_u/milosmisic89_, _u/Kwametoure1_)
- PSP custom texture support (needed for some English patches) (_u/DragonBane52_)
- SNES widescreen patches (_u/Le_Sairo_)
- Rewind (fast-forward already available) (_u/bboy_3431_)

### Controls & display

- Better touchscreen customization overall (_u/Repulsive_Cow_2470_)
- In-game screen size (_u/GhiStale_)

### Library & UI

- Bulk import of RetroArch game covers (migration feature planned) (_u/JungleRollers_)

### Platform & distribution

- Donation option in the Play Store build (support links already exist on GitHub and in About) (_u/Tall-Average5330_)

---

## Known issues
