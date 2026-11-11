# Omnidroid side menus — technical handoff

Current reference: 10 November 2026. Attach this file to future chats about either the in-game menu or the library's per-game Game settings panel. For main app Settings, use [SETTINGS_TECHNICAL_GUIDE.md](D:/Work/Omnidroid/docs/SETTINGS_TECHNICAL_GUIDE.md). Attach both when changing their shared row components.

This is the implemented architecture. Inspect current source before editing and preserve existing staged/unstaged work. Use this guide and the live source as the reference for future changes.

## 1. Shared presentation, separate host effects

Both contexts use one `SideMenuScaffold`, common page/section/row components and shared navigation transitions. Their domain effects remain distinct:

- Library: MainActivity composes `LibraryGameMenu`. GameInteractor/services launch a selected game or change preferences/metadata.
- In-game: GameMenuActivity remains in `:game`, receives the active session's request snapshot and returns typed commands through the existing Intent result contract. BaseGameActivity applies them to that session.

Do not merge the Activities/processes, make live reset call the library's restart launcher, or make a live-session mute toggle become a persisted global setting.

## 2. File and symbol map

Shared UI under `omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose`:

- [SideMenuScaffold.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/sidemenu/SideMenuScaffold.kt): panel geometry, surface/header/accent, scrim, open/close animation, Back and content insets.
- [SideMenuState.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/sidemenu/SideMenuState.kt): `rememberSideMenuState`, close transition, once-only completion gate and width helper.
- [SideMenuNavigation.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/sidemenu/SideMenuNavigation.kt): `SideMenuDestination`, graph validation, shared NavHost and `openMenuPage`.
- [SideMenuPage.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/sidemenu/SideMenuPage.kt): `SideMenuPageScope`, page focus, sections, actions, choices, toggles and links.
- [GamePreferenceChoices.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/gamemenu/GamePreferenceChoices.kt): shared frame-speed/cloud fields and drawable-icon helper.
- [Components.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/Components.kt): underlying settings renderers and `LocalSettingsRowContainerColor`.
- [OmnidroidSettingsList.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/utils/android/settings/OmnidroidSettingsList.kt): shared choice dialog and dialog-local pending radio selection.
- [OmnidroidTheme.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidTheme.kt): panel/content/accent colors, app theme and typography.

Library host:

- [LibraryGameMenu.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/gamemenu/LibraryGameMenu.kt): selected-game session, Root/Appearance registrations, observed game/preferences, picker/name/search state and content.
- [LibraryGameMenuActions.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/gamemenu/LibraryGameMenuActions.kt): host callback bundle.
- [MainActivity.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainActivity.kt): selection entry points, action bindings and parent modal input/hint integration.
- [CoverSearchDialog.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/CoverSearchDialog.kt): search, candidate grid and Cover Only/Full Match confirmation.
- [GameInteractor.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/GameInteractor.kt): launch permissions/busy checks, metadata writes, per-game preferences and sync requests.

In-game host:

- [GameMenuActivity.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuActivity.kt): request parsing, injection, window/input lifecycle, graph and result delivery after closing.
- [GameMenuRequest.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuRequest.kt): snapshot of game/core/options/audio/speed/discs/tilt/capabilities.
- [GameMenuHomeScreen.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuHomeScreen.kt): root sections and capability gates.
- [GameMenuNavigationRoutes.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/GameMenuNavigationRoutes.kt): Home/Save/Load/Options route metadata and helpers.
- [InGameMenuCommand.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/InGameMenuCommand.kt): typed commands, result extra mapping and Intent writing.
- [GameMenuCoreOptionsScreen.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/coreoptions/GameMenuCoreOptionsScreen.kt): controller-port/core/advanced option editing; its view model observes gamepad count.
- [GameMenuStatesScreen.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamemenu/states/GameMenuStatesScreen.kt): save/load slot rows. Its view model loads slot metadata/previews on IO and exposes shared state with explicit slot IDs.
- [GameMenuContract.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/GameMenuContract.kt) and [BaseGameActivity.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/game/BaseGameActivity.kt): existing transport keys and active-game result application.

The old `MainGameContextActions.kt` implementation was removed. The nested `GameMenuActivity.GameMenuRequest` type is now top-level. Do not create alternate shells or resurrect these entry points.

## 3. Appearance and color rules

