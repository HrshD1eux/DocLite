package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_files")
data class FavoriteFileEntity(
    @PrimaryKey val uriString: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val formatName: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)

