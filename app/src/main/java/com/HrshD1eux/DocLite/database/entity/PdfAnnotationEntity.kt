package com.HrshD1eux.DocLite.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val fileUri: String,
    val pageIndex: Int,
    val annotationType: String,
    val colorHex: String,
    val strokeWidthDp: Float,
    val pointsJson: String,
    val noteText: String,
    val signatureBitmapPath: String?,
    val boundsLeftRatio: Float,
    val boundsTopRatio: Float,
    val boundsWidthRatio: Float,
    val boundsHeightRatio: Float,
    val timestamp: Long
)

