package com.omnidroid.app.shared.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.shared.ImmersiveActivity
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.tv.channel.ChannelUpdateWork
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.app.utils.android.displayErrorDialog
import com.omnidroid.common.animationDuration
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.common.coroutines.safeLaunch
import com.omnidroid.common.longAnimationDuration
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.db.RetrogradeDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * This activity is used as an entry point when launching games from external shortcuts. This activity
 * still runs in the main process so it can peek into background job status and wait for them to
 * complete.
 */
@AndroidEntryPoint
@OptIn(FlowPreview::class)
class ExternalGameLauncherActivity : ImmersiveActivity() {
    @Inject
    lateinit var retrogradeDatabase: RetrogradeDatabase

    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var gameLauncher: GameLauncher

    @Inject
    lateinit var castDisplayManager: com.omnidroid.app.shared.cast.CastDisplayManager

    private val loadingState = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_loading)
        if (savedInstanceState == null) {
            val gameId = intent.data?.pathSegments?.let { it[it.size - 1].toInt() }!!

            lifecycleScope.launch {
                loadingState.value = true
                try {
                    loadGame(gameId)
                } catch (e: Throwable) {
                    displayErrorMessage()
                }
                loadingState.value = false
            }

            launchOnState(Lifecycle.State.RESUMED) {
                initializeLoadingFlow(loadingState)
            }
        }
    }

    private suspend fun initializeLoadingFlow(loadingSubject: MutableStateFlow<Boolean>) {
        loadingSubject
            .debounce(longAnimationDuration().toLong())
            .collect {
                findViewById<View>(R.id.progressBar).isVisible = it
            }
    }

    private suspend fun loadGame(gameId: Int) {
        waitPendingOperations()

        val game =
            retrogradeDatabase.gameDao().selectById(gameId)
                ?: throw IllegalArgumentException("Game not found: $gameId")

        delay(animationDuration().toLong())

        val gameLaunchSuccessful =
            gameLauncher.launchGameAsync(
                this,
                game,
                true,
                TVHelper.isTV(applicationContext),
            )

        if (!gameLaunchSuccessful) {
            finish()
        }
    }

    private suspend fun waitPendingOperations() {
        getLoadingLiveData()
            .filter { !it }
            .first()
    }

    private fun displayErrorMessage() {
        displayErrorDialog(R.string.game_loader_error_load_game, R.string.ok) { finish() }
    }

    private fun getLoadingLiveData(): Flow<Boolean> {
        return PendingOperationsMonitor(applicationContext).anyOperationInProgress()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                val isLeanback = data?.extras?.getBoolean(BaseGameActivity.PLAY_GAME_RESULT_LEANBACK) == true

                OmnidroidApplication.scope(this).safeLaunch {
                    if (isLeanback) {
                        ChannelUpdateWork.enqueue(applicationContext)
                    }
                    gameLaunchTaskHandler.handleGameFinish(false, this@ExternalGameLauncherActivity, resultCode, data)
                    castDisplayManager.allowIdle()
                    finish()
                }
            }
        }
    }
}
