package com.HrshD1eux.DocLite.bankstatement.parser

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.bankstatement.model.BankTransaction
import com.HrshD1eux.DocLite.bankstatement.model.PartySummary
import com.HrshD1eux.DocLite.bankstatement.model.StatementAnalysisResult
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader

class PasswordRequiredException(message: String) : Exception(message)
class BankStatementParseException(message: String) : Exception(message)

class BankStatementParser(private val context: Context) {

    suspend fun parseStatement(
        uri: Uri,
        password: String? = null
    ): StatementAnalysisResult = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        // Passwords for Office docs are handled natively by POI if needed,
        // but for now we attempt open. If encrypted, it throws EncryptedDocumentException.

        val rows = mutableListOf<List<String>>()

        try {
            if (ext == "csv" || ext == "txt") {
                rows.addAll(parseCsvRows(uri))
            } else if (ext == "xlsx" || ext == "xls") {
                rows.addAll(parseExcelRows(uri, password))
            } else {
                throw BankStatementParseException("Unsupported file type: $ext")
            }
        } catch (e: org.apache.poi.EncryptedDocumentException) {
            throw PasswordRequiredException("This file is password-protected. Please enter password.")
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            throw BankStatementParseException("Failed to parse document: ${e.message}")
        }

        if (rows.isEmpty()) {
            throw BankStatementParseException("Could not read any transaction data from $fileName")
        }

