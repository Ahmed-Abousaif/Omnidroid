package com.omnidroid.app.shared.library

import android.content.Context
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.lib.library.db.dao.GamePlayStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Play stats for every game from a single Room query, shared by all library rows. Each visible
 * row used to open its own observeStatsForGame flow (one Room observer per row while scrolling).
 */
object GameStatsCache {
    @Volatile
    private var stats: StateFlow<Map<Int, GamePlayStats>>? = null

    fun observe(context: Context): StateFlow<Map<Int, GamePlayStats>> =
        stats ?: synchronized(this) {
            stats ?: create(context).also { stats = it }
        }

    private fun create(context: Context): StateFlow<Map<Int, GamePlayStats>> {
        val app = context.applicationContext as OmnidroidApplication
        return app.retrogradeDb.gameSessionDao().observeAllStats()
            .map { rows -> rows.associate { it.gameId to GamePlayStats(it.sessionCount, it.totalDurationMs) } }
            .stateIn(OmnidroidApplication.scope(context), SharingStarted.WhileSubscribed(5_000), emptyMap())
    }
}
