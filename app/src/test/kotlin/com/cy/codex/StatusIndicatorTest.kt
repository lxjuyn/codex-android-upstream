package com.cy.codex

import com.cy.codex.bottom_pane.activeToolDetail
import com.cy.codex.bottom_pane.formatElapsedCompact
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusIndicatorTest {

    @Test
    fun `compact elapsed matches the upstream boundaries`() {
        assertEquals("0s", formatElapsedCompact(0))
        assertEquals("59s", formatElapsedCompact(59))
        assertEquals("1m 00s", formatElapsedCompact(60))
        assertEquals("1m 05s", formatElapsedCompact(65))
        assertEquals("59m 59s", formatElapsedCompact(3599))
        assertEquals("1h 00m 00s", formatElapsedCompact(3600))
        assertEquals("2h 03m 04s", formatElapsedCompact(7384))
    }

    @Test
    fun `active detail names the last unfinished tool and nothing else`() {
        val items = listOf(
            CommandExecutionItem("c", "ls -la", "/tmp", status = CommandExecutionStatus.Completed),
            McpToolCallItem("m", "srv", "search"),
            AgentMessageItem("a", "thinking"),
        )
        assertEquals("srv.search", activeToolDetail(items))
        assertNull(activeToolDetail(listOf(AgentMessageItem("a", "thinking"))))
    }
}
