package com.HrshD1eux.DocLite.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.AnnotationType
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.PdfAnnotation
import com.HrshD1eux.DocLite.models.PdfSearchResult
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PdfUiState {
    data object Loading : PdfUiState
    data class Success(
        val uri: Uri,
        val pageCount: Int,
        val currentPageIndex: Int = 0,
        val currentPageBitmap: Bitmap? = null,
        val annotations: List<PdfAnnotation> = emptyList(),
        val selectedAnnotationTool: AnnotationType? = null,
        val isAnnotationMode: Boolean = false,
        val isSearchActive: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<PdfSearchResult> = emptyList(),
        val currentMatchIndex: Int = 0,
        val isSearching: Boolean = false,
        val statusMessage: String? = null
    ) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

class PdfViewModel(
    private val documentRepository: DocumentRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceAtLeast(16 * 1024)

    private val pageCache = object : LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun openPdf(uri: Uri) {
        viewModelScope.launch {
            pageCache.evictAll()
            _uiState.value = PdfUiState.Loading
            val pageCount = documentRepository.pdfEngine.openPdf(uri)
            if (pageCount > 0) {
                _uiState.value = PdfUiState.Success(
                    uri = uri,
                    pageCount = pageCount
                )

                fileRepository.recordRecentFile(
                    DocumentFile(
                        id = uri.toString(),
                        name = getFileName(uri),
                        path = uri.path ?: "",
                        uriString = uri.toString(),
                        sizeBytes = 10240,
                        lastModified = System.currentTimeMillis(),
                        format = DocumentFormat.PDF,
                        pageCount = pageCount
                    )
                )

                observeAnnotations(uri.toString())
            } else {
                _uiState.value = PdfUiState.Error("Unable to load PDF document.")
            }
        }
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float {
        return documentRepository.pdfEngine.getPageAspectRatio(pageIndex)
    }

    suspend fun getPage(pageIndex: Int): Bitmap? {
        val cached = pageCache.get(pageIndex)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        val currentState = _uiState.value as? PdfUiState.Success ?: return null
        if (pageIndex in 0 until currentState.pageCount) {
            return try {
                val bitmap = documentRepository.pdfEngine.renderPage(pageIndex)
                if (bitmap != null) {
                    pageCache.put(pageIndex, bitmap)
                }
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    private fun observeAnnotations(fileUri: String) {
        viewModelScope.launch {
            documentRepository.getPdfAnnotationsFlow(fileUri).collect { annotations ->
                val currentState = _uiState.value as? PdfUiState.Success ?: return@collect
                _uiState.value = currentState.copy(annotations = annotations)
            }
        }
    }

    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        if (pageIndex in 0 until currentState.pageCount) {
            _uiState.value = currentState.copy(currentPageIndex = pageIndex)
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        val clamped = pageIndex.coerceIn(0, (currentState.pageCount - 1).coerceAtLeast(0))
        _uiState.value = currentState.copy(currentPageIndex = clamped)
    }

    fun toggleAnnotationMode(enabled: Boolean? = null) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        val newMode = enabled ?: !currentState.isAnnotationMode
        _uiState.value = currentState.copy(
            isAnnotationMode = newMode,
            selectedAnnotationTool = if (!newMode) null else currentState.selectedAnnotationTool
        )
    }

    fun selectTool(tool: AnnotationType?) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        _uiState.value = currentState.copy(
            selectedAnnotationTool = if (currentState.selectedAnnotationTool == tool) null else tool
        )
    }

    fun addHighlightAnnotation(pageIndex: Int) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = currentState.uri.toString(),
                pageIndex = pageIndex,
                type = AnnotationType.HIGHLIGHT,
                colorHex = "#FFEB3B",
                boundsLeftRatio = 0.1f,
                boundsTopRatio = 0.2f,
                boundsWidthRatio = 0.8f,
                boundsHeightRatio = 0.05f
            )
            documentRepository.savePdfAnnotation(annotation)
            _uiState.value = currentState.copy(statusMessage = "Highlight added")
        }
    }

    fun addStickyNoteAnnotation(pageIndex: Int, text: String) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = currentState.uri.toString(),
                pageIndex = pageIndex,
                type = AnnotationType.STICKY_NOTE,
                colorHex = "#2196F3",
                noteText = text,
                boundsLeftRatio = 0.7f,
                boundsTopRatio = 0.1f,
                boundsWidthRatio = 0.2f,
                boundsHeightRatio = 0.1f
            )
            documentRepository.savePdfAnnotation(annotation)
            _uiState.value = currentState.copy(statusMessage = "Sticky Note added")
        }
    }

    fun toggleSearch(active: Boolean? = null) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        val newActive = active ?: !currentState.isSearchActive
        if (!newActive) {
            _uiState.value = currentState.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = emptyList(),
                currentMatchIndex = 0,
                isSearching = false
            )
        } else {
            _uiState.value = currentState.copy(isSearchActive = true)
        }
    }

    fun clearSearch() {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        _uiState.value = currentState.copy(
            searchQuery = "",
            searchResults = emptyList(),
            currentMatchIndex = 0,
            isSearching = false
        )
    }

    fun performSearch(query: String) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        if (query.isBlank()) {
            clearSearch()
            return
        }
        _uiState.value = currentState.copy(searchQuery = query, isSearching = true)

        viewModelScope.launch {
            val results = documentRepository.pdfEngine.searchInPdf(query)
            val state = _uiState.value as? PdfUiState.Success ?: return@launch
            if (state.searchQuery == query) {
                _uiState.value = state.copy(
                    searchResults = results,
                    currentMatchIndex = 0,
                    isSearching = false
                )
            }
        }
    }

    fun nextSearchMatch(): Int? {
        val currentState = _uiState.value as? PdfUiState.Success ?: return null
        if (currentState.searchResults.isEmpty()) return null
        val nextIndex = (currentState.currentMatchIndex + 1) % currentState.searchResults.size
        _uiState.value = currentState.copy(currentMatchIndex = nextIndex)
        return currentState.searchResults[nextIndex].pageIndex
    }

    fun previousSearchMatch(): Int? {
        val currentState = _uiState.value as? PdfUiState.Success ?: return null
        if (currentState.searchResults.isEmpty()) return null
        val prevIndex = if (currentState.currentMatchIndex - 1 < 0) {
            currentState.searchResults.size - 1
        } else {
            currentState.currentMatchIndex - 1
        }
        _uiState.value = currentState.copy(currentMatchIndex = prevIndex)
        return currentState.searchResults[prevIndex].pageIndex
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "document.pdf"
    }

    override fun onCleared() {
        super.onCleared()
        pageCache.evictAll()
        documentRepository.pdfEngine.close()
    }
}

