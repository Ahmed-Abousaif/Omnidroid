package com.omnidroid.metadata.libretrodb.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import com.omnidroid.metadata.libretrodb.db.entity.VerifiedManifest
import kotlinx.coroutines.flow.Flow

@Dao
interface SliceDao {
    @Query("SELECT * FROM installed_slices")
    fun observeAll(): Flow<List<InstalledSlice>>

    @Query("SELECT * FROM installed_slices")
    suspend fun all(): List<InstalledSlice>

    @Query("SELECT * FROM installed_slices WHERE sliceId = :sliceId")
    suspend fun find(sliceId: String): InstalledSlice?

    @Query(
        """
        SELECT sha256 FROM installed_slices
        WHERE systems = :system OR systems LIKE :prefix OR systems LIKE :suffix OR systems LIKE :middle
        LIMIT 1
        """,
    )
    suspend fun stamp(
        system: String,
        prefix: String,
        suffix: String,
        middle: String,
    ): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slice: InstalledSlice)

    @Query(
        """
        SELECT * FROM verified_manifests
        WHERE coreName = :coreName AND coresVersion = :coresVersion
        LIMIT 1
        """,
    )
    suspend fun verified(
        coreName: String,
        coresVersion: String,
    ): VerifiedManifest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerified(manifest: VerifiedManifest)
}
