package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trashed_files")
data class TrashedFileEntity(
    @PrimaryKey val uriString: String,
    val originalPath: String,
    val trashedPath: String,
    val name: String,
    val sizeBytes: Long,
    val formatName: String,
    val trashedTimestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) // 30 days retention
)
