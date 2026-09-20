package com.omnidroid.touchinput.radial.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.layouts.Arcade4Left
import com.omnidroid.touchinput.radial.layouts.Arcade4Right
import com.omnidroid.touchinput.radial.layouts.Arcade6Left
import com.omnidroid.touchinput.radial.layouts.Arcade6Right
import com.omnidroid.touchinput.radial.layouts.Atari2600Left
import com.omnidroid.touchinput.radial.layouts.Atari2600Right
import com.omnidroid.touchinput.radial.layouts.Atari7800Left
import com.omnidroid.touchinput.radial.layouts.Atari7800Right
import com.omnidroid.touchinput.radial.layouts.DOSLeft
import com.omnidroid.touchinput.radial.layouts.DOSRight
import com.omnidroid.touchinput.radial.layouts.DesmumeLeft
import com.omnidroid.touchinput.radial.layouts.DesmumeRight
import com.omnidroid.touchinput.radial.layouts.GBALeft
import com.omnidroid.touchinput.radial.layouts.GBARight
import com.omnidroid.touchinput.radial.layouts.GBLeft
import com.omnidroid.touchinput.radial.layouts.GBRight
import com.omnidroid.touchinput.radial.layouts.GGLeft
import com.omnidroid.touchinput.radial.layouts.GGRight
import com.omnidroid.touchinput.radial.layouts.Genesis3Left
import com.omnidroid.touchinput.radial.layouts.Genesis3Right
import com.omnidroid.touchinput.radial.layouts.Genesis6Left
import com.omnidroid.touchinput.radial.layouts.Genesis6Right
import com.omnidroid.touchinput.radial.layouts.LynxLeft
import com.omnidroid.touchinput.radial.layouts.LynxRight
import com.omnidroid.touchinput.radial.layouts.MelonDSLeft
import com.omnidroid.touchinput.radial.layouts.MelonDSRight
import com.omnidroid.touchinput.radial.layouts.N64Left
import com.omnidroid.touchinput.radial.layouts.N64Right
import com.omnidroid.touchinput.radial.layouts.NESLeft
import com.omnidroid.touchinput.radial.layouts.NESRight
import com.omnidroid.touchinput.radial.layouts.NGPLeft
import com.omnidroid.touchinput.radial.layouts.NGPRight
import com.omnidroid.touchinput.radial.layouts.Nintendo3DSLeft
import com.omnidroid.touchinput.radial.layouts.Nintendo3DSRight
import com.omnidroid.touchinput.radial.layouts.PCELeft
import com.omnidroid.touchinput.radial.layouts.PCERight
import com.omnidroid.touchinput.radial.layouts.PS2Left
import com.omnidroid.touchinput.radial.layouts.PS2Right
import com.omnidroid.touchinput.radial.layouts.PSPLeft
import com.omnidroid.touchinput.radial.layouts.PSPRight
import com.omnidroid.touchinput.radial.layouts.PSXDualShockLeft
import com.omnidroid.touchinput.radial.layouts.PSXDualShockRight
import com.omnidroid.touchinput.radial.layouts.PSXLeft
import com.omnidroid.touchinput.radial.layouts.PSXRight
import com.omnidroid.touchinput.radial.layouts.SMSLeft
import com.omnidroid.touchinput.radial.layouts.SMSRight
import com.omnidroid.touchinput.radial.layouts.SNESLeft
import com.omnidroid.touchinput.radial.layouts.SNESRight
import com.omnidroid.touchinput.radial.layouts.WSLandscapeLeft
import com.omnidroid.touchinput.radial.layouts.WSLandscapeRight
import com.omnidroid.touchinput.radial.layouts.WSPortraitLeft
import com.omnidroid.touchinput.radial.layouts.WSPortraitRight
import com.omnidroid.touchinput.radial.layouts.GameCubeLeft
import com.omnidroid.touchinput.radial.layouts.GameCubeRight
import com.omnidroid.touchinput.radial.layouts.WiiClassicLeft
import com.omnidroid.touchinput.radial.layouts.WiiClassicRight
import com.omnidroid.touchinput.radial.layouts.WiiRemoteLeft
import com.omnidroid.touchinput.radial.layouts.WiiRemoteRight
import gg.padkit.PadKitScope

enum class TouchControllerID {
    GB,
    NES,
    DESMUME,
    MELONDS,
    PSX,
    PSX_DUALSHOCK,
    N64,
    PSP,
    SNES,
    GBA,
    GENESIS_3,
    GENESIS_6,
    ATARI2600,
    SMS,
    GG,
    ARCADE_4,
    ARCADE_6,
    LYNX,
    ATARI7800,
    PCE,
    NGP,
    DOS,
    WS_LANDSCAPE,
    WS_PORTRAIT,
    NINTENDO_3DS,
    PS2,
    GAMECUBE,
    WII_CLASSIC,
    WII_REMOTE,
    ;

    class Config(
        val leftComposable: @Composable PadKitScope.(
            modifier: Modifier,
            settings: TouchControllerSettingsManager.Settings,
        ) -> Unit,
        val rightComposable: @Composable PadKitScope.(
            modifier: Modifier,
            settings: TouchControllerSettingsManager.Settings,
        ) -> Unit,
    )

