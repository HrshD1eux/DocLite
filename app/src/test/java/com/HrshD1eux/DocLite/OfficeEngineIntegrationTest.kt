package com.HrshD1eux.DocLite

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.HrshD1eux.DocLite.database.AppDatabase
import com.HrshD1eux.DocLite.models.Cell
import com.HrshD1eux.DocLite.models.CellFormat
import com.HrshD1eux.DocLite.models.NumberFormat
import com.HrshD1eux.DocLite.models.Paragraph
import com.HrshD1eux.DocLite.models.Sheet
import com.HrshD1eux.DocLite.models.SpreadsheetDocument
import com.HrshD1eux.DocLite.models.TextRun
import com.HrshD1eux.DocLite.models.TextStyle
import com.HrshD1eux.DocLite.models.WordBodyElement
import com.HrshD1eux.DocLite.models.WordDocument
import com.HrshD1eux.DocLite.models.WordTable
import com.HrshD1eux.DocLite.models.WordTableCell
import com.HrshD1eux.DocLite.models.WordTableRow
import com.HrshD1eux.DocLite.ui.screens.home.HomeViewModel
import com.HrshD1eux.DocLite.ui.screens.home.HomeUiState
import com.HrshD1eux.DocLite.repository.SettingsRepository
import com.HrshD1eux.DocLite.office.excel.ExcelEngine
import com.HrshD1eux.DocLite.office.pdf.PdfEngine
import com.HrshD1eux.DocLite.office.powerpoint.PowerPointEngine
import com.HrshD1eux.DocLite.office.word.WordEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import androidx.room.Room
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.ui.screens.excel.ExcelViewModel
import com.HrshD1eux.DocLite.ui.screens.excel.ExcelUiState
import com.HrshD1eux.DocLite.ui.screens.word.WordViewModel
import com.HrshD1eux.DocLite.ui.screens.word.WordUiState
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import com.HrshD1eux.DocLite.database.entity.TrashedFileEntity
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficeEngineIntegrationTest {

    private lateinit var context: Context
    private lateinit var wordEngine: WordEngine
    private lateinit var excelEngine: ExcelEngine
    private lateinit var pptEngine: PowerPointEngine
    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wordEngine = WordEngine(context)
        excelEngine = ExcelEngine(context)
        pptEngine = PowerPointEngine(context)
        pdfEngine = PdfEngine(context)
    }

    @Test
    fun wordEngine_saveAndReloadRoundTrip_preservesContentAndEdits() = runTest {
        val testFile = File(context.cacheDir, "test_roundtrip.docx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        // 1. Create initial document
        val initialDoc = WordDocument(
            title = "test_roundtrip.docx",
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(
                    Paragraph(
                        runs = listOf(TextRun(text = "Header Title", style = TextStyle(isBold = true))),
                        isHeader = true
                    )
                ),
                WordBodyElement.ParagraphElement(
                    Paragraph(
                        runs = listOf(TextRun(text = "Initial paragraph body."))
                    )
                )
            )
        )

        // 2. Save
        val saveSuccess = wordEngine.saveDocument(uri, initialDoc)
        assertTrue("Document must save successfully", saveSuccess)
        assertTrue("Saved file must exist on disk", testFile.exists())

        // 3. Load
        val loadedDoc = wordEngine.loadDocument(uri)
        assertEquals("Loaded document must have 2 paragraphs", 2, loadedDoc.paragraphs.size)
        assertEquals("Header Title", loadedDoc.paragraphs[0].getPlainText().trim())
        assertEquals("Initial paragraph body.", loadedDoc.paragraphs[1].getPlainText().trim())

        // 4. Edit and re-save: modify second paragraph
        val editedElements = loadedDoc.bodyElements.toMutableList()
        editedElements[1] = WordBodyElement.ParagraphElement(
            Paragraph(runs = listOf(TextRun(text = "Edited paragraph content.")))
        )
        val editedDoc = loadedDoc.copy(bodyElements = editedElements)
        val secondSave = wordEngine.saveDocument(uri, editedDoc)
        assertTrue("Second save must succeed", secondSave)

        // 5. Re-open and verify edit is preserved (validates single-source-of-truth fix)
        val reloadedDoc = wordEngine.loadDocument(uri)
        assertEquals("Edited paragraph content.", reloadedDoc.paragraphs[1].getPlainText().trim())
    }

    @Test
    fun wordEngine_nativeXmlZipFile_parsesFormattingAndTablesRobustly() = runTest {
        val testFile = File(context.cacheDir, "test_native_xml.docx")
        if (testFile.exists()) testFile.delete()

        // Create a raw docx zip archive directly with OpenXML word/document.xml
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr><w:jc w:val="center"/><w:pStyle w:val="Heading1"/></w:pPr>
      <w:r>
        <w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="FF0000"/></w:rPr>
        <w:t xml:space="preserve">Native Title Heading</w:t>
      </w:r>
    </w:p>
    <w:p>
      <w:r>
        <w:rPr><w:i/></w:rPr>
        <w:t>Italicized paragraph text</w:t>
      </w:r>
    </w:p>
    <w:tbl>
      <w:tr>
        <w:tc><w:p><w:r><w:t>Col A</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Col B</w:t></w:r></w:p></w:tc>
      </w:tr>
      <w:tr>
        <w:tc><w:p><w:r><w:t>Val 1</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Val 2</w:t></w:r></w:p></w:tc>
      </w:tr>
    </w:tbl>
    <w:sectPr/>
  </w:body>
</w:document>""".trimIndent()

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".trimIndent()

        val zos = java.util.zip.ZipOutputStream(testFile.outputStream())
        zos.use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(docXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val uri = Uri.fromFile(testFile)
        val loadedDoc = wordEngine.loadDocument(uri)

        // Verify document structure
        assertTrue("Must have at least 2 paragraphs", loadedDoc.paragraphs.size >= 2)
        assertEquals("Native Title Heading", loadedDoc.paragraphs[0].getPlainText().trim())
        assertTrue("Heading must be flagged as header", loadedDoc.paragraphs[0].isHeader)
        assertEquals(com.HrshD1eux.DocLite.models.TextAlignment.CENTER, loadedDoc.paragraphs[0].alignment)
        assertTrue("First run must be bold", loadedDoc.paragraphs[0].runs[0].style.isBold)
        assertEquals("#FF0000", loadedDoc.paragraphs[0].runs[0].style.fontColorHex)
        assertEquals(18f, loadedDoc.paragraphs[0].runs[0].style.fontSizeSp)

        // Verify second paragraph
        assertEquals("Italicized paragraph text", loadedDoc.paragraphs[1].getPlainText().trim())
        assertTrue("Second paragraph must be italic", loadedDoc.paragraphs[1].runs[0].style.isItalic)

        // Verify table
        val tableElem = loadedDoc.bodyElements.filterIsInstance<WordBodyElement.TableElement>().firstOrNull()
        assertNotNull("Table element must be parsed", tableElem)
        assertEquals(2, tableElem!!.table.rows.size)
        assertEquals("Col A", tableElem.table.rows[0].cells[0].text.trim())
        assertEquals("Col B", tableElem.table.rows[0].cells[1].text.trim())
        assertEquals("Val 1", tableElem.table.rows[1].cells[0].text.trim())
        assertEquals("Val 2", tableElem.table.rows[1].cells[1].text.trim())
    }

    @Test
    fun excelEngine_saveAndReloadRoundTrip_preservesCellsAndValues() = runTest {
        val testFile = File(context.cacheDir, "test_roundtrip.xlsx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        val cells = mutableMapOf<String, Cell>()
        cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "Item")
        cells[Sheet.getCellKey(0, 1)] = Cell(row = 0, col = 1, value = "Price")
        cells[Sheet.getCellKey(1, 0)] = Cell(row = 1, col = 0, value = "Coffee")
        cells[Sheet.getCellKey(1, 1)] = Cell(row = 1, col = 1, value = "5")
        cells[Sheet.getCellKey(2, 0)] = Cell(row = 2, col = 0, value = "Donut")
        cells[Sheet.getCellKey(2, 1)] = Cell(row = 2, col = 1, value = "3")

        val sheet = Sheet(name = "Receipt", rowCount = 10, colCount = 5, cells = cells)
        val doc = SpreadsheetDocument(title = "test_roundtrip.xlsx", fileUri = uri.toString(), sheets = listOf(sheet))

        val saveResult = excelEngine.saveSpreadsheet(uri, doc)
        assertTrue("Spreadsheet must save successfully", saveResult)

        val loadedDoc = excelEngine.loadSpreadsheet(uri)
        assertEquals(1, loadedDoc.sheets.size)
        val loadedSheet = loadedDoc.sheets[0]
        assertEquals("Coffee", loadedSheet.getCell(1, 0).value)
        assertEquals("5", loadedSheet.getCell(1, 1).value)
        assertEquals("Donut", loadedSheet.getCell(2, 0).value)
        assertEquals("3", loadedSheet.getCell(2, 1).value)
    }

    @Test
    fun excelEngine_dateAndBooleanPreservation_savesAndReloadsWithCorrectTypes() = runTest {
        val testFile = File(context.cacheDir, "test_dates_booleans.xlsx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        val cells = mutableMapOf<String, Cell>()
        cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "Date Col")
        cells[Sheet.getCellKey(0, 1)] = Cell(row = 0, col = 1, value = "Boolean Col")
        cells[Sheet.getCellKey(1, 0)] = Cell(row = 1, col = 0, value = "2026-03-15", format = CellFormat(numberFormat = NumberFormat.DATE))
        cells[Sheet.getCellKey(1, 1)] = Cell(row = 1, col = 1, value = "TRUE", format = CellFormat(numberFormat = NumberFormat.BOOLEAN))
        cells[Sheet.getCellKey(2, 1)] = Cell(row = 2, col = 1, value = "FALSE", format = CellFormat(numberFormat = NumberFormat.BOOLEAN))

        val sheet = Sheet(name = "TypedData", rowCount = 5, colCount = 5, cells = cells)
        val doc = SpreadsheetDocument(title = "test_dates_booleans.xlsx", fileUri = uri.toString(), sheets = listOf(sheet))

        val saveResult = excelEngine.saveSpreadsheet(uri, doc)
        assertTrue("Save must succeed", saveResult)

        val loadedDoc = excelEngine.loadSpreadsheet(uri)
        val loadedSheet = loadedDoc.sheets[0]

        val dateCell = loadedSheet.getCell(1, 0)
        assertEquals("2026-03-15", dateCell.value)
        assertEquals(NumberFormat.DATE, dateCell.format.numberFormat)

        val trueCell = loadedSheet.getCell(1, 1)
        assertEquals("TRUE", trueCell.value)
        assertEquals(NumberFormat.BOOLEAN, trueCell.format.numberFormat)

        val falseCell = loadedSheet.getCell(2, 1)
        assertEquals("FALSE", falseCell.value)
        assertEquals(NumberFormat.BOOLEAN, falseCell.format.numberFormat)
    }

    @Test
    fun excelEngine_multiSheet_roundTripPreservesAllSheets() = runTest {
        val testFile = File(context.cacheDir, "test_multisheet.xlsx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        val s1Cells = mutableMapOf<String, Cell>()
        s1Cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "Sheet1-Cell")
        val sheet1 = Sheet(name = "Overview", rowCount = 5, colCount = 5, cells = s1Cells)

        val s2Cells = mutableMapOf<String, Cell>()
        s2Cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "Sheet2-Cell")
        val sheet2 = Sheet(name = "Analytics", rowCount = 5, colCount = 5, cells = s2Cells)

        val doc = SpreadsheetDocument(title = "test_multisheet.xlsx", fileUri = uri.toString(), sheets = listOf(sheet1, sheet2))

        val saveResult = excelEngine.saveSpreadsheet(uri, doc)
        assertTrue("Save multi-sheet must succeed", saveResult)

        val loadedDoc = excelEngine.loadSpreadsheet(uri)
        assertEquals(2, loadedDoc.sheets.size)
        assertEquals("Overview", loadedDoc.sheets[0].name)
        assertEquals("Analytics", loadedDoc.sheets[1].name)
        assertEquals("Sheet1-Cell", loadedDoc.sheets[0].getCell(0, 0).value)
        assertEquals("Sheet2-Cell", loadedDoc.sheets[1].getCell(0, 0).value)
    }

    @Test
    fun excelEngine_loadCsv_handlesCommasInsideQuotesCorrectly() = runTest {
        val csvFile = File(context.cacheDir, "test_quotes.csv")
        csvFile.writeText(
            """
            "ID","Full Name","Address","Amount"
            "101","Smith, John","New York, NY","1250.50"
            "102","Doe, Jane","Chicago, IL","800.00"
            """.trimIndent()
        )
        val uri = Uri.fromFile(csvFile)

        val doc = excelEngine.loadSpreadsheet(uri)
        val sheet = doc.sheets.first()

        // Verify column 1 contains "Smith, John" unbroken (not split by inner comma)
        assertEquals("Smith, John", sheet.getCell(1, 1).value)
        assertEquals("New York, NY", sheet.getCell(1, 2).value)
        assertEquals("1250.50", sheet.getCell(1, 3).value)
    }

    @Test
    fun powerPointEngine_loadPresentation_parsesSlidesSuccessfully() = runTest {
        val pptFile = File(context.cacheDir, "test_deck.pptx")
        val pptx = XMLSlideShow()
        val slide = pptx.createSlide()
        val textBox = slide.createTextBox()
        textBox.text = "DocLite Test Presentation"

        pptFile.outputStream().use { pptx.write(it) }
        pptx.close()

        val uri = Uri.fromFile(pptFile)
        val presentation = pptEngine.loadPresentation(uri)

        assertEquals("Must load 1 slide", 1, presentation.slides.size)
        assertTrue(
            "Slide elements must contain presentation text",
            presentation.slides[0].elements.any { it.textContent.contains("DocLite Test Presentation") }
        )
    }

    @Test
    fun pdfEngine_openSearchAndClose_executesCleanly() = runTest {
        val pdfFile = File(context.cacheDir, "test_engine.pdf")
        val pdDoc = PDDocument()
        val page = PDPage()
        pdDoc.addPage(page)

        val cs = PDPageContentStream(pdDoc, page)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12f)
        cs.newLineAtOffset(50f, 700f)
        cs.showText("DocLite PDF Engine Integration Test SearchTarget")
        cs.endText()
        cs.close()

        pdDoc.save(pdfFile)
        pdDoc.close()

        val uri = Uri.fromFile(pdfFile)

        // 1. Open
        val pageCount = pdfEngine.openPdf(uri)
        assertEquals(1, pageCount)

        // 2. Aspect Ratio
        val ratio = pdfEngine.getPageAspectRatio(0)
        assertTrue("Aspect ratio must be positive", ratio > 0.5f)

        // 2b. Render Page
        val bitmap = pdfEngine.renderPage(0, 1080)
        if (bitmap != null) {
            assertEquals(android.graphics.Bitmap.Config.ARGB_8888, bitmap.config)
        }

        // 3. Search
        val searchResults = pdfEngine.searchInPdf("SearchTarget")
        assertEquals("Must find 1 match", 1, searchResults.size)
        assertEquals(0, searchResults[0].pageIndex)
        assertTrue("Snippet must contain target", searchResults[0].snippet.contains("SearchTarget"))

        // 4. Export annotated PDF
        val annotation = com.HrshD1eux.DocLite.models.PdfAnnotation(
            id = "ann-1",
            fileUri = uri.toString(),
            pageIndex = 0,
            type = com.HrshD1eux.DocLite.models.AnnotationType.HIGHLIGHT,
            boundsLeftRatio = 0.1f,
            boundsTopRatio = 0.1f,
            boundsWidthRatio = 0.5f,
            boundsHeightRatio = 0.05f
        )
        val exportedUri = pdfEngine.exportAnnotatedPdf(uri, listOf(annotation))
        assertNotNull(exportedUri)
        assertTrue(File(exportedUri.path ?: "").exists())

        // 5. Close
        pdfEngine.close()
    }

    @Test
    fun databaseMigration_migration2To3_createsIndexSuccessfully() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // In-memory database
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `pdf_annotations` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `fileUri` TEXT NOT NULL,
                                `pageIndex` INTEGER NOT NULL,
                                `annotationType` TEXT NOT NULL,
                                `colorHex` TEXT NOT NULL,
                                `strokeWidthDp` REAL NOT NULL,
                                `pointsJson` TEXT NOT NULL,
                                `noteText` TEXT NOT NULL,
                                `signatureBitmapPath` TEXT,
                                `boundsLeftRatio` REAL NOT NULL,
                                `boundsTopRatio` REAL NOT NULL,
                                `boundsWidthRatio` REAL NOT NULL,
                                `boundsHeightRatio` REAL NOT NULL,
                                `timestamp` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper.writableDatabase

        // Execute MIGRATION_2_3
        AppDatabase.MIGRATION_2_3.migrate(db)

        // Query sqlite_master to verify index creation
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_pdf_annotations_fileUri'")
        cursor.use {
            assertTrue("Index index_pdf_annotations_fileUri must exist in database", it.moveToFirst())
            assertEquals("index_pdf_annotations_fileUri", it.getString(0))
        }

        db.close()
    }

    @Test
    fun excelViewModel_rowColumnAndSheetOperations_updatesStateCorrectly() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val docRepo = DocumentRepository(context, db.pdfAnnotationDao())
        val fileRepo = FileRepository(context, db.recentFileDao(), db.favoriteFileDao(), db.passwordProtectionDao())
        val viewModel = ExcelViewModel(docRepo, fileRepo)

        val testFile = File(context.cacheDir, "test_vm_ops.xlsx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        val cells = mutableMapOf<String, Cell>()
        cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "R0C0")
        cells[Sheet.getCellKey(1, 0)] = Cell(row = 1, col = 0, value = "R1C0")
        cells[Sheet.getCellKey(2, 0)] = Cell(row = 2, col = 0, value = "R2C0")
        cells[Sheet.getCellKey(0, 1)] = Cell(row = 0, col = 1, value = "R0C1")

        val sheet = Sheet(name = "Sheet1", rowCount = 5, colCount = 5, cells = cells)
        val doc = SpreadsheetDocument(title = "test_vm_ops.xlsx", fileUri = uri.toString(), sheets = listOf(sheet))
        excelEngine.saveSpreadsheet(uri, doc)

        // Load into ViewModel
        viewModel.loadSpreadsheet(uri)
        var attempts = 0
        while (viewModel.uiState.value !is ExcelUiState.Success && attempts < 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }
        var state = viewModel.uiState.value as ExcelUiState.Success
        val initialRowCount = state.document.sheets[0].rowCount
        val initialColCount = state.document.sheets[0].colCount
        assertEquals("Sheet1", state.document.sheets[0].name)
        assertEquals("R1C0", state.document.sheets[0].getCell(1, 0).value)

        // Delete Row 1: R1C0 should be removed, R2C0 shifted to row 1
        viewModel.deleteRow(1)
        state = viewModel.uiState.value as ExcelUiState.Success
        val currentSheetAfterRowDel = state.document.sheets[state.activeSheetIndex]
        assertEquals("R2C0", currentSheetAfterRowDel.getCell(1, 0).value)
        assertEquals(initialRowCount - 1, currentSheetAfterRowDel.rowCount)

        // Delete Col 0: R0C1 shifted to col 0
        viewModel.deleteColumn(0)
        state = viewModel.uiState.value as ExcelUiState.Success
        val currentSheetAfterColDel = state.document.sheets[state.activeSheetIndex]
        assertEquals("R0C1", currentSheetAfterColDel.getCell(0, 0).value)
        assertEquals(initialColCount - 1, currentSheetAfterColDel.colCount)

        // Multi-sheet ops: addSheet, switchSheet, renameSheet, deleteSheet
        viewModel.addSheet("Sheet2")
        state = viewModel.uiState.value as ExcelUiState.Success
        assertEquals(2, state.document.sheets.size)
        assertEquals("Sheet2", state.document.sheets[1].name)

        viewModel.switchSheet(1)
        state = viewModel.uiState.value as ExcelUiState.Success
        assertEquals(1, state.activeSheetIndex)

        viewModel.renameSheet(1, "RenamedSheet")
        state = viewModel.uiState.value as ExcelUiState.Success
        assertEquals("RenamedSheet", state.document.sheets[1].name)

        viewModel.deleteSheet(1)
        state = viewModel.uiState.value as ExcelUiState.Success
        assertEquals(1, state.document.sheets.size)
        assertEquals(0, state.activeSheetIndex)

        db.close()
    }

    @Test
    fun wordEngine_savePreservesExistingZipEntriesAndMedia() = runTest {
        val testFile = File(context.cacheDir, "test_preserve_media.docx")
        if (testFile.exists()) testFile.delete()

        // 1. Manually craft a .docx containing word/document.xml, word/media/logo.png, and word/styles.xml
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>Original Text</w:t></w:r></w:p>
    <w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>
  </w:body>
</w:document>""".trimIndent()

        val mockImageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03)
        val mockStyles = "<w:styles><w:style w:styleId=\"CustomStyle\"/></w:styles>"

        java.util.zip.ZipOutputStream(testFile.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zos.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("word/media/logo.png"))
            zos.write(mockImageData)
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("word/styles.xml"))
            zos.write(mockStyles.toByteArray())
            zos.closeEntry()
        }

        val uri = Uri.fromFile(testFile)

        // 2. Load the document through WordEngine
        val loaded = wordEngine.loadDocument(uri)
        assertEquals(1, loaded.paragraphs.size)
        assertEquals("Original Text", loaded.paragraphs[0].getPlainText())

        // 3. Edit text and save
        val editedParagraphs = listOf(
            WordBodyElement.ParagraphElement(
                Paragraph(runs = listOf(TextRun(text = "Updated Text Without Data Loss")))
            )
        )
        val editedDoc = loaded.copy(bodyElements = editedParagraphs)
        val saveSuccess = wordEngine.saveDocument(uri, editedDoc)
        assertTrue("Save must succeed", saveSuccess)

        // 4. Verify ZIP package preservation: media and styles MUST still exist and be intact!
        val savedZip = java.util.zip.ZipFile(testFile)
        savedZip.use { zip ->
            val mediaEntry = zip.getEntry("word/media/logo.png")
            assertNotNull("word/media/logo.png must be preserved in the archive", mediaEntry)
            val readMedia = zip.getInputStream(mediaEntry).readBytes()
            org.junit.Assert.assertArrayEquals("Image binary data must remain byte-for-byte identical", mockImageData, readMedia)

            val stylesEntry = zip.getEntry("word/styles.xml")
            assertNotNull("word/styles.xml must be preserved in the archive", stylesEntry)

            val docEntry = zip.getEntry("word/document.xml")
            assertNotNull("word/document.xml must exist", docEntry)
            val docText = zip.getInputStream(docEntry).bufferedReader().readText()
            assertTrue("word/document.xml must contain the updated text", docText.contains("Updated Text Without Data Loss"))
        }
    }

    @Test
    fun wordViewModel_activeParagraphFormattingAndTableOperations() = runTest {
        val testFile = File(context.cacheDir, "test_vm_ops.docx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        val initialDoc = WordDocument(
            title = "test_vm_ops.docx",
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun("First paragraph."))))
            )
        )
        wordEngine.saveDocument(uri, initialDoc)

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        val docRepo = DocumentRepository(context, db.pdfAnnotationDao())
        val fileRepo = FileRepository(context, db.recentFileDao(), db.favoriteFileDao(), db.passwordProtectionDao())

        val viewModel = WordViewModel(docRepo, fileRepo)
        viewModel.loadDocument(uri)

        var state = viewModel.uiState.value
        // Busy-wait briefly for StateFlow emission in test coroutine
        var attempts = 0
        while (viewModel.uiState.value !is WordUiState.Success && attempts < 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }
        state = viewModel.uiState.value
        assertTrue("ViewModel state must become Success", state is WordUiState.Success)
        val successState = state as WordUiState.Success
        assertEquals(1, successState.document.paragraphs.size)

        // 1. Formatting active paragraph: Bold and Heading
        viewModel.setActiveElement(0)
        viewModel.toggleBold()
        var updated = viewModel.uiState.value as WordUiState.Success
        assertTrue("Active paragraph must be bold", updated.isBold)
        val p0 = (updated.document.bodyElements[0] as WordBodyElement.ParagraphElement).paragraph
        assertTrue("Paragraph run must have bold style", p0.runs.all { it.style.isBold })

        viewModel.toggleHeading(1)
        updated = viewModel.uiState.value as WordUiState.Success
        assertTrue("Active paragraph must be marked as header", updated.isHeader)
        assertEquals(1, updated.headerLevel)

        // 2. Insert table and update table cell
        viewModel.insertTable(rows = 2, cols = 2, afterIndex = 0)
        updated = viewModel.uiState.value as WordUiState.Success
        assertEquals(2, updated.document.bodyElements.size)
        assertTrue("Second element must be a table", updated.document.bodyElements[1] is WordBodyElement.TableElement)

        viewModel.updateTableCell(elementIndex = 1, rowIndex = 0, colIndex = 0, text = "Custom Header Cell")
        updated = viewModel.uiState.value as WordUiState.Success
        val tableElem = updated.document.bodyElements[1] as WordBodyElement.TableElement
        assertEquals("Custom Header Cell", tableElem.table.rows[0].cells[0].text)

        // 3. Test Undo restores previous cell text
        viewModel.undo()
        updated = viewModel.uiState.value as WordUiState.Success
        val tableAfterUndo = updated.document.bodyElements[1] as WordBodyElement.TableElement
        assertEquals("Header 1", tableAfterUndo.table.rows[0].cells[0].text)

        // 4. Test Redo re-applies custom cell text
        viewModel.redo()
        updated = viewModel.uiState.value as WordUiState.Success
        val tableAfterRedo = updated.document.bodyElements[1] as WordBodyElement.TableElement
        assertEquals("Custom Header Cell", tableAfterRedo.table.rows[0].cells[0].text)

        db.close()
    }

    @Test
    fun fileRepository_trashBinLifecycle_movesRestoresAndPurgesAfter30Days() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val fileRepo = FileRepository(
            context = context,
            recentFileDao = db.recentFileDao(),
            favoriteFileDao = db.favoriteFileDao(),
            passwordProtectionDao = db.passwordProtectionDao(),
            trashedFileDao = db.trashedFileDao()
        )

        // 1. Create a dummy test file
        val testFile = File(context.filesDir, "test_doc.docx").apply {
            writeText("dummy content for trash bin test")
        }
        val docFile = DocumentFile(
            id = testFile.absolutePath,
            uriString = Uri.fromFile(testFile).toString(),
            name = "test_doc.docx",
            path = testFile.absolutePath,
            sizeBytes = testFile.length(),
            lastModified = System.currentTimeMillis(),
            format = DocumentFormat.WORD
        )

        // 2. Move file to trash
        val moved = fileRepo.moveToTrash(docFile)
        assertTrue("File must be moved to trash successfully", moved)
        assertFalse("Original file must no longer exist at original path", testFile.exists())

        // 3. Verify it appears in trashedFilesFlow
        var trashedList = fileRepo.trashedFilesFlow.first()
        assertEquals("Trash bin must have 1 file", 1, trashedList.size)
        val trashedEntity = trashedList[0]
        assertEquals("test_doc.docx", trashedEntity.name)

        // 4. Restore from trash
        val restored = fileRepo.restoreFromTrash(trashedEntity)
        assertTrue("File must be restored successfully", restored)
        assertTrue("Restored file must exist on disk", testFile.exists())
        trashedList = fileRepo.trashedFilesFlow.first()
        assertEquals("Trash bin must be empty after restore", 0, trashedList.size)

        // 5. Test 30-day purge: Insert an expired entity directly
        val expiredEntity = TrashedFileEntity(
            uriString = "file:///expired.docx",
            originalPath = "/tmp/expired.docx",
            trashedPath = File(context.filesDir, "expired_dummy.docx").apply { writeText("expired") }.absolutePath,
            name = "expired.docx",
            sizeBytes = 100,
            formatName = "WORD",
            trashedTimestamp = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000L),
            expiryTimestamp = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000L) // expired 5 days ago
        )
        db.trashedFileDao().insertOrUpdate(expiredEntity)
        assertEquals(1, fileRepo.trashedFilesFlow.first().size)

        // Purge expired files
        val purgedCount = fileRepo.purgeExpiredTrash()
        assertEquals("Must purge 1 expired file", 1, purgedCount)
        assertEquals("Trash bin must be empty after purging expired items", 0, fileRepo.trashedFilesFlow.first().size)

        // Cleanup
        testFile.delete()
        db.close()
    }

    @Test
    fun wordEngine_pageBreakElements_serializedAndParsedCorrectly() = runTest {
        val testFile = File(context.filesDir, "page_break_test.docx")
        if (testFile.exists()) testFile.delete()
        val uri = Uri.fromFile(testFile)

        // Create a document with Paragraph 1, PageBreak, Paragraph 2
        val initialDoc = WordDocument(
            title = "Page Break Test",
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("First Page Header Content")))
                ),
                WordBodyElement.PageBreakElement(),
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("Second Page Body Content")))
                )
            )
        )

        // Save using WordEngine
        val saveSuccess = wordEngine.saveDocument(uri, initialDoc)
        assertTrue("Saving document with page break must succeed", saveSuccess)
        assertTrue("File must exist on disk", testFile.exists())

        // Reload using WordEngine
        val loadedDoc = wordEngine.loadDocument(uri)
        assertNotNull("Loaded document must not be null", loadedDoc)

        // Verify elements: must contain a page break element
        val hasPageBreak = loadedDoc.bodyElements.any { it is WordBodyElement.PageBreakElement }
        assertTrue("Loaded document must preserve PageBreakElement", hasPageBreak)

        // Verify text from both pages is intact
        val fullText = loadedDoc.paragraphs.joinToString("\n") { it.getPlainText() }
        assertTrue("Full text must contain first page content", fullText.contains("First Page Header Content"))
        assertTrue("Full text must contain second page content", fullText.contains("Second Page Body Content"))

        // Cleanup
        testFile.delete()
    }

    @Test
    fun homeViewModel_clearRecentFiles_emptiesRecentsAndFilteredList() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val fileRepo = FileRepository(
            context = context,
            recentFileDao = db.recentFileDao(),
            favoriteFileDao = db.favoriteFileDao(),
            passwordProtectionDao = db.passwordProtectionDao(),
            trashedFileDao = db.trashedFileDao()
        )
        val settingsRepo = SettingsRepository(context)

        // Record a recent file
        val dummyFile = DocumentFile(
            id = "file:///dummy/RecentDocument.docx",
            name = "RecentDocument.docx",
            path = "/dummy/RecentDocument.docx",
            uriString = "file:///dummy/RecentDocument.docx",
            sizeBytes = 2048,
            lastModified = System.currentTimeMillis(),
            format = DocumentFormat.WORD
        )
        fileRepo.recordRecentFile(dummyFile)

        val vm = HomeViewModel(fileRepo, settingsRepo)
        val collectJob = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            vm.uiState.collect {}
        }

        var attempts = 0
        while ((vm.uiState.value as? HomeUiState.Success)?.recentFiles?.isEmpty() != false && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }

        val initialState = vm.uiState.value as HomeUiState.Success
        assertEquals(1, initialState.recentFiles.size)
        assertEquals(1, initialState.filteredFiles.size)

        // Clear recents via HomeViewModel
        vm.clearRecentFiles()

        attempts = 0
        while ((vm.uiState.value as? HomeUiState.Success)?.recentFiles?.isNotEmpty() != false && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }

        val clearedState = vm.uiState.value as HomeUiState.Success
        assertTrue("recentFiles must be empty after clear", clearedState.recentFiles.isEmpty())
        assertTrue("filteredFiles must be empty when recents are cleared without category filter", clearedState.filteredFiles.isEmpty())

        collectJob.cancel()
        db.close()
    }

    @Test
    fun wordEngine_loadLegacyDocFormat_succeedsWithoutException() = runTest {
        val testDoc = File(context.filesDir, "legacy_sample.doc")
        // Write standard OLE2 header followed by ASCII test text
        testDoc.outputStream().use { out ->
            out.write(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()))
            out.write("This is a legacy Word 97-2003 binary document content for DocLite testing.".toByteArray(Charsets.US_ASCII))
        }

        val loaded = wordEngine.loadDocument(Uri.fromFile(testDoc))
        assertNotNull("Loaded legacy doc must not be null", loaded)
        assertTrue("Must extract paragraph text from legacy doc", loaded.paragraphs.isNotEmpty())
        val fullText = loaded.paragraphs.joinToString(" ") { it.getPlainText() }
        assertTrue("Extracted text must contain content", fullText.contains("legacy Word 97-2003"))

        testDoc.delete()
    }

    @Test
    fun wordEngine_loadDocxWithXmlSyntaxIssue_fallsBackToRegexRecovery() = runTest {
        val testFile = File(context.filesDir, "syntax_issue.docx")
        // Create a zip with word/document.xml having unconventional attributes and self-closing tags
        val unconventionalXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p w14:paraId="12345678">
      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
      <w:r><w:rPr><w:b/></w:rPr><w:t>Heading from Resilient Parser</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>Paragraph content recovered cleanly without XMLBeans.</w:t></w:r>
    </w:p>
  </w:body>
</w:document>"""

        java.util.zip.ZipOutputStream(testFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(unconventionalXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val loaded = wordEngine.loadDocument(Uri.fromFile(testFile))
        assertNotNull("Document must load via resilient parser", loaded)
        val fullText = loaded.paragraphs.joinToString(" ") { it.getPlainText() }
        assertTrue("Must contain heading text", fullText.contains("Heading from Resilient Parser"))
        assertTrue("Must contain body text", fullText.contains("Paragraph content recovered cleanly"))

        testFile.delete()
    }

    @Test
    fun wordViewModel_withSettingsRepo_loadsWithoutHanging() = runTest {
        val testDoc = File(context.filesDir, "vm_load_test.docx")
        if (testDoc.exists()) testDoc.delete()
        val uri = Uri.fromFile(testDoc)
        val initialDoc = WordDocument(
            title = "VM Load Test",
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("Instant Load Verification")))
                )
            )
        )
        wordEngine.saveDocument(uri, initialDoc)

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        val docRepo = DocumentRepository(context, db.pdfAnnotationDao())
        val fileRepo = FileRepository(context, db.recentFileDao(), db.favoriteFileDao(), db.passwordProtectionDao())
        val settingsRepo = SettingsRepository(context)

        val viewModel = WordViewModel(docRepo, fileRepo, settingsRepo)
        viewModel.loadDocument(uri)
        viewModel.loadDocument(uri)

        var attempts = 0
        while (viewModel.uiState.value !is WordUiState.Success && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(30)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }

        assertTrue("ViewModel state must become Success and not hang on loading", viewModel.uiState.value is WordUiState.Success)
        val success = viewModel.uiState.value as WordUiState.Success
        assertEquals("Instant Load Verification", success.document.paragraphs[0].getPlainText().trim())

        testDoc.delete()
        db.close()
    }

    @Test
    fun wordEngine_parsesDocxWithTables_retainsStructureAndHeaders() = runTest {
        val testFile = File(context.filesDir, "table_test.docx")
        if (testFile.exists()) testFile.delete()

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>Document with Table</w:t></w:r></w:p>
    <w:tbl>
      <w:tr>
        <w:tc><w:p><w:r><w:t>Header A</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Header B</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Header C</w:t></w:r></w:p></w:tc>
      </w:tr>
      <w:tr>
        <w:tc><w:p><w:r><w:t>Data 1</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Data 2</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>Data 3</w:t></w:r></w:p></w:tc>
      </w:tr>
    </w:tbl>
    <w:p><w:r><w:t>After Table</w:t></w:r></w:p>
  </w:body>
</w:document>"""

        java.util.zip.ZipOutputStream(testFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(docXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val loaded = wordEngine.loadDocument(Uri.fromFile(testFile))
        assertNotNull(loaded)
        val tableElements = loaded.bodyElements.filterIsInstance<WordBodyElement.TableElement>()
        assertEquals(1, tableElements.size)

        val table = tableElements.first().table
        assertEquals(2, table.rows.size)
        assertEquals(3, table.rows[0].cells.size)
        assertTrue(table.rows[0].cells[0].isHeader)
        assertEquals("Header A", table.rows[0].cells[0].text)
        assertEquals("Header B", table.rows[0].cells[1].text)
        assertEquals("Header C", table.rows[0].cells[2].text)
        assertFalse(table.rows[1].cells[0].isHeader)
        assertEquals("Data 1", table.rows[1].cells[0].text)
        assertEquals("Data 2", table.rows[1].cells[1].text)
        assertEquals("Data 3", table.rows[1].cells[2].text)

        testFile.delete()
    }

    @Test
    fun wordEngine_parsesDocxWithEmbeddedImages_extractsAndMapsMedia() = runTest {
        val testFile = File(context.filesDir, "image_test.docx")
        if (testFile.exists()) testFile.delete()

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
</Relationships>"""

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>Before Image</w:t></w:r></w:p>
    <w:p>
      <w:r>
        <w:drawing>
          <wp:inline>
            <wp:extent cx="1905000" cy="952500"/>
            <a:graphic>
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <a:blip r:embed="rId1"/>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>
  </w:body>
</w:document>"""

        val fakeImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG magic

        java.util.zip.ZipOutputStream(testFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/_rels/document.xml.rels"))
            zip.write(relsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/media/image1.png"))
            zip.write(fakeImageBytes)
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(docXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val loaded = wordEngine.loadDocument(Uri.fromFile(testFile))
        assertNotNull(loaded)
        val imageElements = loaded.bodyElements.filterIsInstance<WordBodyElement.ImageElement>()
        assertEquals(1, imageElements.size)

        val img = imageElements.first()
        assertEquals(333, img.widthDp)
        assertEquals(166, img.heightDp)
        assertTrue(img.imageUri.isNotBlank())
        val cachedFile = File(Uri.parse(img.imageUri).path ?: "")
        assertTrue(cachedFile.exists())
        assertEquals(fakeImageBytes.size.toLong(), cachedFile.length())

        testFile.delete()
    }

    @Test
    fun wordEngine_saveDocumentWithTableAndImage_reloadsWithoutCorruptedError() = runTest {
        val testDoc = File(context.filesDir, "save_table_img_test.docx")
        if (testDoc.exists()) testDoc.delete()
        val uri = Uri.fromFile(testDoc)

        val doc = WordDocument(
            title = "save_table_img_test.docx",
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("Document Title", TextStyle(isBold = true, fontSizeSp = 22f))), isHeader = true, headerLevel = 1)
                ),
                WordBodyElement.TableElement(
                    WordTable(
                        rows = listOf(
                            WordTableRow(listOf(WordTableCell("Item", isHeader = true), WordTableCell("Qty", isHeader = true), WordTableCell("Price", isHeader = true))),
                            WordTableRow(listOf(WordTableCell("Apples"), WordTableCell("5"), WordTableCell("$10"))),
                            WordTableRow(listOf(WordTableCell("Bananas"), WordTableCell("12"), WordTableCell("$15")))
                        )
                    )
                ),
                WordBodyElement.ParagraphElement(
                    Paragraph(runs = listOf(TextRun("Summary paragraph following the table.")))
                )
            ),
            sectPrXml = "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"
        )

        // 1. Initial save
        val save1 = wordEngine.saveDocument(uri, doc)
        assertTrue(save1)

        // 2. Load
        val loaded1 = wordEngine.loadDocument(uri)
        assertNotNull(loaded1)
        assertEquals(1, loaded1.bodyElements.filterIsInstance<WordBodyElement.TableElement>().size)

        // 3. Edit table in memory and re-save
        val tableElem = loaded1.bodyElements.filterIsInstance<WordBodyElement.TableElement>().first()
        val updatedRows = tableElem.table.rows.toMutableList()
        updatedRows.add(WordTableRow(listOf(WordTableCell("Oranges"), WordTableCell("8"), WordTableCell("$12"))))
        val updatedElements = loaded1.bodyElements.map {
            if (it is WordBodyElement.TableElement) WordBodyElement.TableElement(WordTable(rows = updatedRows)) else it
        }
        val modifiedDoc = loaded1.copy(bodyElements = updatedElements)

        val save2 = wordEngine.saveDocument(uri, modifiedDoc)
        assertTrue(save2)

        // 4. Reload to guarantee no XML corruption or sectPr issues
        val loaded2 = wordEngine.loadDocument(uri)
        assertNotNull("Re-saved document must load with zero corrupted errors", loaded2)
        val loadedTable = loaded2.bodyElements.filterIsInstance<WordBodyElement.TableElement>().first().table
        assertEquals(4, loadedTable.rows.size)
        assertEquals("Oranges", loadedTable.rows[3].cells[0].text)

        testDoc.delete()
    }

    @Test
    fun pagination_and_column_weights_dsa_document() = runTest {
        // 1. Test computeColumnWeights on an index table structure
        val indexTable = WordTable(
            id = "index_tbl",
            rows = listOf(
                WordTableRow(
                    cells = listOf(
                        WordTableCell("S.No.", isHeader = true),
                        WordTableCell("Practical Title", isHeader = true),
                        WordTableCell("Date", isHeader = true),
                        WordTableCell("Page", isHeader = true)
                    )
                ),
                WordTableRow(
                    cells = listOf(
                        WordTableCell("1"),
                        WordTableCell("Traversing on a Linear Array"),
                        WordTableCell("11/08/2026"),
                        WordTableCell("3")
                    )
                )
            )
        )
        val weights = com.HrshD1eux.DocLite.ui.screens.word.computeColumnWeights(indexTable)
        assertEquals(4, weights.size)
        assertEquals(1.05f, weights[0], 0.001f) // S.No. -> 1.05f
        assertEquals(4.5f, weights[1], 0.001f)  // Practical Title -> 4.5f
        assertEquals(1.6f, weights[2], 0.001f)  // Date -> 1.6f
        assertEquals(1.3f, weights[3], 0.001f)  // Page -> 1.3f

        // 2. Test pagination does not generate phantom blank pages on PageBreakElement
        val elements = mutableListOf<WordBodyElement>()
        for (i in 1..5) {
            elements.add(WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun("Page $i heading")))))
            if (i < 5) {
                elements.add(WordBodyElement.PageBreakElement(i))
            }
        }
        val pages = com.HrshD1eux.DocLite.ui.screens.word.paginateDocumentElements(elements)
        assertEquals(5, pages.size)
        pages.forEachIndexed { idx, page ->
            assertEquals(idx + 1, page.pageNumber)
            assertEquals(1, page.elements.size)
        }

        // 3. If real DSA Practical File is available on local disk, verify real loading and layout
        val file = File("C:/Users/Harsh/Downloads/DSA_Practical_File.docx")
        if (file.exists()) {
            val doc = wordEngine.loadDocument(Uri.fromFile(file))
            assertNotNull(doc)
            val images = doc.bodyElements.filterIsInstance<WordBodyElement.ImageElement>()
            val tables = doc.bodyElements.filterIsInstance<WordBodyElement.TableElement>()
            assertTrue("Document must contain at least 1 image", images.isNotEmpty())
            assertTrue("Document must contain at least 1 table", tables.isNotEmpty())

            val realPages = com.HrshD1eux.DocLite.ui.screens.word.paginateDocumentElements(doc.bodyElements)
            println("REAL PAGES COUNT: " + realPages.size)
            for (p in realPages.take(6)) {
                val firstTxt = when (val elem = p.elements.firstOrNull()?.element) {
                    is WordBodyElement.ParagraphElement -> elem.paragraph.getPlainText().take(40)
                    is WordBodyElement.TableElement -> "TABLE: " + elem.table.rows.firstOrNull()?.cells?.map { it.text }?.joinToString("|")?.take(40)
                    is WordBodyElement.ImageElement -> "IMAGE"
                    is WordBodyElement.PageBreakElement -> "PAGE BREAK"
                    null -> "EMPTY"
                }
                val lastTxt = when (val elem = p.elements.lastOrNull()?.element) {
                    is WordBodyElement.ParagraphElement -> elem.paragraph.getPlainText().take(40)
                    is WordBodyElement.TableElement -> "TABLE: " + elem.table.rows.lastOrNull()?.cells?.map { it.text }?.joinToString("|")?.take(40)
                    is WordBodyElement.ImageElement -> "IMAGE"
                    is WordBodyElement.PageBreakElement -> "PAGE BREAK"
                    null -> "EMPTY"
                }
                println("Page ${p.pageNumber}: elements=${p.elements.size} | First: $firstTxt | Last: $lastTxt")
            }
            assertTrue("DSA Practical File with 31k words and 20 page breaks must paginate sensibly into 20..65 pages, got ${realPages.size}", realPages.size in 20..65)
        }
    }

    @Test
    fun wordEngine_parsesTableCellStylingAndNavyHeaders() = runTest {
        val testDoc = File(context.cacheDir, "table_styling_${System.currentTimeMillis()}.docx")
        val uri = Uri.fromFile(testDoc)

        val docWithStyledTable = WordDocument(
            title = testDoc.name,
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.TableElement(
                    WordTable(
                        rows = listOf(
                            WordTableRow(
                                cells = listOf(
                                    WordTableCell("S.No.", isHeader = true, backgroundColorHex = "#1F497D", textColorHex = "#FFFFFF", alignment = com.HrshD1eux.DocLite.models.TextAlignment.CENTER, isBold = true),
                                    WordTableCell("Practical Title", isHeader = true, backgroundColorHex = "#1F497D", textColorHex = "#FFFFFF", alignment = com.HrshD1eux.DocLite.models.TextAlignment.CENTER, isBold = true)
                                )
                            ),
                            WordTableRow(
                                cells = listOf(
                                    WordTableCell("1", isHeader = false, alignment = com.HrshD1eux.DocLite.models.TextAlignment.CENTER, isBold = true),
                                    WordTableCell("Linear Array", isHeader = false, alignment = com.HrshD1eux.DocLite.models.TextAlignment.LEFT, isBold = false)
                                )
                            )
                        )
                    )
                )
            )
        )

        val saved = wordEngine.saveDocument(uri, docWithStyledTable)
        assertTrue(saved)

        val loaded = wordEngine.loadDocument(uri)
        assertNotNull(loaded)
        val tableElem = loaded.bodyElements.filterIsInstance<WordBodyElement.TableElement>().firstOrNull()
        assertNotNull(tableElem)
        assertEquals(2, tableElem!!.table.rows.size)
        assertEquals("S.No.", tableElem.table.rows[0].cells[0].text)
        assertTrue(tableElem.table.rows[0].cells[0].isHeader)

        testDoc.delete()
    }

    @Test
    fun nonDestructiveDeltaSaving_preservesOriginalXmlAndZipEntries() = runTest {
        val testDoc = File(context.cacheDir, "delta_test_${System.currentTimeMillis()}.docx")
        val uri = Uri.fromFile(testDoc)

        // 1. Create document with multiple elements
        val initialDoc = WordDocument(
            title = testDoc.name,
            fileUri = uri.toString(),
            bodyElements = listOf(
                WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun("Original Paragraph 1")))),
                WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun("Original Paragraph 2")))),
                WordBodyElement.TableElement(
                    WordTable(
                        rows = listOf(
                            WordTableRow(listOf(WordTableCell("Col A"), WordTableCell("Col B"))),
                            WordTableRow(listOf(WordTableCell("Val 1"), WordTableCell("Val 2")))
                        )
                    )
                ),
                WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun("Original Paragraph 3"))))
            )
        )
        val initialSave = wordEngine.saveDocument(uri, initialDoc)
        assertTrue(initialSave)

        // 2. Reload: elements should now possess captured rawXml chunks
        val loaded1 = wordEngine.loadDocument(uri)
        assertNotNull(loaded1)
        val p1 = (loaded1.bodyElements[0] as WordBodyElement.ParagraphElement).paragraph
        val p2 = (loaded1.bodyElements[1] as WordBodyElement.ParagraphElement).paragraph
        val tbl = (loaded1.bodyElements[2] as WordBodyElement.TableElement).table
        val p3 = (loaded1.bodyElements[3] as WordBodyElement.ParagraphElement).paragraph

        assertNotNull("Untouched paragraph 1 must have rawXml", p1.rawXml)
        assertNotNull("Untouched paragraph 2 must have rawXml", p2.rawXml)
        assertNotNull("Untouched table must have rawXml", tbl.rawXml)
        assertNotNull("Untouched paragraph 3 must have rawXml", p3.rawXml)

        // 3. Edit ONLY paragraph 2: clear its rawXml to simulate a user edit
        val editedP2 = p2.copy(runs = listOf(TextRun("MODIFIED Paragraph 2 Content")), rawXml = null)
        val updatedElements = listOf(
            WordBodyElement.ParagraphElement(p1),      // rawXml intact -> non-destructive delta
            WordBodyElement.ParagraphElement(editedP2), // rawXml null -> serialized cleanly
            WordBodyElement.TableElement(tbl),         // rawXml intact -> non-destructive delta
            WordBodyElement.ParagraphElement(p3)       // rawXml intact -> non-destructive delta
        )
        val deltaDoc = loaded1.copy(bodyElements = updatedElements)
        val saveDelta = wordEngine.saveDocument(uri, deltaDoc)
        assertTrue("Delta save must succeed without POI", saveDelta)

        // 4. Reload and verify exact preservation
        val reloaded = wordEngine.loadDocument(uri)
        assertEquals("Original Paragraph 1", (reloaded.bodyElements[0] as WordBodyElement.ParagraphElement).paragraph.getPlainText().trim())
        assertEquals("MODIFIED Paragraph 2 Content", (reloaded.bodyElements[1] as WordBodyElement.ParagraphElement).paragraph.getPlainText().trim())
        assertEquals("Col A", (reloaded.bodyElements[2] as WordBodyElement.TableElement).table.rows[0].cells[0].text)
        assertEquals("Original Paragraph 3", (reloaded.bodyElements[3] as WordBodyElement.ParagraphElement).paragraph.getPlainText().trim())

        testDoc.delete()
    }

    @Test
    fun uriResolver_resolvesFileProviderUrisAndFallbacksGracefully() {
        val testFile = File(context.cacheDir, "uri_resolver_test.docx")
        testFile.writeText("Test File Provider Resolution")

        // 1. Direct file URI
        val fileUri = Uri.fromFile(testFile)
        val resolvedDirect = com.HrshD1eux.DocLite.core.utils.UriResolver.resolvePhysicalFile(context, fileUri)
        assertNotNull(resolvedDirect)
        assertEquals(testFile.canonicalPath, resolvedDirect!!.canonicalPath)

        // 2. Simulated FileProvider URI with /external_files/
        // If file is under external storage, /external_files/ mapping resolves directly
        val extStorage = android.os.Environment.getExternalStorageDirectory()
        val mockExtFile = File(extStorage, "TestExternalDoc.docx")
        val simulatedUri = Uri.parse("content://com.Hrshd1eux.filemanager.fileprovider/external_files/TestExternalDoc.docx")
        
        // Display name extraction should never throw SecurityException
        val displayName = com.HrshD1eux.DocLite.core.utils.UriResolver.getDisplayName(context, simulatedUri)
        assertEquals("TestExternalDoc.docx", displayName)

        // 3. Fallback openInputStream & openOutputStream
        val stream = com.HrshD1eux.DocLite.core.utils.UriResolver.openInputStream(context, fileUri)
        assertNotNull(stream)
        val content = stream!!.bufferedReader().readText()
        assertEquals("Test File Provider Resolution", content)
        stream.close()

        val outStream = com.HrshD1eux.DocLite.core.utils.UriResolver.openOutputStream(context, fileUri)
        assertNotNull(outStream)
        outStream!!.write("Updated Content".toByteArray())
        outStream.close()

        assertEquals("Updated Content", testFile.readText())
        testFile.delete()
    }
}
