package com.HrshD1eux.DocLite.office.excel

import com.HrshD1eux.DocLite.models.Sheet

class FormulaEngine {

    fun evaluateFormula(formula: String, sheet: Sheet): String {
        val cleanFormula = formula.trim()
        if (!cleanFormula.startsWith("=")) return formula

        val expression = cleanFormula.substring(1).trim().uppercase()

        return try {
            when {
                expression.startsWith("SUM(") -> evaluateSum(expression, sheet)
                expression.startsWith("AVERAGE(") -> evaluateAverage(expression, sheet)
                expression.startsWith("MIN(") -> evaluateMin(expression, sheet)
                expression.startsWith("MAX(") -> evaluateMax(expression, sheet)
                expression.startsWith("COUNT(") -> evaluateCount(expression, sheet)
                else -> evaluateSimpleMath(expression, sheet)
            }
        } catch (e: Exception) {
            "#ERROR!"
        }
    }

    private fun extractValuesFromRange(arg: String, sheet: Sheet): List<Double> {
        val cleanArg = arg.trim('(', ')', ' ')
        val values = mutableListOf<Double>()

        if (cleanArg.contains(":")) {
            val parts = cleanArg.split(":")
            if (parts.size == 2) {
                val startCoords = Sheet.cellNameToCoords(parts[0])
                val endCoords = Sheet.cellNameToCoords(parts[1])

                if (startCoords != null && endCoords != null) {
                    val minRow = minOf(startCoords.first, endCoords.first)
                    val maxRow = maxOf(startCoords.first, endCoords.first)
                    val minCol = minOf(startCoords.second, endCoords.second)
                    val maxCol = maxOf(startCoords.second, endCoords.second)

                    for (r in minRow..maxRow) {
                        for (c in minCol..maxCol) {
                            val cell = sheet.getCell(r, c)
                            val valStr = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                            val num = valStr.toDoubleOrNull()
                            if (num != null) values.add(num)
                        }
                    }
                }
            }
        } else {
            // Comma separated list of cells
            val cellNames = cleanArg.split(",")
            for (cellName in cellNames) {
                val coords = Sheet.cellNameToCoords(cellName.trim())
                if (coords != null) {
                    val cell = sheet.getCell(coords.first, coords.second)
                    val valStr = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                    val num = valStr.toDoubleOrNull()
                    if (num != null) values.add(num)
                }
            }
        }

        return values
    }

    private fun evaluateSum(expression: String, sheet: Sheet): String {
        val arg = expression.substring(4)
        val values = extractValuesFromRange(arg, sheet)
        val sum = values.sum()
        return formatResult(sum)
    }

    private fun evaluateAverage(expression: String, sheet: Sheet): String {
        val arg = expression.substring(8)
        val values = extractValuesFromRange(arg, sheet)
        if (values.isEmpty()) return "0"
        val avg = values.average()
        return formatResult(avg)
    }

    private fun evaluateMin(expression: String, sheet: Sheet): String {
        val arg = expression.substring(4)
        val values = extractValuesFromRange(arg, sheet)
        if (values.isEmpty()) return "0"
        return formatResult(values.minOrNull() ?: 0.0)
    }

    private fun evaluateMax(expression: String, sheet: Sheet): String {
        val arg = expression.substring(4)
        val values = extractValuesFromRange(arg, sheet)
        if (values.isEmpty()) return "0"
        return formatResult(values.maxOrNull() ?: 0.0)
    }

    private fun evaluateCount(expression: String, sheet: Sheet): String {
        val arg = expression.substring(6)
        val values = extractValuesFromRange(arg, sheet)
        return values.size.toString()
    }

    private fun evaluateSimpleMath(expression: String, sheet: Sheet): String {
        // Resolve cell names like A1 + B1 using token matching to avoid substring collision (e.g. A1 inside A10)
        val cellRegex = Regex("[A-Z]+[0-9]+")
        val resolvedExpr = cellRegex.replace(expression) { match ->
            val cellName = match.value
            val coords = Sheet.cellNameToCoords(cellName)
            if (coords != null) {
                val cell = sheet.getCell(coords.first, coords.second)
                val valStr = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                val num = valStr.toDoubleOrNull() ?: 0.0
                num.toString()
            } else {
                match.value
            }
        }

        return try {
            when {
                resolvedExpr.contains("+") -> {
                    val parts = resolvedExpr.split("+").mapNotNull { it.trim().toDoubleOrNull() }
                    formatResult(parts.sum())
                }
                resolvedExpr.contains("-") -> {
                    val parts = resolvedExpr.split("-").mapNotNull { it.trim().toDoubleOrNull() }
                    if (parts.size >= 2) formatResult(parts[0] - parts.drop(1).sum()) else resolvedExpr
                }
                resolvedExpr.contains("*") -> {
                    val parts = resolvedExpr.split("*").mapNotNull { it.trim().toDoubleOrNull() }
                    if (parts.isNotEmpty()) formatResult(parts.reduce { acc, d -> acc * d }) else resolvedExpr
                }
                resolvedExpr.contains("/") -> {
                    val parts = resolvedExpr.split("/").mapNotNull { it.trim().toDoubleOrNull() }
                    if (parts.size >= 2) {
                        if (parts.drop(1).any { it == 0.0 }) {
                            "#DIV/0!"
                        } else {
                            formatResult(parts.drop(1).fold(parts[0]) { acc, d -> acc / d })
                        }
                    } else resolvedExpr
                }
                else -> {
                    val num = resolvedExpr.trim().toDoubleOrNull()
                    if (num != null) formatResult(num) else resolvedExpr
                }
            }
        } catch (e: Exception) {
            resolvedExpr
        }
    }

    private fun formatResult(value: Double): String {
        return if (value.isNaN() || value.isInfinite()) {
            "#ERROR!"
        } else if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
    }
}

