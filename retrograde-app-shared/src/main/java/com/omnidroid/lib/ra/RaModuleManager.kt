package com.omnidroid.lib.ra

import android.net.Uri
import android.os.Build
import com.omnidroid.lib.core.GithubCoreDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

sealed class RaUpdateCheckOutcome {
    data class UpToDate(
        val version: String,
    ) : RaUpdateCheckOutcome()

    data class Updated(
        val module: RaInstalledModule,
    ) : RaUpdateCheckOutcome()

    data class Failed(
        val message: String,
    ) : RaUpdateCheckOutcome()

    data object DisabledOnDistributionChannel : RaUpdateCheckOutcome()
}

/**
 * Coordinates resolution and on-demand download of `libomnidroid_ra.so` from OmnidroidCores,
 * matching standard core delivery (`GithubCoreDownloader` on GitHub builds; packaged native libs on Play/Bundle).
 */
class RaModuleManager(
    private val store: RaModuleStore,
    private val okHttpClient: OkHttpClient,
    private val allowRemoteBinaryDownload: Boolean,
    private val targetVersion: String = GithubCoreDownloader.CORES_VERSION,
    private val baseUri: Uri = GithubCoreDownloader.BASE_URI,
    private val deviceAbisProvider: () -> List<String> = { Build.SUPPORTED_ABIS?.toList().orEmpty() },
    private val canCleanupOldFiles: () -> Boolean = { true },
) {
    private val opMutex = Mutex()

    fun resolveLoadableModule(): RaInstalledModule? = store.resolveLoadableModule(expectedVersion = targetVersion)

    fun getDeliveryStatus(): RaModuleDeliveryStatus = store.getDeliveryStatus()

    fun markNativeInitSucceeded(
        module: RaInstalledModule,
        buildInfo: RaModuleBuildInfo,
    ) {
        store.markLoadSucceeded(module, buildInfo, canCleanupOldFiles)
    }

    fun markNativeInitFailed(
        module: RaInstalledModule,
        reason: String,
    ) {
        store.markLoadFailed(module, reason)
    }

    fun artifactUrlFor(
        version: String = targetVersion,
        abi: String,
    ): String =
        baseUri
            .buildUpon()
            .appendEncodedPath("$version/modules/omni_ra/src/main/jniLibs/")
            .appendPath(abi)
            .appendPath(RaModuleStore.MODULE_FILENAME)
            .build()
            .toString()

    suspend fun checkAndUpdateIfNeeded(force: Boolean = false): RaUpdateCheckOutcome =
        opMutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val current = store.resolveLoadableModule(expectedVersion = targetVersion)
                if (current != null && (current.isBundledFallback || !force)) {
                    store.recordCheckResult(now, null)
                    return@withContext RaUpdateCheckOutcome.UpToDate(current.version)
                }

                if (!allowRemoteBinaryDownload) {
                    store.recordCheckResult(now, null)
                    return@withContext RaUpdateCheckOutcome.DisabledOnDistributionChannel
                }

                val abi = deviceAbisProvider().firstOrNull { it.isNotBlank() }
                if (abi == null) {
                    val msg = "No supported ABI reported by device"
                    store.recordCheckResult(now, msg)
                    return@withContext RaUpdateCheckOutcome.Failed(msg)
                }

                val downloadUrl = artifactUrlFor(version = targetVersion, abi = abi)
                val primaryResult =
                    store.downloadToStore(
                        version = targetVersion,
                        url = downloadUrl,
                        okHttpClient = okHttpClient,
                        canCleanupOldFiles = canCleanupOldFiles,
                    )
                val result =
                    if (primaryResult.isFailure && targetVersion != FALLBACK_VERSION) {
                        val fallbackUrl = artifactUrlFor(version = FALLBACK_VERSION, abi = abi)
                        Timber.i(
                            "RA module not available at tag %s; falling back to %s",
                            targetVersion,
                            fallbackUrl,
                        )
                        store.downloadToStore(
                            version = targetVersion,
                            url = fallbackUrl,
                            okHttpClient = okHttpClient,
                            canCleanupOldFiles = canCleanupOldFiles,
                        )
                    } else {
                        primaryResult
                    }
                result.fold(
                    onSuccess = { installed ->
                        store.recordCheckResult(now, null)
                        RaUpdateCheckOutcome.Updated(installed)
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "RA module download failed"
                        Timber.w(err, "Failed to download RA module %s", targetVersion)
                        store.recordCheckResult(now, msg)
                        RaUpdateCheckOutcome.Failed(msg)
                    },
                )
            }
        }

    companion object {
        private const val FALLBACK_VERSION = "master"
    }
}
