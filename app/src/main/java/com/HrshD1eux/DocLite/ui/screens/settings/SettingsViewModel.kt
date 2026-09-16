package com.HrshD1eux.DocLite.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.DocLite.models.AppSettings
import com.HrshD1eux.DocLite.models.FontSizeMode
import com.HrshD1eux.DocLite.models.StartScreen
import com.HrshD1eux.DocLite.models.ThemeMode
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.repository.SettingsRepository
import com.HrshD1eux.DocLite.core.update.AppUpdateInfo
import com.HrshD1eux.DocLite.core.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState
    data class ReadyToInstall(val apkFile: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = settingsRepository.appSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

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

    fun clearRecentFilesHistory() {
        viewModelScope.launch {
            fileRepository.clearRecentFiles()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            val result = updateManager.checkForUpdate()
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _updateState.value = UpdateUiState.UpdateAvailable(info)
                } else {
                    _updateState.value = UpdateUiState.UpToDate(info.currentVersion)
                }
            }.onFailure { err ->
                _updateState.value = UpdateUiState.Error(err.localizedMessage ?: "Failed to check for updates")
            }
        }
    }

    fun startDownload(downloadUrl: String) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(0f, 0L, 0L)
            val result = updateManager.downloadApk(downloadUrl) { progress, downloaded, total ->
                _updateState.value = UpdateUiState.Downloading(progress, downloaded, total)
            }
            result.onSuccess { file ->
                _updateState.value = UpdateUiState.ReadyToInstall(file)
                installApk(file)
            }.onFailure { err ->
                _updateState.value = UpdateUiState.Error(err.localizedMessage ?: "Download failed")
            }
        }
    }

    fun installApk(file: File) {
        if (!updateManager.canRequestPackageInstalls()) {
            updateManager.openInstallPermissionSettings()
        } else {
            updateManager.installApk(file)
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Idle
    }
}

