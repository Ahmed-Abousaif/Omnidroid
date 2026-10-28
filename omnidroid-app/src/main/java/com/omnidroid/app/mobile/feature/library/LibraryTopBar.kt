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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
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
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.SystemStatusIndicators
import com.omnidroid.app.mobile.shared.compose.ui.hugDisplayCutout
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.core.SyncProgress
import kotlin.math.roundToInt

val LibraryTopBarRowHeight = 40.dp
val LibraryTopBarPortraitHeight = 82.dp
private val LibrarySearchCollapsedWidth = 200.dp

@Composable
fun LibraryTopBar(
    modifier: Modifier = Modifier,
    operationInProgress: Boolean,
    syncProgress: SyncProgress? = null,
    gamepadConnected: Boolean,
    onRandomPressed: () -> Unit,
    onCastPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
    onProfilePressed: () -> Unit = {},
    sort: LibrarySort = LibrarySort.TITLE,
    onSortChange: (LibrarySort) -> Unit = {},
    overlayTitleId: Int? = null,
    onBackPressed: () -> Unit = {},
    compactProgress: Float = 0f,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    casting: Boolean = false,
    consoleTitleId: Int? = null,
    consoleGameCount: Int? = null,
    portrait: Boolean = false,
    portraitColumnWidth: Dp = Dp.Unspecified,
    detailsMode: Boolean = false,
) {
    val adaptiveSpec = LocalAdaptiveLayout.current
    val overlayMode = overlayTitleId != null
    val compact = if (overlayMode) 0f else compactProgress.coerceIn(0f, 1f)
    val showBack = !overlayMode && if (portrait) detailsMode else compact > 0.5f
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val controllerNav = LocalControllerNavigation.current
    val searchArmed = controllerNav?.searchArmed?.value == true
    var searchFocused by remember { mutableStateOf(false) }
    val searchExpanded = searchFocused || searchQuery.isNotEmpty()
    val sortMenu = remember { SortMenuState() }
    var displayedConsoleTitleId by remember { mutableStateOf<Int?>(null) }
    var displayedConsoleGameCount by remember { mutableStateOf<Int?>(null) }
    if (consoleTitleId != null) {
        displayedConsoleTitleId = consoleTitleId
        displayedConsoleGameCount = consoleGameCount
    }
    val dismissSearchOnOutsideTap =
        searchFocused && searchQuery.isEmpty() && !overlayMode && !showBack

    LaunchedEffect(showBack, overlayMode) {
        if (showBack || overlayMode) sortMenu.open = false
    }

    LaunchedEffect(showBack) {
        if (showBack) {
            searchFocused = false
            controllerNav?.searchArmed?.value = false
            controllerNav?.textInputActive = false
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(searchArmed) {
        if (searchArmed) {
            runCatching { controllerNav?.searchFocusRequester?.requestFocus() }
            keyboardController?.show()
        }
    }

    SideEffect {
        controllerNav?.dismissSearch = {
            controllerNav.searchArmed.value = false
            controllerNav.textInputActive = false
            searchFocused = false
            keyboardController?.hide()
            focusManager.clearFocus()
            controllerNav.contentFocusRequester?.let { runCatching { it.requestFocus() } }
        }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        if (portrait && !overlayMode && showBack) {
            PortraitDetailsTopBar(
                modifier = modifier,
                gamepadConnected = gamepadConnected,
                operationInProgress = operationInProgress,
                syncProgress = syncProgress,
                onBackPressed = onBackPressed,
            )
        } else if (portrait && !overlayMode && !showBack) {
            PortraitHomeTopBar(
                modifier = modifier,
                columnWidth = portraitColumnWidth,
                operationInProgress = operationInProgress,
                syncProgress = syncProgress,
                gamepadConnected = gamepadConnected,
                searchQuery = searchQuery,
                dismissSearchOnOutsideTap = dismissSearchOnOutsideTap,
                onRandomPressed = onRandomPressed,
                onCastPressed = onCastPressed,
                onSettingsPressed = onSettingsPressed,
                onProfilePressed = onProfilePressed,
                sort = sort,
                sortMenu = sortMenu,
                onSearchQueryChange = onSearchQueryChange,
                onClearSearch = {
                    onClearSearch()
                    searchFocused = false
                    controllerNav?.searchArmed?.value = false
                    controllerNav?.textInputActive = false
                    focusManager.clearFocus()
                },
                onSearchFocusChange = { focused ->
                    searchFocused = focused
                    controllerNav?.textInputActive = focused
                    if (!focused && controllerNav?.searchArmed?.value == true) {
                        controllerNav.searchArmed.value = false
                    }
                },
                onSearchActivate = { controllerNav?.searchArmed?.value = true },
                searchFocusRequester = controllerNav?.searchFocusRequester,
                searchCanFocus = !gamepadConnected || searchArmed,
                casting = casting,
                onDismissSearch = { focusManager.clearFocus() },
            )
        } else Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(if (dismissSearchOnOutsideTap) Modifier.fillMaxSize() else Modifier),
        ) {
            val sidebarInset = (adaptiveSpec.sidebarWidth - adaptiveSpec.sidebarButtonSize) / 2
            val backStart = if (overlayMode) 0.dp else sidebarInset
            if (dismissSearchOnOutsideTap) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures { focusManager.clearFocus() }
                            },
                )
            }
            // The backdrop runs edge to edge, under the cutout; only the bar contents are inset.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(adaptiveSpec.topBarRowHeight)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Transparent,
                            ),
                        ),
            )
            // Full-bleed bar: the cutout is top-centre in portrait and moves to a side edge in
            // landscape, where the bar runs into it at one end.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .then(
                            if (portrait) Modifier else Modifier.hugDisplayCutout(WindowInsetsSides.Horizontal),
                        ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(adaptiveSpec.topBarRowHeight)
                                .padding(start = if (overlayMode) 8.dp else 0.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                        overlayMode -> {
                            OverlayTitle(titleId = overlayTitleId!!, onBackPressed = onBackPressed)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        showBack -> {
                            Spacer(modifier = Modifier.width(backStart))
                            FloatingChromeButton(onClick = onBackPressed) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        else -> {
                            Spacer(modifier = Modifier.width(sidebarInset))
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val targetWidth =
                                    if (searchExpanded) {
                                        maxWidth
                                    } else {
                                        adaptiveSpec.searchWidth.coerceAtMost(maxWidth)
                                    }
                                val searchWidth by animateDpAsState(
                                    targetValue = targetWidth,
                                    animationSpec =
                                        tween(
                                            durationMillis = 240,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    label = "librarySearchWidth",
                                )
                                val showConsoleTitle = !searchExpanded && consoleTitleId != null
                                val consoleTitle = displayedConsoleTitleId
                                Row(
                                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LibrarySearchField(
                                        query = searchQuery,
                                        onQueryChange = onSearchQueryChange,
                                        onClear = {
                                            onClearSearch()
                                            searchFocused = false
                                            controllerNav?.searchArmed?.value = false
                                            controllerNav?.textInputActive = false
                                            focusManager.clearFocus()
                                        },
                                        onFocusChange = { focused ->
                                            searchFocused = focused
                                            controllerNav?.textInputActive = focused
                                            if (!focused && controllerNav?.searchArmed?.value == true) {
                                                controllerNav.searchArmed.value = false
                                            }
                                        },
                                        onActivate = {
                                            controllerNav?.searchArmed?.value = true
                                        },
                                        focusRequester = controllerNav?.searchFocusRequester,
                                        // Controller D-pad skips search unless Y armed it.
                                        canFocus = !gamepadConnected || searchArmed,
                                        trailing = { LibrarySortButton(sort = sort, menu = sortMenu) },
                                        modifier = Modifier.width(searchWidth),
                                    )
                                    AnimatedVisibility(
                                        visible = showConsoleTitle,
                                        enter =
                                            fadeIn(animationSpec = tween(180)) +
                                                expandHorizontally(expandFrom = Alignment.Start),
                                        exit =
                                            fadeOut(animationSpec = tween(180)) +
                                                shrinkHorizontally(shrinkTowards = Alignment.Start),
                                    ) {
                                        if (consoleTitle != null) {
                                            val titleText =
                                                if (displayedConsoleGameCount != null) {
                                                    "${stringResource(consoleTitle)} (${displayedConsoleGameCount})"
                                                } else {
                                                    stringResource(consoleTitle)
                                                }
                                            Text(
                                                text = titleText,
                                                style =
                                                    MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = 14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                    }
                    SystemStatusIndicators(gamepadConnected = gamepadConnected)
                    AnimatedVisibility(
                        visible = !overlayMode && compact < 0.5f,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(10.dp))
                            LibraryUtilityButtons(
                                casting = casting,
                                iconSize = 18.dp,
                                onRandomPressed = onRandomPressed,
                                onCastPressed = onCastPressed,
                                onSettingsPressed = onSettingsPressed,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ProfileChromeButton(onClick = onProfilePressed, iconSize = 18.dp)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = operationInProgress,
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
                ) {
                    SyncProgressBar(
                        syncProgress = syncProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
        }
        LibrarySortMenu(menu = sortMenu, sort = sort, onSortChange = onSortChange)
    }
}

@Composable
private fun PortraitHomeTopBar(
    modifier: Modifier,
    columnWidth: Dp,
    operationInProgress: Boolean,
    syncProgress: SyncProgress? = null,
    gamepadConnected: Boolean,
    searchQuery: String,
    dismissSearchOnOutsideTap: Boolean,
    onRandomPressed: () -> Unit,
    onCastPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
    onProfilePressed: () -> Unit,
    sort: LibrarySort,
    sortMenu: SortMenuState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchActivate: () -> Unit,
    searchFocusRequester: FocusRequester?,
    searchCanFocus: Boolean,
    casting: Boolean,
    onDismissSearch: () -> Unit,
) {
    val widthModifier =
        if (columnWidth != Dp.Unspecified) Modifier.width(columnWidth) else Modifier.fillMaxWidth()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (dismissSearchOnOutsideTap) Modifier.fillMaxSize() else Modifier),
    ) {
        if (dismissSearchOnOutsideTap) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures { onDismissSearch() }
                        },
            )
        }
        val adaptiveSpec = LocalAdaptiveLayout.current
        Column(
            modifier =
                widthModifier
                    .height(adaptiveSpec.topBarPortraitHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                        ),
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(adaptiveSpec.topBarRowHeight)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileChromeButton(onClick = onProfilePressed, iconSize = 16.dp)
                Spacer(modifier = Modifier.width(6.dp))
                LibraryUtilityButtons(
                    casting = casting,
                    iconSize = 16.dp,
                    onRandomPressed = onRandomPressed,
                    onCastPressed = onCastPressed,
                    onSettingsPressed = onSettingsPressed,
                )
                Spacer(modifier = Modifier.weight(1f))
                SystemStatusIndicators(gamepadConnected = gamepadConnected)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(adaptiveSpec.topBarRowHeight)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibrarySearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = onClearSearch,
                    onFocusChange = onSearchFocusChange,
                    onActivate = onSearchActivate,
                    focusRequester = searchFocusRequester,
                    canFocus = searchCanFocus,
                    trailing = { LibrarySortButton(sort = sort, menu = sortMenu) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedVisibility(
            visible = operationInProgress,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
            modifier =
                widthModifier
                    .align(Alignment.TopCenter)
                    .padding(top = adaptiveSpec.topBarPortraitHeight),
        ) {
            SyncProgressBar(
                syncProgress = syncProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PortraitDetailsTopBar(
    modifier: Modifier,
    gamepadConnected: Boolean,
    operationInProgress: Boolean,
    syncProgress: SyncProgress? = null,
    onBackPressed: () -> Unit,
) {
    val adaptiveSpec = LocalAdaptiveLayout.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(adaptiveSpec.topBarRowHeight)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingChromeButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SystemStatusIndicators(gamepadConnected = gamepadConnected)
        }
        AnimatedVisibility(
            visible = operationInProgress,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
        ) {
            SyncProgressBar(
                syncProgress = syncProgress,
                modifier = Modifier.fillMaxWidth(),
            )
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
        targetValue = if (pressed || focused) ChromeButtonPressedColor else Color.Transparent,
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
            modifier = Modifier.size(iconSize + 2.dp),
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
    Box(
        modifier =
            Modifier
                .size(chromeButtonSize() - 6.dp)
                .onGloballyPositioned { menu.anchor = it.boundsInRoot() }
                .controllerFocusGlow(CircleShape)
                .clip(CircleShape)
                .clickable { menu.open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort),
            tint = if (sort != LibrarySort.TITLE) LibraryNeonGreen else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(16.dp),
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
                    color = Color(0xFF1C1C1E),
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
                color = if (selected) LibraryNeonGreen else Color.White,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = option.icon(),
                contentDescription = null,
                tint = if (selected) LibraryNeonGreen else Color.White.copy(alpha = 0.72f),
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
private fun LibrarySearchField(
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
        color = Color(0xFF161616),
        tonalElevation = 0.dp,
        border =
            if (active) {
                BorderStroke(1.5.dp, LibraryNeonGreen)
            } else {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(start = 10.dp, end = if (trailing != null) 3.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.title_search),
                modifier = Modifier.size(16.dp),
                tint = Color.White.copy(alpha = 0.78f),
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
                cursorBrush = SolidColor(Color.White),
                textStyle =
                    TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.title_search),
                                color = Color.White.copy(alpha = 0.42f),
                                fontSize = 13.sp,
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
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clickable(onClick = onClear)
                            .padding(3.dp),
                )
            }
            if (trailing != null) {
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(1.dp)
                            .height(height / 2)
                            .background(Color.White.copy(alpha = 0.12f)),
                )
                trailing()
            }
        }
    }
}

@Composable
private fun OverlayTitle(
    titleId: Int,
    onBackPressed: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FloatingChromeButton(onClick = onBackPressed) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(titleId),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 12.dp),
        )
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

private val ChromeButtonPressedColor = Color.White.copy(alpha = 0.14f)

@Composable
private fun chromeButtonSize(): Dp = if (LocalAdaptiveLayout.current.isCompact) 32.dp else 36.dp

/** Borderless top bar icon button; it only shows a fill while pressed. */
@Composable
fun FloatingChromeButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) ChromeButtonPressedColor else Color.Transparent,
        animationSpec = tween(120),
        label = "chromeButtonPressed",
    )
    Box(
        modifier =
            Modifier
                .size(chromeButtonSize())
                .controllerFocusGlow(CircleShape)
                .clip(CircleShape)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun SyncProgressBar(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    val displayText =
        syncProgress?.stepText?.ifBlank { null }
            ?: stringResource(R.string.library_scanning)
    val hasCount = (syncProgress?.current ?: 0) > 0 && (syncProgress?.total ?: 0) > 0
    val targetProgress =
        if (hasCount) {
            (syncProgress!!.current.toFloat() / syncProgress.total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "syncProgressAnimation",
    )

    Surface(
        modifier = modifier,
        color = Color(0xF2121212),
        border = BorderStroke(0.5.dp, Color(0xFF262626)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LibraryNeonGreen.copy(alpha = dotAlpha)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayText,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp,
                            ),
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasCount) {
                    Text(
                        text = "${syncProgress!!.current} of ${syncProgress.total}",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                        color = LibraryNeonGreen,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (syncProgress != null && !syncProgress.isIndeterminate && hasCount) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    color = LibraryNeonGreen,
                    trackColor = Color(0x3338EF7D),
                )
            } else {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    color = LibraryNeonGreen,
                    trackColor = Color(0x3338EF7D),
                )
            }
        }
    }
}

