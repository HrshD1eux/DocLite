package com.HrshD1eux.DocLite.ui.screens.image

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImageUiState {
    data object Loading : ImageUiState
    data class Success(
        val uri: Uri,
        val rotationDegrees: Float = 0f,
        val fileName: String = ""
    ) : ImageUiState
}

class ImageViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageUiState>(ImageUiState.Loading)
    val uiState: StateFlow<ImageUiState> = _uiState.asStateFlow()

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            val name = uri.lastPathSegment ?: "image.jpg"
            _uiState.value = ImageUiState.Success(uri = uri, fileName = name)

            fileRepository.recordRecentFile(
                DocumentFile(
                    id = uri.toString(),
                    name = name,
                    path = uri.path ?: "",
                    uriString = uri.toString(),
                    sizeBytes = 2048,
                    lastModified = System.currentTimeMillis(),
                    format = DocumentFormat.IMAGE
                )
            )
        }
    }

    fun rotateRight() {
        val currentState = _uiState.value as? ImageUiState.Success ?: return
        val newDeg = (currentState.rotationDegrees + 90f) % 360f
        _uiState.value = currentState.copy(rotationDegrees = newDeg)
    }
}

