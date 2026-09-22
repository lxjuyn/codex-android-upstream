package com.cy.codex.app
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the automatic recap schedule against `tui/src/app/recap.rs`: three completed turns, two
 * turns between recaps, and a 30 minute idle window measured from the later of focus loss and the
 * last finished turn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecapSchedulerTest {
    private fun completed(id: String) = Turn(id, status = TurnStatus.Completed)

    @Test
    fun `deadline is the later of focus loss and the last finished turn plus 30 minutes`() {
        val scheduler = RecapScheduler()
        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 10_000) }
        scheduler.noteFocusLost(5_000)

        assertEquals(10_000 + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())
        assertFalse(scheduler.shouldGenerate(10_000 + RecapScheduler.RecapDelayMs - 1))
        assertTrue(scheduler.shouldGenerate(10_000 + RecapScheduler.RecapDelayMs))
    }

    @Test
    fun `fewer than three completed turns never become due`() {
        val scheduler = RecapScheduler()
        scheduler.noteTurnFinished(TurnStatus.Completed, 0)
        scheduler.noteTurnFinished(TurnStatus.Completed, 0)
        scheduler.noteFocusLost(0)

        assertNull(scheduler.nextCheckDeadlineMs())
        assertFalse(scheduler.shouldGenerate(Long.MAX_VALUE))
    }

    @Test
    fun `failed and interrupted turns do not count but do refresh the idle clock`() {
        val scheduler = RecapScheduler()
        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 0) }
        scheduler.noteFocusLost(0)
        assertEquals(0 + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())

        scheduler.noteTurnFinished(TurnStatus.Failed, 60_000)
        assertEquals(3, scheduler.completedTurns)
        assertEquals(60_000 + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())
    }

    @Test
    fun `two completed turns must pass between recaps`() {
        val scheduler = RecapScheduler()
        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 0) }
        scheduler.noteFocusLost(0)
        scheduler.markRecapped(3)

        scheduler.noteTurnFinished(TurnStatus.Completed, 1_000)
        assertNull(scheduler.nextCheckDeadlineMs(), "one turn since the last recap is not enough")

        scheduler.noteTurnFinished(TurnStatus.Completed, 2_000)
        assertEquals(2_000 + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())
    }

    @Test
    fun `an armed check fires at the deadline and focus gained cancels it`() = runTest {
        val scheduler = RecapScheduler()
        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 0) }
        scheduler.noteFocusLost(0)
        var fired = 0
        scheduler.scheduleCheck(backgroundScope, "thread", nowMs = 0, enabled = true) { fired++ }

        advanceTimeBy(RecapScheduler.RecapDelayMs - 1)
        runCurrent()
        assertEquals(0, fired)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, fired)

        scheduler.scheduleCheck(backgroundScope, "thread", nowMs = 0, enabled = true) { fired++ }
        scheduler.noteFocusGained()
        advanceTimeBy(RecapScheduler.RecapDelayMs + 1)
        runCurrent()
        assertEquals(1, fired)
        assertNull(scheduler.nextCheckDeadlineMs())
    }

    @Test
    fun `a disabled setting leaves the timer unarmed`() = runTest {
        val scheduler = RecapScheduler()
        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 0) }
        scheduler.noteFocusLost(0)
        var fired = 0
        scheduler.scheduleCheck(backgroundScope, "thread", nowMs = 0, enabled = false) { fired++ }

        advanceTimeBy(RecapScheduler.RecapDelayMs + 1)
        runCurrent()
        assertEquals(0, fired)
    }

    @Test
    fun `an automatic failure retries once per turn revision`() = runTest {
        val scheduler = RecapScheduler()
        scheduler.noteTurnFinished(TurnStatus.Completed, 0)
        var retries = 0
        scheduler.scheduleRetry(backgroundScope, "thread", revision = scheduler.turnRevision) { retries++ }
        scheduler.scheduleRetry(backgroundScope, "thread", revision = scheduler.turnRevision) { retries++ }

        advanceTimeBy(RecapScheduler.RecapRetryDelayMs + 1)
        runCurrent()
        assertEquals(1, retries)

        scheduler.noteTurnFinished(TurnStatus.Completed, 1_000)
        scheduler.scheduleRetry(backgroundScope, "thread", revision = scheduler.turnRevision) { retries++ }
        advanceTimeBy(RecapScheduler.RecapRetryDelayMs + 1)
        runCurrent()
        assertEquals(2, retries)
    }

    @Test
    fun `seeding restores the completed count but not the recap cooldown`() {
        val scheduler = RecapScheduler()
        scheduler.noteFocusLost(0)
        scheduler.seedFromTurns(listOf(completed("a"), completed("b"), completed("c")), 500)

        assertEquals(3, scheduler.completedTurns)
        // `lastRecappedTurnCount` starts null after seeding, so the three seeded turns are eligible
        // once the idle window measured from the seed time passes.
        assertEquals(500 + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())
    }

    @Test
    fun `reset for a new thread preserves the focus clock`() {
        val scheduler = RecapScheduler()
        scheduler.noteFocusLost(1_000)
        repeat(4) { scheduler.noteTurnFinished(TurnStatus.Completed, 2_000) }
        scheduler.markRecapped(4)

        scheduler.resetForNewThread()
        assertEquals(0, scheduler.completedTurns)
        assertNull(scheduler.nextCheckDeadlineMs())

        repeat(3) { scheduler.noteTurnFinished(TurnStatus.Completed, 5_000) }
        assertEquals(maxOf(1_000, 5_000) + RecapScheduler.RecapDelayMs, scheduler.nextCheckDeadlineMs())
    }
}
