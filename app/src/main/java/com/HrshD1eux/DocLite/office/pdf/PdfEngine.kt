package com.HrshD1eux.DocLite.office.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.HrshD1eux.DocLite.models.AnnotationType
import com.HrshD1eux.DocLite.models.PdfAnnotation
import com.HrshD1eux.DocLite.models.PdfSearchResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class PasswordRequiredException(message: String) : Exception(message)

class PdfEngine(private val context: Context) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentUri: Uri? = null
    private var currentPassword: String? = null
    private var tempDecryptedFile: File? = null
    private var tempSeekableFile: File? = null

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private val renderLock = Any()
    private val aspectRatioMap = mutableMapOf<Int, Float>()
    private val pageTextCache = mutableMapOf<Int, String>()

    suspend fun openPdf(uri: Uri, password: String? = null): Int = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            closeLocked()
            currentUri = uri
            currentPassword = password

            // If password is provided, decrypt first via PDFBox and render the decrypted file natively
            if (!password.isNullOrEmpty()) {
                val inputStream = openInputStreamSafe(uri)
                    ?: throw java.io.FileNotFoundException("Could not open PDF file")

                val doc = try {
                    PDDocument.load(inputStream, password)
                } catch (e: InvalidPasswordException) {
                    throw PasswordRequiredException("Invalid password. Please try again.")
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        throw PasswordRequiredException("Invalid password. Please try again.")
                    }
                    throw e
                }

                doc.use { pdDoc ->
                    if (pdDoc.isEncrypted) {
                        pdDoc.isAllSecurityToBeRemoved = true
                    }
                    val temp = File.createTempFile("decrypted_pdf_", ".pdf", context.cacheDir)
                    tempDecryptedFile = temp
                    pdDoc.save(temp)

                    val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                    parcelFileDescriptor = pfd
                    pdfRenderer = PdfRenderer(pfd)
                    val count = pdfRenderer?.pageCount ?: 0
                    if (count > 0) {
                        return@withContext count
                    }
                    return@withContext pdDoc.numberOfPages
                }
            }

            // Normal flow (no password provided yet)
            var pfd: ParcelFileDescriptor? = null
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) {
                    try {
                        pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (t: Throwable) {
                        pfd = null
                    }
                }
            }

            if (pfd == null) {
                // Always cache content:// URIs to a local seekable file
                // This ensures seekability, thread-safety, and avoids contentResolver permission expiry during paging
                val temp = File.createTempFile("pdf_cache_", ".pdf", context.cacheDir)
                tempSeekableFile = temp
                val inputStream = openInputStreamSafe(uri)
                    ?: throw java.io.FileNotFoundException("Could not open PDF file: $uri")
                inputStream.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            parcelFileDescriptor = pfd

            var count = 0
            try {
                pdfRenderer = PdfRenderer(pfd)
                count = pdfRenderer?.pageCount ?: 0
            } catch (e: SecurityException) {
                closeLocked()
                throw PasswordRequiredException("This PDF document is password-protected. Please enter password.")
            } catch (t: Throwable) {
                // Native PdfRenderer may fail under Robolectric or on non-standard PDF formats
            }

            if (count <= 0) {
                // Fallback to PDFBox for page count (e.g. under Robolectric or when PdfRenderer fails)
                val isEncrypted = try {
                    openInputStreamSafe(uri)?.use { stream ->
                        PDDocument.load(stream).use { doc ->
                            count = doc.numberOfPages
                            doc.isEncrypted
                        }
                    } ?: false
                } catch (pe: InvalidPasswordException) {
                    true
                } catch (ignored: Throwable) {
                    false
                }

                if (isEncrypted) {
                    closeLocked()
                    throw PasswordRequiredException("This PDF document is password-protected. Please enter password.")
                }
            }

            if (count > 0) {
                return@withContext count
            }
            closeLocked()
            throw IllegalArgumentException("PDF document contains no pages or is corrupt.")
        }
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            aspectRatioMap[pageIndex]?.let { return@synchronized it }
            val defaultRatio = 595f / 842f // Default A4 portrait width / height
            val renderer = pdfRenderer

            if (renderer != null && pageIndex in 0 until renderer.pageCount) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val ratio = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
                    aspectRatioMap[pageIndex] = ratio
                    return@synchronized ratio
                } catch (t: Throwable) {
                    // Fall through to PDFBox
                } finally {
                    try {
                        page?.close()
                    } catch (ignored: Throwable) {}
                }
            }

            // Fallback via PDFBox
            val uri = currentUri
            if (uri != null) {
                try {
                    openInputStreamSafe(uri)?.use { stream ->
                        val doc = if (!currentPassword.isNullOrEmpty()) {
                            PDDocument.load(stream, currentPassword)
                        } else {
                            PDDocument.load(stream)
                        }
                        doc.use { pdDoc ->
                            if (pageIndex in 0 until pdDoc.numberOfPages) {
                                val pdPage = pdDoc.getPage(pageIndex)
                                val box = pdPage.cropBox ?: pdPage.mediaBox
                                if (box != null && box.height > 0) {
                                    val ratio = box.width / box.height
                                    aspectRatioMap[pageIndex] = ratio
                                    return@synchronized ratio
                                }
                            }
                        }
                    }
                } catch (ignored: Throwable) {}
            }
            defaultRatio
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(renderLock) {
            val renderer = pdfRenderer
            if (renderer != null && pageIndex in 0 until renderer.pageCount) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val widthToHeight = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
                    aspectRatioMap[pageIndex] = widthToHeight
                    val targetHeightPx = (targetWidthPx / widthToHeight).toInt().coerceAtLeast(100)

                    // Android's native PdfRenderer strictly requires ARGB_8888 at the C++ layer.
                    // Using RGB_565 causes IllegalArgumentException("Unsupported pixel format") inside native render.
                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return@synchronized bitmap
                } catch (t: Throwable) {
                    t.printStackTrace()
                } finally {
                    try {
                        page?.close()
                    } catch (ignored: Throwable) {}
                }
            }

            // Fallback rendering via PDFBox if native PdfRenderer is null or failed on this page
            val uri = currentUri
            if (uri != null) {
                try {
                    openInputStreamSafe(uri)?.use { stream ->
                        val doc = if (!currentPassword.isNullOrEmpty()) {
                            PDDocument.load(stream, currentPassword)
                        } else {
                            PDDocument.load(stream)
                        }
                        doc.use { pdDoc ->
                            if (pageIndex in 0 until pdDoc.numberOfPages) {
                                val pdfboxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(pdDoc)
                                val bmp = pdfboxRenderer.renderImage(pageIndex, 1.0f)
                                return@synchronized bmp
                            }
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            null
        }
    }

    suspend fun searchInPdf(query: String): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PdfSearchResult>()
        if (query.isBlank() || currentUri == null) return@withContext results

        try {
            val uri = currentUri ?: return@withContext results
            val doc = if (!currentPassword.isNullOrEmpty()) {
                openInputStreamSafe(uri)?.use { stream ->
                    PDDocument.load(stream, currentPassword)
                }
            } else {
                openInputStreamSafe(uri)?.use { stream ->
                    PDDocument.load(stream)
                }
            } ?: return@withContext results

            doc.use { document ->
                val stripper = PDFTextStripper()

                for (p in 1..document.numberOfPages) {
                    currentCoroutineContext().ensureActive()

                    // Check cached text first to avoid redundant stripping
                    val pageIndex0 = p - 1
                    val pageText = pageTextCache.getOrPut(pageIndex0) {
                        stripper.startPage = p
                        stripper.endPage = p
                        stripper.getText(document) ?: ""
                    }

                    var startIndex = 0
                    while (startIndex < pageText.length) {
                        val index = pageText.indexOf(query, startIndex, ignoreCase = true)
                        if (index == -1) break

                        val start = maxOf(0, index - 30)
                        val end = minOf(pageText.length, index + query.length + 30)
                        val snippet = "... " + pageText.substring(start, end).replace("\n", " ").trim() + " ..."

                        results.add(
                            PdfSearchResult(
                                pageIndex = pageIndex0,
                                snippet = snippet,
                                matchIndex = index
                            )
                        )
                        startIndex = index + query.length.coerceAtLeast(1)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun exportAnnotatedPdf(originalUri: Uri, annotations: List<PdfAnnotation>): Uri = withContext(Dispatchers.IO) {
        if (annotations.isEmpty()) return@withContext originalUri

        val inputStream = openInputStreamSafe(originalUri)
            ?: return@withContext originalUri

        val doc = if (!currentPassword.isNullOrEmpty()) {
            PDDocument.load(inputStream, currentPassword)
        } else {
            PDDocument.load(inputStream)
        }

        doc.use { pdDoc ->
            if (pdDoc.isEncrypted) {
                pdDoc.isAllSecurityToBeRemoved = true
            }

            val annotationsByPage = annotations.groupBy { it.pageIndex }

            for ((pageIdx, pageAnnotations) in annotationsByPage) {
                if (pageIdx in 0 until pdDoc.numberOfPages) {
                    val page = pdDoc.getPage(pageIdx)
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height

                    val contentStream = PDPageContentStream(
                        pdDoc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )

                    contentStream.use { cs ->
                        for (ann in pageAnnotations) {
                            when (ann.type) {
                                AnnotationType.HIGHLIGHT -> {
                                    val x = ann.boundsLeftRatio * pageWidth
                                    val y = (1f - ann.boundsTopRatio - ann.boundsHeightRatio) * pageHeight
                                    val w = ann.boundsWidthRatio * pageWidth
                                    val h = ann.boundsHeightRatio * pageHeight

                                    cs.setNonStrokingColor(255, 235, 59) // Yellow
                                    cs.addRect(x, y, w, h)
                                    cs.fill()
                                }
                                AnnotationType.FREE_DRAW -> {
                                    if (ann.points.size >= 2) {
                                        val parsedColor = try {
                                            Color.parseColor(ann.colorHex)
                                        } catch (e: Exception) {
                                            Color.RED
                                        }
                                        cs.setStrokingColor(
                                            Color.red(parsedColor),
                                            Color.green(parsedColor),
                                            Color.blue(parsedColor)
                                        )
                                        cs.setLineWidth(ann.strokeWidthDp.coerceAtLeast(1f))
                                        val first = ann.points.first()
                                        cs.moveTo(first.xRatio * pageWidth, (1f - first.yRatio) * pageHeight)
                                        for (pt in ann.points.drop(1)) {
                                            cs.lineTo(pt.xRatio * pageWidth, (1f - pt.yRatio) * pageHeight)
                                        }
                                        cs.stroke()
                                    }
                                }
                                AnnotationType.STICKY_NOTE -> {
                                    val x = ann.boundsLeftRatio * pageWidth
                                    val y = (1f - ann.boundsTopRatio - 0.05f) * pageHeight
                                    cs.setNonStrokingColor(33, 150, 243) // Blue
                                    cs.addRect(x, y, 35f, 15f)
                                    cs.fill()
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            val exportFile = File(context.cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
            pdDoc.save(exportFile)
            Uri.fromFile(exportFile)
        }
    }

    fun close() {
        synchronized(renderLock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        try {
            aspectRatioMap.clear()
            pageTextCache.clear()
            pdfRenderer?.close()
            pdfRenderer = null
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
            tempDecryptedFile?.delete()
            tempDecryptedFile = null
            tempSeekableFile?.delete()
            tempSeekableFile = null
            currentPassword = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openInputStreamSafe(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) java.io.FileInputStream(f) else null
        } else {
            try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                null
            }
        }
    }
}
