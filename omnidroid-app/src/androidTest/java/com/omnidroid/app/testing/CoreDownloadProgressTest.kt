package com.omnidroid.app.testing

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.ext.feature.core.CoreUpdaterImpl
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SyncProgress
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CoreDownloadProgressTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var updater: CoreUpdaterImpl
    private lateinit var observer: Job
    private val core = CoreID.FCEUMM // No support-file archive or native loading is needed by this fixture.
    private val requests = AtomicInteger()
    private val metadataChecks = AtomicInteger()
    private val published = CopyOnWriteArrayList<SyncProgress>()
    private val downloadingAtRequest = CopyOnWriteArrayList<SyncProgress>()
    private var responseCode = 200
    private var cancelMetadata = false
    private val requestStarted = CountDownLatch(1)
    private var finishDownload: CountDownLatch? = null
    private val cores get() = File(context.filesDir, "cores")

    private fun cached(version: String = GithubCoreDownloader.CORES_VERSION) =
        File(File(cores, version), core.libretroFileName)

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        WorkManager.getInstance(app).cancelUniqueWork(LibraryIndexScheduler.CORE_UPDATE_WORK_ID)
            .result.get(5, TimeUnit.SECONDS)
        val files = temporary.newFolder("files")
        val native = temporary.newFolder("native")
        context =
            object : ContextWrapper(app) {
                override fun getFilesDir() = files

                override fun getApplicationInfo() =
                    ApplicationInfo(super.getApplicationInfo()).apply {
                        nativeLibraryDir = native.absolutePath
                    }
            }
        val client =
            OkHttpClient.Builder().addInterceptor { chain ->
                requests.incrementAndGet()
                SyncProgressTracker.progress.value?.let(downloadingAtRequest::add)
                requestStarted.countDown()
                check(finishDownload?.await(10, TimeUnit.SECONDS) != false)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(responseCode).message("Fixture response")
                    .body("fixture core binary".toResponseBody()).build()
            }.build()
        val installer =
            object : MetadataSliceInstaller {
                override suspend fun ensureSlices(
                    context: Context,
                    coreIDs: List<CoreID>,
                    force: Boolean,
                ): Set<String> {
                    metadataChecks.incrementAndGet()
                    if (cancelMetadata) throw CancellationException("Fixture cancellation")
                    return emptySet()
                }

                override suspend fun removeSlices(sliceIds: Collection<String>) = Unit
            }
        updater =
            CoreUpdaterImpl(
                DirectoriesManager(context),
                Retrofit.Builder().baseUrl("https://example.invalid/").client(client).build(),
                installer,
            )
        SyncProgressTracker.clear()
        observer =
            CoroutineScope(Dispatchers.Unconfined).launch {
                SyncProgressTracker.progress.filterNotNull().collect { published.add(it) }
            }
    }

    @After fun clean() {
        observer.cancel()
        SyncProgressTracker.clear()
    }

    private fun checkCores() =
        runBlocking(Dispatchers.IO) {
            updater.downloadCores(context, listOf(core))
        }

    @Test fun cachedCoreChecksStaySilentAcrossActivityRecreation() {
        cached().apply {
            parentFile!!.mkdirs()
            writeText("current core")
        }
        ActivityScenario.launch(LibraryStressActivity::class.java).use { activity ->
            checkCores()
            activity.recreate()
            checkCores()
        }
        assertEquals(0, requests.get())
        assertEquals("Database checks still run for already installed cores", 2, metadataChecks.get())
        assertTrue("A routine rotation check must not publish download progress", published.isEmpty())
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun oldVersionIsUpdatedAndGenuineDownloadProgressIsCleared() {
        cached("older-release").apply {
            parentFile!!.mkdirs()
            writeText("old core")
        }
        assertNotNull(CoreLibraryLocator.find(context, core))
        assertNull(CoreLibraryLocator.findCurrent(context, core))
        checkCores()
        assertEquals(1, requests.get())
        assertEquals("Downloading core: FCEUmm", downloadingAtRequest.single().stepText)
        assertEquals(cached(), CoreLibraryLocator.findCurrent(context, core))
        assertFalse(published.isEmpty())
        assertNull("A completed console install must not leave stale progress", SyncProgressTracker.progress.value)
    }

    @Test fun emptyCachedBinaryIsDownloadedAgain() {
        cached().apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf())
        }
        assertNull(CoreLibraryLocator.findCurrent(context, core))
        checkCores()
        assertEquals(1, requests.get())
        assertTrue(cached().length() > 0)
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun failedDownloadClearsItsProgressAndDoesNotBecomeACacheHit() {
        responseCode = 500
        assertNotNull(runCatching { checkCores() }.exceptionOrNull())
        assertEquals(1, requests.get())
        assertEquals(1, downloadingAtRequest.size)
        assertNull(CoreLibraryLocator.findCurrent(context, core))
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun cancellationAfterDownloadingIsPropagatedAndClearsProgress() {
        cancelMetadata = true
        assertTrue(runCatching { checkCores() }.exceptionOrNull() is CancellationException)
        assertEquals(1, requests.get())
        assertEquals(1, downloadingAtRequest.size)
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun realDownloadProgressSurvivesActivityRecreation() {
        val finish = CountDownLatch(1)
        finishDownload = finish
        ActivityScenario.launch(LibraryStressActivity::class.java).use { activity ->
            val task = CoroutineScope(Dispatchers.IO).async { updater.downloadCores(context, listOf(core)) }
            try {
                assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
                assertNotNull(SyncProgressTracker.progress.value)
                activity.recreate()
                assertNotNull(
                    "Rotation must retain progress for an actual download",
                    SyncProgressTracker.progress.value,
                )
                val otherCore = CoreID.STELLA
                File(File(cores, GithubCoreDownloader.CORES_VERSION), otherCore.libretroFileName).apply {
                    parentFile!!.mkdirs()
                    writeText("cached other core")
                }
                runBlocking(Dispatchers.IO) { updater.downloadCores(context, listOf(otherCore)) }
                assertNotNull(
                    "A cached check must not hide another active download",
                    SyncProgressTracker.progress.value,
                )
                finish.countDown()
                runBlocking { task.await() }
                assertNull(SyncProgressTracker.progress.value)
            } finally {
                finish.countDown()
                runBlocking { task.cancelAndJoin() }
            }
        }
    }
}
