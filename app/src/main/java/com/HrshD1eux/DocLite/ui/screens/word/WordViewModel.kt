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
import com.HrshD1eux.DocLite.models.WordTable
import com.HrshD1eux.DocLite.models.WordTableCell
import com.HrshD1eux.DocLite.models.WordTableRow
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
import kotlinx.coroutines.withTimeoutOrNull

sealed interface WordUiState {
    data object Loading : WordUiState
    data class Success(
        val document: WordDocument,
        val isEditing: Boolean = false,
        val activeElementIndex: Int = 0,
        val currentAlignment: TextAlignment = TextAlignment.LEFT,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isHeader: Boolean = false,
        val headerLevel: Int = 0,
        val isBullet: Boolean = false,
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

    private var undoDebounceJob: Job? = null
    private var pendingUndoSnapshot: List<WordBodyElement>? = null
    private var currentLoadingJob: Job? = null
    private var currentLoadingUri: Uri? = null

    fun loadDocument(uri: Uri) {
        if (currentLoadingUri == uri && _uiState.value is WordUiState.Success) {
            return
        }
        currentLoadingJob?.cancel()
        currentLoadingUri = uri

        currentLoadingJob = viewModelScope.launch {
            _uiState.value = WordUiState.Loading
            val scaleFactor = try {
                withTimeoutOrNull(250L) {
                    settingsRepository?.appSettingsFlow?.first()
                }?.fontSizeMode?.scaleFactor ?: 1.0f
            } catch (e: Exception) { 1.0f }
            val baseFontSizeSp = 16f * scaleFactor

            val result = documentRepository.loadWordDocument(uri)
            result.onSuccess { doc ->
                val isNewDoc = doc.paragraphs.size <= 1 && (
                    doc.paragraphs.firstOrNull()?.getPlainText()?.contains("Welcome to your new DocLite document") == true ||
                    doc.paragraphs.firstOrNull()?.getPlainText()?.isBlank() == true
                )
                val firstPara = doc.paragraphs.firstOrNull()
                _uiState.value = WordUiState.Success(
                    document = doc,
                    fontSizeSp = firstPara?.runs?.firstOrNull()?.style?.fontSizeSp ?: baseFontSizeSp,
                    currentAlignment = firstPara?.alignment ?: TextAlignment.LEFT,
                    isBold = firstPara?.runs?.any { it.style.isBold } == true,
                    isItalic = firstPara?.runs?.any { it.style.isItalic } == true,
                    isUnderline = firstPara?.runs?.any { it.style.isUnderline } == true,
                    isHeader = firstPara?.isHeader == true,
                    headerLevel = firstPara?.headerLevel ?: 0,
                    isBullet = firstPara?.bulletPrefix != null,
                    isEditing = isNewDoc
                )
                try {
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
                } catch (t: Throwable) {
                    // Ignored: recording recent file must not fail document opening
                }
            }.onFailure { err ->
                _uiState.value = WordUiState.Error(err.message ?: "Failed to open Word document")
            }
        }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        _uiState.value = currentState.copy(isEditing = !currentState.isEditing)
    }

    fun setActiveElement(index: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val elements = currentState.document.bodyElements
        val clampedIndex = index.coerceIn(0, (elements.size - 1).coerceAtLeast(0))
        val elem = elements.getOrNull(clampedIndex)

        if (elem is WordBodyElement.ParagraphElement) {
            val p = elem.paragraph
            val run = p.runs.firstOrNull()
            _uiState.value = currentState.copy(
                activeElementIndex = clampedIndex,
                currentAlignment = p.alignment,
                isBold = p.runs.any { it.style.isBold },
                isItalic = p.runs.any { it.style.isItalic },
                isUnderline = p.runs.any { it.style.isUnderline },
                isHeader = p.isHeader,
                headerLevel = p.headerLevel,
                isBullet = p.bulletPrefix != null,
                fontSizeSp = run?.style?.fontSizeSp ?: 16f,
                fontColorHex = run?.style?.fontColorHex ?: "#1C1B1F"
            )
        } else {
            _uiState.value = currentState.copy(activeElementIndex = clampedIndex)
        }
    }

    fun updateParagraphText(paragraphIndex: Int, text: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val currentElements = currentState.document.bodyElements

        // Map paragraphIndex to bodyElements index
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

        val existingRuns = p.runs
        val updatedRuns = if (existingRuns.size == 1) {
            listOf(existingRuns[0].copy(text = text))
        } else if (existingRuns.isEmpty()) {
            listOf(TextRun(text = text))
        } else {
            // Preserve first run style for single-line paragraph edits
            listOf(TextRun(text = text, style = existingRuns.first().style))
        }

        val updatedParagraph = p.copy(runs = updatedRuns, rawXml = null)
        val updatedElements = currentElements.toMutableList()
        updatedElements[bodyIndex] = WordBodyElement.ParagraphElement(updatedParagraph)

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
            activeElementIndex = bodyIndex,
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
            activeElementIndex = updatedElements.lastIndex,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun insertParagraphAt(paragraphIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        commitUndoImmediate(currentState.document.bodyElements)

        val currentElements = currentState.document.bodyElements.toMutableList()
        var paraCount = 0
        var insertAfterIndex = currentElements.size
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
            activeElementIndex = insertAfterIndex,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun deleteParagraph(paragraphIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (currentState.document.paragraphs.size <= 1) return

        commitUndoImmediate(currentState.document.bodyElements)
        val currentElements = currentState.document.bodyElements.toMutableList()

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

        val removedParagraph = (currentElements[removeIndex] as WordBodyElement.ParagraphElement).paragraph
        val removedText = removedParagraph.getPlainText()
        val removedWords = if (removedText.isBlank()) 0 else removedText.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
        val removedChars = removedText.length

        currentElements.removeAt(removeIndex)
        val nextActive = removeIndex.coerceAtMost(currentElements.lastIndex)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(
                bodyElements = currentElements,
                wordCount = (currentState.document.wordCount - removedWords).coerceAtLeast(0),
                characterCount = (currentState.document.characterCount - removedChars).coerceAtLeast(0)
            ),
            activeElementIndex = nextActive,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun duplicateParagraph(index: Int = -1) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val targetIndex = if (index >= 0) index else currentState.activeElementIndex
        val elem = currentState.document.bodyElements.getOrNull(targetIndex) ?: return

        commitUndoImmediate(currentState.document.bodyElements)
        val currentElements = currentState.document.bodyElements.toMutableList()

        val duplicated = when (elem) {
            is WordBodyElement.ParagraphElement -> {
                val p = elem.paragraph
                WordBodyElement.ParagraphElement(
                    p.copy(id = java.util.UUID.randomUUID().toString(), rawXml = null)
                )
            }
            is WordBodyElement.TableElement -> {
                WordBodyElement.TableElement(
                    elem.table.copy(id = java.util.UUID.randomUUID().toString(), rawXml = null)
                )
            }
            is WordBodyElement.ImageElement -> {
                elem.copy(id = java.util.UUID.randomUUID().toString())
            }
            is WordBodyElement.PageBreakElement -> {
                elem.copy(id = java.util.UUID.randomUUID().toString())
            }
        }

        currentElements.add(targetIndex + 1, duplicated)
        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = currentElements),
            activeElementIndex = targetIndex + 1,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun insertPageBreak(afterIndex: Int = -1) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val targetIdx = if (afterIndex >= 0) afterIndex else currentState.activeElementIndex
        commitUndoImmediate(currentState.document.bodyElements)

        val currentElements = currentState.document.bodyElements.toMutableList()
        val insertAt = (targetIdx + 1).coerceIn(0, currentElements.size)
        currentElements.add(insertAt, WordBodyElement.PageBreakElement())

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = currentElements),
            activeElementIndex = insertAt,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun deletePageBreak(elementIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (elementIndex !in currentState.document.bodyElements.indices) return
        commitUndoImmediate(currentState.document.bodyElements)

        val currentElements = currentState.document.bodyElements.toMutableList()
        currentElements.removeAt(elementIndex)
        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = currentElements),
            activeElementIndex = (elementIndex - 1).coerceAtLeast(0),
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun toggleBold() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val newBold = !currentState.isBold
        applyFormattingToActiveParagraph { p ->
            val updatedRuns = p.runs.map { run ->
                run.copy(style = run.style.copy(isBold = newBold))
            }
            p.copy(runs = updatedRuns)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(isBold = newBold) ?: state
        }
    }

    fun toggleItalic() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val newItalic = !currentState.isItalic
        applyFormattingToActiveParagraph { p ->
            val updatedRuns = p.runs.map { run ->
                run.copy(style = run.style.copy(isItalic = newItalic))
            }
            p.copy(runs = updatedRuns)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(isItalic = newItalic) ?: state
        }
    }

