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

        // Configure StAX XML engine for Apache POI on Android ART
        try {
            System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
            Thread.currentThread().contextClassLoader = this.classLoader
        } catch (t: Throwable) {
            Log.w("DocLiteApplication", "Could not set StAX system properties: ${t.message}")
        }

        container = AppContainer(this)

        // Purge expired trash documents (>30 days retention) asynchronously on app launch
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.fileRepository.purgeExpiredTrash()
            } catch (t: Throwable) {
                Log.w("DocLiteApplication", "Trash cleanup on launch skipped: ${t.message}")
            }
        }
    }
}

