package com.cy.codex.app

import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.CollabAgentState
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.ThreadStatusTone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The subagent page has nothing of its own to read: a subagent is not a thread on the server, so its
 * page is folded back out of the *parent* transcript. These tests pin that fold — which items count
 * as "about this agent", and that the fold is total for an agent nothing mentions.
 */
class SubAgentTimelineTest {

    private val main = "th_main"
    private val sub = "th_sub_1"
    private val other = "th_sub_2"

    private fun spawn(
        id: String,
        receivers: List<String>,
        prompt: String? = null,
        states: Map<String, CollabAgentState> = emptyMap(),
    ) = CollabAgentToolCallItem(
        id = id,
        tool = CollabAgentTool.SpawnAgent,
        status = CollabAgentToolCallStatus.Completed,
        senderThreadId = main,
        receiverThreadIds = receivers,
        prompt = prompt,
        agentsStates = states,
    )

    @Test
    fun onlyItemsThatNameTheAgentAreIncluded() {
        val items: List<ThreadItem> = listOf(
            AgentMessageItem("m1", "unrelated"),
            spawn("c1", listOf(sub), prompt = "把动效对齐侧栏"),
            spawn("c2", listOf(other), prompt = "另一个任务"),
            SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root/anim"),
            SubAgentActivityItem("a2", SubAgentActivityKind.Started, other, "/root/other"),
        )
        val events = deriveSubAgentTimeline(items, sub)
        assertEquals(listOf("c1", "a1"), events.map { it.id })
        // The fold carries the enum, not the wording: the row's title is resolved where string
        // resources exist, so the test pins what the fold is actually responsible for.
        assertEquals(CollabAgentTool.SpawnAgent, events[0].tool)
        assertEquals("把动效对齐侧栏", events[0].detail)
        assertEquals(SubAgentActivityKind.Started, events[1].activity)
        assertEquals("/root/anim", events[1].detail)
    }

    @Test
    fun theServersOwnStateBeatsThePrompt() {
        // A later collab update carries the agent's last reported message; that is newer evidence
        // than the prompt it was spawned with, so it is what the row shows.
        val items = listOf(
            spawn(
                "c1",
                listOf(sub),
                prompt = "把动效对齐侧栏",
                states = mapOf(sub to CollabAgentState(AgentRunStatus.Completed, "已提交 42 行改动")),
            ),
        )
        assertEquals("已提交 42 行改动", deriveSubAgentTimeline(items, sub).single().detail)
        assertEquals(ThreadStatusTone.Done, deriveSubAgentTimeline(items, sub).single().tone)
    }

    @Test
    fun anUnknownAgentFoldsToNothing() {
        val items = listOf(spawn("c1", listOf(sub)), SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root"))
        assertTrue(deriveSubAgentTimeline(items, "th_nobody").isEmpty())
        assertTrue(deriveSubAgentTimeline(emptyList(), sub).isEmpty())
    }

    @Test
    fun anAgentMentionedOnlyByItsStateIsStillFound() {
        // `agentsStates` can name an agent the receiver list does not (a resumed agent), and the
        // page must still show it rather than claim the transcript never mentioned it.
        val items = listOf(spawn("c1", listOf(other), states = mapOf(sub to CollabAgentState(AgentRunStatus.Running))))
        assertEquals(listOf("c1"), deriveSubAgentTimeline(items, sub).map { it.id })
    }
}
