package com.omnidroid.app.shared.deviceprofile

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class DeviceProfileManager private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val store = DeviceProfileStore(appContext)
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isInitialCaptureDone(): Boolean = store.isInitialCaptureDone()

    fun isBenchmarkPromptShown(): Boolean = store.isBenchmarkPromptShown()

    fun markBenchmarkPromptShown() {
        store.setBenchmarkPromptShown(true)
    }

    fun getProfile(): DeviceProfile? = store.getProfile()

    /**
     * Collects CPU/GPU device info and runs the short (~0.5s) CPU baseline benchmark.
     * Safe to call multiple times; concurrent calls are serialized.
     */
    suspend fun ensureInitialCapture(): DeviceProfile =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val existing = store.getProfile()
                if (store.isInitialCaptureDone() && existing?.quickBenchmark != null) {
                    return@withLock existing
                }
                Timber.i("Running initial device profile capture")
                val (device, cpu, gpu) = DeviceInfoCollector.collect(appContext)
                val quick = CpuBenchmark.runQuick()
                val profile =
                    DeviceProfile(
                        capturedAtEpochMs = System.currentTimeMillis(),
                        device = device,
                        cpu = cpu,
                        gpu = gpu,
                        quickBenchmark = quick,
                        extendedBenchmark = existing?.extendedBenchmark,
                    )
                store.saveProfile(profile)
                profile
            }
        }

    suspend fun runExtendedBenchmark(onProgress: ((Float) -> Unit)? = null): DeviceProfile =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                Timber.i("Running extended CPU benchmark")
                ensureDeviceInfoLocked()
                val progressCallback =
                    onProgress?.let { callback ->
                        { progress: Float ->
                            mainHandler.post { callback(progress) }
                            Unit
                        }
                    }
                val result = CpuBenchmark.runExtended(onProgress = progressCallback)
                store.updateProfile { it.copy(extendedBenchmark = result) }
                store.getProfile() ?: DeviceProfile(extendedBenchmark = result)
            }
        }

    private fun ensureDeviceInfoLocked() {
        val existing = store.getProfile()
        if (existing != null && existing.device.model.isNotEmpty()) return
        val (device, cpu, gpu) = DeviceInfoCollector.collect(appContext)
        store.saveProfile(
            DeviceProfile(
                capturedAtEpochMs = System.currentTimeMillis(),
                device = device,
                cpu = cpu,
                gpu = gpu,
                quickBenchmark = existing?.quickBenchmark,
                extendedBenchmark = existing?.extendedBenchmark,
            ),
        )
    }

    companion object {
        @Volatile
        private var instance: DeviceProfileManager? = null

        fun get(context: Context): DeviceProfileManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceProfileManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
