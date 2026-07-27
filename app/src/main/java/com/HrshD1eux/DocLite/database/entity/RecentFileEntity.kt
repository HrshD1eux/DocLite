package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uriString: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val formatName: String,
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
)

