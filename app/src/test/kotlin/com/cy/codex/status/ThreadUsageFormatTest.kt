package com.cy.codex.status

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadUsageFormatTest {
    @Test
    fun `credits compact at the largest unit with one decimal`() {
        assertEquals("0", formatCreditMicros(0))
        assertEquals("0.5", formatCreditMicros(499_999))
        assertEquals("0.5", formatCreditMicros(500_000))
        assertEquals("1", formatCreditMicros(1_000_000))
        assertEquals("1.5", formatCreditMicros(1_500_000))
        assertEquals("1.3K", formatCreditMicros(1_250_000_000))
        assertEquals("3M", formatCreditMicros(3_000_000_000_000))
    }

    @Test
    fun `usd keeps two extra digits under a dollar`() {
        assertEquals("~$0.000042", formatEstimatedUsdMicros(42))
        assertEquals("~$0.0050", formatEstimatedUsdMicros(5_000))
        assertEquals("~$1.00", formatEstimatedUsdMicros(1_000_000))
        assertEquals("~$12.35", formatEstimatedUsdMicros(12_345_678))
        assertNull(formatEstimatedUsdMicros(null))
        assertNull(formatEstimatedUsdMicros(-1))
    }
}
