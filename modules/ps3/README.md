# ARMSX3 PS3 backend

## Engine-only architecture

Omnidroid links ARMSX3's pinned RPCS3 emulation engine through an
Omnidroid-owned platform host. It does not build or link the upstream Android
application host, XMB player, remote play, HomeMenu or BigPicture sources.
`engine/prepare_sources.py` removes their pad/shell entry points from generated
platform sources without changing the pinned source checkout.

`libomnidroid-armsx3.so` contains the emulation engine, Omnidroid's native host and
JNI adapter in one shared library. The adapter calls the internal versioned C
contract directly; no second library or `dlopen` is involved. Only Omnidroid's
JNI methods are exported. The internal contract retains caller-owned UTF-8
buffers, file descriptors, input/window handles and explicit callbacks for
storage, installation and guest dialogs. No JVM classes or C++ standard-library
objects cross that internal contract.

PS3 game-requested message boxes, text input and save-data selection use
Omnidroid's shared dialog components. Those guest services are separate from
emulator settings and snapshot slots, which use the normal Omnidroid screens.
No upstream frontend icons, Android activities or Java callback namespaces are
packaged. Source, exported-symbol and APK/Dex checks enforce this boundary.

The replacement has been built from source and tested on the connected phone.
Historical prototype results are retained separately below.

## Build

On Linux, install the Android SDK, NDK `29.0.14206865`, CMake `3.30.5`, Ninja,
Python 3, a C/C++ toolchain, nasm/yasm, autoconf, automake, libtool and pkg-config.
Set `ANDROID_HOME`, initialize the parent submodules, then run:

```sh
python3 omnidroid-cores/build_armsx3.py --jobs 2
```

The builder initializes the engine's pinned dependencies, builds its LLVM/FFmpeg
dependencies and emulator, and compiles `modules/ps3/bridge` directly into the
same target with the static C++ runtime. It stages the AArch64 library into
`omnidroid-cores/omnidroid_core_armsx3/src/main/jniLibs/arm64-v8a` and records source,
toolchain, library filename and SHA-256 hash in `src/main/armsx3-build.json`.

Upstream Actions application-host binaries are not accepted as a build fallback.
Windows development uses the Linux Docker builder in `engine/Dockerfile`, with
the Linux Android SDK/NDK and sources held in a persistent volume.

## Runtime and data

The initial baseline is Android 13+, ARM64, Vulkan, and the CPU features of the
pinned ARMv8.2 build. The Android host checks CPU features before loading the
merged library; JNI also verifies the native capability flags.
The initial shader mode uses the asynchronous recompiler to avoid the shader
interpreter's large first-run Vulkan compilation. The host renders guest dialogs without upstream overlay assets.

Data lives under `files/ps3`:

- `config/dev_flash`: installed firmware.
- `config/dev_hdd0/game`: installed games, updates and DLC.
- `config/dev_hdd0/home/00000001/savedata`: native game saves.
- `config/dev_hdd0/home/00000001/exdata`: imported RAP licenses.
- `config/data/redump`: imported disc keys.
- `cache`: rebuildable emulator caches.

Firmware and PKGs use the engine's installer and progress callbacks. A file lock
prevents an installer in the main app process from modifying data used by a game
process. ISO and extracted-folder discovery reads bounded PARAM.SFO metadata;
each extracted game is one library entry. SAF games are accessed through the
engine's storage device and the app's persisted tree grants.

Native save folders are backed up through Omnidroid's existing save storage using
`ps3-TITLEID.srm` archives. Restore validates paths and preserves newer native
files. Rewind, fast-forward, external shaders and rumble remain disabled.

## Host menu, graphics and snapshots

The menu control belongs to Omnidroid. Touch menu events are consumed by the host,
the PS3 pad mapper rejects Home, and the JNI adapter masks out the PS button bit.
The engine library contains no home-menu implementation or legacy menu export. Native game dialogs, such as
save-data selection and the keyboard, remain available.

The normal Omnidroid settings screens expose six common graphics controls:
resolution scale, frame limit, VSync, anisotropic filtering, output scaling and
FSR sharpening. Advanced contains shader compilation/precision, anti-aliasing,
and six rendering compatibility switches. CPU, audio, debug and system settings
are not exposed. Restart-only controls are labeled and applied on the next boot.

