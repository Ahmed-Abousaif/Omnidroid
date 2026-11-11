# Omnidroid settings — technical handoff

Current reference: 10 November 2026. Attach this file to a future chat about the app's main Settings screen. For the in-game or library per-game side menus, attach [SIDE_MENUS_TECHNICAL_GUIDE.md](D:/Work/Omnidroid/docs/SIDE_MENUS_TECHNICAL_GUIDE.md) instead, or attach both when changing shared rows.

This describes the implemented code, not a proposed architecture. Read the current sources before editing: this checkout can contain staged and unstaged work from other tasks. Use this guide and the live source as the reference for future changes.

## 1. Scope and architecture

Main Settings has seven categories: Library, Display, Controls, Saves, Emulation, Advanced and About. Landscape uses a category sidebar; portrait uses a horizontal category menu. Detail pages are embedded inside that content pane. The full-screen UI-scale preview is an intentional exception.

There are four ownership layers:

1. The settings host owns category selection, detail navigation, adaptive layout and controller focus.
2. Section/detail composables own presentation and delegate events to their feature actions/view models.
3. Shared settings components own row/group/dialog rendering.
4. Preference bindings and existing managers/workers own storage, runtime reads and side effects.

Do not add database/file operations to the shell or create another preference listener implementation for a new row.

## 2. File map

The application Kotlin root is `D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app`.

- [MainActivity.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainActivity.kt): outer app routes, host dependencies, padding and UI-scale presentation.
- [SettingsScreen.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsScreen.kt): active category/path, adaptive content assembly, Back and controller effects.
- [SettingsCategories.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsCategories.kt): ordered `SettingsSection`, detail `SettingsSubscreen`, localized title IDs and `LocalSettingsNavigation`.
- [SettingsBackStack.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsBackStack.kt): immutable `current`, `parent`, `push` and `pop` path operations.
- [SettingsCategoryMenu.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsCategoryMenu.kt): sidebar, top category menu and detail Back header.
- [SettingsContentPane.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsContentPane.kt): root-section dispatch, shared page wrapper and embedded detail content.
- [SettingsDetailHost.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/general/SettingsDetailHost.kt): single detail renderer/factory boundary and `SettingsSubscreenEntryPoint` Hilt dependency access.
- [Components.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/Components.kt): page/group/card group, action row, switch, slider, divider, disabled colors and optional row-container override.
- [OmnidroidSettingsList.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/OmnidroidSettingsList.kt): single-choice row/dialog, authoritative index or state binding, and dialog-local pending radio selection.
- [OmnidroidSettingsListMultiSelect.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/OmnidroidSettingsListMultiSelect.kt): multi-choice row/dialog and immediate set updates.
- [States.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/States.kt): normal app-facing preference binding functions using Harmony storage.
- [PreferenceSettingState.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/settings/PreferenceSettingState.kt): one typed state/read/write/listener implementation and safe Boolean/Int/String/set/index adapters.
- [SettingsManager.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/SettingsManager.kt): runtime suspend reads, defaults and numeric conversions used by gameplay/workers.
- [SettingsInteractor.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/settings/SettingsInteractor.kt): folder picking and reset effects.
- [OmnidroidTheme.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidTheme.kt): `AppTheme`, `chromeColors`, backgrounds and typography/color context.

The seven root files are in `mobile/feature/settings/general`: `LibrarySettingsSection.kt`, `DisplaySettingsSection.kt`, `ControlsSettingsSection.kt`, `SavesSettingsSection.kt`, `EmulationSettingsSection.kt`, `AdvancedSettingsSection.kt` and `AboutSettingsSection.kt`. Their function names currently include `RomsSettings`, `StartupScreenSettings`, `LibraryMaintenanceSettings`, `DisplaySettings`, `ControllerSettings`, `SavesSettings`, `EmulationSettings`, `AdvancedSettings` and `AboutSettings`.

Detail folders are under `mobile/feature/settings`: `unrecognized`, `inputdevices`, `savesync`, `coreselection`, `graphicsapi`, `bios`, `deviceprofile` and `uiscale`. Consult `SettingsDetailHost` to find the active screen/view model for a destination.

## 3. Navigation and lifetime

`SettingsSection` is the source of category order for both menu orientations and controller category cycling. `SettingsScreen` remembers the category and `SettingsBackStack<SettingsSubscreen>`; selecting/cycling a category clears its detail path. Back pops one detail level; at category root it delegates to the outer app behavior.

