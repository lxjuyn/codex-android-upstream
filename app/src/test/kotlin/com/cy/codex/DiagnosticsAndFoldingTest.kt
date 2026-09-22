package com.cy.codex

import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.CommandAction
import com.cy.codex.protocol.protocol.v2.CommandExecutionSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two rules that keep the transcript readable in a long session: repeated fallback-metadata
 * warnings are deduplicated by model slug, and a run of exploring commands folds into one row.
 */
class DiagnosticsAndFoldingTest {

    @Test
    fun `fallback metadata warning slug is parsed and only the first is kept`() {
        val message = "Model metadata for `gpt-5.4-mini` not found. " +
            "Defaulting to fallback metadata; this can degrade performance and cause issues."
        assertEquals("gpt-5.4-mini", fallbackModelMetadataWarningSlug(message))
        assertNull(fallbackModelMetadataWarningSlug("some other warning"))
        assertNull(fallbackModelMetadataWarningSlug(null))

        val state = SessionState()
        state.addDiagnostic(SessionDiagnostic(com.cy.codex.protocol.protocol.v2.DiagnosticSeverity.Warning, message))
        state.addDiagnostic(SessionDiagnostic(com.cy.codex.protocol.protocol.v2.DiagnosticSeverity.Warning, message))
        state.addDiagnostic(
            SessionDiagnostic(
                com.cy.codex.protocol.protocol.v2.DiagnosticSeverity.Warning,
                message.replace("gpt-5.4-mini", "gpt-5.4"),
            ),
        )
        assertEquals(2, state.diagnostics.size)

        // A different session starts with a clean set.
        state.beginLoad("next")
        state.addDiagnostic(SessionDiagnostic(com.cy.codex.protocol.protocol.v2.DiagnosticSeverity.Warning, message))
        assertEquals(1, state.diagnostics.size)
    }

    @Test
    fun `generic warnings are never deduplicated`() {
        val state = SessionState()
        val warning = SessionDiagnostic(com.cy.codex.protocol.protocol.v2.DiagnosticSeverity.Warning, "same")
        state.addDiagnostic(warning)
        state.addDiagnostic(warning)
        assertEquals(2, state.diagnostics.size)
    }

    @Test
    fun `exploring commands fold into one row`() {
        val read = exploring("read", CommandAction.Read("cat a.txt", "a.txt", "/w/a.txt"))
        val search = exploring("search", CommandAction.Search("rg needle", "needle", "/w"))
        val run = CommandExecutionItem("run", "make", "/w", commandActions = listOf(CommandAction.Unknown("make")))
        val message = com.cy.codex.protocol.protocol.item.AgentMessageItem("msg", "hi")

        val rows = com.cy.codex.chatwidget.foldTranscriptRows(
            listOf<ThreadItem>(read, search, run, message, read),
        )
        assertEquals(4, rows.size)
        assertTrue(rows[0].exposed)
        assertEquals(listOf(0, 1), rows[0].indices)
        assertEquals("explored:read", rows[0].key)
        assertFalse(rows[1].exposed)
        assertEquals(listOf(2), rows[1].indices)
        assertFalse(rows[2].exposed)
        assertEquals(listOf(3), rows[2].indices)
        assertTrue(rows[3].exposed)
        assertEquals(listOf(4), rows[3].indices)
    }

    @Test
    fun `user shell commands are not exploring`() {
        val item = CommandExecutionItem(
            "shell", "ls", "/w",
            source = CommandExecutionSource.UserShell,
            commandActions = listOf(CommandAction.ListFiles("ls", "/w")),
        )
        val rows = com.cy.codex.chatwidget.foldTranscriptRows(listOf<ThreadItem>(item))
        assertFalse(rows.single().exposed)
    }

    private fun exploring(id: String, action: CommandAction) = CommandExecutionItem(
        id, "cmd", "/w",
        source = CommandExecutionSource.Agent,
        commandActions = listOf(action),
    )
}
