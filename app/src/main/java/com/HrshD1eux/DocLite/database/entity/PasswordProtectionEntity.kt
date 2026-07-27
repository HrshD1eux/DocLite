package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_protected_files")
data class PasswordProtectionEntity(
    @PrimaryKey val fileUri: String,
    val passwordHash: String,
    val dateProtected: Long = System.currentTimeMillis()
)

