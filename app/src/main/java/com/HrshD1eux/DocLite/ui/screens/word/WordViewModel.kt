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
import com.HrshD1eux.DocLite.models.WordBodyElement
import com.HrshD1eux.DocLite.models.WordDocument
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    private var undoStack = mutableListOf<List<WordBodyElement>>()
    private var redoStack = mutableListOf<List<WordBodyElement>>()

    // Debounce job for coalescing undo snapshots
    private var undoDebounceJob: Job? = null
    // Snapshot captured before the current burst of typing
    private var pendingUndoSnapshot: List<WordBodyElement>? = null

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

    /**
     * Updates the text of a paragraph at the given index within bodyElements.
     * Preserves existing run styles for single-run paragraphs instead of
     * replacing with global toolbar state.
     */
    fun updateParagraphText(paragraphIndex: Int, text: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val currentElements = currentState.document.bodyElements

        // Find the N-th paragraph element in bodyElements
        var paraCount = 0
        var bodyIndex = -1
        for (i in currentElements.indices) {
            if (currentElements[i] is WordBodyElement.ParagraphElement) {
                if (paraCount == paragraphIndex) {
                    bodyIndex = i
                    break
                }
                paraCount++
            }
        }
        if (bodyIndex == -1) return

        val paraElement = currentElements[bodyIndex] as WordBodyElement.ParagraphElement
        val p = paraElement.paragraph
        val oldText = p.getPlainText()
        if (oldText == text) return

        // Capture undo snapshot with debouncing — only one snapshot per typing burst
        if (pendingUndoSnapshot == null) {
            pendingUndoSnapshot = currentElements.toList()
        }
        undoDebounceJob?.cancel()
        undoDebounceJob = viewModelScope.launch {
            delay(800)
            pendingUndoSnapshot?.let { snapshot ->
                commitUndo(snapshot)
                pendingUndoSnapshot = null
            }
        }

        // Preserve existing run style for single-run paragraphs.
        // Multi-run paragraphs collapse to one run but keep the first run's style.
        val existingRuns = p.runs
        val updatedRuns = if (existingRuns.size == 1) {
            listOf(existingRuns[0].copy(text = text))
        } else {
            listOf(TextRun(text = text, style = existingRuns.firstOrNull()?.style ?: TextStyle()))
        }

        val updatedParagraph = p.copy(runs = updatedRuns)
        val updatedElements = currentElements.toMutableList()
        updatedElements[bodyIndex] = WordBodyElement.ParagraphElement(updatedParagraph)

        // Update word/char counts
        val oldWords = if (oldText.isBlank()) 0 else oldText.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
        val newWords = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
        val wordDelta = newWords - oldWords
        val charDelta = text.length - oldText.length

        val updatedDoc = currentState.document.copy(
            bodyElements = updatedElements,
            wordCount = (currentState.document.wordCount + wordDelta).coerceAtLeast(0),
            characterCount = (currentState.document.characterCount + charDelta).coerceAtLeast(0)
        )

        _uiState.value = currentState.copy(
            document = updatedDoc,
            canUndo = undoStack.isNotEmpty() || pendingUndoSnapshot != null,
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun addParagraph() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        commitUndoImmediate(currentState.document.bodyElements)

        val newElement = WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun(""))))
        val updatedElements = currentState.document.bodyElements + newElement
        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = updatedElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    /**
     * Inserts a new empty paragraph at the given paragraph index position.
     */
    fun insertParagraphAt(paragraphIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        commitUndoImmediate(currentState.document.bodyElements)

        val currentElements = currentState.document.bodyElements.toMutableList()

        // Find the bodyElements index corresponding to this paragraph index
        var paraCount = 0
        var insertAfterIndex = currentElements.size // default: append at end
        for (i in currentElements.indices) {
            if (currentElements[i] is WordBodyElement.ParagraphElement) {
                if (paraCount == paragraphIndex) {
                    insertAfterIndex = i
                    break
                }
                paraCount++
            }
        }

        val newElement = WordBodyElement.ParagraphElement(Paragraph(runs = listOf(TextRun(""))))
        currentElements.add(insertAfterIndex, newElement)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = currentElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    /**
     * Deletes the paragraph at the given index. Prevents deleting the last paragraph.
     */
    fun deleteParagraph(paragraphIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (currentState.document.paragraphs.size <= 1) return // Keep at least one paragraph

        commitUndoImmediate(currentState.document.bodyElements)

        val currentElements = currentState.document.bodyElements.toMutableList()

        // Find the bodyElements index corresponding to this paragraph index
        var paraCount = 0
        var removeIndex = -1
        for (i in currentElements.indices) {
            if (currentElements[i] is WordBodyElement.ParagraphElement) {
                if (paraCount == paragraphIndex) {
                    removeIndex = i
                    break
                }
                paraCount++
            }
        }
        if (removeIndex == -1) return

        // Recalculate word/char counts for the removed paragraph
        val removedParagraph = (currentElements[removeIndex] as WordBodyElement.ParagraphElement).paragraph
        val removedText = removedParagraph.getPlainText()
        val removedWords = if (removedText.isBlank()) 0 else removedText.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
        val removedChars = removedText.length

        currentElements.removeAt(removeIndex)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(
                bodyElements = currentElements,
                wordCount = (currentState.document.wordCount - removedWords).coerceAtLeast(0),
                characterCount = (currentState.document.characterCount - removedChars).coerceAtLeast(0)
            ),
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

        // Flush any pending debounced snapshot first
        flushPendingUndo(currentState.document.bodyElements)

        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentState.document.bodyElements)

            _uiState.value = currentState.copy(
                document = currentState.document.copy(bodyElements = previous),
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentState.document.bodyElements)

            _uiState.value = currentState.copy(
                document = currentState.document.copy(bodyElements = next),
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    /** Immediately commits an undo snapshot (for structural actions like add/delete paragraph). */
    private fun commitUndoImmediate(elements: List<WordBodyElement>) {
        // Flush any pending debounced snapshot first
        flushPendingUndo(elements)
        commitUndo(elements)
    }

    /** Flushes the pending debounced undo snapshot if one exists. */
    private fun flushPendingUndo(currentElements: List<WordBodyElement>) {
        undoDebounceJob?.cancel()
        undoDebounceJob = null
        pendingUndoSnapshot?.let { snapshot ->
            // Only commit if the snapshot differs from current state
            if (snapshot !== currentElements) {
                commitUndo(snapshot)
            }
            pendingUndoSnapshot = null
        }
    }

    private fun commitUndo(elements: List<WordBodyElement>) {
        if (undoStack.size >= 25) {
            undoStack.removeAt(0)
        }
        undoStack.add(elements.toList())
        redoStack.clear()
    }

    fun saveDocument() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        viewModelScope.launch {
            val result = documentRepository.saveWordDocument(
                Uri.parse(currentState.document.fileUri),
                currentState.document
            )
            // Only update saveStatus — don't replace entire state to avoid overwriting concurrent edits
            _uiState.update { state ->
                (state as? WordUiState.Success)?.copy(
                    saveStatus = if (result.isSuccess) "Document Saved!"
                    else "Failed to Save: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                ) ?: state
            }
        }
    }

    fun renameDocument(newName: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        viewModelScope.launch {
            val formattedName = if (newName.endsWith(".docx", ignoreCase = true)) newName else "$newName.docx"
            val uri = Uri.parse(currentState.document.fileUri)

            val isLocalFile = uri.scheme == "file"
            if (isLocalFile) {
                val targetFile = DocumentFile(
                    id = currentState.document.fileUri,
                    name = currentState.document.title,
                    path = uri.path ?: "",
                    uriString = currentState.document.fileUri,
                    sizeBytes = 0,
                    lastModified = System.currentTimeMillis(),
                    format = DocumentFormat.WORD
                )
                val success = fileRepository.renameFile(targetFile, formattedName)
                if (success) {
                    // Safe to reconstruct URI for file:// scheme
                    val oldFile = java.io.File(uri.path!!)
                    val newFile = java.io.File(oldFile.parentFile, formattedName)
                    val newUri = Uri.fromFile(newFile).toString()
                    _uiState.update { state ->
                        (state as? WordUiState.Success)?.copy(
                            document = currentState.document.copy(title = formattedName, fileUri = newUri),
                            saveStatus = "Renamed to $formattedName"
                        ) ?: state
                    }
                } else {
                    _uiState.update { state ->
                        (state as? WordUiState.Success)?.copy(saveStatus = "Failed to rename document") ?: state
                    }
                }
            } else {
                // For content:// URIs, filesystem rename is not possible — update display title only
                _uiState.update { state ->
                    (state as? WordUiState.Success)?.copy(
                        document = currentState.document.copy(title = formattedName),
                        saveStatus = "Renamed to $formattedName"
                    ) ?: state
                }
            }
        }
    }
}
