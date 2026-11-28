package com.omnidroid.app.shared.game

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

object GameProcessLock {
    private const val LOCK_FILE_NAME = "game_process.lock"

    @Volatile
    private var channel: FileChannel? = null

    @Volatile
    private var lock: FileLock? = null

    fun acquire(appContext: Context) {
        if (lock != null) return
        val lockFile = File(appContext.filesDir, LOCK_FILE_NAME)
        channel = RandomAccessFile(lockFile, "rw").channel
        lock = channel?.tryLock()
    }

    fun isHeldByAnotherProcess(appContext: Context): Boolean {
        if (lock?.isValid == true) return false
        val lockFile = File(appContext.filesDir, LOCK_FILE_NAME)
        RandomAccessFile(lockFile, "rw").channel.use { ch ->
            val testLock =
                try {
                    ch.tryLock()
                } catch (_: OverlappingFileLockException) {
                    return true
                }
            return if (testLock != null) {
                testLock.release()
                false
            } else {
                true
            }
        }
    }

    /** A launch from the library replaces any orphaned emulator, even if its UI is hung. */
    suspend fun stopPreviousGame(appContext: Context): Boolean =
        withContext(Dispatchers.IO) {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processName = "${appContext.applicationInfo.processName}:game"
            withTimeoutOrNull(5_000L) {
                do {
                    val previous =
                        manager.runningAppProcesses.orEmpty().filter {
                            isGameProcess(it.processName, it.uid, it.pid, processName, Process.myUid(), Process.myPid())
                        }
                    previous.forEach { Process.killProcess(it.pid) }
                    val stopped = previous.isEmpty() && !isHeldByAnotherProcess(appContext)
                    if (!stopped) delay(50L)
                } while (!stopped)
                true
            } ?: false
        }

    internal fun isGameProcess(
        name: String,
        uid: Int,
        pid: Int,
        gameProcessName: String,
        ownUid: Int,
        ownPid: Int,
    ): Boolean = name == gameProcessName && uid == ownUid && pid != ownPid
}
