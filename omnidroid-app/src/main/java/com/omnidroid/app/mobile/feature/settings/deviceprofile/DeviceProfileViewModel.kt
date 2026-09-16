package com.omnidroid.app.mobile.feature.settings.deviceprofile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.shared.deviceprofile.DeviceProfile
import com.omnidroid.app.shared.deviceprofile.DeviceProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceProfileViewModel(
    private val manager: DeviceProfileManager,
) : ViewModel() {
    data class UiState(
        val profile: DeviceProfile? = null,
        val runningExtended: Boolean = false,
        val extendedProgress: Float = 0f,
    )

    private val _uiState = MutableStateFlow(UiState(profile = manager.getProfile()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = manager.ensureInitialCapture()
            _uiState.update { it.copy(profile = profile) }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(profile = manager.getProfile()) }
    }

    fun runExtendedBenchmark() {
        if (_uiState.value.runningExtended) return
        viewModelScope.launch {
            _uiState.update { it.copy(runningExtended = true, extendedProgress = 0f) }
            val profile =
                manager.runExtendedBenchmark { progress ->
                    _uiState.update { it.copy(extendedProgress = progress) }
                }
            _uiState.update {
                it.copy(
                    profile = profile,
                    runningExtended = false,
                    extendedProgress = 1f,
                )
            }
        }
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceProfileViewModel(DeviceProfileManager.get(context)) as T
        }
    }
}
