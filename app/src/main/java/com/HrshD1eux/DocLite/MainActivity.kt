package com.HrshD1eux.DocLite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.ThemeMode
import com.HrshD1eux.DocLite.ui.navigation.DocLiteNavigation
import com.HrshD1eux.DocLite.ui.theme.DocLiteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DocLiteApplication
        var initialIntentUri: Uri? = null
        var initialIntentFormat: DocumentFormat? = null

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            try {
                if (uri.scheme == "content") {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            initialIntentUri = uri
            
            val mimeType = contentResolver.getType(uri)
            if (mimeType != null) {
                initialIntentFormat = when {
                    mimeType.contains("pdf") -> DocumentFormat.PDF
                    mimeType.contains("word") || mimeType.contains("document") || mimeType.contains("text/plain") -> DocumentFormat.WORD
                    mimeType.contains("excel") || mimeType.contains("sheet") || mimeType.contains("csv") -> DocumentFormat.EXCEL
                    mimeType.contains("powerpoint") || mimeType.contains("presentation") -> DocumentFormat.POWERPOINT
                    mimeType.startsWith("image/") -> DocumentFormat.IMAGE
                    else -> DocumentFormat.WORD
                }
            } else {
                val ext = uri.path?.substringAfterLast('.', "") ?: ""
                initialIntentFormat = DocumentFormat.fromExtension(ext)
            }
        }

        setContent {
            val settings by app.container.settingsRepository.appSettingsFlow.collectAsStateWithLifecycle(
                initialValue = com.HrshD1eux.DocLite.models.AppSettings()
            )

            DocLiteTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocLiteNavigation(
                        initialIntentUri = initialIntentUri,
                        initialIntentFormat = initialIntentFormat
                    )
                }
            }
        }
    }
}

