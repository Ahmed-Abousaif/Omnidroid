package com.omnidroid.touchinput.radial.layouts

import android.content.res.Configuration
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.layouts.radial.LayoutRadial
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope)
@Composable
fun BaseLayoutLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val interpolatedDialSize =
        remember(settings.scale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                settings.scale,
            )
        }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxMarginY =
        if (isLandscape) {
            TouchControllerSettingsManager.MAX_MARGINS
        } else {
            maxOf(TouchControllerSettingsManager.MAX_MARGIN_Y_PORTRAIT, configuration.screenHeightDp * 0.55f)
        }

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    left = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                    bottom = maxMarginY.dp * settings.marginY,
                )
                .padding(LocalOmnidroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize = 160.dp * interpolatedDialSize,
        secondaryDialsBaseRotationInDegrees = settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}

context(PadKitScope)
@Composable
fun BaseLayoutRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxMarginY =
        if (isLandscape) {
            TouchControllerSettingsManager.MAX_MARGINS
        } else {
            maxOf(TouchControllerSettingsManager.MAX_MARGIN_Y_PORTRAIT, configuration.screenHeightDp * 0.55f)
        }

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    right = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                    bottom = maxMarginY.dp * settings.marginY,
                )
                .padding(LocalOmnidroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize =
            160.dp *
                lerp(
                    TouchControllerSettingsManager.MIN_SCALE,
                    TouchControllerSettingsManager.MAX_SCALE,
                    settings.scale,
                ),
        secondaryDialsBaseRotationInDegrees = -settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}
