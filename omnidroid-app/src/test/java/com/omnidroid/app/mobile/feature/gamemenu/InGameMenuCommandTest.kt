package com.omnidroid.app.mobile.feature.gamemenu

import com.omnidroid.app.shared.GameMenuContract as Contract
import org.junit.Assert.assertEquals
import org.junit.Test

class InGameMenuCommandTest {
    @Test fun speedIncludesLegacyCompatibilityAndOneTimesDisablesFastForward() {
        assertEquals(mapOf(Contract.RESULT_SET_FRAME_SPEED to 1, Contract.RESULT_ENABLE_FAST_FORWARD to false), InGameMenuCommand.Speed(1).extras())
        assertEquals(mapOf(Contract.RESULT_SET_FRAME_SPEED to 8, Contract.RESULT_ENABLE_FAST_FORWARD to true), InGameMenuCommand.Speed(8).extras())
    }
    @Test fun slotAndDiscIndicesRemainZeroBased() {
        assertEquals(mapOf(Contract.RESULT_SAVE to 0), InGameMenuCommand.Save(0).extras())
        assertEquals(mapOf(Contract.RESULT_LOAD to 2), InGameMenuCommand.Load(2).extras())
        assertEquals(mapOf(Contract.RESULT_CHANGE_DISK to 0), InGameMenuCommand.Disc(0).extras())
    }
    @Test fun sessionCommandsHaveOnlyTheirIntendedResult() {
        assertEquals(mapOf(Contract.RESULT_RESET to true), InGameMenuCommand.Restart.extras())
        assertEquals(mapOf(Contract.RESULT_QUIT to true), InGameMenuCommand.Quit.extras())
        assertEquals(mapOf(Contract.RESULT_ENABLE_AUDIO to false), InGameMenuCommand.Audio(false).extras())
        assertEquals(mapOf(Contract.RESULT_EDIT_TOUCH_CONTROLS to true), InGameMenuCommand.EditTouchControls.extras())
    }

    @Test fun autosaveAndNewSlotCommandsDoNotAliasManualSlotIndices() {
        assertEquals(mapOf(Contract.RESULT_LOAD_AUTOSAVE to true), InGameMenuCommand.LoadAutoSave.extras())
        assertEquals(mapOf(Contract.RESULT_SAVE_NEW to true), InGameMenuCommand.SaveNew.extras())
        assertEquals(mapOf(Contract.RESULT_SAVE to 12), InGameMenuCommand.Save(12).extras())
        assertEquals(mapOf(Contract.RESULT_LOAD to 12), InGameMenuCommand.Load(12).extras())
    }
}
