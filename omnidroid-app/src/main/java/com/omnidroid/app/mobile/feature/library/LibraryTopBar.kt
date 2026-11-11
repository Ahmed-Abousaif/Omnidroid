package com.omnidroid.app.mobile.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.AppTopBar
import com.omnidroid.app.mobile.shared.compose.ui.FloatingChromeButton
import com.omnidroid.app.mobile.shared.compose.ui.chromeButtonSize
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.core.SyncProgress
import kotlin.math.roundToInt

/** Keeps app chrome stable across navigation, with optional library controls. */
@Composable
fun LibraryTopBar(
    gamepadConnected: Boolean,
    onRandomPressed: () -> Unit,
    onCastPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    syncProgress: SyncProgress? = null,
    onProfilePressed: () -> Unit = {},
    sort: LibrarySort = LibrarySort.TITLE,
    onSortChange: (LibrarySort) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    casting: Boolean = false,
    consoleTitleId: Int? = null,
    consoleGameCount: Int? = null,
    portrait: Boolean = false,
    showLibraryControls: Boolean = true,
    onBackPressed: (() -> Unit)? = null,
) {
    val spec = LocalAdaptiveLayout.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val controllerNav = LocalControllerNavigation.current
    val searchArmed = controllerNav?.searchArmed?.value == true
    var searchFocused by remember { mutableStateOf(false) }
    val searchExpanded = searchFocused || searchQuery.isNotEmpty()
    val sortMenu = remember { SortMenuState() }
    val dismissSearch = {
        searchFocused = false
        controllerNav?.searchArmed?.value = false
        controllerNav?.textInputActive = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(showLibraryControls) {
        if (!showLibraryControls) {
            sortMenu.open = false
            dismissSearch()
        }
    }
    LaunchedEffect(searchArmed, showLibraryControls) {
        if (searchArmed && showLibraryControls) {
            runCatching { controllerNav?.searchFocusRequester?.requestFocus() }
            keyboardController?.show()
        }
    }
    DisposableEffect(controllerNav) {
        onDispose {
            dismissSearch()
            controllerNav?.dismissSearch = {}
        }
    }
    SideEffect {
        controllerNav?.dismissSearch = {
            dismissSearch()
            controllerNav.contentFocusRequester?.let { runCatching { it.requestFocus() } }
        }
    }

    val searchField: @Composable (Modifier) -> Unit = { searchModifier ->
        LibrarySearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = {
                onClearSearch()
                dismissSearch()
            },
            onFocusChange = { focused ->
                searchFocused = focused
                controllerNav?.textInputActive = focused
                if (!focused) controllerNav?.searchArmed?.value = false
            },
            onActivate = { controllerNav?.searchArmed?.value = true },
            focusRequester = controllerNav?.searchFocusRequester,
            canFocus = !gamepadConnected || searchArmed,
            trailing = { LibrarySortButton(sort = sort, menu = sortMenu) },
            modifier = searchModifier,
        )
    }
    val actions: @Composable () -> Unit = {
        val iconSize = (if (portrait) 16.dp else 18.dp) * spec.uiScale
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (portrait) {
                ProfileChromeButton(onClick = onProfilePressed, iconSize = iconSize)
                Spacer(modifier = Modifier.width(6.dp))
            }
            LibraryUtilityButtons(
                casting = casting,
                iconSize = iconSize,
                onRandomPressed = onRandomPressed,
                onCastPressed = onCastPressed,
                onSettingsPressed = onSettingsPressed,
            )
            if (!portrait) {
                Spacer(modifier = Modifier.width(8.dp))
                ProfileChromeButton(onClick = onProfilePressed, iconSize = iconSize)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showLibraryControls && searchFocused && searchQuery.isEmpty()) {
            Box(
                modifier =
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures { dismissSearch() }
                    },
            )
        }
        AppTopBar(
            gamepadConnected = gamepadConnected,
            portrait = portrait,
            bottomPadding = bottomPadding,
            syncProgress = syncProgress,
            onBackPressed = onBackPressed,
            controlsVisible = showLibraryControls,
            leadingContent = {
                if (portrait) {
                    actions()
                } else {
                    LibrarySearchRow(
                        expanded = searchExpanded,
                        consoleTitleId = consoleTitleId,
                        consoleGameCount = consoleGameCount,
                        searchField = searchField,
                    )
                }
            },
            trailingContent = if (portrait) null else actions,
            bottomContent = if (portrait) ({ searchField(Modifier.fillMaxWidth()) }) else null,
        )
        LibrarySortMenu(menu = sortMenu, sort = sort, onSortChange = onSortChange)
    }
}

