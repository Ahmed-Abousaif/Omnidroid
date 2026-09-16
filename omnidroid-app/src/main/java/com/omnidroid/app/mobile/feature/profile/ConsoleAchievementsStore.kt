package com.omnidroid.app.mobile.feature.profile

import android.content.Context
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A single console entry with its unlock status. */
data class ConsoleAchievement(
    val metaSystemID: MetaSystemID,
    val nameResId: Int,
    val imageResId: Int,
    val unlocked: Boolean,
)

/**
 * Derives console unlock status by combining two sources of truth:
 *  1. [RegisteredSystemsStore] — consoles explicitly installed via "Add Consoles".
 *  2. [RetrogradeDatabase.gameDao] — consoles inferred from scanned games.
 *
 * A console counts as unlocked if it appears in either source.
 */
class ConsoleAchievementsStore(
    private val context: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val registeredSystemsStore: RegisteredSystemsStore,
) {
    /** Total number of consoles in the roster. */
    val totalConsoles: Int = MetaSystemID.values().size

    /**
     * Flow of all [MetaSystemID] values annotated with unlock status, unlocked first.
     * Re-emits whenever registered systems or game library changes.
     */
    fun observeAchievements(): Flow<List<ConsoleAchievement>> =
        combine(
            registeredSystemsStore.observe(),
            retrogradeDb.gameDao().selectSystemsWithCount(),
        ) { registered, counts ->
            val withGames = counts
                .filter { it.count > 0 }
                .mapNotNull { count ->
                    runCatching {
                        com.omnidroid.lib.library.GameSystem.findById(count.systemId).let {
                            MetaSystemID.fromSystemID(it.id)
                        }
                    }.getOrNull()
                }
                .toSet()

            val unlocked = registered + withGames

            MetaSystemID.values()
                .map { meta ->
                    ConsoleAchievement(
                        metaSystemID = meta,
                        nameResId = meta.titleResId,
                        imageResId = meta.imageResId,
                        unlocked = meta in unlocked,
                    )
                }
                .sortedWith(compareByDescending<ConsoleAchievement> { it.unlocked }.thenBy {
                    context.getString(it.nameResId)
                })
        }

    /** Flow of the unlocked console count only. */
    fun observeUnlockedCount(): Flow<Int> =
        observeAchievements().map { list -> list.count { it.unlocked } }

    /**
     * Returns the highest milestone badge earned for [unlockedCount], or null if below the first tier.
     *
     * | Threshold | Badge            |
     * |-----------|-----------------|
     * | 3         | Collector        |
     * | 10        | Enthusiast       |
     * | 15        | Historian        |
     * | 21 (all)  | Omnidroid Master |
     */
    fun getMilestoneBadge(unlockedCount: Int): MilestoneBadge? =
        MilestoneBadge.values()
            .sortedByDescending { it.threshold }
            .firstOrNull { unlockedCount >= it.threshold }

    enum class MilestoneBadge(val threshold: Int, val emoji: String, val labelKey: String) {
        COLLECTOR(3, "🥉", "collector"),
        ENTHUSIAST(10, "🥈", "enthusiast"),
        HISTORIAN(15, "🥇", "historian"),
        OMNIDROID_MASTER(21, "💎", "master"),
    }
}
