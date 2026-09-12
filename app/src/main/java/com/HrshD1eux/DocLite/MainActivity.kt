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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class MainActivity : ComponentActivity() {

    private val _intentDataFlow = MutableStateFlow<Pair<Uri?, DocumentFormat?>>(Pair(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DocLiteApplication
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            val settings by app.container.settingsRepository.appSettingsFlow.collectAsStateWithLifecycle(
                initialValue = com.HrshD1eux.DocLite.models.AppSettings()
            )
            val intentData by _intentDataFlow.collectAsStateWithLifecycle()

            DocLiteTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocLiteNavigation(
                        initialIntentUri = intentData.first,
                        initialIntentFormat = intentData.second,
                        startScreen = settings.startScreen,
                        onIntentConsumed = {
                            _intentDataFlow.value = Pair(null, null)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action == Intent.ACTION_VIEW && incomingIntent.data != null) {
            val uri = incomingIntent.data!!
            try {
                if (uri.scheme == "content") {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Could not take persistable permission for $uri", e)
            }
            
            var displayName: String? = null
            if (uri.scheme == "content") {
                try {
                    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            displayName = cursor.getString(0)
                        }
                    }
                } catch (ignored: Throwable) {}
            }
            val extFromName = (displayName ?: uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
            val mimeType = contentResolver.getType(uri)
            val format = if (extFromName.isNotEmpty()) {
                DocumentFormat.fromExtension(extFromName)
            } else if (mimeType != null) {
                when {
                    mimeType.contains("pdf") -> DocumentFormat.PDF
                    mimeType.contains("text/plain") || mimeType.contains("text/markdown") -> DocumentFormat.TXT
                    mimeType.contains("word") || mimeType.contains("msword") || mimeType.contains("document") -> DocumentFormat.WORD
                    mimeType.contains("excel") || mimeType.contains("sheet") || mimeType.contains("csv") -> DocumentFormat.EXCEL
                    mimeType.contains("powerpoint") || mimeType.contains("presentation") -> DocumentFormat.POWERPOINT
                    mimeType.startsWith("image/") -> DocumentFormat.IMAGE
                    else -> DocumentFormat.WORD
                }
            } else {
                DocumentFormat.WORD
            }
            
            _intentDataFlow.value = Pair(uri, format)
        }
    }
}

