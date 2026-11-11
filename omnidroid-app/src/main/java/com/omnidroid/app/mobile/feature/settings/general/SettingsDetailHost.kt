package com.omnidroid.app.mobile.feature.settings.general

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnidroid.app.mobile.feature.settings.bios.BiosScreen
import com.omnidroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.omnidroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.omnidroid.app.mobile.feature.settings.deviceprofile.DeviceProfileScreen
import com.omnidroid.app.mobile.feature.settings.deviceprofile.DeviceProfileViewModel
import com.omnidroid.app.mobile.feature.settings.graphicsapi.GraphicsApiSelectionScreen
import com.omnidroid.app.mobile.feature.settings.graphicsapi.GraphicsApiSelectionViewModel
import com.omnidroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.omnidroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.omnidroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.unrecognized.UnrecognizedFilesScreen
import com.omnidroid.app.mobile.feature.settings.unrecognized.UnrecognizedFilesViewModel
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsSubscreenEntryPoint {
    fun retrogradeDb(): RetrogradeDatabase
    fun biosManager(): BiosManager
    fun directoriesManager(): DirectoriesManager
    fun retrofit(): Retrofit
    fun sharedPreferences(): SharedPreferences
    fun coresSelection(): CoresSelection
    fun inputDeviceManager(): InputDeviceManager
    fun saveSyncManager(): SaveSyncManager
}

@Composable
private fun rememberSettingsSubscreenEntryPoint(): SettingsSubscreenEntryPoint {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsSubscreenEntryPoint::class.java,
        )
    }
}

@Composable
internal fun SettingsSubscreenContent(
    subscreen: SettingsSubscreen,
    key: String? = subscreen.name,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entryPoint = rememberSettingsSubscreenEntryPoint()

    when (subscreen) {
        SettingsSubscreen.UNRECOGNIZED_FILES -> {
            val vm: UnrecognizedFilesViewModel =
                viewModel(
                    key = key,
                    factory = UnrecognizedFilesViewModel.Factory(entryPoint.retrogradeDb()),
                )
            UnrecognizedFilesScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.INPUT_DEVICES -> {
            val vm: InputDevicesSettingsViewModel =
                viewModel(
                    key = key,
                    factory =
                        InputDevicesSettingsViewModel.Factory(
                            context.applicationContext,
                            entryPoint.inputDeviceManager(),
                        ),
                )
            InputDevicesSettingsScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.SAVE_SYNC -> {
            val vm: SaveSyncSettingsViewModel =
                viewModel(
                    key = key,
                    factory =
                        SaveSyncSettingsViewModel.Factory(
                            context.applicationContext as Application,
                            entryPoint.saveSyncManager(),
                            entryPoint.directoriesManager(),
                        ),
                )
            SaveSyncSettingsScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.CORES_SELECTION -> {
            val vm: CoresSelectionViewModel =
                viewModel(
                    key = key,
                    factory =
                        CoresSelectionViewModel.Factory(
                            context.applicationContext,
                            entryPoint.coresSelection(),
                        ),
                )
            CoresSelectionScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.GRAPHICS_API -> {
            val vm: GraphicsApiSelectionViewModel =
                viewModel(
                    key = key,
                    factory =
                        GraphicsApiSelectionViewModel.Factory(
                            context.applicationContext,
                        ),
                )
            GraphicsApiSelectionScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.BIOS -> {
            val vm: BiosSettingsViewModel =
                viewModel(
                    key = key,
                    factory =
                        BiosSettingsViewModel.Factory(
                            entryPoint.biosManager(),
                            entryPoint.directoriesManager(),
                            entryPoint.retrofit().create(CoreUpdater.CoreManagerApi::class.java),
                            entryPoint.sharedPreferences(),
                        ),
                )
            BiosScreen(modifier = modifier, viewModel = vm)
        }
        SettingsSubscreen.DEVICE_PROFILE -> {
            val vm: DeviceProfileViewModel =
                viewModel(
                    key = key,
                    factory = DeviceProfileViewModel.Factory(context.applicationContext),
                )
            DeviceProfileScreen(modifier = modifier, viewModel = vm)
        }
    }
}
