package com.HrshD1eux.DocLite.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
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
        val searchQuery: String = "",
        val searchResults: List<PdfSearchResult> = emptyList(),
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

    private val pageCache = mutableMapOf<Int, Bitmap>()

    fun openPdf(uri: Uri) {
        viewModelScope.launch {
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

    suspend fun getPage(pageIndex: Int): Bitmap? {
        if (pageCache.containsKey(pageIndex)) {
            return pageCache[pageIndex]
        }
        val currentState = _uiState.value as? PdfUiState.Success ?: return null
        if (pageIndex in 0 until currentState.pageCount) {
            return try {
                val bitmap = documentRepository.pdfEngine.renderPage(pageIndex)
                if (bitmap != null) {
                    pageCache[pageIndex] = bitmap
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

    fun performSearch(query: String) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        _uiState.value = currentState.copy(searchQuery = query, isSearching = true)

        viewModelScope.launch {
            val results = documentRepository.pdfEngine.searchInPdf(query)
            _uiState.value = currentState.copy(
                searchQuery = query,
                searchResults = results,
                isSearching = false
            )
        }
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "document.pdf"
    }
}

