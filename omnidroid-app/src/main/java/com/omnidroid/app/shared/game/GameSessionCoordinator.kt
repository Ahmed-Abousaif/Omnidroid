package com.omnidroid.app.shared.game

import android.app.Activity
import android.content.Intent
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.game.GameService
import com.omnidroid.app.mobile.feature.gamedetails.PlayTimeStore
import com.omnidroid.app.mobile.feature.profile.UserProfileStore
import com.omnidroid.app.mobile.feature.profile.XPCalculator
import com.omnidroid.app.shared.savesync.SaveSyncWork
import com.omnidroid.app.shared.storage.cache.CacheCleanerWork
import com.omnidroid.app.tv.channel.ChannelUpdateWork
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.animationDuration
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Coordinates game session termination, result reporting, stats/XP recording,
 * save sync, and `:game` process termination across all backend host activities.
 */
class GameSessionCoordinator(
    private val activity: Activity,
    private val retrogradeDatabase: RetrogradeDatabase,
    private val gameProvider: () -> Game?,
    private val startGameTime: Long,
    private val isExternalLaunch: Boolean,
    private val onFinishTriggered: () -> Unit = {},
) {
    constructor(
        activity: Activity,
        retrogradeDatabase: RetrogradeDatabase,
        game: Game,
        startGameTime: Long,
        isExternalLaunch: Boolean,
        onFinishTriggered: () -> Unit = {},
    ) : this(
        activity = activity,
        retrogradeDatabase = retrogradeDatabase,
        gameProvider = { game },
        startGameTime = startGameTime,
        isExternalLaunch = isExternalLaunch,
        onFinishTriggered = onFinishTriggered,
    )

    fun performSuccessfulActivityFinish(beforeExit: (suspend () -> Unit)? = null) {
        val resultIntent = Intent().apply {
            putExtra(BaseGameActivity.PLAY_GAME_RESULT_SESSION_DURATION, System.currentTimeMillis() - startGameTime)
            putExtra(BaseGameActivity.PLAY_GAME_RESULT_GAME, activity.intent.getSerializableExtra(BaseGameActivity.EXTRA_GAME))
            putExtra(BaseGameActivity.PLAY_GAME_RESULT_LEANBACK, activity.intent.getBooleanExtra(BaseGameActivity.EXTRA_LEANBACK, false))
        }

        activity.setResult(Activity.RESULT_OK, resultIntent)
        finishAndExitProcess(beforeExit)
    }

    fun performUnexpectedErrorFinish(exception: Throwable, beforeExit: (suspend () -> Unit)? = null) {
        Timber.e(exception, "Handling unexpected exception in game session")
        val resultIntent = Intent().apply {
            putExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR, exception.message)
        }

        activity.setResult(BaseGameActivity.RESULT_UNEXPECTED_ERROR, resultIntent)
        finishAndExitProcess(beforeExit)
    }

    fun performErrorFinish(message: String, beforeExit: (suspend () -> Unit)? = null) {
        val resultIntent = Intent().apply {
            putExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR, message)
        }

        activity.setResult(BaseGameActivity.RESULT_ERROR, resultIntent)
        finishAndExitProcess(beforeExit)
    }

    fun finishAndExitProcess(beforeExit: (suspend () -> Unit)? = null) {
        onFinishTriggered()
        OmnidroidApplication.scope(activity).launch(Dispatchers.IO) {
            try {
                beforeExit?.invoke()
            } catch (e: Throwable) {
                Timber.e(e, "Error executing beforeExit during game termination")
            }
            if (isExternalLaunch) {
                performExternalGameFinishCleanup()
            }
            delay(activity.animationDuration().toLong())
            GameService.requestTermination()
        }
        if (isExternalLaunch) {
            activity.finishAndRemoveTask()
        } else {
            activity.finish()
        }
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    suspend fun performExternalGameFinishCleanup() {
        try {
            val duration = System.currentTimeMillis() - startGameTime
            val game = gameProvider()
            if (game != null && game.id != 0) {
                retrogradeDatabase.gameDao().update(game.copy(lastPlayedAt = System.currentTimeMillis()))
                val profileStore = UserProfileStore(activity)
                profileStore.recordSessionStreak()
                val streak = profileStore.getCurrentStreak()
                val baseXP = XPCalculator.baseXPFromSession(duration)
                val xpEarned = (baseXP * XPCalculator.streakMultiplier(streak)).toLong()
                retrogradeDatabase.gameSessionDao().insert(
                    GameSession(
                        gameId = game.id,
                        durationMs = duration,
                        playedAt = System.currentTimeMillis(),
                        xpEarned = xpEarned,
                        streakDay = streak,
                    ),
                )
                PlayTimeStore(activity).add(game.id, duration)
                SaveSyncWork.enqueueGameWork(activity.applicationContext, game.fileName)
            }
            SaveSyncWork.enqueueAutoWork(activity.applicationContext, 5)
            CacheCleanerWork.enqueueCleanCacheLRU(activity.applicationContext)
            if (TVHelper.isTV(activity.applicationContext)) {
                ChannelUpdateWork.enqueue(activity.applicationContext)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Error performing external game finish cleanup")
        }
    }
}