The detail Back header uses `path.parent?.titleId ?: section.titleId`. Nested content receives navigation through `LocalSettingsNavigation`; it must stay in its owning category. `SettingsContentPane` provides this local around embedded detail content.

The generic stack supports additional depth. New destinations still need an explicit enum entry and a renderer in `SettingsSubscreenContent`; the shell itself does not need another nullable screen field. Destination arguments and multiple instances of the same editor are not a general feature yet. Add explicit argument/key handling if a future feature needs that.

Embedded detail view models use `key = subscreen.name` in the existing settings host owner. Older standalone app detail routes delegate to the same renderer with `key = null`, preserving their default owner/key behavior. Do not delete those compatibility routes just because normal settings links are embedded.

UI scale stays on `MainRoute.SETTINGS_UI_SCALE`: it previews library components full-screen, keeps a local draft, and writes only on Save. Do not give it the embedded settings header/padding.

Current category/detail state uses `remember`, not a new universal process-restoration system. Avoid silently changing restoration or view-model disposal when extracting components: user-initiated downloads/benchmarks have different lifetime requirements from read-only observations.

## 4. Shared rows and styling

- `OmnidroidSettingsPage` provides scrolling and standard top/bottom padding with zero spacing between groups.
- `OmnidroidSettingsGroup` is flat, separated by `SettingsHorizontalDivider()`. `OmnidroidCardSettingsGroup` retains its outlined-card appearance. A group header is optional: omit `titleRes` and `title` for a headerless group.
- Rows support `titleRes`/`subtitleRes` for ordinary resource text and composable slots for dynamic/custom content. Reuse the row implementation rather than recreating Text/Icon/ListItem layout.
- `OmnidroidSettingsSwitch` accepts a state binding, or scalar `value` with `onCheckedChange`. A bound switch updates state before invoking its callback.
- `OmnidroidSettingsList` accepts a state binding, or `selectedIndex`/callbacks. Keep display labels aligned with stored raw values. Empty choices are disabled; invalid stored selections do not crash or automatically rewrite data.
- `OmnidroidSettingsListMultiSelect` writes each selected-set change immediately. Its confirmation button is not an Apply transaction with rollback semantics.
- Slider values are integers. Preserve existing ranges, `steps`, D-pad stepping and disabled rules when moving a slider.

`LocalSettingsRowContainerColor` is an optional host override defined in `Components.kt`. Its default null preserves the normal settings/card colors. Side-menu content provides `chromeColors.panel` so its row containers match its background, including disabled rows. Do not globally change Material surface colors to fix a sidebar issue; that would affect Settings, dialogs and other screens. See the companion guide for the side-menu scope.

Preserve the current theme-aware chrome, category indicators, cutout padding, focus glow and row semantics. Style changes should be explicit feature work, not accidental consequences of moving code.

## 5. Persistence and compatibility

Normal UI code should call `booleanPreferenceState`, `intPreferenceState`, `stringPreferenceState`, `stringsSetPreferenceState` or `indexPreferenceState` from `States.kt`. They select `SharedPreferencesHelper.getSharedPreferences`, which is Harmony-backed for multi-process app/game options.

The lower-level `rememberSafePreference*SettingState` functions also allow an explicit preferences object. Their default preferences argument is Android's default preferences, so do not bypass `States.kt` casually. The ROM directory intentionally uses legacy preferences through `SettingsViewModel` and the folder picker.

Bindings remember preferences/key/default identity, reflect external writes and clear/reset events, register/unregister listeners, reload after registration and copy string sets. Index binding stores a raw string and derives its index from the current options. Reading an unknown value does not mutate it.

Preserve raw key strings, storage types and defaults. Important examples:

- Folder: legacy `external_folder`; the resource identifier is historically misspelled `pref_key_extenral_folder`.
- Display filter: `shader_filter_0`.
- Maximum cache: `max_cache_size_bytes` stores a string representation.
- UI scale: `pref_key_button_ui_scale_step`, integer steps 0..5 representing 100..125%.
- HD quality: absent UI value defaults to **2**, while runtime `SettingsManager.hdModeQuality()` defaults to **1**. This discrepancy is deliberately preserved; do not silently unify it.
- Startup destinations keep the existing All/Favorites/console encoding in `LibraryStartupStore`.
- Dynamic controller keys and system/core option keys must keep their existing key builders.

