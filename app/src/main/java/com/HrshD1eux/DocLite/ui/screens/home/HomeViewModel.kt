package com.HrshD1eux.DocLite.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.AppSettings
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val recentFiles: List<DocumentFile>,
        val favoriteFiles: List<DocumentFile>,
        val filteredFiles: List<DocumentFile>,
        val searchQuery: String,
        val selectedCategory: DocumentFormat?,
        val appSettings: AppSettings
    ) : HomeUiState
}

class HomeViewModel(
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedCategoryFlow = MutableStateFlow<DocumentFormat?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        fileRepository.recentFilesFlow,
        fileRepository.favoriteFilesFlow,
        fileRepository.allDocumentsFlow,
        searchQueryFlow,
        selectedCategoryFlow,
        settingsRepository.appSettingsFlow
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val recents = flows[0] as List<DocumentFile>
        @Suppress("UNCHECKED_CAST")
        val favorites = flows[1] as List<DocumentFile>
        @Suppress("UNCHECKED_CAST")
        val allDocs = flows[2] as List<DocumentFile>
        val query = flows[3] as String
        val category = flows[4] as DocumentFormat?
        val settings = flows[5] as AppSettings

        val filtered = if (category != null) {
            allDocs.filter { file ->
                val matchesQuery = query.isBlank() || file.name.contains(query, ignoreCase = true)
                matchesQuery && file.format == category
            }
        } else if (query.isNotBlank()) {
            allDocs.filter { file ->
                file.name.contains(query, ignoreCase = true)
            }
        } else {
            recents.ifEmpty { allDocs }
        }

        HomeUiState.Success(
            recentFiles = recents,
            favoriteFiles = favorites,
            filteredFiles = filtered,
            searchQuery = query,
            selectedCategory = category,
            appSettings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    fun refreshScan() {
        fileRepository.refreshScan()
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun onCategorySelect(category: DocumentFormat?) {
        selectedCategoryFlow.value = if (selectedCategoryFlow.value == category) null else category
    }

    fun toggleFavorite(file: DocumentFile) {
        viewModelScope.launch {
            fileRepository.toggleFavorite(file)
        }
    }

    fun createNewDocument(
        name: String,
        format: DocumentFormat,
        password: String? = null,
        onCreated: (DocumentFile) -> Unit
    ) {
        viewModelScope.launch {
            val doc = fileRepository.createNewDocument(name, format, password)
            onCreated(doc)
        }
    }

    fun verifyPassword(file: DocumentFile, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isValid = fileRepository.verifyFilePassword(file.uriString, password)
            onResult(isValid)
        }
    }

    fun setFilePassword(file: DocumentFile, password: String) {
        viewModelScope.launch {
            fileRepository.setFilePassword(file.uriString, password)
        }
    }

    fun removeFilePassword(file: DocumentFile) {
        viewModelScope.launch {
            fileRepository.removeFilePassword(file.uriString)
        }
    }

    fun renameDocument(file: DocumentFile, newName: String) {
        viewModelScope.launch {
            fileRepository.renameFile(file, newName)
            refreshScan()
        }
    }

    fun deleteDocument(file: DocumentFile) {
        viewModelScope.launch {
            fileRepository.deleteFile(file)
            refreshScan()
        }
    }
}

