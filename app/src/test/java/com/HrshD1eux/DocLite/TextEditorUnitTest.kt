package com.HrshD1eux.DocLite

import com.HrshD1eux.DocLite.models.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorUnitTest {

    @Test
    fun documentFormat_fromExtension_resolvesTxtLogMdToTXT() {
        assertEquals(DocumentFormat.TXT, DocumentFormat.fromExtension("txt"))
        assertEquals(DocumentFormat.TXT, DocumentFormat.fromExtension("TXT"))
        assertEquals(DocumentFormat.TXT, DocumentFormat.fromExtension("md"))
        assertEquals(DocumentFormat.TXT, DocumentFormat.fromExtension("log"))
    }

    @Test
    fun documentFormat_wordFormatDoesNotMatchTxt() {
        assertEquals(DocumentFormat.WORD, DocumentFormat.fromExtension("docx"))
        assertFalse(DocumentFormat.WORD.extensions.contains("txt"))
        assertFalse(DocumentFormat.WORD.extensions.contains("log"))
    }

    @Test
    fun textMetrics_wordAndCharacterCount_calculatedAccurately() {
        val sampleText = "DocLite is an offline office suite for Android."
        val words = sampleText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val chars = sampleText.length

        assertEquals(8, words)
        assertEquals(47, chars)

        val emptyText = "   \n\t  "
        val emptyWords = emptyText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val emptyChars = emptyText.length

        assertEquals(0, emptyWords)
        assertEquals(7, emptyChars)
    }

    @Test
    fun undoRedoHistory_managesStateSnapshotsCorrectly() {
        val undoStack = mutableListOf<String>()
        val redoStack = mutableListOf<String>()

        var currentText = "Initial"

        fun onTextChange(newText: String) {
            undoStack.add(currentText)
            redoStack.clear()
            currentText = newText
        }

        fun undo() {
            if (undoStack.isNotEmpty()) {
                val prev = undoStack.removeAt(undoStack.lastIndex)
                redoStack.add(currentText)
                currentText = prev
            }
        }

        fun redo() {
            if (redoStack.isNotEmpty()) {
                val next = redoStack.removeAt(redoStack.lastIndex)
                undoStack.add(currentText)
                currentText = next
            }
        }

        onTextChange("Initial edit")
        onTextChange("Initial edit 2")
        assertEquals("Initial edit 2", currentText)
        assertEquals(2, undoStack.size)
        assertTrue(redoStack.isEmpty())

        undo()
        assertEquals("Initial edit", currentText)
        assertEquals(1, undoStack.size)
        assertEquals(1, redoStack.size)

        undo()
        assertEquals("Initial", currentText)
        assertEquals(0, undoStack.size)
        assertEquals(2, redoStack.size)

        redo()
        assertEquals("Initial edit", currentText)
        assertEquals(1, undoStack.size)
        assertEquals(1, redoStack.size)

        onTextChange("Branch edit")
        assertEquals("Branch edit", currentText)
        assertTrue(redoStack.isEmpty())
        assertEquals(2, undoStack.size)
    }
}
