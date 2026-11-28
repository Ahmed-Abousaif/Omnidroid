package com.omnidroid.sevenzip

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SevenZipInstrumentedTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun copyAsset(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val out = File(tempFolder.root, name)
        ctx.assets.open("fixtures/$name").use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    @Test
    fun openExtractsSingleLzma2AndAllowsClosingCallerFd() {
        val archiveFile = copyAsset("single_lzma2.7z")
        val callerPfd = ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val archive = SevenZipArchive.open(callerPfd)
        // Native side dup()s the fd; closing caller PFD immediately must stay safe.
        callerPfd.close()

        archive.use {
            val entries = it.entries()
            assertEquals(1, entries.size)
            assertEquals("hello.txt", entries[0].name)
            assertEquals(26L, entries[0].size)
            assertTrue(entries[0].isSupportedCoder)
            assertFalse(entries[0].isDirectory)

            val outFile = File(tempFolder.root, "out_hello.txt")
            val err =
                ParcelFileDescriptor
                    .open(
                        outFile,
                        ParcelFileDescriptor.MODE_CREATE or
                            ParcelFileDescriptor.MODE_TRUNCATE or
                            ParcelFileDescriptor.MODE_READ_WRITE,
                    ).use { outPfd ->
                        it.extract(0, outPfd)
                    }
            assertEquals(null, err)
            assertEquals("Hello Omnidroid 7z world!\n", outFile.readText())
            assertEquals(26L, it.progress())
        }
    }

    @Test
    fun solidMultiFileAndLzma1ExtractExactBytes() {
        val solidFile = copyAsset("solid_multi.7z")
        val dest = File(tempFolder.root, "solid_target.bin")

        runBlocking {
            val progressSnapshots = mutableListOf<Long>()
            ArchiveExtractor.extractEntryToFile(
                openSourceFd = { ParcelFileDescriptor.open(solidFile, ParcelFileDescriptor.MODE_READ_ONLY) },
                entryName = "04_target_日本語.bin",
                destFile = dest,
                onProgress = { decoded, _ -> progressSnapshots += decoded },
            )
            assertTrue(progressSnapshots.isNotEmpty())
        }

        val expected = patternBytes(2, 90 * 1024)
        assertArrayEquals(expected, dest.readBytes())

        val lzma1File = copyAsset("single_lzma1.7z")
        val lzma1Dest = File(tempFolder.root, "lzma1_out.bin")
        runBlocking {
            ArchiveExtractor.extractEntryToFile(
                openSourceFd = { ParcelFileDescriptor.open(lzma1File, ParcelFileDescriptor.MODE_READ_ONLY) },
                entryName = "lzma1.bin",
                destFile = lzma1Dest,
            )
        }
        assertArrayEquals(patternBytes(4, 70 * 1024), lzma1Dest.readBytes())
    }

    @Test
    fun nonSeekablePipeTriggersFallbackOrNotSeekableError() {
        val pipe = ParcelFileDescriptor.createPipe()
        val archiveBytes = copyAsset("single_lzma2.7z").readBytes()
        val writer =
            Thread {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                    out.write(archiveBytes)
                }
            }
        writer.start()

        try {
            pipe[0].use { readEnd ->
                var thrown: SevenZipError? = null
                try {
                    SevenZipArchive.open(readEnd).close()
                } catch (e: SevenZipException) {
                    thrown = e.error
                }
                assertEquals(SevenZipError.NotSeekable, thrown)
            }
        } finally {
            writer.join(2000)
        }

        // With stream fallback, non-seekable fd copies cleanly and extracts.
        val pipe2 = ParcelFileDescriptor.createPipe()
        val dest = File(tempFolder.root, "pipe_fallback.txt")
        runBlocking {
            val err =
                runCatching {
                    ArchiveExtractor.extractEntryToFile(
                        openSourceFd = { pipe2[0] },
                        entryName = "hello.txt",
                        destFile = dest,
                        openStreamFallback = {
                            ParcelFileDescriptor.AutoCloseInputStream(
                                ParcelFileDescriptor.open(
                                    copyAsset("single_lzma2.7z"),
                                    ParcelFileDescriptor.MODE_READ_ONLY,
                                ),
                            )
                        },
                    )
                }
            pipe2[1].close()
            assertTrue(err.isSuccess)
            assertEquals("Hello Omnidroid 7z world!\n", dest.readText())
        }
    }

    @Test
    fun errorTaxonomyAndStaleTempSweep() {
        val stale = File(tempFolder.root, "stale${ArchiveExtractor.TEMP_FILE_SUFFIX}").apply { writeText("old") }
        ArchiveExtractor.sweepStaleTempFiles(tempFolder.root)
        assertFalse(stale.exists())

        assertExtractError("bcj_lzma2.7z", "hello.txt", 64L * 1024 * 1024, SevenZipError.UnsupportedCoder)
        assertExtractError("encrypted_data.7z", "hello.txt", 64L * 1024 * 1024, SevenZipError.Encrypted)
        assertExtractError("bad_crc.7z", "hello.txt", 64L * 1024 * 1024, SevenZipError.CrcMismatch)
        assertExtractError("big_dict.7z", "big_dict.bin", 1024L * 1024, SevenZipError.MemoryLimitExceeded)

        val encHeader = copyAsset("encrypted_header.7z")
        var openErr: SevenZipError? = null
        try {
            ParcelFileDescriptor.open(encHeader, ParcelFileDescriptor.MODE_READ_ONLY).use {
                SevenZipArchive.open(it).close()
            }
        } catch (e: SevenZipException) {
            openErr = e.error
        }
        assertEquals(SevenZipError.Encrypted, openErr)
    }

    @Test
    fun closeWaitsForInFlightExtractAndCancelCleansTempFile() {
        val archiveFile = copyAsset("big_dict.7z")
        val archive =
            ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
                SevenZipArchive.open(it)
            }

        val outFile = File(tempFolder.root, "concurrent_close.bin")
        val outPfd =
            ParcelFileDescriptor.open(
                outFile,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            )
        val started = CountDownLatch(1)
        val extractResult = AtomicInteger(999)
        val worker =
            Thread {
                started.countDown()
                val err = archive.extract(0, outPfd, 8L * 1024 * 1024)
                extractResult.set(err?.code ?: 0)
            }
        worker.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        archive.close()
        worker.join(5000)
        outPfd.close()
        assertTrue(extractResult.get() == 0 || extractResult.get() == SevenZipError.Cancelled.code)

        // Coroutine cancellation removes temp files cleanly
        val dest = File(tempFolder.root, "cancelled_dest.bin")
        runBlocking {
            val job =
                launch(Dispatchers.IO) {
                    try {
                        ArchiveExtractor.extractEntryToFile(
                            openSourceFd = {
                                ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            },
                            entryName = "big_dict.bin",
                            destFile = dest,
                            maxMemoryBytes = 8L * 1024 * 1024,
                        )
                    } catch (_: CancellationException) {
                    }
                }
            delay(1)
            job.cancel()
            job.join()
        }
        val leftoverTmps =
            tempFolder.root
                .listFiles()
                ?.filter { it.name.endsWith(ArchiveExtractor.TEMP_FILE_SUFFIX) }
                .orEmpty()
        assertTrue(leftoverTmps.isEmpty())
    }

    private fun assertExtractError(
        assetName: String,
        entryName: String,
        maxMem: Long,
        expected: SevenZipError,
    ) {
        val file = copyAsset(assetName)
        val dest = File(tempFolder.root, "err_$assetName.out")
        var caught: SevenZipError? = null
        try {
            ArchiveExtractor.extractEntryToFileBlocking(
                openSourceFd = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
                entryName = entryName,
                destFile = dest,
                maxMemoryBytes = maxMem,
            )
        } catch (e: SevenZipException) {
            caught = e.error
        }
        assertEquals(expected, caught)
        assertFalse(dest.exists())
    }

    private fun patternBytes(
        seed: Int,
        length: Int,
    ): ByteArray {
        val buf = ByteArray(length)
        var x = seed.toLong() and 0xFFFFFFFFL
        for (i in 0 until length) {
            x = (1664525L * x + 1013904223L) and 0xFFFFFFFFL
            buf[i] = ((x ushr 24) and 0xFFL).toByte()
        }
        return buf
    }
}
