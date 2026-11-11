package com.omnidroid.app.mobile.feature.gamemenu

import com.omnidroid.app.shared.coreoptions.OmnidroidCoreOption
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration

data class GameMenuRequest(
    val coreOptions: List<OmnidroidCoreOption>,
    val advancedCoreOptions: List<OmnidroidCoreOption>,
    val game: Game,
    val coreConfig: SystemCoreConfig,
    val audioEnabled: Boolean,
    val fastForwardSupported: Boolean,
    val frameSpeed: Int,
    val numDisks: Int,
    val currentDisk: Int,
    val currentTiltConfiguration: TiltConfiguration,
    val allTiltConfigurations: List<TiltConfiguration>,
    val editTouchControlsSupported: Boolean = true,
)
