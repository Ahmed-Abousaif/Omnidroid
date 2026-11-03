package org.yuzu.yuzu_emu

import android.view.Surface
import androidx.annotation.Keep
import org.yuzu.yuzu_emu.utils.FileUtil
import timber.log.Timber

@Keep
object NativeLibrary {

    interface EmulationCallback {
        fun onEmulationStarted()
        fun onEmulationStopped(status: Int)
        fun onProgramChanged(programIndex: Int)
        fun onExitEmulation(resultCode: Int)
    }

    var emulationCallback: EmulationCallback? = null

    var isLoaded = false
        private set

    fun load() {
        if (!isLoaded) {
            try {
                System.loadLibrary("yuzu-android")
                isLoaded = true
                Timber.i("libyuzu-android.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load libyuzu-android.so")
                throw e
            }
        }
    }

    // --- File & Storage Callbacks (called by native C++ Common::FS::Android) ---

    @Keep
    @JvmStatic
    fun openContentUri(path: String?, openmode: String?): Int {
        if (path == null) return -1
        return FileUtil.openContentUri(path, openmode)
    }

    @Keep
    @JvmStatic
    fun getSize(path: String?): Long {
        if (path == null) return 0L
        return FileUtil.getFileSize(path)
    }

    @Keep
    @JvmStatic
    fun exists(path: String?): Boolean {
        if (path == null) return false
        return FileUtil.exists(path)
    }

    @Keep
    @JvmStatic
    fun isDirectory(path: String?): Boolean {
        if (path == null) return false
        return FileUtil.isDirectory(path)
    }

    @Keep
    @JvmStatic
    fun getParentDirectory(path: String): String {
        return java.io.File(path).parent ?: ""
    }

    @Keep
    @JvmStatic
    fun getFilename(path: String): String {
        return FileUtil.getFilename(android.net.Uri.parse(path))
    }

    @Keep
    @JvmStatic
    fun copyFileToStorage(source: String, destdir: String): Boolean {
        return FileUtil.copyUriToInternalStorage(android.net.Uri.parse(source), destdir) != null
    }

    @Keep
    @JvmStatic
    fun getFileExtension(source: String): String {
        return FileUtil.getExtension(android.net.Uri.parse(source))
    }

    // --- Emulation Lifecycle Callbacks (called by native C++ id_cache.cpp) ---

    @Keep
    @JvmStatic
    fun onEmulationStarted() {
        Timber.i("Native callback: onEmulationStarted")
        emulationCallback?.onEmulationStarted()
    }

    @Keep
    @JvmStatic
    fun onEmulationStopped(status: Int) {
        Timber.i("Native callback: onEmulationStopped status=$status")
        emulationCallback?.onEmulationStopped(status)
    }

    @Keep
    @JvmStatic
    fun onProgramChanged(programIndex: Int) {
        Timber.i("Native callback: onProgramChanged index=$programIndex")
        emulationCallback?.onProgramChanged(programIndex)
    }

    @Keep
    @JvmStatic
    fun exitEmulationActivity(resultCode: Int) {
        Timber.e("Native callback: exitEmulationActivity resultCode=$resultCode")
        emulationCallback?.onExitEmulation(resultCode)
    }

    @Keep
    @JvmStatic
    fun openExternalUrl(url: String) {
        Timber.i("Native callback: openExternalUrl $url")
    }

    @Keep
    @JvmStatic
    fun addNetPlayMessage(type: Int, message: String) {
        Timber.i("Native callback: addNetPlayMessage type=$type msg=$message")
    }

    @Keep
    @JvmStatic
    fun clearChat() {
        Timber.i("Native callback: clearChat")
    }

    // --- Native methods implemented by libyuzu-android.so ---

    external fun initializeGpuDriver(
        hookLibDir: String?,
        customDriverDir: String?,
        customDriverName: String?,
        fileRedirectDir: String?,
    )

    external fun setAppDirectory(directory: String)

    external fun reloadKeys(): Boolean

    external fun initializeSystem(reload: Boolean)

    external fun isFirmwareAvailable(): Boolean

    external fun firmwareVersion(): String

    external fun gameRequiresFirmware(programId: String): Boolean

    external fun run(path: String?, programIndex: Int, frontendInitiated: Boolean)

    external fun surfaceChanged(surf: Surface?)

    external fun surfaceDestroyed()

    external fun unpauseEmulation()

    external fun pauseEmulation()

    external fun stopEmulation()

    external fun isRunning(): Boolean

    external fun isPaused(): Boolean

    external fun applySettings()
}