@Composable
private fun LibrarySearchRow(
    expanded: Boolean,
    consoleTitleId: Int?,
    consoleGameCount: Int?,
    searchField: @Composable (Modifier) -> Unit,
) {
    val spec = LocalAdaptiveLayout.current
    // Keep the last label available while its exit animation finishes.
    var displayedTitleId by remember { mutableStateOf<Int?>(null) }
    var displayedGameCount by remember { mutableStateOf<Int?>(null) }
    if (consoleTitleId != null) {
        displayedTitleId = consoleTitleId
        displayedGameCount = consoleGameCount
    }
    val startPadding = ((spec.sidebarWidth - spec.sidebarButtonSize) / 2 - 8.dp).coerceAtLeast(0.dp)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = 10.dp)) {
        val searchWidth by animateDpAsState(
            targetValue = if (expanded) maxWidth else spec.searchWidth.coerceAtMost(maxWidth),
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "librarySearchWidth",
        )
        Row(modifier = Modifier.fillMaxWidth().clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
            searchField(Modifier.width(searchWidth))
            AnimatedVisibility(
                visible = !expanded && consoleTitleId != null,
                enter = fadeIn(tween(180)) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut(tween(180)) + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                displayedTitleId?.let { titleId ->
                    val title = stringResource(titleId)
                    Text(
                        text = displayedGameCount?.let { "$title ($it)" } ?: title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = chromeColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

/** Settings, cast and play-random: borderless utility actions, unlike the dock's console circles. */
@Composable
private fun LibraryUtilityButtons(
    casting: Boolean,
    iconSize: Dp,
    onRandomPressed: () -> Unit,
    onCastPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FloatingChromeButton(onClick = onSettingsPressed) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier.size(iconSize),
            )
        }
        FloatingChromeButton(onClick = onCastPressed) {
            Icon(
                imageVector = if (casting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                contentDescription = stringResource(R.string.cast),
                tint = if (casting) LibraryNeonGreen else LocalContentColor.current,
                modifier = Modifier.size(iconSize),
            )
        }
        FloatingChromeButton(onClick = onRandomPressed) {
            Icon(
                imageVector = Icons.Outlined.Casino,
                contentDescription = stringResource(R.string.library_play_random),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun ProfileChromeButton(
    onClick: () -> Unit,
    iconSize: Dp,
) {
    val shape = RoundedCornerShape(30)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed || focused) chromeColors.pressed else Color.Transparent,
        animationSpec = tween(120),
        label = "profileButtonPressed",
    )
    Box(
        modifier =
            Modifier
                .size(chromeButtonSize())
                .controllerFocusGlow(shape)
                .clip(shape)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_profile_robot),
            contentDescription = stringResource(R.string.title_profile),
            tint = LibraryNeonGreen,
            modifier = Modifier.size(iconSize + 2.dp * LocalAdaptiveLayout.current.uiScale),
        )
    }
}

@Stable
private class SortMenuState {
    var open by mutableStateOf(false)
    var anchor by mutableStateOf(Rect.Zero)
}

@Composable
private fun LibrarySortButton(
    sort: LibrarySort,
    menu: SortMenuState,
) {
    val scale = LocalAdaptiveLayout.current.uiScale
    Box(
        modifier =
            Modifier
                .size(chromeButtonSize() - 6.dp * scale)
                .onGloballyPositioned { menu.anchor = it.boundsInRoot() }
                .controllerFocusGlow(CircleShape)
                .clip(CircleShape)
                .clickable { menu.open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort),
            tint = if (sort != LibrarySort.TITLE) LibraryNeonGreen else chromeColors.muted,
            modifier = Modifier.size(16.dp * scale),
        )
    }
}

/**
 * Drawn in the activity window rather than a popup window: some foldable configurations leave
 * popup surfaces hidden after a display switch.
 */
@Composable
private fun LibrarySortMenu(
    menu: SortMenuState,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
) {
    BackHandler(enabled = menu.open) { menu.open = false }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = menu.open
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val edge = with(density) { 8.dp.roundToPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val selectedRequester = remember { FocusRequester() }
    LaunchedEffect(menu.open) {
        if (menu.open) runCatching { selectedRequester.requestFocus() }
    }
    Layout(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() },
        content = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { menu.open = false } },
            )
            AnimatedVisibility(
                visibleState = visibility,
                enter =
                    fadeIn(tween(120)) +
                        scaleIn(tween(120), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
                exit =
                    fadeOut(tween(90)) +
                        scaleOut(tween(90), targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = chromeColors.menu,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .width(IntrinsicSize.Max)
                                .padding(vertical = 8.dp),
                    ) {
                        LibrarySort.entries.forEach { option ->
                            LibrarySortMenuItem(
                                option = option,
                                selected = option == sort,
                                onClick = {
                                    menu.open = false
                                    onSortChange(option)
                                },
                                modifier =
                                    if (option == sort) Modifier.focusRequester(selectedRequester) else Modifier,
                            )
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val scrim = measurables[0].measure(constraints)
        val panel = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            scrim.place(0, 0)
            if (panel != null) {
                val maxX = (constraints.maxWidth - panel.width - edge).coerceAtLeast(edge)
                val x = ((menu.anchor.right - origin.x).roundToInt() - panel.width).coerceIn(edge, maxX)
                val y = (menu.anchor.bottom - origin.y).roundToInt() + gap
                panel.place(x, y)
            }
        }
    }
}

@Composable
private fun LibrarySortMenuItem(
    option: LibrarySort,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        modifier = modifier,
        text = {
            Text(
                text = stringResource(option.labelRes()),
                color = if (selected) LibraryNeonGreen else chromeColors.content,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = option.icon(),
                contentDescription = null,
                tint = if (selected) LibraryNeonGreen else chromeColors.muted,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = LibraryNeonGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
    )
}

private fun LibrarySort.labelRes(): Int =
    when (this) {
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.RECENTLY_PLAYED -> R.string.library_sort_recent
        LibrarySort.FAVORITES_FIRST -> R.string.library_sort_favorites
        LibrarySort.CONSOLE -> R.string.library_sort_console
    }

private fun LibrarySort.icon(): ImageVector =
    when (this) {
        LibrarySort.TITLE -> Icons.Filled.SortByAlpha
        LibrarySort.RECENTLY_PLAYED -> Icons.Outlined.History
        LibrarySort.FAVORITES_FIRST -> Icons.Filled.FavoriteBorder
        LibrarySort.CONSOLE -> Icons.Outlined.SportsEsports
    }

@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onActivate: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    canFocus: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val adaptiveSpec = LocalAdaptiveLayout.current
    val scale = adaptiveSpec.uiScale
    var focused by remember { mutableStateOf(false) }
    val active = focused || query.isNotEmpty()
    val height = chromeButtonSize()
    Surface(
        modifier =
            modifier
                .height(height)
                .controllerFocusGlow(CircleShape)
                .then(
                    if (!canFocus && onActivate != null) {
                        Modifier
                            .focusProperties { this.canFocus = false }
                            .clickable(onClick = onActivate)
                    } else {
                        Modifier
                    },
                ),
        shape = CircleShape,
        color = chromeColors.panel,
        tonalElevation = 0.dp,
        border =
            if (active) {
                BorderStroke(1.5.dp, LibraryNeonGreen)
            } else {
                BorderStroke(1.dp, chromeColors.panelBorder)
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(start = 10.dp * scale, end = if (trailing != null) 3.dp * scale else 6.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.title_search),
                modifier = Modifier.size(16.dp * scale),
                tint = chromeColors.muted,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = canFocus,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .focusProperties { this.canFocus = canFocus }
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                        },
                singleLine = true,
                cursorBrush = SolidColor(chromeColors.content),
                textStyle =
                    TextStyle(
                        color = chromeColors.content,
                        fontSize = (13 * scale).sp,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.title_search),
                                color = chromeColors.faint,
                                fontSize = (13 * scale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = chromeColors.muted,
                    modifier =
                        Modifier
                            .size(22.dp * scale)
                            .clickable(onClick = onClear)
                            .padding(3.dp * scale),
                )
            }
            if (trailing != null) {
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp * scale)
                            .width(1.dp)
                            .height(height / 2)
                            .background(chromeColors.divider),
                )
                trailing()
            }
        }
    }
}

@Composable
fun RescanIcon(
    spinning: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (spinning) {
        SpinningRescanIcon(modifier = modifier, contentDescription = contentDescription)
    } else {
        Icon(
            imageVector = Icons.Outlined.Sync,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun SpinningRescanIcon(
    modifier: Modifier,
    contentDescription: String?,
) {
    val rotation by rememberInfiniteTransition(label = "rescan").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rescanRotation",
    )

    Icon(
        imageVector = Icons.Outlined.Sync,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { rotationZ = rotation },
    )
}

