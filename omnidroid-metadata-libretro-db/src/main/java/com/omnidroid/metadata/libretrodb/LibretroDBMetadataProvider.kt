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
import com.omnidroid.metadata.libretrodb.db.dao.GameDao
import com.omnidroid.metadata.libretrodb.db.entity.CandidateRom
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom

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
            thumbnail = if (row != null) computeCoverUrl(GameSystem.findById(systemId), coverTitle, row.thumbnailPath) else null,
        )
    }

    override suspend fun resolveArcade(
        setName: String,
        memberCrcs: List<String>,
    ): com.omnidroid.lib.library.metadata.ArcadeResolution? {
        val cleanName = ScanKeys.romBase(setName)
        val directHash = ScanKeys.romHash(cleanName)

        // Stage 1: Direct driver lookup (e.g. "ddragon", "gunbird", "tetris")
        var matchedSet: com.omnidroid.metadata.libretrodb.detect.ArcadeSet? =
            directHash?.let { ovgdbManager.detectInstance.arcadeSets().find(it) }
        var matchedDriver: String? = matchedSet?.setName ?: cleanName

        // Stage 2: Fallback title matching against arcade database slice (e.g. "double_dragon" -> "Double Dragon" -> ddragon)
        if (matchedSet == null) {
            val arcadeRom =
                findByLooseName(SystemID.FBNEO.dbname, cleanName)
                    ?: findByLooseName(SystemID.MAME2003PLUS.dbname, cleanName)
            if (arcadeRom?.romHash != null) {
                val candidateSet = ovgdbManager.detectInstance.arcadeSets().find(arcadeRom.romHash)
                if (candidateSet != null) {
                    matchedSet = candidateSet
                    matchedDriver = candidateSet.setName
                }
            }
        }

        // Stage 3: Fallback member CRC matching (inspecting files inside the zip)
        if (matchedSet == null && memberCrcs.isNotEmpty()) {
            val dao = ovgdbManager.dbInstance.gameDao()
            for (crc in memberCrcs.take(30)) {
                val rom =
                    dao.findBySystemAndCrc(SystemID.FBNEO.dbname, crc)
                        ?: dao.findBySystemAndCrc(SystemID.MAME2003PLUS.dbname, crc)
                if (rom?.romHash != null) {
                    val found = ovgdbManager.detectInstance.arcadeSets().find(rom.romHash)
                    if (found != null) {
                        matchedSet = found
                        matchedDriver = found.setName
                        break
                    }
                }
            }
        }

        if (matchedSet == null || matchedDriver == null) return null

        val systems = matchedSet.systems.split(',')
        val systemId =
            if (systems.size == 1) {
                systems.first()
            } else {
                val hits = HashMap<String, Int>()
                memberCrcs.distinct().chunked(400).forEach { chunk ->
                    ovgdbManager.detectInstance.arcadeMarkers().matching(matchedSet.setHash, chunk).forEach { marker ->
                        hits[marker.system] = (hits[marker.system] ?: 0) + 1
                    }
                }
                val mameHits = hits[SystemID.MAME2003PLUS.dbname] ?: 0
                val fbneoHits = hits[SystemID.FBNEO.dbname] ?: 0
                if (mameHits > fbneoHits) SystemID.MAME2003PLUS.dbname else SystemID.FBNEO.dbname
            }

        return com.omnidroid.lib.library.metadata.ArcadeResolution(systemId, matchedDriver)
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
        val variants = LibretroTitleMatching.titleVariants(fallbackTitle)
        if (variants.isEmpty()) return null
        val dao = ovgdbManager.dbInstance.gameDao()
        val queryNorm = LibretroTitleMatching.normalizeTitle(fallbackTitle) ?: return null

        // Stage 1: Exact lookups, then region-ranked prefix lookups
        for (normalized in variants) {
            dao.findByNormalizedName(systemId, normalized)?.takeIf { isLibraryTitle(it.name) }?.let {
                return it
            }
        }
        for (normalized in variants) {
            pickBestPrefixMatch(dao, systemId, "$normalized ", queryNorm)?.let { return it }
            pickBestPrefixMatch(dao, systemId, "$normalized (", queryNorm)?.let { return it }
        }

        // Stage 2: Token-Fuzzy fallback (smarter recovery)
        val queryCore = LibretroTitleMatching.stripTags(queryNorm).ifBlank { queryNorm }
        val significantToken = TokenMatcher.selectSignificantToken(queryCore) ?: return null
        val candidates = dao.findCandidatesByToken(systemId, significantToken)

        val bestMatch =
            candidates
                .asSequence()
                .filter { isLibraryTitle(it.name) }
                .map { candidate ->
                    val candidateNorm = candidate.normalizedName ?: ""
                    val candidateCore =
                        LibretroTitleMatching.stripTags(candidateNorm).ifBlank { candidateNorm }
                    val tokenScore = TokenMatcher.bestScore(queryCore, candidateCore)
                    val regionScore = LibretroTitleMatching.regionScore(queryNorm, candidateNorm)
                    candidate to (tokenScore to regionScore)
                }
                .filter { it.second.first >= 85 }
                .maxWithOrNull(
                    compareBy<Pair<CandidateRom, Pair<Int, Int>>> { it.second.first }
                        .thenBy { it.second.second },
                )
                ?.first

        return bestMatch?.let {
            LibretroRom(
                name = it.name,
                system = it.system,
                crc32 = null,
                serial = null,
                code = null,
                size = null,
                romHash = it.romHash,
                normalizedName = it.normalizedName,
                rawName = it.rawName,
                thumbnailPath = it.thumbnailPath,
            )
        }
    }

    private suspend fun pickBestPrefixMatch(
        dao: GameDao,
        systemId: String,
        prefix: String,
        queryNorm: String,
    ): LibretroRom? {
        val matches =
            dao.findAllByNormalizedNamePrefix(systemId, prefix)
                .filter { isLibraryTitle(it.name) }
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.first()
        return matches.maxWithOrNull(
            compareBy<LibretroRom> {
                LibretroTitleMatching.regionScore(queryNorm, it.normalizedName ?: "")
            }.thenByDescending { it.thumbnailPath != null }
                .thenBy { it.normalizedName ?: "" },
        )
    }

    private fun isLibraryTitle(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.length > 200) return false
        return !name.contains(". ") || name.length <= 80
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
        thumbnailPath: String? = null,
    ): String? = CoverSearcher.computeCoverUrl(system, name, thumbnailPath)
}
