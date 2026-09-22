package com.cy.codex

import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SandboxMode
import com.cy.codex.protocol.protocol.v2.SandboxPolicy
import com.cy.codex.protocol.protocol.v2.ThreadStartParams
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/**
 * A hidden, throwaway structured turn, mirroring `tui/src/temporary_structured_request.rs`.
 *
 * The thread is ephemeral, read-only and never asks for approval, so it cannot touch the workspace
 * or pop a dialog; the UI never binds it, and only the final agent message is read back.
 */
internal const val StructuredTurnTimeoutMs = 30_000L

/** Agent-message budget, the `collect_structured_response` 8 KiB cap. */
internal const val MaxStructuredResponseChars = 8 * 1024

/**
 * Run one structured turn and return the final agent message, or a failure when the turn did not
 * finish inside [StructuredTurnTimeoutMs].
 *
 * The temporary thread is always unsubscribed: it is ephemeral, so leaving it subscribed would
 * only keep delivering events for a thread that no surface can open.
 */
internal suspend fun structuredTurn(
    client: AppServerClient,
    cwd: String,
    model: String?,
    developerInstructions: String?,
    prompt: String,
    outputSchema: JsonElement,
    effort: ReasoningEffort? = null,
): Result<String> = runCatching {
    val session = client.startThread(
        ThreadStartParams(
            cwd = cwd,
            model = model?.takeIf { it.isNotBlank() },
            ephemeral = true,
            approvalPolicy = AskForApproval.Never,
            sandbox = SandboxPolicy(SandboxMode.ReadOnly),
            developerInstructions = developerInstructions,
        ),
    ).getOrThrow()
    val threadId = session.threadId
    try {
        client.startTurn(
            threadId = threadId,
            inputs = listOf(UserInput.Text(prompt)),
            outputSchema = outputSchema,
            effort = effort,
        ).getOrThrow()
        // Wait for the turn to finish, then read the transcript back: item bodies are authoritative
        // there, and a fast turn cannot race a collector that starts after `turn/start` returned.
        withTimeoutOrNull(StructuredTurnTimeoutMs) {
            client.events.first { event ->
                event is AppServerEvent.TurnCompleted && event.threadId == threadId
            }
        } ?: error("The structured request timed out")
        val items = client.readThread(
            com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId),
        ).getOrThrow().items
        items.asReversed()
            .filterIsInstance<AgentMessageItem>()
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?.take(MaxStructuredResponseChars)
            .orEmpty()
    } finally {
        client.unsubscribeThread(threadId)
    }
}

/** The text of the thread's first user message, for prompts built from one user request. */
internal fun firstUserMessageText(items: List<com.cy.codex.protocol.protocol.item.ThreadItem>): String? =
    items.filterIsInstance<UserMessageItem>()
        .firstOrNull()
        ?.content
        ?.filterIsInstance<UserInput.Text>()
        ?.joinToString("\n") { it.text }
        ?.takeIf { it.isNotBlank() }
