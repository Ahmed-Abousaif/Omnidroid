package com.omnidroid.metadata.libretrodb.db.dao

import androidx.room.Dao
import androidx.room.Query
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
        ORDER BY length(normalizedName) ASC
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedNamePrefix(
        system: String,
        prefix: String,
    ): LibretroRom?
}
