package com.omnidroid.lib.ra

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RaModuleDeliveryTest {
    @Test
    fun moduleManagerDownloadsValidElfRespectsPlayPolicyAndUsesCache() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            val prefs: SharedPreferences = context.getSharedPreferences("ra_mod_test", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            File(context.filesDir, "modules").deleteRecursively()

            val elfBytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2, 3, 4)
            var serveCorruptBytes = false
            var downloadCount = 0
            val requestedUrls = mutableListOf<String>()

            val interceptor =
                Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls.add(url)
                    downloadCount++
                    val payload = if (serveCorruptBytes) "<html>404</html>".toByteArray() else elfBytes
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }

            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val store = RaModuleStore(context, prefs)
            val baseUri = Uri.parse("https://test.invalid/")

            // Play policy disables remote binary download from GitHub
            val playMgr =
                RaModuleManager(
                    store = store,
                    okHttpClient = client,
                    allowRemoteBinaryDownload = false,
                    targetVersion = "3.2.0",
                    baseUri = baseUri,
                    deviceAbisProvider = { listOf("arm64-v8a") },
                )
            val playOutcome = playMgr.checkAndUpdateIfNeeded(force = true)
            assertEquals(RaUpdateCheckOutcome.DisabledOnDistributionChannel, playOutcome)
            assertEquals(0, downloadCount)

            val freeMgr =
                RaModuleManager(
                    store = store,
                    okHttpClient = client,
                    allowRemoteBinaryDownload = true,
                    targetVersion = "3.2.0",
                    baseUri = baseUri,
                    deviceAbisProvider = { listOf("arm64-v8a") },
                )
            assertEquals(
                "https://test.invalid/3.2.0/modules/omni_ra/src/main/jniLibs/arm64-v8a/libomnidroid_ra.so",
                freeMgr.artifactUrlFor(abi = "arm64-v8a"),
            )

            // Reject non-ELF responses before promoting into module storage
            serveCorruptBytes = true
            assertTrue(freeMgr.checkAndUpdateIfNeeded(force = false) is RaUpdateCheckOutcome.Failed)
            assertNull(freeMgr.resolveLoadableModule())

            // Valid ELF downloads cleanly and promotes to versioned directory
            serveCorruptBytes = false
            val installedOutcome = freeMgr.checkAndUpdateIfNeeded(force = false)
            assertTrue(installedOutcome is RaUpdateCheckOutcome.Updated)
            val installed = freeMgr.resolveLoadableModule()
            assertNotNull(installed)
            assertEquals("3.2.0", installed!!.version)
            assertTrue(installed.file.isFile)
            val downloadsAfterInstall = downloadCount

            // Subsequent non-forced check serves straight from cache with zero HTTP calls
            val cachedOutcome = freeMgr.checkAndUpdateIfNeeded(force = false)
            assertEquals(RaUpdateCheckOutcome.UpToDate("3.2.0"), cachedOutcome)
            assertEquals(downloadsAfterInstall, downloadCount)

            // Native load success updates rcheevos metadata; native init failure deletes corrupt cached file
            freeMgr.markNativeInitSucceeded(
                installed,
                RaModuleBuildInfo(
                    abiMajor = 1,
                    abiMinor = 0,
                    moduleVersion = "1.0.0",
                    rcheevosVersion = "12.5.0",
                    gitHash = "abc",
                ),
            )
            assertEquals("12.5.0", freeMgr.getDeliveryStatus().rcheevosVersion)

            freeMgr.markNativeInitFailed(installed, "dlopen test error")
            assertNull(freeMgr.resolveLoadableModule())
            assertEquals("dlopen test error", freeMgr.getDeliveryStatus().lastError)
        }

    @Test
    fun fallsBackToMasterBranchWhenReleaseTagIsUnavailable() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            val prefs: SharedPreferences = context.getSharedPreferences("ra_mod_fallback_test", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            File(context.filesDir, "modules").deleteRecursively()

            val elfBytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 9, 8, 7, 6)
            val requestedUrls = mutableListOf<String>()

            val interceptor =
                Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls.add(url)
                    if ("/3.2.0/" in url) {
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("Not Found".toResponseBody("text/plain".toMediaType()))
                            .build()
                    } else {
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(elfBytes.toResponseBody("application/octet-stream".toMediaType()))
                            .build()
                    }
                }

            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val store = RaModuleStore(context, prefs)
            val manager =
                RaModuleManager(
                    store = store,
                    okHttpClient = client,
                    allowRemoteBinaryDownload = true,
                    targetVersion = "3.2.0",
                    baseUri = Uri.parse("https://test.invalid/"),
                    deviceAbisProvider = { listOf("arm64-v8a") },
                )

            val outcome = manager.checkAndUpdateIfNeeded(force = false)
            assertTrue(outcome is RaUpdateCheckOutcome.Updated)
            assertEquals(
                listOf(
                    "https://test.invalid/3.2.0/modules/omni_ra/src/main/jniLibs/arm64-v8a/libomnidroid_ra.so",
                    "https://test.invalid/master/modules/omni_ra/src/main/jniLibs/arm64-v8a/libomnidroid_ra.so",
                ),
                requestedUrls,
            )
            val installed = manager.resolveLoadableModule()
            assertNotNull(installed)
            assertEquals("3.2.0", installed!!.version)
            assertTrue(installed.file.isFile)
        }
}
