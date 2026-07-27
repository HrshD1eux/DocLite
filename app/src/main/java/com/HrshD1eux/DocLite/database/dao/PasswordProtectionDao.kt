package com.HrshD1eux.DocLite.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.HrshD1eux.DocLite.database.entity.PasswordProtectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordProtectionDao {

    @Query("SELECT passwordHash FROM password_protected_files WHERE fileUri = :fileUri")
    suspend fun getPasswordHash(fileUri: String): String?

    @Query("SELECT fileUri FROM password_protected_files")
    fun getAllProtectedUris(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPassword(entity: PasswordProtectionEntity)

    @Query("DELETE FROM password_protected_files WHERE fileUri = :fileUri")
    suspend fun removePassword(fileUri: String)

    @Query("SELECT COUNT(*) FROM password_protected_files WHERE fileUri = :fileUri")
    suspend fun isProtected(fileUri: String): Int
}

