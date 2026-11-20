package com.omnidroid.app.mobile.shared.compose.sidemenu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.utils.android.settings.LocalSettingsRowContainerColor

/** Shared visual shell. Android/game lifecycle and action execution belong to the host. */
@Composable
fun SideMenuScaffold(
    state: SideMenuState,
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomReserved: Dp = 0.dp,
    onClosed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val closed by rememberUpdatedState(onClosed)
    val transition = state.transition
    LaunchedEffect(transition.currentState, transition.targetState, transition.isIdle) {
        if (transition.isIdle && !transition.currentState && !transition.targetState) {
            state.completeClose { closed() }
        }
    }
    BackHandler { if (canGoBack) onBack() else state.closeThen() }
    // Once open, controller focus belongs in the menu, not on the button that opened it. A menu opened
    // by touch can't hold focus yet, so the first controller press would land on the screen behind it:
    // pull focus in as soon as the controller is used.
    val controller = LocalControllerNavigation.current
    val inputModeManager = LocalInputModeManager.current
    var panelHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(transition.currentState) {
        if (!transition.currentState) return@LaunchedEffect
        snapshotFlow { inputModeManager.inputMode }.collect { mode ->
            if (mode != InputMode.Touch && !panelHasFocus) {
                runCatching { controller?.contentFocusRequester?.requestFocus() }
            }
        }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (transition.targetState) 0.58f else 0f,
        animationSpec = tween(if (transition.targetState) 280 else 220),
        label = "sideMenuScrim",
    )
    // Share the console selectors' neutral fill with every row in the panel.
    val panelColor = chromeColors.panel
    // The panel's content moves between the sheet and the side panel when the screen rotates;
    // movable content keeps its state (scroll position, focused row) across the move.
    val currentContent by rememberUpdatedState(content)
    val currentTitle by rememberUpdatedState(title)
    val currentCanGoBack by rememberUpdatedState(canGoBack)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentBottomReserved by rememberUpdatedState(bottomReserved)
    val panelContent =
        remember(state) {
            movableContentOf { sheet: Boolean ->
                val insetSides =
                    if (sheet) {
                        WindowInsetsSides.Horizontal
                    } else {
                        WindowInsetsSides.Top + WindowInsetsSides.End
                    }
                val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                Column(
                    Modifier
                        .fillMaxSize()
                        .menuFocusTrap { panelHasFocus = it }
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(insetSides))
                        .padding(bottom = maxOf(currentBottomReserved, bottomInset)),
                ) {
                    SideMenuHeader(currentTitle, currentCanGoBack) {
                        if (currentCanGoBack) currentOnBack() else state.closeThen()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(chromeColors.divider),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        CompositionLocalProvider(
                            LocalSettingsRowContainerColor provides chromeColors.panel,
                            LocalSideMenuIsSheet provides sheet,
                        ) {
                            currentContent()
                        }
                    }
                }
            }
        }
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                // Tap outside to close, but never a controller focus target: as the first focusable
                // node it could take focus from the menu rows, leaving the D-pad and A doing nothing.
                .focusProperties { canFocus = false }
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.closeThen() },
        )
        val sheet = maxHeight > maxWidth
        if (sheet) {
            // Portrait: a bottom sheet, leaving the game visible above it.
            AnimatedVisibility(
                visibleState = transition,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(sideMenuSheetHeight(maxHeight.value).dp),
                    color = panelColor,
                    contentColor = chromeContentColor,
                    // Same treatment as the side panel: straight edges and an accent line along the
                    // edge that faces the game.
                    shape = RectangleShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(chromeColors.gameMenuAccent))
                        Box(Modifier.weight(1f).fillMaxWidth()) { panelContent(true) }
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visibleState = transition,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it },
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(sideMenuWidth(maxWidth.value).dp),
                    color = panelColor,
                    contentColor = chromeContentColor,
                    shape = RectangleShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(chromeColors.gameMenuAccent))
                        Box(Modifier.weight(1f).fillMaxHeight()) { panelContent(false) }
                    }
                }
            }
        }
    }
}

/** True when the menu shows as a portrait bottom sheet rather than a side panel. */
val LocalSideMenuIsSheet = staticCompositionLocalOf { false }

@Composable
private fun SideMenuHeader(title: String, canGoBack: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(40.dp).controllerFocusGlow(CircleShape),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = chromeContentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (canGoBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                    stringResource(if (canGoBack) R.string.back else R.string.close),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = chromeContentColor,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
        )
    }
}

/**
 * Keeps D-pad focus inside the open menu: moving past an edge stays put, and only Back or Close
 * leaves. Focus requested by code (closing the menu, opening a dialog) still moves freely.
 */
private fun Modifier.menuFocusTrap(onHasFocus: (Boolean) -> Unit): Modifier =
    onFocusChanged { onHasFocus(it.hasFocus) }
        .focusProperties {
            onExit = {
                val direction = requestedFocusDirection
                if (direction == FocusDirection.Left ||
                    direction == FocusDirection.Right ||
                    direction == FocusDirection.Up ||
                    direction == FocusDirection.Down ||
                    direction == FocusDirection.Next ||
                    direction == FocusDirection.Previous
                ) {
                    cancelFocusChange()
                }
            }
        }.focusGroup()
