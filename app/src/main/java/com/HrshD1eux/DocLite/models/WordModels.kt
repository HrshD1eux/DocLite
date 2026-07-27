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

data class Paragraph(
    val id: String = java.util.UUID.randomUUID().toString(),
    val runs: List<TextRun> = listOf(TextRun("")),
    val alignment: TextAlignment = TextAlignment.LEFT,
    val isHeader: Boolean = false,
    val headerLevel: Int = 0
) {
    fun getPlainText(): String = runs.joinToString("") { it.text }
}

data class WordDocument(
    val title: String,
    val fileUri: String,
    val paragraphs: List<Paragraph> = listOf(Paragraph()),
    val wordCount: Int = 0,
    val characterCount: Int = 0
)

