package com.HrshD1eux.DocLite.office.word

import android.content.Context
import android.net.Uri
import android.util.Log
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
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.UUID
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
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        private const val DOC_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
      <w:sz w:val="36"/>
      <w:color w:val="1F497D"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="28"/>
      <w:color w:val="2E75B6"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="24"/>
      <w:color w:val="1F497D"/>
    </w:rPr>
  </w:style>
</w:styles>"""
    }

    suspend fun createDocument(title: String): WordDocument = withContext(Dispatchers.IO) {
        val fileName = if (title.endsWith(".docx", ignoreCase = true)) title else "$title.docx"
        val docFile = File(context.filesDir, "DocLite_Documents/$fileName")
        docFile.parentFile?.mkdirs()

        val initialDoc = WordDocument(
            title = fileName,
            fileUri = Uri.fromFile(docFile).toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(
                    Paragraph(
                        runs = listOf(TextRun(text = title.removeSuffix(".docx"), style = TextStyle(isBold = true, fontSizeSp = 24f))),
                        isHeader = true,
                        headerLevel = 1
                    )
                ),
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("Type your document text here...")))
                )
            )
        )

        saveDocument(Uri.fromFile(docFile), initialDoc)
        initialDoc
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

            // Attempt 1: High-speed, robust Native OpenXML streaming parser
            try {
                return@withContext parseDocxNativeXml(tempFile, fileName, uri.toString())
            } catch (t: Throwable) {
                Log.w(TAG, "Native OpenXML parser failed for $fileName (${t.message}). Attempting POI fallback.", t)
            }

            // Attempt 2: Apache POI fallback with configured context ClassLoader
            val originalClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = WordEngine::class.java.classLoader
                val doc = loadViaPoi(tempFile, fileName, uri)
                if (doc != null) {
                    return@withContext doc
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Apache POI also failed to load $fileName (${t.message})", t)
            } finally {
                Thread.currentThread().contextClassLoader = originalClassLoader
            }

            throw IllegalArgumentException("Unsupported or corrupted Word document format. Unable to parse $fileName.")
        } finally {
            tempFile.delete()
        }
    }

    private fun parseDocxNativeXml(docxFile: File, fileName: String, uriString: String): WordDocument {
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(docxFile)
            val docEntry = zipFile.getEntry("word/document.xml")
                ?: zipFile.entries().asSequence().firstOrNull {
                    it.name.replace('\\', '/').trimStart('/').equals("word/document.xml", ignoreCase = true)
                }
                ?: throw IllegalArgumentException("The archive is missing word/document.xml")

            // 1. Read document.xml as string
            val docXmlContent = zipFile.getInputStream(docEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }

            // 2. Extract sectPr if present
            val sectPrXml = Regex("<w:sectPr[\\s\\S]*?</w:sectPr>|<w:sectPr[^>]*/>").find(docXmlContent)?.value

            // 3. Parse relationships to map rId -> media path
            val imageRelsMap = mutableMapOf<String, String>()
            val relsEntry = zipFile.getEntry("word/_rels/document.xml.rels")
                ?: zipFile.entries().asSequence().firstOrNull {
                    it.name.replace('\\', '/').trimStart('/').equals("word/_rels/document.xml.rels", ignoreCase = true)
                }

            if (relsEntry != null) {
                try {
                    zipFile.getInputStream(relsEntry).use { relsStream ->
                        val relsFactory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                        val relsParser = relsFactory.newPullParser()
                        relsParser.setInput(relsStream, "UTF-8")
                        var relEvent = relsParser.eventType
                        while (relEvent != XmlPullParser.END_DOCUMENT) {
                            if (relEvent == XmlPullParser.START_TAG) {
                                val tag = relsParser.name?.substringAfterLast(':') ?: ""
                                if (tag.equals("Relationship", ignoreCase = true)) {
                                    val rId = relsParser.getAttributeValue(null, "Id")
                                    val type = relsParser.getAttributeValue(null, "Type") ?: ""
                                    val target = relsParser.getAttributeValue(null, "Target") ?: ""
                                    if (type.contains("image", ignoreCase = true) && rId != null && target.isNotBlank()) {
                                        val normalizedPath = if (target.startsWith("media/")) "word/$target"
                                        else if (target.startsWith("/word/")) target.removePrefix("/")
                                        else if (!target.startsWith("word/")) "word/$target"
                                        else target
                                        imageRelsMap[rId] = normalizedPath
                                    }
                                }
                            }
                            relEvent = relsParser.next()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error parsing document relationships", t)
                }
            }

            // 4. Extract referenced images to cache directory
            val mediaCacheDir = File(context.cacheDir, "word_media").apply { if (!exists()) mkdirs() }
            val rIdToCachedUri = mutableMapOf<String, String>()
            imageRelsMap.forEach { (rId, zipEntryPath) ->
                val entry = zipFile.getEntry(zipEntryPath)
                    ?: zipFile.entries().asSequence().firstOrNull {
                        it.name.replace('\\', '/').trimStart('/').equals(zipEntryPath, ignoreCase = true)
                    }
                if (entry != null) {
                    try {
                        val fileExt = zipEntryPath.substringAfterLast('.', "png")
                        val cacheImgFile = File(mediaCacheDir, "${UUID.randomUUID()}.$fileExt")
                        zipFile.getInputStream(entry).use { inS ->
                            cacheImgFile.outputStream().use { outS ->
                                inS.copyTo(outS)
                            }
                        }
                        rIdToCachedUri[rId] = Uri.fromFile(cacheImgFile).toString()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to cache media entry $zipEntryPath", t)
                    }
                }
            }

            // 5. Parse body elements
            val bodyElements = mutableListOf<WordBodyElement>()
            var wordCount = 0
            var charCount = 0

            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(docXmlContent))

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
            var hasPageBreakBefore = false
            val currentRuns = mutableListOf<TextRun>()

            // Default run styling from paragraph properties (<w:pPr><w:rPr>...</w:rPr></w:pPr>)
            var insidePPr = false
            var pDefaultBold = false
            var pDefaultItalic = false
            var pDefaultUnderline = false
            var pDefaultFontSize = 16f
            var pDefaultColor = "#1C1B1F"

            var insideRun = false
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var fontSizeSp = 16f
            var fontColorHex = "#1C1B1F"
            var highlightColorHex: String? = null

            var pageNumberCounter = 1

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
                                hasPageBreakBefore = false
                                currentRuns.clear()

                                insidePPr = false
                                pDefaultBold = false
                                pDefaultItalic = false
                                pDefaultUnderline = false
                                pDefaultFontSize = 16f
                                pDefaultColor = "#1C1B1F"
                            }
                            "pPr" -> {
                                insidePPr = true
                            }
                            "pageBreakBefore" -> {
                                if (insidePPr) {
                                    hasPageBreakBefore = true
                                }
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
                                            pDefaultBold = true
                                            pDefaultFontSize = when (headerLevel) {
                                                1 -> 24f
                                                2 -> 20f
                                                else -> 18f
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
                                isBold = pDefaultBold
                                isItalic = pDefaultItalic
                                isUnderline = pDefaultUnderline
                                fontSizeSp = pDefaultFontSize
                                fontColorHex = pDefaultColor
                                highlightColorHex = null
                            }
                            "rStyle" -> {
                                val valAttr = getValAttr(parser)?.lowercase() ?: ""
                                if (valAttr.contains("strong") || valAttr.contains("bold")) {
                                    if (insideRun) isBold = true else pDefaultBold = true
                                } else if (valAttr.contains("emphasis") || valAttr.contains("italic")) {
                                    if (insideRun) isItalic = true else pDefaultItalic = true
                                }
                            }
                            "b", "bCs" -> {
                                val valAttr = getValAttr(parser)
                                val bVal = valAttr == null || valAttr == "1" || valAttr.equals("true", ignoreCase = true)
                                if (insideRun) isBold = bVal else if (insidePPr) pDefaultBold = bVal
                            }
                            "i", "iCs" -> {
                                val valAttr = getValAttr(parser)
                                val iVal = valAttr == null || valAttr == "1" || valAttr.equals("true", ignoreCase = true)
                                if (insideRun) isItalic = iVal else if (insidePPr) pDefaultItalic = iVal
                            }
                            "u" -> {
                                val valAttr = getValAttr(parser)
                                val uVal = valAttr == null || valAttr != "none"
                                if (insideRun) isUnderline = uVal else if (insidePPr) pDefaultUnderline = uVal
                            }
                            "sz", "szCs" -> {
                                val valAttr = getValAttr(parser)
                                val halfPts = valAttr?.toFloatOrNull()
                                if (halfPts != null && halfPts > 0) {
                                    val sizeSp = halfPts / 2f
                                    if (insideRun) fontSizeSp = sizeSp else if (insidePPr) pDefaultFontSize = sizeSp
                                }
                            }
                            "color" -> {
                                val valAttr = getValAttr(parser)
                                if (valAttr != null && valAttr != "auto") {
                                    val colHex = if (valAttr.startsWith("#")) valAttr else "#$valAttr"
                                    if (insideRun) fontColorHex = colHex else if (insidePPr) pDefaultColor = colHex
                                }
                            }
                            "highlight" -> {
                                val valAttr = getValAttr(parser)
                                if (valAttr != null && valAttr != "none") {
                                    highlightColorHex = when (valAttr.lowercase()) {
                                        "yellow" -> "#FFF59D"
                                        "green" -> "#A5D6A7"
                                        "cyan" -> "#80DEEA"
                                        "magenta" -> "#F48FB1"
                                        "blue" -> "#90CAF9"
                                        "red" -> "#FFAB91"
                                        else -> "#FFF59D"
                                    }
                                }
                            }
                            "lastRenderedPageBreak" -> {
                                pageNumberCounter++
                                bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
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
                                                        fontColorHex = fontColorHex,
                                                        highlightColorHex = highlightColorHex
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
                                val typeAttr = parser.getAttributeValue(null, "type")
                                if (typeAttr.equals("page", ignoreCase = true)) {
                                    pageNumberCounter++
                                    bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                } else if (insideRun) {
                                    if (insideTableCell) {
                                        currentCellText.append("\n")
                                    } else {
                                        currentRuns.add(TextRun(text = "\n", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                                    }
                                }
                            }
                            "drawing", "pict" -> {
                                val isDrawing = tag == "drawing"
                                var blipId: String? = null
                                var depth = 1
                                while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                                    if (parser.eventType == XmlPullParser.START_TAG) {
                                        depth++
                                        val innerTag = parser.name?.substringAfterLast(':') ?: ""
                                        if (innerTag == "blip" || innerTag == "imagedata") {
                                            for (i in 0 until parser.attributeCount) {
                                                val attrName = parser.getAttributeName(i).substringAfterLast(':')
                                                if (attrName == "embed" || attrName == "id") {
                                                    blipId = parser.getAttributeValue(i)
                                                    break
                                                }
                                            }
                                        }
                                    } else if (parser.eventType == XmlPullParser.END_TAG) {
                                        depth--
                                    }
                                }

                                if (blipId != null && rIdToCachedUri.containsKey(blipId)) {
                                    val rawXml = if (isDrawing) {
                                        Regex("<w:drawing[\\s\\S]*?r:embed=\"${Regex.escape(blipId)}\"[\\s\\S]*?</w:drawing>").find(docXmlContent)?.value
                                    } else {
                                        Regex("<w:pict[\\s\\S]*?r:id=\"${Regex.escape(blipId)}\"[\\s\\S]*?</w:pict>").find(docXmlContent)?.value
                                    }
                                    val cachedUri = rIdToCachedUri[blipId]!!
                                    bodyElements.add(
                                        WordBodyElement.ImageElement(
                                            imageUri = cachedUri,
                                            rawDrawingXml = rawXml
                                        )
                                    )
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.substringAfterLast(':') ?: ""
                        when (tag) {
                            "pPr" -> insidePPr = false
                            "r" -> insideRun = false
                            "p" -> {
                                if (!insideTableCell) {
                                    val runs = if (currentRuns.isEmpty()) listOf(TextRun("")) else currentRuns.toList()
                                    val paragraph = Paragraph(
                                        runs = runs,
                                        alignment = paraAlignment,
                                        isHeader = isHeader,
                                        headerLevel = headerLevel,
                                        bulletPrefix = bulletPrefix,
                                        hasPageBreakBefore = hasPageBreakBefore
                                    )
                                    if (hasPageBreakBefore) {
                                        pageNumberCounter++
                                        bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                    }
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

            val finalElements = bodyElements.ifEmpty {
                listOf(WordBodyElement.ParagraphElement(Paragraph()))
            }

            return WordDocument(
                title = fileName,
                fileUri = uriString,
                bodyElements = finalElements,
                wordCount = wordCount,
                characterCount = charCount,
                hasUnrecognizedElements = false,
                sectPrXml = sectPrXml
            )
        } finally {
            try {
                zipFile?.close()
            } catch (ignored: Throwable) {}
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
                    hasUnrecognizedElements = false
                )
            }
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
        try {
            saveDocumentViaNativeXml(uri, document)
        } catch (t: Throwable) {
            Log.w(TAG, "Native OpenXML save encountered issue (${t.message}), attempting POI fallback.", t)
            val originalClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = WordEngine::class.java.classLoader
                saveDocumentViaPoi(uri, document)
            } catch (t2: Throwable) {
                Log.e(TAG, "Both native and POI save failed for $uri", t2)
                throw t2
            } finally {
                Thread.currentThread().contextClassLoader = originalClassLoader
            }
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
                                    val writtenEntries = mutableSetOf<String>()
                                    val entries = zipIn.entries()
                                    while (entries.hasMoreElements()) {
                                        val entry = entries.nextElement()
                                        val normName = entry.name.replace('\\', '/').trimStart('/')
                                        if (normName.equals("word/document.xml", ignoreCase = true)) {
                                            continue
                                        }
                                        if (!writtenEntries.add(normName)) {
                                            continue
                                        }

                                        val newEntry = ZipEntry(normName)
                                        zipOut.putNextEntry(newEntry)
                                        if (!entry.isDirectory) {
                                            zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                                        }
                                        zipOut.closeEntry()
                                    }

                                    // Write updated document.xml
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
                    Log.w(TAG, "Could not reuse existing docx ZIP entries, generating clean OpenXML package.", t)
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

                    zipOut.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                    zipOut.write(DOC_RELS_XML.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("word/styles.xml"))
                    zipOut.write(STYLES_XML.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("word/document.xml"))
                    zipOut.write(docXmlContent)
                    zipOut.closeEntry()
                }
            }

            val out = openOutputStreamRobust(uri)
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
                                val rowObj = xwpfTable.getRow(rIdx) ?: xwpfTable.createRow()
                                row.cells.forEachIndexed { cIdx, cell ->
                                    val cellObj = if (cIdx < rowObj.tableCells.size) rowObj.getCell(cIdx) else rowObj.addNewTableCell()
                                    cellObj.text = cell.text
                                }
                            }
                        }
                    }
                    is WordBodyElement.ImageElement -> {
                        // Handled natively by XML serializer
                    }
                    is WordBodyElement.PageBreakElement -> {
                        val p = xwpf.createParagraph()
                        p.createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE)
                    }
                }
            }

            val outputStream = openOutputStreamRobust(uri)
                ?: throw java.io.IOException("Could not open output stream for saving.")
            outputStream.use { xwpf.write(it) }
            return true
        } finally {
            try {
                xwpf.close()
            } catch (ignored: Throwable) {}
        }
    }

    private fun buildDocumentXml(document: WordDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document """)
        sb.append("""xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """)
        sb.append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """)
        sb.append("""xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math" """)
        sb.append("""xmlns:v="urn:schemas-microsoft-com:vml" """)
        sb.append("""xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """)
        sb.append("""xmlns:w10="urn:schemas-microsoft-com:office:word" """)
        sb.append("""xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """)
        sb.append("""xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
        sb.append("<w:body>")

        for (elem in document.bodyElements) {
            when (elem) {
                is WordBodyElement.PageBreakElement -> {
                    sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
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

                    if (p.hasPageBreakBefore) {
                        sb.append("<w:pageBreakBefore/>")
                    }

                    // Enforce at most one w:pStyle per OpenXML specification
                    if (p.isHeader) {
                        val level = p.headerLevel.coerceIn(1, 3)
                        sb.append("""<w:pStyle w:val="Heading$level"/>""")
                    } else if (p.bulletPrefix != null) {
                        sb.append("""<w:pStyle w:val="ListParagraph"/>""")
                    }
                    sb.append("</w:pPr>")

                    for (run in p.runs) {
                        if (run.rawXml != null) {
                            sb.append(run.rawXml)
                        } else {
                            sb.append("<w:r>")
                            sb.append("<w:rPr>")
                            if (run.style.isBold || (p.isHeader && !run.style.isBold)) sb.append("<w:b/>")
                            if (run.style.isItalic) sb.append("<w:i/>")
                            if (run.style.isUnderline) sb.append("""<w:u w:val="single"/>""")
                            val effectiveFontSize = if (p.isHeader) {
                                when (p.headerLevel) {
                                    1 -> 24f
                                    2 -> 20f
                                    else -> 18f
                                }
                            } else run.style.fontSizeSp
                            val halfPts = (effectiveFontSize * 2).toInt()
                            if (halfPts > 0) sb.append("""<w:sz w:val="$halfPts"/>""")
                            val colorHex = run.style.fontColorHex.removePrefix("#")
                            if (colorHex.length == 6) sb.append("""<w:color w:val="$colorHex"/>""")
                            if (run.style.highlightColorHex != null) {
                                sb.append("""<w:highlight w:val="yellow"/>""")
                            }
                            sb.append("</w:rPr>")
                            sb.append("""<w:t xml:space="preserve">""")
                            sb.append(escapeXml(run.text))
                            sb.append("</w:t>")
                            sb.append("</w:r>")
                        }
                    }
                    sb.append("</w:p>")
                }
                is WordBodyElement.TableElement -> {
                    val table = elem.table
                    val numCols = table.rows.maxOfOrNull { it.cells.size }?.coerceAtLeast(1) ?: 1
                    val colWidthDxa = (9000 / numCols).coerceAtLeast(720)

                    sb.append("<w:tbl>")
                    sb.append("<w:tblPr>")
                    sb.append("""<w:tblW w:w="0" w:type="auto"/>""")
                    sb.append("""<w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tblBorders>""")
                    sb.append("</w:tblPr>")

                    sb.append("<w:tblGrid>")
                    for (c in 0 until numCols) {
                        sb.append("""<w:gridCol w:w="$colWidthDxa"/>""")
                    }
                    sb.append("</w:tblGrid>")

                    for (row in table.rows) {
                        sb.append("<w:tr>")
                        for (cell in row.cells) {
                            sb.append("<w:tc>")
                            sb.append("<w:tcPr>")
                            sb.append("""<w:tcW w:w="$colWidthDxa" w:type="dxa"/>""")
                            sb.append("</w:tcPr>")
                            sb.append("<w:p>")
                            sb.append("<w:r>")
                            if (cell.isHeader) {
                                sb.append("<w:rPr><w:b/></w:rPr>")
                            }
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
                is WordBodyElement.ImageElement -> {
                    if (!elem.rawDrawingXml.isNullOrBlank()) {
                        sb.append("<w:p><w:r>")
                        sb.append(elem.rawDrawingXml)
                        sb.append("</w:r></w:p>")
                    }
                }
            }
        }

        val rawSectPr = document.sectPrXml?.trim()
        if (!rawSectPr.isNullOrBlank() && (rawSectPr.endsWith("</w:sectPr>") || rawSectPr.endsWith("/>"))) {
            sb.append(rawSectPr)
        } else {
            sb.append("""<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
        }
        sb.append("</w:body>")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        val sanitized = buildString(text.length) {
            for (ch in text) {
                // Strip control characters illegal in XML 1.0 (permits tab \t, lf \n, cr \r)
                if (ch == '\t' || ch == '\n' || ch == '\r' || (ch in ' '..'\uD7FF') || (ch in '\uE000'..'\uFFFD')) {
                    append(ch)
                }
            }
        }
        return sanitized
            .replace("&", "&amp;")
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

    private fun openOutputStreamRobust(uri: Uri): OutputStream? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                file.parentFile?.mkdirs()
                java.io.FileOutputStream(file)
            } else {
                try {
                    context.contentResolver.openOutputStream(uri, "w")
                } catch (e1: Exception) {
                    try {
                        context.contentResolver.openOutputStream(uri, "rwt")
                    } catch (e2: Exception) {
                        context.contentResolver.openOutputStream(uri)
                    }
                }
            }
        } catch (t: Throwable) {
            null
        }
    }
}
