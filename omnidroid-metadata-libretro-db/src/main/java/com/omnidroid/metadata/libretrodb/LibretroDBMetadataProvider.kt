package com.omnidroid.metadata.libretrodb

import com.omnidroid.common.kotlin.filterNullable
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.storage.StorageFile
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import com.omnidroid.metadata.libretrodb.db.LibretroDatabase
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {
    companion object {
        // Disallowed filesystem, URI-special, control, and path traversal characters
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|\"#'%^~;\\[\\]{}@+=!\$]")
        private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\u0000-\\u001F\\u007F-\\u009F]")
        private val PATH_TRAVERSAL = Regex("\\.{2,}")
        private const val MAX_TITLE_LENGTH = 200
        private const val BASE_THUMBNAIL_URL = "http://thumbnails.libretro.com"
        private const val IMAGE_TYPE = "Named_Boxarts"
    }

    private val sortedSystemIds: List<String> by lazy {
        SystemID.values()
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        val metadata =
            runCatching {
                // Folder name is the intentional disambiguator for shared extensions
                // (e.g. PS2 ISOs also match PLAYSTATION magic and would otherwise become PSX).
                val pathSystemMetadata = findByPathAndSupportedExtension(storageFile)

                findByCRC(storageFile, db)?.takeUnless { conflictsWithPath(it, pathSystemMetadata) }
                    ?: findBySerial(storageFile, db)?.takeUnless {
                        conflictsWithPath(it, pathSystemMetadata)
                    }
                    ?: findByFilename(db, storageFile)?.takeUnless {
                        conflictsWithPath(it, pathSystemMetadata)
                    }
                    ?: findByPathAndFilename(db, storageFile)?.takeUnless {
                        conflictsWithPath(it, pathSystemMetadata)
                    }
                    ?: findByUniqueExtension(storageFile)
                    ?: pathSystemMetadata
                    ?: findByKnownSystem(storageFile)
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun conflictsWithPath(
        metadata: GameMetadata,
        pathMetadata: GameMetadata?,
    ): Boolean {
        return pathMetadata != null && pathMetadata.system != metadata.system
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata {
        val system = GameSystem.findById(rom.system!!)
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = rom.system,
            developer = rom.developer,
        )
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByFilename }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByPathAndFilename }
            .filterNullable { parentContainsSystem(file.path, extractGameSystem(it).id.dbname) }
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        val system =
            sortedSystemIds
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = computeCoverUrl(it, file.extensionlessName),
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        return parent?.lowercase(Locale.getDefault())?.contains(dbname) == true
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        if (file.systemID == null) return null
        val system = GameSystem.findById(file.systemID!!.dbname)

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = computeCoverUrl(system, file.extensionlessName),
            system = file.systemID!!.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        val result =
            system?.let {
                GameMetadata(
                    name = file.extensionlessName,
                    romName = file.name,
                    thumbnail = computeCoverUrl(it, file.extensionlessName),
                    system = it.id.dbname,
                    developer = null,
                )
            }

        return result
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun computeCoverUrl(
        system: GameSystem?,
        name: String?,
    ): String? {
        if (system == null || name.isNullOrBlank()) {
            return null
        }

        var systemName = system.libretroFullName
        if (system.id == SystemID.MAME2003PLUS) {
            systemName = "MAME"
        }

        // Validate systemName format (alphanumeric, spaces, and hyphens only)
        if (!systemName.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) {
            return null
        }

        // Clean & sanitize title to eliminate path traversal, control codes, and illegal URI chars
        val cleanName = name
            .replace(CONTROL_CHARS, "")
            .replace(PATH_TRAVERSAL, "_")
            .replace(THUMB_REPLACE, "_")
            .trim()
            .trim('.', '_', ' ')

        if (cleanName.isBlank() || cleanName.length > MAX_TITLE_LENGTH) {
            return null
        }

        return "$BASE_THUMBNAIL_URL/$systemName/$IMAGE_TYPE/$cleanName.png"
    }
}
