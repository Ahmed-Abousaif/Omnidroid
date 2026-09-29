package com.omnidroid.metadata.libretrodb

import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.lib.library.scan.MetadataKeyType
import com.omnidroid.lib.library.scan.ScanKeys
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import com.omnidroid.metadata.libretrodb.db.LibretroDatabase
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom
import java.net.URLEncoder

class LibretroDBMetadataProvider(
    private val ovgdbManager: LibretroDBManager,
) : GameMetadataProvider {
    override val databaseVersion: Int = LibretroDatabase.VERSION

    override suspend fun sliceStamps(): Map<String, String> {
        val stamps = HashMap<String, String>()
        ovgdbManager.dbInstance.sliceDao().all().forEach { slice ->
            slice.systems.split(',').forEach { system ->
                stamps[system] = slice.sha256
            }
        }
        return stamps
    }

    override suspend fun describe(
        systemId: String,
        fallbackTitle: String,
        key: MetadataKey?,
        fileSize: Long,
    ): GameMetadata {
        val row =
            key?.let { find(systemId, it, fileSize) }
                ?: findByLooseName(systemId, fallbackTitle)
                ?: key?.takeIf {
                    it.type == MetadataKeyType.ROM_BASE || it.type == MetadataKeyType.ROM_NAME
                }?.let { findByLooseName(systemId, it.value) }
        val title = row?.name?.takeIf(::isLibraryTitle) ?: fallbackTitle
        val coverTitle = row?.rawName ?: row?.name ?: title
        return GameMetadata(
            name = title,
            system = systemId,
            romName = null,
            developer = null,
            thumbnail = computeCoverUrl(GameSystem.findById(systemId), coverTitle),
        )
    }

    override suspend fun resolveArcade(
        setName: String,
        memberCrcs: List<String>,
    ): String? {
        val setHash = ScanKeys.romHash(setName) ?: return null
        val set = ovgdbManager.detectInstance.arcadeSets().find(setHash) ?: return null
        val systems = set.systems.split(',')
        if (systems.size == 1) return systems.first()
        val hits = HashMap<String, Int>()
        memberCrcs.distinct().chunked(400).forEach { chunk ->
            ovgdbManager.detectInstance.arcadeMarkers().matching(setHash, chunk).forEach { marker ->
                hits[marker.system] = (hits[marker.system] ?: 0) + 1
            }
        }
        val mameHits = hits[SystemID.MAME2003PLUS.dbname] ?: 0
        val fbneoHits = hits[SystemID.FBNEO.dbname] ?: 0
        return if (mameHits > fbneoHits) SystemID.MAME2003PLUS.dbname else SystemID.FBNEO.dbname
    }

    override suspend fun findCartridge(crc: String): GameMetadata? {
        val cart = ovgdbManager.detectInstance.binCarts().find(crc) ?: return null
        return GameMetadata(
            name = null,
            system = cart.system,
            romName = null,
            developer = null,
            thumbnail = null,
        )
    }

    private suspend fun findByLooseName(
        systemId: String,
        fallbackTitle: String,
    ): LibretroRom? {
        val variants = titleVariants(fallbackTitle)
        if (variants.isEmpty()) return null
        val dao = ovgdbManager.dbInstance.gameDao()
        for (normalized in variants) {
            dao.findByNormalizedName(systemId, normalized)?.takeIf { isLibraryTitle(it.name) }?.let {
                return it
            }
            dao.findByNormalizedNamePrefix(systemId, "$normalized ")?.takeIf { isLibraryTitle(it.name) }?.let {
                return it
            }
            dao.findByNormalizedNamePrefix(systemId, "$normalized (")?.takeIf { isLibraryTitle(it.name) }?.let {
                return it
            }
        }
        return null
    }

    private fun titleVariants(value: String): List<String> {
        val base = normalizeTitle(value) ?: return emptyList()
        val variants = linkedSetOf(base)
        if (base.startsWith("the ")) {
            variants += base.removePrefix("the ").trim()
        } else {
            variants += "the $base"
        }
        if (base.endsWith(" the")) {
            variants += "the " + base.removeSuffix(" the").trim()
            variants += base.removeSuffix(" the").trim()
        }
        val withoutRegion = base.substringBefore('(').trim()
        if (withoutRegion.isNotEmpty() && withoutRegion != base) {
            variants += withoutRegion
            if (withoutRegion.startsWith("the ")) {
                variants += withoutRegion.removePrefix("the ").trim()
            } else {
                variants += "the $withoutRegion"
            }
        }
        return variants.filter { it.isNotBlank() }
    }

    private fun normalizeTitle(value: String): String? {
        val cleaned =
            value
                .lowercase()
                .replace('_', ' ')
                .replace(",", "")
                .replace(Regex("\\s+"), " ")
                .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun isLibraryTitle(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.length > 160 && !name.contains('(')) return false
        val lower = name.lowercase()
        return " is " !in lower && " are " !in lower && " was " !in lower && " were " !in lower
    }

    private suspend fun find(
        systemId: String,
        key: MetadataKey,
        fileSize: Long,
    ): LibretroRom? {
        val dao = ovgdbManager.dbInstance.gameDao()
        return when (key.type) {
            MetadataKeyType.CRC -> dao.findBySystemAndCrc(systemId, key.value)
            MetadataKeyType.SERIAL -> dao.findBySystemAndSerial(systemId, key.value, fileSize)
            MetadataKeyType.CODE -> dao.findBySystemAndCode(systemId, key.value, fileSize)
            MetadataKeyType.ROM_BASE, MetadataKeyType.ROM_NAME -> {
                val hash = ScanKeys.romHash(key.value) ?: return null
                dao.findBySystemAndRomHash(systemId, hash)
            }
        }
    }

    private fun computeCoverUrl(
        system: GameSystem?,
        name: String?,
    ): String? {
        if (system == null || name.isNullOrBlank()) return null
        var systemName = system.libretroFullName
        if (system.id == SystemID.MAME2003PLUS) {
            systemName = "MAME"
        }
        if (!systemName.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) return null
        val cleanName =
            name
                .replace(CONTROL_CHARS, "")
                .replace(PATH_TRAVERSAL, "_")
                .replace(THUMB_REPLACE, "_")
                .trim()
                .trim('.', '_', ' ')
        if (cleanName.isBlank() || cleanName.length > MAX_TITLE_LENGTH) return null
        return "$BASE_THUMBNAIL_URL/${encode(systemName)}/$IMAGE_TYPE/${encode(cleanName)}.png"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|\"#%^~;\\[\\]{}@+=!\$]")
        private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\u0000-\\u001F\\u007F-\\u009F]")
        private val PATH_TRAVERSAL = Regex("\\.{2,}")
        private const val MAX_TITLE_LENGTH = 200
        private const val BASE_THUMBNAIL_URL = "https://thumbnails.libretro.com"
        private const val IMAGE_TYPE = "Named_Boxarts"
    }
}
