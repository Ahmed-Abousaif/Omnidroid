package com.omnidroid.metadata.libretrodb.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.omnidroid.metadata.libretrodb.db.entity.CandidateRom
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom

@Dao
interface GameDao {
    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND crc32 = :crc
        LIMIT 1
        """,
    )
    suspend fun findBySystemAndCrc(
        system: String,
        crc: String,
    ): LibretroRom?

    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND serial = :serial
        ORDER BY CASE WHEN size = :size THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun findBySystemAndSerial(
        system: String,
        serial: String,
        size: Long,
    ): LibretroRom?

    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND code = :code
        ORDER BY CASE WHEN size = :size THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun findBySystemAndCode(
        system: String,
        code: String,
        size: Long,
    ): LibretroRom?

    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND romHash = :romHash
        LIMIT 1
        """,
    )
    suspend fun findBySystemAndRomHash(
        system: String,
        romHash: Long,
    ): LibretroRom?

    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND normalizedName = :normalized
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedName(
        system: String,
        normalized: String,
    ): LibretroRom?

    @Query(
        """
        SELECT * FROM games
        WHERE system = :system AND normalizedName LIKE :prefix || '%'
        ORDER BY normalizedName ASC
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedNamePrefix(
        system: String,
        prefix: String,
    ): LibretroRom?

    @Query(
        """
        SELECT name, system, romHash, normalizedName, rawName FROM games
        WHERE system = :system AND normalizedName LIKE :token || '%'
        ORDER BY normalizedName ASC
        LIMIT :limit
        """,
    )
    suspend fun findCandidatesByTokenPrefix(
        system: String,
        token: String,
        limit: Int = 50,
    ): List<CandidateRom>

    @Query(
        """
        SELECT name, system, romHash, normalizedName, rawName FROM games
        WHERE system = :system AND normalizedName LIKE '% ' || :token || '%'
        ORDER BY normalizedName ASC
        LIMIT :limit
        """,
    )
    suspend fun findCandidatesByTokenWord(
        system: String,
        token: String,
        limit: Int = 50,
    ): List<CandidateRom>

    suspend fun findCandidatesByToken(
        system: String,
        token: String,
    ): List<CandidateRom> {
        val prefixMatches = findCandidatesByTokenPrefix(system, token, 50)
        if (prefixMatches.size >= 15) {
            return prefixMatches
        }
        val wordMatches = findCandidatesByTokenWord(system, token, 50 - prefixMatches.size)
        return (prefixMatches + wordMatches).distinctBy { it.name ?: it.rawName }
    }

    @Query(
        """
        SELECT name, system, romHash, normalizedName, rawName FROM games
        WHERE system = :system AND normalizedName LIKE :query || '%'
        ORDER BY normalizedName ASC
        LIMIT :limit
        """,
    )
    suspend fun searchGamesByPrefix(
        system: String,
        query: String,
        limit: Int = 40,
    ): List<CandidateRom>

    @Query(
        """
        SELECT name, system, romHash, normalizedName, rawName FROM games
        WHERE system = :system AND (
            normalizedName LIKE '% ' || :query || '%' OR
            name LIKE '%' || :query || '%'
        )
        ORDER BY normalizedName ASC
        LIMIT :limit
        """,
    )
    suspend fun searchGamesBySubstring(
        system: String,
        query: String,
        limit: Int = 40,
    ): List<CandidateRom>

    suspend fun searchGamesByQuery(
        system: String,
        query: String,
    ): List<CandidateRom> {
        val prefixMatches = searchGamesByPrefix(system, query, 40)
        if (prefixMatches.size >= 40) {
            return prefixMatches
        }
        val remaining = 40 - prefixMatches.size
        val substringMatches = searchGamesBySubstring(system, query, remaining)
        return (prefixMatches + substringMatches).distinctBy { it.name ?: it.rawName }
    }
}
