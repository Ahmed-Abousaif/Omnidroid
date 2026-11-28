package com.omnidroid.ra

import com.omnidroid.lib.ra.RaFileOpenDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal interface NativeBridgeCallbacks {
    fun onAsyncCompleted(
        token: Long,
        result: Int,
        errorMessage: String,
    )

    fun onEvent(
        type: Int,
        primaryId: Int,
        points: Int,
        newRank: Int,
        numEntries: Int,
        errCode: Int,
        title: String,
        description: String,
        badgeUrl: String,
        measured: String,
        extra1: String,
        extra2: String,
    )
}

internal class NativeRetroAchievementsBridge(
    private val okHttpClient: OkHttpClient,
    private val callbacks: NativeBridgeCallbacks,
) {
    private val httpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightCalls = ConcurrentHashMap<Long, okhttp3.Call>()

    @Volatile
    var activeFileOpenDelegate: RaFileOpenDelegate? = null

    fun ensureBridgeLoaded(): Boolean {
        if (bridgeLoaded.get()) return true
        return synchronized(loadLock) {
            if (bridgeLoaded.get()) return@synchronized true
            try {
                System.loadLibrary("omnidroid-ra-bridge")
                bridgeLoaded.set(true)
                true
            } catch (t: UnsatisfiedLinkError) {
                Timber.e(t, "Failed to load libomnidroid-ra-bridge.so")
                false
            }
        }
    }

    fun cancelAllHttp() {
        val iterator = inFlightCalls.values.iterator()
        while (iterator.hasNext()) {
            runCatching { iterator.next().cancel() }
            iterator.remove()
        }
    }

    @Suppress("unused") // Called via JNI from ra_native_bridge.cpp
    fun onNativeHttpRequest(
        requestId: Long,
        url: String,
        postData: String?,
        contentType: String?,
        userAgent: String?,
    ) {
        httpScope.launch {
            var status = OMNI_RA_HTTP_RETRYABLE_CLIENT_ERROR
            var bytes = ByteArray(0)
            try {
                val builder = Request.Builder().url(url)
                if (!userAgent.isNullOrBlank()) {
                    builder.header("User-Agent", userAgent)
                }
                if (postData != null) {
                    val mediaType = (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                    builder.post(postData.toRequestBody(mediaType))
                } else {
                    builder.get()
                }
                val call = okHttpClient.newCall(builder.build())
                inFlightCalls[requestId] = call
                try {
                    call.execute().use { resp ->
                        status = resp.code
                        bytes = resp.body?.bytes() ?: ByteArray(0)
                    }
                } finally {
                    inFlightCalls.remove(requestId)
                }
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Malformed RA request URL")
                status = OMNI_RA_HTTP_CLIENT_ERROR
            } catch (e: IOException) {
                Timber.d(e, "RA HTTP request transient error")
                status = OMNI_RA_HTTP_RETRYABLE_CLIENT_ERROR
            } catch (t: Throwable) {
                Timber.w(t, "RA HTTP request unexpected failure")
                status = OMNI_RA_HTTP_CLIENT_ERROR
            }
            if (bridgeLoaded.get()) {
                nativeHttpComplete(requestId, status, bytes)
            }
        }
    }

    @Suppress("unused") // Called via JNI from ra_native_bridge.cpp
    fun onNativeAsyncComplete(
        token: Long,
        result: Int,
        errorMessage: String,
    ) {
        callbacks.onAsyncCompleted(token, result, errorMessage)
    }

    @Suppress("unused") // Called via JNI from ra_native_bridge.cpp
    fun onNativeEvent(
        type: Int,
        primaryId: Int,
        points: Int,
        newRank: Int,
        numEntries: Int,
        errCode: Int,
        title: String,
        description: String,
        badgeUrl: String,
        measured: String,
        extra1: String,
        extra2: String,
    ) {
        callbacks.onEvent(
            type = type,
            primaryId = primaryId,
            points = points,
            newRank = newRank,
            numEntries = numEntries,
            errCode = errCode,
            title = title,
            description = description,
            badgeUrl = badgeUrl,
            measured = measured,
            extra1 = extra1,
            extra2 = extra2,
        )
    }

    @Suppress("unused") // Called via JNI from ra_native_bridge.cpp
    fun onNativeOpenFd(path: String): Int {
        val delegate = activeFileOpenDelegate ?: return -1
        val pfd = runCatching { delegate.openFd(path) }.getOrNull() ?: return -1
        return try {
            pfd.detachFd()
        } catch (_: Throwable) {
            runCatching { pfd.close() }
            -1
        }
    }

    external fun nativeLoadModule(
        path: String,
        expectedAbiMajor: Int,
        outBuildInfo: Array<String?>,
    ): String?

    external fun nativeUnloadModule()

    external fun nativeHttpComplete(
        requestId: Long,
        status: Int,
        body: ByteArray,
    )

    external fun nativeSetUserAgent(userAgent: String)

    external fun nativeApplySettings(
        hardcore: Boolean,
        encore: Boolean,
        unofficial: Boolean,
        spectator: Boolean,
    )

    external fun nativeGetHardcore(): Boolean

    external fun nativeLoginWithPassword(
        token: Long,
        username: String,
        password: String,
    )

    external fun nativeLoginWithToken(
        token: Long,
        username: String,
        apiToken: String,
    )

    external fun nativeLogout()

    external fun nativeGetUserInfo(): Array<String>?

    external fun nativeIsSystemAllowed(
        libraryName: String?,
        consoleId: Int,
    ): Boolean

    external fun nativeIsSettingAllowed(
        libraryName: String?,
        consoleId: Int,
        key: String,
        value: String,
    ): Boolean

    external fun nativeBeginIdentifyAndLoadGame(
        token: Long,
        consoleId: Int,
        path: String,
        data: ByteArray?,
    )

    external fun nativeBeginLoadGameHash(
        token: Long,
        hash: String,
    )

    external fun nativeBeginIdentifyAndChangeMedia(
        token: Long,
        path: String,
        data: ByteArray?,
    )

    external fun nativeUnloadGame()

    external fun nativeAttachLibretroSession(consoleId: Int)

    external fun nativeDetachLibretroSession()

    external fun nativeRefreshMemoryMap(consoleId: Int)

    external fun nativeDumpMemoryRegions(): String

    external fun nativeIdle()

    external fun nativeReset()

    external fun nativeCanPause(): Int

    external fun nativeSerializeProgress(): ByteArray?

    external fun nativeDeserializeProgress(data: ByteArray): Boolean

    external fun nativeGetGameInfo(): Array<String>?

    external fun nativeGetUserGameSummary(): LongArray?

    external fun nativeGetRichPresence(): String

    external fun nativeGetAchievements(
        category: Int,
        grouping: Int,
    ): Array<Array<String>>?

    external fun nativeGetLeaderboards(grouping: Int): Array<Array<String>>?

    external fun nativeGetLibraryName(): String?

    external fun nativeGetLibraryVersion(): String?

    companion object {
        const val OMNI_RA_HTTP_CLIENT_ERROR = -1
        const val OMNI_RA_HTTP_RETRYABLE_CLIENT_ERROR = -2

        private val bridgeLoaded = AtomicBoolean(false)
        private val loadLock = Any()
    }
}
