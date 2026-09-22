package com.cy.codex

import com.cy.codex.app.transcriptMarkdown
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptExportTest {

    @Test
    fun `markdown uses upstream headings and indents activity`() {
        val markdown = transcriptMarkdown(
            listOf(
                UserMessageItem("u", content = listOf(UserInput.Text("hello"))),
                AgentMessageItem("a", "hi there"),
                CommandExecutionItem(
                    "c", "ls", "/tmp",
                    status = CommandExecutionStatus.Completed,
                    aggregatedOutput = "file.txt",
                ),
                TurnSeparatorItem("sep", "Worked for 1m"),
            ),
        )!!
        assertTrue(markdown.startsWith("# Codex conversation\n"))
        assertTrue(markdown.contains("\n## User\n\nhello\n"))
        assertTrue(markdown.contains("\n## Assistant\n\nhi there\n"))
        assertTrue(markdown.contains("\n## Activity\n\n    command: ls\n"))
        assertTrue(markdown.contains("    file.txt"))
        assertTrue(!markdown.contains("Worked for"))
    }

    @Test
    fun `a transcript with nothing exportable is null`() {
        assertNull(transcriptMarkdown(listOf(TurnSeparatorItem("sep", "Worked for 1m"))))
        assertNull(transcriptMarkdown(emptyList()))
    }
}
