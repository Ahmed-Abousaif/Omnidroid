package com.omnidroid.lib.saves

import android.content.Context
import android.net.Uri
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.scan.Ps3Content
import java.io.File

/** The native engine streams multi-hundred-MB snapshots directly to disk. */
class Ps3StateSlots(private val directory: File) {
    fun file(index: Int): File? {
        require(index >= 0)
        return listOf(".zst", ".gz", "").map { File(directory, "slot$index.SAVESTAT$it") }
            .firstOrNull { it.isFile && it.length() > 0 }
    }

    fun version(index: Int): String? = file(index)?.let {
        "${it.name}:${it.length()}:${java.nio.file.Files.getLastModifiedTime(it.toPath())}"
    }

    /** Capture into a private staging slot; replace the user's slot only after success. */
    fun commitCapture(index: Int) {
        require(index >= 0 && index != CAPTURE_SLOT)
        val captured = requireNotNull(file(CAPTURE_SLOT)) { "PS3 did not produce a state" }
        val suffix = captured.name.removePrefix("slot$CAPTURE_SLOT")
        val destination = File(directory, "slot$index$suffix")
        try {
            java.nio.file.Files.move(captured.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(captured.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        listOf(".zst", ".gz", "").map { File(directory, "slot$index.SAVESTAT$it") }
            .filter { it != destination }.forEach { it.delete() }
        for (extension in listOf("history", "thumb")) {
            val source = File(directory, "slot$CAPTURE_SLOT.$extension")
            if (source.isFile) runCatching {
                java.nio.file.Files.move(source.toPath(), File(directory, "slot$index.$extension").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun indices(): List<Int> {
        val stored = directory.listFiles().orEmpty().mapNotNull {
            PATTERN.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { index -> index < CAPTURE_SLOT && it.isFile && it.length() > 0 }
        }
        return ((0 until StatesManager.DEFAULT_STATES) + stored).distinct().sorted()
    }

    fun nextIndex(): Int = (indices().last() + 1).also { require(it < CAPTURE_SLOT) }

    fun info(index: Int) = file(index)?.let { SaveInfo(true, it.lastModified()) } ?: SaveInfo(false, 0)

    companion object {
        const val QUICK_SLOT = Int.MAX_VALUE
        const val CAPTURE_SLOT = Int.MAX_VALUE - 1
        private val PATTERN = Regex("slot([0-9]+)\\.SAVESTAT(?:\\.zst|\\.gz)?")

        fun forTitle(context: Context, titleId: String): Ps3StateSlots {
            require(titleId.matches(Regex("[A-Z]{4}[0-9]{5}")))
            return Ps3StateSlots(File(context.filesDir, "ps3/config/savestates/$titleId/armsx3_slots"))
        }

        fun forGame(context: Context, game: Game): Ps3StateSlots {
            val titleId = requireNotNull(Ps3Content.read(context, Uri.parse(game.fileUri))) { "Cannot read PS3 title identity" }.titleId
            return forTitle(context, titleId)
        }
    }
}
