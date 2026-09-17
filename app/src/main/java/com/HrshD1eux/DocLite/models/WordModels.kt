package com.HrshD1eux.DocLite.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

enum class TextAlignment {
    LEFT, CENTER, RIGHT, JUSTIFY;

    fun toComposeTextAlign(): TextAlign = when (this) {
        LEFT -> TextAlign.Left
        CENTER -> TextAlign.Center
        RIGHT -> TextAlign.Right
        JUSTIFY -> TextAlign.Justify
    }
}

data class TextStyle(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontSizeSp: Float = 16f,
    val fontColorHex: String = "#1C1B1F"
) {
    fun getComposeColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(fontColorHex))
        } catch (e: Exception) {
            Color.Black
        }
    }
}

data class TextRun(
    val text: String,
    val style: TextStyle = TextStyle()
)

data class WordTableCell(
    val text: String,
    val isHeader: Boolean = false
)

data class WordTableRow(
    val cells: List<WordTableCell> = emptyList()
)

data class WordTable(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rows: List<WordTableRow> = emptyList()
)

sealed interface WordBodyElement {
    data class ParagraphElement(val paragraph: Paragraph) : WordBodyElement
    data class TableElement(val table: WordTable) : WordBodyElement
}

data class Paragraph(
    val id: String = java.util.UUID.randomUUID().toString(),
    val runs: List<TextRun> = listOf(TextRun("")),
    val alignment: TextAlignment = TextAlignment.LEFT,
    val isHeader: Boolean = false,
    val headerLevel: Int = 0,
    val bulletPrefix: String? = null
) {
    fun getPlainText(): String {
        val content = runs.joinToString("") { it.text }
        return if (bulletPrefix != null) "$bulletPrefix$content" else content
    }
}

data class WordDocument(
    val title: String,
    val fileUri: String,
    val bodyElements: List<WordBodyElement> = listOf(
        WordBodyElement.ParagraphElement(Paragraph())
    ),
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val hasUnrecognizedElements: Boolean = false
) {
    /** Computed view — always in sync with bodyElements. */
    val paragraphs: List<Paragraph>
        get() = bodyElements.filterIsInstance<WordBodyElement.ParagraphElement>()
            .map { it.paragraph }

    /** Computed view — all tables from bodyElements. */
    val tables: List<WordTable>
        get() = bodyElements.filterIsInstance<WordBodyElement.TableElement>()
            .map { it.table }
}

