package com.omnidroid.app.mobile.shared.compose.sidemenu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
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
    val scrimAlpha by animateFloatAsState(
        targetValue = if (transition.targetState) 0.58f else 0f,
        animationSpec = tween(if (transition.targetState) 280 else 220),
        label = "sideMenuScrim",
    )
    // Share the console selectors' neutral fill with every row in the panel.
    val panelColor = chromeColors.panel
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.closeThen() },
        )
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
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                            .padding(bottom = maxOf(bottomReserved, WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())),
                    ) {
                        SideMenuHeader(title, canGoBack) { if (canGoBack) onBack() else state.closeThen() }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(chromeColors.divider))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            CompositionLocalProvider(LocalSettingsRowContainerColor provides panelColor) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

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
