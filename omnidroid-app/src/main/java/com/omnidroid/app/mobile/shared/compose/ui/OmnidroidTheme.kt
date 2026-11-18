package com.omnidroid.app.mobile.shared.compose.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.stringPreferenceState

// Cool gray ↔ white fills (#E8E8E8…#FFFFFF). Override surfaceContainer* so M3 dialogs
// don't fall back to the purple baseline tokens.
private val LightSurfaceDim = Color(0xFFE8E8E8)
private val LightBackground = Color(0xFFF2F2F2)
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
        background = LightBackground,
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

val LibraryNeonGreen = Color(0xFF00B33C)

/** Text and icons on a [LibraryNeonGreen] fill: near-black reads at ~7:1, white only ~2.8:1. */
val OnLibraryNeonGreen = Color(0xFF0A0A0A)

/**
 * Highlight colours for the Accent color setting (Display): focus and selection highlights,
 * selected borders and accent fills. Each has a darker light-mode variant to keep >= 3:1 against
 * light chrome. Green is the default and matches the brand colours used before the setting.
 */
enum class AccentPreset(val key: String, val dark: Color, val light: Color) {
    GREEN("green", LibraryNeonGreen, Color(0xFF00702A)),
    BLUE("blue", Color(0xFF3D8BFF), Color(0xFF0B5ED7)),
    PURPLE("purple", Color(0xFFA77BFF), Color(0xFF6A3FC1)),
    PINK("pink", Color(0xFFFF5CA8), Color(0xFFC2185B)),
    RED("red", Color(0xFFFF4D45), Color(0xFFC62828)),
    ORANGE("orange", Color(0xFFFF8A1F), Color(0xFFB85400)),
    YELLOW("yellow", Color(0xFFFFD60A), Color(0xFF8A6D00)),
    TEAL("teal", Color(0xFF1FD1C1), Color(0xFF00796B)),
    WHITE("white", Color(0xFFF2F2F2), Color(0xFF1A1A1A)),
    ;

    fun color(dark: Boolean): Color = if (dark) this.dark else light

    /** Text and icons on a fill of this accent: black or white, whichever has more contrast. */
    fun onAccent(dark: Boolean): Color =
        if (this == GREEN && dark) OnLibraryNeonGreen else Color(AccentContrast.onColor(color(dark).toArgb()))

    companion object {
        fun fromKey(key: String?): AccentPreset = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

val LocalAccentColor = staticCompositionLocalOf { LibraryNeonGreen }

val LocalOnAccentColor = staticCompositionLocalOf { OnLibraryNeonGreen }

/** The user's accent colour (Display > Accent color) for icons, text, highlights and fills. */
val libraryAccentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentColor.current

/** Text and icons on a [libraryAccentColor] fill: black or white, whichever reads better. */
val onLibraryAccentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalOnAccentColor.current
val BatteryLowRed = Color(0xFFFF3B30)

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
    val gameMenuAccent: Color,
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
        gameMenuAccent = Color(0xFF388D56),
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
        gameMenuAccent = Color(0xFF6ED18F),
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
 * Plain off-white fill in light mode; solid near-black in dark mode.
 * Use on library/settings screen roots — not in-game or gamepad surfaces.
 */
fun Modifier.homeChromeBackground(): Modifier =
    composed {
        background(MaterialTheme.colorScheme.background)
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
    val accentPreference = stringPreferenceState(R.string.pref_key_accent_color, AccentPreset.GREEN.key)
    val accentPreset = AccentPreset.fromKey(accentPreference.value)
    val accent = accentPreset.color(darkTheme)
    val baseChrome = if (darkTheme) DarkChromeColors else LightChromeColors
    // The game menu's accent follows the setting too; green keeps its own tone.
    val chrome = if (accentPreset == AccentPreset.GREEN) baseChrome else baseChrome.copy(gameMenuAccent = accent)
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
        LocalAccentColor provides accent,
        LocalOnAccentColor provides accentPreset.onAccent(darkTheme),
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
