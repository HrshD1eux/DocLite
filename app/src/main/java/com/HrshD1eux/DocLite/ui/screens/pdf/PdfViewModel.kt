package com.HrshD1eux.DocLite.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.AnnotationType
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.DrawingPoint
import com.HrshD1eux.DocLite.models.PdfAnnotation
import com.HrshD1eux.DocLite.models.PdfSearchResult
import com.HrshD1eux.DocLite.office.pdf.PasswordRequiredException
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PdfUiState {
    data object Loading : PdfUiState
    data class PasswordRequired(
        val uri: Uri,
        val errorMessage: String? = null
    ) : PdfUiState
    data class Success(
        val uri: Uri,
        val pageCount: Int,
        val currentPageIndex: Int = 0,
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
    // With RGB_565 (3.3MB per page), 32MB+ holds 10+ pages comfortably
    private val cacheSizeKb = (maxMemoryKb / 6).coerceAtLeast(32 * 1024)

    private val pageCache = object : LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var searchJob: Job? = null

    fun openPdf(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            pageCache.evictAll()
            if (password == null) {
                _uiState.value = PdfUiState.Loading
            }

            try {
                val pageCount = documentRepository.pdfEngine.openPdf(uri, password)
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
            } catch (e: PasswordRequiredException) {
                _uiState.value = PdfUiState.PasswordRequired(
                    uri = uri,
                    errorMessage = if (password != null) "Invalid password. Please try again." else null
                )
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.message ?: "Failed to open PDF document.")
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
                _uiState.update { state ->
                    (state as? PdfUiState.Success)?.copy(annotations = annotations) ?: state
                }
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
            selectedAnnotationTool = if (!newMode) null else (currentState.selectedAnnotationTool ?: AnnotationType.HIGHLIGHT)
        )
    }

    fun selectTool(tool: AnnotationType?) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        _uiState.value = currentState.copy(
            selectedAnnotationTool = if (currentState.selectedAnnotationTool == tool) null else tool
        )
    }

    fun addHighlightAnnotation(
        pageIndex: Int,
        boundsLeftRatio: Float = 0.08f,
        boundsTopRatio: Float = 0.15f,
        boundsWidthRatio: Float = 0.84f,
        boundsHeightRatio: Float = 0.04f,
        colorHex: String = "#FFEB3B"
    ) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = currentState.uri.toString(),
                pageIndex = pageIndex,
                type = AnnotationType.HIGHLIGHT,
                colorHex = colorHex,
                boundsLeftRatio = boundsLeftRatio,
                boundsTopRatio = boundsTopRatio,
                boundsWidthRatio = boundsWidthRatio,
                boundsHeightRatio = boundsHeightRatio
            )
            documentRepository.savePdfAnnotation(annotation)
            _uiState.update { state ->
                (state as? PdfUiState.Success)?.copy(statusMessage = "Highlight added") ?: state
            }
        }
    }

    fun addStickyNoteAnnotation(
        pageIndex: Int,
        text: String,
        boundsLeftRatio: Float = 0.7f,
        boundsTopRatio: Float = 0.1f
    ) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = currentState.uri.toString(),
                pageIndex = pageIndex,
                type = AnnotationType.STICKY_NOTE,
                colorHex = "#2196F3",
                noteText = text,
                boundsLeftRatio = boundsLeftRatio,
                boundsTopRatio = boundsTopRatio,
                boundsWidthRatio = 0.25f,
                boundsHeightRatio = 0.08f
            )
            documentRepository.savePdfAnnotation(annotation)
            _uiState.update { state ->
                (state as? PdfUiState.Success)?.copy(statusMessage = "Sticky Note added") ?: state
            }
        }
    }

    fun addFreeDrawAnnotation(
        pageIndex: Int,
        points: List<DrawingPoint>,
        colorHex: String = "#F44336",
        strokeWidthDp: Float = 3f
    ) {
        if (points.size < 2) return
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = currentState.uri.toString(),
                pageIndex = pageIndex,
                type = AnnotationType.FREE_DRAW,
                colorHex = colorHex,
                strokeWidthDp = strokeWidthDp,
                points = points
            )
            documentRepository.savePdfAnnotation(annotation)
            _uiState.update { state ->
                (state as? PdfUiState.Success)?.copy(statusMessage = "Drawing saved") ?: state
            }
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            documentRepository.deletePdfAnnotation(id)
            _uiState.update { state ->
                (state as? PdfUiState.Success)?.copy(statusMessage = "Annotation deleted") ?: state
            }
        }
    }

    fun prepareSharePdf(onReady: (Uri) -> Unit) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        viewModelScope.launch {
            val exportUri = documentRepository.pdfEngine.exportAnnotatedPdf(
                currentState.uri,
                currentState.annotations
            )
            onReady(exportUri)
        }
    }

    fun toggleSearch(active: Boolean? = null) {
        val currentState = _uiState.value as? PdfUiState.Success ?: return
        val newActive = active ?: !currentState.isSearchActive
        if (!newActive) {
            clearSearch()
            _uiState.value = currentState.copy(isSearchActive = false)
        } else {
            _uiState.value = currentState.copy(isSearchActive = true)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { state ->
            (state as? PdfUiState.Success)?.copy(
                searchQuery = "",
                searchResults = emptyList(),
                currentMatchIndex = 0,
                isSearching = false
            ) ?: state
        }
    }

    fun performSearch(query: String) {
        searchJob?.cancel()
        val currentState = _uiState.value as? PdfUiState.Success ?: return

        if (query.isBlank()) {
            clearSearch()
            return
        }

        _uiState.value = currentState.copy(searchQuery = query, isSearching = true)

        // Debounce keystrokes by 350ms to prevent ANR and OOM on large PDFs
        searchJob = viewModelScope.launch {
            delay(350)
            val results = documentRepository.pdfEngine.searchInPdf(query)
            _uiState.update { state ->
                if (state is PdfUiState.Success && state.searchQuery == query) {
                    state.copy(
                        searchResults = results,
                        currentMatchIndex = 0,
                        isSearching = false
                    )
                } else state
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
        searchJob?.cancel()
        pageCache.evictAll()
        documentRepository.pdfEngine.close()
    }
}
