package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.app.utils.games.GameUtils
import com.omnidroid.lib.library.db.entity.Game

@Composable
fun OmnidroidGameTexts(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val context = LocalContext.current
    val subtitle =
        remember(game.id) {
            GameUtils.getGameSubtitle(context, game)
        }

    OmnidroidTexts(modifier, game.displayName, subtitle)
}

@Composable
fun OmnidroidTexts(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = modifier.padding(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
