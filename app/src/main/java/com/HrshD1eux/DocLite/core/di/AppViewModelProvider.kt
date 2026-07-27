package com.HrshD1eux.DocLite.core.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.HrshD1eux.DocLite.DocLiteApplication
import com.HrshD1eux.DocLite.bankstatement.ui.BankStatementViewModel
import com.HrshD1eux.DocLite.ui.screens.excel.ExcelViewModel
import com.HrshD1eux.DocLite.ui.screens.filemanager.FileManagerViewModel
import com.HrshD1eux.DocLite.ui.screens.home.HomeViewModel
import com.HrshD1eux.DocLite.ui.screens.image.ImageViewModel
import com.HrshD1eux.DocLite.ui.screens.pdf.PdfViewModel
import com.HrshD1eux.DocLite.ui.screens.powerpoint.PowerPointViewModel
import com.HrshD1eux.DocLite.ui.screens.settings.SettingsViewModel
import com.HrshD1eux.DocLite.ui.screens.word.WordViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                fileRepository = docLiteApplication().container.fileRepository,
                settingsRepository = docLiteApplication().container.settingsRepository
            )
        }
        initializer {
            WordViewModel(
                documentRepository = docLiteApplication().container.documentRepository,
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            ExcelViewModel(
                documentRepository = docLiteApplication().container.documentRepository,
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            PowerPointViewModel(
                documentRepository = docLiteApplication().container.documentRepository,
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            PdfViewModel(
                documentRepository = docLiteApplication().container.documentRepository,
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            ImageViewModel(
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            FileManagerViewModel(
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            SettingsViewModel(
                settingsRepository = docLiteApplication().container.settingsRepository,
                fileRepository = docLiteApplication().container.fileRepository
            )
        }
        initializer {
            BankStatementViewModel(
                context = docLiteApplication().applicationContext
            )
        }
    }
}

fun CreationExtras.docLiteApplication(): DocLiteApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DocLiteApplication)

