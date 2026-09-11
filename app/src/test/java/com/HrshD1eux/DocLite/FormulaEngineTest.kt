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
}
