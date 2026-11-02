package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.TrashedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashedFileDao {
    @Query("SELECT * FROM trashed_files ORDER BY trashedTimestamp DESC")
    fun getAllTrashedFiles(): Flow<List<TrashedFileEntity>>

    @Query("SELECT * FROM trashed_files WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): TrashedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(trashedFile: TrashedFileEntity)

    @Query("DELETE FROM trashed_files WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("SELECT * FROM trashed_files WHERE expiryTimestamp <= :now")
    suspend fun getExpiredFiles(now: Long): List<TrashedFileEntity>

    @Query("DELETE FROM trashed_files")
    suspend fun clearAll()
}
