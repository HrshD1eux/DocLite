package com.HrshD1eux.DocLite

import com.HrshD1eux.DocLite.models.Cell
import com.HrshD1eux.DocLite.models.Sheet
import com.HrshD1eux.DocLite.office.excel.FormulaEngine
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormulaEngineTest {

    private lateinit var formulaEngine: FormulaEngine
    private lateinit var testSheet: Sheet

    @Before
    fun setUp() {
        formulaEngine = FormulaEngine()
        val cells = mutableMapOf<String, Cell>()

        // A1 = 10, A2 = 20, A3 = 30
        cells[Sheet.getCellKey(0, 0)] = Cell(row = 0, col = 0, value = "10")
        cells[Sheet.getCellKey(1, 0)] = Cell(row = 1, col = 0, value = "20")
        cells[Sheet.getCellKey(2, 0)] = Cell(row = 2, col = 0, value = "30")

        // B1 = 5, B2 = 0
        cells[Sheet.getCellKey(0, 1)] = Cell(row = 0, col = 1, value = "5")
        cells[Sheet.getCellKey(1, 1)] = Cell(row = 1, col = 1, value = "0")

        // A10 = 100 (row 9, col 0) to test token replacement without collision with A1
        cells[Sheet.getCellKey(9, 0)] = Cell(row = 9, col = 0, value = "100")

        testSheet = Sheet(name = "TestSheet", cells = cells)
    }

    @Test
    fun evaluateSum_withRange_computesCorrectSum() {
        val result = formulaEngine.evaluateFormula("=SUM(A1:A3)", testSheet)
        assertEquals("60", result)
    }

    @Test
    fun evaluateSum_withCommaSeparatedCells_computesCorrectSum() {
        val result = formulaEngine.evaluateFormula("=SUM(A1,A3)", testSheet)
        assertEquals("40", result)
    }

    @Test
    fun evaluateAverage_withRange_computesCorrectAverage() {
        val result = formulaEngine.evaluateFormula("=AVERAGE(A1:A3)", testSheet)
        assertEquals("20", result)
    }

    @Test
    fun evaluateMinAndMax_withRange_computesCorrectValues() {
        val minResult = formulaEngine.evaluateFormula("=MIN(A1:A3)", testSheet)
        val maxResult = formulaEngine.evaluateFormula("=MAX(A1:A3)", testSheet)
        assertEquals("10", minResult)
        assertEquals("30", maxResult)
    }

    @Test
    fun evaluateCount_withRange_returnsCellCount() {
        val countResult = formulaEngine.evaluateFormula("=COUNT(A1:A3)", testSheet)
        assertEquals("3", countResult)
    }

    @Test
    fun evaluateSimpleMath_additionAndSubtraction_computesCorrectly() {
        val addResult = formulaEngine.evaluateFormula("=A1+B1", testSheet)
        val subResult = formulaEngine.evaluateFormula("=A1-B1", testSheet)
        assertEquals("15", addResult)
        assertEquals("5", subResult)
    }

    @Test
    fun evaluateSimpleMath_multiplicationAndDivision_computesCorrectly() {
        val mulResult = formulaEngine.evaluateFormula("=A1*B1", testSheet)
        val divResult = formulaEngine.evaluateFormula("=A1/B1", testSheet)
        assertEquals("50", mulResult)
        assertEquals("2", divResult)
    }

    @Test
    fun evaluateDivision_byZero_returnsDivZeroError() {
        val divZero = formulaEngine.evaluateFormula("=A1/B2", testSheet)
        assertEquals("#DIV/0!", divZero)
    }

    @Test
    fun evaluateFormula_tokenReplacement_doesNotCorruptA10WithA1() {
        // A1 is 10, A10 is 100. If A1 substring replaced A10, it would produce 10 + 100 corrupted.
        val result = formulaEngine.evaluateFormula("=A1+A10", testSheet)
        assertEquals("110", result)
    }

    @Test
    fun evaluateFormula_caseInsensitive_computesProperly() {
        val result = formulaEngine.evaluateFormula("=sum(a1:a3)", testSheet)
        assertEquals("60", result)
    }

    @Test
    fun evaluateDependentCells_whenSourceCellChanges_recalculationUpdatesDependentValue() {
        // Initially A1=10, A2=20. Dependent cell C1 has formula =A1+A2
        val cells = testSheet.cells.toMutableMap()
        val c1Key = Sheet.getCellKey(0, 2)
        val initialFormula = "=A1+A2"
        cells[c1Key] = Cell(
            row = 0,
            col = 2,
            value = initialFormula,
            formula = initialFormula,
            evaluatedValue = formulaEngine.evaluateFormula(initialFormula, testSheet)
        )
        assertEquals("30", cells[c1Key]?.evaluatedValue)

        // Now simulate editing A1 from 10 to 50
        val a1Key = Sheet.getCellKey(0, 0)
        cells[a1Key] = Cell(row = 0, col = 0, value = "50", evaluatedValue = "50")

        // Recalculate dependent cells with updated sheet
        val updatedSheet = testSheet.copy(cells = cells)
        val reevaluated = formulaEngine.evaluateFormula(cells[c1Key]!!.formula, updatedSheet)
        cells[c1Key] = cells[c1Key]!!.copy(evaluatedValue = reevaluated)

        assertEquals("70", cells[c1Key]?.evaluatedValue)
        assertEquals("70", cells[c1Key]?.displayValue)
    }

    @Test
    fun evaluateFormula_pemdasPrecedence_multipliesBeforeAdding() {
        // A1=10, B1=5. 10 + 5 * 2 = 10 + 10 = 20 (NOT (10 + 5) * 2 = 30)
        val result = formulaEngine.evaluateFormula("=A1 + B1 * 2", testSheet)
        assertEquals("20", result)

        // 2 + 3 * 4 = 14
        val directResult = formulaEngine.evaluateFormula("=2 + 3 * 4", testSheet)
        assertEquals("14", directResult)

        // 100 - 20 / 4 = 100 - 5 = 95
        val divPrecedence = formulaEngine.evaluateFormula("=100 - 20 / 4", testSheet)
        assertEquals("95", divPrecedence)
    }

    @Test
    fun evaluateFormula_parentheses_overridesDefaultPrecedence() {
        // (A1 + B1) * 2 = (10 + 5) * 2 = 30
        val result = formulaEngine.evaluateFormula("=(A1 + B1) * 2", testSheet)
        assertEquals("30", result)

        // (2 + 3) * (4 + 1) = 25
        val complexResult = formulaEngine.evaluateFormula("=(2 + 3) * (4 + 1)", testSheet)
        assertEquals("25", complexResult)
    }

    @Test
    fun evaluateFormula_exponentiation_evaluatesCorrectly() {
        // 2 ^ 3 = 8
        val powResult = formulaEngine.evaluateFormula("=2 ^ 3", testSheet)
        assertEquals("8", powResult)

        // 2 + 3 ^ 2 = 2 + 9 = 11
        val powPrecedence = formulaEngine.evaluateFormula("=2 + 3 ^ 2", testSheet)
        assertEquals("11", powPrecedence)
    }

    @Test
    fun evaluateFormula_unaryMinusAndNegatives_handlesProperly() {
        // -5 + 10 = 5
        val unaryResult = formulaEngine.evaluateFormula("=-5 + 10", testSheet)
        assertEquals("5", unaryResult)

        // A1 * -2 = 10 * -2 = -20
        val mulNegative = formulaEngine.evaluateFormula("=A1 * -2", testSheet)
        assertEquals("-20", mulNegative)
    }

    @Test
    fun evaluateFormula_mixedRangesAndScalars_computesCorrectly() {
        // SUM(A1:A3, B1, 15) = (10 + 20 + 30) + 5 + 15 = 80
        val sumResult = formulaEngine.evaluateFormula("=SUM(A1:A3, B1, 15)", testSheet)
        assertEquals("80", sumResult)

        // AVERAGE(A1:A3, 60) = (10 + 20 + 30 + 60) / 4 = 30
        val avgResult = formulaEngine.evaluateFormula("=AVERAGE(A1:A3, 60)", testSheet)
        assertEquals("30", avgResult)

        // MAX(A1:A3, 100) = 100
        val maxResult = formulaEngine.evaluateFormula("=MAX(A1:A3, 100)", testSheet)
        assertEquals("100", maxResult)

        // MIN(A1:A3, -5) = -5
        val minResult = formulaEngine.evaluateFormula("=MIN(A1:A3, -5)", testSheet)
        assertEquals("-5", minResult)

        // COUNT(A1:A3, B1, B2, 99) = 3 + 1 + 1 + 1 = 6
        val countResult = formulaEngine.evaluateFormula("=COUNT(A1:A3, B1, B2, 99)", testSheet)
        assertEquals("6", countResult)
    }

    @Test
    fun evaluateIf_conditionsAndBranches_evaluatesProperly() {
        // True branch: A1 (10) > 5 -> "Pass"
        val ifTrue = formulaEngine.evaluateFormula("=IF(A1 > 5, \"Pass\", \"Fail\")", testSheet)
        assertEquals("Pass", ifTrue)

        // False branch: A1 (10) < 5 -> "Fail"
        val ifFalse = formulaEngine.evaluateFormula("=IF(A1 < 5, \"Pass\", \"Fail\")", testSheet)
        assertEquals("Fail", ifFalse)

        // Equality comparison
        val ifEq = formulaEngine.evaluateFormula("=IF(A1 = 10, 100, 200)", testSheet)
        assertEquals("100", ifEq)

        // Not equal comparison
        val ifNeq = formulaEngine.evaluateFormula("=IF(A1 <> 10, 100, 200)", testSheet)
        assertEquals("200", ifNeq)

        // 2-argument IF: condition false returns FALSE
        val ifTwoArgsFalse = formulaEngine.evaluateFormula("=IF(A1 < 5, \"Yes\")", testSheet)
        assertEquals("FALSE", ifTwoArgsFalse)
    }

    @Test
    fun evaluateVlookup_validAndMissingLookups_handlesProperly() {
        // A1=10, B1=5; A2=20, B2=0; A3=30
        // Update B3 to 99 for a clean lookup test
        val cells = testSheet.cells.toMutableMap()
        cells[Sheet.getCellKey(2, 1)] = Cell(row = 2, col = 1, value = "99")
        val sheetWithB3 = testSheet.copy(cells = cells)

        // Lookup 20 in col 1, return col 2 -> 0
        val vlookupResult1 = formulaEngine.evaluateFormula("=VLOOKUP(20, A1:B3, 2, FALSE)", sheetWithB3)
        assertEquals("0", vlookupResult1)

        // Lookup 30 in col 1, return col 2 -> 99
        val vlookupResult2 = formulaEngine.evaluateFormula("=VLOOKUP(30, A1:B3, 2, FALSE)", sheetWithB3)
        assertEquals("99", vlookupResult2)

        // Lookup 999 (not found) -> #N/A
        val notFound = formulaEngine.evaluateFormula("=VLOOKUP(999, A1:B3, 2, FALSE)", sheetWithB3)
        assertEquals("#N/A", notFound)

        // Out of range col index -> #REF!
        val invalidCol = formulaEngine.evaluateFormula("=VLOOKUP(20, A1:B3, 5, FALSE)", sheetWithB3)
        assertEquals("#REF!", invalidCol)
    }

    @Test
    fun evaluateIndexAndMatch_lookupCoordinates_returnsTargetValue() {
        // INDEX(A1:B2, 2, 1) -> Row 2, Col 1 -> A2 = 20
        val indexVal = formulaEngine.evaluateFormula("=INDEX(A1:B2, 2, 1)", testSheet)
        assertEquals("20", indexVal)

        // INDEX out of bounds -> #REF!
        val indexOob = formulaEngine.evaluateFormula("=INDEX(A1:B2, 5, 5)", testSheet)
        assertEquals("#REF!", indexOob)

        // MATCH(20, A1:A3) -> Position 2
        val matchVal = formulaEngine.evaluateFormula("=MATCH(20, A1:A3)", testSheet)
        assertEquals("2", matchVal)

        // MATCH not found -> #N/A
        val matchNotFound = formulaEngine.evaluateFormula("=MATCH(999, A1:A3)", testSheet)
        assertEquals("#N/A", matchNotFound)
    }

    @Test
    fun evaluateRound_variousPrecisions_roundsCorrectly() {
        val roundDec = formulaEngine.evaluateFormula("=ROUND(3.14159, 2)", testSheet)
        assertEquals("3.14", roundDec)

        val roundZero = formulaEngine.evaluateFormula("=ROUND(15.6, 0)", testSheet)
        assertEquals("16", roundZero)
    }

    @Test
    fun evaluateConcat_functionAndAmpersand_concatenatesStrings() {
        // Ampersand concatenation
        val ampResult = formulaEngine.evaluateFormula("=\"Total: \" & A1", testSheet)
        assertEquals("Total: 10", ampResult)

        // CONCAT function
        val concatResult = formulaEngine.evaluateFormula("=CONCAT(\"Doc\", \"Lite\", \" \", A1)", testSheet)
        assertEquals("DocLite 10", concatResult)

        // CONCATENATE function alias
        val concatenateResult = formulaEngine.evaluateFormula("=CONCATENATE(\"Hello\", \" \", \"World\")", testSheet)
        assertEquals("Hello World", concatenateResult)
    }

    @Test
    fun evaluateToday_returnsFormattedDate() {
        val todayResult = formulaEngine.evaluateFormula("=TODAY()", testSheet)
        org.junit.Assert.assertTrue(
            "TODAY() must return yyyy-MM-dd format, got: $todayResult",
            todayResult.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
        )
    }

    @Test
    fun evaluateErrors_returnsStandardExcelErrorCodes() {
        // Unknown function
        val unknownFunc = formulaEngine.evaluateFormula("=NONEXISTENT_FUNC(A1)", testSheet)
        assertEquals("#NAME?", unknownFunc)

        // Division by zero
        val divZero = formulaEngine.evaluateFormula("=A1 / 0", testSheet)
        assertEquals("#DIV/0!", divZero)
    }
}