Manual save/load slots and quick-save shortcuts use the engine's numbered snapshot
API through Omnidroid's existing menus. Snapshot files remain under
`config/savestates/TITLEID/armsx3_slots`, with metadata read directly for the slot
list. They are streamed by the engine rather than loaded into Java byte arrays.
Capture writes a private staging slot before replacing a selected slot, and the
host retains the native data lock across the save/load restart. Previews use
Omnidroid's existing preview storage. Quick and staging slots are excluded from
the visible list. Automatic VM snapshots are disabled; native in-game save backups
continue independently. VM snapshots are local and are not part of the native
save-folder archive/cloud transport.

## Performance policy

Android performance hints are always enabled, with power-efficiency preference
explicitly disabled when the system API supports it. The native RSX flip hook
reports each valid frame directly to Android without per-frame JNI calls. Hint
sessions close on pause/stop and recreate for the new RSX thread after restart or
snapshot load. Invalid/long-gap samples are skipped; unavailable or failed hint
services back off without stopping emulation. Debug instrumentation can temporarily
change hints/logging for controlled comparisons; there is no user-facing toggle.

The host reports the actual Android display refresh rate and listens for changes,
instead of assuming 60 Hz. Routine notice/trace logs are suppressed by default;
errors, warnings and important always-level diagnostics remain available. CPU/SPU
scheduler settings and game-speed limits retain their existing compatibility
baseline rather than adopting unmeasured aggressive overrides.

Snapshot-compatible SPU compilation remains enabled. It changes emitted code and
cache identity, so toggling a setting at capture time cannot make existing fast
code save-safe. A dynamic design would need generated checkpoints and a coordinated
SPU save barrier. The current implementation does not claim that capability.

The generated engine adaptations also fix snapshot preparation for titles with no
SPU threads: an empty list proceeds through the existing suspension/race checks
instead of waiting for a nonexistent save-safe SPU. Genuine capture rejections are
reported to Omnidroid promptly, without waiting for the wrapper's full timeout.

A GTA San Andreas comparison used four 20-second samples in baseline/optimized/
optimized/baseline order after startup, with CJ stationary in the starting alley.
Both modes stayed near 29.85 valid RSX flips/s. Mean sampled RSX work was about
6.80 ms baseline and 6.66 ms optimized; this small difference is not evidence of a
repeatable speedup. The optimized windows submitted 1,194 Android hint reports
without errors. Battery temperature stayed at 29.9 C and Android reported no
thermal throttling. Timing samples exclude invalid counter resets/long gaps and
are not a complete simulation-speed or frame-pacing benchmark.

The performance-enabled build passed GTA and Minecraft rendering, pause/resume,
numbered snapshot save/load, restart and shutdown on the Galaxy S24 Ultra. Native
hint lifecycle/error tests, all 297 Android unit tests, and the Play release bundle
passed. Debug APK and release-bundle inspection confirm a single frontend-free
PS3 library matching its recorded SHA-256 hash.

## Engine-only validation — 2026-11-22

The full Linux/Android source build passed with NDK 29, including a separate
configuration check of the staged source project. The source audit rejects the
upstream application host and menu targets. Inspection of the unstripped library
rejects linked HomeMenu, BigPicture and remote-play capture implementations;
public symbols are restricted to Omnidroid JNI entry points. APK/Dex inspection
and native SHA-256 comparison check the packaged library against its build.

All 296 Android unit tests passed, as did Python boundary/project tests, native
request-lifecycle/ABI tests and the Play release R8 check.

On the Galaxy S24 Ultra (Adreno 750, API 37), the engine-only package passed:

- Initialization and all 15 curated graphics controls.
- Reading the supplied Minecraft ISO and installing the supplied PS3 firmware.
- The host-dialog protocol: message confirmation, Unicode keyboard input,
  save-data selection and engine-originated dismissal without a user reply.
- Non-black Vulkan output, pause/resume, numbered snapshot save/load, restart
  and clean shutdown. The legacy UI icon directory was absent from its runtime
  path throughout this test.
