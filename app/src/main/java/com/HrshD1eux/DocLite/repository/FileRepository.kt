package com.HrshD1eux.DocLite.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.HrshD1eux.DocLite.database.dao.FavoriteFileDao
import com.HrshD1eux.DocLite.database.dao.PasswordProtectionDao
import com.HrshD1eux.DocLite.database.dao.RecentFileDao
import com.HrshD1eux.DocLite.database.entity.FavoriteFileEntity
import com.HrshD1eux.DocLite.database.entity.PasswordProtectionEntity
import com.HrshD1eux.DocLite.database.entity.RecentFileEntity
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class FileRepository(
    private val context: Context,
    private val recentFileDao: RecentFileDao,
    private val favoriteFileDao: FavoriteFileDao,
    private val passwordProtectionDao: PasswordProtectionDao
) {

    private val scanTriggerFlow = MutableStateFlow(System.currentTimeMillis())

    val protectedUrisFlow: Flow<List<String>> = passwordProtectionDao.getAllProtectedUris()

    val allDocumentsFlow: Flow<List<DocumentFile>> = combine(
        scanTriggerFlow,
        protectedUrisFlow
    ) { _, protectedUris ->
        val protectedSet = protectedUris.toSet()
        withContext(Dispatchers.IO) {
            val allFiles = scanAllDocuments()
            allFiles.map { file ->
                file.copy(isPasswordProtected = protectedSet.contains(file.uriString))
            }
        }
    }

    val recentFilesFlow: Flow<List<DocumentFile>> = combine(
        recentFileDao.getAllRecentFiles(),
        protectedUrisFlow
    ) { entities, protectedUris ->
        val protectedSet = protectedUris.toSet()
        entities.map { entity ->
            DocumentFile(
                id = entity.uriString,
                name = entity.name,
                path = entity.path,
                uriString = entity.uriString,
                sizeBytes = entity.sizeBytes,
                lastModified = entity.lastOpenedTimestamp,
                format = DocumentFormat.valueOf(entity.formatName),
                isPasswordProtected = protectedSet.contains(entity.uriString)
            )
        }
    }

    val favoriteFilesFlow: Flow<List<DocumentFile>> = combine(
        favoriteFileDao.getAllFavoriteFiles(),
        protectedUrisFlow
    ) { entities, protectedUris ->
        val protectedSet = protectedUris.toSet()
        entities.map { entity ->
            DocumentFile(
                id = entity.uriString,
                name = entity.name,
                path = entity.path,
                uriString = entity.uriString,
                sizeBytes = entity.sizeBytes,
                lastModified = entity.addedTimestamp,
                format = DocumentFormat.valueOf(entity.formatName),
                isFavorite = true,
                isPasswordProtected = protectedSet.contains(entity.uriString)
            )
        }
    }

    fun refreshScan() {
        scanTriggerFlow.value = System.currentTimeMillis()
    }

    suspend fun recordRecentFile(file: DocumentFile) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(file.uriString)
        if (uri.scheme == "content") {
            val isPersisted = try {
                context.contentResolver.persistedUriPermissions.any { it.uri == uri }
            } catch (e: Exception) { false }
            
            // Allow media store URIs since they don't require persistable permission if we have read external storage
            val isMediaStore = uri.authority == "media"
            
            if (!isPersisted && !isMediaStore) {
                return@withContext // Do not save temporary URIs
            }
        }
        
        recentFileDao.insertOrUpdate(
            RecentFileEntity(
                uriString = file.uriString,
                name = file.name,
                path = file.path,
                sizeBytes = file.sizeBytes,
                formatName = file.format.name,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFavorite(file: DocumentFile) = withContext(Dispatchers.IO) {
        val isFav = favoriteFileDao.isFavorite(file.uriString).first()
        if (isFav) {
            favoriteFileDao.removeFavorite(file.uriString)
        } else {
            favoriteFileDao.addFavorite(
                FavoriteFileEntity(
                    uriString = file.uriString,
                    name = file.name,
                    path = file.path,
                    sizeBytes = file.sizeBytes,
                    formatName = file.format.name,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun isFavoriteFlow(uriString: String): Flow<Boolean> {
        return favoriteFileDao.isFavorite(uriString)
    }

    suspend fun clearRecentFiles() = withContext(Dispatchers.IO) {
        recentFileDao.clearAll()
    }

    suspend fun setFilePassword(fileUri: String, password: String) = withContext(Dispatchers.IO) {
        val hash = hashPassword(password)
        passwordProtectionDao.setPassword(
            PasswordProtectionEntity(fileUri = fileUri, passwordHash = hash)
        )
    }

    suspend fun removeFilePassword(fileUri: String) = withContext(Dispatchers.IO) {
        passwordProtectionDao.removePassword(fileUri)
    }

    suspend fun verifyFilePassword(fileUri: String, inputPassword: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = passwordProtectionDao.getPasswordHash(fileUri) ?: return@withContext true
        storedHash == hashPassword(inputPassword)
    }

    suspend fun isFileProtected(fileUri: String): Boolean = withContext(Dispatchers.IO) {
        passwordProtectionDao.isProtected(fileUri) > 0
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun createNewDocument(
        name: String, 
        format: DocumentFormat,
        password: String? = null
    ): DocumentFile = withContext(Dispatchers.IO) {
        val docsDir = File(context.filesDir, "DocLite_Documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val extension = format.extensions.first()
        val file = File(docsDir, "$name.$extension")
        if (!file.exists()) {
            file.createNewFile()
            file.writeText(getInitialContentForFormat(name, format))
        }

        val uriString = Uri.fromFile(file).toString()
        if (!password.isNullOrBlank()) {
            setFilePassword(uriString, password)
        }

        val docFile = DocumentFile(
            id = uriString,
            name = file.name,
            path = file.absolutePath,
            uriString = uriString,
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            format = format,
            isPasswordProtected = !password.isNullOrBlank()
        )

        recordRecentFile(docFile)
        docFile
    }

    private fun getInitialContentForFormat(name: String, format: DocumentFormat): String {
        return when (format) {
            DocumentFormat.WORD -> "# $name\n\nWelcome to your new DocLite document. Start writing your content here."
            DocumentFormat.EXCEL -> "Item,Quantity,Price,Total\nProduct A,10,15.00,=B2*C2\nProduct B,5,25.00,=B3*C3\nTotal,,=SUM(D2:D3)"
            DocumentFormat.POWERPOINT -> "# $name\nSlide 1: Title Slide\nSlide 2: Content Overview"
            DocumentFormat.PDF -> "PDF Document Placeholder"
            DocumentFormat.IMAGE -> ""
        }
    }

    suspend fun scanAllDocuments(): List<DocumentFile> = withContext(Dispatchers.IO) {
        val appDocsDir = File(context.filesDir, "DocLite_Documents").apply { if (!exists()) mkdirs() }
        val foundFiles = mutableMapOf<String, DocumentFile>()
        
        val protectedUris = try {
            passwordProtectionDao.getAllProtectedUris().first().toSet()
        } catch (e: Exception) {
            emptySet()
        }

        val allowedExtensions = setOf(
            "doc", "docx", "txt", "rtf",
            "xls", "xlsx", "csv",
            "ppt", "pptx",
            "pdf"
        )

        // 1. App local documents
        appDocsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (allowedExtensions.contains(ext)) {
                    val format = DocumentFormat.fromExtension(ext)
                    val uriStr = Uri.fromFile(file).toString()
                    foundFiles[file.canonicalPath] = DocumentFile(
                        id = uriStr,
                        name = file.name,
                        path = file.absolutePath,
                        uriString = uriStr,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        format = format,
                        isDirectory = false,
                        isPasswordProtected = protectedUris.contains(uriStr)
                    )
                }
            }
        }

        // 2. MediaStore Query
        val projection = arrayOf(
            android.provider.MediaStore.Files.FileColumns._ID,
            android.provider.MediaStore.Files.FileColumns.DATA,
            android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
            android.provider.MediaStore.Files.FileColumns.SIZE,
            android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.pdf' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.doc' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.docx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.xls' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.xlsx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.csv' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.ppt' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.pptx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.txt' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.rtf'"

        val sortOrder = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val queryUri = android.provider.MediaStore.Files.getContentUri("external")

        try {
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val data = cursor.getString(dataCol) ?: continue
                    val ext = data.substringAfterLast('.', "").lowercase()
                    if (!allowedExtensions.contains(ext)) continue

                    val fileObj = File(data)
                    val canonicalKey = try { fileObj.canonicalPath } catch (e: Exception) { data }
                    val name = cursor.getString(nameCol) ?: fileObj.name
                    val size = cursor.getLong(sizeCol)
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    val format = DocumentFormat.fromExtension(ext)

                    // Prefer Uri.fromFile if file exists, otherwise MediaStore content URI
                    val uriStr = if (fileObj.exists()) Uri.fromFile(fileObj).toString() else android.content.ContentUris.withAppendedId(queryUri, cursor.getLong(idCol)).toString()

                    foundFiles[canonicalKey] = DocumentFile(
                        id = uriStr,
                        name = name,
                        path = data,
                        uriString = uriStr,
                        sizeBytes = size,
                        lastModified = dateMod,
                        format = format,
                        isDirectory = false,
                        isPasswordProtected = protectedUris.contains(uriStr)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Raw File Recursion for unindexed locations (e.g. downloads, custom folders)
        val visitedDirs = mutableSetOf<String>()

        fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 4) return
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
            val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
            if (canonicalPath.contains("/Android/data") || canonicalPath.contains("/Android/obb") || dir.name.startsWith(".")) return
            if (!visitedDirs.add(canonicalPath)) return

            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanDir(file, depth + 1)
                    } else if (file.isFile && file.canRead()) {
                        val ext = file.extension.lowercase()
                        if (allowedExtensions.contains(ext)) {
                            val cPath = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                            if (!foundFiles.containsKey(cPath)) {
                                val format = DocumentFormat.fromExtension(ext)
                                val uriStr = Uri.fromFile(file).toString()
                                foundFiles[cPath] = DocumentFile(
                                    id = uriStr,
                                    name = file.name,
                                    path = file.absolutePath,
                                    uriString = uriStr,
                                    sizeBytes = file.length(),
                                    lastModified = file.lastModified(),
                                    format = format,
                                    isDirectory = false,
                                    isPasswordProtected = protectedUris.contains(uriStr)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip unreadable
            }
        }

        val externalRoots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStorageDirectory()
        )

        externalRoots.forEach { root ->
            scanDir(root, depth = 0)
        }

        foundFiles.values.sortedByDescending { it.lastModified }
    }

    suspend fun listLocalDocuments(directory: File? = null): List<DocumentFile> = withContext(Dispatchers.IO) {
        if (directory == null) {
            return@withContext scanAllDocuments()
        }
        val targetDir = directory
        val filesList = mutableListOf<DocumentFile>()
        val protectedUris = try {
            passwordProtectionDao.getAllProtectedUris().first().toSet()
        } catch (e: Exception) {
            emptySet()
        }

        targetDir.listFiles()?.forEach { file ->
            val ext = file.extension.lowercase()
            val format = DocumentFormat.fromExtension(ext)
            val uriStr = Uri.fromFile(file).toString()
            filesList.add(
                DocumentFile(
                    id = uriStr,
                    name = file.name,
                    path = file.absolutePath,
                    uriString = uriStr,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    format = format,
                    isDirectory = file.isDirectory,
                    isPasswordProtected = protectedUris.contains(uriStr)
                )
            )
        }

        filesList.sortedByDescending { it.lastModified }
    }

    suspend fun renameFile(file: DocumentFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val localFile = File(file.path)
            if (localFile.exists()) {
                val newFile = File(localFile.parentFile, newName)
                val success = localFile.renameTo(newFile)
                if (success && file.isPasswordProtected) {
                    val oldUri = file.uriString
                    val newUri = Uri.fromFile(newFile).toString()
                    val hash = passwordProtectionDao.getPasswordHash(oldUri)
                    if (hash != null) {
                        passwordProtectionDao.removePassword(oldUri)
                        passwordProtectionDao.setPassword(
                            PasswordProtectionEntity(fileUri = newUri, passwordHash = hash)
                        )
                    }
                }
                success
            } else true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(file: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            recentFileDao.deleteByUri(file.uriString)
            favoriteFileDao.removeFavorite(file.uriString)
            passwordProtectionDao.removePassword(file.uriString)
            val localFile = File(file.path)
            if (localFile.exists()) localFile.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun seedInitialSampleDocumentsIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("DocLite_Prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("samples_seeded", false)) {
            val docsDir = File(context.filesDir, "DocLite_Documents")
            if (!docsDir.exists()) docsDir.mkdirs()
            
            // Seed Sample Word
            createNewDocument("Project_Proposal_DocLite", DocumentFormat.WORD)
            // Seed Sample Excel
            createNewDocument("Quarterly_Budget_Report", DocumentFormat.EXCEL)
            // Seed Sample PowerPoint
            createNewDocument("DocLite_Feature_Deck", DocumentFormat.POWERPOINT)

            prefs.edit().putBoolean("samples_seeded", true).apply()
        }
    }
}

