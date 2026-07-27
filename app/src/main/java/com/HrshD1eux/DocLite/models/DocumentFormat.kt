package com.HrshD1eux.DocLite.models

import androidx.compose.ui.graphics.Color

enum class DocumentFormat(
    val displayName: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
    val accentColor: Long
) {
    WORD("Word", listOf("docx", "doc", "txt", "rtf"), listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "text/plain",
        "application/rtf",
        "text/rtf"
    ), 0xFF2B579A),
    
    EXCEL("Excel", listOf("xlsx", "xls", "csv"), listOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "text/csv"
    ), 0xFF217346),
    
    POWERPOINT("PowerPoint", listOf("pptx", "ppt"), listOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-powerpoint"
    ), 0xFFD24726),
    
    PDF("PDF", listOf("pdf"), listOf("application/pdf"), 0xFFCC292B),
    
    IMAGE("Image", listOf("jpg", "jpeg", "png", "webp", "gif"), listOf(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    ), 0xFF7B1FA2);

    fun getComposeColor(): Color = Color(accentColor)

    companion object {
        fun fromExtension(ext: String): DocumentFormat {
            val cleanExt = ext.lowercase().trimStart('.')
            return entries.firstOrNull { format -> format.extensions.contains(cleanExt) } ?: WORD
        }

        fun fromMimeType(mime: String): DocumentFormat {
            return entries.firstOrNull { format -> format.mimeTypes.contains(mime.lowercase()) }
                ?: fromExtension(mime)
        }
    }
}

