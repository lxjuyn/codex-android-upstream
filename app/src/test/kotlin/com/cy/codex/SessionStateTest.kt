package com.cy.codex

import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import org.junit.Test
import kotlin.test.assertEquals

class SessionStateTest {

    @Test
    fun `an interrupted turn closes still-running tool items`() {
        val state = SessionState()
        state.upsert(CommandExecutionItem("c", "ls", "/tmp"))
        state.upsert(McpToolCallItem("m", "srv", "tool"))
        state.failInProgressItems()
        assertEquals(CommandExecutionStatus.Failed, (state.item("c") as CommandExecutionItem).status)
        assertEquals(McpToolCallStatus.Failed, (state.item("m") as McpToolCallItem).status)
        // The failed item keeps its identity, so a late completion can still replace it.
        assertEquals("c", state.item("c")?.id)
    }

    @Test
    fun `a turn separator is appended once`() {
        val state = SessionState()
        state.appendTurnSeparator(TurnSeparatorItem("turn-separator-t", "Worked for 2m"))
        state.appendTurnSeparator(TurnSeparatorItem("turn-separator-t", "Worked for 2m"))
        assertEquals(1, state.items.size)
    }
}
