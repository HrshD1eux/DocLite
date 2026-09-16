package com.HrshD1eux.DocLite.office.word

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.models.Paragraph
import com.HrshD1eux.DocLite.models.TextAlignment
import com.HrshD1eux.DocLite.models.TextRun
import com.HrshD1eux.DocLite.models.TextStyle
import com.HrshD1eux.DocLite.models.WordBodyElement
import com.HrshD1eux.DocLite.models.WordDocument
import com.HrshD1eux.DocLite.models.WordTable
import com.HrshD1eux.DocLite.models.WordTableCell
import com.HrshD1eux.DocLite.models.WordTableRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
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
            val stream = openInputStreamRobust(uri) ?: throw java.io.FileNotFoundException("Could not open file: $fileName")
            stream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalArgumentException("The Word document is empty or could not be read from storage.")
            }

            val pkg = try {
                OPCPackage.open(tempFile, PackageAccess.READ)
            } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                throw IllegalArgumentException("Unsupported or corrupted Word document format. The file is not a valid .docx document.", e)
            } catch (e: org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException) {
                throw UnsupportedOperationException("Legacy Word binary format (.doc) is not supported. Please convert to .docx.", e)
            } catch (t: Throwable) {
                val detail = t.localizedMessage ?: t.message ?: t::class.java.simpleName
                throw IllegalArgumentException("Could not read Word package: $detail", t)
            }

            pkg.use { opcPackage ->
                val document = try {
                    XWPFDocument(opcPackage)
                } catch (e: OutOfMemoryError) {
                    throw IllegalStateException("This Word document is too large to load in available device memory.", e)
                } catch (t: Throwable) {
                    throw IllegalArgumentException("Failed to open Word document: ${t.localizedMessage ?: "Corrupted or unsupported format"}", t)
                }

                document.use { doc ->
                    val hasPictures = try { doc.allPictures.isNotEmpty() } catch (t: Throwable) { false }

                    val paragraphs = mutableListOf<Paragraph>()
                    val tables = mutableListOf<WordTable>()
                    val bodyElements = mutableListOf<WordBodyElement>()
                    var wordCount = 0
                    var charCount = 0

                    val elements: List<IBodyElement> = try {
                        doc.bodyElements.ifEmpty { doc.paragraphs }
                    } catch (t: Throwable) {
                        doc.paragraphs
                    }

                    for (elem in elements) {
                        when (elem) {
                            is XWPFParagraph -> {
                                val runs = mutableListOf<TextRun>()

                                for (xwpfRun in elem.runs) {
                                    val text = try {
                                        val t = xwpfRun.text()
                                        if (!t.isNullOrEmpty()) t else xwpfRun.getText(0)
                                    } catch (t: Throwable) {
                                        null
                                    }
                                    if (text.isNullOrEmpty()) continue

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

                                val plainText = elem.text ?: ""
                                if (runs.isEmpty() && plainText.isNotBlank()) {
                                    runs.add(TextRun(text = plainText))
                                    charCount += plainText.length
                                }

                                if (plainText.isNotBlank()) {
                                    wordCount += plainText.split("\\s+".toRegex()).count { it.isNotBlank() }
                                }

                                val alignment = when (elem.alignment) {
                                    ParagraphAlignment.CENTER -> TextAlignment.CENTER
                                    ParagraphAlignment.RIGHT -> TextAlignment.RIGHT
                                    ParagraphAlignment.BOTH -> TextAlignment.JUSTIFY
                                    else -> TextAlignment.LEFT
                                }

                                val style = elem.style ?: ""
                                val isHeader = style.contains("Heading", ignoreCase = true) || style.contains("Title", ignoreCase = true)
                                val headerLevel = when {
                                    style.contains("1") -> 1
                                    style.contains("2") -> 2
                                    style.contains("3") -> 3
                                    isHeader -> 1
                                    else -> 0
                                }

                                val numId = try { elem.numID } catch (t: Throwable) { null }
                                val isBullet = numId != null || style.contains("bullet", ignoreCase = true) || style.contains("list", ignoreCase = true)
                                val bulletPrefix = if (isBullet) "• " else null

                                val paragraph = Paragraph(
                                    runs = runs.ifEmpty { listOf(TextRun("")) },
                                    alignment = alignment,
                                    isHeader = isHeader,
                                    headerLevel = headerLevel,
                                    bulletPrefix = bulletPrefix
                                )
                                paragraphs.add(paragraph)
                                bodyElements.add(WordBodyElement.ParagraphElement(paragraph))
                            }

                            is XWPFTable -> {
                                val tableRows = mutableListOf<WordTableRow>()
                                for (rIndex in elem.rows.indices) {
                                    val xwpfRow = elem.rows[rIndex]
                                    val cells = mutableListOf<WordTableCell>()
                                    for (xwpfCell in xwpfRow.tableCells) {
                                        val cellText = xwpfCell.text ?: ""
                                        cells.add(WordTableCell(text = cellText, isHeader = rIndex == 0))
                                        charCount += cellText.length
                                        if (cellText.isNotBlank()) {
                                            wordCount += cellText.split("\\s+".toRegex()).count { it.isNotBlank() }
                                        }
                                    }
                                    tableRows.add(WordTableRow(cells))
                                }
                                val wordTable = WordTable(rows = tableRows)
                                tables.add(wordTable)
                                bodyElements.add(WordBodyElement.TableElement(wordTable))
                            }
                        }
                    }

                    WordDocument(
                        title = fileName,
                        fileUri = uri.toString(),
                        paragraphs = paragraphs.ifEmpty { listOf(Paragraph()) },
                        tables = tables,
                        bodyElements = bodyElements.ifEmpty { paragraphs.map { WordBodyElement.ParagraphElement(it) } },
                        wordCount = wordCount,
                        characterCount = charCount,
                        hasUnrecognizedElements = hasPictures
                    )
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun saveDocument(uri: Uri, document: WordDocument): Boolean = withContext(Dispatchers.IO) {
        try {
            val xwpf = XWPFDocument()
            try {
                val elementsToSave = if (document.bodyElements.isNotEmpty()) {
                    document.bodyElements
                } else {
                    document.paragraphs.map { WordBodyElement.ParagraphElement(it) }
                }

                elementsToSave.forEach { bodyElem ->
                    when (bodyElem) {
                        is WordBodyElement.ParagraphElement -> {
                            val paraModel = bodyElem.paragraph
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
                        is WordBodyElement.TableElement -> {
                            val tableModel = bodyElem.table
                            if (tableModel.rows.isNotEmpty()) {
                                val numCols = tableModel.rows.first().cells.size.coerceAtLeast(1)
                                val xwpfTable = xwpf.createTable(tableModel.rows.size, numCols)
                                tableModel.rows.forEachIndexed { rIdx, row ->
                                    row.cells.forEachIndexed { cIdx, cell ->
                                        val rowObj = xwpfTable.getRow(rIdx) ?: xwpfTable.createRow()
                                        val cellObj = if (cIdx < rowObj.tableCells.size) rowObj.getCell(cIdx) else rowObj.addNewTableCell()
                                        cellObj.text = cell.text
                                    }
                                }
                            }
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
        } catch (t: Throwable) {
            t.printStackTrace()
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

    private fun openInputStreamRobust(uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists() && file.canRead()) java.io.FileInputStream(file)
                else context.contentResolver.openInputStream(uri)
            } else {
                context.contentResolver.openInputStream(uri) ?: uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists() && file.canRead()) java.io.FileInputStream(file) else null
                }
            }
        } catch (t: Throwable) {
            try {
                uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists() && file.canRead()) java.io.FileInputStream(file) else null
                }
            } catch (ignored: Throwable) {
                null
            }
        }
    }
}

