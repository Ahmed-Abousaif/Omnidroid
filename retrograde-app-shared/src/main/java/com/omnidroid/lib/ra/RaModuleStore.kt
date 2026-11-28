package com.omnidroid.lib.ra

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class RaInstalledModule(
    val version: String,
    val file: File,
    val isBundledFallback: Boolean = false,
)

data class RaModuleDeliveryStatus(
    val installedVersion: String? = null,
    val rcheevosVersion: String? = null,
    val lastCheckEpochMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * Resolves bundled (`nativeLibraryDir`) and downloaded (`files/modules/omni_ra/<version>`)
 * `libomnidroid_ra.so` binaries using the same direct ELF staging model as console cores.
 */
class RaModuleStore(
    private val context: Context,
    private val prefs: SharedPreferences,
) {
    private val moduleRoot: File
        get() = File(context.filesDir, "modules/omni_ra")

    fun resolveLoadableModule(
        expectedVersion: String,
        allowBundledFallback: Boolean = true,
    ): RaInstalledModule? {
        if (allowBundledFallback) {
            val bundled = File(context.applicationInfo.nativeLibraryDir, MODULE_FILENAME)
            if (bundled.isFile && bundled.length() > 0L) {
                return RaInstalledModule(
                    version = "bundled",
                    file = bundled,
                    isBundledFallback = true,
                )
            }
        }

        val cached = binaryFileFor(expectedVersion)
        if (cached.isFile && cached.length() > 4L && hasElfMagic(cached)) {
            return RaInstalledModule(
                version = expectedVersion,
                file = cached,
                isBundledFallback = false,
            )
        }
        return null
    }

    fun markLoadSucceeded(
        module: RaInstalledModule,
        buildInfo: RaModuleBuildInfo,
        canCleanupOldFiles: () -> Boolean = { true },
    ) {
        val editor =
            prefs
                .edit()
                .putString(KEY_ACTIVE_VERSION, if (module.isBundledFallback) "bundled" else module.version)
                .putString(KEY_ACTIVE_RCHEEVOS_VERSION, buildInfo.rcheevosVersion)
                .remove(KEY_LAST_ERROR)
        editor.apply()
        if (!module.isBundledFallback) {
            pruneOldVersionsIfSafe(module.version, canCleanupOldFiles)
        }
    }

    fun markLoadFailed(
        module: RaInstalledModule,
        reason: String,
    ) {
        Timber.e("RA module load failed for %s: %s", module.version, reason)
        prefs
            .edit()
            .putString(KEY_LAST_ERROR, reason)
            .apply()
        if (!module.isBundledFallback) {
            runCatching { module.file.delete() }
        }
    }

    fun downloadToStore(
        version: String,
        url: String,
        okHttpClient: OkHttpClient,
        canCleanupOldFiles: () -> Boolean = { true },
    ): Result<RaInstalledModule> {
        val dir = File(moduleRoot, version)
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.failure(IOException("Failed to create RA module directory"))
        }
        val stagedFile = File(dir, "$MODULE_FILENAME.part")
        val destFile = binaryFileFor(version)

        try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("HTTP ${resp.code} downloading RA module"))
                }
                val body = resp.body ?: return Result.failure(IOException("Empty response body"))
                FileOutputStream(stagedFile).use { out ->
                    body.byteStream().copyTo(out)
                    out.fd.sync()
                }
            }

            if (!stagedFile.isFile || stagedFile.length() <= 4L || !hasElfMagic(stagedFile)) {
                stagedFile.delete()
                return Result.failure(IOException("Downloaded file is not a valid ELF binary"))
            }

            stagedFile.setReadable(true, false)
            stagedFile.setExecutable(true, false)

            if (destFile.exists()) {
                destFile.delete()
            }
            if (!stagedFile.renameTo(destFile)) {
                stagedFile.copyTo(destFile, overwrite = true)
                stagedFile.delete()
            }
            destFile.setReadable(true, false)
            destFile.setExecutable(true, false)

            prefs
                .edit()
                .putString(KEY_ACTIVE_VERSION, version)
                .remove(KEY_LAST_ERROR)
                .apply()
            pruneOldVersionsIfSafe(version, canCleanupOldFiles)

            return Result.success(
                RaInstalledModule(
                    version = version,
                    file = destFile,
                    isBundledFallback = false,
                ),
            )
        } catch (t: Throwable) {
            stagedFile.delete()
            return Result.failure(t)
        }
    }

    fun pruneOldVersionsIfSafe(
        keepVersion: String?,
        canDeleteActiveFiles: () -> Boolean,
    ) {
        if (!canDeleteActiveFiles()) return
        moduleRoot.listFiles()?.forEach { child ->
            if (child.name != keepVersion) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    fun getDeliveryStatus(): RaModuleDeliveryStatus =
        RaModuleDeliveryStatus(
            installedVersion = prefs.getString(KEY_ACTIVE_VERSION, null),
            rcheevosVersion = prefs.getString(KEY_ACTIVE_RCHEEVOS_VERSION, null),
            lastCheckEpochMs = prefs.getLong(KEY_LAST_CHECK_EPOCH_MS, 0L),
            lastError = prefs.getString(KEY_LAST_ERROR, null),
        )

    fun recordCheckResult(
        epochMs: Long,
        error: String? = null,
    ) {
        val editor = prefs.edit().putLong(KEY_LAST_CHECK_EPOCH_MS, epochMs)
        if (error != null) editor.putString(KEY_LAST_ERROR, error) else editor.remove(KEY_LAST_ERROR)
        editor.apply()
    }

    private fun binaryFileFor(version: String): File = File(File(moduleRoot, version), MODULE_FILENAME)

    private fun hasElfMagic(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 0x7F.toByte() &&
                    magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)

    companion object {
        const val MODULE_FILENAME = "libomnidroid_ra.so"

        private const val KEY_ACTIVE_VERSION = "ra_mod_active_version"
        private const val KEY_ACTIVE_RCHEEVOS_VERSION = "ra_mod_active_rcheevos_version"
        private const val KEY_LAST_CHECK_EPOCH_MS = "ra_mod_last_check_ms"
        private const val KEY_LAST_ERROR = "ra_mod_last_error"
    }
}
