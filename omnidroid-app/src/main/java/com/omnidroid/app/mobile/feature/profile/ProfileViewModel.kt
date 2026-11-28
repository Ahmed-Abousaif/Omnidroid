package com.omnidroid.app.mobile.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.shared.ra.RetroAchievementsCoordinator
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.GameSession
import com.omnidroid.lib.ra.RaUserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val raCoordinator: RetroAchievementsCoordinator? = null,
) : ViewModel() {
    data class RecentSessionItem(
        val session: GameSession,
        val gameTitle: String,
        val coverArt: String? = null,
        val systemId: String = "",
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
        val sessionsLoading: Boolean = true,
        val raSignedIn: Boolean = false,
        val raUsername: String? = null,
        val raUserInfo: RaUserInfo? = null,
        val raUserLoading: Boolean = false,
        val raAggregate: ProfileRaLibraryAggregate = ProfileRaLibraryAggregate(),
    )

    private var raRefreshJob: Job? = null

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

        // A join avoids one additional database lookup per session and observes game edits too.
        retrogradeDb.gameSessionDao().observeRecentWithGames(50)
            .onEach { sessions ->
                val items =
                    sessions.map { item ->
                        RecentSessionItem(
                            session = item.session,
                            gameTitle = item.gameTitle,
                            coverArt = item.coverArt,
                            systemId = item.systemId,
                        )
                    }
                _uiState.update { current ->
                    current.copy(recentSessions = items, sessionsLoading = false)
                }
            }
            .launchIn(viewModelScope)

        retrogradeDb
            .raGameCacheDao()
            .observeAll()
            .onEach { entries ->
                val aggregate =
                    withContext(Dispatchers.Default) {
                        aggregateProfileRetroAchievements(entries)
                    }
                _uiState.update { current ->
                    current.copy(raAggregate = aggregate)
                }
            }.launchIn(viewModelScope)

        refreshRetroAchievements()
    }

    fun refreshRetroAchievements() {
        val coordinator = raCoordinator ?: return
        val signedIn = coordinator.isSignedIn()
        val username = coordinator.savedUsername()
        val cachedUser = coordinator.engine.getUserInfo()
        if (!signedIn) {
            raRefreshJob?.cancel()
            _uiState.update { current ->
                current.copy(
                    raSignedIn = false,
                    raUsername = null,
                    raUserInfo = null,
                    raUserLoading = false,
                )
            }
            return
        }
        _uiState.update { current ->
            current.copy(
                raSignedIn = true,
                raUsername = cachedUser?.username ?: username,
                raUserInfo = cachedUser ?: current.raUserInfo,
                raUserLoading = cachedUser == null,
            )
        }
        if (cachedUser != null || raRefreshJob?.isActive == true) return
        raRefreshJob =
            viewModelScope.launch {
                val result = runCatching { coordinator.silentLoginIfNeeded() }.getOrNull()
                val resolved = result?.getOrNull() ?: coordinator.engine.getUserInfo()
                _uiState.update { current ->
                    current.copy(
                        raUserInfo = resolved ?: current.raUserInfo,
                        raUsername = resolved?.username ?: current.raUsername,
                        raUserLoading = false,
                    )
                }
            }
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
        private val raCoordinator: RetroAchievementsCoordinator? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(
                context = context,
                userProfileStore = UserProfileStore(context),
                achievementsStore = ConsoleAchievementsStore(context, retrogradeDb, registeredSystemsStore),
                retrogradeDb = retrogradeDb,
                raCoordinator = raCoordinator,
            ) as T
        }
    }
}
