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
            thumbnail = computeCoverUrl(GameSystem.findById(systemId), coverTitle, row?.thumbnailPath),
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
        val variants = titleVariants(fallbackTitle)
        if (variants.isEmpty()) return null
        val dao = ovgdbManager.dbInstance.gameDao()

        // Stage 1: Exact and Prefix lookups (fast path)
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

        // Stage 2: Token-Fuzzy fallback (smarter recovery)
        val queryNorm = normalizeTitle(fallbackTitle) ?: return null
        val significantToken = TokenMatcher.selectSignificantToken(queryNorm) ?: return null
        val candidates = dao.findCandidatesByToken(systemId, significantToken)

        val bestMatch =
            candidates
                .asSequence()
                .filter { isLibraryTitle(it.name) }
                .map { candidate ->
                    candidate to TokenMatcher.bestScore(queryNorm, candidate.normalizedName ?: "")
                }
                .filter { it.second >= 85 }
                .maxByOrNull { it.second }
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

    private val commonAliases =
        mapOf(
            "megaman" to "mega man",
            "thps" to "tony hawk's pro skater",
            "sf2" to "street fighter ii",
            "sfii" to "street fighter ii",
            "mk" to "mortal kombat",
            "smb" to "super mario bros",
            "tmnt" to "teenage mutant ninja turtles",
            "zelda" to "the legend of zelda",
            "castlevania sotn" to "castlevania symphony of the night",
            "re1" to "resident evil",
            "re2" to "resident evil 2",
            "re3" to "resident evil 3",
            "bros" to "brothers",
            "pacman" to "pac man",
            "ddragon" to "double dragon",
        )

    private val regionOrTagSuffix =
        Regex(
            """\s*\((?:usa|europe|japan|world|rev\s*\d+|v\d+\.\d+|beta|proto|track\s*\d+)[^)]*\)$""",
            RegexOption.IGNORE_CASE,
        )

    private fun titleVariants(value: String): List<String> {
        val base = normalizeTitle(value) ?: return emptyList()
        val variants = linkedSetOf(base)

        // 1. Article variations ("the", "a", "an")
        if (base.startsWith("the ")) {
            variants += base.removePrefix("the ").trim()
        } else {
            variants += "the $base"
        }
        if (base.endsWith(" the")) {
            variants += "the " + base.removeSuffix(" the").trim()
            variants += base.removeSuffix(" the").trim()
        }

        // 2. Region / Tag Stripping
        val withoutTags = base.replace(regionOrTagSuffix, "").trim()
        if (withoutTags.isNotEmpty() && withoutTags != base) {
            variants += withoutTags
            if (withoutTags.startsWith("the ")) {
                variants += withoutTags.removePrefix("the ").trim()
            }
        }
        val genericNoParen = base.substringBefore('(').trim()
        if (genericNoParen.isNotEmpty() && genericNoParen != base) {
            variants += genericNoParen
        }

        // 3. Common Alias Mapping
        for (v in variants.toList()) {
            for ((alias, canonical) in commonAliases) {
                if (v.contains(alias)) {
                    variants += v.replace(alias, canonical).trim()
                }
            }
        }

        return variants.filter { it.isNotBlank() }
    }

    private fun normalizeTitle(value: String): String? {
        val cleaned =
            value
                .lowercase(java.util.Locale.US)
                .replace(Regex("""\.\w{1,4}$"""), "")
                .replace(Regex("""\s*\[[^\]]*\]"""), "")
                .replace('_', ' ')
                .replace(",", "")
                .replace(Regex("""[-:]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        return cleaned.takeIf { it.isNotBlank() }
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
