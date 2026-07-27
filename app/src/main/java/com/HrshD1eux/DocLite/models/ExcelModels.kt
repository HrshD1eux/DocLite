package com.HrshD1eux.DocLite.models

import androidx.compose.ui.graphics.Color

enum class NumberFormat {
    GENERAL, TEXT, NUMBER, CURRENCY, PERCENTAGE
}

data class CellFormat(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textColorHex: String = "#1C1B1F",
    val backgroundColorHex: String = "#FFFFFF",
    val numberFormat: NumberFormat = NumberFormat.GENERAL
) {
    fun getTextColor(): Color = try {
        Color(android.graphics.Color.parseColor(textColorHex))
    } catch (e: Exception) { Color.Black }

    fun getBgColor(): Color = try {
        Color(android.graphics.Color.parseColor(backgroundColorHex))
    } catch (e: Exception) { Color.White }
}

data class Cell(
    val row: Int,
    val col: Int,
    val value: String = "",
    val formula: String = "",
    val evaluatedValue: String = "",
    val format: CellFormat = CellFormat()
) {
    val displayValue: String
        get() = if (evaluatedValue.isNotEmpty()) evaluatedValue else value
}

data class Sheet(
    val name: String,
    val rowCount: Int = 50,
    val colCount: Int = 15,
    val cells: Map<String, Cell> = emptyMap() // Key: "R0C0", "A1" etc.
) {
    fun getCell(row: Int, col: Int): Cell {
        val key = getCellKey(row, col)
        return cells[key] ?: Cell(row = row, col = col)
    }

    companion object {
        fun getCellKey(row: Int, col: Int): String = "R${row}C${col}"

        fun colIndexToName(col: Int): String {
            var temp = col
            val name = StringBuilder()
            while (temp >= 0) {
                name.insert(0, (('A'.code + (temp % 26)).toChar()))
                temp = (temp / 26) - 1
            }
            return name.toString()
        }

        fun cellNameToCoords(cellName: String): Pair<Int, Int>? {
            val uppercase = cellName.uppercase().trim()
            val colStr = uppercase.takeWhile { it.isLetter() }
            val rowStr = uppercase.dropWhile { it.isLetter() }

            if (colStr.isEmpty() || rowStr.isEmpty()) return null

            var col = 0
            for (char in colStr) {
                col = col * 26 + (char - 'A' + 1)
            }
            col -= 1

            val row = (rowStr.toIntOrNull() ?: 1) - 1
            return Pair(row, col)
        }
    }
}

data class SpreadsheetDocument(
    val title: String,
    val fileUri: String,
    val sheets: List<Sheet> = listOf(Sheet(name = "Sheet1"))
)

