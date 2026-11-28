package com.omnidroid.lib.saves

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.findByName
import java.util.Locale

/**
 * Maps consoles ([SystemID]) to stable, locale-independent directory names for organizing
 * saves, save states, and state previews inside a user-configured custom save folder.
 */
object SaveConsoleFolders {
    private val SYSTEM_TO_FOLDER: Map<SystemID, String> =
        mapOf(
            SystemID.ATARI2600 to "A26",
            SystemID.ATARI7800 to "A78",
            SystemID.LYNX to "Lynx",
            SystemID.NES to "NES",
            SystemID.SNES to "SNES",
            SystemID.N64 to "N64",
            SystemID.GAMECUBE to "GameCube",
            SystemID.WII to "Wii",
            SystemID.WII_U to "Wii U",
            SystemID.SWITCH to "Switch",
            SystemID.GB to "GB",
            SystemID.GBC to "GBC",
            SystemID.GBA to "GBA",
            SystemID.NDS to "NDS",
            SystemID.NINTENDO_3DS to "3DS",
            SystemID.SMS to "SMS",
            SystemID.GENESIS to "Genesis",
            SystemID.SEGACD to "SCD",
            SystemID.SEGA32X to "32X",
            SystemID.GG to "GG",
            SystemID.DREAMCAST to "Dreamcast",
            SystemID.PSX to "PSX",
            SystemID.PS2 to "PS2",
            SystemID.PS3 to "PS3",
            SystemID.PSP to "PSP",
            SystemID.FBNEO to "FBNeo",
            SystemID.MAME2003PLUS to "MAME2003",
            SystemID.PC_ENGINE to "PCE",
            SystemID.NGP to "NGP",
            SystemID.NGC to "NGC",
            SystemID.WS to "WS",
            SystemID.WSC to "WSC",
            SystemID.DOS to "DOS",
            SystemID.ATARI5200 to "Atari 5200",
            SystemID.ATARI800 to "Atari 8-bit",
            SystemID.SATURN to "Sega Saturn",
            SystemID.THREE_DO to "3DO",
            SystemID.COLECOVISION to "ColecoVision",
            SystemID.C64 to "Commodore 64",
            SystemID.MSX to "MSX",
            SystemID.MSX2 to "MSX2",
            SystemID.APPLE2 to "Apple II",
            SystemID.JAGUAR to "Atari Jaguar",
        )

    private val DBNAME_TO_FOLDER: Map<String, String> =
        SYSTEM_TO_FOLDER.entries.associate { (systemId, folder) -> systemId.dbname to folder }

    private val PS3_ARCHIVE_REGEX = Regex("""ps3-[A-Z]{4}[0-9]{5}\.srm""", RegexOption.IGNORE_CASE)
    private val SLOT_FILE_REGEX = Regex("""^(.+)\.slot[1-9][0-9]*$""")
    private val PREVIEW_FILE_REGEX = Regex("""^(.+)\.slot[1-9][0-9]*\.jpg$""")

    val ALL_FOLDERS: Set<String> = SYSTEM_TO_FOLDER.values.toSet()

    fun forSystem(systemId: SystemID): String = SYSTEM_TO_FOLDER[systemId] ?: sanitize(systemId.dbname)

    fun forSystemId(systemId: String): String = DBNAME_TO_FOLDER[systemId] ?: sanitize(systemId)

    fun isConsoleScoped(relativePath: String): Boolean = consoleFolderOf(relativePath) != null

    fun consoleFolderOf(relativePath: String): String? {
        val parts = relativePath.trimStart('/').split('/')
        return when {
            parts.size >= 3 && parts[0] == "saves" && parts[1] in ALL_FOLDERS -> parts[1]
            parts.size >= 4 &&
                (parts[0] == "states" || parts[0] == "state-previews") &&
                parts[1] in ALL_FOLDERS &&
                findByName(parts[2]) != null -> parts[1]
            else -> null
        }
    }

    fun pathPriority(relativePath: String): Int {
        val clean = relativePath.trimStart('/')
        return when {
            isConsoleScoped(clean) -> 0
            clean.startsWith("saves/") ||
                clean.startsWith("states/") ||
                clean.startsWith("state-previews/") -> 1
            else -> 2
        }
    }

