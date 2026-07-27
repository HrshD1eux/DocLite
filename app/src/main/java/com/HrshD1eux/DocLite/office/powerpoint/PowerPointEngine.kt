package com.HrshD1eux.DocLite.office.powerpoint

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.models.ElementType
import com.HrshD1eux.DocLite.models.PresentationDocument
import com.HrshD1eux.DocLite.models.Slide
import com.HrshD1eux.DocLite.models.SlideElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.InputStream
import java.io.OutputStream

class PowerPointEngine(private val context: Context) {

    suspend fun loadPresentation(uri: Uri): PresentationDocument = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val ppt = XMLSlideShow(inputStream)
                val parsedSlides = mutableListOf<Slide>()

                for ((index, xslfSlide) in ppt.slides.withIndex()) {
                    val elements = mutableListOf<SlideElement>()
                    
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
                    slides = parsedSlides.ifEmpty { createDefaultSlides(fileName) }
                )
            } ?: createDefaultPresentation(uri, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            createDefaultPresentation(uri, fileName)
        }
    }

    private fun createDefaultPresentation(uri: Uri, fileName: String): PresentationDocument {
        return PresentationDocument(
            title = fileName,
            fileUri = uri.toString(),
            slides = createDefaultSlides(fileName)
        )
    }

    private fun createDefaultSlides(fileName: String): List<Slide> {
        return listOf(
            Slide(
                slideNumber = 1,
                title = "Welcome to DocLite Presentation",
                elements = listOf(
                    SlideElement(type = ElementType.TITLE, textContent = fileName.substringBeforeLast("."), fontSizeSp = 30f),
                    SlideElement(type = ElementType.SUBTITLE, textContent = "Lightweight Offline PowerPoint Viewer & Editor", fontSizeSp = 20f)
                )
            )
        )
    }

    suspend fun savePresentation(uri: Uri, document: PresentationDocument): Boolean = withContext(Dispatchers.IO) {
        try {
            var ppt: XMLSlideShow? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                try {
                    ppt = XMLSlideShow(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (ppt == null) {
                ppt = XMLSlideShow()
            }

            ppt?.let { slideshow ->
                // Clear existing slides for overwrite
                while (slideshow.slides.size > 0) {
                    slideshow.removeSlide(0)
                }

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
                }
                slideshow.close()
                true
            } ?: false
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
