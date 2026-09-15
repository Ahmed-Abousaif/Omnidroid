package com.omnidroid.app.mobile.feature.game

import androidx.compose.runtime.Composable
import com.omnidroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.game.BaseGameScreenViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameActivity : BaseGameActivity() {
    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}
