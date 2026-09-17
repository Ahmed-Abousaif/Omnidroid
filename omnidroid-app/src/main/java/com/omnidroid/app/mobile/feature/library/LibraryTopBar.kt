package com.omnidroid.app.mobile.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsColumnGap
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsCoverWeight
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsScreenPadding
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.SystemStatusIndicators
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

val LibraryTopBarRowHeight = 40.dp
private val LibrarySearchCollapsedWidth = 200.dp
private val LibrarySearchFieldHeight = 36.dp

@Composable
fun LibraryTopBar(
    modifier: Modifier = Modifier,
    operationInProgress: Boolean,
    gamepadConnected: Boolean,
    onScanPressed: () -> Unit,
    onCastPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
    onProfilePressed: () -> Unit = {},
    overlayTitleId: Int? = null,
    onBackPressed: () -> Unit = {},
    compactProgress: Float = 0f,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    casting: Boolean = false,
    consoleTitleId: Int? = null,
    consoleGameCount: Int? = null,
) {
    val overlayMode = overlayTitleId != null
    val compact = if (overlayMode) 0f else compactProgress.coerceIn(0f, 1f)
    val showBack = !overlayMode && compact > 0.5f
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val controllerNav = LocalControllerNavigation.current
    val searchArmed = controllerNav?.searchArmed?.value == true
    var searchFocused by remember { mutableStateOf(false) }
    val searchExpanded = searchFocused || searchQuery.isNotEmpty()
    var displayedConsoleTitleId by remember { mutableStateOf<Int?>(null) }
    var displayedConsoleGameCount by remember { mutableStateOf<Int?>(null) }
    if (consoleTitleId != null) {
        displayedConsoleTitleId = consoleTitleId
        displayedConsoleGameCount = consoleGameCount
    }
    val dismissSearchOnOutsideTap =
        searchFocused && searchQuery.isEmpty() && !overlayMode && !showBack

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
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(if (dismissSearchOnOutsideTap) Modifier.fillMaxSize() else Modifier),
        ) {
            val available =
                (maxWidth - GameDetailsScreenPadding * 2 - GameDetailsColumnGap).coerceAtLeast(0.dp)
            val detailsStart =
                GameDetailsScreenPadding + available * GameDetailsCoverWeight + GameDetailsColumnGap
            val backStart = if (overlayMode) 0.dp else lerp(LibrarySidebarInset, detailsStart, compact)
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
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(LibraryTopBarRowHeight)
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                )
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
                            Spacer(modifier = Modifier.width(LibrarySidebarInset))
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val targetWidth =
                                    if (searchExpanded) {
                                        maxWidth
                                    } else {
                                        LibrarySearchCollapsedWidth.coerceAtMost(maxWidth)
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
                            FloatingChromeButton(onClick = { if (!operationInProgress) onScanPressed() }) {
                                RescanIcon(
                                    spinning = operationInProgress,
                                    modifier = Modifier.size(18.dp),
                                    contentDescription =
                                        stringResource(
                                            if (operationInProgress) R.string.library_scanning else R.string.rescan,
                                        ),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingChromeButton(onClick = onCastPressed) {
                                Icon(
                                    imageVector =
                                        if (casting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                                    contentDescription = stringResource(R.string.cast),
                                    tint = if (casting) LibraryNeonGreen else LocalContentColor.current,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingChromeButton(onClick = onSettingsPressed) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingChromeButton(onClick = onProfilePressed) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_profile_robot),
                                    contentDescription = stringResource(R.string.title_profile),
                                    tint = LibraryNeonGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(operationInProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            }
        }
    }
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
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val active = focused || query.isNotEmpty()
    Surface(
        modifier =
            modifier
                .height(LibrarySearchFieldHeight)
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
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        border =
            BorderStroke(
                width = if (active) 2.dp else 1.dp,
                color = if (active) LibraryNeonGreen else Color(0xFF2E2E2E),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LibrarySearchFieldHeight)
                    .padding(start = 10.dp, end = 6.dp),
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

@Composable
fun FloatingChromeButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp).controllerFocusGlow(CircleShape),
        shape = CircleShape,
        color = Color(0xFF161616),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
    ) {
        Row(
            modifier = Modifier.size(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
