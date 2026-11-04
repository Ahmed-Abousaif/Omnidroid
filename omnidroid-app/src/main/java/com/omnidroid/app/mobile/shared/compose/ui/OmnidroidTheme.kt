package com.omnidroid.app.mobile.shared.compose.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.stringPreferenceState

// Cool gray ↔ white fills (#E8E8E8…#FFFFFF). Override surfaceContainer* so M3 dialogs
// don't fall back to the purple baseline tokens.
private val LightSurfaceDim = Color(0xFFE8E8E8)
private val LightSurface = Color(0xFFF5F5F5)
private val LightSurfaceBright = Color(0xFFFFFFFF)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF7F7F7)
private val LightSurfaceContainer = Color(0xFFF5F5F5)
private val LightSurfaceContainerHigh = Color(0xFFFFFFFF)
private val LightSurfaceContainerHighest = Color(0xFFF2F2F2)

private val LightColorScheme =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        errorContainer = md_theme_light_errorContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inverseSurface = md_theme_light_inverseSurface,
        inversePrimary = md_theme_light_inversePrimary,
        // Neutral tint so tonal elevation stays gray, not brand/purple.
        surfaceTint = LightSurface,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
        surfaceBright = LightSurfaceBright,
        surfaceDim = LightSurfaceDim,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainerLowest = LightSurfaceContainerLowest,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

/** Fixed dark chrome used by in-game UI. Prefer [homeChromeColor] / [Modifier.homeChromeBackground] for library chrome. */
val HomeChromeBackground = Color(0xFF0B0B0B)
val LibraryNeonGreen = Color(0xFF00B33C)
val BatteryLowRed = Color(0xFFFF3B30)

private val LightStripe = Color(0xFFF2F2F2)

@Immutable
data class ChromeColors(
    val panel: Color,
    val panelSelected: Color,
    val panelBorder: Color,
    val content: Color,
    val muted: Color,
    val faint: Color,
    val pressed: Color,
    val iconButton: Color,
    val iconButtonBorder: Color,
    val listRow: Color,
    val listRowBorder: Color,
    val menu: Color,
    val progressBar: Color,
    val progressBarBorder: Color,
    val divider: Color,
)

private val DarkChromeColors =
    ChromeColors(
        panel = Color(0xFF1C1C1E),
        panelSelected = Color(0xFF242424),
        panelBorder = Color(0xFF2E2E2E),
        content = Color.White,
        muted = Color.White.copy(alpha = 0.72f),
        faint = Color.White.copy(alpha = 0.42f),
        pressed = Color.White.copy(alpha = 0.14f),
        iconButton = Color(0xFF2A2A2A),
        iconButtonBorder = Color(0xFF3A3A3A),
        listRow = Color(0xFF161616),
        listRowBorder = Color(0xFF2E2E2E),
        menu = Color(0xFF1C1C1E),
        progressBar = Color(0xF2121212),
        progressBarBorder = Color(0xFF262626),
        divider = Color.White.copy(alpha = 0.12f),
    )

private val LightChromeColors =
    ChromeColors(
        panel = Color(0xFFF5F5F5),
        panelSelected = Color(0xFFEEEEEE),
        panelBorder = Color(0xFFC8C8C8),
        content = Color(0xFF1A1A1A),
        muted = Color(0xFF1A1A1A).copy(alpha = 0.68f),
        faint = Color(0xFF1A1A1A).copy(alpha = 0.42f),
        pressed = Color(0xFF1A1A1A).copy(alpha = 0.10f),
        iconButton = Color(0xFFF2F2F2),
        iconButtonBorder = Color(0xFFC8C8C8),
        listRow = Color(0xFFF5F5F5),
        listRowBorder = Color(0xFFC8C8C8),
        menu = Color(0xFFF5F5F5),
        progressBar = Color(0xF2F2F2F2),
        progressBarBorder = Color(0xFFC8C8C8),
        divider = Color(0xFF1A1A1A).copy(alpha = 0.12f),
    )

val LocalAppDarkTheme = staticCompositionLocalOf { true }
val LocalChromeColors = staticCompositionLocalOf { DarkChromeColors }

val chromeColors: ChromeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChromeColors.current

/** Theme-aware library/settings chrome fill. Prefer [Modifier.homeChromeBackground] for screen roots. */
val homeChromeColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val homeChromeCardColor: Color
    @Composable
    @ReadOnlyComposable
    get() = chromeColors.panel

val chromeContentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = chromeColors.content

val chromeMutedContentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = chromeColors.muted

/**
 * Soft gray fill with subtle diagonal stripes in light mode; solid near-black in dark mode.
 * Use on library/settings screen roots — not in-game or gamepad surfaces.
 */
fun Modifier.homeChromeBackground(): Modifier =
    composed {
        val darkTheme = LocalAppDarkTheme.current
        val base = MaterialTheme.colorScheme.background
        val stripe = LightStripe.copy(alpha = 0.32f)
        drawBehind {
            drawRect(base)
            if (!darkTheme) {
                val spacing = 36.dp.toPx()
                val stroke = 2.75.dp.toPx()
                val extent = size.width + size.height
                var x = -size.height
                while (x < extent) {
                    drawLine(
                        color = stripe,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = stroke,
                    )
                    x += spacing
                }
            }
        }
    }

/**
 * @param followUserPreference when false, always uses dark theme (in-game view, gamepad binding).
 * @param syncSystemBars when false, skips edge-to-edge system bar contrast updates (game menu overlay).
 */
@Composable
fun AppTheme(
    followUserPreference: Boolean = true,
    syncSystemBars: Boolean = followUserPreference,
    content: @Composable () -> Unit,
) {
    // Always read the preference so composition stays consistent; in-game hosts ignore it.
    val themePreference = stringPreferenceState(R.string.pref_key_app_theme, AppThemeMode.DARK)
    val darkTheme = !followUserPreference || themePreference.value != AppThemeMode.LIGHT
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val chrome = if (darkTheme) DarkChromeColors else LightChromeColors
    val context = LocalContext.current

    SideEffect {
        // Only MainActivity chrome should flip system bar icon contrast with the theme.
        // In-game / gamepad / game-menu hosts keep their own window setup.
        if (!syncSystemBars) return@SideEffect
        val activity = context.findComponentActivity() ?: return@SideEffect
        val barStyle =
            if (darkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            }
        activity.enableEdgeToEdge(barStyle, barStyle)
    }

    CompositionLocalProvider(
        LocalAppDarkTheme provides darkTheme,
        LocalChromeColors provides chrome,
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            content()
        }
    }
}

object AppThemeMode {
    const val DARK = "dark"
    const val LIGHT = "light"
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
