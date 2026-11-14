package com.omnidroid.app.shared.gamemenu

import android.content.Context
import android.graphics.Bitmap
import com.omnidroid.common.graphics.GraphicsUtils
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.SaveInfo
import com.omnidroid.lib.saves.StatesPreviewManager
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

object GameMenuHelper {
    fun getSaveStateDescription(saveInfo: SaveInfo): String {
        val formatter = SimpleDateFormat.getDateTimeInstance()
        return if (saveInfo.exists) {
            formatter.format(saveInfo.date)
        } else {
            ""
        }
    }

    suspend fun getSaveStateBitmap(
        context: Context,
        statesPreviewManager: StatesPreviewManager,
        saveStateInfo: SaveInfo,
        game: Game,
        coreID: CoreID,
        index: Int,
    ): Bitmap? {
        if (!saveStateInfo.exists) return null
        val imageSize = GraphicsUtils.convertDpToPixel(96f, context).roundToInt()
        return statesPreviewManager.getPreviewForSlot(game, coreID, index, imageSize)
    }
}