    fun toggleUnderline() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val newUnderline = !currentState.isUnderline
        applyFormattingToActiveParagraph { p ->
            val updatedRuns = p.runs.map { run ->
                run.copy(style = run.style.copy(isUnderline = newUnderline))
            }
            p.copy(runs = updatedRuns)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(isUnderline = newUnderline) ?: state
        }
    }

    fun setAlignment(alignment: TextAlignment) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        applyFormattingToActiveParagraph { p ->
            p.copy(alignment = alignment)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(currentAlignment = alignment) ?: state
        }
    }

    fun toggleHeading(level: Int = 1) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val isCurrentlyThisHeading = currentState.isHeader && currentState.headerLevel == level
        val newHeader = !isCurrentlyThisHeading
        val newLevel = if (newHeader) level else 0

        applyFormattingToActiveParagraph { p ->
            p.copy(
                isHeader = newHeader,
                headerLevel = newLevel
            )
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(isHeader = newHeader, headerLevel = newLevel) ?: state
        }
    }

    fun toggleBullet() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val newBullet = !currentState.isBullet
        applyFormattingToActiveParagraph { p ->
            p.copy(bulletPrefix = if (newBullet) "• " else null)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(isBullet = newBullet) ?: state
        }
    }

    fun setFontSize(sp: Float) {
        val clampedSp = sp.coerceIn(8f, 72f)
        applyFormattingToActiveParagraph { p ->
            val updatedRuns = p.runs.map { run ->
                run.copy(style = run.style.copy(fontSizeSp = clampedSp))
            }
            p.copy(runs = updatedRuns)
        }
        _uiState.update { state ->
            (state as? WordUiState.Success)?.copy(fontSizeSp = clampedSp) ?: state
        }
    }

    fun insertTable(rows: Int = 3, cols: Int = 3, afterIndex: Int = -1) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        commitUndoImmediate(currentState.document.bodyElements)

        val targetIndex = if (afterIndex >= 0) afterIndex else currentState.activeElementIndex
        val currentElements = currentState.document.bodyElements.toMutableList()

        val tableRows = (0 until rows).map { rIdx ->
            val cells = (0 until cols).map { cIdx ->
                WordTableCell(
                    text = if (rIdx == 0) "Header ${cIdx + 1}" else "Cell ${rIdx},${cIdx + 1}",
                    isHeader = rIdx == 0
                )
            }
            WordTableRow(cells = cells)
        }
        val newTableElem = WordBodyElement.TableElement(WordTable(rows = tableRows))
        val insertPos = (targetIndex + 1).coerceIn(0, currentElements.size)
        currentElements.add(insertPos, newTableElem)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = currentElements),
            activeElementIndex = insertPos,
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun updateTableCell(elementIndex: Int, rowIndex: Int, colIndex: Int, text: String) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val elem = currentState.document.bodyElements.getOrNull(elementIndex) as? WordBodyElement.TableElement ?: return
        val table = elem.table

        if (rowIndex !in table.rows.indices) return
        val row = table.rows[rowIndex]
        if (colIndex !in row.cells.indices) return

        commitUndoImmediate(currentState.document.bodyElements)
        val updatedCells = row.cells.toMutableList()
        updatedCells[colIndex] = updatedCells[colIndex].copy(text = text)

        val updatedRows = table.rows.toMutableList()
        updatedRows[rowIndex] = row.copy(cells = updatedCells)

        val updatedElements = currentState.document.bodyElements.toMutableList()
        updatedElements[elementIndex] = WordBodyElement.TableElement(table.copy(rows = updatedRows, rawXml = null))

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = updatedElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun addTableRow(elementIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val elem = currentState.document.bodyElements.getOrNull(elementIndex) as? WordBodyElement.TableElement ?: return
        val table = elem.table
        val colCount = table.rows.firstOrNull()?.cells?.size ?: 2

        commitUndoImmediate(currentState.document.bodyElements)
        val newRow = WordTableRow(cells = (0 until colCount).map { WordTableCell(text = "") })
        val updatedRows = table.rows + newRow

        val updatedElements = currentState.document.bodyElements.toMutableList()
        updatedElements[elementIndex] = WordBodyElement.TableElement(table.copy(rows = updatedRows, rawXml = null))

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = updatedElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun deleteTableRow(elementIndex: Int, rowIndex: Int) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val elem = currentState.document.bodyElements.getOrNull(elementIndex) as? WordBodyElement.TableElement ?: return
        val table = elem.table
        if (table.rows.size <= 1 || rowIndex !in table.rows.indices) return

        commitUndoImmediate(currentState.document.bodyElements)
        val updatedRows = table.rows.toMutableList().apply { removeAt(rowIndex) }

        val updatedElements = currentState.document.bodyElements.toMutableList()
        updatedElements[elementIndex] = WordBodyElement.TableElement(table.copy(rows = updatedRows, rawXml = null))

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = updatedElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    private fun applyFormattingToActiveParagraph(transform: (Paragraph) -> Paragraph) {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        val elements = currentState.document.bodyElements
        val activeIdx = currentState.activeElementIndex

        val elem = elements.getOrNull(activeIdx) as? WordBodyElement.ParagraphElement ?: return
        commitUndoImmediate(elements)

        val updatedParagraph = transform(elem.paragraph).copy(rawXml = null)
        val updatedElements = elements.toMutableList()
        updatedElements[activeIdx] = WordBodyElement.ParagraphElement(updatedParagraph)

        _uiState.value = currentState.copy(
            document = currentState.document.copy(bodyElements = updatedElements),
            canUndo = undoStack.isNotEmpty()
        )
    }

    fun undo() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        flushPendingUndo(currentState.document.bodyElements)

        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentState.document.bodyElements)

            val nextActive = currentState.activeElementIndex.coerceIn(0, (previous.size - 1).coerceAtLeast(0))
            _uiState.value = currentState.copy(
                document = currentState.document.copy(bodyElements = previous),
                activeElementIndex = nextActive,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
            setActiveElement(nextActive)
        }
    }

    fun redo() {
        val currentState = _uiState.value as? WordUiState.Success ?: return
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentState.document.bodyElements)

            val nextActive = currentState.activeElementIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
            _uiState.value = currentState.copy(
                document = currentState.document.copy(bodyElements = next),
                activeElementIndex = nextActive,
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
            setActiveElement(nextActive)
        }
    }

    private fun commitUndoImmediate(elements: List<WordBodyElement>) {
        flushPendingUndo(elements)
        commitUndo(elements)
    }

    private fun flushPendingUndo(currentElements: List<WordBodyElement>) {
        undoDebounceJob?.cancel()
        undoDebounceJob = null
        pendingUndoSnapshot?.let { snapshot ->
            if (snapshot !== currentElements) {
                commitUndo(snapshot)
            }
            pendingUndoSnapshot = null
        }
    }

    private fun commitUndo(elements: List<WordBodyElement>) {
        if (undoStack.size >= 50) {
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
