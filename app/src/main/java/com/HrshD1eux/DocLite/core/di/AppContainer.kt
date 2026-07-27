package com.HrshD1eux.DocLite.core.di

import android.content.Context
import com.HrshD1eux.DocLite.database.AppDatabase
import com.HrshD1eux.DocLite.repository.DocumentRepository
import com.HrshD1eux.DocLite.repository.FileRepository
import com.HrshD1eux.DocLite.repository.SettingsRepository

class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val fileRepository: FileRepository by lazy {
        FileRepository(
            context = context,
            recentFileDao = database.recentFileDao(),
            favoriteFileDao = database.favoriteFileDao(),
            passwordProtectionDao = database.passwordProtectionDao()
        )
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            context = context,
            pdfAnnotationDao = database.pdfAnnotationDao()
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }
}

