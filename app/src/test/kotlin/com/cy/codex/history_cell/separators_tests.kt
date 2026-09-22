package com.cy.codex.history_cell
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeparatorsTest {

    private val now = Instant.parse("2026-09-18T15:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")

    @Test
    fun `short turns show only the completion time`() {
        val completed = Instant.parse("2026-09-18T14:32:00Z").toEpochMilli()
        assertEquals(
            "2:32 PM",
            finalMessageSeparatorLabel(30, completed, now, zone),
        )
    }

    @Test
    fun `turns over a minute show the duration first`() {
        val completed = Instant.parse("2026-09-10T09:05:00Z").toEpochMilli()
        assertEquals(
            "Worked for 2m 5s · Sep 10 at 9:05 AM",
            finalMessageSeparatorLabel(125, completed, now, zone),
        )
    }

    @Test
    fun `other years carry the year and absent metadata has no label`() {
        val completed = Instant.parse("2024-01-02T23:59:00Z").toEpochMilli()
        assertEquals(
            "Jan 2, 2024 at 11:59 PM",
            finalMessageSeparatorLabel(null, completed, now, zone),
        )
        assertNull(finalMessageSeparatorLabel(null, null, now, zone))
        assertNull(finalMessageSeparatorLabel(59, null, now, zone))
        assertNull(finalMessageSeparatorLabel(60, null, now, zone))
        assertEquals("Worked for 1m 1s", finalMessageSeparatorLabel(61, null, now, zone))
    }

    @Test
    fun `transcript separators land after finished turns only`() {
        val first = Turn(
            id = "t1",
            items = listOf(
                UserMessageItem("u1", content = listOf(UserInput.Text("hi"))),
                AgentMessageItem("a1", "there"),
            ),
            status = TurnStatus.Completed,
            completedAt = Instant.parse("2026-09-18T14:32:00Z").toEpochMilli(),
        )
        val running = Turn(
            id = "t2",
            items = listOf(UserMessageItem("u2", content = listOf(UserInput.Text("again")))),
            status = TurnStatus.InProgress,
        )
        val items = transcriptWithSeparators(listOf(first, running))
        assertEquals(4, items.size)
        assertTrue(items[2] is TurnSeparatorItem)
        assertEquals("turn-separator-t1", items[2].id)
        assertEquals("u2", (items.last() as UserMessageItem).id)
    }
}
