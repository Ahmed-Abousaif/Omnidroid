package com.omnidroid.app.mobile.feature.addconsole

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

@Composable
internal fun ConsoleCatalogueCard(
    console: ConsoleItem,
    selected: Boolean,
    busy: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().controllerFocusGlow(ConsolePanelShape),
        shape = ConsolePanelShape,
        color = if (selected) chromeColors.panelSelected else chromeColors.panel,
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) LibraryNeonGreen else chromeColors.panelBorder,
            ),
    ) {
        val info: @Composable () -> Unit = {
            Text(
                stringResource(console.metaSystemID.titleResId),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = chromeColors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    when {
                        console.needsAttention -> R.string.consoles_attention
                        console.registeredOrHasGames -> R.string.consoles_in_library
                        console.coreInstalled -> R.string.consoles_emulator_ready
                        else -> R.string.consoles_available
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (console.needsAttention) MaterialTheme.colorScheme.error else chromeColors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Storage, null, Modifier.size(13.dp), tint = chromeColors.muted)
                Text(
                    stringResource(
                        when {
                            console.databaseInstalled -> R.string.consoles_db_ready
                            console.hasDatabase -> R.string.consoles_db_partial
                            !console.databaseAvailable -> R.string.consoles_db_unavailable
                            else -> R.string.consoles_db_missing
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = chromeColors.muted,
                )
            }
        }
        if (horizontal) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConsoleArtwork(console, busy, Modifier.size(54.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { info() }
            }
        } else {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ConsoleArtwork(console, busy, Modifier.size(54.dp))
                    Spacer(Modifier.weight(1f))
                    if (console.registeredOrHasGames) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            stringResource(R.string.consoles_in_library),
                            Modifier.size(18.dp),
                            tint = LibraryNeonGreen,
                        )
                    }
                }
                info()
                Text(
                    stringResource(R.string.consoles_your_games, console.libraryGameCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = chromeColors.muted,
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
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(console.metaSystemID.color())),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Image(
                painterResource(console.metaSystemID.imageResId),
                null,
                Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
