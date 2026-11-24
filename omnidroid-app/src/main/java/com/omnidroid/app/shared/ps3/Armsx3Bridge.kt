package com.omnidroid.app.shared.ps3

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.Surface
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.library.CoreID
import timber.log.Timber
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object Armsx3Bridge {
    private val operations = ReentrantLock()
    val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "PS3-session") }

    @Volatile private var initialized = false

    @Volatile private var gameActive = false

    @Volatile var stateOperationActive = false
        private set
    private var appContext: Context? = null
    private var sessionDataLock: Pair<java.io.RandomAccessFile, java.nio.channels.FileLock>? = null

    fun baseDir(context: Context) = File(context.filesDir, "ps3").apply { mkdirs() }

    private fun acquireDataLock(context: Context): Pair<java.io.RandomAccessFile, java.nio.channels.FileLock> {
        val file = java.io.RandomAccessFile(File(baseDir(context), "session.lock"), "rw")
        try {
            val lock =
                file.channel.tryLock() ?: error("PS3 data is in use. Finish the running game or installation first.")
            return file to lock
        } catch (failure: Exception) {
            file.close()
            throw failure
        }
    }

    fun <T> withDataAccess(
        context: Context,
        block: () -> T,
    ): T =
        operations.withLock {
            val (file, lock) = acquireDataLock(context)
            try {
                block()
            } finally {
                lock.release()
                file.close()
            }
        }

    fun initialize(
        context: Context,
        coreFile: File? = null,
    ) = operations.withLock {
        if (initialized) return@withLock
        check(Build.VERSION.SDK_INT >= 33 && "arm64-v8a" in Build.SUPPORTED_ABIS) {
            "PS3 requires Android 13 or newer and an ARM64 device."
        }
        check(context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
            "PS3 requires a Vulkan-capable device."
        }
        val library = coreFile ?: CoreLibraryLocator.find(context, CoreID.ARMSX3)
        check(library?.isFile == true && library.length() > 0) { "The ARMSX3 engine is not installed." }
        // Check before loading: emulator static initializers may use ARMv8.2 instructions.
        val cpuFeatures =
            runCatching { File("/proc/cpuinfo").readLines() }
                .getOrElse { error("Cannot verify the CPU features required for PS3 emulation.") }
                .filter { it.substringBefore(':').trim() == "Features" }
                .map {
                    it
                        .substringAfter(':')
                        .trim()
                        .split(Regex("\\s+"))
                        .toSet()
                }
        val required = setOf("atomics", "asimdrdm", "fphp", "asimdhp", "asimddp")
        check(cpuFeatures.isNotEmpty() && cpuFeatures.all { it.containsAll(required) }) {
            "PS3 requires an ARM64 CPU with LSE, RDM, FP16 and dot-product instructions."
        }
        System.load(library!!.absolutePath)
        Armsx3Native.cpuError()?.let { error(it) }
        appContext = context.applicationContext
        ContentUri.attach(context)
        context.contentResolver.persistedUriPermissions
            .filter {
                it.isReadPermission &&
                    android.provider.DocumentsContract.isTreeUri(it.uri)
            }.forEach { ContentUri.keyForTree(it.uri) }
        Ps3HostCallbacks.attach(context)
        Armsx3Native.open()
        val root = baseDir(context)
        check(
            Armsx3Native.initialize(
                root.absolutePath + "/",
                File(context.cacheDir, "ps3").apply { mkdirs() }.absolutePath,
                Ps3HostCallbacks::class.java,
                ContentUri::class.java,
            ),
        ) {
            "ARMSX3 initialization failed."
        }
        // The interpreter builds thousands of Vulkan variants on its first use.
        // Start with the asynchronous recompiler on Android; persist this before boot.
        check(Armsx3Native.setting("Video@@Shader Mode", "\"Async Recompiler (multi-threaded)\""))
        // State capture must be enabled before the SPU threads are created.
        check(Armsx3Native.setting("Savestate@@Compatible Savestate Mode", "true"))
        check(Armsx3Native.setting("Savestate@@Suspend Emulation Savestate Mode", "false"))
        initialized = true
        Timber.i("PS3 engine initialized: %s", library.absolutePath)
    }

    fun boot(path: String): Int =
        operations.withLock {
            check(initialized) { "PS3 engine is not initialized." }
            check(!gameActive) { "A PS3 session is already active." }
            val uri = Uri.parse(path)
            if (uri.scheme == "content") ContentUri.refresh(requireNotNull(appContext))
            val target =
                when (uri.scheme) {
                    "file" -> requireNotNull(uri.path)
                    "content" ->
                        ContentUri.devicePathForDocument(uri)
                            ?: error(
                                "PS3 cannot access this document. Add its containing folder to your library again.",
                            )
                    else -> path
                }
            val bootTarget = if (target.endsWith("/PARAM.SFO", true)) target.substringBeforeLast('/') else target
            val lock = acquireDataLock(requireNotNull(appContext))
            try {
                val result = Armsx3Native.boot(bootTarget)
                gameActive = result == 0
                if (gameActive) {
                    sessionDataLock = lock
                } else {
                    lock.second.release()
                    lock.first.close()
                }
                if (gameActive) 0 else result
            } catch (failure: Exception) {
                lock.second.release()
                lock.first.close()
                throw failure
            }
        }

    fun stop() =
        operations.withLock {
            if (initialized && gameActive) {
                Armsx3Native.control(2)
                // Kill requests a detached join. Keep ownership until the VM has really stopped.
                val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
                while (Armsx3Native.state() != 0) {
                    check(android.os.SystemClock.elapsedRealtime() < deadline) {
                        "PS3 is still shutting down. Wait before starting another session."
                    }
                    Thread.sleep(20)
                }
                gameActive = false
                sessionDataLock?.let { (file, lock) ->
                    lock.release()
                    file.close()
                }
                sessionDataLock = null
            }
        }

    fun pause() {
        if (initialized && gameActive && !stateOperationActive) Armsx3Native.control(0)
    }

    fun resume() {
        if (initialized && gameActive && !stateOperationActive) Armsx3Native.control(1)
    }

    fun displayRate(hz: Float) {
        if (initialized) Armsx3Native.displayRate(hz)
    }

    fun performanceStats() = Ps3PerformanceStats(Armsx3Native.performanceStats())

    /** Only instrumentation can change the always-on production performance policy. */
    fun performanceOptionsForTesting(options: Int) {
        check(
            requireNotNull(
                appContext,
            ).applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        Armsx3Native.performanceOptions(options)
    }

    fun state() = if (initialized) Armsx3Native.state() else 0

    fun settings(path: String) = Armsx3Native.settings(path)

    fun stateSlot(
        save: Boolean,
        index: Int,
        slots: com.omnidroid.lib.saves.Ps3StateSlots,
    ): Boolean =
        operations.withLock {
            check(gameActive && state() in setOf(3, 4)) { "Wait until the PS3 game is running." }
            require(index >= 0)
            if (!save && slots.file(index) == null) return@withLock false
            val nativeIndex = if (save) com.omnidroid.lib.saves.Ps3StateSlots.CAPTURE_SLOT else index
            val previous = slots.version(nativeIndex)
            stateOperationActive = true
            try {
                if (!Armsx3Native.stateSlot(save, nativeIndex)) return@withLock false
                val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
                var transitioned = false
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    check(!Armsx3Native.stateOperationFailed()) {
                        "The PS3 engine could not capture a safe snapshot. Your game is still running."
                    }
                    val current = state()
                    // The native continuous-mode flag stays set after a successful restart;
                    // observe an actual VM transition before reporting a completed load.
                    if (current !in setOf(3, 4)) transitioned = true
                    val written = slots.version(nativeIndex)
                    if (transitioned && current in setOf(3, 4) && (!save || written != previous && written != null)) {
                        if (save) slots.commitCapture(index)
                        return@withLock true
                    }
                    Thread.sleep(10)
                }
                error("PS3 ${if (save) "save" else "load"} did not complete in time.")
            } finally {
                stateOperationActive = false
            }
        }

    fun surface(
        surface: Surface?,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (initialized) Armsx3Native.surface(surface, width, height)
    }

    fun pad(
        port: Int,
        d1: Int,
        d2: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
    ) {
        if (initialized && gameActive) Armsx3Native.pad(port, d1, d2, lx, ly, rx, ry)
    }

    fun setting(
        path: String,
        value: String,
    ) {
        if (initialized) check(Armsx3Native.setting(path, value)) { "Unsupported PS3 setting: $path" }
    }

    fun probe(
        context: Context,
        uri: Uri,
    ): com.omnidroid.lib.library.scan.Ps3Content =
        operations.withLock {
            check(initialized && !gameActive) { "Stop PS3 emulation before reading disc metadata." }
            ContentUri.refresh(context)
            var path =
                when (uri.scheme) {
                    "file" -> requireNotNull(uri.path)
                    "content" ->
                        ContentUri.devicePathForDocument(uri)
                            ?: error("Add the game's containing folder to your library again.")
                    else -> uri.toString()
                }
            if (path.endsWith("/PARAM.SFO", true)) path = path.substringBeforeLast('/')
            val icon =
                File(
                    baseDir(context),
                    "covers/${uri.toString().hashCode().toUInt()}.png",
                ).apply { parentFile!!.mkdirs() }
            val json = org.json.JSONObject(Armsx3Native.probe(path, icon.absolutePath))
            val id = json.optString("titleId")
            check(
                id.matches(Regex("[A-Z]{4}[0-9]{5}")),
            ) { "Could not read the PS3 disc. Check its files and disc key." }
            val info =
                com.omnidroid.lib.library.scan
                    .Ps3Content(id, json.optString("title", id), "DG")
            com.omnidroid.lib.library.scan.Ps3Content
                .cache(context, uri, info)
            info
        }

    fun install(
        context: Context,
        uri: Uri,
        operation: String,
        progress: (String) -> Unit,
    ): String =
        withDataAccess(context) {
            initialize(context)
            check(!gameActive) { "Stop PS3 emulation before installing system files or content." }
            val name =
                context.contentResolver
                    .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
            val extension = name.substringAfterLast('.', "").lowercase()
            if (operation == "key" && extension in setOf("key", "dkey")) {
                val destination = File(baseDir(context), "config/data/redump").apply { mkdirs() }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(destination, File(name).name).outputStream().use { input.copyTo(it) }
                } ?: error("Cannot open the selected key.")
                return@withDataAccess "Disc key imported"
            }
            if (operation == "key" && extension == "rap") {
                val bytes =
                    context.contentResolver.openInputStream(uri)?.use { it.readNBytes(17) }
                        ?: error("Cannot open the selected license.")
                require(bytes.size == 16) { "A RAP license must contain exactly 16 bytes." }
                val destination = File(baseDir(context), "config/dev_hdd0/home/00000001/exdata").apply { mkdirs() }
                File(destination, File(name).name).writeBytes(bytes)
                return@withDataAccess "Game license imported"
            }
            val kind =
                when (operation) {
                    "firmware" -> {
                        require(extension == "pup") { "Select a .PUP firmware file." }
                        0
                    }
                    "package" -> {
                        require(extension == "pkg") { "Select a .PKG package file." }
                        1
                    }
                    "key" -> error("Select a .RAP, .KEY or .DKEY file.")
                    else -> error("Unknown PS3 installation action.")
                }
            val completion = CompletableFuture<String>()
            val id = Ps3HostCallbacks.register(progress, completion)
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    if (!Armsx3Native.install(kind, descriptor.fd, id)) {
                        if (completion.isDone) completion.get()
                        error("ARMSX3 could not install this file.")
                    }
                } ?: error("Cannot open the selected file.")
                completion.get()
            } finally {
                Ps3HostCallbacks.remove(id)
            }
        }

    fun bootError(code: Int): String =
        when (code) {
            1 -> "The PS3 game path is invalid."
            2 -> "The previous PS3 session is still shutting down."
            3 -> "No PS3 game was found at this location."
            4 -> "This PS3 content is incompatible with the installed engine."
            5 -> "The PS3 game could not start. Check firmware, folder permission and game files."
            else -> "PS3 could not start this game (error $code)."
        }
}
