package com.studypath.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculatorTest {

    @Test
    fun `normal weighted percent`() {
        // 60min*100% + 30min*50% = 7500; total 90min → 83%
        val weighted = 60L * 100 + 30L * 50
        assertEquals(83, ProgressCalculator.percent(weighted, 90))
    }

    @Test
    fun `zero total yields zero`() {
        assertEquals(0, ProgressCalculator.percent(0, 0))
        assertEquals(0, ProgressCalculator.percent(100, 0))
    }

    @Test
    fun `all tasks complete yields 100`() {
        val weighted = 45L * 100 + 45L * 100
        assertEquals(100, ProgressCalculator.percent(weighted, 90))
    }

    @Test
    fun `partial progress never exceeds 100`() {
        assertEquals(100, ProgressCalculator.percent(5000, 10))
    }
}
