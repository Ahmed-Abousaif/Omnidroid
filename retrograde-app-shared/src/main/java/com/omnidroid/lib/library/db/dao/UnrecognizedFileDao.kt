package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnidroid.lib.library.db.entity.UnrecognizedFile
import kotlinx.coroutines.flow.Flow

@Dao
interface UnrecognizedFileDao {
    @Query("SELECT * FROM unrecognized_files ORDER BY fileName ASC")
    fun observeAll(): Flow<List<UnrecognizedFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(file: UnrecognizedFile)

    @Query("DELETE FROM unrecognized_files WHERE fileUri = :fileUri")
    fun deleteByUri(fileUri: String)

    @Query("DELETE FROM unrecognized_files WHERE lastIndexedAt < :lastIndexedAt")
    fun deleteOlderThan(lastIndexedAt: Long)
}