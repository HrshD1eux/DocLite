package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_metadata")
data class DocumentMetadataEntity(
    @PrimaryKey val fileUri: String,
    val wordCount: Int = 0,
    val pageCount: Int = 1,
    val lastReadPosition: Int = 0,
    val customNotes: String = ""
)

