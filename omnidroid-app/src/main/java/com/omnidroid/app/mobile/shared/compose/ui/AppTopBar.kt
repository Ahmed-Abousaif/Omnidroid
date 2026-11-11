package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.library.NotchOrientation
import com.omnidroid.app.mobile.feature.library.SyncProgressNotch
import com.omnidroid.lib.core.SyncProgress

/** Shared app chrome. Screens can supply controls without changing its styling. */
@Composable
fun AppTopBar(
    gamepadConnected: Boolean,
    portrait: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    syncProgress: SyncProgress? = null,
    onBackPressed: (() -> Unit)? = null,
    controlsVisible: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val spec = LocalAdaptiveLayout.current
    val height by animateDpAsState(
        targetValue = if (controlsVisible && bottomContent != null) spec.topBarPortraitHeight else spec.topBarRowHeight,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "topBarHeight",
    )
    val syncAlignment = if (portrait) Alignment.Top else Alignment.Bottom
    val syncOrigin = TransformOrigin(0.5f, if (portrait) 0f else 1f)

    CompositionLocalProvider(LocalContentColor provides chromeContentColor) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clipToBounds()
                        .background(Brush.verticalGradient(listOf(homeChromeColor, Color.Transparent)))
                        .then(if (portrait) Modifier else Modifier.hugDisplayCutout(WindowInsetsSides.Horizontal)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(spec.topBarRowHeight).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = controlsVisible && leadingContent != null,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) { leadingContent?.invoke() }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = onBackPressed != null,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) {
                            FloatingChromeButton(onClick = { onBackPressed?.invoke() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    modifier = Modifier.size(18.dp * spec.uiScale),
                                )
                            }
                        }
                    }
                    SystemStatusIndicators(gamepadConnected = gamepadConnected)
                    AnimatedVisibility(
                        visible = controlsVisible && trailingContent != null,
                        enter = fadeIn(tween(200)) + expandHorizontally(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.End),
                        exit = fadeOut(tween(200)) + shrinkHorizontally(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.End),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(10.dp))
                            trailingContent?.invoke()
                        }
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible && bottomContent != null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(spec.topBarRowHeight).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { bottomContent?.invoke() }
                    }
                }
            }

            AnimatedVisibility(
                visible = syncProgress != null,
                enter =
                    fadeIn(tween(220)) +
                        expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = syncAlignment) +
                        scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.85f, transformOrigin = syncOrigin),
                exit =
                    fadeOut(tween(200)) +
                        shrinkVertically(tween(280, easing = FastOutSlowInEasing), shrinkTowards = syncAlignment) +
                        scaleOut(tween(280, easing = FastOutSlowInEasing), targetScale = 0.85f, transformOrigin = syncOrigin),
                modifier =
                    Modifier
                        .align(if (portrait) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(top = if (portrait) height else 0.dp, bottom = if (portrait) 0.dp else bottomPadding)
                        .fillMaxWidth(0.5f),
            ) {
                SyncProgressNotch(
                    syncProgress = syncProgress,
                    orientation = if (portrait) NotchOrientation.TOP else NotchOrientation.BOTTOM,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
