package com.HrshD1eux.DocLite.ui.screens.excel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.Cell
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.Sheet
import com.HrshD1eux.DocLite.models.SpreadsheetDocument
import com.HrshD1eux.DocLite.office.excel.FormulaEngine
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.HrshD1eux.DocLite.office.pdf.PasswordRequiredException

sealed interface ExcelUiState {
    data object Loading : ExcelUiState
    data class PasswordRequired(
        val uri: Uri,
        val errorMessage: String? = null
    ) : ExcelUiState
    data class Success(
        val document: SpreadsheetDocument,
        val activeSheetIndex: Int = 0,
        val selectedRow: Int = 0,
        val selectedCol: Int = 0,
        val formulaInput: String = "",
        val isEditingCell: Boolean = false,
        val saveStatus: String? = null
    ) : ExcelUiState
    data class Error(val message: String) : ExcelUiState
}

class ExcelViewModel(
    private val documentRepository: DocumentRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val formulaEngine = FormulaEngine()

    private val _uiState = MutableStateFlow<ExcelUiState>(ExcelUiState.Loading)
    val uiState: StateFlow<ExcelUiState> = _uiState.asStateFlow()

    fun loadSpreadsheet(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            if (password == null) {
                _uiState.value = ExcelUiState.Loading
            }
            val result = documentRepository.loadSpreadsheet(uri, password)
            result.onSuccess { doc ->
                val activeSheet = doc.sheets.firstOrNull()
                val selectedCell = activeSheet?.getCell(0, 0)
                _uiState.value = ExcelUiState.Success(
                    document = doc,
                    formulaInput = selectedCell?.formula?.ifEmpty { selectedCell.value } ?: ""
                )

                fileRepository.recordRecentFile(
                    DocumentFile(
                        id = uri.toString(),
                        name = doc.title,
                        path = uri.path ?: "",
                        uriString = uri.toString(),
                        sizeBytes = 2048,
                        lastModified = System.currentTimeMillis(),
                        format = DocumentFormat.EXCEL
                    )
                )
            }.onFailure { err ->
                if (err is PasswordRequiredException || err.message?.contains("password", ignoreCase = true) == true) {
                    _uiState.value = ExcelUiState.PasswordRequired(
                        uri = uri,
                        errorMessage = if (password != null) "Invalid password. Please try again." else null
                    )
                } else {
                    _uiState.value = ExcelUiState.Error(err.message ?: "Failed to open Spreadsheet")
                }
            }
        }
    }

    fun selectCell(row: Int, col: Int) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheet = currentState.document.sheets.getOrNull(currentState.activeSheetIndex) ?: return
        val cell = sheet.getCell(row, col)

        _uiState.value = currentState.copy(
            selectedRow = row,
            selectedCol = col,
            formulaInput = if (cell.formula.isNotEmpty()) cell.formula else cell.value,
            isEditingCell = false
        )
    }

    fun updateFormulaInput(input: String) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        _uiState.value = currentState.copy(formulaInput = input)
    }

    fun applyCellEdit() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        val r = currentState.selectedRow
        val c = currentState.selectedCol
        val input = currentState.formulaInput.trim()

        val key = Sheet.getCellKey(r, c)
        val updatedCells = currentSheet.cells.toMutableMap()

        if (input.startsWith("=")) {
            val eval = formulaEngine.evaluateFormula(input, currentSheet)
            updatedCells[key] = Cell(
                row = r,
                col = c,
                value = input,
                formula = input,
                evaluatedValue = eval
            )
        } else {
            updatedCells[key] = Cell(
                row = r,
                col = c,
                value = input,
                formula = "",
                evaluatedValue = input
            )
        }

        // Re-evaluate dependent formula cells in this sheet
        val tempSheet = currentSheet.copy(cells = updatedCells)
        for ((cellKey, cell) in updatedCells) {
            if (cell.formula.isNotEmpty() && cellKey != key) {
                val reevaluated = formulaEngine.evaluateFormula(cell.formula, tempSheet)
                if (reevaluated != cell.evaluatedValue) {
                    updatedCells[cellKey] = cell.copy(evaluatedValue = reevaluated)
                }
            }
        }

        val updatedSheet = currentSheet.copy(cells = updatedCells)
        sheets[sheetIndex] = updatedSheet
        val updatedDoc = currentState.document.copy(sheets = sheets)

        _uiState.value = currentState.copy(
            document = updatedDoc,
            isEditingCell = false
        )
    }

    fun insertRow(targetRow: Int? = null) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        val rToInsert = targetRow ?: currentState.selectedRow
        val newCells = mutableMapOf<String, Cell>()

        for ((_, cell) in currentSheet.cells) {
            if (cell.row >= rToInsert) {
                val shifted = cell.copy(row = cell.row + 1)
                newCells[Sheet.getCellKey(shifted.row, shifted.col)] = shifted
            } else {
                newCells[Sheet.getCellKey(cell.row, cell.col)] = cell
            }
        }

        val updatedSheet = currentSheet.copy(rowCount = currentSheet.rowCount + 1, cells = newCells)
        sheets[sheetIndex] = updatedSheet
        _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
    }

    fun deleteRow(targetRow: Int? = null) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        if (currentSheet.rowCount <= 1) return

        val rToDelete = targetRow ?: currentState.selectedRow
        val newCells = mutableMapOf<String, Cell>()

        for ((_, cell) in currentSheet.cells) {
            when {
                cell.row == rToDelete -> {
                    // Deleted cell
                }
                cell.row > rToDelete -> {
                    val shifted = cell.copy(row = cell.row - 1)
                    newCells[Sheet.getCellKey(shifted.row, shifted.col)] = shifted
                }
                else -> {
                    newCells[Sheet.getCellKey(cell.row, cell.col)] = cell
                }
            }
        }

        val newRowCount = currentSheet.rowCount - 1
        val updatedSheet = currentSheet.copy(rowCount = newRowCount, cells = newCells)
        sheets[sheetIndex] = updatedSheet

        val newSelectedRow = currentState.selectedRow.coerceAtMost(newRowCount - 1)
        val selectedCell = updatedSheet.getCell(newSelectedRow, currentState.selectedCol)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(sheets = sheets),
            selectedRow = newSelectedRow,
            formulaInput = if (selectedCell.formula.isNotEmpty()) selectedCell.formula else selectedCell.value
        )
    }

    fun insertColumn(targetCol: Int? = null) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        val cToInsert = targetCol ?: currentState.selectedCol
        val newCells = mutableMapOf<String, Cell>()

        for ((_, cell) in currentSheet.cells) {
            if (cell.col >= cToInsert) {
                val shifted = cell.copy(col = cell.col + 1)
                newCells[Sheet.getCellKey(shifted.row, shifted.col)] = shifted
            } else {
                newCells[Sheet.getCellKey(cell.row, cell.col)] = cell
            }
        }

        val updatedSheet = currentSheet.copy(colCount = currentSheet.colCount + 1, cells = newCells)
        sheets[sheetIndex] = updatedSheet
        _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
    }

    fun deleteColumn(targetCol: Int? = null) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        if (currentSheet.colCount <= 1) return

        val cToDelete = targetCol ?: currentState.selectedCol
        val newCells = mutableMapOf<String, Cell>()

        for ((_, cell) in currentSheet.cells) {
            when {
                cell.col == cToDelete -> {
                    // Deleted cell
                }
                cell.col > cToDelete -> {
                    val shifted = cell.copy(col = cell.col - 1)
                    newCells[Sheet.getCellKey(shifted.row, shifted.col)] = shifted
                }
                else -> {
                    newCells[Sheet.getCellKey(cell.row, cell.col)] = cell
                }
            }
        }

        val newColCount = currentSheet.colCount - 1
        val updatedSheet = currentSheet.copy(colCount = newColCount, cells = newCells)
        sheets[sheetIndex] = updatedSheet

        val newSelectedCol = currentState.selectedCol.coerceAtMost(newColCount - 1)
        val selectedCell = updatedSheet.getCell(currentState.selectedRow, newSelectedCol)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(sheets = sheets),
            selectedCol = newSelectedCol,
            formulaInput = if (selectedCell.formula.isNotEmpty()) selectedCell.formula else selectedCell.value
        )
    }

    fun switchSheet(index: Int) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        if (index !in currentState.document.sheets.indices) return
        val newSheet = currentState.document.sheets[index]
        val selectedCell = newSheet.getCell(0, 0)

        _uiState.value = currentState.copy(
            activeSheetIndex = index,
            selectedRow = 0,
            selectedCol = 0,
            formulaInput = if (selectedCell.formula.isNotEmpty()) selectedCell.formula else selectedCell.value,
            isEditingCell = false
        )
    }

    fun addSheet(name: String? = null) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()

        val sheetName = name?.trim()?.ifEmpty { null } ?: run {
            var counter = sheets.size + 1
            var candidate = "Sheet$counter"
            while (sheets.any { it.name.equals(candidate, ignoreCase = true) }) {
                counter++
                candidate = "Sheet$counter"
            }
            candidate
        }

        val newSheet = Sheet(name = sheetName, rowCount = 40, colCount = 12)
        sheets.add(newSheet)
        val newIndex = sheets.lastIndex

        _uiState.value = currentState.copy(
            document = currentState.document.copy(sheets = sheets),
            activeSheetIndex = newIndex,
            selectedRow = 0,
            selectedCol = 0,
            formulaInput = "",
            isEditingCell = false
        )
    }

    fun renameSheet(index: Int, newName: String) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || index !in currentState.document.sheets.indices) return

        val sheets = currentState.document.sheets.toMutableList()
        sheets[index] = sheets[index].copy(name = trimmed)
        _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
    }

    fun deleteSheet(index: Int) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        if (currentState.document.sheets.size <= 1 || index !in currentState.document.sheets.indices) return

        val sheets = currentState.document.sheets.toMutableList()
        sheets.removeAt(index)
        val newIndex = currentState.activeSheetIndex.coerceAtMost(sheets.lastIndex)
        val activeSheet = sheets[newIndex]
        val selectedCell = activeSheet.getCell(0, 0)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(sheets = sheets),
            activeSheetIndex = newIndex,
            selectedRow = 0,
            selectedCol = 0,
            formulaInput = if (selectedCell.formula.isNotEmpty()) selectedCell.formula else selectedCell.value,
            isEditingCell = false
        )
    }

    fun insertFormulaSnippet(snippet: String) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        _uiState.value = currentState.copy(formulaInput = "=$snippet()")
    }

    fun saveSpreadsheet() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val docToSave = currentState.document
        viewModelScope.launch {
            val result = documentRepository.saveSpreadsheet(
                Uri.parse(docToSave.fileUri),
                docToSave
            )
            val statusMsg = if (result.isSuccess) "Spreadsheet Saved!" else "Failed to Save Spreadsheet"
            _uiState.update { current ->
                if (current is ExcelUiState.Success) {
                    current.copy(saveStatus = statusMsg)
                } else {
                    current
                }
            }
        }
    }
}