Both menus use `chromeColors.panel` for the panel surface, matching the library's idle console selector buttons (`#1C1C1E` in dark mode, `#F5F5F5` in light mode). SideMenuScaffold provides that same color through `LocalSettingsRowContainerColor` around its content. The shared action/switch/choice rows consume the override; disabled row containers keep the same fill. Slider ListItems also honor it if used within this scope. The library loading notch uses this same untinted fill, half-height flare/corner radii, a faint green contour and activity dot, and text counts without a green progress line.

This is a scoped override, not a global Material color change. The local defaults to null so ordinary app settings/cards retain their existing colors. To change the sidebar's background, change its host/theme token and keep the local provider aligned; do not independently hardcode row colors or change all app surfaces. Press/focus/disabled foreground feedback remains intentional.

Library Appearance currently has **one headerless `MenuSection("appearance-actions")`**, with no internal divider. Default actions are Search box art (when a provider exists), Set/Change thumbnail and Set/Change name. Existing custom data adds its conditional Remove thumbnail/Remove name actions inside the same group. Removing headers does not remove those capabilities. The page still has the normal top Appearance navigation header.

Current shared geometry/motion: 42% viewport width bounded to 320..440 dp and clamped to available width; rectangular panel; 3 dp accent edge (`#388D56` green tint in dark mode, `#6ED18F` faded system green in light mode); 56 dp header; 40 dp Close/Back button; alpha-0.58 scrim; 280 ms opening / 220 ms closing; 160 ms child fades. Insets and optional hint-bar reservation are applied once in the shell.

The in-game host calls `AppTheme(syncSystemBars = false)`: it still follows the app theme unless `followUserPreference` is explicitly changed. Do not assume that menu is permanently dark. System-bar/game lifecycle policy belongs to the Android host.

## 4. Close, navigation and input lifecycle

Create one remembered `SideMenuState` per menu session. Its MutableTransitionState represents opening/open/closing/closed presentation. `closeThen { effect() }` accepts one completion; repeated Close/Back/taps cannot replace the accepted action. Completion occurs after exit is idle, with `onClosed` called before the stored completion. Closing before the first open frame is supported.

Library keeps a render target through exit and clears selection when fully closed. Launch/restart run through close completion. In-game commands are stored by the host, and its `onClosed` encodes/returns the command once; do not finish the Activity on the row click before animation completion.

The side menus use Compose NavHost, separately from app Settings' immutable SettingsBackStack. `SideMenuDestination` stores route/title/parent metadata, not callbacks or Android objects. Graph validation rejects duplicate routes, missing parents and cycles.

Library metadata is in `rootPage`, `appearancePage` and `libraryPages`; header title/Back state derives from the active metadata. In-game metadata is in GameMenuRoute. Root Back closes; child Back pops one entry. Scrim closes the whole menu. Native choice/editor/confirmation dialogs receive their usual dismiss priority.

[ControllerNavigation.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/controller/ControllerNavigation.kt) holds the optional modal controller owner. [ControllerInputBridge.kt](D:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/controller/ControllerInputBridge.kt) dispatches to that owner while the library menu is active, so background search/zoom/scan/console actions are not invoked.

SideMenuPage tracks stable row IDs/requesters and remembered focus. Only the resumed NavHost entry owns focus; outgoing animation content must not take it back. Disabled rows are excluded from initial focus. Library restores the parent's content focus when the modal owner is removed. Each host owns one hint bar; do not render another per page or double-reserve bottom padding.

## 5. Component API and extension recipes

The normal API is Compose functions, not a universal serialized menu schema:

- `SideMenuPage(id) { ... }` supplies page scrolling/focus scope.
- `MenuSection(id, visible, titleRes) { ... }` groups content. Omit titleRes for no header; explicitly hide empty conditional groups to avoid orphan separators.
- `MenuAction(id, titleRes/title, subtitle, icon, enabled, onClick)` describes a row button.
- `MenuToggle(id, value, onChanged)` binds a Boolean without owning its persistence.
- `MenuChoice<T>(id, options: List<MenuOption<T>>, value, onSelected)` pairs stored values with labels.
- `MenuLink(id, destination, onNavigate)` renders a child-page link.
- Existing content slots handle identity/preview images; reuse settings renderers for new control types rather than copying them.

IDs must be stable and unique within a page. They drive focus and test tags. Dynamic entries should use game/port/core-option/slot identifiers rather than filtered-list positions. Keep labels localized and values unlocalized.

Example pattern inside a feature-owned composable (the feature supplies state/actions/menu):

