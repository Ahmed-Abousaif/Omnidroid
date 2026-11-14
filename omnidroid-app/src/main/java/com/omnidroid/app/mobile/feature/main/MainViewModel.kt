package com.omnidroid.app.mobile.feature.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(appContext: Context) : ViewModel() {
    class Factory(
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(appContext) as T
        }
    }

    data class UiState(
        val operationInProgress: Boolean = false,
    )

    val state: StateFlow<UiState> =
        PendingOperationsMonitor(appContext).anyOperationInProgress()
            .map { UiState(operationInProgress = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = UiState(),
            )
}
