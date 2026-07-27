package com.HrshD1eux.DocLite.bankstatement.model

data class BankTransaction(
    val id: String,
    val date: String,
    val partyName: String,
    val narration: String,
    val amount: Double,
    val isCredit: Boolean
)

data class PartySummary(
    val partyName: String,
    val totalAmount: Double,
    val transactionCount: Int
)

data class StatementAnalysisResult(
    val fileName: String,
    val totalCreditAmount: Double,
    val totalDebitAmount: Double,
    val totalCreditCount: Int,
    val totalDebitCount: Int,
    val topDebitRecipients: List<PartySummary>,
    val topCreditSenders: List<PartySummary>,
    val rawTransactions: List<BankTransaction>
) {
    val netBalance: Double
        get() = totalCreditAmount - totalDebitAmount

    val totalTransactionCount: Int
        get() = totalCreditCount + totalDebitCount
}

