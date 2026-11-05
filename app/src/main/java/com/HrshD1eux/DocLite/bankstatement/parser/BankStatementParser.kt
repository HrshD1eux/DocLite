package com.HrshD1eux.DocLite.bankstatement.parser

import android.content.Context
import android.net.Uri
import com.HrshD1eux.DocLite.core.utils.UriResolver
import com.HrshD1eux.DocLite.bankstatement.model.BankTransaction
import com.HrshD1eux.DocLite.bankstatement.model.PartySummary
import com.HrshD1eux.DocLite.bankstatement.model.StatementAnalysisResult
import com.opencsv.CSVReaderBuilder
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
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

        val rows = mutableListOf<List<String>>()

        try {
            if (ext == "csv" || ext == "txt") {
                rows.addAll(parseCsvRows(uri))
            } else if (ext == "xlsx" || ext == "xls") {
                rows.addAll(parseExcelRows(uri, password))
            } else if (ext == "pdf") {
                rows.addAll(parsePdfRows(uri, password))
            } else {
                throw BankStatementParseException("Unsupported file type: $ext. Please choose an Excel (.xlsx, .xls), CSV, or PDF bank statement.")
            }
        } catch (e: org.apache.poi.EncryptedDocumentException) {
            throw PasswordRequiredException("This file is password-protected. Please enter password.")
        } catch (e: InvalidPasswordException) {
            throw PasswordRequiredException("This PDF statement is password-protected. Please enter password.")
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypted", ignoreCase = true) == true) {
                throw PasswordRequiredException("This file is password-protected. Please enter password.")
            }
            e.printStackTrace()
            throw BankStatementParseException("Failed to parse document: ${e.message}")
        }

        if (rows.isEmpty()) {
            throw BankStatementParseException("Could not read any transaction data from $fileName")
        }

        processTransactionRows(fileName, rows)
    }

    private fun parsePdfRows(uri: Uri, password: String?): List<List<String>> {
        val resultRows = mutableListOf<List<String>>()
        PDFBoxResourceLoader.init(context.applicationContext)

        val inputStream = UriResolver.openInputStream(context, uri)
            ?: throw java.io.FileNotFoundException("Could not open PDF file")

        val document = try {
            if (password.isNullOrEmpty()) {
                PDDocument.load(inputStream)
            } else {
                PDDocument.load(inputStream, password)
            }
        } catch (e: InvalidPasswordException) {
            throw PasswordRequiredException("This PDF statement is password-protected. Please enter password.")
        } catch (e: java.io.IOException) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypted", ignoreCase = true) == true) {
                throw PasswordRequiredException("This PDF statement is password-protected. Please enter password.")
            }
            throw e
        }

        document.use { doc ->
            if (doc.isEncrypted && password.isNullOrEmpty()) {
                throw PasswordRequiredException("This PDF statement is password-protected. Please enter password.")
            }

            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.wordSeparator = " " // Single space ensures natural sentence words are not split into columns
            val text = stripper.getText(doc) ?: ""
            val lines = text.lines()

            val universalDateRegex = Regex("(?<!\\d)(?:\\d{1,2}[/.-](?:\\d{1,2}|[A-Za-z]{3,9})[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})(?!\\d)", RegexOption.IGNORE_CASE)
            // Flexible regex that matches amounts even if decimal dot has spaces around it (e.g. 1499 . 00) or commas
            val currencyAmountRegex = Regex("(?<!\\w)(?:₹|Rs\\.?|\\$)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(CR|DR|cr|dr)?(?!\\w)")

            for (line in lines) {
                // Normalize spaces around dots and commas inside numbers (e.g. "1,499 . 00" -> "1,499.00")
                val trimmed = line
                    .replace(Regex("(\\d+)\\s*\\.\\s*(\\d{1,2})\\b"), "$1.$2")
                    .replace(Regex("(\\d+)\\s*,\\s*(\\d{2,3})"), "$1,$2")
                    .trim()
                if (trimmed.isBlank()) continue

                // Skip recurring boilerplate
                if (trimmed.startsWith("Page ", ignoreCase = true) ||
                    trimmed.startsWith("Statement of Account", ignoreCase = true) ||
                    trimmed.contains("Generated On:", ignoreCase = true) ||
                    trimmed.contains("This is a system generated", ignoreCase = true) ||
                    trimmed.contains("Statement Period", ignoreCase = true)) {
                    continue
                }

                val dateMatch = universalDateRegex.find(trimmed)
                if (dateMatch != null) {
                    val dateStr = dateMatch.value
                    val beforeDate = trimmed.substring(0, dateMatch.range.first).trim()
                    val afterDate = trimmed.substring(dateMatch.range.last + 1).trim()

                    // Filter valid transaction amounts (must have dot, comma, CR/DR or appear at the end)
                    val rawAmtMatches = currencyAmountRegex.findAll(afterDate).toList()
                    val amtMatches = rawAmtMatches.filter { match ->
                        val v = match.groupValues[1]
                        val tag = match.groupValues[2]
                        v.contains('.') || v.contains(',') || tag.isNotEmpty() || (match.range.first >= afterDate.length / 2 && v.toDoubleOrNull() ?: 0.0 >= 10.0)
                    }

                    if (amtMatches.isNotEmpty()) {
                        val firstAmtStart = amtMatches.first().range.first
                        val narration = afterDate.substring(0, firstAmtStart).trim()
                        val row = mutableListOf<String>()
                        row.add(dateStr)
                        row.add(if (narration.isNotBlank()) narration else (if (beforeDate.isNotBlank()) beforeDate else "Transaction"))
                        for (amt in amtMatches) {
                            row.add(amt.value.trim())
                        }
                        resultRows.add(row)
                    } else {
                        val tokens = trimmed.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotBlank() }
                        if (tokens.size >= 2) {
                            resultRows.add(tokens)
                        } else {
                            resultRows.add(listOf(dateStr, afterDate))
                        }
                    }
                } else if (resultRows.isNotEmpty()) {
                    val rawAmtMatches = currencyAmountRegex.findAll(trimmed).toList()
                    val amtMatches = rawAmtMatches.filter { match ->
                        val v = match.groupValues[1]
                        val tag = match.groupValues[2]
                        v.contains('.') || v.contains(',') || tag.isNotEmpty()
                    }
                    if (amtMatches.isEmpty()) {
                        // Multi-line narration continuation: append to previous row's narration
                        val lastIdx = resultRows.lastIndex
                        val prevRow = resultRows[lastIdx].toMutableList()
                        if (prevRow.size >= 2) {
                            prevRow[1] = "${prevRow[1]} $trimmed"
                            resultRows[lastIdx] = prevRow
                        }
                    } else {
                        val tokens = trimmed.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotBlank() }
                        if (tokens.size >= 2) {
                            resultRows.add(tokens)
                        }
                    }
                } else {
                    val tokens = trimmed.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotBlank() }
                    if (tokens.size >= 2) {
                        resultRows.add(tokens)
                    }
                }
            }

            // If rows have structured format, add explicit header row so processTransactionRows never misclassifies balance as credit
            val firstData = resultRows.firstOrNull { it.size >= 3 && universalDateRegex.containsMatchIn(it[0]) }
            if (firstData != null) {
                if (firstData.size == 4) {
                    resultRows.add(0, listOf("Date", "Description", "Amount", "Balance"))
                } else if (firstData.size >= 5) {
                    resultRows.add(0, listOf("Date", "Description", "Debit", "Credit", "Balance"))
                }
            }
        }
        return resultRows
    }

    private fun parseCsvRows(uri: Uri): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        UriResolver.openInputStream(context, uri)?.use { inputStream ->
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
        
        UriResolver.openInputStream(context, uri)?.use { inputStream ->
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

    internal fun processTransactionRows(
        fileName: String,
        allRows: List<List<String>>
    ): StatementAnalysisResult {
        var headerRowIndex = -1
        var dateColIndex = -1
        var partyColIndex = -1
        var creditColIndex = -1
        var debitColIndex = -1
        var amountColIndex = -1
        var balanceColIndex = -1
        var typeColIndex = -1

        // 1. Detect Header Row in top 50 rows
        for (i in 0 until minOf(50, allRows.size)) {
            val row = allRows[i].map { it.lowercase().trim() }

            var hasDate = false
            var hasNarration = false
            var hasCreditOrDebit = false

            row.forEachIndexed { colIdx, text ->
                // Check if this column is the Balance column first! NEVER match balance as credit/debit!
                val isBalance = text.contains("balance") || text.contains("closing") || text.contains("running") ||
                    text.contains("available") || text == "bal" || text.startsWith("bal ") ||
                    text.endsWith(" bal") || text.contains("bal(") || text.contains("bal (") || text.contains("bal.")
                if (isBalance) {
                    balanceColIndex = colIdx
                    return@forEachIndexed
                }

                if (text.contains("date") || text == "dt" || text == "txn date" || text == "value date") {
                    dateColIndex = colIdx
                    hasDate = true
                    return@forEachIndexed
                }

                val isNarration = text.contains("particular") || text.contains("description") ||
                    text.contains("narration") || text.contains("party") ||
                    text.contains("account") || text.contains("payee") ||
                    text.contains("sender") || text.contains("remarks") || text.contains("details") ||
                    text.contains("transaction") || text.contains("tran details")
                if (isNarration) {
                    partyColIndex = colIdx
                    hasNarration = true
                    return@forEachIndexed
                }

                val isChqRef = text.contains("chq") || text.contains("cheque") || text.contains("ref") ||
                    text.contains("utr") || text.contains("txn id") || text.contains("trans id") ||
                    text.contains("instrument") || text.contains("reference")
                if (isChqRef) {
                    return@forEachIndexed
                }

                val isCredit = text in listOf("credit", "cr", "cr.", "cr amt", "cr amount") ||
                    text.contains("deposit") || text.contains("money in") || text.contains("received") ||
                    text.contains("receipt") || text.contains("paid in") ||
                    text.matches(Regex(".*\\b(cr|credit|deposits?)\\b.*"))
                if (isCredit) {
                    creditColIndex = colIdx
                    hasCreditOrDebit = true
                    return@forEachIndexed
                }

                val isDebit = text in listOf("debit", "dr", "dr.", "dr amt", "dr amount") ||
                    text.contains("withdrawal") || text.contains("money out") || text.contains("paid out") ||
                    text.contains("spent") || text.contains("payment") ||
                    text.matches(Regex(".*\\b(dr|debit|withdrawals?)\\b.*"))
                if (isDebit) {
                    debitColIndex = colIdx
                    hasCreditOrDebit = true
                    return@forEachIndexed
                }

                if (text in listOf("amount", "amt", "txn amount", "net amount", "transaction amount")) {
                    amountColIndex = colIdx
                    return@forEachIndexed
                }

                if (text in listOf("type", "d/c", "c/d", "cr/dr", "dr/cr") || text.contains("indicator") || text.contains("tran type")) {
                    typeColIndex = colIdx
                    return@forEachIndexed
                }
            }

            if ((hasDate || hasNarration) && (hasCreditOrDebit || amountColIndex != -1)) {
                headerRowIndex = i
                break
            }
        }

        // Safety guarantee: balance column must never be credit or debit
        if (balanceColIndex != -1) {
            if (creditColIndex == balanceColIndex) creditColIndex = -1
            if (debitColIndex == balanceColIndex) debitColIndex = -1
            if (amountColIndex == balanceColIndex) amountColIndex = -1
        }

        if (headerRowIndex == -1) {
            val dateRegex = Regex("\\b(?:\\d{1,2}[/.-](?:\\d{1,2}|[A-Za-z]{3,9})[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{2,4})\\b", RegexOption.IGNORE_CASE)
            val firstDataRow = allRows.indexOfFirst { row ->
                row.isNotEmpty() && row.any { cell -> dateRegex.containsMatchIn(cell) } && row.any { cell -> parseDoubleAmount(cell) > 0 }
            }
            if (firstDataRow != -1) {
                headerRowIndex = (firstDataRow - 1).coerceAtLeast(0)
                // Find column that actually holds the date
                val sampleRow = allRows[firstDataRow]
                val detectedDateCol = sampleRow.indexOfFirst { dateRegex.containsMatchIn(it) }
                dateColIndex = if (detectedDateCol != -1) detectedDateCol else 0
                partyColIndex = if (dateColIndex == 0) 1.coerceAtMost(sampleRow.lastIndex) else 0
                if (sampleRow.size >= 5) {
                    debitColIndex = 2
                    creditColIndex = 3
                } else if (sampleRow.size == 4) {
                    // Check if column 2 and 3 behave as separate Debit & Credit columns (one is 0 or empty when other has value)
                    val hasMutuallyExclusiveDrCr = allRows.drop(firstDataRow).take(10).any { r ->
                        if (r.size >= 4) {
                            val a = parseDoubleAmount(r[2])
                            val b = parseDoubleAmount(r[3])
                            (a > 0 && b == 0.0) || (b > 0 && a == 0.0)
                        } else false
                    }
                    if (hasMutuallyExclusiveDrCr) {
                        debitColIndex = 2
                        creditColIndex = 3
                    } else {
                        amountColIndex = 2
                    }
                } else if (sampleRow.size == 3) {
                    amountColIndex = 2
                }
            } else {
                throw BankStatementParseException("Could not identify the header row. Please ensure columns have clear names like 'Date', 'Description', 'Credit', 'Debit'.")
            }
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
        val dataRows = if (headerRowIndex >= 0) allRows.drop(headerRowIndex + 1) else allRows
        var prevBalance: Double? = null

        dataRows.forEachIndexed { idx, row ->
            if (row.isEmpty() || row.all { it.isBlank() }) return@forEachIndexed

            val narration = if (partyColIndex < row.size && partyColIndex != -1) row[partyColIndex].trim() else (if (row.size > 1) row[1].trim() else "")
            if (narration.isBlank()) return@forEachIndexed

            val lowerNarration = narration.lowercase()
            if (lowerNarration.contains("total") || lowerNarration.contains("opening balance") || lowerNarration.contains("closing balance") || lowerNarration.contains("b/f")) {
                if (lowerNarration.contains("opening balance") || lowerNarration.contains("b/f")) {
                    val openingBal = if (balanceColIndex != -1 && balanceColIndex < row.size) parseDoubleAmount(row[balanceColIndex])
                                     else row.drop(2).map { parseDoubleAmount(it) }.firstOrNull { it > 0 }
                    if (openingBal != null && openingBal > 0) {
                        prevBalance = openingBal
                    }
                }
                return@forEachIndexed
            }

            val dateStr = if (dateColIndex < row.size && dateColIndex != -1) row[dateColIndex].trim() else "N/A"

            var parsedCredit = 0.0
            var parsedDebit = 0.0
            var isCredit = false
            var isDebit = false

            val bal = if (balanceColIndex != -1 && balanceColIndex < row.size) parseDoubleAmount(row[balanceColIndex])
                      else (if (amountColIndex != -1 && amountColIndex + 1 < row.size) parseDoubleAmount(row[amountColIndex + 1]) else null)

            if (creditColIndex != -1 && debitColIndex != -1 && creditColIndex < row.size && debitColIndex < row.size) {
                parsedCredit = parseDoubleAmount(row[creditColIndex])
                parsedDebit = parseDoubleAmount(row[debitColIndex])

                if (parsedCredit > 0 && parsedDebit == 0.0) {
                    isCredit = true
                } else if (parsedDebit > 0 && parsedCredit == 0.0) {
                    isDebit = true
                } else if (parsedCredit > 0 && parsedDebit > 0) {
                    if (bal != null && prevBalance != null) {
                        if (Math.abs((prevBalance + parsedCredit) - bal) < 0.05) isCredit = true
                        else if (Math.abs((prevBalance - parsedDebit) - bal) < 0.05) isDebit = true
                        else if (parsedCredit >= parsedDebit) isCredit = true else isDebit = true
                    } else {
                        if (lowerNarration.contains("cr") || lowerNarration.contains("deposit") || lowerNarration.contains("salary")) isCredit = true
                        else isDebit = true
                    }
                }
            } else if (amountColIndex != -1 && amountColIndex < row.size) {
                val amtCell = row[amountColIndex].uppercase()
                val amt = parseDoubleAmount(amtCell)
                val typeStr = if (typeColIndex != -1 && typeColIndex < row.size) row[typeColIndex].uppercase() else ""

                if (amtCell.contains("CR") || typeStr.contains("CR") || typeStr.contains("DEPOSIT") || typeStr.contains("CREDIT") || typeStr.contains("REC")) {
                    isCredit = true
                    parsedCredit = Math.abs(amt)
                } else if (amtCell.contains("DR") || typeStr.contains("DR") || typeStr.contains("WITHDRAW") || typeStr.contains("DEBIT") || typeStr.contains("PAY") || amt < 0) {
                    isDebit = true
                    parsedDebit = Math.abs(amt)
                } else if (bal != null && prevBalance != null && bal != prevBalance && Math.abs((prevBalance + amt) - bal) < 0.05) {
                    // Exact mathematical proof: balance increased by transaction amount -> Credit
                    isCredit = true
                    parsedCredit = amt
                } else if (bal != null && prevBalance != null && bal != prevBalance && Math.abs((prevBalance - amt) - bal) < 0.05) {
                    // Exact mathematical proof: balance decreased by transaction amount -> Debit
                    isDebit = true
                    parsedDebit = amt
                } else if (bal != null && prevBalance != null && bal != prevBalance && Math.abs((bal + amt) - prevBalance) < 0.05) {
                    // Reverse chronological proof: newer balance + amt = older balance -> Debit
                    isDebit = true
                    parsedDebit = amt
                } else if (bal != null && prevBalance != null && bal != prevBalance && Math.abs((bal - amt) - prevBalance) < 0.05) {
                    // Reverse chronological proof: newer balance - amt = older balance -> Credit
                    isCredit = true
                    parsedCredit = amt
                } else if (bal != null && prevBalance != null && bal != prevBalance) {
                    if (bal > prevBalance) {
                        isCredit = true
                        parsedCredit = amt
                    } else {
                        isDebit = true
                        parsedDebit = amt
                    }
                } else if (lowerNarration.contains("by ") || lowerNarration.matches(Regex(".*\\b(cr|salary|refund|interest|dividend|deposit|received|receipt)\\b.*"))) {
                    isCredit = true
                    parsedCredit = amt
                } else {
                    isDebit = true
                    parsedDebit = amt
                }

                if (bal != null && bal > 0.0) {
                    prevBalance = bal
                }
            } else {
                // Take candidate amounts excluding the balance column
                val maxColsToInspect = if (balanceColIndex != -1) balanceColIndex else (if (row.size >= 4) row.size - 1 else row.size)
                val candidateAmounts = row.take(maxColsToInspect).drop(2).map { parseDoubleAmount(it) to it.uppercase() }.filter { it.first > 0 }
                if (candidateAmounts.isNotEmpty()) {
                    val (txnAmt, rawCell) = candidateAmounts.first()
                    if (rawCell.contains("CR") || lowerNarration.contains("by ") || lowerNarration.matches(Regex(".*\\b(cr|salary|refund|interest|dividend|deposit|received)\\b.*"))) {
                        isCredit = true
                        parsedCredit = txnAmt
                    } else if (rawCell.contains("DR") || lowerNarration.contains("to ") || lowerNarration.matches(Regex(".*\\b(dr|paid|spent|atm|pos|withdrawal)\\b.*"))) {
                        isDebit = true
                        parsedDebit = txnAmt
                    } else {
                        isDebit = true
                        parsedDebit = txnAmt
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

    internal fun extractPartyName(narration: String): String {
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

    internal fun parseDoubleAmount(str: String): Double {
        if (str.isBlank()) return 0.0
        val clean = str.replace(",", "")
            .replace("₹", "")
            .replace("$", "")
            .replace(Regex("(?i)rs\\.?|inr|usd|eur|gbp"), "")
            .replace(Regex("[^0-9.\\-+]"), "")
            .trim()
        return clean.toDoubleOrNull() ?: 0.0
    }

    private fun getFileName(uri: Uri): String {
        return UriResolver.getDisplayName(context, uri, "Bank_Statement.xlsx")
    }
}
