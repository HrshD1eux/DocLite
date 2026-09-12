package com.HrshD1eux.DocLite.office.powerpoint

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.models.ElementType
import com.HrshD1eux.DocLite.models.PresentationDocument
import com.HrshD1eux.DocLite.models.Slide
import com.HrshD1eux.DocLite.models.SlideElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class PowerPointEngine(private val context: Context) {

    suspend fun loadPresentation(uri: Uri): PresentationDocument = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext == "ppt") {
            throw UnsupportedOperationException("Legacy PowerPoint 97-2003 (.ppt) format is not supported. Please convert to .pptx.")
        }

        val tempFile = File.createTempFile("pptx_cache_", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw java.io.FileNotFoundException("Could not open file: $fileName")

            val pkg = try {
                OPCPackage.open(tempFile, PackageAccess.READ)
            } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                throw IllegalArgumentException("Unsupported or corrupted PowerPoint format. The file is not a valid .pptx document.", e)
            } catch (t: Throwable) {
                throw IllegalArgumentException("Could not read PowerPoint package: ${t.message ?: "Invalid file"}", t)
            }

            val ppt = try {
                XMLSlideShow(pkg)
            } catch (e: OutOfMemoryError) {
                throw IllegalStateException("This presentation is too large to open in available device memory.", e)
            } catch (t: Throwable) {
                throw IllegalArgumentException("Failed to open presentation: ${t.localizedMessage ?: "Corrupted file"}", t)
            }

        val parsedSlides = mutableListOf<Slide>()
        var hasUnrecognizedElements = false

        for ((index, xslfSlide) in ppt.slides.withIndex()) {
            val elements = mutableListOf<SlideElement>()
            
            if (xslfSlide.shapes.any { it !is XSLFTextShape }) {
                hasUnrecognizedElements = true
            }

            for (shape in xslfSlide.shapes) {
                if (shape is XSLFTextShape) {
                    val text = shape.text
                    if (text.isNotBlank()) {
                        val type = if (shape.placeholder != null) {
                            if (shape.placeholder.name.contains("TITLE", ignoreCase = true)) ElementType.TITLE
                            else if (shape.placeholder.name.contains("SUBTITLE", ignoreCase = true)) ElementType.SUBTITLE
                            else ElementType.BODY_TEXT
                        } else {
                            ElementType.BODY_TEXT
                        }
                        
                        elements.add(
                            SlideElement(
                                type = type,
                                textContent = text,
                                fontSizeSp = shape.textParagraphs.firstOrNull()?.textRuns?.firstOrNull()?.fontSize?.toFloat() ?: 18f
                            )
                        )
                    }
                }
            }

            parsedSlides.add(
                Slide(
                    slideNumber = index + 1,
                    title = xslfSlide.title ?: "Slide ${index + 1}",
                    elements = elements.ifEmpty { listOf(SlideElement(type = ElementType.BODY_TEXT, textContent = "Empty Slide")) }
                )
            )
        }

            PresentationDocument(
                title = fileName,
                fileUri = uri.toString(),
                slides = parsedSlides.ifEmpty { listOf(Slide(slideNumber = 1)) },
                hasUnrecognizedElements = hasUnrecognizedElements
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun savePresentation(uri: Uri, document: PresentationDocument): Boolean = withContext(Dispatchers.IO) {
        if (document.hasUnrecognizedElements) {
            throw IllegalStateException("Cannot save: Presentation contains unsupported elements (images, shapes, or layouts) that would be destroyed.")
        }
        try {
            val slideshow = XMLSlideShow()
            try {
                document.slides.forEach { slideModel ->
                    val xslfSlide = slideshow.createSlide()

                    slideModel.elements.forEach { elem ->
                        if (elem.textContent.isNotBlank()) {
                            val shape = xslfSlide.createTextBox()
                            shape.text = elem.textContent
                        }
                    }
                }

                context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    slideshow.write(outputStream)
                } ?: return@withContext false
                true
            } finally {
                slideshow.close()
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) result = it.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "presentation.pptx"
    }
}
