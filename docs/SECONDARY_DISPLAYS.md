# Secondary displays

The first version shows the Omnidroid icon and a welcome message on each supported extra display. It does not move games or split emulated screens. The application stays wherever Android or the launcher placed it.

## Ownership and discovery

`DisplayClient` registers application lifecycle callbacks in both the main and `:game` processes. While primary activities are visible, a lightweight process-local display listener watches for connections and disconnections. The client binds to the non-exported main-process `DisplaySessionService` only when an accessible display exists outside its primary activities. Messenger messages describe the client's visible display IDs and foreground state. The service owns detailed discovery, output windows, and the state published to clients. Helper output activities never count as primary app usage.

The service has one output per display. A short lifecycle handoff delay avoids unnecessary dismissal between activities. Binder death removes abandoned clients. When no primary activity is foreground, output is dismissed. No placeholder wake lock or foreground service is used. A game client keeps the main-process coordinator available during gameplay only when extra displays are connected.

## Single-screen fast path

With only the current app display available, the client does not bind or start the coordinator, create output windows, attach view-layout listeners, query full display metrics or activity-launch permissions, or send lifecycle snapshots between processes. The Messenger endpoint and service connection are created lazily. A display listener checks the accessible display IDs at initial registration, explicit refresh, and add/remove events; ordinary display-change events do no client-side work. There is no polling loop or per-frame display processing.

The game process can therefore watch for a TV connection without keeping the main process bound through this feature. Connecting an extra display activates the coordinator automatically, including during gameplay. Disconnecting the last extra display releases the binding and removes layout tracking. The listener is unregistered after the client's primary activities leave the screen, with a short grace period for activity handoffs.

The Displays dialog remains ready on a single-screen device, and changing its automatic setting or refreshing it does not start the service. The state's `connected` field means that the visible client has a ready inventory, which can come from the local fast path; it does not by itself imply a coordinator connection.

`DisplayRepository` enumerates every accessible valid display instead of returning only the first non-empty display category. Metadata includes power state, dimensions, density, rotation, flags, presentation support, and permission for the helper activity. Device model names, hardcoded IDs, hidden APIs, and display-name heuristics do not determine behavior. Presentation support and activity-launch permission do not establish touchscreen support.

## Output policy

- Exclude all displays containing visible primary Omnidroid activities, including a non-default display.
- Prefer a non-focusable, non-touchable `Presentation` when advertised and allowed.
- When a presentation cannot be used, consider a non-exported helper activity only on Android 10 or newer with secondary-activity support and a positive launch-permission check. Older systems retain presentation support.
- Launch helper activities from a foreground activity, in a separate task, with a display-specific launch option. Validate the actual display on arrival. A session token rejects stale acknowledgments; a timeout handles missing acknowledgments. Close the helper if it takes focus or interrupts the original activity. Report unavailable instead of automatically looping.
- Preserve the private-display flag as metadata, but let Android enforce ownership and access. An app-owned private display can support a presentation; an inaccessible display cannot. Guard actual window creation and never bypass platform restrictions.
- Keep sleeping displays in the inventory but dismiss their output. Recreate output after wake or a meaningful metadata change.
- Mark output active only after the presentation has shown or the helper has acknowledged layout.
- Share the same native placeholder view between both output paths. Native views avoid dependence on a background activity's Compose lifecycle. Use the output's own context and resources.

The automatic-output setting uses the app's existing Harmony preferences, allowing either process to read it without a service connection. The previous main-process preference file is read as a fallback until the setting is changed, preserving existing choices. Per-display hide choices and failures are scoped to the current service/connection session. Numeric display IDs are never persisted; removing a display drops those choices. Explicit Show/Retry or a capability change can retry rejected output.

## UI and connection limits

The library's Displays control reports actual output state and offers an automatic switch, per-display Show/Hide/Retry, and Connection settings. Hiding output does not disconnect a system route. Connection settings are handled by Android; Omnidroid does not silently disconnect TVs.

Thor presentation panels and compatible TVs use presentation output. Pocket DS configurations can use either output path according to advertised capabilities and actual results. If the main app is placed on the bottom screen, output on the other screen is attempted only where Android permits it.

A TV connection must expose an independently usable Android display to receive different content. Mirroring-only connections retain system mirroring. A Google Cast receiver is not automatically an Android secondary display; a dedicated sender/receiver integration is outside this version.

## Verification

Pure JVM policy tests cover coordinator activation, the single-screen path with different display IDs, background or fully occupied displays, multiple display types together, non-default primary screens, private-display capabilities and rejected/unsupported output, sleep/wake, background/disabled/hidden states, and success-versus-candidate indicators.

`SecondaryDisplaysTest` exercises public and private virtual displays, concurrent output, focus retention, individual hiding, automatic mode, sleep/wake, recreation, background/foreground, reconnecting, and a real `:game` process handoff through a debug-only host. It also verifies that no coordinator service runs during single-screen settings changes, recreation, or gameplay, that a TV connection during gameplay activates output, and that disconnecting it releases the service again. It exports the rendered handheld placeholder to the target app's external files directory as `secondary-display-test.png` for visual inspection.

Run the focused checks in PowerShell:

```powershell
.\gradlew.bat :omnidroid-app:testFreeBundleDebugUnitTest
.\gradlew.bat :omnidroid-app:connectedFreeBundleDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.omnidroid.app.testing.SecondaryDisplaysTest'
```

Before claiming hardware compatibility, verify on Pocket DS, Thor, HDMI, an independently addressable wireless display, and a mirroring-only connection. Check primary-screen placement, controller focus, actual game pause behavior, app backgrounding, process death, lid/sleep/wake, unplug/replug, changing display settings, and moving the app between screens. Emulator results do not establish OEM input or display behavior.

Local verification on 2026-11-14: FreeBundleDebug builds, all 60 JVM tests pass (including ten display-policy tests), all four focused Android 10 x86_64 emulator tests pass, and PlayBundleDebug Kotlin compilation passes. The rendered placeholder was captured and visually checked. A scoped ktlint check of the new files also passes using the cached compiler version required by ktlint; the override was confined to the local check and does not change project build settings. On this emulator, the activity-only fallback is rejected when it disrupts source-window focus, and remains unavailable without repeated launches. The single-screen regression verifies actual service absence before and during gameplay, saved settings without service startup, and coordinator activation and release when connecting and disconnecting a display during gameplay. These are lifecycle checks, not an FPS or memory benchmark.
