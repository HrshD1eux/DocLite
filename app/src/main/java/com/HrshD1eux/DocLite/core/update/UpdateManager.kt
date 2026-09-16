package com.HrshD1eux.DocLite.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.HrshD1eux.DocLite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val isUpdateAvailable: Boolean
)

class UpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/HrshD1eux/DocLite/releases/latest"

        fun isNewerVersion(current: String, latest: String): Boolean {
            val cleanCurrent = current.trimStart('v', 'V').substringBefore('-').split('.')
            val cleanLatest = latest.trimStart('v', 'V').substringBefore('-').split('.')

            val maxLen = maxOf(cleanCurrent.size, cleanLatest.size)
            for (i in 0 until maxLen) {
                val currentPart = cleanCurrent.getOrNull(i)?.toIntOrNull() ?: 0
                val latestPart = cleanLatest.getOrNull(i)?.toIntOrNull() ?: 0
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        }
    }

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASES_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DocLite-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("Failed to check for updates (HTTP $responseCode)")
                )
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val body = json.optString("body", "No release notes provided.")
            val currentVersion = BuildConfig.VERSION_NAME

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isAvailable = isNewerVersion(currentVersion, tagName)

            Result.success(
                AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    releaseTitle = name,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    apkSize = apkSize,
                    isUpdateAvailable = isAvailable
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (downloadUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Download URL is empty"))
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0
            val maxRedirects = 5

            // Follow HTTP redirects (GitHub releases redirect to AWS S3)
            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "DocLite-Android-App")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    currentUrl = newUrl
                    redirects++
                    if (redirects > maxRedirects) {
                        return@withContext Result.failure(Exception("Too many redirects"))
                    }
                } else if (status == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    return@withContext Result.failure(Exception("Download failed with HTTP $status"))
                }
            }

            val totalBytes = connection.contentLength.toLong()
            val outputFile = File(context.cacheDir, "DocLite-update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        onProgress(progress, totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(outputFile)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }
}
