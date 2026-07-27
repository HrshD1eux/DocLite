package com.HrshD1eux.DocLite.repository

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.database.dao.PdfAnnotationDao
import com.HrshD1eux.DocLite.database.entity.PdfAnnotationEntity
import com.HrshD1eux.DocLite.models.PdfAnnotation
import com.HrshD1eux.DocLite.models.PresentationDocument
import com.HrshD1eux.DocLite.models.SpreadsheetDocument
import com.HrshD1eux.DocLite.models.WordDocument
import com.HrshD1eux.DocLite.office.excel.ExcelEngine
import com.HrshD1eux.DocLite.office.powerpoint.PowerPointEngine
import com.HrshD1eux.DocLite.office.pdf.PdfEngine
import com.HrshD1eux.DocLite.office.word.WordEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentRepository(
    private val context: Context,
    private val pdfAnnotationDao: PdfAnnotationDao
) {
    val wordEngine = WordEngine(context)
    val excelEngine = ExcelEngine(context)
    val powerPointEngine = PowerPointEngine(context)
    val pdfEngine = PdfEngine(context)

    suspend fun loadWordDocument(uri: Uri): Result<WordDocument> = withContext(Dispatchers.IO) {
        try {
            val doc = wordEngine.loadDocument(uri)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveWordDocument(uri: Uri, document: WordDocument): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = wordEngine.saveDocument(uri, document)
            if (success) Result.success(true) else Result.failure(Exception("Failed to save Word document"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadSpreadsheet(uri: Uri): Result<SpreadsheetDocument> = withContext(Dispatchers.IO) {
        try {
            val sheetDoc = excelEngine.loadSpreadsheet(uri)
            Result.success(sheetDoc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSpreadsheet(uri: Uri, document: SpreadsheetDocument): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = excelEngine.saveSpreadsheet(uri, document)
            if (success) Result.success(true) else Result.failure(Exception("Failed to save Spreadsheet"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadPresentation(uri: Uri): Result<PresentationDocument> = withContext(Dispatchers.IO) {
        try {
            val pptDoc = powerPointEngine.loadPresentation(uri)
            Result.success(pptDoc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePresentation(uri: Uri, document: PresentationDocument): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = powerPointEngine.savePresentation(uri, document)
            if (success) Result.success(true) else Result.failure(Exception("Failed to save Presentation"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPdfAnnotationsFlow(fileUri: String): Flow<List<PdfAnnotation>> {
        return pdfAnnotationDao.getAnnotationsForFile(fileUri).map { entities ->
            entities.map { entity ->
                PdfAnnotation(
                    id = entity.id,
                    fileUri = entity.fileUri,
                    pageIndex = entity.pageIndex,
                    type = com.HrshD1eux.DocLite.models.AnnotationType.valueOf(entity.annotationType),
                    colorHex = entity.colorHex,
                    strokeWidthDp = entity.strokeWidthDp,
                    noteText = entity.noteText,
                    signatureBitmapPath = entity.signatureBitmapPath,
                    boundsLeftRatio = entity.boundsLeftRatio,
                    boundsTopRatio = entity.boundsTopRatio,
                    boundsWidthRatio = entity.boundsWidthRatio,
                    boundsHeightRatio = entity.boundsHeightRatio,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    suspend fun savePdfAnnotation(annotation: PdfAnnotation) = withContext(Dispatchers.IO) {
        pdfAnnotationDao.insertAnnotation(
            PdfAnnotationEntity(
                id = annotation.id,
                fileUri = annotation.fileUri,
                pageIndex = annotation.pageIndex,
                annotationType = annotation.type.name,
                colorHex = annotation.colorHex,
                strokeWidthDp = annotation.strokeWidthDp,
                pointsJson = "",
                noteText = annotation.noteText,
                signatureBitmapPath = annotation.signatureBitmapPath,
                boundsLeftRatio = annotation.boundsLeftRatio,
                boundsTopRatio = annotation.boundsTopRatio,
                boundsWidthRatio = annotation.boundsWidthRatio,
                boundsHeightRatio = annotation.boundsHeightRatio,
                timestamp = annotation.timestamp
            )
        )
    }

    suspend fun deletePdfAnnotation(id: String) = withContext(Dispatchers.IO) {
        pdfAnnotationDao.deleteAnnotation(id)
    }
}

