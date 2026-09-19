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
import android.util.Log
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class WordEngine(private val context: Context) {

    companion object {
        private const val TAG = "WordEngine"

        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        private const val DOC_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>"""
    }

    suspend fun loadDocument(uri: Uri): WordDocument = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext == "doc") {
            throw UnsupportedOperationException("Legacy Word 97-2003 (.doc) format is not supported. Please convert to .docx.")
        }

        val tempFile = File.createTempFile("docx_cache_", ".tmp", context.cacheDir)
        try {
            val stream = openInputStreamRobust(uri)
                ?: throw java.io.FileNotFoundException("Could not open file: $fileName")
            stream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalArgumentException("The Word document is empty or could not be read from storage.")
            }

            // Attempt 1: Apache POI with configured context ClassLoader
            val originalClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = WordEngine::class.java.classLoader
                val doc = loadViaPoi(tempFile, fileName, uri)
                if (doc != null) {
                    return@withContext doc
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Apache POI failed to load $fileName (${t.message}). Falling back to native OpenXML parser.", t)
            } finally {
                Thread.currentThread().contextClassLoader = originalClassLoader
            }

            // Attempt 2: High-speed Native OpenXML XML parser fallback
            // Completely independent of Apache POI / XMLBeans / desktop reflection
            try {
                return@withContext parseDocxNativeXml(tempFile, fileName, uri.toString())
            } catch (t: Throwable) {
                Log.e(TAG, "Native OpenXML parser also failed for $fileName", t)
                throw IllegalArgumentException("Unsupported or corrupted Word document format. Unable to parse $fileName.", t)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun loadViaPoi(tempFile: File, fileName: String, uri: Uri): WordDocument? {
        val pkg = try {
            OPCPackage.open(tempFile, PackageAccess.READ)
        } catch (t: Throwable) {
            return null
        }

        return pkg.use { opcPackage ->
            val document = XWPFDocument(opcPackage)
            document.use { doc ->
                val hasPictures = try { doc.allPictures.isNotEmpty() } catch (t: Throwable) { false }
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
                                } catch (t: Throwable) { null }
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
                            bodyElements.add(WordBodyElement.TableElement(wordTable))
                        }
                    }
                }

                val finalElements = bodyElements.ifEmpty {
                    listOf(WordBodyElement.ParagraphElement(Paragraph()))
                }

                WordDocument(
                    title = fileName,
                    fileUri = uri.toString(),
                    bodyElements = finalElements,
                    wordCount = wordCount,
                    characterCount = charCount,
                    hasUnrecognizedElements = hasPictures
                )
            }
        }
    }

    private fun parseDocxNativeXml(docxFile: File, fileName: String, uriString: String): WordDocument {
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(docxFile)
            val docEntry = zipFile.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("The archive is missing word/document.xml")

            val bodyElements = mutableListOf<WordBodyElement>()
            var wordCount = 0
            var charCount = 0
            var hasPictures = false

            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.name.startsWith("word/media/")) {
                    hasPictures = true
                    break
                }
            }

            zipFile.getInputStream(docEntry).use { inStream ->
                val factory = XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = true
                }
                val parser = factory.newPullParser()
                parser.setInput(inStream, "UTF-8")

                var eventType = parser.eventType
                var insideTable = false
                var insideTableRow = false
                var insideTableCell = false
                val currentTableRows = mutableListOf<WordTableRow>()
                val currentCells = mutableListOf<WordTableCell>()
                val currentCellText = StringBuilder()

                var insideParagraph = false
                var paraAlignment = TextAlignment.LEFT
                var isHeader = false
                var headerLevel = 0
                var bulletPrefix: String? = null
                val currentRuns = mutableListOf<TextRun>()

                var insideRun = false
                var isBold = false
                var isItalic = false
                var isUnderline = false
                var fontSizeSp = 16f
                var fontColorHex = "#1C1B1F"

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name?.substringAfterLast(':') ?: ""
                            when (tag) {
                                "tbl" -> {
                                    insideTable = true
                                    currentTableRows.clear()
                                }
                                "tr" -> {
                                    if (insideTable) {
                                        insideTableRow = true
                                        currentCells.clear()
                                    }
                                }
                                "tc" -> {
                                    if (insideTableRow) {
                                        insideTableCell = true
                                        currentCellText.clear()
                                    }
                                }
                                "p" -> {
                                    insideParagraph = true
                                    paraAlignment = TextAlignment.LEFT
                                    isHeader = false
                                    headerLevel = 0
                                    bulletPrefix = null
                                    currentRuns.clear()
                                }
                                "jc" -> {
                                    if (insideParagraph) {
                                        val valAttr = getValAttr(parser)
                                        paraAlignment = when (valAttr?.lowercase()) {
                                            "center" -> TextAlignment.CENTER
                                            "right" -> TextAlignment.RIGHT
                                            "both" -> TextAlignment.JUSTIFY
                                            else -> TextAlignment.LEFT
                                        }
                                    }
                                }
                                "pStyle" -> {
                                    if (insideParagraph) {
                                        val valAttr = getValAttr(parser)
                                        if (valAttr != null) {
                                            val styleVal = valAttr.lowercase()
                                            if (styleVal.contains("heading") || styleVal.contains("title")) {
                                                isHeader = true
                                                headerLevel = when {
                                                    styleVal.contains("1") -> 1
                                                    styleVal.contains("2") -> 2
                                                    styleVal.contains("3") -> 3
                                                    else -> 1
                                                }
                                            }
                                            if (styleVal.contains("bullet") || styleVal.contains("list")) {
                                                bulletPrefix = "• "
                                            }
                                        }
                                    }
                                }
                                "numPr" -> {
                                    if (insideParagraph) {
                                        bulletPrefix = "• "
                                    }
                                }
                                "r" -> {
                                    insideRun = true
                                    isBold = false
                                    isItalic = false
                                    isUnderline = false
                                    fontSizeSp = 16f
                                    fontColorHex = "#1C1B1F"
                                }
                                "b" -> {
                                    if (insideRun) {
                                        val valAttr = getValAttr(parser)
                                        isBold = valAttr == null || valAttr == "1" || valAttr.equals("true", ignoreCase = true)
                                    }
                                }
                                "i" -> {
                                    if (insideRun) {
                                        val valAttr = getValAttr(parser)
                                        isItalic = valAttr == null || valAttr == "1" || valAttr.equals("true", ignoreCase = true)
                                    }
                                }
                                "u" -> {
                                    if (insideRun) {
                                        val valAttr = getValAttr(parser)
                                        isUnderline = valAttr == null || valAttr != "none"
                                    }
                                }
                                "sz" -> {
                                    if (insideRun) {
                                        val valAttr = getValAttr(parser)
                                        val halfPts = valAttr?.toFloatOrNull()
                                        if (halfPts != null && halfPts > 0) {
                                            fontSizeSp = halfPts / 2f
                                        }
                                    }
                                }
                                "color" -> {
                                    if (insideRun) {
                                        val valAttr = getValAttr(parser)
                                        if (valAttr != null && valAttr != "auto") {
                                            fontColorHex = if (valAttr.startsWith("#")) valAttr else "#$valAttr"
                                        }
                                    }
                                }
                                "t" -> {
                                    if (insideRun) {
                                        val text = parser.nextText()
                                        if (text.isNotEmpty()) {
                                            if (insideTableCell) {
                                                if (currentCellText.isNotEmpty()) currentCellText.append(" ")
                                                currentCellText.append(text)
                                            } else {
                                                currentRuns.add(
                                                    TextRun(
                                                        text = text,
                                                        style = TextStyle(
                                                            isBold = isBold,
                                                            isItalic = isItalic,
                                                            isUnderline = isUnderline,
                                                            fontSizeSp = fontSizeSp,
                                                            fontColorHex = fontColorHex
                                                        )
                                                    )
                                                )
                                            }
                                            charCount += text.length
                                            wordCount += text.split("\\s+".toRegex()).count { it.isNotBlank() }
                                        }
                                    }
                                }
                                "tab" -> {
                                    if (insideRun) {
                                        if (insideTableCell) {
                                            currentCellText.append("\t")
                                        } else {
                                            currentRuns.add(TextRun(text = "\t", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                                        }
                                    }
                                }
                                "br" -> {
                                    if (insideRun) {
                                        if (insideTableCell) {
                                            currentCellText.append("\n")
                                        } else {
                                            currentRuns.add(TextRun(text = "\n", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                                        }
                                    }
                                }
                                "drawing", "pict" -> {
                                    hasPictures = true
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name?.substringAfterLast(':') ?: ""
                            when (tag) {
                                "r" -> insideRun = false
                                "p" -> {
                                    if (!insideTableCell) {
                                        val runs = if (currentRuns.isEmpty()) listOf(TextRun("")) else currentRuns.toList()
                                        val paragraph = Paragraph(
                                            runs = runs,
                                            alignment = paraAlignment,
                                            isHeader = isHeader,
                                            headerLevel = headerLevel,
                                            bulletPrefix = bulletPrefix
                                        )
                                        bodyElements.add(WordBodyElement.ParagraphElement(paragraph))
                                    }
                                    insideParagraph = false
                                }
                                "tc" -> {
                                    if (insideTableRow) {
                                        currentCells.add(WordTableCell(text = currentCellText.toString(), isHeader = currentTableRows.isEmpty()))
                                        insideTableCell = false
                                    }
                                }
                                "tr" -> {
                                    if (insideTable) {
                                        currentTableRows.add(WordTableRow(cells = currentCells.toList()))
                                        insideTableRow = false
                                    }
                                }
                                "tbl" -> {
                                    if (currentTableRows.isNotEmpty()) {
                                        bodyElements.add(WordBodyElement.TableElement(WordTable(rows = currentTableRows.toList())))
                                    }
                                    insideTable = false
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            val finalElements = bodyElements.ifEmpty {
                listOf(WordBodyElement.ParagraphElement(Paragraph()))
            }

            return WordDocument(
                title = fileName,
                fileUri = uriString,
                bodyElements = finalElements,
                wordCount = wordCount,
                characterCount = charCount,
                hasUnrecognizedElements = hasPictures
            )
        } finally {
            try {
                zipFile?.close()
            } catch (ignored: Throwable) {}
        }
    }

    private fun getValAttr(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i).substringAfterLast(':')
            if (name.equals("val", ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    suspend fun saveDocument(uri: Uri, document: WordDocument): Boolean = withContext(Dispatchers.IO) {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = WordEngine::class.java.classLoader
            saveDocumentViaPoi(uri, document)
        } catch (t: Throwable) {
            Log.w(TAG, "Apache POI save failed (${t.message}), falling back to native OpenXML serializer.", t)
            saveDocumentViaNativeXml(uri, document)
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    private fun saveDocumentViaPoi(uri: Uri, document: WordDocument): Boolean {
        val xwpf = XWPFDocument()
        try {
            document.bodyElements.forEach { bodyElem ->
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

            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Could not open output stream for saving.")
            outputStream.use { xwpf.write(it) }
            return true
        } finally {
            try {
                xwpf.close()
            } catch (ignored: Throwable) {}
        }
    }

    private fun saveDocumentViaNativeXml(uri: Uri, document: WordDocument): Boolean {
        val tempTarget = File.createTempFile("docx_save_", ".tmp", context.cacheDir)
        try {
            val docXmlContent = buildDocumentXml(document).toByteArray(Charsets.UTF_8)
            var existingZipCopied = false

            val existingStream = openInputStreamRobust(uri)
            if (existingStream != null) {
                try {
                    val tempSource = File.createTempFile("docx_src_", ".tmp", context.cacheDir)
                    try {
                        existingStream.use { inStream ->
                            tempSource.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }

                        if (tempSource.length() > 0) {
                            val zipFile = ZipFile(tempSource)
                            zipFile.use { zipIn ->
                                val zos = ZipOutputStream(tempTarget.outputStream())
                                zos.use { zipOut ->
                                    val entries = zipIn.entries()
                                    while (entries.hasMoreElements()) {
                                        val entry = entries.nextElement()
                                        if (entry.name != "word/document.xml") {
                                            val newEntry = ZipEntry(entry.name)
                                            zipOut.putNextEntry(newEntry)
                                            zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                                            zipOut.closeEntry()
                                        }
                                    }
                                    val docEntry = ZipEntry("word/document.xml")
                                    zipOut.putNextEntry(docEntry)
                                    zipOut.write(docXmlContent)
                                    zipOut.closeEntry()
                                }
                            }
                            existingZipCopied = true
                        }
                    } finally {
                        tempSource.delete()
                    }
                } catch (t: Throwable) {
                    existingZipCopied = false
                }
            }

            if (!existingZipCopied) {
                val zos = ZipOutputStream(tempTarget.outputStream())
                zos.use { zipOut ->
                    zipOut.putNextEntry(ZipEntry("[Content_Types].xml"))
                    zipOut.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("_rels/.rels"))
                    zipOut.write(ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("word/document.xml"))
                    zipOut.write(docXmlContent)
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                    zipOut.write(DOC_RELS_XML.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }

            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Could not open destination stream for $uri")
            out.use { dst ->
                tempTarget.inputStream().use { src ->
                    src.copyTo(dst)
                }
            }
            return true
        } finally {
            tempTarget.delete()
        }
    }

    private fun buildDocumentXml(document: WordDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """)
        sb.append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("<w:body>")

        for (elem in document.bodyElements) {
            when (elem) {
                is WordBodyElement.ParagraphElement -> {
                    val p = elem.paragraph
                    sb.append("<w:p>")
                    sb.append("<w:pPr>")
                    val jcVal = when (p.alignment) {
                        TextAlignment.CENTER -> "center"
                        TextAlignment.RIGHT -> "right"
                        TextAlignment.JUSTIFY -> "both"
                        TextAlignment.LEFT -> "left"
                    }
                    sb.append("""<w:jc w:val="$jcVal"/>""")
                    if (p.isHeader) {
                        val level = p.headerLevel.coerceIn(1, 3)
                        sb.append("""<w:pStyle w:val="Heading$level"/>""")
                    }
                    sb.append("</w:pPr>")

                    for (run in p.runs) {
                        sb.append("<w:r>")
                        sb.append("<w:rPr>")
                        if (run.style.isBold) sb.append("<w:b/>")
                        if (run.style.isItalic) sb.append("<w:i/>")
                        if (run.style.isUnderline) sb.append("""<w:u w:val="single"/>""")
                        val halfPts = (run.style.fontSizeSp * 2).toInt()
                        if (halfPts > 0) sb.append("""<w:sz w:val="$halfPts"/>""")
                        val colorHex = run.style.fontColorHex.removePrefix("#")
                        if (colorHex.length == 6) sb.append("""<w:color w:val="$colorHex"/>""")
                        sb.append("</w:rPr>")
                        sb.append("""<w:t xml:space="preserve">""")
                        sb.append(escapeXml(run.text))
                        sb.append("</w:t>")
                        sb.append("</w:r>")
                    }
                    sb.append("</w:p>")
                }
                is WordBodyElement.TableElement -> {
                    val table = elem.table
                    sb.append("<w:tbl>")
                    sb.append("<w:tblPr>")
                    sb.append("""<w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tblBorders>""")
                    sb.append("</w:tblPr>")
                    for (row in table.rows) {
                        sb.append("<w:tr>")
                        for (cell in row.cells) {
                            sb.append("<w:tc>")
                            sb.append("<w:p>")
                            sb.append("<w:r>")
                            sb.append("""<w:t xml:space="preserve">""")
                            sb.append(escapeXml(cell.text))
                            sb.append("</w:t>")
                            sb.append("</w:r>")
                            sb.append("</w:p>")
                            sb.append("</w:tc>")
                        }
                        sb.append("</w:tr>")
                    }
                    sb.append("</w:tbl>")
                }
            }
        }

        sb.append("""<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
        sb.append("</w:body>")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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
                context.contentResolver.openInputStream(uri)
            }
        } catch (t: Throwable) {
            null
        }
    }
}

