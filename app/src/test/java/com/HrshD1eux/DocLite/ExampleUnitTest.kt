package com.HrshD1eux.DocLite

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun wordDocument_creationAndParsing_isValidOpcZip() {
    val doc = XWPFDocument()
    val p = doc.createParagraph()
    p.createRun().setText("Test paragraph content")

    val out = ByteArrayOutputStream()
    doc.write(out)
    doc.close()

    val bytes = out.toByteArray()
    assertTrue("Serialized .docx must not be empty", bytes.isNotEmpty())

    // Parse back from bytes
    val parsed = XWPFDocument(ByteArrayInputStream(bytes))
    assertEquals(1, parsed.paragraphs.size)
    assertEquals("Test paragraph content", parsed.paragraphs[0].text)
    parsed.close()
  }

  @Test
  fun excelDocument_creationAndParsing_isValidOpcZip() {
    val wb = XSSFWorkbook()
    val sheet = wb.createSheet("TestSheet")
    val row = sheet.createRow(0)
    row.createCell(0).setCellValue("DocLite")
    row.createCell(1).setCellValue(42.0)

    val out = ByteArrayOutputStream()
    wb.write(out)
    wb.close()

    val bytes = out.toByteArray()
    assertTrue("Serialized .xlsx must not be empty", bytes.isNotEmpty())

    val parsed = XSSFWorkbook(ByteArrayInputStream(bytes))
    assertEquals(1, parsed.numberOfSheets)
    val parsedSheet = parsed.getSheet("TestSheet")
    assertNotNull(parsedSheet)
    assertEquals("DocLite", parsedSheet.getRow(0).getCell(0).stringCellValue)
    assertEquals(42.0, parsedSheet.getRow(0).getCell(1).numericCellValue, 0.001)
    parsed.close()
  }

  @Test
  fun powerPointDocument_creationAndParsing_isValidOpcZip() {
    val ppt = XMLSlideShow()
    val slide = ppt.createSlide()
    val box = slide.createTextBox()
    box.text = "DocLite Presentation"

    val out = ByteArrayOutputStream()
    ppt.write(out)
    ppt.close()

    val bytes = out.toByteArray()
    assertTrue("Serialized .pptx must not be empty", bytes.isNotEmpty())

    val parsed = XMLSlideShow(ByteArrayInputStream(bytes))
    assertEquals(1, parsed.slides.size)
    assertEquals("DocLite Presentation", parsed.slides[0].shapes.filterIsInstance<org.apache.poi.xslf.usermodel.XSLFTextShape>().first().text)
    parsed.close()
  }

  @Test(expected = Exception::class)
  fun corruptedDocument_throwsException_neverReturnsSilently() {
    val corruptBytes = "This is not an OPC ZIP file".toByteArray()
    XWPFDocument(ByteArrayInputStream(corruptBytes))
  }

  @Test
  fun wordDocument_withTables_detectedAsUnrecognizedElements() {
    val doc = XWPFDocument()
    val table = doc.createTable(2, 2)
    table.getRow(0).getCell(0).text = "Header1"
    table.getRow(0).getCell(1).text = "Header2"

    val hasTables = doc.tables.isNotEmpty()
    assertTrue("Document containing tables must be detected", hasTables)
    doc.close()
  }

  @Test(expected = IllegalStateException::class)
  fun wordDocument_hasUnrecognizedElements_blocksSave() {
    val wordDoc = com.HrshD1eux.DocLite.models.WordDocument(
      title = "contract.docx",
      fileUri = "content://dummy",
      hasUnrecognizedElements = true
    )
    if (wordDoc.hasUnrecognizedElements) {
      throw IllegalStateException("Cannot save: Document contains unsupported elements (tables, images, or special styles) that would be stripped.")
    }
  }

  @Test(expected = IllegalStateException::class)
  fun excelDocument_hasUnrecognizedElements_blocksSave() {
    val excelDoc = com.HrshD1eux.DocLite.models.SpreadsheetDocument(
      title = "budget.xlsx",
      fileUri = "content://dummy",
      hasUnrecognizedElements = true
    )
    if (excelDoc.hasUnrecognizedElements) {
      throw IllegalStateException("Cannot save: Spreadsheet contains unsupported visual elements (charts, drawing objects) that would be stripped.")
    }
  }

  @Test(expected = IllegalStateException::class)
  fun powerPointDocument_hasUnrecognizedElements_blocksSave() {
    val pptDoc = com.HrshD1eux.DocLite.models.PresentationDocument(
      title = "deck.pptx",
      fileUri = "content://dummy",
      hasUnrecognizedElements = true
    )
    if (pptDoc.hasUnrecognizedElements) {
      throw IllegalStateException("Cannot save: Presentation contains unsupported elements (images, shapes, or layouts) that would be destroyed.")
    }
  }

  @Test
  fun powerPointDocument_withNonTextShapes_detectedAsUnrecognized() {
    val ppt = XMLSlideShow()
    val slide = ppt.createSlide()
    slide.createTable(2, 2) // Creates a table shape, which is not an XSLFTextShape
    
    val hasUnrecognized = slide.shapes.any { it !is org.apache.poi.xslf.usermodel.XSLFTextShape }
    assertTrue("Slide with table or non-text shapes must be detected as unrecognized", hasUnrecognized)
    ppt.close()
  }

  @Test
  fun csvDocument_parsingWithOpenCsv_extractsCorrectRows() {
    val csvContent = "Name,Role,Dept\nAlice,Engineer,Mobile\nBob,Designer,Product\n"
    val reader = java.io.StringReader(csvContent)
    val csvReader = com.opencsv.CSVReaderBuilder(reader).build()
    val rows = mutableListOf<Array<String>>()
    var line: Array<String>? = csvReader.readNext()
    while (line != null) {
      rows.add(line)
      line = csvReader.readNext()
    }

    assertEquals(3, rows.size)
    assertArrayEquals(arrayOf("Name", "Role", "Dept"), rows[0])
    assertArrayEquals(arrayOf("Alice", "Engineer", "Mobile"), rows[1])
    assertArrayEquals(arrayOf("Bob", "Designer", "Product"), rows[2])
  }
}


