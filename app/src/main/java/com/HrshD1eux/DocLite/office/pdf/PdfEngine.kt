package com.HrshD1eux.DocLite.office.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.HrshD1eux.DocLite.models.PdfSearchResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfEngine(private val context: Context) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentUri: Uri? = null

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private val renderLock = Any()
    private val aspectRatioMap = mutableMapOf<Int, Float>()

    suspend fun openPdf(uri: Uri): Int = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            closeLocked()
            currentUri = uri
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    parcelFileDescriptor = pfd
                    pdfRenderer = PdfRenderer(pfd)
                    pdfRenderer?.pageCount ?: 0
                } else 0
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            aspectRatioMap[pageIndex]?.let { return@synchronized it }
            val defaultRatio = 595f / 842f // Default A4 portrait width / height
            val renderer = pdfRenderer ?: return@synchronized defaultRatio
            if (pageIndex !in 0 until renderer.pageCount) return@synchronized defaultRatio
            try {
                val page = renderer.openPage(pageIndex)
                // In Compose Modifier.aspectRatio(ratio), ratio is width / height
                val ratio = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
                page.close()
                aspectRatioMap[pageIndex] = ratio
                ratio
            } catch (e: Exception) {
                defaultRatio
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return@synchronized null
            if (pageIndex !in 0 until renderer.pageCount) return@synchronized null

            try {
                val page = renderer.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                aspectRatioMap[pageIndex] = aspectRatio
                val targetHeightPx = (targetWidthPx * aspectRatio).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun searchInPdf(query: String): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PdfSearchResult>()
        if (query.isBlank() || currentUri == null) return@withContext results

        try {
            context.contentResolver.openInputStream(currentUri!!)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()

                for (p in 1..document.numberOfPages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(document) ?: continue

                    var startIndex = 0
                    while (startIndex < pageText.length) {
                        val index = pageText.indexOf(query, startIndex, ignoreCase = true)
                        if (index == -1) break

                        val start = maxOf(0, index - 30)
                        val end = minOf(pageText.length, index + query.length + 30)
                        val snippet = "... " + pageText.substring(start, end).replace("\n", " ").trim() + " ..."

                        results.add(
                            PdfSearchResult(
                                pageIndex = p - 1, // 0-indexed for the UI
                                snippet = snippet,
                                matchIndex = index
                            )
                        )
                        startIndex = index + query.length.coerceAtLeast(1)
                    }
                }
                document.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    fun close() {
        synchronized(renderLock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        try {
            aspectRatioMap.clear()
            pdfRenderer?.close()
            pdfRenderer = null
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
