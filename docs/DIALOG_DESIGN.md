# Shared dialogs

All app-owned modal dialogs use `OmnidroidDialog` in
`omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/dialog/`.
Android/Google-owned pickers, permissions, account and installation prompts retain
their platform UI. Side navigation and external-display presentations are separate
surfaces.

## Change the design once

- `OmnidroidDialogStyle`: panel/button rounding, compact spacing, width caps and touch targets. Buttons have a 36dp minimum visible height (25% shorter than 48dp); Material retains its surrounding 48dp interaction target, and large text can increase the visual height.
- `OmnidroidDialog`: window policy, safe bounds, typography, scrolling, focus and action layout.
- Colors come from `chromeColors.panel`, exactly like `SideMenuScaffold`; dark/light theme changes propagate automatically.
- `OmnidroidDialogButton`: primary green, neutral cancel/alternative and red destructive actions with white text. Use it for every dialog action; labels inherit the correct contrasting color.

Callers supply title/body content, confirm/cancel slots and callbacks. Set
`destructive = true` for a destructive main action, or give an individual button
`role = DialogActionRole.Destructive`. Do not use warning colors for ordinary
acknowledgement buttons. Progress dialogs can disable both dismissal policies.

The body scrolls by default. Do not nest another unbounded vertical scroll inside
it. Lazy grids/lists need finite constraints; `scrollBody = false` lets a bounded
body manage its own scrolling. Use `width = DialogWidth.Expanded` for artwork grids rather
than supplying a feature-specific window width.

Ordinary dialogs use `DialogWidth.Content` by default: they wrap their title,
body and actions between a 280dp minimum and the common width cap. The minimum
shrinks to the available safe width on narrower windows. Use `DialogWidth.Standard` for
containers that require a bounded measurement, such as `BoxWithConstraints`,
sliders or HTML/native content. Lazy grids use the expanded preset. Content
width relies on intrinsic measurements, so subcomposed bodies must use an
explicit bounded preset.

For text-entry dialogs use `width = DialogWidth.Input` and
`OmnidroidDialogTextField`. The compact panel is 312dp wide at most (280dp input
plus the common horizontal padding), and the field always fills the inner width.
The same field also fills wider content dialogs, such as cover search. Native
keyboard dialogs use the input width preset through the Activity bridge.

## Bounds and input

The shell measures its actual window, applies system/cutout/IME insets, and selects
the larger pane around a separating fold hinge. The panel is capped to that safe
area. Long bodies scroll; short windows or oversized headers/actions switch to a
scrollable panel. The action flow wraps long/localized labels onto additional rows.
Text respects system font scaling and rows retain at least 48dp touch targets.
Scrollable bodies show a persistent vertical track/thumb plus a **More below**
label and down arrow in reserved space beneath the viewport. The cue disappears
at the end without resizing the viewport; the scrollbar remains visible to show the current position. Short
windows use one scroll area for the entire panel; extremely short windows keep
the scrollbar and omit the text cue to preserve room for content. Neither indicator
covers content or actions. Bounded lazy content supplies `bodyScrollInfo`, as
the cover search grid does, to use the same indicators with its own scroll state.

Destructive dialogs focus Cancel once their window receives focus, and repeat
that choice when input switches to a keyboard/controller. Escape and controller B
dismiss cancelable dialogs. Binding prompts opt out of B handling so B can still
be bound. Android Back follows the configured dismissal policy.

Single- and multi-choice settings hold a draft until Confirm. Cancel/back/outside
discard it. Touch controls retain live preview, restore the opening settings on
Cancel, and keep the preview on Save. Native text input retains its incremental
core updates and Done/reopen behavior.

## Activity/View callers

Use `showOmnidroidDialog` with `OmnidroidDialogAction` values. It mounts the same
Compose component in a lifecycle-owned host and returns an idempotent dismiss
handle. It does not implement a second native dialog style. Custom Android views
can be placed in its body using `AndroidView`.

## Verification

- `DialogSafePaneTest`: ordinary windows, vertical/horizontal hinges, and separating folds.
- `OmnidroidDialogTest`: long content, controller focus, large RTL text, draft cancellation, keyboard input, Activity bridge cleanup, and compact/square/short window constraints.
- `ConsoleManagementScreenTest`: existing console confirmation flows.

Build free and Play variants when changing the shell. Device-test screenshots are
written to the debug app's external files directory as `dialogs-*.png`.
