package com.omnidroid.app.mobile.feature.settings.uiscale

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.FloatingChromeButton
import com.omnidroid.app.mobile.feature.library.LibraryFilter
import com.omnidroid.app.mobile.feature.library.LibraryLayout
import com.omnidroid.app.mobile.feature.library.LibraryLayoutToggle
import com.omnidroid.app.mobile.feature.library.LibrarySidebar
import com.omnidroid.app.mobile.feature.library.LibraryTopBar
import com.omnidroid.app.mobile.feature.library.PortraitConsoleBar
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.calculateAdaptiveLayoutSpec
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.hugDisplayCutout
import com.omnidroid.app.utils.settings.safeGetInt
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlin.math.roundToInt

private val PreviewDefaultSystems =
    listOf(
        MetaSystemID.GBA,
        MetaSystemID.SNES,
        MetaSystemID.GENESIS,
        MetaSystemID.PSX,
        MetaSystemID.N64,
    )

@Composable
fun UiScalePreviewScreen(
    availableConsoles: List<MetaSystemID> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { SharedPreferencesHelper.getSharedPreferences(context) }
    val prefKey = stringResource(R.string.pref_key_button_ui_scale_step)
    val initialStep = remember { preferences.safeGetInt(prefKey, 0).coerceIn(0, UiScaleHelper.TOTAL_INCREMENTS) }

    var currentStep by rememberSaveable { mutableIntStateOf(initialStep) }
    var previewFilter by remember { mutableStateOf<LibraryFilter>(LibraryFilter.All) }
    var previewLayout by remember { mutableStateOf(LibraryLayout.CONSOLE) }

    val displaySystems =
        remember(availableConsoles) {
            availableConsoles.ifEmpty { PreviewDefaultSystems }
        }

    BackHandler { onDismiss() }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .homeChromeBackground(),
    ) {
        val screenWidth = this@BoxWithConstraints.maxWidth
        val screenHeight = this@BoxWithConstraints.maxHeight
        val portrait = screenHeight > screenWidth
        val currentScale = UiScaleHelper.scaleForStep(currentStep)
        val adaptiveSpec =
            remember(screenWidth, screenHeight, currentScale) {
                calculateAdaptiveLayoutSpec(screenWidth, screenHeight, currentScale)
            }
        val libraryTopPadding =
            if (portrait) adaptiveSpec.topBarPortraitHeight else adaptiveSpec.topBarRowHeight

        CompositionLocalProvider(
            LocalAdaptiveLayout provides adaptiveSpec,
        ) {
            // 1. Mock library background with controls at their natural positions
            Box(modifier = Modifier.fillMaxSize()) {
                if (portrait) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = libraryTopPadding),
                    ) {
                        // Empty games area in the middle with View toggle at the bottom
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                        ) {
                            LibraryLayoutToggle(
                                layout = previewLayout,
                                onLayoutChange = { previewLayout = it },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 8.dp, bottom = 6.dp),
                            )
                        }
                        // Bottom console bar
                        PortraitConsoleBar(
                            filter = previewFilter,
                            systems = displaySystems,
                            selectedGameCount = null,
                            startupFilter = LibraryFilter.All,
                            onFilterSelected = { previewFilter = it },
                            onConsoleLongClick = {},
                            onAddConsole = {},
                        )
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = libraryTopPadding)
                                .hugDisplayCutout(WindowInsetsSides.Horizontal),
                    ) {
                        LibrarySidebar(
                            filter = previewFilter,
                            systems = displaySystems,
                            startupFilter = LibraryFilter.All,
                            onFilterSelected = { previewFilter = it },
                            onConsoleLongClick = {},
                            onAddConsole = {},
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        ) {
                            LibraryLayoutToggle(
                                layout = previewLayout,
                                onLayoutChange = { previewLayout = it },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 8.dp, bottom = 6.dp),
                            )
                        }
                    }
                }

                // Top bar
                LibraryTopBar(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    gamepadConnected = false,
                    onRandomPressed = {},
                    onDisplaysPressed = {},
                    onSettingsPressed = {},
                    onProfilePressed = {},
                    portrait = portrait,
                )
            }

            // 2. Very subtle dark scrim over the entire mock library screen
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f)),
            )

            // 3. Central scale slider (sitting directly in the mock library screen)
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .widthIn(min = 280.dp, max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.ui_scale_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chromeColors.content,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${UiScaleHelper.percentageForStep(currentStep)}%",
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                    color = LibraryNeonGreen,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingChromeButton(
                        onClick = { if (currentStep > 0) currentStep-- },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.ui_scale_default),
                            tint = if (currentStep > 0) chromeColors.content else chromeColors.muted.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Slider(
                        value = currentStep.toFloat(),
                        onValueChange = { currentStep = it.roundToInt() },
                        valueRange = 0f..UiScaleHelper.TOTAL_INCREMENTS.toFloat(),
                        steps = UiScaleHelper.TOTAL_INCREMENTS - 1,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = LibraryNeonGreen,
                                activeTrackColor = LibraryNeonGreen,
                                inactiveTrackColor = chromeColors.muted.copy(alpha = 0.3f),
                            ),
                    )
                    FloatingChromeButton(
                        onClick = { if (currentStep < UiScaleHelper.TOTAL_INCREMENTS) currentStep++ },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.ui_scale_maximum),
                            tint =
                                if (currentStep < UiScaleHelper.TOTAL_INCREMENTS) {
                                    chromeColors.content
                                } else {
                                    chromeColors.muted.copy(alpha = 0.35f)
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // 4. Save and Cancel buttons at the bottom (rounded shape, not circle rounds)
            val bottomPadding =
                if (portrait) {
                    adaptiveSpec.consoleBarHeight + 20.dp
                } else {
                    24.dp
                }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    color = chromeColors.panel,
                    border = BorderStroke(1.dp, chromeColors.panelBorder),
                    modifier =
                        Modifier
                            .height(44.dp)
                            .widthIn(min = 120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = chromeColors.content,
                        )
                    }
                }

                Surface(
                    onClick = {
                        preferences.edit { putInt(prefKey, currentStep) }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = LibraryNeonGreen,
                    modifier =
                        Modifier
                            .height(44.dp)
                            .widthIn(min = 120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                        )
                    }
                }
            }
        }
    }
}
