package com.HrshD1eux.DocLite.office.excel

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.models.Cell
import com.HrshD1eux.DocLite.models.Sheet
import com.HrshD1eux.DocLite.models.SpreadsheetDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.FormulaEvaluator
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.InputStreamReader
import com.opencsv.CSVReaderBuilder

class ExcelEngine(private val context: Context) {

    suspend fun loadSpreadsheet(uri: Uri): SpreadsheetDocument = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext == "csv" || ext == "txt") {
            return@withContext loadCsv(uri, fileName)
        }

        val tempFile = File.createTempFile("xlsx_cache_", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw java.io.FileNotFoundException("Could not open file: $fileName")

            val workbook = try {
                WorkbookFactory.create(tempFile)
            } catch (e: org.apache.poi.EncryptedDocumentException) {
                throw IllegalArgumentException("Password-protected Excel files cannot be edited directly. Please remove password.", e)
            } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                throw IllegalArgumentException("Unsupported or corrupted spreadsheet format. The file is not a valid Excel document.", e)
            } catch (e: OutOfMemoryError) {
                throw IllegalStateException("This spreadsheet is too large to open in available device memory.", e)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to open spreadsheet: ${e.localizedMessage ?: "Corrupted file"}", e)
            }

        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        val parsedSheets = mutableListOf<Sheet>()
        var hasUnrecognizedElements = false

        for (i in 0 until workbook.numberOfSheets) {
            val poiSheet = workbook.getSheetAt(i)
            if (poiSheet.drawingPatriarch != null) {
                hasUnrecognizedElements = true
            }

            val cellsMap = mutableMapOf<String, Cell>()
            var maxRow = 0
            var maxCol = 0

            for (row in poiSheet) {
                maxRow = maxOf(maxRow, row.rowNum)
                for (poiCell in row) {
                    maxCol = maxOf(maxCol, poiCell.columnIndex)
                    val key = Sheet.getCellKey(row.rowNum, poiCell.columnIndex)
                    cellsMap[key] = extractCellData(poiCell, evaluator, row.rowNum, poiCell.columnIndex)
                }
            }

            parsedSheets.add(
                Sheet(
                    name = poiSheet.sheetName ?: "Sheet${i + 1}",
                    rowCount = maxOf(maxRow + 10, 40),
                    colCount = maxOf(maxCol + 5, 12),
                    cells = cellsMap
                )
            )
        }

            SpreadsheetDocument(
                title = fileName,
                fileUri = uri.toString(),
                sheets = parsedSheets.ifEmpty { listOf(Sheet(name = "Sheet1")) },
                hasUnrecognizedElements = hasUnrecognizedElements
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun loadCsv(uri: Uri, fileName: String): SpreadsheetDocument {
        val cellsMap = mutableMapOf<String, Cell>()
        var maxRow = 0
        var maxCol = 0

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = InputStreamReader(inputStream)
            val csvReader = CSVReaderBuilder(reader).build()
            var rowIdx = 0
            var line: Array<String>? = csvReader.readNext()
            while (line != null) {
                maxRow = maxOf(maxRow, rowIdx)
                line.forEachIndexed { colIdx, text ->
                    maxCol = maxOf(maxCol, colIdx)
                    val key = Sheet.getCellKey(rowIdx, colIdx)
                    cellsMap[key] = Cell(
                        row = rowIdx,
                        col = colIdx,
                        value = text,
                        formula = if (text.startsWith("=")) text else "",
                        evaluatedValue = if (!text.startsWith("=")) text else ""
                    )
                }
                rowIdx++
                line = csvReader.readNext()
            }
        } ?: throw java.io.FileNotFoundException("Could not open CSV file: $fileName")

        val sheet = Sheet(
            name = "CSV Data",
            rowCount = maxOf(maxRow + 10, 40),
            colCount = maxOf(maxCol + 5, 12),
            cells = cellsMap
        )
        return SpreadsheetDocument(
            title = fileName,
            fileUri = uri.toString(),
            sheets = listOf(sheet),
            hasUnrecognizedElements = false
        )
    }

    private fun extractCellData(poiCell: org.apache.poi.ss.usermodel.Cell, evaluator: FormulaEvaluator, row: Int, col: Int): Cell {
        var value = ""
        var formula = ""
        var evaluatedValue = ""

        when (poiCell.cellType) {
            CellType.STRING -> value = poiCell.stringCellValue
            CellType.NUMERIC -> {
                value = if (DateUtil.isCellDateFormatted(poiCell)) {
                    poiCell.dateCellValue?.toString() ?: ""
                } else {
                    val num = poiCell.numericCellValue
                    if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                }
            }
            CellType.BOOLEAN -> value = poiCell.booleanCellValue.toString()
            CellType.FORMULA -> {
                formula = "=" + poiCell.cellFormula
                value = formula
                try {
                    val eval = evaluator.evaluate(poiCell)
                    evaluatedValue = when (eval.cellType) {
                        CellType.STRING -> eval.stringValue
                        CellType.NUMERIC -> {
                            val num = eval.numberValue
                            if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                        }
                        CellType.BOOLEAN -> eval.booleanValue.toString()
                        else -> ""
                    }
                } catch (e: Exception) {
                    evaluatedValue = "#ERROR!"
                }
            }
            else -> {}
        }

        return Cell(row = row, col = col, value = value, formula = formula, evaluatedValue = evaluatedValue)
    }

    private fun createEmptySpreadsheet(fileName: String, uri: Uri): SpreadsheetDocument {
        return SpreadsheetDocument(
            title = fileName,
            fileUri = uri.toString(),
            sheets = listOf(Sheet(name = "Sheet1", rowCount = 40, colCount = 12))
        )
    }

    suspend fun saveSpreadsheet(uri: Uri, document: SpreadsheetDocument): Boolean = withContext(Dispatchers.IO) {
        if (document.hasUnrecognizedElements) {
            throw IllegalStateException("Cannot save: Spreadsheet contains unsupported visual elements (charts, drawing objects) that would be stripped.")
        }

        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext == "csv" || ext == "txt") {
            return@withContext try {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    val writer = outputStream.bufferedWriter()
                    val sheet = document.sheets.firstOrNull() ?: Sheet("Sheet1")
                    for (r in 0 until sheet.rowCount) {
                        val rowValues = (0 until sheet.colCount).map { c ->
                            val cell = sheet.getCell(r, c)
                            val str = if (cell.formula.isNotEmpty()) cell.formula else cell.value
                            if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                                "\"${str.replace("\"", "\"\"")}\""
                            } else str
                        }
                        if (rowValues.any { it.isNotEmpty() }) {
                            writer.write(rowValues.joinToString(","))
                            writer.newLine()
                        }
                    }
                    writer.flush()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        try {
            // First read the existing workbook to preserve non-data elements (styles, charts)
            var workbook: Workbook? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                try {
                    workbook = WorkbookFactory.create(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // If it couldn't be parsed (or is a new file), create a new XSSFWorkbook
            if (workbook == null) {
                workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            }

            workbook?.let { wb ->
                // Update sheets
                document.sheets.forEachIndexed { index, sheet ->
                    var poiSheet = if (index < wb.numberOfSheets) wb.getSheetAt(index) else wb.createSheet(sheet.name)
                    wb.setSheetName(wb.getSheetIndex(poiSheet), sheet.name)
                    
                    sheet.cells.values.forEach { cellData ->
                        var row = poiSheet.getRow(cellData.row)
                        if (row == null) row = poiSheet.createRow(cellData.row)
                        
                        var poiCell = row.getCell(cellData.col)
                        if (poiCell == null) poiCell = row.createCell(cellData.col)
                        
                        if (cellData.formula.startsWith("=")) {
                            try {
                                poiCell.cellFormula = cellData.formula.substring(1)
                            } catch (e: Exception) {
                                poiCell.setCellValue(cellData.value)
                            }
                        } else {
                            val doubleVal = cellData.value.toDoubleOrNull()
                            if (doubleVal != null) {
                                poiCell.setCellValue(doubleVal)
                            } else {
                                poiCell.setCellValue(cellData.value)
                            }
                        }
                    }
                }

                // Force formula recalculation on opening in Excel
                wb.forceFormulaRecalculation = true

                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    wb.write(outputStream)
                }
                wb.close()
                true
            } ?: false
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
                    if (nameIndex != -1) result = it.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "spreadsheet.xlsx"
    }
}
