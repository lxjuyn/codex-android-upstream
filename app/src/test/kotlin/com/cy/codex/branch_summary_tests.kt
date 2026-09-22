package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BranchSummaryTest {

    @Test
    fun `numstat totals skip binary rows`() {
        assertEquals(
            12 to 4,
            parseNumstat("10\t3\tsrc/a.kt\n2\t1\tsrc/b.kt\n-\t-\tassets/icon.png\n"),
        )
        assertEquals(0 to 0, parseNumstat(""))
    }

    @Test
    fun `only an open pull request is reported`() {
        assertEquals(7, parseGhPrJson("""{"number":7,"url":"https://x/pr/7","state":"OPEN"}""")?.number)
        assertNull(parseGhPrJson("""{"number":7,"url":"https://x/pr/7","state":"MERGED"}"""))
        assertNull(parseGhPrJson("no pull requests found"))
        assertNull(parseGhPrJson("""{"state":"OPEN"}"""))
    }
}
