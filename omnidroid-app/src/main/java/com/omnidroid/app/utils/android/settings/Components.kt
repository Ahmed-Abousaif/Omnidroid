package com.omnidroid.app.utils.android.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt

/** Optional host surface for rows; null preserves standard settings/card colors. */
val LocalSettingsRowContainerColor = staticCompositionLocalOf<Color?> { null }

@Composable
fun OmnidroidSettingsPage(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = verticalArrangement,
    ) {
        content()
    }
}

@Composable
fun OmnidroidSettingsSwitch(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SettingValueState<Boolean>? = null,
    value: Boolean = state?.value ?: false,
    icon: @Composable (() -> Unit)? = null,
    titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    subtitleRes: Int? = null,
    subtitle: (@Composable () -> Unit)? = subtitleRes?.let { { Text(stringResource(it)) } },
    onCheckedChange: (Boolean) -> Unit = {},
) {
    SettingsSwitch(
        modifier = modifier.controllerFocusGlow(RoundedCornerShape(8.dp)),
        enabled = enabled,
        state = value,
        icon = icon,
        title = title,
        subtitle = subtitle,
        onCheckedChange = {
            state?.value = it
            onCheckedChange(it)
        },
        colors = omnidroidSettingsColor(enabled),
    )
}

@Composable
fun OmnidroidSettingsMenuLink(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    subtitleRes: Int? = null,
    subtitle: (@Composable () -> Unit)? = subtitleRes?.let { { Text(stringResource(it)) } },
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SettingsMenuLink(
        modifier = modifier.controllerFocusGlow(RoundedCornerShape(8.dp)),
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = onClick,
        colors = omnidroidSettingsColor(enabled),
    )
}

@Composable
fun OmnidroidSettingsGroup(
    modifier: Modifier = Modifier,
    titleRes: Int? = null,
    title: @Composable (() -> Unit)? = titleRes?.let { { Text(stringResource(it)) } },
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun SettingsHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.75f),
            thickness = 1.dp,
            color = chromeColors.divider,
        )
    }
}

@Composable
fun OmnidroidCardSettingsGroup(
    modifier: Modifier = Modifier,
    titleRes: Int? = null,
    title: @Composable (() -> Unit)? = titleRes?.let { { Text(stringResource(it)) } },
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            OutlinedCard {
                if (title != null) {
                    SettingsGroupTitleSmall(title)
                }
                content()
            }
        }
    }
}

@Composable
fun OmnidroidSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    subtitleRes: Int? = null,
    subtitle: (@Composable () -> Unit)? = subtitleRes?.let { { Text(stringResource(it)) } },
    valueText: (@Composable (Int) -> Unit)? = null,
) {
    val defaultColors = ListItemDefaults.colors(
        containerColor = LocalSettingsRowContainerColor.current ?: Color.Transparent,
    )
    val disabledColors =
        ListItemDefaults.colors(
            containerColor = defaultColors.containerColor,
            headlineColor = defaultColors.disabledHeadlineColor,
            leadingIconColor = defaultColors.disabledLeadingIconColor,
            trailingIconColor = defaultColors.disabledTrailingIconColor,
            supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
        )
    val colors = if (enabled) defaultColors else disabledColors
    val min = valueRange.start.roundToInt()
    val max = valueRange.endInclusive.roundToInt()
    val stepSize =
        if (steps > 0) {
            ((max - min).toFloat() / (steps + 1)).coerceAtLeast(1f).roundToInt().coerceAtLeast(1)
        } else {
            1
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .focusable(enabled)
                .controllerFocusGlow(RoundedCornerShape(8.dp))
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            state.value = (state.value - stepSize).coerceIn(min, max)
                            true
                        }
                        Key.DirectionRight -> {
                            state.value = (state.value + stepSize).coerceIn(min, max)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        ListItem(
            headlineContent = title,
            supportingContent = subtitle,
            trailingContent = valueText?.let { { it(state.value) } },
            colors = colors,
        )
        Slider(
            value = state.value.toFloat(),
            onValueChange = { state.value = it.roundToInt() },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SettingsGroupTitleSmall(title: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val titleStyle = MaterialTheme.typography.labelLarge.copy(color = primary)
        ProvideTextStyle(value = titleStyle) { title() }
    }
}

@Composable
private fun omnidroidSettingsColor(enabled: Boolean): ListItemColors {
    val defaultColors = LocalSettingsRowContainerColor.current?.let {
        ListItemDefaults.colors(containerColor = it)
    } ?: ListItemDefaults.colors()

    if (enabled) {
        return defaultColors
    }

    return ListItemDefaults.colors(
        containerColor = defaultColors.containerColor,
        headlineColor = defaultColors.disabledHeadlineColor,
        leadingIconColor = defaultColors.disabledLeadingIconColor,
        trailingIconColor = defaultColors.disabledTrailingIconColor,
        supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
    )
}