```kotlin
SideMenuPage("game-tools") {
    MenuSection("playback", visible = state.supportsSpeed) {
        FrameSpeedMenuChoice(state.speed, actions::setSpeed)
    }
    MenuSection("actions") {
        MenuAction(id = "resume", titleRes = R.string.game_context_menu_resume) {
            menu.closeThen { actions.resume() }
        }
    }
}
```

For a new subsection or deeper page: add destination metadata with its parent; include it in the validated collection; register its composable in the host SideMenuNavHost; add MenuLink/openMenuPage from its parent. Library headers derive from libraryPages and in-game headers from GameMenuRoute. The shared shell does not gain another depth variable or a feature-specific branch.

Keep frequently reused section content in a composable extension on SideMenuPageScope. The speed/cloud components demonstrate reuse with different host effects. No reflection, JSON schema, new Gradle module or app-wide DI rewrite is needed.

## 6. Action and storage contracts

LibraryGameMenuActions supplies `play`, `restart`, `favorite`, `cloudOverride`, `frameSpeed`, `syncNow`, `setThumbnail`, `removeThumbnail`, `setCoverUrl`, `fixMatch`, `setName` and `removeName`. MainActivity connects these to current GameInteractor methods; do not duplicate notification/busy/unavailable checks in the shell.

Library favorites/preferences/sync stay open. Appearance dialogs/picker keep their existing save/cancel semantics. Selected game observation is keyed to game ID, stops after exit, and does not query ID -1 while closed. Picker state saves the initiating ID and resolves the current database row on result; never retarget a result to the game currently selected afterward.

InGameMenuCommand covers Restart, Quit, Save(slot), Load(slot), Audio(enabled), Speed(multiplier), Disc(index) and Tilt(configuration), plus EditTouchControls. Keep all GameMenuContract strings/types and BaseGameActivity's refresh/result handling. Slot/disc results are zero-based even when their labels are one-based. Speed sends both the multiplier and legacy fast-forward Boolean. Mute is the inverse of audio enabled. Immediate toggle/radio feedback is presentation state; the host remains authoritative.

Cloud override and core/controller preference edits stay open in-game; session commands close. Preserve capability gates for states, speed, discs, touch editing, tilt and options. Save permits missing slots; Load disables them. Use explicit slot IDs, not row indices, for results.

The existing Harmony stores retain `frame_speed_<gameId>` (1/2/4/8/16, fallback 1) and `save_sync_game_<gameId>` (inherit/always/never). Core/controller keys remain system/core/port-scoped rather than becoming per-game overrides. The shared field components render values/callbacks; they do not add duplicate persistence.

Preview work is IO/subscription-scoped, with sampled decode in StatesPreviewManager. User-initiated metadata/import/sync effects retain their appropriate service/application/worker lifetime after a page closes. Keep CoverSearchDialog's game/query identity, debounce, cancellation and Cover Only versus Full Match behavior.

GameMenuHelper is still used for slot descriptions/preview loading and may serve TV callers. It is not dead solely because mobile rows are Compose-based.

## 7. Validation and troubleshooting

JVM tests: `SideMenuStateTest` (close-once/early close/width), `SideMenuNavigationTest` (deep valid graph/duplicates/missing parents/cycles), `InGameMenuCommandTest` (result mapping), plus the settings tests when shared controls change.

```powershell
.\gradlew.bat :omnidroid-app:compileFreeBundleDebugKotlin --offline
.\gradlew.bat :omnidroid-app:testFreeBundleDebugUnitTest --offline
.\gradlew.bat :omnidroid-app:assembleFreeBundleDebug --offline
```

Compile the other Free/Play × bundle/dynamic variants when changing shared APIs, DI or transport. Package Free Bundle separately from Play Dynamic task requests: build configuration conditionally changes ABI splitting. Select the phone APK from fresh output metadata.

On device check dark/light panel-versus-row colors, headerless Appearance actions, parent Back/root close, choice/editor dismissal, selected values, large/narrow viewports, controller focus/hotplug and return to the active game. Preserve metadata while testing editors; cover/name removal and saves are real data mutations.

Use fresh build logs, crash logs and device checks as evidence; a successful compile alone does not establish visual parity. As of this date ktlint had a lexer HEADER_KEYWORD compatibility failure and the backend ratchet had an unrelated untouched touch-layout limit failure; recheck and report those separately from new failures.
