package com.omnidroid.lib.saves

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * PS2 memory cards live in the external saves folder, so they can be imported, backed up and synced
 * like other battery saves. The armsx2 core has no option for its memory card folder and keeps the
 * cards under the private system folder by default, so its ini is pointed here before each launch.
 */
object Ps2MemoryCards {
    /** Card folder, relative to the saves directory. */
    const val RELATIVE_DIR = "pcsx2/memcards"

    private const val LEGACY_DIR = "pcsx2/memcards"
    private const val CORE_INI = "pcsx2/inis/armsx2-libretro.ini"
    private const val MIGRATED_MARKER = ".moved-to-saves"

    fun isCardPath(relativePath: String): Boolean = relativePath.replace('\\', '/').startsWith("$RELATIVE_DIR/")

    /** Where a `.ps2` file found anywhere in an import goes, relative to the saves directory. */
    fun importPath(path: String): String? {
        val name = path.replace('\\', '/').substringAfterLast('/')
        return if (name.endsWith(".ps2", ignoreCase = true) && name.length > 4) "$RELATIVE_DIR/$name" else null
    }

    /**
     * Run before the core starts. Until the core has written its ini once there is nothing to point,
     * and the cards it creates on that first run are carried over on the next launch.
     */
    fun prepare(
        systemDirectory: File,
        savesDirectory: File,
    ) {
        val ini = File(systemDirectory, CORE_INI)
        if (!ini.isFile) return
        val target = File(savesDirectory, RELATIVE_DIR).apply { mkdirs() }
        migrateOnce(File(systemDirectory, LEGACY_DIR), target)
        pointCoreAt(ini, target)
    }

    /**
     * Copies the cards from the old private folder once, keeping the originals. A card that already
     * exists here (from an import) stays in place, and the old one is kept beside it under a
     * different name rather than being hidden.
     */
    internal fun migrateOnce(
        legacy: File,
        target: File,
    ) {
        // Kept beside the old cards, where backups and sync don't see it.
        val marker = File(legacy, MIGRATED_MARKER)
        if (marker.exists()) return
        val oldCards = legacy.listFiles().orEmpty().filter { it.isFile && it.extension.equals("ps2", true) }
        oldCards.forEach { card ->
            val existing = File(target, card.name)
            val destination =
                when {
                    !existing.exists() -> existing
                    sameContent(existing, card) -> return@forEach
                    else -> File(target, "${card.nameWithoutExtension} (old location).${card.extension}")
                }
            if (destination.exists()) return@forEach
            val temporary = File(target, ".${card.name}.tmp")
            card.copyTo(temporary, overwrite = true)
            if (!sameContent(temporary, card) || !temporary.renameTo(destination)) {
                temporary.delete()
                throw IOException("Could not copy memory card ${card.name}")
            }
        }
        legacy.mkdirs()
        marker.createNewFile()
    }

    /** Sets `[Folders] MemoryCards` to [target], keeping the rest of the core's ini as it is. */
    internal fun pointCoreAt(
        ini: File,
        target: File,
    ) {
        val wanted = "MemoryCards = ${target.absolutePath}"
        val lines = ini.readLines().toMutableList()
        val section = lines.indexOfFirst { it.trim().equals("[Folders]", ignoreCase = true) }
        if (section < 0) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += listOf("[Folders]", wanted)
        } else {
            val end = (section + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
            val key = (section + 1 until end).firstOrNull { lines[it].substringBefore('=').trim() == "MemoryCards" }
            when {
                key == null -> lines.add(section + 1, wanted)
                lines[key] == wanted -> return
                else -> lines[key] = wanted
            }
        }
        val temporary = File(ini.parentFile, ".${ini.name}.tmp")
        temporary.writeText(lines.joinToString("\n", postfix = "\n"))
        check(temporary.renameTo(ini)) { "Could not update ${ini.name}" }
    }

    private fun sameContent(
        a: File,
        b: File,
    ): Boolean = a.length() == b.length() && digest(a).contentEquals(digest(b))

    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
