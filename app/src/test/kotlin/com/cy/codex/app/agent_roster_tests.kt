package com.cy.codex.app

import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.CollabAgentState
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The roster is folded out of the transcript because the app-server has no "agents" endpoint:
 * subagents only exist as collaboration items inside the parent thread. These tests pin that fold,
 * including the cases the protocol actually produces (a later `wait` with no prompt, an activity
 * item that arrives before the collab item, a thread id that is the main thread).
 */
class AgentRosterTest {

    private val main = "th_main"
    private val sub = "th_sub_1"

    /** The wording the UI resolves from resources and hands to the fold. */
    private val MAIN_LABEL = "主代理"
    private val SUB_FORMAT = "子代理 · %s"

    private fun spawn(
        id: String,
        receivers: List<String>,
        prompt: String? = null,
        states: Map<String, CollabAgentState> = emptyMap(),
        model: String? = null,
    ) = CollabAgentToolCallItem(
        id = id,
        tool = CollabAgentTool.SpawnAgent,
        status = CollabAgentToolCallStatus.Completed,
        senderThreadId = main,
        receiverThreadIds = receivers,
        prompt = prompt,
        model = model,
        reasoningEffort = ReasoningEffort.Medium,
        agentsStates = states,
    )

    @Test
    fun mainAgentAlwaysComesFirst() {
        val roster = deriveAgentRoster(emptyList(), main, MAIN_LABEL, SUB_FORMAT)
        assertEquals(1, roster.size)
        assertEquals(main, roster[0].threadId)
        assertEquals(AgentRole.Main, roster[0].role)
        assertEquals("主代理", roster[0].name)
        assertNull(roster[0].status)
    }

    @Test
    fun subagentsAppearInFirstAppearanceOrder() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub)),
                spawn("c2", listOf("th_sub_2")),
                spawn("c3", listOf(sub, "th_sub_2")),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub, "th_sub_2"), roster.map { it.threadId })
        assertEquals(listOf(AgentRole.Main, AgentRole.Sub, AgentRole.Sub), roster.map { it.role })
    }

    @Test
    fun activityItemNamesTheAgentFromItsPath() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub)),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root/status_panel_animation"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals("子代理 · status_panel_animation", entry.name)
        assertEquals(SubAgentActivityKind.Started, entry.activity)
    }

    @Test
    fun withoutAPathTheNameFallsBackToTheThreadSuffix() {
        val roster = deriveAgentRoster(listOf(spawn("c1", listOf("th_abcdef"))), main, MAIN_LABEL, SUB_FORMAT)
        // The last four characters are enough to tell two spawned agents apart in the picker.
        assertEquals("子代理 · cdef", roster.first { it.threadId == "th_abcdef" }.name)
    }

    @Test
    fun latestStatusAndActivityWin() {
        val roster = deriveAgentRoster(
            listOf(
                spawn(
                    "c1",
                    listOf(sub),
                    states = mapOf(sub to CollabAgentState(AgentRunStatus.Running)),
                ),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root/sub"),
                spawn(
                    "c2",
                    listOf(sub),
                    states = mapOf(
                        sub to CollabAgentState(AgentRunStatus.Completed, "已提交 42 行改动"),
                    ),
                ),
                SubAgentActivityItem("a2", SubAgentActivityKind.Completed, sub, "/root/sub"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals(AgentRunStatus.Completed, entry.status)
        assertEquals(SubAgentActivityKind.Completed, entry.activity)
        // The message that came with the state is not carried on the roster entry: it is
        // transient progress text, and the entry is a snapshot the surfaces re-derive.
    }

    @Test
    fun aLaterInteractionWithoutAPromptKeepsTheSpawnPrompt() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub), prompt = "把状态悬浮窗的展开动效对齐侧栏", model = "gpt-5.1-codex-mini"),
                CollabAgentToolCallItem(
                    id = "c2",
                    tool = CollabAgentTool.Wait,
                    status = CollabAgentToolCallStatus.InProgress,
                    senderThreadId = main,
                    receiverThreadIds = listOf(sub),
                    prompt = null,
                ),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals("把状态悬浮窗的展开动效对齐侧栏", entry.task)
        assertEquals("gpt-5.1-codex-mini", entry.model)
        assertEquals(ReasoningEffort.Medium, entry.effort)
    }

    @Test
    fun theMainThreadIsNeverAddedTwice() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(main, sub)),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, main, "/root/self"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub), roster.map { it.threadId })
    }

    @Test
    fun nonCollaborationItemsAreIgnored() {
        val roster = deriveAgentRoster(
            listOf(
                UserMessageItem("u1", content = listOf(UserInput.Text("hi"))),
                AgentMessageItem("m1", "hello"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(1, roster.size)
        assertTrue(roster.all { it.role == AgentRole.Main })
    }

    @Test
    fun tokensAreNotInvented() {
        // The item stream carries no per-agent usage, so the fold must not make numbers up.
        val roster = deriveAgentRoster(listOf(spawn("c1", listOf(sub))), main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertTrue(roster.all { it.tokens == 0 })
    }
}
