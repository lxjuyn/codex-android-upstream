package com.cy.codex.bottom_pane.chat_composer
import org.junit.Test
import kotlin.test.assertEquals

class HistorySearchTest {
    private val history = listOf("third ask", "second one", "first one")

    @Test
    fun `matching is case insensitive and keeps newest-first order`() {
        assertEquals(listOf(1, 2), historySearchMatches(history, "one"))
        assertEquals(listOf(2), historySearchMatches(history, "FIRST"))
        assertEquals(emptyList(), historySearchMatches(history, "absent"))
    }

    @Test
    fun `older walks toward the start and keeps the oldest match at the boundary`() {
        val matches = historySearchMatches(history, "one")
        val newest = matches.first()
        val older = nextHistoryMatch(matches, newest, older = true)
        assertEquals(2, older)
        assertEquals(2, nextHistoryMatch(matches, older, older = true))
    }

    @Test
    fun `newer walks back and keeps the newest match at the boundary`() {
        val matches = historySearchMatches(history, "one")
        assertEquals(1, nextHistoryMatch(matches, 2, older = false))
        assertEquals(1, nextHistoryMatch(matches, 1, older = false))
    }

    @Test
    fun `a stale index restarts from the nearest end`() {
        val matches = historySearchMatches(history, "one")
        assertEquals(1, nextHistoryMatch(matches, -1, older = true))
        assertEquals(2, nextHistoryMatch(matches, -1, older = false))
    }

    @Test
    fun `no matches yields the sentinel`() {
        assertEquals(-1, nextHistoryMatch(emptyList(), -1, older = true))
    }
}
