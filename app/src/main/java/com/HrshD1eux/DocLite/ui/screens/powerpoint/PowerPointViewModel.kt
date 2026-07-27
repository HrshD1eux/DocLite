package com.HrshD1eux.DocLite.ui.screens.powerpoint

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.ElementType
import com.HrshD1eux.DocLite.models.PresentationDocument
import com.HrshD1eux.DocLite.models.Slide
import com.HrshD1eux.DocLite.models.SlideElement
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PowerPointUiState {
    data object Loading : PowerPointUiState
    data class Success(
        val document: PresentationDocument,
        val activeSlideIndex: Int = 0,
        val isEditing: Boolean = false,
        val saveStatus: String? = null
    ) : PowerPointUiState
    data class Error(val message: String) : PowerPointUiState
}

class PowerPointViewModel(
    private val documentRepository: DocumentRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PowerPointUiState>(PowerPointUiState.Loading)
    val uiState: StateFlow<PowerPointUiState> = _uiState.asStateFlow()

    fun loadPresentation(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = PowerPointUiState.Loading
            val result = documentRepository.loadPresentation(uri)
            result.onSuccess { doc ->
                _uiState.value = PowerPointUiState.Success(document = doc)
                fileRepository.recordRecentFile(
                    DocumentFile(
                        id = uri.toString(),
                        name = doc.title,
                        path = uri.path ?: "",
                        uriString = uri.toString(),
                        sizeBytes = 3072,
                        lastModified = System.currentTimeMillis(),
                        format = DocumentFormat.POWERPOINT
                    )
                )
            }.onFailure { err ->
                _uiState.value = PowerPointUiState.Error(err.message ?: "Failed to open PowerPoint presentation")
            }
        }
    }

    fun selectSlide(index: Int) {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        if (index in currentState.document.slides.indices) {
            _uiState.value = currentState.copy(activeSlideIndex = index)
        }
    }

    fun updateElementText(slideIndex: Int, elementId: String, newText: String) {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        val slides = currentState.document.slides.toMutableList()
        val currentSlide = slides.getOrNull(slideIndex) ?: return

        val updatedElements = currentSlide.elements.map { elem ->
            if (elem.id == elementId) elem.copy(textContent = newText) else elem
        }

        slides[slideIndex] = currentSlide.copy(elements = updatedElements)
        _uiState.value = currentState.copy(document = currentState.document.copy(slides = slides))
    }

    fun addSlide() {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        val slides = currentState.document.slides.toMutableList()
        val newSlideNum = slides.size + 1
        val newSlide = Slide(
            slideNumber = newSlideNum,
            title = "Slide $newSlideNum",
            elements = listOf(
                SlideElement(type = ElementType.TITLE, textContent = "New Slide Title", fontSizeSp = 28f),
                SlideElement(type = ElementType.BODY_TEXT, textContent = "Tap to add presentation bullet points", fontSizeSp = 18f)
            )
        )
        slides.add(newSlide)
        _uiState.value = currentState.copy(
            document = currentState.document.copy(slides = slides),
            activeSlideIndex = slides.lastIndex
        )
    }

    fun deleteSlide() {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        if (currentState.document.slides.size > 1) {
            val slides = currentState.document.slides.toMutableList()
            slides.removeAt(currentState.activeSlideIndex)
            val newIdx = (currentState.activeSlideIndex - 1).coerceAtLeast(0)
            _uiState.value = currentState.copy(
                document = currentState.document.copy(slides = slides),
                activeSlideIndex = newIdx
            )
        }
    }

    fun moveSlideUp() {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        val idx = currentState.activeSlideIndex
        if (idx > 0) {
            val slides = currentState.document.slides.toMutableList()
            val temp = slides[idx]
            slides[idx] = slides[idx - 1]
            slides[idx - 1] = temp
            _uiState.value = currentState.copy(
                document = currentState.document.copy(slides = slides),
                activeSlideIndex = idx - 1
            )
        }
    }

    fun moveSlideDown() {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        val idx = currentState.activeSlideIndex
        if (idx < currentState.document.slides.lastIndex) {
            val slides = currentState.document.slides.toMutableList()
            val temp = slides[idx]
            slides[idx] = slides[idx + 1]
            slides[idx + 1] = temp
            _uiState.value = currentState.copy(
                document = currentState.document.copy(slides = slides),
                activeSlideIndex = idx + 1
            )
        }
    }

    fun savePresentation() {
        val currentState = _uiState.value as? PowerPointUiState.Success ?: return
        viewModelScope.launch {
            val result = documentRepository.savePresentation(
                Uri.parse(currentState.document.fileUri),
                currentState.document
            )
            if (result.isSuccess) {
                _uiState.value = currentState.copy(saveStatus = "Presentation Saved!")
            } else {
                _uiState.value = currentState.copy(saveStatus = "Failed to Save Presentation")
            }
        }
    }
}

