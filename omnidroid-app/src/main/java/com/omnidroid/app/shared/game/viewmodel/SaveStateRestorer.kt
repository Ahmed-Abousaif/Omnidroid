package com.omnidroid.app.shared.game.viewmodel

import kotlinx.coroutines.delay
import java.io.IOException

/** Retry transient core rejections, but propagate incompatible states, I/O errors and cancellation. */
internal suspend fun restoreSaveStateWithRetry(load: () -> Boolean) {
    repeat(10) { attempt ->
        if (load()) return
        if (attempt < 9) delay(200)
    }
    throw IOException("Core rejected autosave after retries")
}