    fun toConsoleScopedPath(
        relativePath: String,
        gamesByStem: Map<String, Set<String>>,
        gamesByFileName: Map<String, Set<String>>,
    ): String {
        val clean = relativePath.trimStart('/')
        if (isConsoleScoped(clean)) return clean

        val parts = clean.split('/')
        return when {
            parts.size == 2 && parts[0] == "saves" -> {
                val fileName = parts[1]
                val console = resolveSaveConsole(fileName, gamesByStem)
                if (console != null) "saves/$console/$fileName" else clean
            }
            parts.size == 1 && isSaveExtension(clean) -> {
                val console = resolveSaveConsole(clean, gamesByStem)
                if (console != null) "saves/$console/$clean" else clean
            }
            parts.size == 3 && parts[0] == "states" && findByName(parts[1]) != null -> {
                val coreName = parts[1]
                val fileName = parts[2]
                val base = fileName.removeSuffix(".metadata")
                val romFileName =
                    when {
                        base.endsWith(".state") -> base.removeSuffix(".state")
                        else -> SLOT_FILE_REGEX.matchEntire(base)?.groupValues?.get(1)
                    }
                val console = resolveStateConsole(coreName, romFileName, gamesByFileName)
                if (console != null) "states/$console/$coreName/$fileName" else clean
            }
            parts.size == 3 && parts[0] == "state-previews" && findByName(parts[1]) != null -> {
                val coreName = parts[1]
                val fileName = parts[2]
                val romFileName = PREVIEW_FILE_REGEX.matchEntire(fileName)?.groupValues?.get(1)
                val console = resolveStateConsole(coreName, romFileName, gamesByFileName)
                if (console != null) "state-previews/$console/$coreName/$fileName" else clean
            }
            else -> clean
        }
    }

    fun toInternalPath(relativePath: String): String {
        val clean = relativePath.trimStart('/')
        val parts = clean.split('/')
        return when {
            parts.size >= 3 && parts[0] == "saves" && parts[1] in ALL_FOLDERS ->
                "saves/" + parts.drop(2).joinToString("/")
            parts.size >= 4 &&
                parts[0] == "states" &&
                parts[1] in ALL_FOLDERS &&
                findByName(parts[2]) != null ->
                "states/" + parts.drop(2).joinToString("/")
            parts.size >= 4 &&
                parts[0] == "state-previews" &&
                parts[1] in ALL_FOLDERS &&
                findByName(parts[2]) != null ->
                "state-previews/" + parts.drop(2).joinToString("/")
            else -> clean
        }
    }

    fun isUnambiguousStateOwner(
        coreName: String,
        romFileName: String,
        systemId: String,
        matchingSystemIdsInDb: Set<String>? = null,
    ): Boolean {
        val coreId = findByName(coreName) ?: return false
        val coreSystems = GameSystem.findSystemForCore(coreId)
        if (coreSystems.size == 1 && coreSystems.single().id.dbname == systemId) {
            return true
        }
        val ext = romFileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isNotEmpty()) {
            val byExt = coreSystems.filter { ext in it.uniqueExtensions }
            if (byExt.size == 1 && byExt.single().id.dbname == systemId) {
                return true
            }
        }
        if (matchingSystemIdsInDb != null) {
            val coreSystemDbNames = coreSystems.map { it.id.dbname }.toSet()
            val matching = matchingSystemIdsInDb.intersect(coreSystemDbNames)
            return matching.size == 1 && systemId in matching
        }
        return false
    }

    private fun resolveSaveConsole(
        fileName: String,
        gamesByStem: Map<String, Set<String>>,
    ): String? {
        if (PS3_ARCHIVE_REGEX.matches(fileName)) {
            return forSystem(SystemID.PS3)
        }
        val stem = fileName.substringBeforeLast('.')
        val matchingSystems = gamesByStem[stem].orEmpty()
        return if (matchingSystems.size == 1) {
            forSystemId(matchingSystems.single())
        } else {
            null
        }
    }

    private fun resolveStateConsole(
        coreName: String,
        romFileName: String?,
        gamesByFileName: Map<String, Set<String>>,
    ): String? {
        val coreId: CoreID = findByName(coreName) ?: return null
        val coreSystems = GameSystem.findSystemForCore(coreId)
        val coreSystemDbNames = coreSystems.map { it.id.dbname }.toSet()

        if (romFileName != null) {
            val dbMatches = gamesByFileName[romFileName].orEmpty().intersect(coreSystemDbNames)
            if (dbMatches.size == 1) {
                return forSystemId(dbMatches.single())
            }
        }

        if (coreSystems.size == 1) {
            return forSystem(coreSystems.single().id)
        }

        if (romFileName != null) {
            val ext = romFileName.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext.isNotEmpty()) {
                val byExt = coreSystems.filter { ext in it.uniqueExtensions }
                if (byExt.size == 1) {
                    return forSystem(byExt.single().id)
                }
            }
        }
        return null
    }

    private fun isSaveExtension(fileName: String): Boolean =
        fileName.endsWith(".srm", ignoreCase = true) || fileName.endsWith(".sav", ignoreCase = true)

    private fun sanitize(value: String): String =
        value.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "other" }
}
