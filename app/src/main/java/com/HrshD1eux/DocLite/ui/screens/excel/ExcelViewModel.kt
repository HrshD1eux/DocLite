package com.HrshD1eux.DocLite.ui.screens.excel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.Cell
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.Sheet
import com.HrshD1eux.DocLite.models.SpreadsheetDocument
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExcelUiState {
    data object Loading : ExcelUiState
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

    private val _uiState = MutableStateFlow<ExcelUiState>(ExcelUiState.Loading)
    val uiState: StateFlow<ExcelUiState> = _uiState.asStateFlow()

    fun loadSpreadsheet(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExcelUiState.Loading
            val result = documentRepository.loadSpreadsheet(uri)
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
                _uiState.value = ExcelUiState.Error(err.message ?: "Failed to open Spreadsheet")
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
            updatedCells[key] = Cell(row = r, col = c, value = "", formula = input)
        } else {
            updatedCells[key] = Cell(row = r, col = c, value = input, formula = "")
        }

        val updatedSheet = currentSheet.copy(cells = updatedCells)
        sheets[sheetIndex] = updatedSheet
        val updatedDoc = currentState.document.copy(sheets = sheets)

        _uiState.value = currentState.copy(
            document = updatedDoc,
            isEditingCell = false
        )
    }

    fun insertRow() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        sheets[sheetIndex] = currentSheet.copy(rowCount = currentSheet.rowCount + 1)
        _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
    }

    fun deleteRow() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        if (currentSheet.rowCount > 1) {
            sheets[sheetIndex] = currentSheet.copy(rowCount = currentSheet.rowCount - 1)
            _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
        }
    }

    fun insertColumn() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        sheets[sheetIndex] = currentSheet.copy(colCount = currentSheet.colCount + 1)
        _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
    }

    fun deleteColumn() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        val sheets = currentState.document.sheets.toMutableList()
        val sheetIndex = currentState.activeSheetIndex
        val currentSheet = sheets.getOrNull(sheetIndex) ?: return

        if (currentSheet.colCount > 1) {
            sheets[sheetIndex] = currentSheet.copy(colCount = currentSheet.colCount - 1)
            _uiState.value = currentState.copy(document = currentState.document.copy(sheets = sheets))
        }
    }

    fun insertFormulaSnippet(snippet: String) {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        _uiState.value = currentState.copy(formulaInput = "=$snippet()")
    }

    fun saveSpreadsheet() {
        val currentState = _uiState.value as? ExcelUiState.Success ?: return
        viewModelScope.launch {
            val result = documentRepository.saveSpreadsheet(
                Uri.parse(currentState.document.fileUri),
                currentState.document
            )
            if (result.isSuccess) {
                _uiState.value = currentState.copy(saveStatus = "Spreadsheet Saved!")
            } else {
                _uiState.value = currentState.copy(saveStatus = "Failed to Save Spreadsheet")
            }
        }
    }
}

