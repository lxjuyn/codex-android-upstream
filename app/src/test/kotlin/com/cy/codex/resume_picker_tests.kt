package com.cy.codex

import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.GitInfo
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure half of the resume picker: ordering, local search and preview formatting.
 *
 * Mirrors `ThreadSortKey::UpdatedAt` and `Row::matches_query` from
 * `codex-rs/tui/src/resume_picker.rs`, plus the six-line transcript preview from
 * `resume_picker_transcript_preview.rs`. The composables around these functions only resolve
 * labels and render what lands here.
 */
class ResumePickerTest {

    @Test
    fun `threads are ordered by recency and fall back to updatedAt`() {
        val threads = listOf(
            thread("updated-newer", updatedAt = 300L),
            thread("recency-oldest", updatedAt = 100L, recencyAt = 250L),
            thread("recency-newest", updatedAt = 100L, recencyAt = 400L),
        )
        assertEquals(
            listOf("recency-newest", "updated-newer", "recency-oldest"),
            resumeThreads(threads, "").map { it.id },
        )
    }

    @Test
    fun `search matches name preview id branch and cwd case insensitively`() {
        val row = thread(
            id = "thread-abc123",
            preview = "Ship the parser",
            name = "Release plan",
            branch = "feature/Resume-Picker",
            cwd = "/work/Codex-Android",
        )
        assertTrue(threadMatchesQuery(row, "release"))
        assertTrue(threadMatchesQuery(row, "PARSER"))
        assertTrue(threadMatchesQuery(row, "abc123"))
        assertTrue(threadMatchesQuery(row, "resume-picker"))
        assertTrue(threadMatchesQuery(row, "codex-android"))
        assertTrue(threadMatchesQuery(row, ""))
        assertFalse(threadMatchesQuery(row, "mongodb"))
    }

    @Test
    fun `an empty query keeps every row and a miss drops it`() {
        val threads = listOf(
            thread("alpha", preview = "one"),
            thread("beta", preview = "two"),
        )
        assertEquals(listOf("alpha", "beta"), resumeThreads(threads, "").map { it.id })
        assertEquals(listOf("beta"), resumeThreads(threads, "TWO").map { it.id })
        assertEquals(emptyList(), resumeThreads(threads, "missing").map { it.id })
    }

    @Test
    fun `preview keeps the six newest messages in conversation order`() {
        val items = (1..8).flatMap { index ->
            listOf(
                UserMessageItem(id = "u$index", content = listOf(UserInput.Text("ask $index"))),
                AgentMessageItem(id = "a$index", text = "answer $index"),
            )
        }
        val lines = transcriptPreviewLines(items)
        assertEquals(
            listOf("ask 6", "answer 6", "ask 7", "answer 7", "ask 8", "answer 8"),
            lines.map { it.text },
        )
        assertEquals(
            listOf(
                ResumePreviewSpeaker.User,
                ResumePreviewSpeaker.Assistant,
                ResumePreviewSpeaker.User,
                ResumePreviewSpeaker.Assistant,
                ResumePreviewSpeaker.User,
                ResumePreviewSpeaker.Assistant,
            ),
            lines.map { it.speaker },
        )
    }

    @Test
    fun `preview joins user text parts and reduces each message to its first line`() {
        val items = listOf(
            UserMessageItem(
                id = "u",
                content = listOf(
                    UserInput.Text("first part"),
                    UserInput.Image(url = "https://example.com/a.png"),
                    UserInput.Text("second part"),
                ),
            ),
            AgentMessageItem(id = "a", text = "\n  answer line one  \nanswer line two\n"),
            PlanItem(id = "p", text = "not a message"),
        )
        assertEquals(
            listOf(
                ResumePreviewLine(ResumePreviewSpeaker.User, "first part second part"),
                ResumePreviewLine(ResumePreviewSpeaker.Assistant, "answer line one"),
            ),
            transcriptPreviewLines(items),
        )
    }

    @Test
    fun `preview skips blank messages and honours the limit`() {
        val items = listOf(
            UserMessageItem(id = "blank", content = listOf(UserInput.Text("  "))),
            UserMessageItem(id = "kept", content = listOf(UserInput.Text("kept"))),
            AgentMessageItem(id = "empty", text = ""),
        )
        assertEquals(listOf("kept"), transcriptPreviewLines(items).map { it.text })
        assertEquals(
            emptyList(),
            transcriptPreviewLines(items, limit = 0),
        )
    }

    /** A `thread/list` row with everything the wire always sends filled in. */
    private fun thread(
        id: String,
        preview: String = "",
        name: String? = null,
        branch: String? = null,
        cwd: String = "",
        updatedAt: Long = 0L,
        recencyAt: Long? = null,
    ) = Thread(
        id = id,
        preview = preview,
        modelProvider = "openai",
        createdAt = 0L,
        updatedAt = updatedAt,
        cwd = cwd,
        status = ThreadStatus.Idle,
        cliVersion = "1.0",
        ephemeral = false,
        projectId = null,
        sessionId = id,
        name = name,
        gitInfo = branch?.let { GitInfo(branch = it) },
        recencyAt = recencyAt,
    )
}
