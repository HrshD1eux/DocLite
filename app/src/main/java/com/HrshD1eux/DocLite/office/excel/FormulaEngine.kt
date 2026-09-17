package com.HrshD1eux.DocLite.office.excel

import com.HrshD1eux.DocLite.models.Sheet
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormulaEngine {

    companion object {
        const val ERROR_DIV_ZERO = "#DIV/0!"
        const val ERROR_NAME = "#NAME?"
        const val ERROR_REF = "#REF!"
        const val ERROR_VALUE = "#VALUE!"
        const val ERROR_NOT_AVAILABLE = "#N/A"
        const val ERROR_GENERIC = "#ERROR!"

        private val KNOWN_FUNCTIONS = setOf(
            "SUM", "AVERAGE", "MIN", "MAX", "COUNT",
            "IF", "VLOOKUP", "INDEX", "MATCH", "ROUND",
            "CONCAT", "CONCATENATE", "TODAY"
        )
    }

    fun evaluateFormula(formula: String, sheet: Sheet): String {
        val cleanFormula = formula.trim()
        if (!cleanFormula.startsWith("=")) return formula

        val expression = cleanFormula.substring(1).trim()
        if (expression.isEmpty()) return ""

        return try {
            evaluateExpression(expression, sheet)
        } catch (e: ArithmeticException) {
            ERROR_DIV_ZERO
        } catch (e: IllegalArgumentException) {
            if (e.message in setOf(ERROR_DIV_ZERO, ERROR_NAME, ERROR_REF, ERROR_VALUE, ERROR_NOT_AVAILABLE, ERROR_GENERIC)) {
                e.message!!
            } else {
                ERROR_VALUE
            }
        } catch (e: Exception) {
            ERROR_GENERIC
        }
    }

    private fun evaluateExpression(expr: String, sheet: Sheet): String {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return ""

        // Check for string concatenation (& operator) outside quotes
        val concatParts = splitByOperatorOutsideQuotes(trimmed, '&')
        if (concatParts.size > 1) {
            val sb = StringBuilder()
            for (part in concatParts) {
                sb.append(evaluateExpression(part, sheet))
            }
            return sb.toString()
        }

        // Check if expression is a function call: e.g. FUNC_NAME(...)
        if (trimmed.endsWith(")") && trimmed.contains("(")) {
            val openParen = trimmed.indexOf('(')
            val candidateFunc = trimmed.substring(0, openParen).trim().uppercase()
            if (candidateFunc.isNotEmpty() && candidateFunc.all { it.isLetterOrDigit() || it == '_' }) {
                if (candidateFunc !in KNOWN_FUNCTIONS) {
                    throw IllegalArgumentException(ERROR_NAME)
                }

                val argsStr = trimmed.substring(openParen + 1, trimmed.length - 1)
                return when (candidateFunc) {
                    "SUM" -> evaluateSum(argsStr, sheet)
                    "AVERAGE" -> evaluateAverage(argsStr, sheet)
                    "MIN" -> evaluateMin(argsStr, sheet)
                    "MAX" -> evaluateMax(argsStr, sheet)
                    "COUNT" -> evaluateCount(argsStr, sheet)
                    "IF" -> evaluateIf(argsStr, sheet)
                    "VLOOKUP" -> evaluateVlookup(argsStr, sheet)
                    "INDEX" -> evaluateIndex(argsStr, sheet)
                    "MATCH" -> evaluateMatch(argsStr, sheet)
                    "ROUND" -> evaluateRound(argsStr, sheet)
                    "CONCAT", "CONCATENATE" -> evaluateConcat(argsStr, sheet)
                    "TODAY" -> evaluateToday()
                    else -> throw IllegalArgumentException(ERROR_NAME)
                }
            }
        } else if (trimmed.equals("TODAY()", ignoreCase = true) || trimmed.equals("TODAY", ignoreCase = true)) {
            return evaluateToday()
        }

        // Quoted string literal: "hello"
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Single cell reference: e.g. A1
        Sheet.cellNameToCoords(trimmed.uppercase())?.let { coords ->
            val cell = sheet.getCell(coords.first, coords.second)
            return if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
        }

        // Math expression with PEMDAS
        return evaluateMath(trimmed, sheet)
    }

    private fun evaluateSum(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val values = extractValuesFromArgs(args, sheet)
        return formatResult(values.sum())
    }

    private fun evaluateAverage(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val values = extractValuesFromArgs(args, sheet)
        if (values.isEmpty()) return "0"
        return formatResult(values.average())
    }

    private fun evaluateMin(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val values = extractValuesFromArgs(args, sheet)
        if (values.isEmpty()) return "0"
        return formatResult(values.minOrNull() ?: 0.0)
    }

    private fun evaluateMax(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val values = extractValuesFromArgs(args, sheet)
        if (values.isEmpty()) return "0"
        return formatResult(values.maxOrNull() ?: 0.0)
    }

    private fun evaluateCount(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val values = extractValuesFromArgs(args, sheet)
        return values.size.toString()
    }

    private fun evaluateRound(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        if (args.isEmpty() || args.size > 2) throw IllegalArgumentException(ERROR_VALUE)
        val num = resolveValue(args[0], sheet).toDoubleOrNull() ?: throw IllegalArgumentException(ERROR_VALUE)
        val digits = if (args.size > 1) {
            resolveValue(args[1], sheet).toDoubleOrNull()?.toInt() ?: 0
        } else {
            0
        }
        val bd = BigDecimal.valueOf(num).setScale(digits.coerceAtLeast(0), RoundingMode.HALF_UP)
        return if (digits == 0) {
            bd.toLong().toString()
        } else {
            val plain = bd.toPlainString()
            if (plain.contains(".") && plain.endsWith("0")) plain.trimEnd('0').trimEnd('.') else plain
        }
    }

    private fun evaluateConcat(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        val sb = StringBuilder()
        for (arg in args) {
            sb.append(resolveValue(arg, sheet))
        }
        return sb.toString()
    }

    private fun evaluateToday(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun evaluateIf(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        if (args.size < 2 || args.size > 3) throw IllegalArgumentException(ERROR_VALUE)

        val condition = args[0].trim()
        val conditionResult = evaluateCondition(condition, sheet)

        return if (conditionResult) {
            evaluateExpression(args[1], sheet)
        } else if (args.size > 2) {
            evaluateExpression(args[2], sheet)
        } else {
            "FALSE"
        }
    }

    private fun evaluateCondition(cond: String, sheet: Sheet): Boolean {
        // Operators in order of precedence for detection: <=, >=, <>, !=, =, <, >
        val ops = listOf("<=", ">=", "<>", "!=", "=", "<", ">")
        var chosenOp: String? = null
        var opIndex = -1

        for (op in ops) {
            val idx = findOperatorOutsideQuotes(cond, op)
            if (idx != -1) {
                chosenOp = op
                opIndex = idx
                break
            }
        }

        if (chosenOp == null || opIndex == -1) {
            val singleVal = resolveValue(cond, sheet)
            return singleVal.equals("TRUE", ignoreCase = true) || (singleVal.toDoubleOrNull() ?: 0.0) != 0.0
        }

        val leftExpr = cond.substring(0, opIndex).trim()
        val rightExpr = cond.substring(opIndex + chosenOp.length).trim()

        val leftVal = resolveValue(leftExpr, sheet)
        val rightVal = resolveValue(rightExpr, sheet)

        val leftNum = leftVal.toDoubleOrNull()
        val rightNum = rightVal.toDoubleOrNull()

        if (leftNum != null && rightNum != null) {
            return when (chosenOp) {
                "=" -> leftNum == rightNum
                "<>", "!=" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                ">" -> leftNum > rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> leftNum >= rightNum
                else -> false
            }
        }

        val cmp = leftVal.compareTo(rightVal, ignoreCase = true)
        return when (chosenOp) {
            "=" -> cmp == 0
            "<>", "!=" -> cmp != 0
            "<" -> cmp < 0
            ">" -> cmp > 0
            "<=" -> cmp <= 0
            ">=" -> cmp >= 0
            else -> false
        }
    }

    private fun evaluateVlookup(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        if (args.size < 3 || args.size > 4) throw IllegalArgumentException(ERROR_VALUE)

        val lookupVal = resolveValue(args[0], sheet)
        val range = Sheet.parseRangeCoords(args[1]) ?: throw IllegalArgumentException(ERROR_REF)
        val colIndex = resolveValue(args[2], sheet).toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException(ERROR_VALUE)

        val (start, end) = range
        val rangeWidth = end.second - start.second + 1
        if (colIndex < 1 || colIndex > rangeWidth) {
            throw IllegalArgumentException(ERROR_REF)
        }

        val targetCol = start.second + colIndex - 1
        val lookupNum = lookupVal.toDoubleOrNull()

        for (r in start.first..end.first) {
            val cell = sheet.getCell(r, start.second)
            val cellVal = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
            val cellNum = cellVal.toDoubleOrNull()

            val matches = if (lookupNum != null && cellNum != null) {
                lookupNum == cellNum
            } else {
                cellVal.equals(lookupVal, ignoreCase = true)
            }

            if (matches) {
                val targetCell = sheet.getCell(r, targetCol)
                return if (targetCell.evaluatedValue.isNotEmpty()) targetCell.evaluatedValue else targetCell.value
            }
        }

        throw IllegalArgumentException(ERROR_NOT_AVAILABLE)
    }

    private fun evaluateIndex(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        if (args.size < 2 || args.size > 3) throw IllegalArgumentException(ERROR_VALUE)

        val range = Sheet.parseRangeCoords(args[0]) ?: throw IllegalArgumentException(ERROR_REF)
        val rowNum = resolveValue(args[1], sheet).toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException(ERROR_VALUE)
        val colNum = if (args.size > 2) {
            resolveValue(args[2], sheet).toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException(ERROR_VALUE)
        } else {
            1
        }

        val (start, end) = range
        val targetRow = start.first + rowNum - 1
        val targetCol = start.second + colNum - 1

        if (targetRow < start.first || targetRow > end.first || targetCol < start.second || targetCol > end.second) {
            throw IllegalArgumentException(ERROR_REF)
        }

        val cell = sheet.getCell(targetRow, targetCol)
        return if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
    }

    private fun evaluateMatch(argsStr: String, sheet: Sheet): String {
        val args = splitArguments(argsStr)
        if (args.size < 2 || args.size > 3) throw IllegalArgumentException(ERROR_VALUE)

        val lookupVal = resolveValue(args[0], sheet)
        val range = Sheet.parseRangeCoords(args[1]) ?: throw IllegalArgumentException(ERROR_REF)
        val lookupNum = lookupVal.toDoubleOrNull()

        val (start, end) = range
        var index = 1

        for (r in start.first..end.first) {
            for (c in start.second..end.second) {
                val cell = sheet.getCell(r, c)
                val cellVal = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                val cellNum = cellVal.toDoubleOrNull()

                val matches = if (lookupNum != null && cellNum != null) {
                    lookupNum == cellNum
                } else {
                    cellVal.equals(lookupVal, ignoreCase = true)
                }

                if (matches) return index.toString()
                index++
            }
        }

        throw IllegalArgumentException(ERROR_NOT_AVAILABLE)
    }

    private fun extractValuesFromArgs(args: List<String>, sheet: Sheet): List<Double> {
        val values = mutableListOf<Double>()
        for (arg in args) {
            val cleanArg = arg.trim()
            val range = Sheet.parseRangeCoords(cleanArg)
            if (range != null) {
                val (start, end) = range
                for (r in start.first..end.first) {
                    for (c in start.second..end.second) {
                        val cell = sheet.getCell(r, c)
                        val valStr = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                        val num = valStr.toDoubleOrNull()
                        if (num != null) values.add(num)
                    }
                }
            } else {
                val coords = Sheet.cellNameToCoords(cleanArg.uppercase())
                if (coords != null) {
                    val cell = sheet.getCell(coords.first, coords.second)
                    val valStr = if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
                    val num = valStr.toDoubleOrNull()
                    if (num != null) values.add(num)
                } else {
                    val resolved = try {
                        evaluateExpression(cleanArg, sheet).toDoubleOrNull()
                    } catch (e: Exception) {
                        null
                    }
                    if (resolved != null) values.add(resolved)
                }
            }
        }
        return values
    }

    private fun resolveValue(arg: String, sheet: Sheet): String {
        val clean = arg.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length >= 2) {
            return clean.substring(1, clean.length - 1)
        }
        val coords = Sheet.cellNameToCoords(clean.uppercase())
        if (coords != null) {
            val cell = sheet.getCell(coords.first, coords.second)
            return if (cell.evaluatedValue.isNotEmpty()) cell.evaluatedValue else cell.value
        }
        return evaluateExpression(clean, sheet)
    }

    private fun splitArguments(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        var parenDepth = 0
        var inQuotes = false
        var current = StringBuilder()

        for (ch in argsStr) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch == '(' && !inQuotes -> {
                    parenDepth++
                    current.append(ch)
                }
                ch == ')' && !inQuotes -> {
                    parenDepth = maxOf(0, parenDepth - 1)
                    current.append(ch)
                }
                ch == ',' && parenDepth == 0 && !inQuotes -> {
                    args.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotEmpty() || args.isNotEmpty()) {
            args.add(current.toString().trim())
        }

        return args.filter { it.isNotEmpty() }
    }

    private fun splitByOperatorOutsideQuotes(str: String, op: Char): List<String> {
        val parts = mutableListOf<String>()
        var inQuotes = false
        var parenDepth = 0
        var current = StringBuilder()

        for (ch in str) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch == '(' && !inQuotes -> {
                    parenDepth++
                    current.append(ch)
                }
                ch == ')' && !inQuotes -> {
                    parenDepth = maxOf(0, parenDepth - 1)
                    current.append(ch)
                }
                ch == op && parenDepth == 0 && !inQuotes -> {
                    parts.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        parts.add(current.toString().trim())
        return parts
    }

    private fun findOperatorOutsideQuotes(str: String, op: String): Int {
        var inQuotes = false
        var parenDepth = 0

        for (i in 0 until (str.length - op.length + 1)) {
            val ch = str[i]
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == '(' && !inQuotes) {
                parenDepth++
            } else if (ch == ')' && !inQuotes) {
                parenDepth = maxOf(0, parenDepth - 1)
            } else if (!inQuotes && parenDepth == 0) {
                if (str.startsWith(op, i)) {
                    return i
                }
            }
        }
        return -1
    }

    private fun evaluateMath(expression: String, sheet: Sheet): String {
        // Resolve cell names like A1 + B1 using token matching
        val cellRegex = Regex("\\b[A-Za-z]+[0-9]+\\b")
        val resolvedExpr = cellRegex.replace(expression) { match ->
            val cellName = match.value.uppercase()
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

        val evaluator = MathEvaluator(resolvedExpr)
        val result = evaluator.parse()
        return formatResult(result)
    }

    private fun formatResult(value: Double): String {
        return if (value.isNaN() || value.isInfinite()) {
            ERROR_DIV_ZERO
        } else if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    /**
     * Recursive descent parser for math expressions respecting standard PEMDAS:
     * - Parentheses ()
     * - Exponents ^
     * - Multiplication * and Division / (and Modulo %)
     * - Addition + and Subtraction -
     * - Unary plus/minus
     */
    private class MathEvaluator(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            while (ch == ' '.code) nextChar()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character: " + ch.toChar())
            return x
        }

        // Expression = Term (('+' | '-') Term)*
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        // Term = Factor (('*' | '/' | '%') Factor)*
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        // Factor = Primary ('^' Factor)?
        private fun parseFactor(): Double {
            while (ch == ' '.code) nextChar()
            if (eat('+'.code)) return parseFactor() // unary plus
            if (eat('-'.code)) return -parseFactor() // unary minus

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected: " + (if (ch != -1) ch.toChar() else "end of input"))
            }

            if (eat('^'.code)) x = Math.pow(x, parseFactor())

            return x
        }
    }
}
