package com.omnidroid.lib.backend

import android.graphics.Bitmap
import android.view.View

/**
 * Minimal active emulation session owned by the host activity.
 * Emulation engines implement this along with optional capability interfaces
 * (e.g. [StateSaveableSession], [DiscSwappableSession]).
 */
interface BackendSession {
    val view: View
    val capabilities: BackendCapabilities

    fun start()
    fun pause()
    fun resume()
    fun stop()

    fun sendNormalizedKey(action: Int, key: NormalizedKey, port: Int = 0): Boolean
    fun sendNormalizedMotion(source: NormalizedMotionSource, x: Float, y: Float, port: Int = 0): Boolean

    fun applySetting(key: String, value: String)
    fun setAudioMuted(muted: Boolean)
}

interface StateSaveableSession {
    suspend fun saveState(slot: Int): Boolean
    suspend fun loadState(slot: Int): Boolean
    suspend fun getAutoSaveState(): ByteArray?
    suspend fun restoreAutoSaveState(state: ByteArray): Boolean
}

interface DiscSwappableSession {
    val totalDiscs: Int
    val currentDisc: Int
    suspend fun changeDisc(discIndex: Int): Boolean
}

interface FastForwardableSession {
    fun setFastForwardEnabled(enabled: Boolean)
    fun setSpeedMultiplier(multiplier: Float)
}

interface RewindableSession {
    fun setRewindEnabled(enabled: Boolean)
}

interface ScreenshotSession {
    suspend fun captureScreenshot(): Bitmap?
}

