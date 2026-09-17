package com.omnidroid.app.mobile.feature.gamedetails

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.covers.RawgCoverStore
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameDetailsViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val settingsManager: SettingsManager,
    gameId: Int,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val settingsManager: SettingsManager,
        private val gameId: Int,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameDetailsViewModel(appContext, retrogradeDb, settingsManager, gameId) as T
        }
    }

    data class UiState(
        val game: Game? = null,
        val metadata: GameRemoteMetadata = GameRemoteMetadata(),
        val playTimeMs: Long = 0L,
        val loadingMetadata: Boolean = true,
        val playingTrailer: Boolean = false,
    )

    private val playTimeStore = PlayTimeStore(appContext)
    private val metadataFlow = MutableStateFlow(GameRemoteMetadata())
    private val loadingMetadataFlow = MutableStateFlow(true)
    private val playingTrailerFlow = MutableStateFlow(false)

    val state: StateFlow<UiState> =
        combine(
            retrogradeDb.gameDao().observeById(gameId).filterNotNull(),
            metadataFlow,
            playTimeStore.observe(gameId),
            loadingMetadataFlow,
            playingTrailerFlow,
        ) { game, metadata, playTimeMs, loadingMetadata, playingTrailer ->
            UiState(
                game = game,
                metadata = metadata,
                playTimeMs = playTimeMs,
                loadingMetadata = loadingMetadata,
                playingTrailer = playingTrailer,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            if (settingsManager.enableRawgMetadata()) {
                val row = retrogradeDb.rawgGameMetadataDao().selectByGameId(gameId)
                val metadata = GameMetadataMapper.fromRawg(row)
                metadataFlow.value = metadata
                metadata.coverImageUrl?.takeIf { it.isNotBlank() }?.let {
                    RawgCoverStore.put(gameId, it)
                }
            } else {
                metadataFlow.value = GameRemoteMetadata()
            }
            loadingMetadataFlow.value = false
        }
    }

    fun toggleTrailer() {
        playingTrailerFlow.value = !playingTrailerFlow.value
    }

    fun trailerHtml(): String? = null

    fun trailerSearchUrl(): String? {
        val game = state.value.game ?: return null
        return GameMetadataMapper.trailerSearchUrl(game.title, systemName(game))
    }

    private fun systemName(game: Game): String {
        return runCatching { GameSystem.findById(game.systemId) }
            .getOrNull()
            ?.let { appContext.getString(it.shortTitleResId) }
            .orEmpty()
    }
}
