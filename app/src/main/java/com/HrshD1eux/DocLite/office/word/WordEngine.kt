package com.HrshD1eux.DocLite.office.word

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.models.Paragraph
import com.HrshD1eux.DocLite.models.TextAlignment
import com.HrshD1eux.DocLite.models.TextRun
import com.HrshD1eux.DocLite.models.TextStyle
import com.HrshD1eux.DocLite.models.WordDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class WordEngine(private val context: Context) {

    suspend fun loadDocument(uri: Uri): WordDocument = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext == "doc") {
            throw UnsupportedOperationException("Legacy Word 97-2003 (.doc) format is not supported. Please convert to .docx.")
        }

        val tempFile = File.createTempFile("docx_cache_", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw java.io.FileNotFoundException("Could not open file: $fileName")

            val pkg = try {
                OPCPackage.open(tempFile, PackageAccess.READ)
            } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                throw IllegalArgumentException("Unsupported or corrupted Word document format. The file is not a valid .docx document.", e)
            } catch (e: org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException) {
                throw UnsupportedOperationException("Legacy Word binary format (.doc) is not supported. Please convert to .docx.", e)
            }

            pkg.use { opcPackage ->
                val document = try {
                    XWPFDocument(opcPackage)
                } catch (e: OutOfMemoryError) {
                    throw IllegalStateException("This Word document is too large to load in available device memory.", e)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Failed to open Word document: ${e.localizedMessage ?: "Corrupted file"}", e)
                }

                document.use { doc ->
                    val hasTables = doc.tables.isNotEmpty()
                    val hasPictures = doc.allPictures.isNotEmpty()
                    val hasUnrecognizedElements = hasTables || hasPictures || (doc.bodyElements.size > doc.paragraphs.size)

                    val paragraphs = mutableListOf<Paragraph>()
                    var wordCount = 0
                    var charCount = 0

                    for (xwpfParagraph in doc.paragraphs) {
                        val runs = mutableListOf<TextRun>()

                        for (xwpfRun in xwpfParagraph.runs) {
                            val text = xwpfRun.text() ?: continue
                            if (text.isEmpty()) continue

                            val isBold = xwpfRun.isBold
                            val isItalic = xwpfRun.isItalic
                            val isUnderline = xwpfRun.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
                            val fontSize = xwpfRun.fontSize.takeIf { it > 0 }?.toFloat() ?: 16f
                            val color = xwpfRun.color?.let { "#$it" } ?: "#1C1B1F"

                            runs.add(
                                TextRun(
                                    text = text,
                                    style = TextStyle(isBold, isItalic, isUnderline, fontSize, color)
                                )
                            )

                            charCount += text.length
                        }

                        val plainText = xwpfParagraph.text
                        if (plainText.isNotBlank()) {
                            wordCount += plainText.split("\\s+".toRegex()).count { it.isNotBlank() }
                        }

                        val alignment = when (xwpfParagraph.alignment) {
                            ParagraphAlignment.CENTER -> TextAlignment.CENTER
                            ParagraphAlignment.RIGHT -> TextAlignment.RIGHT
                            ParagraphAlignment.BOTH -> TextAlignment.JUSTIFY
                            else -> TextAlignment.LEFT
                        }

                        val style = xwpfParagraph.style ?: ""
                        val isHeader = style.contains("Heading")

                        paragraphs.add(
                            Paragraph(
                                runs = runs.ifEmpty { listOf(TextRun("")) },
                                alignment = alignment,
                                isHeader = isHeader
                            )
                        )
                    }

                    WordDocument(
                        title = fileName,
                        fileUri = uri.toString(),
                        paragraphs = paragraphs.ifEmpty { listOf(Paragraph()) },
                        wordCount = wordCount,
                        characterCount = charCount,
                        hasUnrecognizedElements = hasUnrecognizedElements
                    )
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun saveDocument(uri: Uri, document: WordDocument): Boolean = withContext(Dispatchers.IO) {
        if (document.hasUnrecognizedElements) {
            throw IllegalStateException("Cannot save: Document contains unsupported elements (tables, images, or special styles) that would be stripped.")
        }
        try {
            val xwpf = XWPFDocument()
            try {
                document.paragraphs.forEach { paraModel ->
                    val xwpfParagraph = xwpf.createParagraph()

                    xwpfParagraph.alignment = when (paraModel.alignment) {
                        TextAlignment.CENTER -> ParagraphAlignment.CENTER
                        TextAlignment.RIGHT -> ParagraphAlignment.RIGHT
                        TextAlignment.JUSTIFY -> ParagraphAlignment.BOTH
                        else -> ParagraphAlignment.LEFT
                    }

                    paraModel.runs.forEach { runModel ->
                        val xwpfRun = xwpfParagraph.createRun()
                        xwpfRun.setText(runModel.text)
                        xwpfRun.isBold = runModel.style.isBold
                        xwpfRun.isItalic = runModel.style.isItalic
                        if (runModel.style.isUnderline) {
                            xwpfRun.underline = org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                        }
                        xwpfRun.fontSize = runModel.style.fontSizeSp.toInt()

                        val colorHex = runModel.style.fontColorHex.removePrefix("#")
                        if (colorHex.length == 6) {
                            xwpfRun.setColor(colorHex)
                        }
                    }
                }

                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    xwpf.write(outputStream)
                } ?: return@withContext false
                true
            } finally {
                xwpf.close()
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document.docx"
    }
}