        processTransactionRows(fileName, rows)
    }

    private fun parseCsvRows(uri: Uri): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = InputStreamReader(inputStream)
            val csvReader = CSVReaderBuilder(reader).build()
            
            var line: Array<String>? = csvReader.readNext()
            while (line != null) {
                if (line.any { it.isNotBlank() }) {
                    rows.add(line.toList())
                }
                line = csvReader.readNext()
            }
        }
        return rows
    }

    private fun parseExcelRows(uri: Uri, password: String?): List<List<String>> {
        val resultRows = mutableListOf<List<String>>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val workbook = if (password.isNullOrEmpty()) {
                WorkbookFactory.create(inputStream)
            } else {
                WorkbookFactory.create(inputStream, password)
            }
            
            val sheet = workbook.getSheetAt(0)
            for (row in sheet) {
                val rowData = mutableListOf<String>()
                val lastCol = row.lastCellNum.toInt()
                for (cn in 0 until lastCol) {
                    val cell = row.getCell(cn)
                    if (cell == null) {
                        rowData.add("")
                    } else {
                        val value = when (cell.cellType) {
                            CellType.STRING -> cell.stringCellValue
                            CellType.NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    cell.dateCellValue?.toString() ?: ""
                                } else {
                                    val num = cell.numericCellValue
                                    if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                                }
                            }
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            CellType.FORMULA -> cell.cellFormula
                            else -> ""
                        }
                        rowData.add(value)
                    }
                }
                if (rowData.any { it.isNotBlank() }) {
                    resultRows.add(rowData)
                }
            }
            workbook.close()
        }
        return resultRows
    }

    private fun processTransactionRows(
        fileName: String,
        allRows: List<List<String>>
    ): StatementAnalysisResult {
        var headerRowIndex = -1
        var dateColIndex = -1
        var partyColIndex = -1
        var creditColIndex = -1
        var debitColIndex = -1
        var amountColIndex = -1
        var typeColIndex = -1

        // 1. Detect Header Row in top 25 rows
        for (i in 0 until minOf(25, allRows.size)) {
            val row = allRows[i].map { it.lowercase().trim() }

            var hasDate = false
            var hasNarration = false
            var hasCreditOrDebit = false

            row.forEachIndexed { colIdx, text ->
                if (text.contains("date")) {
                    dateColIndex = colIdx
                    hasDate = true
                }
                if (text.contains("particular") || text.contains("description") ||
                    text.contains("narration") || text.contains("party") ||
                    text.contains("account") || text.contains("payee") ||
                    text.contains("sender") || text.contains("remarks") || text.contains("details")) {
                    partyColIndex = colIdx
                    hasNarration = true
                }
                if (text == "credit" || text.contains("cr") || text.contains("deposit") || text.contains("money in") || text.contains("received")) {
                    creditColIndex = colIdx
                    hasCreditOrDebit = true
                }
                if (text == "debit" || text.contains("dr") || text.contains("withdrawal") || text.contains("money out") || text.contains("paid") || text.contains("spent")) {
                    debitColIndex = colIdx
                    hasCreditOrDebit = true
                }
                if (text == "amount" || text.contains("txn amount") || text.contains("net amount")) {
                    amountColIndex = colIdx
                }
                if (text == "type" || text == "d/c" || text == "cr/dr" || text.contains("indicator")) {
                    typeColIndex = colIdx
                }
            }

            if ((hasDate || hasNarration) && (hasCreditOrDebit || amountColIndex != -1)) {
                headerRowIndex = i
                break
            }
        }

        if (headerRowIndex == -1) {
            throw BankStatementParseException("Could not identify the header row. Please ensure columns have clear names like 'Date', 'Description', 'Credit', 'Debit'.")
        }

        if (partyColIndex == -1) partyColIndex = 1.coerceAtMost(allRows.firstOrNull()?.lastIndex ?: 0)
        if (dateColIndex == -1) dateColIndex = 0

        val transactions = mutableListOf<BankTransaction>()
        var totalCredit = 0.0
        var totalDebit = 0.0
        var totalCreditCount = 0
        var totalDebitCount = 0

        val creditPartyMap = mutableMapOf<String, Pair<Double, Int>>() // party -> (sumAmount, count)
        val debitPartyMap = mutableMapOf<String, Pair<Double, Int>>()  // party -> (sumAmount, count)

        // Process data rows
        val dataRows = allRows.drop(headerRowIndex + 1)

        dataRows.forEachIndexed { idx, row ->
            if (row.isEmpty() || row.all { it.isBlank() }) return@forEachIndexed

            val narration = if (partyColIndex < row.size) row[partyColIndex].trim() else ""
            if (narration.isBlank()) return@forEachIndexed

            val lowerNarration = narration.lowercase()
            if (lowerNarration.contains("total") || lowerNarration.contains("opening balance") || lowerNarration.contains("closing balance")) {
                return@forEachIndexed
            }

            val dateStr = if (dateColIndex < row.size) row[dateColIndex].trim() else "N/A"

            var parsedCredit = 0.0
            var parsedDebit = 0.0
            var isCredit = false
            var isDebit = false

            if (creditColIndex != -1 && creditColIndex < row.size) {
                parsedCredit = parseDoubleAmount(row[creditColIndex])
            }
            if (debitColIndex != -1 && debitColIndex < row.size) {
                parsedDebit = parseDoubleAmount(row[debitColIndex])
            }

            if (parsedCredit > 0) {
                isCredit = true
            } else if (parsedDebit > 0) {
                isDebit = true
            } else if (amountColIndex != -1 && amountColIndex < row.size) {
                val amt = parseDoubleAmount(row[amountColIndex])
                val typeStr = if (typeColIndex != -1 && typeColIndex < row.size) row[typeColIndex].uppercase() else ""

                if (typeStr.contains("CR") || typeStr.contains("DEPOSIT") || typeStr.contains("CREDIT") || amt > 0) {
                    isCredit = true
                    parsedCredit = Math.abs(amt)
                } else if (typeStr.contains("DR") || typeStr.contains("WITHDRAW") || typeStr.contains("DEBIT") || amt < 0) {
                    isDebit = true
                    parsedDebit = Math.abs(amt)
                } else {
                    if (lowerNarration.contains("by ") || lowerNarration.contains("cr") || lowerNarration.contains("salary") || lowerNarration.contains("refund")) {
                        isCredit = true
                        parsedCredit = Math.abs(amt)
                    } else {
                        isDebit = true
                        parsedDebit = Math.abs(amt)
                    }
                }
            } else {
                row.forEach { cell ->
                    val valAmt = parseDoubleAmount(cell)
                    if (valAmt > 0) {
                        if (lowerNarration.contains("cr") || lowerNarration.contains("by ")) {
                            isCredit = true
                            parsedCredit = valAmt
                        } else {
                            isDebit = true
                            parsedDebit = valAmt
                        }
                    }
                }
            }

            if (!isCredit && !isDebit) return@forEachIndexed

            val cleanParty = extractPartyName(narration)

            if (isCredit && parsedCredit > 0) {
                totalCredit += parsedCredit
                totalCreditCount++

                val current = creditPartyMap.getOrDefault(cleanParty, Pair(0.0, 0))
                creditPartyMap[cleanParty] = Pair(current.first + parsedCredit, current.second + 1)

                transactions.add(
                    BankTransaction(
                        id = "tx_${idx}_cr",
                        date = dateStr,
                        partyName = cleanParty,
                        narration = narration,
                        amount = parsedCredit,
                        isCredit = true
                    )
                )
            }

            if (isDebit && parsedDebit > 0) {
                totalDebit += parsedDebit
                totalDebitCount++

                val current = debitPartyMap.getOrDefault(cleanParty, Pair(0.0, 0))
                debitPartyMap[cleanParty] = Pair(current.first + parsedDebit, current.second + 1)

                transactions.add(
                    BankTransaction(
                        id = "tx_${idx}_dr",
                        date = dateStr,
                        partyName = cleanParty,
                        narration = narration,
                        amount = parsedDebit,
                        isCredit = false
                    )
                )
            }
        }

        if (transactions.isEmpty()) {
            throw BankStatementParseException("Successfully read the file, but no valid transactions were found.")
        }

        val topDebitRecipients = debitPartyMap.map { (party, pair) ->
            PartySummary(partyName = party, totalAmount = pair.first, transactionCount = pair.second)
        }.sortedByDescending { it.totalAmount }.take(60)

        val topCreditSenders = creditPartyMap.map { (party, pair) ->
            PartySummary(partyName = party, totalAmount = pair.first, transactionCount = pair.second)
        }.sortedByDescending { it.totalAmount }.take(60)

        return StatementAnalysisResult(
            fileName = fileName,
            totalCreditAmount = totalCredit,
            totalDebitAmount = totalDebit,
            totalCreditCount = totalCreditCount,
            totalDebitCount = totalDebitCount,
            topDebitRecipients = topDebitRecipients,
            topCreditSenders = topCreditSenders,
            rawTransactions = transactions
        )
    }

    private fun extractPartyName(narration: String): String {
        var clean = narration.trim()

        if (clean.contains("/")) {
            val parts = clean.split("/").map { it.trim() }.filter { it.isNotBlank() }
            val namePart = parts.firstOrNull { part ->
                part.length >= 3 &&
                !part.all { it.isDigit() } &&
                !part.equals("UPI", ignoreCase = true) &&
                !part.equals("IMPS", ignoreCase = true) &&
                !part.equals("NEFT", ignoreCase = true) &&
                !part.equals("RTGS", ignoreCase = true) &&
                !part.equals("CR", ignoreCase = true) &&
                !part.equals("DR", ignoreCase = true) &&
                !part.equals("PAY", ignoreCase = true) &&
                !part.matches(Regex("^[0-9A-Z]{8,}$"))
            }
            if (namePart != null) {
                clean = namePart
            }
        } else if (clean.contains("-")) {
            val parts = clean.split("-").map { it.trim() }.filter { it.isNotBlank() }
            val namePart = parts.lastOrNull { part ->
                !part.all { it.isDigit() } && part.length >= 3
            }
            if (namePart != null) {
                clean = namePart
            }
        }

        clean = clean.replace(Regex("\\b[0-9]{8,}\\b"), "")
            .replace(Regex("\\b(BY|TO|NEFT|IMPS|UPI|RTGS|TRANSFER|INF|POS|ATM|WDR)\\b", RegexOption.IGNORE_CASE), "")
            .trim()

        if (clean.isBlank()) clean = narration.take(30)
        return clean.uppercase()
    }

    private fun parseDoubleAmount(str: String): Double {
        if (str.isBlank()) return 0.0
        val clean = str.replace(",", "")
            .replace("₹", "")
            .replace("$", "")
            .replace("Rs", "", ignoreCase = true)
            .trim()
        return clean.toDoubleOrNull() ?: 0.0
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
        return result ?: "Bank_Statement.xlsx"
    }
}
