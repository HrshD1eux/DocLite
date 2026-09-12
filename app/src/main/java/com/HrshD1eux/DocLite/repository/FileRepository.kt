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
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Color

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
            val format = DocumentFormat.entries.firstOrNull { it.name.equals(entity.formatName, ignoreCase = true) }
                ?: DocumentFormat.fromExtension(entity.name.substringAfterLast('.', ""))
            DocumentFile(
                id = entity.uriString,
                name = entity.name,
                path = entity.path,
                uriString = entity.uriString,
                sizeBytes = entity.sizeBytes,
                lastModified = entity.lastOpenedTimestamp,
                format = format,
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
            val format = DocumentFormat.entries.firstOrNull { it.name.equals(entity.formatName, ignoreCase = true) }
                ?: DocumentFormat.fromExtension(entity.name.substringAfterLast('.', ""))
            DocumentFile(
                id = entity.uriString,
                name = entity.name,
                path = entity.path,
                uriString = entity.uriString,
                sizeBytes = entity.sizeBytes,
                lastModified = entity.addedTimestamp,
                format = format,
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
            writeInitialDocumentContent(file, name, format)
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

    private fun writeInitialDocumentContent(file: File, name: String, format: DocumentFormat) {
        file.parentFile?.mkdirs()
        try {
            when (format) {
                DocumentFormat.WORD -> {
                    val doc = XWPFDocument()
                    val p = doc.createParagraph()
                    val run = p.createRun()
                    run.setText("Welcome to your new DocLite document. Start writing your content here.")
                    file.outputStream().use { doc.write(it) }
                    doc.close()
                }
                DocumentFormat.EXCEL -> {
                    val wb = XSSFWorkbook()
                    val sheet = wb.createSheet("Sheet1")
                    val headerRow = sheet.createRow(0)
                    listOf("Item", "Quantity", "Price", "Total").forEachIndexed { idx, title ->
                        headerRow.createCell(idx).setCellValue(title)
                    }
                    val r1 = sheet.createRow(1)
                    r1.createCell(0).setCellValue("Product A")
                    r1.createCell(1).setCellValue(10.0)
                    r1.createCell(2).setCellValue(15.0)
                    r1.createCell(3).cellFormula = "B2*C2"

                    val r2 = sheet.createRow(2)
                    r2.createCell(0).setCellValue("Product B")
                    r2.createCell(1).setCellValue(5.0)
                    r2.createCell(2).setCellValue(25.0)
                    r2.createCell(3).cellFormula = "B3*C3"

                    file.outputStream().use { wb.write(it) }
                    wb.close()
                }
                DocumentFormat.POWERPOINT -> {
                    val ppt = XMLSlideShow()
                    val slide = ppt.createSlide()
                    val titleBox = slide.createTextBox()
                    titleBox.text = name
                    val bodyBox = slide.createTextBox()
                    bodyBox.text = "Welcome to your new DocLite presentation deck."
                    file.outputStream().use { ppt.write(it) }
                    ppt.close()
                }
                DocumentFormat.PDF -> {
                    val pdfDoc = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val paint = Paint().apply {
                        textSize = 16f
                        color = Color.BLACK
                    }
                    page.canvas.drawText(name, 50f, 80f, paint)
                    page.canvas.drawText("Generated by DocLite", 50f, 110f, paint)
                    pdfDoc.finishPage(page)
                    file.outputStream().use { pdfDoc.writeTo(it) }
                    pdfDoc.close()
                }
                DocumentFormat.IMAGE -> {
                    file.createNewFile()
                }
                DocumentFormat.TXT -> {
                    file.writeText("Welcome to your new DocLite document. Start writing your content here.\n", Charsets.UTF_8)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("FileRepository", "Failed to write document content: ${t.message}")
            // Fallback to empty file if document engine initialization fails
            if (!file.exists()) {
                try {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                } catch (ignored: Throwable) {}
            }
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

        // 3. App external documents directory (Scoped Storage compliant)
        val extDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        extDocsDir?.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (allowedExtensions.contains(ext)) {
                    val format = DocumentFormat.fromExtension(ext)
                    val uriStr = Uri.fromFile(file).toString()
                    val cPath = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                    if (!foundFiles.containsKey(cPath)) {
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

        // 4. Persisted Storage Access Framework (SAF) documents
        try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            for (pUri in persistedUris) {
                if (pUri.isReadPermission) {
                    val uri = pUri.uri
                    val uriStr = uri.toString()
                    if (!foundFiles.containsKey(uriStr)) {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                val name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "Document" else "Document"
                                val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                                val ext = name.substringAfterLast('.', "").lowercase()
                                if (allowedExtensions.contains(ext)) {
                                    val format = DocumentFormat.fromExtension(ext)
                                    foundFiles[uriStr] = DocumentFile(
                                        id = uriStr,
                                        name = name,
                                        path = uri.path ?: "",
                                        uriString = uriStr,
                                        sizeBytes = size,
                                        lastModified = System.currentTimeMillis(),
                                        format = format,
                                        isDirectory = false,
                                        isPasswordProtected = protectedUris.contains(uriStr)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                if (success) {
                    val oldUri = file.uriString
                    val newUri = Uri.fromFile(newFile).toString()

                    // Update Recent Files
                    recentFileDao.deleteByUri(oldUri)
                    recentFileDao.insertOrUpdate(
                        RecentFileEntity(
                            uriString = newUri,
                            name = newName,
                            path = newFile.absolutePath,
                            sizeBytes = newFile.length(),
                            formatName = file.format.name,
                            lastOpenedTimestamp = System.currentTimeMillis()
                        )
                    )

                    // Update Favorites
                    try {
                        val isFav = favoriteFileDao.isFavorite(oldUri).first()
                        if (isFav) {
                            favoriteFileDao.removeFavorite(oldUri)
                            favoriteFileDao.addFavorite(
                                FavoriteFileEntity(
                                    uriString = newUri,
                                    name = newName,
                                    path = newFile.absolutePath,
                                    sizeBytes = newFile.length(),
                                    formatName = file.format.name
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Non-critical favorite migration fallback
                    }

                    // Update Password Protection
                    if (file.isPasswordProtected) {
                        val hash = passwordProtectionDao.getPasswordHash(oldUri)
                        if (hash != null) {
                            passwordProtectionDao.removePassword(oldUri)
                            passwordProtectionDao.setPassword(
                                PasswordProtectionEntity(fileUri = newUri, passwordHash = hash)
                            )
                        }
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
            
            try {
                // Seed Sample Word
                createNewDocument("Project_Proposal_DocLite", DocumentFormat.WORD)
            } catch (t: Throwable) {
                android.util.Log.w("FileRepository", "Sample Word creation skipped: ${t.message}")
            }
            try {
                // Seed Sample Excel
                createNewDocument("Quarterly_Budget_Report", DocumentFormat.EXCEL)
            } catch (t: Throwable) {
                android.util.Log.w("FileRepository", "Sample Excel creation skipped: ${t.message}")
            }
            try {
                // Seed Sample PowerPoint
                createNewDocument("DocLite_Feature_Deck", DocumentFormat.POWERPOINT)
            } catch (t: Throwable) {
                android.util.Log.w("FileRepository", "Sample PowerPoint creation skipped: ${t.message}")
            }

            prefs.edit().putBoolean("samples_seeded", true).apply()
        }
    }
}

