package com.omnidroid.app.tv.game

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import com.omnidroid.R
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.game.BaseGameScreenViewModel
import com.omnidroid.app.tv.gamemenu.TVGameMenuActivity
import dagger.hilt.android.AndroidEntryPoint
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.common.coroutines.safeCollect
import com.omnidroid.common.displayToast
import kotlinx.coroutines.flow.filter

@AndroidEntryPoint
class TVGameActivity : BaseGameActivity() {
    override fun getDialogClass() = TVGameMenuActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFlows()
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        TVGameScreen(viewModel)
    }

    private fun initializeFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeShortcutToastFlow()
        }
    }

    private suspend fun initializeShortcutToastFlow() {
        inputDeviceManager
            .getEnabledInputsObservable()
            .filter { it.isEmpty() }
            .safeCollect {
                displayToast(R.string.tv_game_message_missing_gamepad)
            }
    }
}
