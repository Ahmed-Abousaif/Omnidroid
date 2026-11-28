# Additional libretro systems

Omnidroid includes these systems in its console picker and bundled APKs:

- Atari 5200: a5200
- Atari 400/800/XL/XE: Atari800 (800XL by default; change the model in Core Settings)
- Sega Saturn: YabaSanshiro
- 3DO: Opera
- ColecoVision: Gearcoleco
- Commodore 64: VICE x64 (fast)
- MSX and MSX2: fMSX, with separate machine defaults, firmware checks and catalogs
- Apple II: AppleWin
- Atari Jaguar cartridges: Virtual Jaguar

The core updater and Play dynamic-feature modules include all nine cores for arm64-v8a, armeabi-v7a, x86 and x86_64. On-demand free builds download the new cores from the official Libretro Android buildbot. Bundled builds include the binaries and catalogs locally. Existing cores keep their current download source.

## Firmware

Saturn, ColecoVision and fMSX firmware installs automatically with the selected core, using the verified ZIPs in `omnidroid-cores/assets`. Builds include these three small archives for offline installation; the asset manager also supports downloads from the OmnidroidCores master branch after the archives are published there. Missing or corrupted managed files are repaired from the verified cache, and valid managed copies survive library rescans. Removing a console deletes only copies installed by Omnidroid; existing imports and later user replacements are preserved.

Manual imports remain supported through the BIOS workflow or a scanned folder. Names are matched without regard to case and staged under their canonical filenames.

- `saturn.zip` supplies `saturn_bios.bin`.
- `colecovision.zip` supplies `colecovision.rom`.
- `msx.zip` supplies `MSX.ROM`, `MSX2.ROM`, `MSX2EXT.ROM`, `DISK.ROM`, `FMPAC.ROM`, `MSXDOS2.ROM`, `PAINTER.ROM` and `KANJI.ROM` for both MSX systems.
- MSX2 still validates that both its system ROM and extension ROM are present before launch.
- a5200 defaults to its internal Altirra OS. Import `5200.rom` and select Official in Core Settings to use the original firmware.
- Atari800 uses its built-in replacement firmware when original ROMs are absent. Original Atari OS/BASIC ROMs can be imported.
- Opera defaults to its embedded Opera Boot ROM. Original Panasonic, Goldstar and Sanyo BIOS images and Japanese font ROMs can be imported and selected in Core Settings.
- VICE x64, AppleWin and Virtual Jaguar do not require external firmware for the configured machines.

Core option availability follows the downloaded binaries. Restart emulation after changing an option marked for restart.

## Importing games

Distinct suffixes such as `.a52`, `.atr`, `.col`, `.d64`, `.mx1`, `.mx2`, `.woz` and `.j64` identify their systems automatically. Saturn and 3DO disc headers are recognized in ISO, CUE/BIN and CHD images. Saturn CloneCD (`.ccd` with `.img`/`.sub`) and Media Descriptor (`.mds` with `.mdf`) files retain their sibling files during import.

Put ambiguous formats in console folders: `atari5200`, `atari800`, `saturn`, `3do`, `colecovision`, `c64`, `msx`, `msx2`, `apple2` or `jaguar`. In particular, `.rom`, `.dsk`, `.cas`, `.nib` and `.prg` are shared by multiple machines. External frontends can supply the same names in their system hint. `.mx1` and `.mx2` remain authoritative inside generic MSX folders.

Single-file ROMs in ZIP archives retain the console folder hint and their archive member name. Extract multi-file disk sets before importing them. Keep playlist entries and their companion files together; existing M3U disk switching is available for Atari800, VICE x64, fMSX and AppleWin.

Game catalogs ship for all ten systems. Precomputed artwork paths are included where the upstream thumbnail index is available; an unavailable art index does not prevent catalog generation or emulation.

## Controls

Each new system has a touch profile with its core's button labels. Atari 5200, ColecoVision and Jaguar also have a Keypad panel exposing every number, star and hash key. Atari800, C64, MSX/MSX2 and Apple II have a Keyboard panel and accept physical keyboards. Keyboard events support both libretro polling and callbacks, with callbacks delivered on the emulation thread.

fMSX reads keyboard input only in its Keyboard controller mode. Opening its keyboard panel or typing on a physical keyboard selects that mode; closing the panel or pressing a gamepad/touch action button restores Joystick + Emulated Keyboard. Player 2 retains the normal joystick device.

## Maintaining binaries and catalogs

Run the core updater with `--cores a5200,atari800,yabasanshiro,opera,gearcoleco,vice_x64,fmsx,applewin,virtualjaguar` to refresh the binaries. Run `:libretro-db-builder:buildLegacySystems` to regenerate only these catalogs while preserving existing slices and accepted catalog seeds. The full database builder also includes these systems. New assets use `.gzip` suffixes so Android preserves their verified compressed bytes.

The native keyboard changes live in the `omni-libretrodroid` submodule; core modules and catalog tooling live in `omnidroid-cores`. The parent pins track the published submodule revisions. The fallback release AAR is rebuilt from the pinned native source, and its sidecar records that revision and the artifact checksum.

### Firmware packages

`python omnidroid-cores/package_firmware.py` regenerates the three archives from the pinned sources in `omnidroid-cores/firmware-packages.json`. Downloads must match the configured byte lengths and Libretro BIOS MD5 checksums before any archives are replaced. `python omnidroid-cores/package_firmware.py --verify` checks the archives offline. `omnidroid-cores/assets/firmware-sources.json` records every immutable source URL, per-file SHA-256 and archive SHA-256. The MSX system BIOS uses the documented VG-8020 revision; the other MSX ROMs come from the fMSX core source tree.
