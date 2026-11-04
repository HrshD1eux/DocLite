package com.HrshD1eux.DocLite.core.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object UriResolver {
    private const val TAG = "UriResolver"

    /**
     * Resolves a content:// or file:// URI to a direct physical File on storage if accessible.
     * Handles FileProvider URIs (external_files, root, etc.), SAF primary URIs, MediaStore URIs,
     * and raw physical paths.
     */
    fun resolvePhysicalFile(context: Context, uri: Uri): File? {
        try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists()) return file
                }
            }

            val rawPath = uri.path ?: ""
            val decodedPath = try { Uri.decode(rawPath) } catch (e: Exception) { rawPath }
            val extStorage = Environment.getExternalStorageDirectory()

            // 1. Android FileProvider: content://.../external_files/<subpath>
            for (p in listOf(decodedPath, rawPath)) {
                if (p.contains("/external_files/")) {
                    val sub = p.substringAfter("/external_files/").trimStart('/')
                    val file = File(extStorage, sub)
                    if (file.exists()) return file
                }
            }

            // 2. Storage Access Framework: content://.../document/primary:<subpath>
            for (p in listOf(decodedPath, rawPath)) {
                if (p.contains("primary:")) {
                    val sub = p.substringAfter("primary:").trimStart('/')
                    val file = File(extStorage, sub)
                    if (file.exists()) return file
                }
            }

            // 3. Root FileProvider: content://.../root/<subpath>
            for (p in listOf(decodedPath, rawPath)) {
                if (p.startsWith("/root/")) {
                    val file = File("/" + p.removePrefix("/root/"))
                    if (file.exists()) return file
                }
            }

            // 4. Any URI containing direct /storage/emulated/0/ path
            for (p in listOf(decodedPath, rawPath)) {
                if (p.contains("/storage/emulated/0/")) {
                    val sub = p.substringAfter("/storage/emulated/0/").trimStart('/')
                    val file = File(extStorage, sub)
                    if (file.exists()) return file
                }
            }

            // 5. Try MediaStore _data column query (safely with try-catch)
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIdx != -1) {
                                val dataPath = cursor.getString(dataIdx)
                                if (!dataPath.isNullOrBlank()) {
                                    val f = File(dataPath)
                                    if (f.exists()) return f
                                }
                            }
                        }
                    }
                } catch (ignored: Throwable) {}
            }

            // 6. Direct file check on decodedPath
            if (decodedPath.isNotBlank()) {
                val direct = File(decodedPath)
                if (direct.exists()) return direct
            }

            // 7. Check if path relative to extStorage exists
            val relativeSub = decodedPath.trimStart('/')
            val relativeFile = File(extStorage, relativeSub)
            if (relativeFile.exists()) return relativeFile

        } catch (t: Throwable) {
            Log.w(TAG, "Error resolving physical file for $uri", t)
        }
        return null
    }

    /**
     * Resolves an incoming URI. If it is an external content:// URI (e.g. from WhatsApp or FileProvider),
     * immediately caches it to local storage to prevent SecurityException (Permission Denial)
     * when accessed asynchronously or across activity lifecycle events.
     */
    fun resolveOrCacheIncomingUri(context: Context, uri: Uri): Uri {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) return uri
            }

            // 1. Check if direct physical file is accessible on storage
            val physicalFile = resolvePhysicalFile(context, uri)
            if (physicalFile != null && physicalFile.exists() && physicalFile.canRead()) {
                return Uri.fromFile(physicalFile)
            }

            // 2. If content URI, copy immediately to local cache
            if (uri.scheme == "content") {
                val displayName = getDisplayName(context, uri, "document")
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val cacheDir = File(context.cacheDir, "opened_external_files").apply { mkdirs() }
                val cachedFile = File(cacheDir, "${System.currentTimeMillis()}_$safeName")

                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    stream.use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cachedFile.exists() && cachedFile.length() > 0L) {
                        return Uri.fromFile(cachedFile)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resolveOrCacheIncomingUri failed for $uri, falling back to permanent URI", t)
        }
        return getCanonicalPermanentUri(context, uri)
    }

    /**
     * Opens an InputStream with automatic fallback from ContentResolver to direct physical file.
     * Prevents Permission Denial crashes on FileProvider and external content URIs.
     */
    fun openInputStream(context: Context, uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            try {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) return FileInputStream(f)
            } catch (ignored: Throwable) {}
        }

        // Try standard ContentResolver first
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.openInputStream failed for $uri (${t.message}), attempting physical resolution", t)
        }

        // Fallback: Resolve physical file on storage
        try {
            val physicalFile = resolvePhysicalFile(context, uri)
            if (physicalFile != null && physicalFile.exists() && physicalFile.canRead()) {
                return FileInputStream(physicalFile)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Physical file fallback read failed for $uri", t)
        }

        return null
    }

    /**
     * Opens an OutputStream with automatic fallback from ContentResolver to direct physical file.
     */
    fun openOutputStream(context: Context, uri: Uri): OutputStream? {
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                file.parentFile?.mkdirs()
                return FileOutputStream(file)
            } catch (ignored: Throwable) {}
        }

        // Try ContentResolver
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri, "rwt")
                ?: context.contentResolver.openOutputStream(uri)
            if (stream != null) return stream
        } catch (t: Throwable) {
            Log.w(TAG, "ContentResolver.openOutputStream failed for $uri (${t.message}), attempting physical resolution", t)
        }

        // Fallback: Resolve physical file on storage
        try {
            val physicalFile = resolvePhysicalFile(context, uri)
            if (physicalFile != null) {
                physicalFile.parentFile?.mkdirs()
                if (physicalFile.canWrite() || !physicalFile.exists()) {
                    return FileOutputStream(physicalFile)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Physical file fallback write failed for $uri", t)
        }

        return null
    }

    /**
     * Safely retrieves display name with zero risk of SecurityException / Permission Denial.
     */
    fun getDisplayName(context: Context, uri: Uri, defaultName: String = "document"): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (ignored: Throwable) {}
        }

        if (name.isNullOrBlank()) {
            val physical = resolvePhysicalFile(context, uri)
            if (physical != null && physical.name.isNotBlank()) {
                name = physical.name
            }
        }

        if (name.isNullOrBlank()) {
            val seg = uri.lastPathSegment ?: uri.path ?: ""
            name = Uri.decode(seg.substringAfterLast('/'))
        }

        return name?.ifBlank { null } ?: defaultName
    }

    /**
     * Normalizes an incoming URI to a permanent, accessible URI:
     * If it is a FileProvider URI that resolves to a physical file, returns Uri.fromFile(physicalFile).
     * This ensures Recent Files and re-openings never hit expired permission denials.
     */
    fun getCanonicalPermanentUri(context: Context, uri: Uri): Uri {
        val physicalFile = resolvePhysicalFile(context, uri)
        if (physicalFile != null && physicalFile.exists()) {
            return Uri.fromFile(physicalFile)
        }
        return uri
    }
}
