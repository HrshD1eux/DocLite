package com.HrshD1eux.DocLite

import android.app.Application
import com.HrshD1eux.DocLite.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.util.Log

class DocLiteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Seed initial sample documents asynchronously and safely
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.fileRepository.seedInitialSampleDocumentsIfNeeded()
            } catch (t: Throwable) {
                Log.w("DocLiteApplication", "Sample document seeding skipped or failed safely: ${t.message}")
            }
        }
    }
}

