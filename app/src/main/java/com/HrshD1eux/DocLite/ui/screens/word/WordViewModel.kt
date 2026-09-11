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
import com.HrshD1eux.DocLite.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<WordUiState>(WordUiState.Loading)
    val uiState: StateFlow<WordUiState> = _uiState.asStateFlow()

    private var undoStack = mutableListOf<List<Paragraph>>()
    private var redoStack = mutableListOf<List<Paragraph>>()

    fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = WordUiState.Loading
            val scaleFactor = try {
                settingsRepository?.appSettingsFlow?.first()?.fontSizeMode?.scaleFactor ?: 1.0f
            } catch (e: Exception) { 1.0f }
            val baseFontSizeSp = 16f * scaleFactor

            val result = documentRepository.loadWordDocument(uri)
            result.onSuccess { doc ->
                val isNewDoc = doc.paragraphs.size <= 1 && (
                    doc.paragraphs.firstOrNull()?.getPlainText()?.contains("Welcome to your new DocLite document") == true ||
                    doc.paragraphs.firstOrNull()?.getPlainText()?.isBlank() == true
                )
                _uiState.value = WordUiState.Success(
                    document = doc,
                    fontSizeSp = baseFontSizeSp,
                    isEditing = isNewDoc
                )
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
            val p = currentParagraphs[index]
            val oldText = p.getPlainText()
            if (oldText == text) return

            pushUndo(currentParagraphs)

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

            val oldWords = if (oldText.isBlank()) 0 else oldText.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
            val newWords = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
            val wordDelta = newWords - oldWords
            val charDelta = text.length - oldText.length

            val updatedDoc = currentState.document.copy(
                paragraphs = currentParagraphs,
                wordCount = (currentState.document.wordCount + wordDelta).coerceAtLeast(0),
                characterCount = (currentState.document.characterCount + charDelta).coerceAtLeast(0)
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
        if (undoStack.size >= 25) {
            undoStack.removeAt(0)
        }
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

    fun renameDocument(newName: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        viewModelScope.launch {
            val formattedName = if (newName.endsWith(".docx", ignoreCase = true)) newName else "$newName.docx"
            val uri = Uri.parse(currentState.document.fileUri)
            val dummyFile = DocumentFile(
                id = currentState.document.fileUri,
                name = currentState.document.title,
                path = uri.path ?: "",
                uriString = currentState.document.fileUri,
                sizeBytes = 0,
                lastModified = System.currentTimeMillis(),
                format = DocumentFormat.WORD
            )
            val success = fileRepository.renameFile(dummyFile, formattedName)
            if (success) {
                val oldFile = java.io.File(uri.path ?: "")
                val newFile = java.io.File(oldFile.parentFile, formattedName)
                val newUri = Uri.fromFile(newFile).toString()
                val updatedDoc = currentState.document.copy(
                    title = formattedName,
                    fileUri = newUri
                )
                _uiState.value = currentState.copy(
                    document = updatedDoc,
                    saveStatus = "Renamed to $formattedName"
                )
            } else {
                _uiState.value = currentState.copy(saveStatus = "Failed to rename document")
            }
        }
    }
}

