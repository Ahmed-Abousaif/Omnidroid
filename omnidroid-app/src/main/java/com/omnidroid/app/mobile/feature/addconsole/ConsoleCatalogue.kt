package com.omnidroid.app.mobile.feature.addconsole

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAppDarkTheme
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

private val UninstalledConsoleGray = Color(0xFF4A4A4A)

@Composable
internal fun ConsoleCatalogueRow(
    console: ConsoleItem,
    selected: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val installed = console.registeredOrHasGames
    val uninstalledContent = if (LocalAppDarkTheme.current) chromeColors.muted else UninstalledConsoleGray
    val accent = if (installed) Color(console.metaSystemID.color()) else uninstalledContent
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().controllerFocusGlow(ConsoleControlShape),
        shape = ConsoleControlShape,
        color = if (selected) chromeColors.panelSelected else chromeColors.panel,
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) accent else chromeColors.panelBorder,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConsoleArtwork(console, busy, Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(console.metaSystemID.titleResId),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (installed) chromeColors.content else uninstalledContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        when {
                            console.needsAttention -> R.string.consoles_attention
                            installed -> R.string.consoles_library_count
                            console.coreInstalled -> R.string.consoles_emulator_ready
                            else -> R.string.consoles_available
                        },
                        console.libraryGameCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        when {
                            installed && console.needsAttention -> MaterialTheme.colorScheme.error
                            installed -> chromeColors.muted
                            else -> uninstalledContent
                        },
                )
            }
            if (installed) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    stringResource(R.string.consoles_in_library),
                    Modifier.size(18.dp),
                    tint = LibraryNeonGreen,
                )
            }
        }
    }
}

@Composable
internal fun ConsoleArtwork(
    console: ConsoleItem,
    busy: Boolean,
    modifier: Modifier,
) {
    val darkTheme = LocalAppDarkTheme.current
    val installed = console.registeredOrHasGames
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    installed -> Color(console.metaSystemID.color())
                    darkTheme -> chromeColors.panelSelected
                    else -> UninstalledConsoleGray
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                color = if (installed || !darkTheme) Color.White else chromeColors.muted,
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                painterResource(console.metaSystemID.imageResId),
                null,
                Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
                colorFilter =
                    if (installed) {
                        null
                    } else {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    },
            )
        }
    }
}
