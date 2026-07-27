package com.HrshD1eux.DocLite.ui.screens.word

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.Paragraph
import com.HrshD1eux.DocLite.models.TextAlignment
import com.HrshD1eux.DocLite.models.TextRun
import com.HrshD1eux.DocLite.models.TextStyle
import com.HrshD1eux.DocLite.models.WordDocument
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WordUiState {
    data object Loading : WordUiState
    data class Success(
        val document: WordDocument,
        val isEditing: Boolean = false,
        val currentAlignment: TextAlignment = TextAlignment.LEFT,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val fontSizeSp: Float = 16f,
        val fontColorHex: String = "#1C1B1F",
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val saveStatus: String? = null
    ) : WordUiState
    data class Error(val message: String) : WordUiState
}

class WordViewModel(
    private val documentRepository: DocumentRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WordUiState>(WordUiState.Loading)
    val uiState: StateFlow<WordUiState> = _uiState.asStateFlow()

    private var undoStack = mutableListOf<List<Paragraph>>()
    private var redoStack = mutableListOf<List<Paragraph>>()

    fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = WordUiState.Loading
            val result = documentRepository.loadWordDocument(uri)
            result.onSuccess { doc ->
                _uiState.value = WordUiState.Success(document = doc)
                // Record in recent files
                fileRepository.recordRecentFile(
                    DocumentFile(
                        id = uri.toString(),
                        name = doc.title,
                        path = uri.path ?: "",
                        uriString = uri.toString(),
                        sizeBytes = 1024,
                        lastModified = System.currentTimeMillis(),
                        format = DocumentFormat.WORD
                    )
                )
            }.onFailure { err ->
                _uiState.value = WordUiState.Error(err.message ?: "Failed to open Word document")
            }
        }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(isEditing = !currentState.isEditing)
    }

    fun updateParagraphText(index: Int, text: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val currentParagraphs = currentState.document.paragraphs.toMutableList()
        if (index in currentParagraphs.indices) {
            pushUndo(currentParagraphs)

            val p = currentParagraphs[index]
            val updatedRun = TextRun(
                text = text,
                style = TextStyle(
                    isBold = currentState.isBold,
                    isItalic = currentState.isItalic,
                    isUnderline = currentState.isUnderline,
                    fontSizeSp = currentState.fontSizeSp,
                    fontColorHex = currentState.fontColorHex
                )
            )
            currentParagraphs[index] = p.copy(runs = listOf(updatedRun))

            val wordCount = currentParagraphs.sumOf { p -> p.getPlainText().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size }
            val charCount = currentParagraphs.sumOf { p -> p.getPlainText().length }

            val updatedDoc = currentState.document.copy(
                paragraphs = currentParagraphs,
                wordCount = wordCount,
                characterCount = charCount
            )

            _uiState.value = currentState.copy(
                document = updatedDoc,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun addParagraph() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        pushUndo(currentState.document.paragraphs)

        val updatedParagraphs = currentState.document.paragraphs + Paragraph(runs = listOf(TextRun("")))
        _uiState.value = currentState.copy(
            document = currentState.document.copy(paragraphs = updatedParagraphs),
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun setAlignment(alignment: TextAlignment) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(currentAlignment = alignment)
    }

    fun toggleBold() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(isBold = !currentState.isBold)
    }

    fun toggleItalic() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(isItalic = !currentState.isItalic)
    }

    fun toggleUnderline() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(isUnderline = !currentState.isUnderline)
    }

    fun setFontSize(sp: Float) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(fontSizeSp = sp)
    }

    fun undo() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentState.document.paragraphs)

            _uiState.value = currentState.copy(
                document = currentState.document.copy(paragraphs = previous),
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentState.document.paragraphs)

            _uiState.value = currentState.copy(
                document = currentState.document.copy(paragraphs = next),
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    private fun pushUndo(paragraphs: List<Paragraph>) {
        undoStack.add(paragraphs.toList())
        redoStack.clear()
    }

    fun saveDocument() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        viewModelScope.launch {
            val result = documentRepository.saveWordDocument(
                Uri.parse(currentState.document.fileUri),
                currentState.document
            )
            if (result.isSuccess) {
                _uiState.value = currentState.copy(saveStatus = "Document Saved!")
            } else {
                _uiState.value = currentState.copy(saveStatus = "Failed to Save Document")
            }
        }
    }
}

