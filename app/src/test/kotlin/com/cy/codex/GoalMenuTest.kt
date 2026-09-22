package com.cy.codex

import com.cy.codex.chatwidget.editedGoalStatus
import com.cy.codex.protocol.protocol.v2.GoalStatus
import org.junit.Test
import kotlin.test.assertEquals

class GoalMenuTest {

    @Test
    fun `editing keeps live states and restarts terminal ones`() {
        assertEquals(GoalStatus.Paused, editedGoalStatus(GoalStatus.Paused))
        assertEquals(GoalStatus.Blocked, editedGoalStatus(GoalStatus.Blocked))
        assertEquals(GoalStatus.UsageLimited, editedGoalStatus(GoalStatus.UsageLimited))
        assertEquals(GoalStatus.Active, editedGoalStatus(GoalStatus.Active))
        assertEquals(GoalStatus.Active, editedGoalStatus(GoalStatus.BudgetLimited))
        assertEquals(GoalStatus.Active, editedGoalStatus(GoalStatus.Complete))
    }
}
