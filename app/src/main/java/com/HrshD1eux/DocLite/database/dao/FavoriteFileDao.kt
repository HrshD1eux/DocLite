package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.FavoriteFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFileDao {
    @Query("SELECT * FROM favorite_files ORDER BY addedTimestamp DESC")
    fun getAllFavoriteFiles(): Flow<List<FavoriteFileEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_files WHERE uriString = :uriString)")
    fun isFavorite(uriString: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favoriteFile: FavoriteFileEntity)

    @Query("DELETE FROM favorite_files WHERE uriString = :uriString")
    suspend fun removeFavorite(uriString: String)
}

