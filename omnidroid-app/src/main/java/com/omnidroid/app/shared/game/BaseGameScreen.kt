package com.omnidroid.app.shared.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.omnidroid.app.shared.game.viewmodel.GameViewModelRetroGameView

@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    if (isGameReady) {
        gameScreen(viewModel)
    } else {
        GameLoadingScreen(
            game = viewModel.game,
            system = viewModel.system,
            loadingMessage = (gameState as? GameViewModelRetroGameView.GameState.Loading)?.message,
        )
    }
}
