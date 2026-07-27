package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.DocumentMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentMetadataDao {
    @Query("SELECT * FROM document_metadata WHERE fileUri = :fileUri")
    fun getMetadata(fileUri: String): Flow<DocumentMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(metadata: DocumentMetadataEntity)
}

