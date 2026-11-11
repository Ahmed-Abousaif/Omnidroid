# Library screen technical guide: Console, Grid and List

**Source snapshot:** 2026-11-11, current Omnidroid working tree, including the
Grid/List rebuild and Continue-cover fix. This describes the Android mobile
library hosted by `MainActivity`, including its controller support. The separate
TV feature is outside this guide.

**Purpose:** attach this file to a future chat with the requested library change.
It contains the architecture, source map, behavior contracts, previous failure
causes and verification commands needed to work on any of the three views.
The code is authoritative if it has changed since this snapshot. Function/class
names are useful search anchors; linked line numbers are snapshot conveniences.

All source links are relative to this file in `docs/`. For example, a target
starting `../omnidroid-app/` means `omnidroid-app/` from the repository root.
The checkout used to write this guide is `D:\Work\Omnidroid`.

Quick navigation:

- [Source map](#2-source-map-and-ownership) and [runtime path](#3-end-to-end-runtime-path).
- [Data and state](#4-game-data-queries-and-state), [screen structure](#5-shared-screen-structure-and-controls).
- [Console](#6-console-view), [Grid](#7-grid-view), [List](#8-list-view).
- [Scroll, keys and transitions](#9-scroll-positions-keys-and-shared-cover-animations).
- [Images and caching](#10-image-loading-caches-and-lookahead), [host/input/scans](#11-host-actions-controller-input-and-scanning).
- [Where to edit](#12-where-to-make-common-changes) and [verification commands](#13-verification-and-reproduction).

## 1. Context to carry into a future chat

The three modes are `LibraryLayout.CONSOLE`, `GRID` and `LIST`. They consume the
same Room-backed `Flow<PagingData<Game>>` from `LibraryViewModel`. Console uses a
custom windowed carousel and animation state. Grid and List share a lazy-grid
renderer; List is a one-column vertical grid. Their source-of-truth state,
database queries, presentation geometry, image loading and host actions live in
different files.

The rebuilt implementation addresses two separate identity problems: collisions
between loaded game IDs and placeholder keys, and duplicate shared-cover
animation matches for a pinned Continue game also present in normal results.
Keep both identity contracts in mind during future edits. The tested default
stress ceiling is **100,000 games**; million-game runs are unnecessary unless
explicitly requested later.

Suggested future-chat prompt:

> Use this technical guide as context for the Omnidroid library. Read the current
> referenced implementation before editing. The change I want is: [describe the
> behavior and affected views]. Check the affected orientations, controller
> behavior, paging placeholders and pinned Continue case.

## 2. Source map and ownership

### Entry, state and chrome

- [MainActivity.kt][main] — creates `LibraryViewModel.Factory`, supplies adaptive
  and controller composition locals, hosts HOME/details navigation, renders the
  top bar, and connects UI callbacks to `GameInteractor` and menus.
- [LibraryViewModel.kt][vm] — `UiState`, the paged `games` flow, filters, search,
  sort, density, layout preferences, Continue selection and scan requests. Also
  declares `LibraryLayout` and `LibrarySort`.
- [LibraryFilter.kt][filter] — `All`, `Favorites`, and `System(MetaSystemID)`.
- [LibraryScreen.kt][screen] — outer portrait/landscape arrangement, Console
  backdrop, sidebar/bottom dock, loading/empty states, renderer dispatch,
  view/density controls, pinch zoom and hoisted selection/scroll states.
- [LibraryTopBar.kt][topbar] — library-only search, sorting, random game, cast,
  settings and profile controls, also used by the UI-scale preview. This is hosted
  outside `LibraryScreen`, so changing the screen body alone does not change the
  header. `shared/compose/ui/AppTopBar.kt` owns the common vertical fade, status
  indicators, shared back button and sync notch. The host keeps this bar mounted
  across routes so indicators move as library actions animate out or back in.
  Other routes hide library controls and show a back button without a page title;
  the UI-scale preview retains the normal library bar without a back button.
- [SyncProgressNotch.kt][notch] — animated scan-progress notch geometry and UI.
- [LibraryStartupStore.kt][startup] and
  [RegisteredSystemsStore.kt][registered] — persisted startup filter and
  explicitly registered console groups. [StartupDialogs.kt][startupdialogs]
  contains the startup-selection dialog UI.

### The three renderers

- [ConsoleLibrary.kt][console] — `ConsoleCarouselState`, `ConsoleItems`,
  `ConsoleLibrary`, landscape/portrait carousels, metrics, card placement,
  gestures, selected-game actions/stats, backdrop and Console cover transition.
- [LibraryPagedContent.kt][paged] — `LibraryPagedScrollState`,
  `rememberLibraryPagedScrollState`, `LibraryPagedContent`, `libraryCells`,
  `PrefetchViewportCovers` and `libraryCoverPrefetchRange`.
- [LibraryPagedItems.kt][items] — shared Grid/List index, pinned-item, key and
  content-type adapter. This is where paging item identity belongs.
- [LibraryGameCells.kt][cells] — `LibraryGameCardItem`, `LibraryGameRow`,
  `libraryCoverCorner` and `libraryCoverSharedBounds`. These paint loaded cells
  inside geometry allocated by the viewport.
- [LibraryPaging.kt][paging] — library-specific page size, memory-cache target,
  prefetch distance and jump threshold.

### Supporting systems

- [Game.kt][game] and [GameDao.kt][dao] — persistent game data, raw Room paging
  source, filtering, SQL ordering, counts, random selection and last-played row.
- [GameSystem.kt][systems] and [MetaSystemID.kt][metasystems] — system metadata,
  user-facing console groups and the `GameSystem.metaSystemID()` mapping.
- [AdaptiveDimensions.kt][adaptive] — `AdaptiveLayoutSpec`,
  `calculateAdaptiveLayoutSpec` and `LocalAdaptiveLayout`.
- [OmnidroidTheme.kt][theme] — `AppTheme`, chrome color tokens and shared home
  background styling. [GameUtils.kt][gameutils] supplies subtitle/system labels.
- [OmnidroidGameCard.kt][card] — shared cover-card appearance, info styles,
  caption/Continue UI and the nominal cover aspect ratio.
- [OmnidroidGameImage.kt][image] — Compose/Coil request construction and fallback
  rendering. [CoverUtils.kt][covers] and [CoverCacheStore.kt][coverstore] own
  image sources, cache keys, loaders and processed cover files.
- [LibraryCoverPrefetcher.kt][prefetch] — optional off-main-thread processing
  of the active Grid/List viewport's lookahead covers.
- [GameDetailsScreen.kt][details] — destination side of the shared-cover match.
- [GameSessionDao.kt][sessions] and [PlayTimeStore.kt][playtime] — Console stats
  and the legacy duration fallback. [GameInteractor.kt][interactor] owns game
  effects invoked by the host.
- [ControllerNavigation.kt][controller], [ControllerFocus.kt][focus] and
  [ControllerInputBridge.kt][bridge] — focused-game reporting, focus effects,
  controller callbacks and physical input translation.
- [LibraryGameMenu.kt][menu] and [LibraryGameMenuActions.kt][menuactions] —
  library game context-menu UI and its effect callbacks.
- [LibraryIndexScheduler.kt][scheduler], [LibraryIndexWork.kt][indexwork],
  [PendingOperationsMonitor.kt][operations], [LocalFolderWatcher.kt][watcher]
  and [LibraryPeriodicScanScheduler.kt][periodic] — scan scheduling, work,
  operation state and folder-change/periodic triggers.

## 3. End-to-end runtime path

1. `MainActivity` constructs the registered-system and startup stores, then
   obtains `LibraryViewModel` through its explicit factory and injected Room DB.
2. The host collects `LibraryViewModel.state`. It calculates the adaptive layout
   from available width/height and UI scale, and provides `LocalAdaptiveLayout`
   and `LocalControllerNavigation` to the UI tree.
3. HOME renders `LibraryScreen` inside the host's `SharedTransitionLayout` and
   navigation visibility scope. The screen collects both `state` and
   `games.collectAsLazyPagingItems()`.
4. `LibraryGames` chooses loading/empty/content UI, determines whether Continue
   is eligible, and dispatches to `ConsoleLibrary` or `LibraryPagedContent`.
5. Accessing a rendered game's paging index supplies a load hint. Room loads
   nearby pages; placeholders represent unloaded positions in the total result.
6. UI actions return through callbacks. The host handles details navigation,
   gameplay, favorites, cover/name edits and context menus. Cells do not start
   emulator backends directly. [Sources: host][main], [screen][screen], [VM][vm].

## 4. Game data, queries and state

### Persistent game rows

`Game.id` is the stable database identity; `fileUri` is also unique in the
schema. Rendering uses `displayName`, which is a nonblank trimmed `customName`
when present, otherwise `title`. Covers use `customCoverPath` and/or
`coverFrontUrl`. Other relevant fields are `systemId`, `developer`,
`isFavorite`, `isAvailable` and `lastPlayedAt`. [Game source][game].

A missing/unavailable game can remain in results. The current paged query does
not filter on `isAvailable`; loaded rows/cards render it at reduced alpha
(`0.45f`). Removing missing rows is a separate ViewModel operation. A visible
row is therefore not proof that its ROM can currently be opened.

### Query flow and paging policy

`LibraryViewModel.games` combines filter, trimmed search query and sort. It uses
`flatMapLatest` to create the current `Pager`, then applies
`cachedIn(viewModelScope)` once after the switch. All three views use this flow.
Layout and density changes do not create a different SQL query. [VM][vm].

`GameDao.selectPaged` creates a parameterized `SimpleSQLiteQuery`, then calls
`selectPagedRaw`, whose `@RawQuery` observes `Game` for Room invalidation:

- Favorites adds `isFavorite = 1`.
- A system filter expands the selected `MetaSystemID` into its database system
  IDs and adds `systemId IN (...)`.
- Search matches both `title` and `customName` using SQL `LIKE` substrings.
- TITLE orders by `title ASC, id DESC`.
- RECENTLY_PLAYED puts non-null last-played dates first, then orders descending
  by date, followed by title/ID tie-breakers.
- FAVORITES_FIRST orders by favorite flag, then title/ID.
- CONSOLE uses a `CASE systemId ...` rank built from the dock's localized console
  order, then the system/title/ID ordering. [DAO source][dao].

**Name-editing detail:** the displayed custom name participates in search, but
TITLE sorting currently uses the original `title` column. A request to sort by
displayed name needs a DAO/query change, not only a cell text change.

The current `LibraryPaging.config()` is:

- Page size: 60 games; initial load: 120.
- Paging prefetch distance: 30 games.
- Placeholders enabled; loaded-game cache target: 600.
- Distant-jump threshold: 180 positions.

Room's offset paging can refresh around a distant requested index instead of
loading every intervening page. The 600 value is a best-effort Paging cache
target, not a strict total-process memory cap; the active viewport/prefetch
window and page boundaries can require extra items. [Paging policy][paging].

### UI state and preferences

`UiState` includes filter, search query, sidebar systems, Continue game,
operation/progress state, density, layout, sort, selected-system count and
startup filter. It uses `stateIn` with `WhileSubscribed(5_000)`. The Continue game
comes from `selectLastPlayed()`; the DAO returns the latest played row as a list
with `LIMIT 1`, and the VM takes its first element. [VM][vm], [DAO][dao].

Presentation preferences use `SharedPreferencesHelper`'s Harmony preferences:

- `pref_library_layout`: enum name; fallback is `CONSOLE`.
- `pref_library_zoom_density`: integer clamped to `0..2`; fallback is `0`.
- `pref_library_sort`: enum name; fallback is `TITLE`.
- Startup destination uses the `pref_key_startup_destination` string resource's
  preference key, encoded as `all`, `favorites` or `system:<MetaSystemID name>`.
- `pref_registered_meta_systems`: set of registered `MetaSystemID` names.

The active filter is runtime state initialized from the startup filter; search
starts empty. Explicit startup selection is distinct from visiting a filter.
Changing the stored startup filter while viewing the previous startup filter
moves the current view to All. Sidebar systems are the sorted union of explicit
registrations, systems with games, and the selected startup system.
[VM][vm], [startup store][startup], [registered store][registered].

`selectedSystemGameCount` totals database counts across that console group's
system IDs. It is not the current search-result count.

## 5. Shared screen structure and controls

`LibraryScreen` chooses portrait when available height exceeds width. Portrait
uses a vertical content column with `PortraitConsoleBar` below it; landscape
uses `LibrarySidebar` beside the content and applies horizontal cutout insets.
The root's available dimensions and the host's `LocalAdaptiveLayout` both
matter. Square/compact classification is not the same condition as portrait.
[Screen][screen], [adaptive layout][adaptive].

`LibraryGames` decides what the body renders:

- Initial refresh with no paging items: centered progress indicator.
- Empty result, blank search, filter other than Favorites: scan/sync empty UI.
- Other empty result: the common empty view.
- Otherwise: the active renderer, potentially with Continue prepended.

Continue is eligible only for **All + blank search + a last-played game**. It is
an additional presentation item, not a replacement for the ordinary result row.
The current screen does not implement a dedicated paging-error/retry panel in
this branch; do not describe a paging failure as an explicit retry UI.
[Screen `LibraryGames`][screen].

The bottom view bar selects the three layouts. Density controls apply only to
Grid; `0` means largest cards and `2` the densest grid. Touch zoom controls are
hidden when a controller is connected; controller zoom remains wired through
the host. Pinch zoom is Grid-only, accumulates a live scale, crosses a `1.25f`
step threshold to change density, and resets the temporary scale on release.
`LibraryZoomControls` reverses the track position relative to density with
`MAX_ZOOM_DENSITY - density`. [Controls in LibraryScreen.kt][screen].

Long-pressing a console logo opens `SetStartupScreenDialog`, which sets/unsets
that system as startup. Favorites and All have their own dock entries; Add
Console routes through the host callback. [Screen][screen], [dialog][startupdialogs].

## 6. Console view

### State, identities and bounded composition

Console is implemented entirely by the custom carousel code in
[ConsoleLibrary.kt][console]. It is not the Grid renderer or a `LazyRow` of all
games. `ConsoleItems` adapts paging plus an optional pinned game:

- With Continue, display index 0 is pinned and normal paging indexes shift by 1.
- Without it, display and paging indexes match.
- Loaded normal keys are game IDs; unloaded keys are `placeholder-<display index>`.
- The pinned key is `continue-<game ID>`.
- `gameAt` triggers a paging access hint; `keyAt` uses `peek`.

`ConsoleCarouselState` owns the fractional `position`, integer `targetIndex`,
non-observable `itemCount`, active settle job and computed rounded
`selectedIndex`. The separate `rememberConsoleSelection` uses `rememberSaveable`
above the orientation branches. Its reset key includes filter, raw search text,
Continue game ID and sort. The current target is written back to this hoisted
selection as the carousel moves.

The custom `Layout` composes a window around selection:

- Portrait-style carousel: up to three neighbors on either side (seven cards).
- Landscape carousel: two preceding cards plus a calculated number after the
  selection based on available width and `LandscapeMetrics.visibleAfter`.

The composition window scales with the viewport, not total library size.
Games in that small window can be placeholders while paging catches up.

### Orientation-specific layout

`ConsoleLibrary` chooses the portrait carousel for portrait screens. It also
uses that carousel in landscape when the area is small/squarish:
`(isCompact || height <= 500.dp) && (isSquare || width/height <= 1.45f)`.
In that landscape fallback, `showButtons` is false. Otherwise it uses
`ConsoleLandscapeCarousel`.

`PortraitMetrics` determines card size from width and remaining height, with a
minimum width of 80 dp, a nominal width fraction of `0.68f`, and a drag step of
`cardWidth * 0.74f`. Placement uses distance from fractional position to apply
translation, scale, 3D Y rotation (up to 32 degrees), alpha and depth ordering.

`LandscapeMetrics` calculates the selected large card, smaller neighbors,
anchor position, information panel and drag step. Large-card height is bounded
to 96–440 dp; smaller cards use `0.55f` of that height. Placement packs cards
around the moving selection and transitions their widths based on focus.
Use these metrics classes for carousel sizing changes rather than Grid helpers.

### Input and selection semantics

- Drag cancels the current settle job, updates fractional position, applies
  `0.35f` resistance outside the legal range, and permits up to `0.35f` overscroll.
- Release projects velocity by `0.28f` seconds; velocity is limited to 40
  items/second before choosing a destination.
- Settling uses a spring with damping ratio `0.86f` and stiffness `320f`.
- Left/right key-down events move the target by one item. At the ends they are
  left unconsumed so focus can move to surrounding navigation.
- Center/Enter/NumPadEnter/A confirm on key-up and play the selected game.
- Tapping a side cover moves selection. Tapping a settled selected cover opens
  details. This selected-cover path also applies to the pinned game; it is
  distinct from the primary Continue/play action.
- Long press opens options; favorite and settings/play actions return through
  the supplied callbacks. [Carousel state/input/renderers][console].

`clampToCount()` handles shrinking results. Preserve selection/count safety
when changing search, scan invalidation or carousel physics.

### Backdrop, glow, title and stats

Console reports the selected game to the backdrop after a 160 ms delay and
clears it when disposed. `ConsoleBackdrop` crossfades over 450 ms, uses a 36 dp
blur on Android 12/API 31 and above, and uses a lower-alpha unblurred image on
older versions. The host allows it to bleed underneath top chrome.

The selection glow pulses between `0.8f` and `1f` on a reversing 2,400 ms
animation. Glow is suppressed while shared-cover transitions are active.
`ConsoleRotatingTitle` handles overflow/title motion after a 1,500 ms delay.

`ConsoleGameStats` reads the application's `GameSessionDao` and legacy
`PlayTimeStore`, using the greater stored duration. Its achievement text is
currently literal `0/0`; it does not represent an achievement-service query.
Use [GameSessionDao.kt][sessions] and [PlayTimeStore.kt][playtime] when changing
duration/session semantics.
Console images generally use `OmnidroidGameImage`'s default decode width unless
overridden at a particular call; Grid/List's explicit decode sizing does not
automatically change every Console image. [Console source][console], [image][image].

## 7. Grid view

Grid uses [LibraryPagedContent.kt][paged], with shared identity from
[LibraryPagedItems.kt][items] and painting from [LibraryGameCells.kt][cells].
The same `libraryCells` content builder is supplied to both grid orientations.

### Adaptive layout and visual styles

Portrait Grid uses `LazyVerticalGrid` with a fixed adaptive column count. The
ordinary display order runs left-to-right, then down. `libraryPortraitColumns`
uses target widths dependent on density and available width, clamped to 2–6
columns. Its square-layout branch returns 3, 4 or 5 columns at densities 0–2.

Landscape Grid uses `LazyHorizontalGrid` with adaptive fixed rows. Display
indexes run down a column before continuing to the next column. In
`libraryGridRows`, non-square height bands select:

- Below 440 dp: 1 / 2 / 3 rows at densities 0 / 1 / 2.
- Below 640 dp: 2 / 3 / 5 rows.
- Below 880 dp: 3 / 5 / 7 rows.
- Otherwise: 3 rows at density 0; height-derived 5–6 or 7–9 at densities 1/2.
- The square-layout branch instead selects 2 / 3 / 4 rows.

These helpers are in [LibraryScreen.kt][screen]; viewport dimensions and
`LocalAdaptiveLayout.isSquare` are their inputs. The actual grid geometry and
padding are in [LibraryPagedContent.kt][paged].

`GameCardInfoStyle` is OVERLAY at portrait densities 0/1 and MINIMAL at 2.
Landscape uses BELOW at density 0, OVERLAY at 1 and MINIMAL at 2. BELOW reserves
the shared 52 dp caption-height allowance when calculating cover width.
The nominal cover ratio is `2f / 3f`. [Card UI][card].

### Cells, loading and image sizes

The viewport determines spacing, padding, lane count, cell dimensions and
requested cover width. Portrait cells fill lane width and use the nominal cover
ratio; landscape cells have calculated width and fill their row height.
`LibraryGameCardItem` passes the assigned geometry and decode width to
`OmnidroidGameCard`.

An unloaded slot renders a lightweight, rounded background at the same geometry.
It has no image request, game callback, focus target or accessibility label.
Returning an empty composable for an unloaded slot would collapse its size,
distort scroll estimation and encourage excessive paging work.

Normal card taps open details; the pinned Continue card invokes
`onContinueClick`. Long press invokes `onGameLongClick`. The pinned game can
appear again as an ordinary item with a different lazy key.

## 8. List view

List uses the same `LibraryPagedContent` and `libraryCells` path, with
`LazyVerticalGrid(columns = GridCells.Fixed(1))` in both orientations. There is
no separate current `LazyColumn` library implementation to modify.

Each row fills width and has height `adaptive.listThumbHeight + 8.dp`. List
uses 8 dp item spacing and horizontal padding, plus 12 dp vertical padding.
`LibraryGameRow` draws the thumbnail, display name, subtitle and trailing
Continue/favorite marker. The row has a 10 dp corner radius, 4 dp cover inset,
and reduced alpha for unavailable games. Its thumbnail width is
`listThumbHeight * LibraryGameCardAspectRatio`, and that width is also supplied
as its image decode width. [Viewport][paged], [row renderer][cells].

`GameUtils.getGameSubtitle` produces the subtitle. Displayed title, metadata,
favorite styling, focus and actions are painted by the row renderer; ordering
and which rows exist are the ViewModel/DAO's responsibility.

Row taps open details; the pinned row invokes Continue. List has no density
control of its own. A stored Grid density does not turn List into multiple
columns.

## 9. Scroll positions, keys and shared-cover animations

### Scroll ownership

`LibraryScreen` owns `LibraryPagedScrollState` above portrait/landscape branches.
It contains independent `LazyGridState` instances for List and Grid. A dataset
key of filter, raw search text and sort creates/reset these states. A mode or
orientation change within the same live screen composition can therefore keep
the dataset's positions. These are Compose/Saveable states, not separately
written scroll-position preferences; arbitrary process/navigation lifetime
guarantees should be tested rather than assumed. [Screen][screen], [state][paged].

Console's hoisted selection is separate and also resets when its Continue ID
changes. Do not reset all three modes' state on every scroll or page load.

### Paging key contract

`LibraryPagedItems` maintains one optional pinned offset for all Grid/List paths:

- Pinned index 0 has `continue-<id>` as key and `continue` as content type.
- Normal loaded entries use `games.itemKey { it.id }` and game content type.
- Unloaded entries get AndroidX Paging's dedicated placeholder key/content type.
- Keys and cover-prefetch lookups use `peek`; rendering uses `get` to load data.

Keep paging positions and game IDs in separate key spaces. The former
`games.peek(index)?.id ?: index` expression could assign the same integer to a
loaded row and a placeholder, causing `IllegalArgumentException: Key ... was
already used`. Both load and eviction transitions need unique keys.
Console already uses string placeholder keys in its own adapter. [Items][items],
[ConsoleItems][console].

### Shared-cover match contract

Ordinary covers register `game-cover-<id>` in the host's shared-transition scope.
The details cover registers the same key. Grid/List centralize their corner and
shared-bounds policy in `libraryCoverCorner`/`libraryCoverSharedBounds`; Console
uses `consoleCoverSharedBounds`. The transition duration is 400 ms with
`FastOutSlowInEasing`, `RemeasureToBounds` and a rounded overlay clip.
[Cells][cells], [Console][console], [details destination][details].

The pinned Continue cover opts out of this match in every renderer. Its
ordinary duplicate remains the details-transition participant. Registering both
copies under the same shared-cover key at different sizes previously caused
continuous lookahead measurement and `ComposeNotIdleException` in landscape
large-card Grid and Console. This is a separate defect from lazy-item keys.

If a future request needs a dedicated pinned-cover-to-details animation,
coordinate an explicit origin-aware identity with the details destination.
Simply enabling the duplicate match reintroduces the measurement problem.

## 10. Image loading, caches and lookahead

`OmnidroidGameImage` builds a Coil request keyed by game ID, cover URL, custom
cover path and requested pixel width in its Compose `remember`. The default
decode width is 160 dp; the row/card callers override it for Grid/List. It uses
`CoverUtils` for request data and a text drawable as fallback/error painter.
[Image composable][image].

`CoverUtils.coverRequest` prefers a valid custom local cover, then a processed
cached file, then an allowed remote URL. Its cache keys account for IDs and
local-file path/modification time. When a processed file is absent, the request
can also schedule `ensureGrid` in application scope. That per-image work is
separate from viewport lookahead. [Cover utilities][covers].

There are distinct budgets:

- Room Paging retains the configured nearby game rows.
- Coil's image loader uses a 15% memory-cache policy and a separate disk-cache
  policy at 10% (`image_cache`).
- `CoverCacheStore` keeps processed covers in `cover_cache`, with a 300 MiB LRU
  budget, grid/detail maximum edges of 480/768 pixels, and negative caching of
  failed remote covers. It encodes WebP with JPEG fallback.

Changing one cache does not impose a cap on the entire process or replace the
other caches. [Loader][covers], [processed cache][coverstore].

`PrefetchViewportCovers` is Grid/List-only. It observes first/last visible
indexes with `snapshotFlow`, tracks direction, coalesces changes for 100 ms and
uses `collectLatest` to cancel obsolete batches. It peeks at two rows/columns
of candidates, clamped to 4–24 items. The IO worker processes them sequentially,
skips custom local covers, propagates cancellation and logs ordinary cache
failures instead of failing the UI. A synchronous in-flight download may finish
before cancellation is observed. This is not global deduplication of every
application-scope image request. [Viewport prefetch][paged], [IO worker][prefetch].

## 11. Host actions, controller input and scanning

### Navigation and game effects

`MainActivity` stores `lastClickedGame` for initial details content and navigates
by `game.id`. Its Continue callback clears search and invokes
`gameInteractor.onGamePlay`. Long press selects a game for `LibraryGameMenu`.
Favorite toggles and the menu's play/restart, cloud/frame-speed, cover, match
and custom-name changes are host effects exposed through
`LibraryGameMenuActions`. [Host][main], [menu][menu], [menu actions][menuactions].

Random selection uses the current system/Favorites filter but deliberately
ignores search; the host opens the chosen game's details. Top-bar search and
sort call the VM, rather than filtering a loaded in-memory list.
[Host][main], [VM][vm], [top bar][topbar].

### Controllers

`ControllerInputBridge` receives host key/motion events, selects modal navigation
when present, translates controller buttons/axes and invokes the navigation
callbacks. `ControllerNavigationState` holds focused game, hints, search/input
state, focus requesters and action callbacks. `MainActivity` wires scan, zoom,
previous/next console, search, options and back behavior. [Bridge][bridge],
[navigation state][controller], [host][main].

The library publishes its first-item `FocusRequester` through the composition
local and requests it when a controller is present. Normal rows/cards use
`controllerFocusGlow` and `reportControllerGame`. Console is a focusable carousel
and reports the selected game while it owns focus. Placeholders remain
non-interactive. [Focus helpers][focus], [screen][screen], [Console][console].

### Scans and progress

On the host's STARTED lifecycle, `autoSyncLibrary` requests a shallow sync if a
stored folder exists. `LocalFolderWatcher` also triggers shallow sync. Manual
`syncLibrary` defaults to deep scanning; without a folder it opens the folder
picker. The VM guards against overlapping requested/library operations and
clears its temporary request flag in `finally`. [Host][main], [VM][vm], [watcher][watcher].

`LibraryIndexScheduler` enqueues the library index work, followed by core update
and Switch-icon extraction. `PendingOperationsMonitor` combines the relevant
WorkManager states; `SyncProgressTracker` supplies progress to the VM and header.
Optional periodic scans are scheduled separately by
`LibraryPeriodicScanScheduler`. Scanning and emulator backend details are not
implemented in the card renderers. [Scheduler][scheduler], [index work][indexwork],
[operation monitor][operations], [periodic scheduler][periodic], [notch][notch].

## 12. Where to make common changes

- **Carousel dimensions, rotation, spring, fling or selected-card behavior:**
  `ConsoleLibrary.kt`, especially `ConsoleCarouselState`, the metrics classes
  and corresponding carousel/placement function.
- **Console glow/backdrop or overall chrome colors:** `consoleSelectionGlow`,
  `ConsoleBackdrop` and `systemColor` in `ConsoleLibrary.kt`, plus the shared
  tokens/background helpers in `OmnidroidTheme.kt`.
- **Grid columns/rows by density:** `libraryPortraitColumns`/`libraryGridRows`
  in `LibraryScreen.kt`; cell geometry and spacing in `LibraryPagedContent.kt`.
- **Grid caption/overlay/Continue appearance:** `OmnidroidGameCard.kt`; its
  shared-transition wrapper lives in `LibraryGameCells.kt`.
- **List typography, thumbnail, row inset or badges:** `LibraryGameRow` in
  `LibraryGameCells.kt`, with height/spacing supplied by `LibraryPagedContent.kt`
  and `AdaptiveDimensions.kt`.
- **Search, sorting, favorites/system result selection:** VM flow plus
  `GameDao.selectPaged`/`GameOrder`. Change presentation controls in the top bar
  only when the interaction itself changes.
- **Dock membership, startup console or counts:** registered/startup stores,
  VM sidebar/count flows, and `PortraitConsoleBar`/`LibrarySidebar`.
- **Memory growth, distant jumps or paging throughput:** `LibraryPaging.kt`,
  the VM caching boundary, stable placeholder geometry and the paging tests.
- **Wrong/low-resolution/missing covers:** caller decode size, image request
  construction, custom/processed/remote source selection and cache keys. Check
  Console's separate image call sites too.
- **Details animation or a Continue-specific layout loop:** both lazy-item
  identity and shared-cover identity, including the destination-side key.
- **Launch/menu behavior:** host callbacks and `LibraryGameMenuActions`/
  [GameInteractor.kt][interactor]; retain the renderer/side-effect boundary.
- **Controller issues:** host wiring, `ControllerInputBridge`, navigation/focus
  helpers, and Console's local key handler.
- **Scan/progress issues:** scheduler/worker/monitor/VM first, then top-bar/notch
  presentation.

When a change affects one of these contracts, update its producers, consumers
and focused tests together. Preserve viewport-bounded composition and avoid
turning `games.itemCount` into a full-list materialization just to lay out cards.

## 13. Verification and reproduction

### Tests and historical evidence

- [LibraryPagingRegressionTest.kt][regression] has a delayed 20,000-row fixture
  checking pinned/normal/placeholder key uniqueness, efficient distant jumps,
  page eviction within one source generation, and List position restoration
  after visiting Grid. Its second test checks landscape Continue + ordinary
  covers at all three densities, followed by List.
- [LibraryCapacityTest.kt][capacity] renders the production screen and VM against
  a separate on-disk Room DB named `library-capacity-test`. It seeds synthetic
  NES/SNES rows in batches, uses 256 repeated local covers, jumps through the
  dataset, swipes, searches for the final game and returns to the start.
- [LibraryStressActivity.kt][testhost] is the non-exported debug-only host,
  registered in the [debug manifest][testmanifest]. The test runner is configured
  in [omnidroid-app/build.gradle.kts][appbuild].
- [scripts/stress_library.py][runner] runs fresh-process trials per
  count/layout/orientation. It saves device metadata, incremental JSON results,
  instrumentation/logcat, live graphics statistics and optional screenshots.
- Rebuild verification on 2026-11-10 included 26 passing real-screen trials and
  two focused regressions. Both rebuilt views passed 100,000 games in portrait
  and landscape, including Continue. The earlier key-collision and shared-cover
  failures described above were fixed before the final verification.

The runner writes disposable evidence under `build/library-capacity/`; the
rebuild used `rebuilt-regression`, `rebuilt-large`, `rebuilt-featured-verified`
and `rebuilt-final` subdirectories. These ignored build artifacts may be absent
in another checkout. Regenerate them with the commands below when needed.

These are debug-build results on the tested S24 Ultra, not a universal crash
count or a release frame-time benchmark. A million-row DB and a million
simultaneously resident objects are different workloads. The current default
sweep stops at 100,000 games.

### Build and install for the tested ARM64 setup

From the repository root in PowerShell, with an unlocked authorized device:

```powershell
.\gradlew.bat :omnidroid-app:assembleFreeDynamicDebug :omnidroid-app:assembleFreeDynamicDebugAndroidTest -PabiFilters=arm64-v8a

$adb = 'C:\Users\Bob\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial = 'DEVICE_SERIAL'
& $adb devices -l

$debugApk = Get-ChildItem 'omnidroid-app/build/outputs/apk/freeDynamic/debug/*-arm64-v8a-debug-*.apk' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
& $adb -s $serial install -r $debugApk.FullName
& $adb -s $serial install -r 'omnidroid-app/build/outputs/apk/androidTest/freeDynamic/debug/omnidroid-app-free-dynamic-debug-androidTest.apk'
```

Adjust the SDK path, ABI and APK selection for another environment. Keep device
selection explicit. The tested app package is `com.omnidroid.debug`; the test
runner is `com.omnidroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.

Run the focused regressions:

```powershell
& $adb -s $serial shell am instrument -w -r -e class com.omnidroid.app.testing.LibraryPagingRegressionTest com.omnidroid.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Run a quick all-view check, followed by the relevant larger Grid/List sweep:

```powershell
python scripts/stress_library.py --adb $adb --serial $serial --counts 1000 --layouts CONSOLE,GRID,LIST --orientations portrait,landscape --swipes 10 --zoom 0 --featured --screenshots
python scripts/stress_library.py --adb $adb --serial $serial --counts 1000,10000,100000 --layouts GRID,LIST --orientations portrait,landscape --swipes 30
```

Test the other densities with `--zoom 1` and `--zoom 2` as appropriate. The
default timeout is 240 seconds per trial; `--out` selects an evidence directory.
No app/device tests were rerun merely to write this guide.

### Reading outcomes correctly

The runner requires both an explicit capacity PASS marker and JUnit success for
`pass`. It separates observed process crashes (`app_crash`), recognized duplicate
key rendering exceptions caught by the test framework (`ui_exception`), expected
game rendering deadlines (`render_timeout`), other test failures and whole-trial
timeouts. A test-runner error or a failure to become idle is not evidence of an
out-of-memory threshold. Read the stack and phase checkpoints.

The normal `retrograde` DB is not seeded/replaced by the fixture. Test layout,
zoom and sort preferences are briefly configured for VM construction and
restored; normal app startup services still exist. Generated covers/screenshots
remain in test cache/output locations. A force-killed test cannot run its
`finally`; the next trial deletes/recreates its dedicated test DB.

The repository formatter previously failed with a Kotlin/ktlint
`KtTokens.HEADER_KEYWORD` linkage error. That tooling issue is separate from
runtime rendering. Recheck its current state before
reporting formatter success in a later chat.

## 14. Related documents

- [README.md](../README.md): player features and developer setup.
- [ROADMAP.md](../ROADMAP.md): brief completed changes and future work.
- [SIDE_MENUS_TECHNICAL_GUIDE.md](SIDE_MENUS_TECHNICAL_GUIDE.md): adjacent menu behavior.
- [SETTINGS_TECHNICAL_GUIDE.md](SETTINGS_TECHNICAL_GUIDE.md): adjacent settings behavior.

This file is the all-three-view handoff. Keep it synchronized when a later
change alters ownership, state keys, layout policy, transition identity or test
commands.

<!-- Source anchors verified against the working tree on 2026-11-11. -->

[main]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainActivity.kt#L143
[vm]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryViewModel.kt#L38
[filter]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryFilter.kt#L5
[screen]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryScreen.kt#L193
[topbar]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryTopBar.kt#L137
[notch]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/SyncProgressNotch.kt#L250
[startup]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryStartupStore.kt#L11
[registered]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/RegisteredSystemsStore.kt#L10
[startupdialogs]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/StartupDialogs.kt#L79
[console]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/ConsoleLibrary.kt#L263
[paged]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryPagedContent.kt#L61
[items]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryPagedItems.kt#L11
[cells]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryGameCells.kt#L63
[paging]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/LibraryPaging.kt#L6
[game]: ../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/entity/Game.kt#L41
[dao]: ../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/dao/GameDao.kt#L162
[systems]: ../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/GameSystem.kt#L27
[metasystems]: ../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/MetaSystemID.kt#L10
[adaptive]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/AdaptiveDimensions.kt#L109
[card]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidGameCard.kt#L58
[image]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidGameImage.kt#L19
[covers]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/covers/CoverUtils.kt#L106
[coverstore]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/covers/CoverCacheStore.kt#L26
[prefetch]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/LibraryCoverPrefetcher.kt#L13
[details]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamedetails/GameDetailsScreen.kt#L105
[controller]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/controller/ControllerNavigation.kt#L13
[focus]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/controller/ControllerFocus.kt#L47
[bridge]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/controller/ControllerInputBridge.kt#L9
[menu]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/gamemenu/LibraryGameMenu.kt#L90
[menuactions]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/gamemenu/LibraryGameMenuActions.kt#L8
[scheduler]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/LibraryIndexScheduler.kt#L9
[indexwork]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/LibraryIndexWork.kt#L20
[operations]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/PendingOperationsMonitor.kt#L13
[watcher]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/LocalFolderWatcher.kt#L15
[periodic]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/library/LibraryPeriodicScanScheduler.kt#L13
[regression]: ../omnidroid-app/src/androidTest/java/com/omnidroid/app/testing/LibraryPagingRegressionTest.kt#L46
[capacity]: ../omnidroid-app/src/androidTest/java/com/omnidroid/app/testing/LibraryCapacityTest.kt#L54
[testhost]: ../omnidroid-app/src/debug/java/com/omnidroid/app/testing/LibraryStressActivity.kt#L6
[testmanifest]: ../omnidroid-app/src/debug/AndroidManifest.xml#L3
[appbuild]: ../omnidroid-app/build.gradle.kts#L1781
[runner]: ../scripts/stress_library.py#L21
[theme]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidTheme.kt#L247
[gameutils]: ../omnidroid-app/src/main/java/com/omnidroid/app/utils/games/GameUtils.kt#L9
[sessions]: ../retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/dao/GameSessionDao.kt#L11
[playtime]: ../omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamedetails/PlayTimeStore.kt#L8
[interactor]: ../omnidroid-app/src/main/java/com/omnidroid/app/shared/GameInteractor.kt#L31