Resources primarily live in [keys.xml](D:/Work/Omnidroid/omnidroid-app/src/main/res/values/keys.xml), [strings.xml](D:/Work/Omnidroid/omnidroid-app/src/main/res/values/strings.xml) and the shared library's resource files. Some preference keys are in strings.xml. Raw values and localized labels are distinct.

## 6. Adding or changing functionality

### Existing page: add a field

Edit the owning section/detail component. Use the current shared row and preference binding. The following illustrates existing APIs, not an instruction to add another autosave control:

```kotlin
val autosave = booleanPreferenceState(R.string.pref_key_autosave, true)
OmnidroidSettingsGroup {
    OmnidroidSettingsSwitch(
        state = autosave,
        titleRes = R.string.settings_title_enable_autosave,
        subtitleRes = R.string.settings_description_enable_autosave,
    )
}
```

For a new persisted key, add its resource and confirm the runtime consumer's type/default. If changing the field schedules work, persist the value before the scheduler reads it. Do not make every observation emission repeat an action.

### New group or category

Compose a flat/card group inside the existing page. For a category, add its localized title and `SettingsSection` entry, then dispatch its component from `SettingsContentPane`. Orientation menus/cycling derive from the enum; do not copy the two menu lists.

### New child or deep page

Add a `SettingsSubscreen` entry with category/title and a matching `SettingsSubscreenContent` renderer. Add only the dependencies needed by that feature to the existing factory/entry-point boundary. A root section uses its `onNavigateSubscreen` callback; embedded child content obtains `LocalSettingsNavigation.current` and invokes it from a link. Reuse the existing Back header/path.

## 7. Feature-specific behavior to preserve

- Library: directory-scan progress and broader indexing progress are different flags. Preserve Stop/Rescan behavior, startup picker, periodic-work constraints, missing-game confirmation/cover cleanup and result toast.
- Display: HD is allowed strictly above 15% battery. `HdModeBatteryMonitor` runs in main/game processes; preserve dependent control enablement and filter exclusion.
- Controls: mobile order is On-screen/Rumble/Gamepads; TV order is Rumble/Gamepads/On-screen. Dynamic device defaults and binding Activity extras are intentional. Rumble intensity currently stays independently enabled.
- Saves: backup/import/export/migration/conflicts remain available even without a cloud provider. Preserve worker identities, scheduling order, pre-game sync, sign-in/out and conflict resolutions.
- Core/API selection: bind authoritative values, not a second remembered index. Keep raw graphics value casing, current choices and legacy `pcee2` core resolution.
- BIOS: preserve PS2 regional download/pinning and backend requirement descriptors. Import/status IO must stay off main; accepted imports use application lifetime. Keep native reload behavior.
- Device profile: preserve its specialized panel layout, benchmark/progress lifetime and dialogs also used by MainActivity.
- Advanced reset: clears the current legacy/Harmony stores, schedules library/cache work and deletes downloaded cores; it is not permission to delete libraries/saves.

The old `GameDatabasesScreen.kt` and five separate `Safe*PreferenceSettingValueState.kt` implementations were removed. Do not recreate them as alternate infrastructure. Metadata slice installation remains active elsewhere.

## 8. Verification and debugging

Relevant tests are `SettingsBackStackTest` and `PreferenceSettingStateTest` under `omnidroid-app/src/test/java`; shared menu tests also matter when changing shared rows.

```powershell
.\gradlew.bat :omnidroid-app:compileFreeBundleDebugKotlin --offline
.\gradlew.bat :omnidroid-app:testFreeBundleDebugUnitTest --offline
.\gradlew.bat :omnidroid-app:assembleFreeBundleDebug --offline
```

Check both themes/orientations, category selection/cycling, detail Back, disabled controls, persistence after reopening, external writes and relevant file/sign-in Activity returns. UI-scale Cancel must not save. Use a device to substantiate visual claims.

After cross-flavor/DI changes, also compile Free Dynamic, Play Bundle and Play Dynamic. Package Free Bundle separately: requesting Play Dynamic tasks in the same Gradle invocation changes this repository's conditional ABI packaging. Select the phone APK from fresh output metadata instead of assuming its filename/version code.

As of this reference date, repository ktlint had a Kotlin lexer `HEADER_KEYWORD` compatibility failure, and the backend ratchet found 21 branches in untouched TouchScreenLayoutController against limit 19. Recheck current status; do not weaken unrelated guardrails or upgrade toolchains to conceal a settings regression. Keep allowlist paths in sync if moving/deleting allowlisted BIOS/API code.
