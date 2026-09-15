package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.mobile.shared.controller.reportControllerGame
import com.omnidroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmnidroidGameListRow(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            modifier
                .wrapContentHeight()
                .controllerFocusGlow(RoundedCornerShape(10.dp))
                .reportControllerGame(game)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                    end = 16.dp,
                ),
        ) {
            OmnidroidSmallGameImage(
                modifier =
                    Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .align(Alignment.CenterVertically),
                game = game,
            )
            OmnidroidGameTexts(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                game = game,
            )
            Box(
                modifier =
                    Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .align(Alignment.CenterVertically),
            ) {
                FavoriteToggle(
                    isToggled = game.isFavorite,
                    onFavoriteToggle = onFavoriteToggle,
                )
            }
        }
    }
}
