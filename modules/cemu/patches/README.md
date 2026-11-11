# Cemu Patch Series

Any patches required for upstream `cemu-libretro-android` live in this directory as numbered git patches:
e.g. `0001-description.patch`.

The upstream updater scripts update the submodule revision; they do not apply these
patches. Apply pending fixes in a separate upstream checkout when rebuilding the
core, keeping Omnidroid's upstream submodule clean.

## Pending upstream fixes

`0001-snapshot-tv-and-gamepad-scanouts.patch` (base: `55ca7def98ce71d4477df84ee8aa42802d238320`)
copies each TV/DRC scanout into an owned Vulkan image. Games may reuse a render
target between TV and GamePad submissions; caching texture pointers caused both
halves of the side-by-side view to show the same latest image.

From a separate `cemu-libretro-android` checkout at the base revision:

```powershell
git apply D:/Work/Omnidroid/modules/cemu/patches/0001-snapshot-tv-and-gamepad-scanouts.patch
python tests/libretro/test_scanout.py
cmake --build build_libretro_android_arm64 --target cemu_libretro
```

The regression harness needs a host C++20 compiler (`cl` in a Visual Studio
developer shell on Windows, or `$CXX` / `c++` elsewhere). It executes the production
compositor with CPU-backed Vulkan blits and covers a shared source texture, both
submission orders, switching views, absent DRC output, texture release, and resize.
It does not validate Vulkan driver synchronization or replace an on-device game
test. Strip and copy the built ARM64 core into
`omnidroid-cores/omnidroid_core_cemu/src/main/jniLibs/arm64-v8a/libcemu_libretro_android.so`.

Once the fix lands upstream, update the submodule and remove the corresponding patch.

`0002-use-frontend-software-keyboard.patch` uses Cemu's software-keyboard
requests to open the phone IME through Omnidroid's private libretro text-input
interface. Apply it after patch 0001 in the separate upstream checkout and rebuild
the core. It requires the matching frontend and Android app changes; older
frontends retain Cemu's existing keyboard behavior.

The ABI header `src/libretro/omni_text_input.h` must match
`omni-libretrodroid/src/main/cpp/textinput/omni_text_input.h`. Text and limits use
UTF-16 units. The frontend copies requests and queues complete text replacements
with a session ID; Cemu consumes edits from `retro_run`, updates the game's
receiver, and handles Done as its keyboard confirmation. Closing the Android
editor preserves the pending request; touching the game reopens it. Requests are
cleared when the game closes its keyboard or the core unloads.

The frontend mailbox regression test covers editing, deletion, Unicode, length
limits, confirmation, stale sessions, teardown, and concurrent polling. From a
Visual Studio developer shell in Omnidroid:

```powershell
cl /nologo /std:c++17 /EHsc omni-libretrodroid/tests/textinput_test.cpp /Fo:build/textinput_test.obj /Fe:build/textinput_test.exe
build/textinput_test.exe
```

`0003-stack-landscape-displays-and-map-gamepad-touch.patch` replaces landscape
dual view with a 1280x1080 canvas: TV at 1280x720 on top and a centered 640x360
GamePad below. The renderer and native pointer mapping use the same screen
rectangles. TV and black margins reject GamePad touches. Portrait keeps the
existing horizontal arrangement. The old `side_by_side` screen-view token remains
compatible and is displayed as "Dual View"; `cemu_dual_screen_layout` is selected
by the frontend for the current orientation.

The Android frontend marks Wii U as touch-capable, toggles TV/dual without the
GamePad-only stop, and captures touches only over the displayed GamePad. It
releases the emulated pen when that region disappears or a gesture is cancelled.
GamePad-only view remains available in core settings.

Apply patches in numeric order. Patch 0003 extends the compositor regression test
to check stacking, black margins, and rotation, and adds a standalone native
geometry/touch test:

```powershell
cl /nologo /std:c++20 /EHsc tests/libretro/screen_layout_test.cpp /Fo:build_libretro_android_arm64/screen_layout_test.obj /Fe:build_libretro_android_arm64/screen_layout_test.exe
build_libretro_android_arm64/screen_layout_test.exe
python tests/libretro/test_scanout.py
```

The Android layout tests are `CemuScreenLayoutTest`, run with
`:omnidroid-app:testFreeBundleDebugUnitTest` from the Omnidroid checkout.

