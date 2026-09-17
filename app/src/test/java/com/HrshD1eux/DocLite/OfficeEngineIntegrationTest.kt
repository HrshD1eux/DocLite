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
import kotlinx.coroutines.test.runTest
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
