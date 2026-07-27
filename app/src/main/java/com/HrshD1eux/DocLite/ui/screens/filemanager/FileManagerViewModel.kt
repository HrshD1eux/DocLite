package com.HrshD1eux.DocLite.ui.screens.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SortOption {
    NAME, DATE, SIZE, TYPE
}

sealed interface FileManagerUiState {
    data object Loading : FileManagerUiState
    data class Success(
        val files: List<DocumentFile>,
        val searchQuery: String = "",
        val currentSort: SortOption = SortOption.DATE,
        val isAscending: Boolean = false,
        val actionMessage: String? = null
    ) : FileManagerUiState
}

class FileManagerViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FileManagerUiState>(FileManagerUiState.Loading)
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            val rawFiles = fileRepository.listLocalDocuments()
            val currentState = _uiState.value as? FileManagerUiState.Success
            val query = currentState?.searchQuery ?: ""
            val sort = currentState?.currentSort ?: SortOption.DATE
            val asc = currentState?.isAscending ?: false

            val filtered = filterAndSortFiles(rawFiles, query, sort, asc)
            _uiState.value = FileManagerUiState.Success(
                files = filtered,
                searchQuery = query,
                currentSort = sort,
                isAscending = asc
            )
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value as? FileManagerUiState.Success ?: return
        viewModelScope.launch {
            val rawFiles = fileRepository.listLocalDocuments()
            val filtered = filterAndSortFiles(rawFiles, query, currentState.currentSort, currentState.isAscending)
            _uiState.value = currentState.copy(files = filtered, searchQuery = query)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        val currentState = _uiState.value as? FileManagerUiState.Success ?: return
        val newAsc = if (currentState.currentSort == sortOption) !currentState.isAscending else false
        viewModelScope.launch {
            val rawFiles = fileRepository.listLocalDocuments()
            val filtered = filterAndSortFiles(rawFiles, currentState.searchQuery, sortOption, newAsc)
            _uiState.value = currentState.copy(files = filtered, currentSort = sortOption, isAscending = newAsc)
        }
    }

    fun renameFile(file: DocumentFile, newName: String) {
        viewModelScope.launch {
            val success = fileRepository.renameFile(file, newName)
            loadFiles()
            val currentState = _uiState.value as? FileManagerUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(
                    actionMessage = if (success) "Renamed successfully" else "Failed to rename"
                )
            }
        }
    }

    fun deleteFile(file: DocumentFile) {
        viewModelScope.launch {
            val success = fileRepository.deleteFile(file)
            loadFiles()
            val currentState = _uiState.value as? FileManagerUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(
                    actionMessage = if (success) "File deleted" else "Failed to delete"
                )
            }
        }
    }

    fun toggleFavorite(file: DocumentFile) {
        viewModelScope.launch {
            fileRepository.toggleFavorite(file)
            loadFiles()
        }
    }

    fun setFilePassword(file: DocumentFile, password: String) {
        viewModelScope.launch {
            fileRepository.setFilePassword(file.uriString, password)
            loadFiles()
            val currentState = _uiState.value as? FileManagerUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(actionMessage = "Password protected successfully")
            }
        }
    }

    fun removeFilePassword(file: DocumentFile) {
        viewModelScope.launch {
            fileRepository.removeFilePassword(file.uriString)
            loadFiles()
            val currentState = _uiState.value as? FileManagerUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(actionMessage = "Password protection removed")
            }
        }
    }

    fun verifyPassword(file: DocumentFile, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isValid = fileRepository.verifyFilePassword(file.uriString, password)
            onResult(isValid)
        }
    }

    private fun filterAndSortFiles(
        files: List<DocumentFile>,
        query: String,
        sort: SortOption,
        ascending: Boolean
    ): List<DocumentFile> {
        val matching = files.filter { it.name.contains(query, ignoreCase = true) }
        val sorted = when (sort) {
            SortOption.NAME -> if (ascending) matching.sortedBy { it.name } else matching.sortedByDescending { it.name }
            SortOption.DATE -> if (ascending) matching.sortedBy { it.lastModified } else matching.sortedByDescending { it.lastModified }
            SortOption.SIZE -> if (ascending) matching.sortedBy { it.sizeBytes } else matching.sortedByDescending { it.sizeBytes }
            SortOption.TYPE -> if (ascending) matching.sortedBy { it.format.displayName } else matching.sortedByDescending { it.format.displayName }
        }
        return sorted
    }
}