    companion object {
        fun getConfig(id: TouchControllerID): Config {
            return when (id) {
                GB ->
                    Config(
                        { modifier, settings -> GBLeft(modifier, settings) },
                        { modifier, settings -> GBRight(modifier, settings) },
                    )

                NES ->
                    Config(
                        { modifier, settings -> NESLeft(modifier, settings) },
                        { modifier, settings -> NESRight(modifier, settings) },
                    )

                DESMUME ->
                    Config(
                        { modifier, settings -> DesmumeLeft(modifier, settings) },
                        { modifier, settings -> DesmumeRight(modifier, settings) },
                    )

                MELONDS ->
                    Config(
                        { modifier, settings -> MelonDSLeft(modifier, settings) },
                        { modifier, settings -> MelonDSRight(modifier, settings) },
                    )

                PSX ->
                    Config(
                        { modifier, settings -> PSXLeft(modifier, settings) },
                        { modifier, settings -> PSXRight(modifier, settings) },
                    )

                PSX_DUALSHOCK ->
                    Config(
                        { modifier, settings -> PSXDualShockLeft(modifier, settings) },
                        { modifier, settings -> PSXDualShockRight(modifier, settings) },
                    )

                N64 ->
                    Config(
                        { modifier, settings -> N64Left(modifier, settings) },
                        { modifier, settings -> N64Right(modifier, settings) },
                    )

                PSP ->
                    Config(
                        { modifier, settings -> PSPLeft(modifier, settings) },
                        { modifier, settings -> PSPRight(modifier, settings) },
                    )

                SNES ->
                    Config(
                        { modifier, settings -> SNESLeft(modifier, settings) },
                        { modifier, settings -> SNESRight(modifier, settings) },
                    )

                GBA ->
                    Config(
                        { modifier, settings -> GBALeft(modifier, settings) },
                        { modifier, settings -> GBARight(modifier, settings) },
                    )

                GENESIS_3 ->
                    Config(
                        { modifier, settings -> Genesis3Left(modifier, settings) },
                        { modifier, settings -> Genesis3Right(modifier, settings) },
                    )

                GENESIS_6 ->
                    Config(
                        { modifier, settings -> Genesis6Left(modifier, settings) },
                        { modifier, settings -> Genesis6Right(modifier, settings) },
                    )

                ATARI2600 ->
                    Config(
                        { modifier, settings -> Atari2600Left(modifier, settings) },
                        { modifier, settings -> Atari2600Right(modifier, settings) },
                    )

                SMS ->
                    Config(
                        { modifier, settings -> SMSLeft(modifier, settings) },
                        { modifier, settings -> SMSRight(modifier, settings) },
                    )

                GG ->
                    Config(
                        { modifier, settings -> GGLeft(modifier, settings) },
                        { modifier, settings -> GGRight(modifier, settings) },
                    )

                ARCADE_4 ->
                    Config(
                        { modifier, settings -> Arcade4Left(modifier, settings) },
                        { modifier, settings -> Arcade4Right(modifier, settings) },
                    )

                ARCADE_6 ->
                    Config(
                        { modifier, settings -> Arcade6Left(modifier, settings) },
                        { modifier, settings -> Arcade6Right(modifier, settings) },
                    )

                LYNX ->
                    Config(
                        { modifier, settings -> LynxLeft(modifier, settings) },
                        { modifier, settings -> LynxRight(modifier, settings) },
                    )

                ATARI7800 ->
                    Config(
                        { modifier, settings -> Atari7800Left(modifier, settings) },
                        { modifier, settings -> Atari7800Right(modifier, settings) },
                    )

                PCE ->
                    Config(
                        { modifier, settings -> PCELeft(modifier, settings) },
                        { modifier, settings -> PCERight(modifier, settings) },
                    )

                NGP ->
                    Config(
                        { modifier, settings -> NGPLeft(modifier, settings) },
                        { modifier, settings -> NGPRight(modifier, settings) },
                    )

                DOS ->
                    Config(
                        { modifier, settings -> DOSLeft(modifier, settings) },
                        { modifier, settings -> DOSRight(modifier, settings) },
                    )

                WS_LANDSCAPE ->
                    Config(
                        { modifier, settings -> WSLandscapeLeft(modifier, settings) },
                        { modifier, settings -> WSLandscapeRight(modifier, settings) },
                    )

                WS_PORTRAIT ->
                    Config(
                        { modifier, settings -> WSPortraitLeft(modifier, settings) },
                        { modifier, settings -> WSPortraitRight(modifier, settings) },
                    )

                NINTENDO_3DS ->
                    Config(
                        { modifier, settings -> Nintendo3DSLeft(modifier, settings) },
                        { modifier, settings -> Nintendo3DSRight(modifier, settings) },
                    )

                PS2 ->
                    Config(
                        { modifier, settings -> PS2Left(modifier, settings) },
                        { modifier, settings -> PS2Right(modifier, settings) },
                    )

                GAMECUBE ->
                    Config(
                        { modifier, settings -> GameCubeLeft(modifier, settings) },
                        { modifier, settings -> GameCubeRight(modifier, settings) },
                    )

                WII_CLASSIC ->
                    Config(
                        { modifier, settings -> WiiClassicLeft(modifier, settings) },
                        { modifier, settings -> WiiClassicRight(modifier, settings) },
                    )

                WII_REMOTE ->
                    Config(
                        { modifier, settings -> WiiRemoteLeft(modifier, settings) },
                        { modifier, settings -> WiiRemoteRight(modifier, settings) },
                    )
            }
        }
    }
}
