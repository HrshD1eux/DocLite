package com.HrshD1eux.DocLite

import android.app.Application
import com.HrshD1eux.DocLite.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DocLiteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Seed initial sample documents asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            container.fileRepository.seedInitialSampleDocumentsIfNeeded()
        }
    }
}

