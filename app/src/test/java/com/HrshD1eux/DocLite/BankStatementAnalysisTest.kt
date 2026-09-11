package com.HrshD1eux.DocLite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.HrshD1eux.DocLite.bankstatement.parser.BankStatementParseException
import com.HrshD1eux.DocLite.bankstatement.parser.BankStatementParser
import com.HrshD1eux.DocLite.bankstatement.parser.PasswordRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankStatementAnalysisTest {

    private lateinit var parser: BankStatementParser

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        parser = BankStatementParser(context)
    }

    @Test
    fun extractPartyName_stripsUpiPrefixAndIdentifiers() {
        val input = "UPI/428938291048/AMAZON SELLER SERVICES/PAYMENT"
        val clean = parser.extractPartyName(input)
        assertEquals("AMAZON SELLER SERVICES", clean)
    }

    @Test
    fun extractPartyName_stripsNeftImpsPrefix() {
        val input = "NEFT/HDFC0000123/SWIGGY DELIVERIES/BANGALORE"
        val clean = parser.extractPartyName(input)
        assertEquals("SWIGGY DELIVERIES", clean)
    }

    @Test
    fun extractPartyName_cleansAtmCashWithdrawals() {
        val input = "ATM WDR CASH WITHDRAWAL MUMBAI"
        val clean = parser.extractPartyName(input)
        assertTrue(clean.contains("CASH WITHDRAWAL MUMBAI"))
    }

    @Test
    fun parseDoubleAmount_handlesCurrencySymbolsAndCommas() {
        assertEquals(12345.67, parser.parseDoubleAmount("₹12,345.67"), 0.001)
        assertEquals(5000.0, parser.parseDoubleAmount("Rs. 5,000.00"), 0.001)
        assertEquals(99.99, parser.parseDoubleAmount("$99.99"), 0.001)
        assertEquals(0.0, parser.parseDoubleAmount(""), 0.001)
    }

    @Test
    fun processTransactionRows_parsesCreditAndDebitAccurately() {
        val rows = listOf(
            listOf("Account Statement Summary", "", "", ""),
            listOf("Date", "Narration", "Withdrawal (Debit)", "Deposit (Credit)", "Balance"),
            listOf("01/01/2025", "UPI/1111/UBER RIDES", "450.00", "", "9550.00"),
            listOf("02/01/2025", "SALARY CREDITED BY EMPLOYER", "", "50000.00", "59550.00"),
            listOf("03/01/2025", "UPI/2222/SWIGGY", "320.00", "", "59230.00"),
            listOf("04/01/2025", "UPI/3333/UBER RIDES", "250.00", "", "58980.00")
        )

        val result = parser.processTransactionRows("test_statement.xlsx", rows)

        assertEquals("test_statement.xlsx", result.fileName)
        assertEquals(50000.0, result.totalCreditAmount, 0.001)
        assertEquals(1020.0, result.totalDebitAmount, 0.001) // 450 + 320 + 250
        assertEquals(48980.0, result.netBalance, 0.001)
        assertEquals(1, result.totalCreditCount)
        assertEquals(3, result.totalDebitCount)
        assertEquals(4, result.totalTransactionCount)

        // Verify top debit recipient grouping
        val topRecipient = result.topDebitRecipients.firstOrNull()
        assertNotNull(topRecipient)
        assertEquals("UBER RIDES", topRecipient!!.partyName)
        assertEquals(700.0, topRecipient.totalAmount, 0.001) // 450 + 250
        assertEquals(2, topRecipient.transactionCount)
    }

    @Test
    fun processTransactionRows_heuristicFallback_parsesStatementsWithoutExplicitHeader() {
        val rows = listOf(
            listOf("Statement Header Information"),
            listOf("01/01/2025", "UPI/999/GROCERIES", "1500.00", "0.00"),
            listOf("02/01/2025", "FREELANCE INCOME", "0.00", "8000.00")
        )

        val result = parser.processTransactionRows("fallback.csv", rows)
        assertEquals(8000.0, result.totalCreditAmount, 0.001)
        assertEquals(1500.0, result.totalDebitAmount, 0.001)
        assertEquals(2, result.totalTransactionCount)
    }

    @Test
    fun passwordRequiredException_hasMeaningfulMessage() {
        val ex = PasswordRequiredException("This PDF statement is password-protected. Please enter password.")
        assertTrue(ex.message!!.contains("password-protected"))
    }
}
