package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.PdfAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE fileUri = :fileUri ORDER BY timestamp ASC")
    fun getAnnotationsForFile(fileUri: String): Flow<List<PdfAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PdfAnnotationEntity)

    @Query("DELETE FROM pdf_annotations WHERE id = :annotationId")
    suspend fun deleteAnnotation(annotationId: String)

    @Query("DELETE FROM pdf_annotations WHERE fileUri = :fileUri")
    suspend fun clearAnnotationsForFile(fileUri: String)
}

