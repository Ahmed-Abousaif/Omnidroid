package com.omnidroid.app.mobile.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the User Profile screen.
 *
 * Combines data from [UserProfileStore] (lightweight preferences),
 * [ConsoleAchievementsStore] (unlocked console achievements), and
 * [RetrogradeDatabase] (Room-backed total XP, total playtime, and session history).
 */
class ProfileViewModel(
    private val context: Context,
    private val userProfileStore: UserProfileStore,
    private val achievementsStore: ConsoleAchievementsStore,
    private val retrogradeDb: RetrogradeDatabase,
) : ViewModel() {

    data class RecentSessionItem(
        val session: GameSession,
        val gameTitle: String,
    )

    data class UiState(
        val tag: String = UserProfileStore.DEFAULT_TAG,
        val profilePicUri: String? = null,
        val level: Int = 0,
        val xpProgress: Float = 0f,
        val xpCurrent: Long = 0L,
        val xpForNext: Long = 100L,
        val xpToNext: Long = 100L,
        val totalPlayTimeMs: Long = 0L,
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val streakMultiplier: Double = 1.0,
        val achievements: List<ConsoleAchievement> = emptyList(),
        val unlockedConsoles: Int = 0,
        val totalConsoles: Int = MetaSystemID.values().size,
        val milestoneBadge: ConsoleAchievementsStore.MilestoneBadge? = null,
        val recentSessions: List<RecentSessionItem> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Observe profile preferences (tag, picture, streak)
        combine(
            userProfileStore.observeTag(),
            userProfileStore.observeProfilePicUri(),
            userProfileStore.observeCurrentStreak(),
            userProfileStore.observeBestStreak(),
        ) { tag, picUri, currentStreak, bestStreak ->
            _uiState.update { current ->
                current.copy(
                    tag = tag,
                    profilePicUri = picUri,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    streakMultiplier = XPCalculator.streakMultiplier(currentStreak),
                )
            }
        }.launchIn(viewModelScope)

        // Observe total XP and playtime from Room session history
        combine(
            retrogradeDb.gameSessionDao().observeTotalXP(),
            retrogradeDb.gameSessionDao().observeTotalPlayTime(),
        ) { totalXP, totalPlayTimeMs ->
            val level = XPCalculator.levelFromXP(totalXP)
            val progress = XPCalculator.progressInLevel(totalXP)
            val nextLevelXP = XPCalculator.xpForLevel(level + 1)
            val xpToNext = XPCalculator.xpToNextLevel(totalXP)

            _uiState.update { current ->
                current.copy(
                    level = level,
                    xpProgress = progress,
                    xpCurrent = totalXP,
                    xpForNext = nextLevelXP,
                    xpToNext = xpToNext,
                    totalPlayTimeMs = totalPlayTimeMs,
                )
            }
        }.launchIn(viewModelScope)

        // Observe console unlock achievements
        achievementsStore.observeAchievements()
            .onEach { achievements ->
                val unlockedCount = achievements.count { it.unlocked }
                val badge = achievementsStore.getMilestoneBadge(unlockedCount)
                _uiState.update { current ->
                    current.copy(
                        achievements = achievements,
                        unlockedConsoles = unlockedCount,
                        totalConsoles = achievements.size,
                        milestoneBadge = badge,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observe recent 10 sessions with resolved game titles
        retrogradeDb.gameSessionDao().observeRecent(10)
            .onEach { sessions ->
                val items = sessions.map { session ->
                    val game = retrogradeDb.gameDao().selectById(session.gameId)
                    RecentSessionItem(
                        session = session,
                        gameTitle = game?.displayName ?: "Game #${session.gameId}",
                    )
                }
                _uiState.update { current ->
                    current.copy(recentSessions = items)
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateTag(tag: String) {
        userProfileStore.setTag(tag)
    }

    fun updateProfilePicUri(uri: String?) {
        userProfileStore.setProfilePicUri(uri)
    }

    fun removeProfilePic() {
        userProfileStore.setProfilePicUri(null)
    }

    class Factory(
        private val context: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val registeredSystemsStore: RegisteredSystemsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(
                context = context,
                userProfileStore = UserProfileStore(context),
                achievementsStore = ConsoleAchievementsStore(context, retrogradeDb, registeredSystemsStore),
                retrogradeDb = retrogradeDb,
            ) as T
        }
    }
}
