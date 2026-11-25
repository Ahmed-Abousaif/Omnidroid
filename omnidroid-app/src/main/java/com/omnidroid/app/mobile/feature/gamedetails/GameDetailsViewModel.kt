package com.omnidroid.app.mobile.feature.gamedetails

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.metadata.MetadataRepository
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameDetailsViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val settingsManager: SettingsManager,
    gameId: Int,
    initialGame: Game? = null,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val settingsManager: SettingsManager,
        private val gameId: Int,
        private val initialGame: Game? = null,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameDetailsViewModel(appContext, retrogradeDb, settingsManager, gameId, initialGame) as T
    }

    data class UiState(
        val game: Game? = null,
        val metadata: GameRemoteMetadata = GameRemoteMetadata(),
        val playTimeMs: Long = 0L,
        val sessionCount: Int = 0,
        val playingTrailer: Boolean = false,
    )

    private val playTimeStore = PlayTimeStore(appContext)
    private val metadataFlow = MutableStateFlow(GameRemoteMetadata())
    private val playingTrailerFlow = MutableStateFlow(false)

    val state: StateFlow<UiState> =
        combine(
            retrogradeDb.gameDao().observeById(gameId).filterNotNull(),
            metadataFlow,
            playTimeStore.observe(gameId),
            retrogradeDb.gameSessionDao().observeStatsForGame(gameId),
            playingTrailerFlow,
        ) { game, metadata, legacyPlayTimeMs, sessionStats, playingTrailer ->
            val totalPlayTimeMs = maxOf(sessionStats.totalDurationMs, legacyPlayTimeMs)
            val sessionCount = maxOf(sessionStats.sessionCount, if (totalPlayTimeMs > 0L) 1 else 0)
            UiState(
                game = game,
                metadata = metadata,
                playTimeMs = totalPlayTimeMs,
                sessionCount = sessionCount,
                playingTrailer = playingTrailer,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                game = initialGame,
                playTimeMs = playTimeStore.get(gameId),
                sessionCount = if (playTimeStore.get(gameId) > 0L) 1 else 0,
            ),
        )

    init {
        viewModelScope.launch {
            retrogradeDb
                .gameDao()
                .observeById(gameId)
                .filterNotNull()
                .distinctUntilChangedBy { Triple(it.id, it.systemId, it.title) }
                .collectLatest { game ->
                    try {
                        val metadata = MetadataRepository.get(appContext).details(game)
                        metadataFlow.value = metadata?.let {
                            GameRemoteMetadata(
                                description = it.description,
                                genre = it.genres.joinToString(", ").ifBlank { null },
                                releaseDate = it.releaseDate,
                                publisher = it.publisher,
                                rating = it.rating?.let { rating -> "${rating.toInt()} / 100" },
                                backgroundImageUrl =
                                    it
                                        .artwork(
                                            "background",
                                            null,
                                        )?.url
                                        ?.takeIf(CoverUtils::isAllowedRemoteCoverUrl),
                            )
                        } ?: GameRemoteMetadata()
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Existing local details remain useful offline.
                    }
                }
        }
        viewModelScope.launch {
            retrogradeDb.gameDao().observeById(gameId).collect { game ->
                val url = game?.coverFrontUrl ?: return@collect
                if (url.isBlank()) return@collect
                runCatching {
                    com.omnidroid.app.shared.covers.CoverUtils
                        .coverCache(appContext)
                        .ensureDetail(game.id, url)
                }
            }
        }
    }

    fun toggleTrailer() {
        if (state.value.metadata.trailerUrl
                .isNullOrBlank()
        ) {
            return
        }
        playingTrailerFlow.value = !playingTrailerFlow.value
    }

    fun stopTrailer() {
        playingTrailerFlow.value = false
    }
}
