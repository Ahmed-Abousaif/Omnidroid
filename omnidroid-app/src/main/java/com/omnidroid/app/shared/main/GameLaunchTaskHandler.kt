package com.omnidroid.app.shared.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamedetails.PlayTimeStore
import com.omnidroid.app.mobile.feature.profile.UserProfileStore
import com.omnidroid.app.mobile.feature.profile.XPCalculator
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.gamecrash.GameCrashActivity
import com.omnidroid.app.shared.savesync.SaveSyncWork
import com.omnidroid.app.shared.storage.cache.CacheCleanerWork
import com.omnidroid.ext.feature.review.ReviewManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameSession
import kotlinx.coroutines.delay

class GameLaunchTaskHandler(
    private val reviewManager: ReviewManager,
    private val retrogradeDb: RetrogradeDatabase,
) {
    fun handleGameStart(context: Context) {
        cancelBackgroundWork(context)
    }

    suspend fun handleGameFinish(
        enableRatingFlow: Boolean,
        activity: Activity,
        resultCode: Int,
        data: Intent?,
    ) {
        val finishedGame =
            data?.extras?.getSerializable(BaseGameActivity.PLAY_GAME_RESULT_GAME) as Game?
        rescheduleBackgroundWork(activity.applicationContext, finishedGame)
        when (resultCode) {
            Activity.RESULT_OK -> handleSuccessfulGameFinish(activity, enableRatingFlow, data)
            BaseGameActivity.RESULT_ERROR ->
                handleUnsuccessfulGameFinish(
                    activity,
                    data?.getStringExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR)!!,
                    null,
                )
            BaseGameActivity.RESULT_UNEXPECTED_ERROR ->
                handleUnsuccessfulGameFinish(
                    activity,
                    activity.getString(R.string.omnidroid_crash_disclamer),
                    data?.getStringExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR),
                )
        }
    }

    private fun cancelBackgroundWork(context: Context) {
        SaveSyncWork.cancelAutoWork(context)
        SaveSyncWork.cancelManualWork(context)
        CacheCleanerWork.cancelCleanCacheLRU(context)
    }

    private fun rescheduleBackgroundWork(
        context: Context,
        game: Game? = null,
    ) {
        if (game != null) {
            SaveSyncWork.enqueueGameWork(context, game.fileName)
        }
        SaveSyncWork.enqueueAutoWork(context, 5)
        CacheCleanerWork.enqueueCleanCacheLRU(context)
    }

    private fun handleUnsuccessfulGameFinish(
        activity: Activity,
        message: String,
        messageDetail: String?,
    ) {
        GameCrashActivity.launch(activity, message, messageDetail)
    }

    private suspend fun handleSuccessfulGameFinish(
        activity: Activity,
        enableRatingFlow: Boolean,
        data: Intent?,
    ) {
        val duration =
            data?.extras?.getLong(BaseGameActivity.PLAY_GAME_RESULT_SESSION_DURATION)
                ?: 0L
        val game = data?.extras?.getSerializable(BaseGameActivity.PLAY_GAME_RESULT_GAME) as Game

        updateGamePlayedTimestamp(game)
        // Keep per-game playtime in SharedPreferences for quick reads on game detail screens
        PlayTimeStore(activity).add(game.id, duration)

        // Record a session in Room — this is the source of truth for XP and total playtime
        val profileStore = UserProfileStore(activity)
        profileStore.recordSessionStreak()
        val streak = profileStore.getCurrentStreak()
        val baseXP = XPCalculator.baseXPFromSession(duration)
        val xpEarned = (baseXP * XPCalculator.streakMultiplier(streak)).toLong()
        retrogradeDb.gameSessionDao().insert(
            GameSession(
                gameId = game.id,
                durationMs = duration,
                playedAt = System.currentTimeMillis(),
                xpEarned = xpEarned,
                streakDay = streak,
            ),
        )

        if (enableRatingFlow) {
            displayReviewRequest(activity, duration)
        }
    }

    private suspend fun displayReviewRequest(
        activity: Activity,
        durationMillis: Long,
    ) {
        delay(500)
        reviewManager.launchReviewFlow(activity, durationMillis)
    }

    private suspend fun updateGamePlayedTimestamp(game: Game) {
        retrogradeDb.gameDao().update(game.copy(lastPlayedAt = System.currentTimeMillis()))
    }
}
