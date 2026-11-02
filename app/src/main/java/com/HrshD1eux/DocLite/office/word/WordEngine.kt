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
import org.apache.poi.hwpf.HWPFDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
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
                        runs = listOf(TextRun(text = title.removeSuffix(".docx").removeSuffix(".DOCX"), style = TextStyle(isBold = true, fontSizeSp = 24f))),
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

            // Check if binary .doc format (Word 97-2003 OLE2)
            val isLegacyDoc = ext == "doc" || isOle2File(tempFile)
            if (isLegacyDoc) {
                return@withContext parseDocLegacy(tempFile, fileName, uri.toString())
            }

            // Attempt 1: High-speed, robust Native OpenXML streaming parser with internal fallback
            try {
                return@withContext parseDocxNativeXml(tempFile, fileName, uri.toString())
            } catch (t: Throwable) {
                Log.w(TAG, "Native OpenXML parser failed for $fileName (${t.message}). Attempting fallback extraction.", t)
            }

            // Attempt 2: Direct raw XML extraction from zip archive with regex recovery
            try {
                return@withContext extractAndParseDocxFallback(tempFile, fileName, uri.toString())
            } catch (t: Throwable) {
                Log.w(TAG, "Zip fallback parser failed for $fileName (${t.message}). Attempting POI fallback.", t)
            }

            // Attempt 3: Apache POI fallback with configured context ClassLoader
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

    private fun isOle2File(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                read == 8 &&
                    header[0] == 0xD0.toByte() &&
                    header[1] == 0xCF.toByte() &&
                    header[2] == 0x11.toByte() &&
                    header[3] == 0xE0.toByte() &&
                    header[4] == 0xA1.toByte() &&
                    header[5] == 0xB1.toByte() &&
                    header[6] == 0x1A.toByte() &&
                    header[7] == 0xE1.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseDocLegacy(file: File, fileName: String, uriString: String): WordDocument {
        // Attempt 1: HWPFDocument via poi-scratchpad
        try {
            file.inputStream().use { inStream ->
                val hwpf = HWPFDocument(inStream)
                val bodyElements = mutableListOf<WordBodyElement>()
                val range = hwpf.range
                val numParas = range.numParagraphs()
                var totalWords = 0
                var totalChars = 0

                for (i in 0 until numParas) {
                    val p = range.getParagraph(i)
                    val rawText = p.text().trimEnd('\r', '\n', '\u0007', '\u000c')
                    if (rawText.isBlank() && i > 0 && i == numParas - 1) continue

                    val runs = mutableListOf<TextRun>()
                    val numCharRuns = p.numCharacterRuns()
                    if (numCharRuns > 0) {
                        for (c in 0 until numCharRuns) {
                            val cr = p.getCharacterRun(c)
                            val crText = cr.text().trimEnd('\r', '\n', '\u0007', '\u000c')
                            if (crText.isNotEmpty()) {
                                runs.add(
                                    TextRun(
                                        text = crText,
                                        style = TextStyle(
                                            isBold = cr.isBold,
                                            isItalic = cr.isItalic,
                                            isUnderline = cr.underlineCode != 0,
                                            fontSizeSp = (cr.fontSize / 2f).takeIf { it > 0 } ?: 16f
                                        )
                                    )
                                )
                            }
                        }
                    }
                    if (runs.isEmpty()) {
                        runs.add(TextRun(rawText))
                    }

                    totalChars += rawText.length
                    totalWords += rawText.split("\\s+".toRegex()).count { it.isNotBlank() }

                    val styleIdx = p.styleIndex.toInt()
                    val isHeading = !p.isInTable && (styleIdx in 1..3 || rawText.startsWith("Chapter") || rawText.startsWith("Section"))
                    val headerLvl = if (isHeading) (styleIdx.takeIf { it in 1..3 } ?: 1) else 0

                    bodyElements.add(
                        WordBodyElement.ParagraphElement(
                            Paragraph(
                                runs = runs,
                                isHeader = isHeading,
                                headerLevel = headerLvl
                            )
                        )
                    )
                }

                val finalElements = bodyElements.ifEmpty {
                    listOf(WordBodyElement.ParagraphElement(Paragraph()))
                }

                return WordDocument(
                    title = fileName,
                    fileUri = uriString,
                    bodyElements = finalElements,
                    wordCount = totalWords,
                    characterCount = totalChars
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "HWPF failed to parse legacy doc: ${t.message}. Attempting OLE2 raw text extraction.", t)
        }

        // Attempt 2: OLE2 raw stream text extraction fallback
        return extractRawTextFromLegacyDoc(file, fileName, uriString)
    }

    private fun extractRawTextFromLegacyDoc(file: File, fileName: String, uriString: String): WordDocument {
        val paragraphs = mutableListOf<Paragraph>()
        try {
            val bytes = file.readBytes()
            val extractedLines = mutableListOf<String>()
            val sb = StringBuilder()

            // Try UTF-16LE scanning (standard in Word binary streams)
            var i = 0
            while (i < bytes.size - 1) {
                val b1 = bytes[i].toInt() and 0xFF
                val b2 = bytes[i + 1].toInt() and 0xFF
                val charVal = (b2 shl 8) or b1
                if (charVal in 0x0020..0x007E || charVal in 0x00A0..0x024F) {
                    sb.append(charVal.toChar())
                    i += 2
                } else if (charVal == 0x000D || charVal == 0x000A) {
                    if (sb.isNotBlank()) extractedLines.add(sb.toString().trim())
                    sb.setLength(0)
                    i += 2
                } else {
                    if (sb.length >= 3) {
                        extractedLines.add(sb.toString().trim())
                    }
                    sb.setLength(0)
                    i += 2
                }
            }
            if (sb.isNotBlank()) extractedLines.add(sb.toString().trim())

            // If UTF-16LE scanning yielded little, try ASCII scanning
            if (extractedLines.sumOf { it.length } < 40) {
                extractedLines.clear()
                sb.setLength(0)
                for (b in bytes) {
                    val ch = b.toInt() and 0xFF
                    if (ch in 32..126) {
                        sb.append(ch.toChar())
                    } else if (ch == 10 || ch == 13) {
                        if (sb.isNotBlank()) extractedLines.add(sb.toString().trim())
                        sb.setLength(0)
                    } else {
                        if (sb.length >= 3) extractedLines.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
                if (sb.isNotBlank()) extractedLines.add(sb.toString().trim())
            }

            for (line in extractedLines.filter { it.isNotBlank() }) {
                paragraphs.add(Paragraph(runs = listOf(TextRun(line))))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Raw text extraction from legacy doc failed", t)
        }

        val bodyElements = paragraphs.ifEmpty {
            listOf(Paragraph(runs = listOf(TextRun("Could not extract readable text from this legacy document."))))
        }.map { WordBodyElement.ParagraphElement(it) }

        return WordDocument(
            title = fileName,
            fileUri = uriString,
            bodyElements = bodyElements,
            wordCount = bodyElements.sumOf { it.paragraph.getPlainText().split("\\s+".toRegex()).count { w -> w.isNotBlank() } },
            characterCount = bodyElements.sumOf { it.paragraph.getPlainText().length }
        )
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

            val rawXml = zipFile.getInputStream(docEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val docXmlContent = rawXml.removePrefix("\uFEFF").trimStart()

            try {
                return parseDocXmlWithPullParser(zipFile, docXmlContent, fileName, uriString)
            } catch (t: Throwable) {
                Log.w(TAG, "XmlPullParser encountered an issue on $fileName (${t.message}), using regex fallback", t)
                return parseDocxFallbackRegex(docXmlContent, fileName, uriString)
            }
        } finally {
            try { zipFile?.close() } catch (ignored: Throwable) {}
        }
    }

    private fun parseDocXmlWithPullParser(
        zipFile: ZipFile,
        docXmlContent: String,
        fileName: String,
        uriString: String
    ): WordDocument {
        val sectPrXml = Regex("<w:sectPr[\\s\\S]*?</w:sectPr>|<w:sectPr[^>]*/>").find(docXmlContent)?.value

        // Parse relationships to map rId -> media path
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

        // Extract referenced images to cache directory
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
        var insideTextTag = false

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
                                        bulletPrefix = "\u2022 "
                                    }
                                }
                            }
                        }
                        "numPr" -> {
                            if (insideParagraph) {
                                bulletPrefix = "\u2022 "
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
                            insideTextTag = true
                        }
                        "tab" -> {
                            if (insideRun) {
                                if (insideTableCell) currentCellText.append("\t")
                                else currentRuns.add(TextRun(text = "\t", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                            }
                        }
                        "br" -> {
                            var isPageBreak = false
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i).substringAfterLast(':')
                                val attrVal = parser.getAttributeValue(i)
                                if (attrName.equals("type", ignoreCase = true) && attrVal.equals("page", ignoreCase = true)) {
                                    isPageBreak = true
                                    break
                                }
                            }
                            if (isPageBreak) {
                                pageNumberCounter++
                                bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                            } else if (insideRun) {
                                if (insideTableCell) currentCellText.append("\n")
                                else currentRuns.add(TextRun(text = "\n", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                            }
                        }
                        "drawing", "pict" -> {
                            val isDrawing = tag == "drawing"
                            var blipId: String? = null
                            var depth = 1
                            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                                if (parser.eventType == XmlPullParser.START_TAG) {
                                    if (!parser.isEmptyElementTag) depth++
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
                XmlPullParser.TEXT -> {
                    if (insideTextTag && insideRun) {
                        val text = parser.text ?: ""
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
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.substringAfterLast(':') ?: ""
                    when (tag) {
                        "pPr" -> insidePPr = false
                        "r" -> insideRun = false
                        "t" -> insideTextTag = false
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
    }

    private fun extractAndParseDocxFallback(docxFile: File, fileName: String, uriString: String): WordDocument {
        ZipInputStream(docxFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val normName = entry.name.replace('\\', '/').trimStart('/')
                if (normName.contains("document", ignoreCase = true) && normName.endsWith(".xml", ignoreCase = true)) {
                    val xmlContent = zis.bufferedReader(Charsets.UTF_8).readText().removePrefix("\uFEFF").trimStart()
                    return parseDocxFallbackRegex(xmlContent, fileName, uriString)
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("No document XML found in archive $fileName")
    }

    private fun parseDocxFallbackRegex(docXmlContent: String, fileName: String, uriString: String): WordDocument {
        val bodyElements = mutableListOf<WordBodyElement>()
        var totalWords = 0
        var totalChars = 0

        val sectPrXml = Regex("<w:sectPr[\\s\\S]*?</w:sectPr>|<w:sectPr[^>]*/>").find(docXmlContent)?.value

        val pRegex = Regex("<w:p[ >][\\s\\S]*?</w:p>")
        val tRegex = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
        val rRegex = Regex("<w:r[ >][\\s\\S]*?</w:r>")
        val bRegex = Regex("<w:b[ />]")
        val iRegex = Regex("<w:i[ />]")
        val uRegex = Regex("<w:u[ />]")
        val szRegex = Regex("<w:sz\\s+[^>]*w:val=\"(\\d+)\"")
        val colorRegex = Regex("<w:color\\s+[^>]*w:val=\"([A-Fa-f0-9]{6})\"")
        val headingRegex = Regex("<w:pStyle\\s+[^>]*w:val=\"(?:heading|Heading|Title)(\\d?)\"")
        val jcRegex = Regex("<w:jc\\s+[^>]*w:val=\"(\\w+)\"")

        val pMatches = pRegex.findAll(docXmlContent).toList()
        for (pMatch in pMatches) {
            val pXml = pMatch.value
            val runs = mutableListOf<TextRun>()

            val isHeading = headingRegex.containsMatchIn(pXml)
            val headingLvl = headingRegex.find(pXml)?.groupValues?.get(1)?.toIntOrNull() ?: if (isHeading) 1 else 0

            val jcVal = jcRegex.find(pXml)?.groupValues?.get(1)?.lowercase()
            val alignment = when (jcVal) {
                "center" -> TextAlignment.CENTER
                "right" -> TextAlignment.RIGHT
                "both" -> TextAlignment.JUSTIFY
                else -> TextAlignment.LEFT
            }

            val rMatches = rRegex.findAll(pXml).toList()
            if (rMatches.isNotEmpty()) {
                for (rMatch in rMatches) {
                    val rXml = rMatch.value
                    val isBold = bRegex.containsMatchIn(rXml) || (isHeading && headingLvl > 0)
                    val isItalic = iRegex.containsMatchIn(rXml)
                    val isUnderline = uRegex.containsMatchIn(rXml)
                    val szVal = szRegex.find(rXml)?.groupValues?.get(1)?.toFloatOrNull()
                    val fontSize = if (szVal != null && szVal > 0) szVal / 2f else if (isHeading) 20f else 16f
                    val colorVal = colorRegex.find(rXml)?.groupValues?.get(1)
                    val fontColor = if (colorVal != null) "#$colorVal" else "#1C1B1F"

                    val tMatches = tRegex.findAll(rXml)
                    for (tMatch in tMatches) {
                        val rawText = unescapeXml(tMatch.groupValues[1])
                        if (rawText.isNotEmpty()) {
                            runs.add(
                                TextRun(
                                    text = rawText,
                                    style = TextStyle(
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        isUnderline = isUnderline,
                                        fontSizeSp = fontSize,
                                        fontColorHex = fontColor
                                    )
                                )
                            )
                            totalChars += rawText.length
                            totalWords += rawText.split("\\s+".toRegex()).count { it.isNotBlank() }
                        }
                    }
                }
            } else {
                val tMatches = tRegex.findAll(pXml)
                for (tMatch in tMatches) {
                    val rawText = unescapeXml(tMatch.groupValues[1])
                    if (rawText.isNotEmpty()) {
                        runs.add(TextRun(text = rawText))
                        totalChars += rawText.length
                        totalWords += rawText.split("\\s+".toRegex()).count { it.isNotBlank() }
                    }
                }
            }

            if (runs.isNotEmpty() || bodyElements.isEmpty()) {
                bodyElements.add(
                    WordBodyElement.ParagraphElement(
                        Paragraph(
                            runs = runs.ifEmpty { listOf(TextRun("")) },
                            alignment = alignment,
                            isHeader = isHeading,
                            headerLevel = headingLvl
                        )
                    )
                )
            }
        }

        val finalElements = bodyElements.ifEmpty {
            listOf(WordBodyElement.ParagraphElement(Paragraph()))
        }

        return WordDocument(
            title = fileName,
            fileUri = uriString,
            bodyElements = finalElements,
            wordCount = totalWords,
            characterCount = totalChars,
            sectPrXml = sectPrXml
        )
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun loadViaPoi(tempFile: File, fileName: String, uri: Uri): WordDocument? {
        val pkg = try {
            OPCPackage.open(tempFile, PackageAccess.READ)
        } catch (t: Throwable) {
            return null
        }

        return try {
            pkg.use { opcPackage ->
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
                                    val colorHex = xwpfRun.color?.let { "#$it" } ?: "#1C1B1F"

                                    runs.add(
                                        TextRun(
                                            text = text,
                                            style = TextStyle(
                                                isBold = isBold,
                                                isItalic = isItalic,
                                                isUnderline = isUnderline,
                                                fontSizeSp = fontSize,
                                                fontColorHex = colorHex
                                            )
                                        )
                                    )
                                    charCount += text.length
                                    wordCount += text.split("\\s+".toRegex()).count { it.isNotBlank() }
                                }

                                val alignment = when (elem.alignment) {
                                    ParagraphAlignment.CENTER -> TextAlignment.CENTER
                                    ParagraphAlignment.RIGHT -> TextAlignment.RIGHT
                                    ParagraphAlignment.BOTH -> TextAlignment.JUSTIFY
                                    else -> TextAlignment.LEFT
                                }

                                val isHeader = elem.style?.startsWith("Heading", ignoreCase = true) == true
                                val headerLevel = if (isHeader) {
                                    elem.style?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                                } else 0

                                bodyElements.add(
                                    WordBodyElement.ParagraphElement(
                                        Paragraph(
                                            runs = runs.ifEmpty { listOf(TextRun("")) },
                                            alignment = alignment,
                                            isHeader = isHeader,
                                            headerLevel = headerLevel,
                                            bulletPrefix = if (elem.numFmt != null) "\u2022 " else null
                                        )
                                    )
                                )
                            }
                            is XWPFTable -> {
                                val rows = elem.rows.map { row ->
                                    WordTableRow(
                                        cells = row.tableCells.map { cell ->
                                            WordTableCell(
                                                text = cell.text ?: "",
                                                isHeader = false
                                            )
                                        }
                                    )
                                }
                                bodyElements.add(WordBodyElement.TableElement(WordTable(rows = rows)))
                            }
                        }
                    }

                    WordDocument(
                        title = fileName,
                        fileUri = uri.toString(),
                        bodyElements = bodyElements.ifEmpty { listOf(WordBodyElement.ParagraphElement(Paragraph())) },
                        wordCount = wordCount,
                        characterCount = charCount,
                        hasUnrecognizedElements = false
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "POI load encountered exception: ${t.message}")
            null
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

                        if (tempSource.length() > 0 && !isOle2File(tempSource)) {
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
        try {
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
                                val xwpfTable = xwpf.createTable(tableModel.rows.size, tableModel.rows[0].cells.size)
                                tableModel.rows.forEachIndexed { rIdx, row ->
                                    val xwpfRow = xwpfTable.getRow(rIdx)
                                    row.cells.forEachIndexed { cIdx, cell ->
                                        val xwpfCell = xwpfRow.getCell(cIdx) ?: xwpfRow.createCell()
                                        xwpfCell.text = cell.text
                                    }
                                }
                            }
                        }
                        is WordBodyElement.PageBreakElement -> {
                            val p = xwpf.createParagraph()
                            p.isPageBreak = true
                        }
                        is WordBodyElement.ImageElement -> {}
                    }
                }

                val outputStream = openOutputStreamRobust(uri)
                    ?: throw java.io.IOException("Could not open destination stream for $uri")
                outputStream.use { out ->
                    xwpf.write(out)
                }
                return true
            } finally {
                try { xwpf.close() } catch (ignored: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "saveDocumentViaPoi failed: ${t.message}", t)
            return false
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
                    context.contentResolver.openOutputStream(uri, "wt")
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
