package com.HrshD1eux.DocLite.models

import android.net.Uri

data class DocumentFile(
    val id: String, // Path or Uri String
    val name: String,
    val path: String,
    val uriString: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val format: DocumentFormat,
    val isFavorite: Boolean = false,
    val isDirectory: Boolean = false,
    val pageCount: Int = 1,
    val isPasswordProtected: Boolean = false
) {
    val formattedSize: String
        get() {
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.1f GB", gb)
        }

    fun getUri(): Uri = Uri.parse(uriString)
}

