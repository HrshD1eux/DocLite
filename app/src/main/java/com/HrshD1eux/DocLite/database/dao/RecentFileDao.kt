package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.RecentFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecentFiles(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE formatName = :formatName ORDER BY lastOpenedTimestamp DESC")
    fun getRecentFilesByFormat(formatName: String): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(recentFile: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}

