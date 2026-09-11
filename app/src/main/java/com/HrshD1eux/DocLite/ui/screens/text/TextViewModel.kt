package com.HrshD1eux.DocLite.ui.screens.text

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface TextUiState {
    data object Loading : TextUiState
    data class Success(
        val fileUri: Uri,
        val fileName: String,
        val text: String,
        val isModified: Boolean = false,
        val wordCount: Int = 0,
        val charCount: Int = 0,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val statusMessage: String? = null
    ) : TextUiState
    data class Error(val message: String) : TextUiState
}

class TextViewModel(
    private val context: Context,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextUiState>(TextUiState.Loading)
    val uiState: StateFlow<TextUiState> = _uiState.asStateFlow()

    private var originalText: String = ""
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun loadTextFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = TextUiState.Loading
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: ""
                }
                originalText = content
                undoStack.clear()
                redoStack.clear()

                val fileName = getFileName(uri)
                val words = calculateWords(content)
                val chars = content.length

                _uiState.value = TextUiState.Success(
                    fileUri = uri,
                    fileName = fileName,
                    text = content,
                    isModified = false,
                    wordCount = words,
                    charCount = chars,
                    canUndo = false,
                    canRedo = false
                )

                // Record in recent files
                fileRepository.recordRecentFile(
                    DocumentFile(
                        id = uri.toString(),
                        name = fileName,
                        path = uri.path ?: "",
                        uriString = uri.toString(),
                        sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                        lastModified = System.currentTimeMillis(),
                        format = DocumentFormat.TXT
                    )
                )
            } catch (e: Exception) {
                _uiState.value = TextUiState.Error("Failed to load text file: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun updateText(newText: String) {
        val currentState = _uiState.value as? TextUiState.Success ?: return
        if (currentState.text == newText) return

        if (undoStack.isEmpty() || undoStack.last() != currentState.text) {
            undoStack.add(currentState.text)
            if (undoStack.size > 50) undoStack.removeAt(0)
        }
        redoStack.clear()

        _uiState.value = currentState.copy(
            text = newText,
            isModified = newText != originalText,
            wordCount = calculateWords(newText),
            charCount = newText.length,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    fun undo() {
        val currentState = _uiState.value as? TextUiState.Success ?: return
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentState.text)

            _uiState.value = currentState.copy(
                text = prev,
                isModified = prev != originalText,
                wordCount = calculateWords(prev),
                charCount = prev.length,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        val currentState = _uiState.value as? TextUiState.Success ?: return
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentState.text)

            _uiState.value = currentState.copy(
                text = next,
                isModified = next != originalText,
                wordCount = calculateWords(next),
                charCount = next.length,
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun saveText() {
        val currentState = _uiState.value as? TextUiState.Success ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = currentState.fileUri
                    context.contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(currentState.text)
                    } ?: throw java.io.IOException("Cannot open output stream for $uri")
                }
                originalText = currentState.text
                _uiState.value = currentState.copy(
                    isModified = false,
                    statusMessage = "File saved successfully"
                )
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    statusMessage = "Error saving file: ${e.localizedMessage}"
                )
            }
        }
    }

    fun renameDocument(newName: String) {
        val currentState = _uiState.value as? TextUiState.Success ?: return
        viewModelScope.launch {
            val formattedName = if (newName.endsWith(".txt", ignoreCase = true)) newName else "$newName.txt"
            val targetFile = DocumentFile(
                id = currentState.fileUri.toString(),
                name = currentState.fileName,
                path = currentState.fileUri.path ?: "",
                uriString = currentState.fileUri.toString(),
                sizeBytes = 0,
                lastModified = System.currentTimeMillis(),
                format = DocumentFormat.TXT
            )
            val success = fileRepository.renameFile(targetFile, formattedName)
            if (success) {
                val newFile = File(File(currentState.fileUri.path ?: "").parentFile, formattedName)
                val newUri = Uri.fromFile(newFile)
                _uiState.value = currentState.copy(
                    fileUri = newUri,
                    fileName = formattedName,
                    statusMessage = "Renamed to $formattedName"
                )
            } else {
                _uiState.value = currentState.copy(statusMessage = "Failed to rename file")
            }
        }
    }

    private fun calculateWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split("\\s+".toRegex()).count { it.isNotBlank() }
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
        return result ?: "document.txt"
    }
}
