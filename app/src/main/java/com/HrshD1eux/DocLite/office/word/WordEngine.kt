package com.HrshD1eux.DocLite.office.word

import android.content.Context
import android.net.Uri
import android.util.Log
import com.HrshD1eux.DocLite.core.utils.UriResolver
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
import kotlinx.coroutines.withTimeout
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
        withTimeout(15_000L) {
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
                    return@withTimeout parseDocLegacy(tempFile, fileName, uri.toString())
                }

                // Attempt 1: High-speed, robust Native OpenXML streaming parser with internal fallback
                try {
                    return@withTimeout parseDocxNativeXml(tempFile, fileName, uri.toString())
                } catch (t: Throwable) {
                    Log.w(TAG, "Native OpenXML parser failed for $fileName (${t.message}). Attempting fallback extraction.", t)
                }

                // Attempt 2: Direct raw XML extraction from zip archive with non-regex recovery
                try {
                    return@withTimeout extractAndParseDocxFallback(tempFile, fileName, uri.toString())
                } catch (t: Throwable) {
                    Log.w(TAG, "Zip fallback parser failed for $fileName (${t.message}). Attempting POI fallback.", t)
                }

                // Attempt 3: Apache POI fallback with configured context ClassLoader
                val originalClassLoader = Thread.currentThread().contextClassLoader
                try {
                    Thread.currentThread().contextClassLoader = WordEngine::class.java.classLoader
                    val doc = loadViaPoi(tempFile, fileName, uri)
                    if (doc != null) {
                        return@withTimeout doc
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

                // Extract embedded pictures from legacy doc if any
                try {
                    val pictures = hwpf.picturesTable?.allPictures
                    if (!pictures.isNullOrEmpty()) {
                        val mediaCacheDir = File(context.cacheDir, "word_media_legacy_${System.currentTimeMillis()}").apply { mkdirs() }
                        for (pic in pictures) {
                            try {
                                val ext = pic.suggestFileExtension() ?: "png"
                                val picFile = File(mediaCacheDir, "pic_${pic.hashCode()}.$ext")
                                picFile.outputStream().use { pic.writeImageContent(it) }
                                bodyElements.add(
                                    WordBodyElement.ImageElement(
                                        imageUri = Uri.fromFile(picFile).toString(),
                                        widthDp = 300,
                                        heightDp = 220
                                    )
                                )
                            } catch (ignored: Throwable) {}
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Legacy doc picture extraction skipped: ${t.message}")
                }

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
                Log.w(TAG, "XmlPullParser encountered an issue on $fileName (${t.message}), using fallback parser", t)
                return parseDocxFallbackParser(docXmlContent, fileName, uriString)
            }
        } finally {
            try { zipFile?.close() } catch (ignored: Throwable) {}
        }
    }

    private fun extractTopLevelRawBlocks(xml: String): List<Pair<String, String>> {
        val bStartIdx = xml.indexOf("<w:body>").takeIf { it != -1 } ?: xml.indexOf("<w:body ")
        if (bStartIdx == -1) return emptyList()
        val bStart = xml.indexOf('>', bStartIdx) + 1
        val bEnd = xml.lastIndexOf("</w:body>")
        if (bEnd == -1 || bEnd <= bStart) return emptyList()

        val blocks = mutableListOf<Pair<String, String>>()
        var pos = bStart
        val tagPattern = java.util.regex.Pattern.compile("<(w:p|w:tbl|w:sectPr)[ >]")

        while (pos < bEnd) {
            val matcher = tagPattern.matcher(xml)
            if (!matcher.find(pos) || matcher.start() >= bEnd) break
            val start = matcher.start()
            val tag = matcher.group(1) ?: break

            when (tag) {
                "w:p" -> {
                    val end = xml.indexOf("</w:p>", start)
                    if (end != -1 && end < bEnd) {
                        blocks.add("p" to xml.substring(start, end + 6))
                        pos = end + 6
                    } else {
                        pos = bEnd
                    }
                }
                "w:tbl" -> {
                    val end = xml.indexOf("</w:tbl>", start)
                    if (end != -1 && end < bEnd) {
                        blocks.add("tbl" to xml.substring(start, end + 8))
                        pos = end + 8
                    } else {
                        pos = bEnd
                    }
                }
                "w:sectPr" -> {
                    val end = xml.indexOf("</w:sectPr>", start)
                    if (end != -1 && end <= bEnd) {
                        blocks.add("sectPr" to xml.substring(start, end + 11))
                        pos = end + 11
                    } else {
                        val slashGt = xml.indexOf("/>", start)
                        if (slashGt != -1 && slashGt <= bEnd) {
                            blocks.add("sectPr" to xml.substring(start, slashGt + 2))
                            pos = slashGt + 2
                        } else {
                            pos = bEnd
                        }
                    }
                }
                else -> {
                    pos = matcher.end()
                }
            }
        }
        return blocks
    }

    private fun parseDocXmlWithPullParser(
        zipFile: ZipFile,
        docXmlContent: String,
        fileName: String,
        uriString: String
    ): WordDocument {
        val sectPrXml = extractSectPrXml(docXmlContent)

        val docTagStart = docXmlContent.indexOf("<w:document")
        val bodyTagStart = docXmlContent.indexOf("<w:body", if (docTagStart != -1) docTagStart else 0)
        val originalRootDocumentTag = if (docTagStart != -1 && bodyTagStart != -1) {
            docXmlContent.substring(docTagStart, bodyTagStart).trim()
        } else null

        val rawBlocks = extractTopLevelRawBlocks(docXmlContent)
        val rawParaBlocks = rawBlocks.filter { it.first == "p" }.map { it.second }
        val rawTableBlocks = rawBlocks.filter { it.first == "tbl" }.map { it.second }
        var topLevelParaIndex = 0
        var topLevelTableIndex = 0

        // Parse relationships to map rId -> media path
        val imageRelsMap = mutableMapOf<String, String>()
        val relsEntry = zipFile.getEntry("word/_rels/document.xml.rels")
            ?: zipFile.entries().asSequence().firstOrNull {
                it.name.replace('\\', '/').trimStart('/').equals("word/_rels/document.xml.rels", ignoreCase = true)
            }

        if (relsEntry != null) {
            try {
                zipFile.getInputStream(relsEntry).use { relsStream ->
                    val relsFactory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                    val relsParser = relsFactory.newPullParser()
                    relsParser.setInput(relsStream, "UTF-8")
                    var relEvent = relsParser.eventType
                    while (relEvent != XmlPullParser.END_DOCUMENT) {
                        if (relEvent == XmlPullParser.START_TAG) {
                            val tag = relsParser.name?.substringAfterLast(':') ?: ""
                            if (tag.equals("Relationship", ignoreCase = true)) {
                                var rId: String? = null
                                var type = ""
                                var target = ""
                                for (i in 0 until relsParser.attributeCount) {
                                    val attrName = relsParser.getAttributeName(i).substringAfterLast(':')
                                    val attrVal = relsParser.getAttributeValue(i) ?: ""
                                    if (attrName.equals("Id", ignoreCase = true)) rId = attrVal
                                    else if (attrName.equals("Type", ignoreCase = true)) type = attrVal
                                    else if (attrName.equals("Target", ignoreCase = true)) target = attrVal
                                }
                                if (type.contains("image", ignoreCase = true) && !rId.isNullOrBlank() && target.isNotBlank()) {
                                    val normalizedPath = when {
                                        target.contains("media/") -> "word/media/" + target.substringAfter("media/")
                                        target.startsWith("/word/") -> target.removePrefix("/")
                                        target.startsWith("word/") -> target
                                        else -> "word/media/" + target.substringAfterLast('/')
                                    }
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
                    val entryName = it.name.replace('\\', '/').trimStart('/')
                    entryName.equals(zipEntryPath, ignoreCase = true) ||
                    entryName.substringAfterLast('/').equals(zipEntryPath.substringAfterLast('/'), ignoreCase = true)
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
                    val uriStr = Uri.fromFile(cacheImgFile).toString()
                    rIdToCachedUri[rId] = uriStr
                    rIdToCachedUri[zipEntryPath.substringAfterLast('/')] = uriStr
                    rIdToCachedUri[zipEntryPath.substringAfterLast('/').substringBeforeLast('.')] = uriStr
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to cache media entry $zipEntryPath", t)
                }
            }
        }

        // Direct scan of all zip entries for media files so no images are missed
        try {
            val allEntries = zipFile.entries().asSequence().toList()
            for (ze in allEntries) {
                val nName = ze.name.replace('\\', '/').trimStart('/')
                if (nName.contains("media/", ignoreCase = true) && !ze.isDirectory) {
                    val fName = nName.substringAfterLast('/')
                    if (!rIdToCachedUri.containsKey(fName)) {
                        val fileExt = fName.substringAfterLast('.', "png")
                        val cacheImgFile = File(mediaCacheDir, "${UUID.randomUUID()}.$fileExt")
                        zipFile.getInputStream(ze).use { inS ->
                            cacheImgFile.outputStream().use { outS -> inS.copyTo(outS) }
                        }
                        val uriStr = Uri.fromFile(cacheImgFile).toString()
                        rIdToCachedUri[fName] = uriStr
                        rIdToCachedUri[fName.substringBeforeLast('.')] = uriStr
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Direct zip media scan exception", t)
        }

        val stylesMap = mutableMapOf<String, DocxStyleInfo>()
        val stylesEntry = zipFile.getEntry("word/styles.xml")
            ?: zipFile.entries().asSequence().firstOrNull { it.name.endsWith("styles.xml", ignoreCase = true) }
        if (stylesEntry != null) {
            try {
                val stylesXml = zipFile.getInputStream(stylesEntry).bufferedReader(Charsets.UTF_8).readText()
                parseDocxStyles(stylesXml, stylesMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse word/styles.xml", e)
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
        var currentTableHasBorders = true
        var insideTblBorders = false
        var nilBorderCount = 0
        var totalBorderCount = 0
        var tableHasPageBreakBefore = false
        var pSpacingBeforeDp: Float? = null
        var pSpacingAfterDp: Float? = null
        var currentRowIsHeader = false
        val currentTableRows = mutableListOf<WordTableRow>()
        val currentTableColWidths = mutableListOf<Int>()
        val currentCells = mutableListOf<WordTableCell>()
        val currentCellText = StringBuilder()
        var currentCellBgHex: String? = null
        var currentCellColorHex: String? = null
        var currentCellAlignment = TextAlignment.LEFT
        var currentCellBold = false

        var insideParagraph = false
        var paraAlignment = TextAlignment.LEFT
        var isHeader = false
        var headerLevel = 0
        var bulletPrefix: String? = null
        var hasPageBreakBefore = false
        var isStandalonePageBreak = false
        val currentRuns = mutableListOf<TextRun>()

        var insidePPr = false
        var pDefaultBold = false
        var pDefaultItalic = false
        var pDefaultUnderline = false
        var pDefaultFontSize = 12f  // Word default body text = 12pt
        var pDefaultColor = "#1C1B1F"

        var insideRun = false
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var fontSizeSp = 12f  // Word default = 12pt
        var fontColorHex = "#1C1B1F"
        var highlightColorHex: String? = null
        var insideTextTag = false
        var insideDrawing = false
        var currentDrawingBlipId: String? = null
        var currentDrawingWidthDp = 280
        var currentDrawingHeightDp = 200

        var pageNumberCounter = 1

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.substringAfterLast(':') ?: ""
                    when (tag) {
                        "tbl" -> {
                            insideTable = true
                            currentTableHasBorders = true  // default true for standard Word tables; set false when all borders are nil
                            insideTblBorders = false
                            nilBorderCount = 0
                            totalBorderCount = 0
                            currentTableRows.clear()
                            currentTableColWidths.clear()
                        }
                        "gridCol" -> {
                            if (insideTable && !insideTableCell) {
                                val wAttr = parser.getAttributeValue(null, "w")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).substringAfterLast(':').equals("w", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }
                                wAttr?.toIntOrNull()?.let { currentTableColWidths.add(it) }
                            }
                        }
                        "tblBorders" -> {
                            if (insideTable && !insideTableCell) {
                                insideTblBorders = true
                                nilBorderCount = 0
                                totalBorderCount = 0
                            }
                        }
                        "tblStyle" -> {
                            // TableGrid, TableNormal, and any style with "grid" or "table" in name imply borders
                            if (insideTable && !insideTableCell) {
                                val styleVal = getValAttr(parser)?.lowercase() ?: ""
                                if (styleVal.contains("grid") || styleVal == "tablegrid" || styleVal == "normaltable" ||
                                    styleVal.contains("bordered") || styleVal.contains("border")) {
                                    currentTableHasBorders = true
                                }
                            }
                        }
                        "top", "left", "bottom", "right", "insideH", "insideV" -> {
                            if (insideTblBorders) {
                                totalBorderCount++
                                val valAttr = getValAttr(parser)
                                if (valAttr != null && (valAttr.equals("nil", ignoreCase = true) || valAttr.equals("none", ignoreCase = true))) {
                                    nilBorderCount++
                                } else {
                                    // At least one actual border defined → this table has borders
                                    currentTableHasBorders = true
                                }
                            }
                        }
                        "tr" -> {
                            if (insideTable) {
                                insideTableRow = true
                                currentRowIsHeader = false
                                currentCells.clear()
                            }
                        }
                        "tblHeader" -> {
                            if (insideTableRow) {
                                currentRowIsHeader = true
                            }
                        }
                        "tc" -> {
                            if (insideTableRow) {
                                insideTableCell = true
                                currentCellText.clear()
                                currentCellBgHex = null
                                currentCellColorHex = null
                                currentCellAlignment = TextAlignment.LEFT
                                currentCellBold = false
                            }
                        }
                        "p" -> {
                            insideParagraph = true
                            paraAlignment = TextAlignment.LEFT
                            isHeader = false
                            headerLevel = 0
                            bulletPrefix = null
                            hasPageBreakBefore = false
                            isStandalonePageBreak = false
                            pSpacingBeforeDp = null
                            pSpacingAfterDp = null
                            currentRuns.clear()

                            insidePPr = false
                            pDefaultBold = false
                            pDefaultItalic = false
                            pDefaultUnderline = false
                            pDefaultFontSize = 12f  // Word default body text = 12pt
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
                        "spacing" -> {
                            if (insidePPr) {
                                val beforeTwips = parser.getAttributeValue(null, "before")?.toIntOrNull()
                                val afterTwips = parser.getAttributeValue(null, "after")?.toIntOrNull()
                                if (beforeTwips != null) pSpacingBeforeDp = (beforeTwips / 20f).coerceIn(0f, 48f)
                                if (afterTwips != null) pSpacingAfterDp = (afterTwips / 20f).coerceIn(0f, 48f)
                            }
                        }
                        "jc" -> {
                            val valAttr = getValAttr(parser)
                            val align = when (valAttr?.lowercase()) {
                                "center" -> TextAlignment.CENTER
                                "right" -> TextAlignment.RIGHT
                                "both" -> TextAlignment.JUSTIFY
                                else -> TextAlignment.LEFT
                            }
                            if (insideParagraph) {
                                paraAlignment = align
                            }
                            if (insideTableCell) {
                                currentCellAlignment = align
                            }
                        }
                        "pStyle" -> {
                            if (insideParagraph) {
                                val valAttr = getValAttr(parser)
                                if (valAttr != null) {
                                    val styleVal = valAttr.lowercase()
                                    val styleDef = stylesMap[valAttr] ?: stylesMap[styleVal]
                                    if (styleDef != null) {
                                        if (styleDef.isBold) pDefaultBold = true
                                        if (styleDef.isItalic) pDefaultItalic = true
                                        if (styleDef.fontSizeSp != null) pDefaultFontSize = styleDef.fontSizeSp
                                        if (styleDef.fontColorHex != null) pDefaultColor = styleDef.fontColorHex
                                        if (styleVal.contains("heading") || styleVal.contains("title")) {
                                            isHeader = true
                                            headerLevel = when {
                                                styleVal.contains("1") -> 1
                                                styleVal.contains("2") -> 2
                                                styleVal.contains("3") -> 3
                                                else -> 1
                                            }
                                        }
                                    } else if (styleVal.contains("heading") || styleVal.contains("title")) {
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
                                        pDefaultColor = when (headerLevel) {
                                            1 -> "#17365D"
                                            2 -> "#2E74B5"
                                            else -> "#4F81BD"
                                        }
                                    } else if (styleVal.contains("subtitle")) {
                                        pDefaultItalic = true
                                        pDefaultFontSize = 16f
                                        pDefaultColor = "#4F81BD"
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
                            if (insideTableCell && bVal) currentCellBold = true
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
                                if (insideRun) {
                                    fontSizeSp = sizeSp
                                    if (fontSizeSp >= 22f && fontColorHex == "#1C1B1F") fontColorHex = "#1F497D"
                                } else if (insidePPr) {
                                    pDefaultFontSize = sizeSp
                                    if (pDefaultFontSize >= 22f && pDefaultColor == "#1C1B1F") pDefaultColor = "#1F497D"
                                }
                            }
                        }
                        "shd" -> {
                            var fillAttr: String? = null
                            for (i in 0 until parser.attributeCount) {
                                val aName = parser.getAttributeName(i).substringAfterLast(':')
                                if (aName.equals("fill", ignoreCase = true)) {
                                    fillAttr = parser.getAttributeValue(i)
                                    break
                                }
                            }
                            if (fillAttr != null && fillAttr != "auto" && fillAttr != "none") {
                                val hex = if (fillAttr.startsWith("#")) fillAttr else "#$fillAttr"
                                if (insideTableCell) {
                                    currentCellBgHex = hex
                                }
                            }
                        }
                        "color" -> {
                            val valAttr = getValAttr(parser)
                            if (valAttr != null && valAttr != "auto") {
                                val colHex = if (valAttr.startsWith("#")) valAttr else "#$valAttr"
                                if (insideRun) fontColorHex = colHex else if (insidePPr) pDefaultColor = colHex
                                if (insideTableCell) currentCellColorHex = colHex
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
                            if (insideTable && currentTableRows.size <= 1) {
                                tableHasPageBreakBefore = true
                            }
                            if (insideParagraph) {
                                val prevIsBreak = bodyElements.lastOrNull() is WordBodyElement.PageBreakElement
                                if (!prevIsBreak && !hasPageBreakBefore) {
                                    if (currentRuns.isEmpty() || currentRuns.all { it.text.isBlank() }) {
                                        hasPageBreakBefore = true
                                    } else {
                                        val runsBefore = currentRuns.toList()
                                        currentRuns.clear()
                                        val prevParagraph = Paragraph(
                                            runs = runsBefore,
                                            alignment = paraAlignment,
                                            isHeader = isHeader,
                                            headerLevel = headerLevel,
                                            bulletPrefix = bulletPrefix
                                        )
                                        bodyElements.add(WordBodyElement.ParagraphElement(prevParagraph))
                                        pageNumberCounter++
                                        bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                    }
                                }
                            }
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
                                val prevIsBreak = bodyElements.lastOrNull() is WordBodyElement.PageBreakElement
                                if (!prevIsBreak) {
                                    if (insideParagraph) {
                                        if (currentRuns.isEmpty() || currentRuns.all { it.text.isBlank() }) {
                                            hasPageBreakBefore = true
                                            isStandalonePageBreak = true
                                        } else {
                                            val runsBefore = currentRuns.toList()
                                            currentRuns.clear()
                                            val prevParagraph = Paragraph(
                                                runs = runsBefore,
                                                alignment = paraAlignment,
                                                isHeader = isHeader,
                                                headerLevel = headerLevel,
                                                bulletPrefix = bulletPrefix
                                            )
                                            bodyElements.add(WordBodyElement.ParagraphElement(prevParagraph))
                                            pageNumberCounter++
                                            bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                        }
                                    } else {
                                        pageNumberCounter++
                                        bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                    }
                                }
                            } else {
                                if (insideTableCell) currentCellText.append("\n")
                                else if (insideRun) currentRuns.add(TextRun(text = "\n", style = TextStyle(isBold, isItalic, isUnderline, fontSizeSp, fontColorHex)))
                            }
                        }
                        "drawing", "pict" -> {
                            insideDrawing = true
                            currentDrawingBlipId = null
                            currentDrawingWidthDp = 280
                            currentDrawingHeightDp = 200
                        }
                        "blip", "imagedata" -> {
                            for (i in 0 until parser.attributeCount) {
                                val aName = parser.getAttributeName(i).substringAfterLast(':')
                                if (aName.equals("embed", ignoreCase = true) || aName.equals("id", ignoreCase = true) || aName.equals("href", ignoreCase = true)) {
                                    val valAttr = parser.getAttributeValue(i)
                                    if (!valAttr.isNullOrBlank()) {
                                        currentDrawingBlipId = valAttr
                                        break
                                    }
                                }
                            }
                        }
                        "extent" -> {
                            for (i in 0 until parser.attributeCount) {
                                val aName = parser.getAttributeName(i).substringAfterLast(':')
                                val aVal = parser.getAttributeValue(i)
                                if (aName.equals("cx", ignoreCase = true)) {
                                    val emu = aVal?.toLongOrNull()
                                    if (emu != null && emu > 0) currentDrawingWidthDp = (emu / 5715L).toInt().coerceIn(60, 360)
                                } else if (aName.equals("cy", ignoreCase = true)) {
                                    val emu = aVal?.toLongOrNull()
                                    if (emu != null && emu > 0) currentDrawingHeightDp = (emu / 5715L).toInt().coerceIn(40, 420)
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (text.isNotEmpty()) {
                        if (insideTableCell) {
                            currentCellText.append(text)
                        } else if (insideTextTag && insideRun) {
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
                        "drawing", "pict" -> {
                            if (insideDrawing) {
                                insideDrawing = false
                                val blipId = currentDrawingBlipId
                                if (blipId != null) {
                                    val cachedUri = rIdToCachedUri[blipId]
                                        ?: rIdToCachedUri[blipId.substringAfterLast('/')]
                                        ?: rIdToCachedUri[blipId.substringBeforeLast('.')]
                                        ?: rIdToCachedUri.values.firstOrNull()
                                    if (cachedUri != null) {
                                        val rawXml = extractDrawingXml(docXmlContent, blipId, tag == "drawing")
                                        bodyElements.add(
                                            WordBodyElement.ImageElement(
                                                imageUri = cachedUri,
                                                widthDp = currentDrawingWidthDp,
                                                heightDp = currentDrawingHeightDp,
                                                alignment = paraAlignment,
                                                rawDrawingXml = rawXml
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        "tblBorders" -> {
                            if (insideTblBorders) {
                                // If ALL declared borders are nil, explicitly mark as borderless
                                if (totalBorderCount > 0 && nilBorderCount == totalBorderCount) {
                                    currentTableHasBorders = false
                                }
                                insideTblBorders = false
                            }
                        }
                        "p" -> {
                            if (!insideTableCell && !insideTable) {
                                val prevIsBreak = bodyElements.lastOrNull() is WordBodyElement.PageBreakElement
                                if (isStandalonePageBreak && currentRuns.all { it.text.isBlank() }) {
                                    if (!prevIsBreak) {
                                        pageNumberCounter++
                                        bodyElements.add(WordBodyElement.PageBreakElement(pageNumber = pageNumberCounter))
                                    }
                                } else {
                                    val runs = if (currentRuns.isEmpty()) listOf(TextRun("")) else currentRuns.toList()
                                    val rawXml = rawParaBlocks.getOrNull(topLevelParaIndex++)
                                    val paragraph = Paragraph(
                                        runs = runs,
                                        alignment = paraAlignment,
                                        isHeader = isHeader,
                                        headerLevel = headerLevel,
                                        bulletPrefix = bulletPrefix,
                                        hasPageBreakBefore = if (prevIsBreak) false else hasPageBreakBefore,
                                        spacingBeforeDp = pSpacingBeforeDp,
                                        spacingAfterDp = pSpacingAfterDp,
                                        rawXml = rawXml
                                    )
                                    bodyElements.add(WordBodyElement.ParagraphElement(paragraph))
                                }
                            } else if (insideTableCell) {
                                if (currentCellText.isNotEmpty()) currentCellText.append("\n")
                            }
                            insideParagraph = false
                        }
                        "tc" -> {
                            if (insideTableRow) {
                                val isHeader = currentRowIsHeader
                                currentCells.add(
                                    WordTableCell(
                                        text = currentCellText.toString().trim(),
                                        isHeader = isHeader,
                                        backgroundColorHex = currentCellBgHex,
                                        textColorHex = currentCellColorHex,
                                        alignment = currentCellAlignment,
                                        isBold = currentCellBold || isHeader
                                    )
                                )
                                insideTableCell = false
                            }
                        }
                        "tr" -> {
                            if (insideTable) {
                                if (currentCells.isNotEmpty()) {
                                    currentTableRows.add(WordTableRow(cells = currentCells.toList()))
                                }
                                insideTableRow = false
                                currentRowIsHeader = false
                            }
                        }
                        "tbl" -> {
                            if (currentTableRows.isNotEmpty()) {
                                val rawXml = rawTableBlocks.getOrNull(topLevelTableIndex++)
                                val firstCellText = currentTableRows.firstOrNull()?.cells?.firstOrNull()?.text ?: ""
                                val isCodeBlock = currentTableRows.size == 1 &&
                                    currentTableRows[0].cells.size == 1 &&
                                    (firstCellText.contains("#include") ||
                                     firstCellText.lines().size > 3 ||
                                     firstCellText.contains("int main") ||
                                     firstCellText.contains("void "))
                                val finalRows = if (currentTableRows.size > 1 && currentTableRows[0].cells.size >= 3) {
                                    val row0 = currentTableRows[0]
                                    val newRow0 = WordTableRow(row0.cells.map { it.copy(isHeader = true) })
                                    listOf(newRow0) + currentTableRows.drop(1)
                                } else {
                                    currentTableRows.toList()
                                }
                                bodyElements.add(WordBodyElement.TableElement(WordTable(
                                    rows = finalRows,
                                    hasBorders = currentTableHasBorders,
                                    hasPageBreakBefore = tableHasPageBreakBefore,
                                    columnWidthsDxa = currentTableColWidths.toList(),
                                    isCodeBlock = isCodeBlock,
                                    rawXml = rawXml
                                )))
                            }
                            insideTable = false
                            tableHasPageBreakBefore = false
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
            sectPrXml = sectPrXml,
            originalXmlContent = docXmlContent,
            originalRootDocumentTag = originalRootDocumentTag
        )
    }

    private fun extractAndParseDocxFallback(docxFile: File, fileName: String, uriString: String): WordDocument {
        val mediaCacheDir = File(context.cacheDir, "word_media_fallback_${System.currentTimeMillis()}").apply { mkdirs() }
        val rIdToCachedUri = mutableMapOf<String, String>()
        var docXmlContent: String? = null

        ZipInputStream(docxFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val normName = entry.name.replace('\\', '/').trimStart('/')
                if (normName.contains("document", ignoreCase = true) && normName.endsWith(".xml", ignoreCase = true)) {
                    docXmlContent = zis.bufferedReader(Charsets.UTF_8).readText().removePrefix("\uFEFF").trimStart()
                } else if (normName.contains("media/", ignoreCase = true) && !entry.isDirectory) {
                    try {
                        val imgFile = File(mediaCacheDir, normName.substringAfterLast('/'))
                        imgFile.outputStream().use { outS -> zis.copyTo(outS) }
                        val uriStr = Uri.fromFile(imgFile).toString()
                        rIdToCachedUri[normName.substringAfterLast('/')] = uriStr
                        rIdToCachedUri[normName.substringAfterLast('/').substringBeforeLast('.')] = uriStr
                    } catch (ignored: Throwable) {}
                }
                entry = zis.nextEntry
            }
        }
        if (docXmlContent != null) {
            return parseDocxFallbackParser(docXmlContent!!, fileName, uriString, rIdToCachedUri)
        }
        throw IllegalArgumentException("No document XML found in archive $fileName")
    }

    private fun parseDocxFallbackParser(
        docXmlContent: String,
        fileName: String,
        uriString: String,
        rIdToCachedUri: Map<String, String> = emptyMap()
    ): WordDocument {
        val bodyElements = mutableListOf<WordBodyElement>()
        var totalWords = 0
        var totalChars = 0

        val sectPrXml = extractSectPrXml(docXmlContent)

        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(docXmlContent))

            var event = parser.eventType
            var insideP = false
            var insideR = false
            var insideT = false
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var fontSize = 16f
            var fontColor = "#1C1B1F"
            var highlightColor: String? = null
            val currentRuns = mutableListOf<TextRun>()
            var alignment = TextAlignment.LEFT
            var isHeading = false
            var headingLvl = 0
            var hasPageBreakBefore = false

            // Table parsing state
            var insideTable = false
            var insideTableRow = false
            var insideTableCell = false
            val currentTableRows = mutableListOf<WordTableRow>()
            val currentCells = mutableListOf<WordTableCell>()
            val currentCellText = StringBuilder()
            var currentCellBgHex: String? = null
            var currentCellColorHex: String? = null
            var currentCellAlignment = TextAlignment.LEFT
            var currentCellBold = false

            // Drawing parsing state
            var insideDrawing = false
            var currentDrawingBlipId: String? = null
            var currentDrawingWidthDp = 300
            var currentDrawingHeightDp = 200

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name?.substringAfterLast(':') ?: ""
                        when (name) {
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
                                    currentCellText.setLength(0)
                                    currentCellBgHex = null
                                    currentCellColorHex = null
                                    currentCellAlignment = TextAlignment.LEFT
                                    currentCellBold = false
                                }
                            }
                            "drawing", "pict" -> {
                                insideDrawing = true
                                currentDrawingBlipId = null
                                currentDrawingWidthDp = 300
                                currentDrawingHeightDp = 200
                            }
                            "blip", "imagedata" -> {
                                if (insideDrawing) {
                                    for (i in 0 until parser.attributeCount) {
                                        val attr = parser.getAttributeName(i).substringAfterLast(':')
                                        val v = parser.getAttributeValue(i)
                                        if (attr.equals("embed", ignoreCase = true) ||
                                            attr.equals("id", ignoreCase = true) ||
                                            attr.equals("href", ignoreCase = true)
                                        ) {
                                            currentDrawingBlipId = v
                                            break
                                        }
                                    }
                                }
                            }
                            "extent" -> {
                                if (insideDrawing) {
                                    val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                                    val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                                    if (cx != null && cx > 0) {
                                        currentDrawingWidthDp = (cx / 9525L).toInt().coerceIn(40, 800)
                                    }
                                    if (cy != null && cy > 0) {
                                        currentDrawingHeightDp = (cy / 9525L).toInt().coerceIn(40, 1200)
                                    }
                                }
                            }
                            "p" -> {
                                insideP = true
                                currentRuns.clear()
                                alignment = TextAlignment.LEFT
                                isHeading = false
                                headingLvl = 0
                                hasPageBreakBefore = false
                            }
                            "pageBreakBefore" -> {
                                if (insideP) hasPageBreakBefore = true
                            }
                            "br" -> {
                                val brType = parser.getAttributeValue(null, "type")
                                    ?: getValAttr(parser)
                                if (brType.equals("page", ignoreCase = true)) {
                                    bodyElements.add(WordBodyElement.PageBreakElement())
                                }
                            }
                            "jc" -> {
                                val v = getValAttr(parser)?.lowercase()
                                alignment = when (v) {
                                    "center" -> TextAlignment.CENTER
                                    "right" -> TextAlignment.RIGHT
                                    "both" -> TextAlignment.JUSTIFY
                                    else -> TextAlignment.LEFT
                                }
                                if (insideTableCell) currentCellAlignment = alignment
                            }
                            "pStyle" -> {
                                val v = getValAttr(parser)?.lowercase() ?: ""
                                if (v.contains("heading") || v.contains("title")) {
                                    isHeading = true
                                    headingLvl = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                                }
                            }
                            "r" -> {
                                insideR = true
                                isBold = isHeading
                                isItalic = false
                                isUnderline = false
                                fontSize = if (isHeading) 20f else 16f
                                fontColor = "#1C1B1F"
                                highlightColor = null
                            }
                            "b" -> {
                                isBold = true
                                if (insideTableCell) currentCellBold = true
                            }
                            "i" -> isItalic = true
                            "u" -> isUnderline = true
                            "sz" -> {
                                val szVal = getValAttr(parser)?.toFloatOrNull()
                                if (szVal != null && szVal > 0) fontSize = szVal / 2f
                            }
                            "shd" -> {
                                val fillAttr = parser.getAttributeValue(null, "fill") ?: getValAttr(parser)
                                if (fillAttr != null && fillAttr != "auto" && fillAttr != "none") {
                                    val hex = if (fillAttr.startsWith("#")) fillAttr else "#$fillAttr"
                                    if (insideTableCell) currentCellBgHex = hex
                                }
                            }
                            "color" -> {
                                val c = getValAttr(parser)
                                if (c != null && c != "auto") {
                                    val hex = if (c.startsWith("#")) c else "#$c"
                                    fontColor = hex
                                    if (insideTableCell) currentCellColorHex = hex
                                }
                            }
                            "highlight" -> {
                                val h = getValAttr(parser)
                                if (h != null && h != "none") highlightColor = "#FFFF00"
                            }
                            "t" -> insideT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text ?: ""
                        if (txt.isNotEmpty()) {
                            if (insideTableCell) {
                                currentCellText.append(txt)
                            } else if (insideP && insideR && insideT) {
                                currentRuns.add(
                                    TextRun(
                                        text = txt,
                                        style = TextStyle(
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            isUnderline = isUnderline,
                                            fontSizeSp = fontSize,
                                            fontColorHex = fontColor,
                                            highlightColorHex = highlightColor
                                        )
                                    )
                                )
                                totalChars += txt.length
                                totalWords += txt.split("\\s+".toRegex()).count { it.isNotBlank() }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name?.substringAfterLast(':') ?: ""
                        when (name) {
                            "t" -> insideT = false
                            "r" -> insideR = false
                            "drawing", "pict" -> {
                                if (insideDrawing) {
                                    insideDrawing = false
                                    val blipId = currentDrawingBlipId
                                    val cachedUri = if (blipId != null) {
                                        rIdToCachedUri[blipId]
                                            ?: rIdToCachedUri[blipId.substringAfterLast('/')]
                                            ?: rIdToCachedUri[blipId.substringBeforeLast('.')]
                                            ?: rIdToCachedUri.values.firstOrNull()
                                    } else rIdToCachedUri.values.firstOrNull()

                                    if (cachedUri != null) {
                                        bodyElements.add(
                                            WordBodyElement.ImageElement(
                                                imageUri = cachedUri,
                                                widthDp = currentDrawingWidthDp,
                                                heightDp = currentDrawingHeightDp
                                            )
                                        )
                                    }
                                }
                            }
                            "p" -> {
                                if (!insideTableCell && !insideTable) {
                                    val runs = if (currentRuns.isEmpty()) listOf(TextRun("")) else currentRuns.toList()
                                    bodyElements.add(
                                        WordBodyElement.ParagraphElement(
                                            Paragraph(
                                                runs = runs,
                                                alignment = alignment,
                                                isHeader = isHeading,
                                                headerLevel = headingLvl,
                                                hasPageBreakBefore = hasPageBreakBefore
                                            )
                                        )
                                    )
                                } else if (insideTableCell) {
                                    if (currentCellText.isNotEmpty()) currentCellText.append("\n")
                                }
                                insideP = false
                            }
                            "tc" -> {
                                if (insideTableRow) {
                                    val isHeader = currentTableRows.isEmpty() || (currentCellBgHex != null && !currentCellBgHex.equals("#FFFFFF", ignoreCase = true))
                                    currentCells.add(
                                        WordTableCell(
                                            text = currentCellText.toString().trim(),
                                            isHeader = isHeader,
                                            backgroundColorHex = currentCellBgHex,
                                            textColorHex = currentCellColorHex,
                                            alignment = currentCellAlignment,
                                            isBold = currentCellBold
                                        )
                                    )
                                    insideTableCell = false
                                }
                            }
                            "tr" -> {
                                if (insideTable) {
                                    if (currentCells.isNotEmpty()) {
                                        currentTableRows.add(WordTableRow(cells = currentCells.toList()))
                                    }
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
                event = parser.next()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Fallback XML pull parser failed, using raw text recovery", t)
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

    private fun extractSectPrXml(docXml: String): String? {
        val s1 = docXml.indexOf("<w:sectPr")
        val s2 = docXml.indexOf("<sectPr")
        val start = when {
            s1 != -1 && s2 != -1 -> minOf(s1, s2)
            s1 != -1 -> s1
            else -> s2
        }
        if (start == -1) return null

        val firstGt = docXml.indexOf('>', start)
        if (firstGt == -1) return null

        // Check if the sectPr tag itself is self-closing: <w:sectPr ... />
        if (docXml[firstGt - 1] == '/') {
            return docXml.substring(start, firstGt + 1)
        }

        val endTag1 = docXml.indexOf("</w:sectPr>", firstGt)
        val endTag2 = docXml.indexOf("</sectPr>", firstGt)
        val fullEndTag = when {
            endTag1 != -1 && endTag2 != -1 -> minOf(endTag1, endTag2)
            endTag1 != -1 -> endTag1
            else -> endTag2
        }
        if (fullEndTag != -1) {
            val closeLen = if (fullEndTag == endTag1) "</w:sectPr>".length else "</sectPr>".length
            return docXml.substring(start, fullEndTag + closeLen)
        }
        return null
    }

    private fun extractDrawingXml(docXml: String, blipId: String, isDrawing: Boolean): String? {
        val searchPattern = "=\"$blipId\""
        var idx = docXml.indexOf(searchPattern)
        while (idx != -1) {
            val tagStart = if (isDrawing) {
                val s1 = docXml.lastIndexOf("<w:drawing", idx)
                val s2 = docXml.lastIndexOf("<drawing", idx)
                maxOf(s1, s2)
            } else {
                val s1 = docXml.lastIndexOf("<w:pict", idx)
                val s2 = docXml.lastIndexOf("<pict", idx)
                maxOf(s1, s2)
            }
            if (tagStart != -1) {
                val endTag = if (isDrawing) "</w:drawing>" else "</w:pict>"
                val altEndTag = if (isDrawing) "</drawing>" else "</pict>"
                val e1 = docXml.indexOf(endTag, idx)
                val e2 = docXml.indexOf(altEndTag, idx)
                val endIdx = when {
                    e1 != -1 && e2 != -1 -> minOf(e1, e2)
                    e1 != -1 -> e1
                    else -> e2
                }
                if (endIdx != -1) {
                    val closeLen = if (endIdx == e1) endTag.length else altEndTag.length
                    return docXml.substring(tagStart, endIdx + closeLen)
                }
            }
            idx = docXml.indexOf(searchPattern, idx + searchPattern.length)
        }
        return null
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

    private data class DocxStyleInfo(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val fontSizeSp: Float? = null,
        val fontColorHex: String? = null
    )

    private fun parseDocxStyles(stylesXml: String, targetMap: MutableMap<String, DocxStyleInfo>) {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(stylesXml))
            var eventType = parser.eventType
            var currentStyleId: String? = null
            var inRPr = false
            var isBold = false
            var isItalic = false
            var fontSizeSp: Float? = null
            var fontColorHex: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.substringAfterLast(':') ?: ""
                        when (tag) {
                            "style" -> {
                                currentStyleId = null
                                inRPr = false
                                isBold = false
                                isItalic = false
                                fontSizeSp = null
                                fontColorHex = null
                                for (i in 0 until parser.attributeCount) {
                                    val aName = parser.getAttributeName(i).substringAfterLast(':')
                                    if (aName.equals("styleId", ignoreCase = true)) {
                                        currentStyleId = parser.getAttributeValue(i)
                                        break
                                    }
                                }
                            }
                            "rPr" -> inRPr = true
                            "b", "bCs" -> {
                                if (inRPr) {
                                    val v = getValAttr(parser)
                                    isBold = v == null || v == "1" || v.equals("true", ignoreCase = true)
                                }
                            }
                            "i", "iCs" -> {
                                if (inRPr) {
                                    val v = getValAttr(parser)
                                    isItalic = v == null || v == "1" || v.equals("true", ignoreCase = true)
                                }
                            }
                            "sz", "szCs" -> {
                                if (inRPr) {
                                    val v = getValAttr(parser)?.toFloatOrNull()
                                    if (v != null && v > 0) fontSizeSp = v / 2f
                                }
                            }
                            "color" -> {
                                if (inRPr) {
                                    val v = getValAttr(parser)
                                    if (v != null && v != "auto") {
                                        fontColorHex = if (v.startsWith("#")) v else "#$v"
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.substringAfterLast(':') ?: ""
                        when (tag) {
                            "rPr" -> inRPr = false
                            "style" -> {
                                val sId = currentStyleId
                                if (sId != null) {
                                    val info = DocxStyleInfo(isBold, isItalic, fontSizeSp, fontColorHex)
                                    targetMap[sId] = info
                                    targetMap[sId.lowercase()] = info
                                }
                                currentStyleId = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception parsing styles.xml", e)
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
        saveDocumentViaNativeXml(uri, document)
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

    private fun buildDocumentXml(document: WordDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        val rootTag = document.originalRootDocumentTag?.takeIf { it.startsWith("<w:document") }
            ?: """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:w10="urn:schemas-microsoft-com:office:word" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml" xmlns:w15="http://schemas.microsoft.com/office/word/2012/wordml">"""
        sb.append(rootTag)
        sb.append("<w:body>")

        for (elem in document.bodyElements) {
            when (elem) {
                is WordBodyElement.PageBreakElement -> {
                    sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
                is WordBodyElement.ParagraphElement -> {
                    val p = elem.paragraph
                    if (!p.rawXml.isNullOrBlank()) {
                        // NON-DESTRUCTIVE DELTA PRESERVATION: byte-for-byte exact original XML!
                        sb.append(p.rawXml)
                    } else {
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
                }
                is WordBodyElement.TableElement -> {
                    val table = elem.table
                    if (!table.rawXml.isNullOrBlank()) {
                        // NON-DESTRUCTIVE DELTA PRESERVATION: byte-for-byte exact original XML!
                        sb.append(table.rawXml)
                    } else {
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
                            val isHeaderRow = row.cells.any { it.isHeader }
                            sb.append("<w:tr>")
                            if (isHeaderRow) {
                                sb.append("<w:trPr><w:tblHeader/></w:trPr>")
                            }
                            for (cell in row.cells) {
                                sb.append("<w:tc>")
                                sb.append("<w:tcPr>")
                                sb.append("""<w:tcW w:w="$colWidthDxa" w:type="dxa"/>""")
                                if (cell.backgroundColorHex != null) {
                                    val fillHex = cell.backgroundColorHex.removePrefix("#")
                                    sb.append("""<w:shd w:val="clear" w:color="auto" w:fill="$fillHex"/>""")
                                }
                                sb.append("</w:tcPr>")
                                sb.append("<w:p>")
                                val jcAlign = when (cell.alignment) {
                                    TextAlignment.CENTER -> "center"
                                    TextAlignment.RIGHT -> "right"
                                    TextAlignment.JUSTIFY -> "both"
                                    TextAlignment.LEFT -> "left"
                                }
                                sb.append("<w:pPr>")
                                sb.append("""<w:jc w:val="$jcAlign"/>""")
                                sb.append("</w:pPr>")
                                sb.append("<w:r>")
                                val hasFormat = cell.isBold || cell.isHeader || cell.textColorHex != null
                                if (hasFormat) {
                                    sb.append("<w:rPr>")
                                    if (cell.isBold || cell.isHeader) sb.append("<w:b/>")
                                    if (cell.textColorHex != null) {
                                        sb.append("""<w:color w:val="${cell.textColorHex.removePrefix("#")}"/>""")
                                    }
                                    sb.append("</w:rPr>")
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
        return UriResolver.getDisplayName(context, uri, "document.docx")
    }

    private fun openInputStreamRobust(uri: Uri): InputStream? {
        return UriResolver.openInputStream(context, uri)
    }

    private fun openOutputStreamRobust(uri: Uri): OutputStream? {
        return UriResolver.openOutputStream(context, uri)
    }
}
