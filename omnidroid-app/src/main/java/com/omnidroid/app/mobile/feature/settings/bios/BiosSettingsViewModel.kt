package com.omnidroid.app.mobile.feature.settings.bios

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.assetsmanager.PCEE2AssetsManager
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class BiosSettingsViewModel(
    private val biosManager: BiosManager,
    private val directoriesManager: DirectoriesManager,
    private val coreApi: CoreUpdater.CoreManagerApi,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {
    class Factory(
        private val biosManager: BiosManager,
        private val directoriesManager: DirectoriesManager,
        private val coreApi: CoreUpdater.CoreManagerApi,
        private val sharedPreferences: SharedPreferences,
    ) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BiosSettingsViewModel(
                    biosManager,
                    directoriesManager,
                    coreApi,
                    sharedPreferences,
                ) as T
            }
        }

    private val refresh = MutableStateFlow(0)
    private val assets = PCEE2AssetsManager()

    val downloading = MutableStateFlow<String?>(null)
    val downloadFailed = MutableStateFlow<String?>(null)

    val uiState =
        refresh
            .flatMapLatest {
                flow { emit(biosManager.getBiosInfoAsync()) }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                BiosManager.BiosInfo(emptyList(), emptyList()),
            )

    fun selectPs2Bios(fileName: String?) {
        biosManager.setPs2Selection(fileName)
        refresh.value += 1
    }

    fun downloadPs2Bios(fileName: String) {
        val region =
            when (fileName) {
                Ps2Bios.USA -> PCEE2AssetsManager.ServedRegion.USA
                Ps2Bios.EUROPE -> PCEE2AssetsManager.ServedRegion.EUROPE
                Ps2Bios.JAPAN_SERVED -> PCEE2AssetsManager.ServedRegion.JAPAN
                else -> return
            }
        if (downloading.value != null) return
        viewModelScope.launch {
            downloading.value = fileName
            downloadFailed.value = null
            try {
                assets.installServedRegion(coreApi, directoriesManager, sharedPreferences, region)
                biosManager.setPs2Selection(fileName)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Could not download PS2 BIOS %s", fileName)
                downloadFailed.value = fileName
            } finally {
                downloading.value = null
                refresh.value += 1
            }
        }
    }
}
