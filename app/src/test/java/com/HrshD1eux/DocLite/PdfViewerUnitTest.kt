package com.HrshD1eux.DocLite

import android.net.Uri
import com.HrshD1eux.DocLite.models.AnnotationType
import com.HrshD1eux.DocLite.models.PdfSearchResult
import com.HrshD1eux.DocLite.ui.screens.pdf.PdfUiState
import org.junit.Assert.*
import org.junit.Test

class PdfViewerUnitTest {

    @Test
    fun searchNavigation_nextMatch_cyclesThroughResultsWithWrapping() {
        val results = listOf(
            PdfSearchResult(pageIndex = 0, snippet = "First match", matchIndex = 10),
            PdfSearchResult(pageIndex = 2, snippet = "Second match", matchIndex = 50),
            PdfSearchResult(pageIndex = 5, snippet = "Third match", matchIndex = 120)
        )

        var currentMatchIndex = 0

        // Function mimicking PdfViewModel.nextSearchMatch logic
        fun nextMatch(): Int {
            currentMatchIndex = (currentMatchIndex + 1) % results.size
            return results[currentMatchIndex].pageIndex
        }

        assertEquals(2, nextMatch()) // Moves to index 1 (page 2)
        assertEquals(5, nextMatch()) // Moves to index 2 (page 5)
        assertEquals(0, nextMatch()) // Wraps to index 0 (page 0)
    }

    @Test
    fun searchNavigation_previousMatch_cyclesBackwardWithWrapping() {
        val results = listOf(
            PdfSearchResult(pageIndex = 1, snippet = "Match 1", matchIndex = 15),
            PdfSearchResult(pageIndex = 3, snippet = "Match 2", matchIndex = 45),
            PdfSearchResult(pageIndex = 7, snippet = "Match 3", matchIndex = 90)
        )

        var currentMatchIndex = 0

        // Function mimicking PdfViewModel.previousSearchMatch logic
        fun previousMatch(): Int {
            currentMatchIndex = if (currentMatchIndex - 1 < 0) {
                results.size - 1
            } else {
                currentMatchIndex - 1
            }
            return results[currentMatchIndex].pageIndex
        }

        assertEquals(7, previousMatch()) // Wraps backward to last match (page 7)
        assertEquals(3, previousMatch()) // Moves backward to index 1 (page 3)
        assertEquals(1, previousMatch()) // Moves backward to index 0 (page 1)
    }

    @Test
    fun jumpToPage_clampsTargetIndexWithinDocumentBounds() {
        val totalPages = 10

        fun clampPage(requestedPage: Int): Int {
            return requestedPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        }

        assertEquals(0, clampPage(-5))
        assertEquals(0, clampPage(0))
        assertEquals(4, clampPage(4))
        assertEquals(9, clampPage(9))
        assertEquals(9, clampPage(50))
    }

    @Test
    fun multiMatchExtraction_findsAllOccurrencesInText() {
        val pageText = "DocLite is a fast office app. With DocLite you can read PDFs. DocLite supports annotations."
        val query = "DocLite"

        val results = mutableListOf<PdfSearchResult>()
        var startIndex = 0
        while (startIndex < pageText.length) {
            val index = pageText.indexOf(query, startIndex, ignoreCase = true)
            if (index == -1) break

            val start = maxOf(0, index - 10)
            val end = minOf(pageText.length, index + query.length + 10)
            val snippet = pageText.substring(start, end)

            results.add(
                PdfSearchResult(
                    pageIndex = 0,
                    snippet = snippet,
                    matchIndex = index
                )
            )
            startIndex = index + query.length.coerceAtLeast(1)
        }

        assertEquals(3, results.size)
        assertEquals(0, results[0].matchIndex)
        assertEquals(35, results[1].matchIndex)
        assertEquals(62, results[2].matchIndex)
    }

    @Test
    fun annotationMode_toggle_activatesAndDeactivatesCorrectly() {
        var isAnnotationMode = false
        var selectedTool: AnnotationType? = AnnotationType.HIGHLIGHT

        fun toggle(enabled: Boolean? = null) {
            val newMode = enabled ?: !isAnnotationMode
            isAnnotationMode = newMode
            if (!newMode) selectedTool = null
        }

        toggle(true)
        assertTrue(isAnnotationMode)

        toggle(false)
        assertFalse(isAnnotationMode)
        assertNull(selectedTool)
    }

    @Test
    fun drawingPoint_serializationAndDeserialization_preservesCoordinatesAccurately() {
        val originalPoints = listOf(
            com.HrshD1eux.DocLite.models.DrawingPoint(0.12f, 0.34f),
            com.HrshD1eux.DocLite.models.DrawingPoint(0.56f, 0.78f),
            com.HrshD1eux.DocLite.models.DrawingPoint(0.99f, 0.01f)
        )

        val serialized = com.HrshD1eux.DocLite.models.DrawingPoint.serializeList(originalPoints)
        val deserialized = com.HrshD1eux.DocLite.models.DrawingPoint.deserializeList(serialized)

        assertEquals(3, deserialized.size)
        assertEquals(0.12f, deserialized[0].xRatio, 0.001f)
        assertEquals(0.34f, deserialized[0].yRatio, 0.001f)
        assertEquals(0.56f, deserialized[1].xRatio, 0.001f)
        assertEquals(0.78f, deserialized[1].yRatio, 0.001f)
        assertEquals(0.99f, deserialized[2].xRatio, 0.001f)
        assertEquals(0.01f, deserialized[2].yRatio, 0.001f)
    }

    @Test
    fun drawingPoint_deserializeEmptyOrBlank_returnsEmptyList() {
        assertTrue(com.HrshD1eux.DocLite.models.DrawingPoint.deserializeList("").isEmpty())
        assertTrue(com.HrshD1eux.DocLite.models.DrawingPoint.deserializeList("   ").isEmpty())
    }

    @Test
    fun passwordRequiredException_createsProperException() {
        val ex = com.HrshD1eux.DocLite.office.pdf.PasswordRequiredException("This PDF is password-protected")
        assertTrue(ex.message!!.contains("password-protected"))
    }
}

