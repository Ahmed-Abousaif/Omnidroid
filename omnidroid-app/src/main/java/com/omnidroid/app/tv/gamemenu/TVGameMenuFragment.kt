package com.omnidroid.app.tv.gamemenu

import android.os.Bundle
import android.view.View
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.omnidroid.R
import com.omnidroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.omnidroid.app.shared.coreoptions.OmnidroidCoreOption
import com.omnidroid.app.shared.gamemenu.GameMenuHelper
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.common.coroutines.safeCollect
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.lib.savesync.GameCloudSyncOverride
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import androidx.preference.ListPreference

class TVGameMenuFragment(
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val inputDeviceManager: InputDeviceManager,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val coreOptions: Array<OmnidroidCoreOption>,
    private val advancedCoreOptions: Array<OmnidroidCoreOption>,
    private val numDisks: Int,
    private val currentDisk: Int,
    private val audioEnabled: Boolean,
    private val frameSpeed: Int,
    private val fastForwardSupported: Boolean,
    private val saveSyncSupported: Boolean = false,
) : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceManager.preferenceDataStore =
            SharedPreferencesHelper.getSharedPreferencesDataStore(requireContext())
        setPreferencesFromResource(R.xml.tv_game_settings, rootKey)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupCloudSaveOption()

        GameMenuHelper.setupAudioOption(preferenceScreen, audioEnabled)
        GameMenuHelper.setupFastForwardOption(activity, preferenceScreen, frameSpeed, fastForwardSupported)
        GameMenuHelper.setupSaveOption(preferenceScreen, systemCoreConfig)

        if (numDisks > 1) {
            GameMenuHelper.setupChangeDiskOption(activity, preferenceScreen, currentDisk, numDisks)
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeLoadAndSave()
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeControllers()
        }
    }

    private fun setupCloudSaveOption() {
        val preference = findPreference<ListPreference>("pref_game_cloud_save")
        if (!saveSyncSupported) {
            preference?.isVisible = false
            return
        }
        val prefs = GameCloudSyncPreferences(requireContext())
        preference?.value = prefs.getOverride(game.id).name.lowercase()
        preference?.setOnPreferenceChangeListener { _, newValue ->
            val override =
                runCatching { GameCloudSyncOverride.valueOf((newValue as String).uppercase()) }
                    .getOrDefault(GameCloudSyncOverride.INHERIT)
            prefs.setOverride(game.id, override)
            true
        }
    }

    private suspend fun initializeControllers() {
        inputDeviceManager.getGamePadsObservable()
            .safeCollect { setupCoreOptions(it.size) }
    }

    private fun setupCoreOptions(connectedGamePads: Int) {
        val coreOptionsScreen =
            findPreference<PreferenceScreen>(GameMenuHelper.SECTION_CORE_OPTIONS)
                ?: return

        coreOptionsScreen.removeAll()

        CoreOptionsPreferenceHelper.addPreferences(
            coreOptionsScreen,
            game.systemId,
            coreOptions.toList(),
            advancedCoreOptions.toList(),
        )

        CoreOptionsPreferenceHelper.addControllers(
            coreOptionsScreen,
            game.systemId,
            systemCoreConfig.coreID,
            connectedGamePads,
            systemCoreConfig.controllerConfigs,
        )
    }

    private suspend fun initializeLoadAndSave() {
        val saveScreen = findPreference<PreferenceScreen>(GameMenuHelper.SECTION_SAVE_GAME)
        val loadScreen = findPreference<PreferenceScreen>(GameMenuHelper.SECTION_LOAD_GAME)

        saveScreen?.isEnabled = systemCoreConfig.statesSupported
        loadScreen?.isEnabled = systemCoreConfig.statesSupported

        val slotsInfo = statesManager.getSavedSlotsInfo(game, systemCoreConfig.coreID)

        slotsInfo.forEachIndexed { index, saveInfo ->
            val bitmap =
                GameMenuHelper.getSaveStateBitmap(
                    requireContext(),
                    statesPreviewManager,
                    saveInfo,
                    game,
                    systemCoreConfig.coreID,
                    index,
                )

            if (saveScreen != null) {
                GameMenuHelper.addSavePreference(saveScreen, index, saveInfo, bitmap)
            }

            if (loadScreen != null) {
                GameMenuHelper.addLoadPreference(loadScreen, index, saveInfo, bitmap)
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (GameMenuHelper.onPreferenceTreeClicked(activity, preference)) {
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }
}
