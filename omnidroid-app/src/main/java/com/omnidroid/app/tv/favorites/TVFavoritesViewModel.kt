package com.omnidroid.app.tv.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.omnidroid.common.paging.buildFlowPaging
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

class TVFavoritesViewModel(retrogradeDb: RetrogradeDatabase) : ViewModel() {
    class Factory(val retrogradeDb: RetrogradeDatabase) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TVFavoritesViewModel(retrogradeDb) as T
        }
    }

    val favorites: Flow<PagingData<Game>> =
        buildFlowPaging(20, viewModelScope) { retrogradeDb.gameDao().selectFavorites() }
}
