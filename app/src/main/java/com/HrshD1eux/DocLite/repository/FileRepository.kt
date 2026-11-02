package com.HrshD1eux.DocLite.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.HrshD1eux.DocLite.database.dao.FavoriteFileDao
import com.HrshD1eux.DocLite.database.dao.PasswordProtectionDao
import com.HrshD1eux.DocLite.database.dao.RecentFileDao
import com.HrshD1eux.DocLite.database.dao.TrashedFileDao
import com.HrshD1eux.DocLite.database.entity.TrashedFileEntity
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Color

class FileRepository(
    private val context: Context,
    private val recentFileDao: RecentFileDao,
    private val favoriteFileDao: FavoriteFileDao,
    private val passwordProtectionDao: PasswordProtectionDao,
    private val trashedFileDao: TrashedFileDao? = null
) {

    val trashedFilesFlow: kotlinx.coroutines.flow.Flow<List<TrashedFileEntity>> =
        trashedFileDao?.getAllTrashedFiles() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private val scanTriggerFlow = MutableStateFlow(System.currentTimeMillis())

    private fun isIgnoredFileOrDirectory(name: String, path: String? = null): Boolean {
        if (name.startsWith(".")) return true
        if (name.equals(".trashes", ignoreCase = true) || name.equals(".trash", ignoreCase = true)) return true
        if (name.contains("trashes", ignoreCase = true) || name.contains("trash", ignoreCase = true)) return true
        if (name.equals("Android", ignoreCase = true) || name.equals("data", ignoreCase = true) ||
            name.equals("lost+found", ignoreCase = true) || name.equals(".thumbnails", ignoreCase = true)) return true
        if (path != null) {
            val lowerPath = path.lowercase()
            if (lowerPath.contains("/.trash") || lowerPath.contains("/.trashes") ||
                lowerPath.contains("/trash/") || lowerPath.contains("/trashes/") ||
                lowerPath.contains("/android/data") || lowerPath.contains("/android/obb")) return true
        }
        return false
    }

    val protectedUrisFlow: Flow<List<String>> = passwordProtectionDao.getAllProtectedUris()

    val allDocumentsFlow: Flow<List<DocumentFile>> = combine(
        scanTriggerFlow,
        protectedUrisFlow
    ) { _, protectedUris ->
        val protectedSet = protectedUris.toSet()
        withContext(Dispatchers.IO) {
            val allFiles = scanAllDocuments()
            allFiles
                .filterNot { isIgnoredFileOrDirectory(it.name, it.path) }
                .map { file ->
                    file.copy(isPasswordProtected = protectedSet.contains(file.uriString))
                }
        }
    }

    val recentFilesFlow: Flow<List<DocumentFile>> = combine(
        recentFileDao.getAllRecentFiles(),
        protectedUrisFlow
    ) { entities, protectedUris ->
        val protectedSet = protectedUris.toSet()
        entities
            .filterNot { isIgnoredFileOrDirectory(it.name, it.path) }
            .map { entity ->
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
        entities
            .filterNot { isIgnoredFileOrDirectory(it.name, it.path) }
            .map { entity ->
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

    private fun createCleanInitialDocx(file: File, name: String) {
        val titleText = name.removeSuffix(".docx").removeSuffix(".DOCX")
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    <w:p>
      <w:pPr>
        <w:pStyle w:val="Heading1"/>
        <w:jc w:val="left"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="48"/>
          <w:color w:val="1F497D"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(titleText)}</w:t>
      </w:r>
    </w:p>
    <w:p>
      <w:pPr>
        <w:jc w:val="left"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="32"/>
          <w:color w:val="1C1B1F"/>
        </w:rPr>
        <w:t xml:space="preserve">Welcome to your new DocLite document. Start writing your content here.</w:t>
      </w:r>
    </w:p>
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
        <w:sz w:val="24"/>
        <w:color w:val="1C1B1F"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="48"/>
      <w:color w:val="1F497D"/>
    </w:rPr>
  </w:style>
</w:styles>"""

        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
            zip.write(rootRelsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/_rels/document.xml.rels"))
            zip.write(docRelsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/styles.xml"))
            zip.write(stylesXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(docXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun writeInitialDocumentContent(file: File, name: String, format: DocumentFormat) {
        file.parentFile?.mkdirs()
        try {
            when (format) {
                DocumentFormat.WORD -> {
                    createCleanInitialDocx(file, name)
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
            if (file.isFile && !file.isHidden && !isIgnoredFileOrDirectory(file.name, file.absolutePath)) {
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

        // 2. MediaStore Files Query (using DISPLAY_NAME, MIME_TYPE, and DATA)
        val projection = arrayOf(
            android.provider.MediaStore.Files.FileColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.SIZE,
            android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.MIME_TYPE,
            android.provider.MediaStore.MediaColumns.DATA
        )

        var selection = "(${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.docx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xlsx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.csv' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.ppt' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pptx' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.txt' OR " +
                "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.rtf' OR " +
                "${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} IN (" +
                "'application/pdf', 'application/msword', " +
                "'application/vnd.openxmlformats-officedocument.wordprocessingml.document', " +
                "'application/vnd.ms-excel', " +
                "'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', " +
                "'application/vnd.ms-powerpoint', " +
                "'application/vnd.openxmlformats-officedocument.presentationml.presentation', " +
                "'text/plain', 'text/csv'))"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selection += " AND (${android.provider.MediaStore.MediaColumns.IS_TRASHED} = 0)"
        }

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
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = try { cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA) } catch (t: Throwable) { -1 }

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val dataPath = if (dataCol != -1) try { cursor.getString(dataCol) } catch (t: Throwable) { null } else null
                    if (isIgnoredFileOrDirectory(name, dataPath)) continue

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!allowedExtensions.contains(ext)) continue

                    val id = cursor.getLong(idCol)
                    val contentUri = android.content.ContentUris.withAppendedId(queryUri, id)
                    val uriStr = contentUri.toString()
                    val size = cursor.getLong(sizeCol)
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    val format = DocumentFormat.fromExtension(ext)

                    foundFiles[uriStr] = DocumentFile(
                        id = uriStr,
                        name = name,
                        path = dataPath ?: uriStr,
                        uriString = uriStr,
                        sizeBytes = size,
                        lastModified = dateMod,
                        format = format,
                        isDirectory = false,
                        isPasswordProtected = protectedUris.contains(uriStr)
                    )
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("FileRepository", "MediaStore query failed: ${t.message}")
        }

        // 3. Direct Storage Scan if permissions granted (MANAGE_EXTERNAL_STORAGE on Android 11+ or READ_EXTERNAL_STORAGE on Android <= 10)
        val hasStorageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { Environment.isExternalStorageManager() } catch (t: Throwable) { false }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasStorageAccess) {
            fun scanDirectoryRecursively(dir: File, currentDepth: Int, maxDepth: Int = 3) {
                if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
                dir.listFiles()?.forEach { file ->
                    val fileName = file.name
                    val filePath = file.absolutePath
                    if (file.isHidden || isIgnoredFileOrDirectory(fileName, filePath)) return@forEach

                    if (file.isDirectory) {
                        scanDirectoryRecursively(file, currentDepth + 1, maxDepth)
                    } else if (file.isFile && file.canRead()) {
                        val ext = file.extension.lowercase()
                        if (allowedExtensions.contains(ext)) {
                            val cPath = try { file.canonicalPath } catch (t: Throwable) { file.absolutePath }
                            if (!foundFiles.containsKey(cPath)) {
                                val uriStr = Uri.fromFile(file).toString()
                                val format = DocumentFormat.fromExtension(ext)
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
            }

            try {
                // Documents & Downloads public folders
                val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val rootStorage = Environment.getExternalStorageDirectory()

                scanDirectoryRecursively(publicDocs, currentDepth = 0, maxDepth = 4)
                scanDirectoryRecursively(publicDownloads, currentDepth = 0, maxDepth = 4)
                scanDirectoryRecursively(rootStorage, currentDepth = 0, maxDepth = 2)
            } catch (t: Throwable) {
                android.util.Log.w("FileRepository", "Direct storage scan error: ${t.message}")
            }
        }

        // 4. App external documents directory
        val extDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        extDocsDir?.listFiles()?.forEach { file ->
            if (file.isFile && !file.isHidden && !isIgnoredFileOrDirectory(file.name, file.absolutePath)) {
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

        // 5. Persisted Storage Access Framework (SAF) documents
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
                                if (allowedExtensions.contains(ext) && !isIgnoredFileOrDirectory(name, uri.path)) {
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
        } catch (t: Throwable) {
            android.util.Log.w("FileRepository", "SAF query error: ${t.message}")
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

        val allowedExtensions = setOf(
            "doc", "docx", "txt", "rtf",
            "xls", "xlsx", "csv",
            "ppt", "pptx",
            "pdf"
        )

        targetDir.listFiles()?.forEach { file ->
            val fileName = file.name
            val filePath = file.absolutePath
            if (file.isHidden || isIgnoredFileOrDirectory(fileName, filePath)) return@forEach

            val ext = file.extension.lowercase()
            if (!file.isDirectory && !allowedExtensions.contains(ext)) return@forEach

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

    suspend fun moveToTrash(file: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            recentFileDao.deleteByUri(file.uriString)
            favoriteFileDao.removeFavorite(file.uriString)
            passwordProtectionDao.removePassword(file.uriString)

            val trashDir = File(context.filesDir, "trash").apply { if (!exists()) mkdirs() }
            val uniqueName = "${System.currentTimeMillis()}_${file.name}"
            val trashedTargetFile = File(trashDir, uniqueName)

            val localFile = File(file.path)
            var moved = false
            if (localFile.exists()) {
                moved = localFile.renameTo(trashedTargetFile)
                if (!moved) {
                    try {
                        localFile.inputStream().use { input ->
                            trashedTargetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        localFile.delete()
                        moved = true
                    } catch (t: Throwable) {
                        moved = false
                    }
                }
            } else {
                try {
                    val uri = Uri.parse(file.uriString)
                    val input = context.contentResolver.openInputStream(uri)
                    if (input != null) {
                        input.use { inStream ->
                            trashedTargetFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        try { context.contentResolver.delete(uri, null, null) } catch (ignored: Throwable) {}
                        moved = true
                    }
                } catch (t: Throwable) {
                    moved = false
                }
            }

            if (moved && trashedFileDao != null) {
                trashedFileDao.insertOrUpdate(
                    TrashedFileEntity(
                        uriString = file.uriString,
                        originalPath = file.path,
                        trashedPath = trashedTargetFile.absolutePath,
                        name = file.name,
                        sizeBytes = file.sizeBytes,
                        formatName = file.format.name,
                        trashedTimestamp = System.currentTimeMillis(),
                        expiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
                    )
                )
            }
            refreshScan()
            moved
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restoreFromTrash(trashed: TrashedFileEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashedFile = File(trashed.trashedPath)
            if (!trashedFile.exists()) {
                trashedFileDao?.deleteByUri(trashed.uriString)
                return@withContext false
            }

            var destFile = File(trashed.originalPath)
            if (destFile.parentFile == null || !destFile.parentFile!!.exists() || !destFile.parentFile!!.canWrite()) {
                val docsDir = File(context.filesDir, "DocLite_Documents").apply { if (!exists()) mkdirs() }
                destFile = File(docsDir, trashed.name)
            }

            var restored = trashedFile.renameTo(destFile)
            if (!restored) {
                try {
                    trashedFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    trashedFile.delete()
                    restored = true
                } catch (t: Throwable) {
                    restored = false
                }
            }

            if (restored) {
                trashedFileDao?.deleteByUri(trashed.uriString)
                val newUri = Uri.fromFile(destFile).toString()
                recentFileDao.insertOrUpdate(
                    RecentFileEntity(
                        uriString = newUri,
                        name = trashed.name,
                        path = destFile.absolutePath,
                        sizeBytes = destFile.length(),
                        formatName = trashed.formatName,
                        lastOpenedTimestamp = System.currentTimeMillis()
                    )
                )
                refreshScan()
            }
            restored
        } catch (e: Exception) {
            false
        }
    }

    suspend fun permanentlyDeleteTrashed(trashed: TrashedFileEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(trashed.trashedPath)
            if (f.exists()) f.delete()
            trashedFileDao?.deleteByUri(trashed.uriString)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashDir = File(context.filesDir, "trash")
            if (trashDir.exists()) {
                trashDir.listFiles()?.forEach { it.delete() }
            }
            trashedFileDao?.clearAll()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        try {
            val expired = trashedFileDao?.getExpiredFiles(System.currentTimeMillis()) ?: emptyList()
            var purged = 0
            for (t in expired) {
                val f = File(t.trashedPath)
                if (f.exists()) f.delete()
                trashedFileDao?.deleteByUri(t.uriString)
                purged++
            }
            purged
        } catch (e: Exception) {
            0
        }
    }

    suspend fun deleteFile(file: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        moveToTrash(file)
    }

    suspend fun permanentlyDeleteFile(file: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            recentFileDao.deleteByUri(file.uriString)
            favoriteFileDao.removeFavorite(file.uriString)
            passwordProtectionDao.removePassword(file.uriString)
            val localFile = File(file.path)
            val fileDeleted = if (localFile.exists()) {
                localFile.delete()
            } else {
                try {
                    val uri = Uri.parse(file.uriString)
                    if (uri.scheme == "content") {
                        context.contentResolver.delete(uri, null, null) > 0
                    } else true
                } catch (t: Throwable) {
                    false
                }
            }
            refreshScan()
            fileDeleted
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFiles(files: List<DocumentFile>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (file in files) {
            if (deleteFile(file)) {
                count++
            }
        }
        count
    }
}

