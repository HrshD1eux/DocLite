package com.HrshD1eux.DocLite.bankstatement.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.bankstatement.model.StatementAnalysisResult
import com.HrshD1eux.DocLite.bankstatement.parser.BankStatementParser
import com.HrshD1eux.DocLite.bankstatement.parser.PasswordRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BankStatementUiState {
    data object Idle : BankStatementUiState
    data class Loading(val message: String) : BankStatementUiState
    data class PasswordRequired(val fileUri: Uri, val fileName: String, val errorMessage: String? = null) : BankStatementUiState
    data class Success(
        val analysisResult: StatementAnalysisResult,
        val searchQuery: String = "",
        val activeTab: Int = 0
    ) : BankStatementUiState
    data class Error(val message: String) : BankStatementUiState
}

class BankStatementViewModel(context: Context) : ViewModel() {

    private val parser = BankStatementParser(context)

    private val _uiState = MutableStateFlow<BankStatementUiState>(BankStatementUiState.Idle)
    val uiState: StateFlow<BankStatementUiState> = _uiState.asStateFlow()

    fun analyzeStatement(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = BankStatementUiState.Loading("Decrypting and processing statement data...")
            try {
                val result = parser.parseStatement(uri, password)
                _uiState.value = BankStatementUiState.Success(analysisResult = result)
            } catch (e: PasswordRequiredException) {
                val fileName = uri.lastPathSegment ?: "Excel File"
                _uiState.value = BankStatementUiState.PasswordRequired(
                    fileUri = uri,
                    fileName = fileName,
                    errorMessage = if (password != null) "Incorrect password. Please try again." else null
                )
            } catch (e: Exception) {
                _uiState.value = BankStatementUiState.Error(
                    e.localizedMessage ?: "Failed to process bank statement. Please verify the Excel format."
                )
            }
        }
    }

    fun submitPassword(uri: Uri, password: String) {
        if (password.isBlank()) {
            val fileName = uri.lastPathSegment ?: "Excel File"
            _uiState.value = BankStatementUiState.PasswordRequired(
                fileUri = uri,
                fileName = fileName,
                errorMessage = "Password cannot be empty"
            )
            return
        }
        analyzeStatement(uri, password)
    }

    // Removed loadSampleStatement as it was using fake data

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        if (currentState is BankStatementUiState.Success) {
            _uiState.value = currentState.copy(searchQuery = query)
        }
    }

    fun setActiveTab(tabIndex: Int) {
        val currentState = _uiState.value
        if (currentState is BankStatementUiState.Success) {
            _uiState.value = currentState.copy(activeTab = tabIndex)
        }
    }

    fun reset() {
        _uiState.value = BankStatementUiState.Idle
    }
}