- The actual app game screen: touch menu, Omnidroid save-slot listing, curated
  graphics settings and normal quit releasing the cross-process data lock.

The dialog protocol test drives the Android host directly; it does not establish
compatibility with every game's keyboard/save-dialog usage. PKG installation,
license/key imports, external SAF providers, physical controllers, long gameplay
and native game save-folder backup/restore still need device validation.
The source build and debug APK are local; no release/download tag was published.

### Single-library packaging on 2026-11-22

The JNI adapter is now compiled into `libomnidroid-armsx3.so`; the separate engine
shared library and standalone adapter build have been removed. Downloads and core
metadata name only that one library. Downloads, lookup, uninstall and build staging
use a single file directly, without a library-list abstraction. The internal C interface remains for source
separation, with JNI as the only public native ABI. Source/symbol audits, six
Python boundary/project tests, five updater tests, native lifecycle/API tests,
all 296 Android unit tests and Play release R8 passed. The ARM64 debug APK contains
exactly one PS3 library, with its hash matching the verified build. The Play
dynamic-feature release bundle also passed the same packaging/hash audit. The merged
library is approximately 59.8 MB before APK compression. The merged debug APK was
installed on the Galaxy S24 Ultra and passed initialization, missing-game error
handling and shutdown. The gameplay results below precede this packaging change.

The PS3 face-button mapping now follows Omnidroid's shared PlayStation layout:
A is circle, B is cross, X is triangle and Y is square. This fixes swapped
cross/circle and triangle/square inputs. All four PS3 input regression tests passed.

### GTA San Andreas device check on 2026-11-22

The supplied USA disc (BLUS31584) passed native metadata detection and library
indexing on the same Galaxy S24 Ultra. The engine-only debug build completed its
initial code compilation and reached 3D gameplay with CJ on the BMX through
Omnidroid's normal game screen. The user confirmed that the game worked correctly.
The native log records a clean shutdown after the Omnidroid menu was used.

The run also exposed missing compilation-progress labels; these were added to
the host string mapping, rebuilt and installed for the gameplay check. Background
and lock-screen interruptions limit this run's usefulness as a sustained stability
or performance test; no numerical FPS or long-session compatibility is claimed.

## Historical prototype device checks

`Ps3NativeSmokeTest` contains an engine-loading test and optional tests using
user-provided firmware and games. Supply `ps3Firmware` or `ps3Game` as
instrumentation arguments with paths readable by the debug app. The rendered-game
test requires the phone to remain unlocked with the test activity in front.

The test captures `files/ps3-test/frame.png`, checks non-black Vulkan output,
and exercises pause, resume, restart, native save persistence and shutdown. Test game and
firmware files belong in ignored build/test directories, never in source control.

The app-level test indexes the selected ISO, launches the normal game screen,
captures `files/ps3-test/app-game-screen.png` and `app-game-later.png`, and checks
that normal quit releases the cross-process data lock.

### Validation on 2026-11-21

On a Galaxy S24 Ultra (Adreno 750, Android API 37), the pinned engine loaded,
installed and compiled the supplied firmware, probed the supplied Minecraft ISO,
and rendered through Omnidroid's normal PS3 game screen. The longer capture
reached Minecraft's world-creation screen, including its native keyboard dialog.
Pause, resume, restart and shutdown passed the connected-device lifecycle test.

Focused scanner/backend/save tests, the core updater's Python tests, the minified
Free release APK, and the Play dynamic-feature bundle passed. Both native libraries
were verified in the Play bundle. The device engine came from upstream Actions run
44898051491; the matching JNI bridge was built locally with NDK 29.

### Menu and snapshot validation on 2026-11-22

The same phone passed the graphics API check for all 15 curated controls and a
numbered snapshot save/load round trip, followed by restart and clean shutdown.
The captured Minecraft snapshot was approximately 26 MiB. The app-level touch test
opened Omnidroid's menu, displayed the native snapshot in its Load screen, and
opened the curated graphics page with the correct selected values. Regression
tests cover menu-button filtering, graphics preference types, slot enumeration,
and replacing a selected slot only after capture completes.

These historical results used the upstream application-host binary and are not
evidence for the engine-only boundary. The current source build, checks and
remaining validation limits are described above.
