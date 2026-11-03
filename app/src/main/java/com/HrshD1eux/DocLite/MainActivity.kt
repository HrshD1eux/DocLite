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
import com.HrshD1eux.DocLite.core.utils.UriResolver
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.ThemeMode
import com.HrshD1eux.DocLite.ui.navigation.DocLiteNavigation
import com.HrshD1eux.DocLite.ui.theme.DocLiteTheme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class MainActivity : ComponentActivity() {

    private val _intentDataFlow = MutableStateFlow<Pair<Uri?, DocumentFormat?>>(Pair(null, null))
    private var isExternalIntent = false

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
                        isExternalIntent = isExternalIntent,
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
        val hasData = incomingIntent?.data != null
        val isSupportedAction = incomingIntent?.action == Intent.ACTION_VIEW || incomingIntent?.action == Intent.ACTION_EDIT
        if (isSupportedAction && hasData) {
            isExternalIntent = true
            val rawUri = incomingIntent!!.data!!
            try {
                if (rawUri.scheme == "content") {
                    contentResolver.takePersistableUriPermission(
                        rawUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Could not take persistable permission for $rawUri", e)
            }

            // Canonicalize to direct physical URI if accessible on storage, preventing FileProvider permission expirations
            val effectiveUri = UriResolver.getCanonicalPermanentUri(this, rawUri)
            val displayName = UriResolver.getDisplayName(this, effectiveUri)
            val extFromName = displayName.substringAfterLast('.', "").lowercase()
            val mimeType = try { contentResolver.getType(rawUri) } catch (t: Throwable) { null }
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

            _intentDataFlow.value = Pair(effectiveUri, format)
        }
    }
}

