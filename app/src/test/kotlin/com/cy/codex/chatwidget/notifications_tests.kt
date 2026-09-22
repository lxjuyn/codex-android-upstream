package com.cy.codex.chatwidget

import org.junit.Test
import kotlin.test.assertEquals

class NotificationsTest {
    @Test
    fun `wire names match the TUI notification whitelist`() {
        assertEquals("agent-turn-complete", AgentNotification.TurnComplete.wire)
        assertEquals("approval-requested", AgentNotification.ApprovalRequested.wire)
    }
}
