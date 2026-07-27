package com.HrshD1eux.DocLite.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.AppSettings
import com.HrshD1eux.DocLite.models.FontSizeMode
import com.HrshD1eux.DocLite.models.StartScreen
import com.HrshD1eux.DocLite.models.ThemeMode
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = settingsRepository.appSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateThemeMode(mode)
        }
    }

    fun setFontSize(size: FontSizeMode) {
        viewModelScope.launch {
            settingsRepository.updateFontSize(size)
        }
    }

    fun setStartScreen(screen: StartScreen) {
        viewModelScope.launch {
            settingsRepository.updateStartScreen(screen)
        }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoSave(enabled)
        }
    }

    fun clearRecentFilesHistory() {
        viewModelScope.launch {
            fileRepository.clearRecentFiles()
        }
    }
}

