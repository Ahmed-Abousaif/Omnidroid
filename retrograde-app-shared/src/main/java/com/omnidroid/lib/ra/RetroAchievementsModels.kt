package com.omnidroid.lib.ra

import kotlinx.serialization.Serializable

data class RaModuleBuildInfo(
    val abiMajor: Int,
    val abiMinor: Int,
    val moduleVersion: String,
    val rcheevosVersion: String,
    val gitHash: String,
)

sealed class RaModuleLoadStatus {
    data object Missing : RaModuleLoadStatus()

    data object Loading : RaModuleLoadStatus()

    data class Loaded(
        val buildInfo: RaModuleBuildInfo,
        val path: String,
    ) : RaModuleLoadStatus()

    data class Failed(
        val reason: String,
    ) : RaModuleLoadStatus()
}

data class RaUserInfo(
    val username: String,
    val displayName: String,
    val token: String,
    val score: Int,
    val scoreSoftcore: Int,
    val numUnreadMessages: Int,
    val avatarUrl: String,
    val avatarLastUpdated: Long,
)

data class RaGameInfo(
    val id: Int,
    val consoleId: Int,
    val title: String,
    val hash: String,
    val badgeName: String,
    val badgeUrl: String,
)

data class RaUserGameSummary(
    val numCoreAchievements: Int,
    val numUnofficialAchievements: Int,
    val numUnlockedAchievements: Int,
    val numUnsupportedAchievements: Int,
    val pointsCore: Int,
    val pointsUnlocked: Int,
    val beatenTime: Long,
    val completedTime: Long,
)

@Serializable
data class RaAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlockTime: Long,
    val measuredPercent: Float,
    val measuredProgress: String,
    val rarity: Float,
    val rarityHardcore: Float,
    val state: Int,
    val category: Int,
    val bucket: Int,
    val unlocked: Int,
    val type: Int,
    val badgeName: String,
    val badgeUrl: String,
    val badgeLockedUrl: String,
)

@Serializable
data class RaAchievementBucket(
    val subsetId: Int,
    val bucketType: Int,
    val label: String,
    val achievements: List<RaAchievement>,
)

@Serializable
data class RaLeaderboard(
    val id: Int,
    val title: String,
    val description: String,
    val trackerValue: String,
    val state: Int,
    val format: Int,
    val lowerIsBetter: Boolean,
)

@Serializable
data class RaLeaderboardBucket(
    val subsetId: Int,
    val bucketType: Int,
    val label: String,
    val leaderboards: List<RaLeaderboard>,
)

data class RaLeaderboardTracker(
    val id: Int,
    val display: String,
)

data class RaLeaderboardScoreboardEntry(
    val rank: Int,
    val username: String,
    val score: String,
)

data class RaLeaderboardScoreboard(
    val leaderboardId: Int,
    val submittedScore: String,
    val bestScore: String,
    val newRank: Int,
    val numEntries: Int,
    val topEntries: List<RaLeaderboardScoreboardEntry>,
)

data class RaSubset(
    val id: Int,
    val title: String,
    val badgeName: String,
    val badgeUrl: String,
    val numAchievements: Int,
    val numLeaderboards: Int,
)

data class RaServerError(
    val result: Int,
    val relatedId: Int,
    val errorMessage: String,
    val api: String,
)

data class RetroAchievementsSettings(
    val enabled: Boolean = false,
    val hardcore: Boolean = true,
    val encoreMode: Boolean = false,
    val unofficialEnabled: Boolean = false,
    val spectatorMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val leaderboardsEnabled: Boolean = true,
    val richPresenceEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val notificationPosition: RaNotificationPosition = RaNotificationPosition.TOP_LEFT,
)

enum class RaNotificationPosition(
    val key: String,
) {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right"),
    ;

    companion object {
        fun fromKey(key: String?): RaNotificationPosition = entries.firstOrNull { it.key == key } ?: TOP_LEFT
    }
}

sealed class RaPauseDecision {
    data object Allowed : RaPauseDecision()

    data class Cooldown(
        val framesRemaining: Int,
    ) : RaPauseDecision()
}

sealed class RetroAchievementsEvent {
    data class AchievementTriggered(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class LeaderboardStarted(
        val leaderboard: RaLeaderboard,
    ) : RetroAchievementsEvent()

    data class LeaderboardFailed(
        val leaderboard: RaLeaderboard,
    ) : RetroAchievementsEvent()

    data class LeaderboardSubmitted(
        val leaderboard: RaLeaderboard,
    ) : RetroAchievementsEvent()

    data class LeaderboardScoreboard(
        val scoreboard: RaLeaderboardScoreboard,
    ) : RetroAchievementsEvent()

    data class ChallengeIndicatorShow(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class ChallengeIndicatorHide(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class ProgressIndicatorShow(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class ProgressIndicatorHide(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class ProgressIndicatorUpdate(
        val achievement: RaAchievement,
    ) : RetroAchievementsEvent()

    data class LeaderboardTrackerShow(
        val tracker: RaLeaderboardTracker,
    ) : RetroAchievementsEvent()

    data class LeaderboardTrackerHide(
        val tracker: RaLeaderboardTracker,
    ) : RetroAchievementsEvent()

    data class LeaderboardTrackerUpdate(
        val tracker: RaLeaderboardTracker,
    ) : RetroAchievementsEvent()

    data object ResetRequired : RetroAchievementsEvent()

    data object GameCompleted : RetroAchievementsEvent()

    data class SubsetCompleted(
        val subset: RaSubset,
    ) : RetroAchievementsEvent()

    data class ServerError(
        val error: RaServerError,
    ) : RetroAchievementsEvent()

    data object Disconnected : RetroAchievementsEvent()

    data object Reconnected : RetroAchievementsEvent()
}
