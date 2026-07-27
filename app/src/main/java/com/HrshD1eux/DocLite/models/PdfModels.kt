package com.HrshD1eux.DocLite.models

import androidx.compose.ui.graphics.Color

enum class AnnotationType {
    HIGHLIGHT, UNDERLINE, FREE_DRAW, STICKY_NOTE, SIGNATURE, FORM_FIELD
}

data class DrawingPoint(
    val xRatio: Float,
    val yRatio: Float
)

data class PdfAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileUri: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val colorHex: String = "#FFEB3B",
    val strokeWidthDp: Float = 3f,
    val points: List<DrawingPoint> = emptyList(), // For FREE_DRAW
    val noteText: String = "",                    // For STICKY_NOTE / FORM_FIELD
    val signatureBitmapPath: String? = null,      // For SIGNATURE
    val boundsLeftRatio: Float = 0f,
    val boundsTopRatio: Float = 0f,
    val boundsWidthRatio: Float = 0.2f,
    val boundsHeightRatio: Float = 0.1f,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getComposeColor(): Color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) { Color.Yellow }
}

data class PdfSearchResult(
    val pageIndex: Int,
    val snippet: String,
    val matchIndex: Int
)

data class PdfFormField(
    val id: String,
    val label: String,
    val value: String = "",
    val pageIndex: Int,
    val isRequired: Boolean = false
)

