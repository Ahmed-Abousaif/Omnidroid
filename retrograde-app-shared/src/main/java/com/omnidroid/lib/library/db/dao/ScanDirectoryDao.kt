package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnidroid.lib.library.db.entity.ScanDirectory

@Dao
interface ScanDirectoryDao {
    @Query("SELECT * FROM scan_directories WHERE treeUri = :treeUri")
    fun selectByTreeUri(treeUri: String): List<ScanDirectory>

    @Query(
        """
        SELECT * FROM scan_directories
        WHERE treeUri = :treeUri AND documentId = :documentId
        LIMIT 1
        """,
    )
    fun select(treeUri: String, documentId: String): ScanDirectory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entries: List<ScanDirectory>)

    @Query("DELETE FROM scan_directories WHERE treeUri = :treeUri")
    fun deleteByTreeUri(treeUri: String)

    @Query(
        """
        DELETE FROM scan_directories
        WHERE treeUri = :treeUri AND documentId NOT IN (:documentIds)
        """,
    )
    fun deleteMissing(treeUri: String, documentIds: List<String>)
}
