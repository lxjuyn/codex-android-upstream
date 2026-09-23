package com.cy.codex.protocol.protocol.item

import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion
import com.cy.codex.protocol.protocol.v2.CollabAgentState
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.CommandAction
import com.cy.codex.protocol.protocol.v2.CommandExecutionSource
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.DynamicToolCallStatus
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.protocol.protocol.v2.MessagePhase
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.UserInput

/**
 * The v2 `ThreadItem` union — the unit the transcript is made of.
 *
 * Mirrors `codex-rs/app-server-protocol/src/protocol/v2/item.rs`. The server streams one item at a
 * time: `item/started` opens it, zero or more deltas fill it in (`item/agentMessage/delta`,
 * `item/commandExecution/outputDelta`, …) and `item/completed` finalises it. Everything the
 * transcript renders is one of these, so the UI never works from raw text.
 */
sealed interface ThreadItem {
    /** Stable server-assigned id; deltas address the item through it. */
    val id: String
}

data class UserMessageItem(
    override val id: String,
    val clientId: String? = null,
    val content: List<UserInput>,
) : ThreadItem

data class HookPromptItem(
    override val id: String,
    val fragments: List<HookPromptFragment>,
) : ThreadItem

/**
 * One `<hook_prompt>` block: the text a hook injected plus the run it belongs to.
 *
 * [hookRunId] is the opaque id of one hook invocation (`HookPromptFragment` in
 * `codex-rs/protocol/src/items.rs`), not a hook name — the wire shape never carries the name, and
 * `hooks/list` does not expose a matching run id, so it is displayed only when it is the one piece
 * of identity a transcript has.
 */
data class HookPromptFragment(val text: String, val hookRunId: String = "")

data class AgentMessageItem(
    override val id: String,
    val text: String,
    val phase: MessagePhase? = null,
    val questions: List<AsyncUserInputQuestion>? = null,
    val memoryCitation: MemoryCitation? = null,
    val delivery: AgentMessageDelivery? = null,
) : ThreadItem

/**
 * The memory entries an agent message leaned on.
 *
 * Mirrors `MemoryCitation` in `codex-rs/protocol/src/memory_citation.rs`: `threadIds` is the wire
 * name for the rollout ids the citation came from, and each entry points at a line range in one
 * file.
 */
data class MemoryCitation(
    val entries: List<MemoryCitationEntry> = emptyList(),
    val threadIds: List<String> = emptyList(),
)

data class MemoryCitationEntry(
    val path: String,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
    val note: String? = null,
)

/** `AgentMessageDelivery`: the only variant the wire currently defines is `async`. */
enum class AgentMessageDelivery(val wire: String) {
    Async("async"),
    ;

    companion object {
        fun fromWire(value: String?): AgentMessageDelivery? =
            entries.firstOrNull { it.wire == value }
    }
}

data class FunctionCallOutputItem(
    override val id: String,
    val name: String,
    val namespace: String? = null,
    val output: String,
) : ThreadItem

data class PlanItem(
    override val id: String,
    val text: String,
) : ThreadItem

data class ReasoningItem(
    override val id: String,
    val summary: List<String> = emptyList(),
    val content: List<String> = emptyList(),
) : ThreadItem {
    val title: String get() = summary.firstOrNull() ?: content.firstOrNull().orEmpty()
}

data class CommandExecutionItem(
    override val id: String,
    val command: String,
    val cwd: String,
    val processId: String? = null,
    val source: CommandExecutionSource = CommandExecutionSource.Agent,
    val status: CommandExecutionStatus = CommandExecutionStatus.InProgress,
    val commandActions: List<CommandAction> = emptyList(),
    /** Trusted plugin id when the command resolves to one plugin script, else `null`. */
    val pluginId: String? = null,
    /** Safe plugin-relative path for that same plugin script. */
    val scriptPath: String? = null,
    val aggregatedOutput: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
) : ThreadItem

data class FileChangeItem(
    override val id: String,
    val changes: List<FileUpdateChange>,
    val status: PatchApplyStatus = PatchApplyStatus.InProgress,
) : ThreadItem

data class McpToolCallItem(
    override val id: String,
    val server: String,
    val tool: String,
    val status: McpToolCallStatus = McpToolCallStatus.InProgress,
    val arguments: String = "{}",
    /** `appContext`: the connector/app a descriptor-backed tool call went through. */
    val appContext: McpToolCallAppContext? = null,
    /** Presentation captured from the invoked descriptor; absent in older history. */
    val mcpAppUi: McpAppUi? = null,
    /** Trusted plugin id when the tool came from a plugin. */
    val pluginId: String? = null,
    /** The descriptor's `readOnlyHint`, when it declared one. */
    val readOnlyHint: Boolean? = null,
    /** Legacy resource uri; prefer [mcpAppUi]'s own field when both are present. */
    val mcpAppResourceUri: String? = null,
    val result: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
) : ThreadItem {
    /**
     * The app resource this call acted on.
     *
     * The descriptor-captured uri is the current shape and the legacy field only fills the gap for
     * older history, which is the order upstream resolves them in (`core/src/mcp_tool_call.rs`).
     */
    val appResourceUri: String? get() = mcpAppUi?.resourceUri ?: mcpAppResourceUri
}

/**
 * Which connector/app an MCP tool call was routed through.
 *
 * Mirrors `McpToolCallAppContext` in `codex-rs/app-server-protocol/src/protocol/v2/item.rs`.
 */
data class McpToolCallAppContext(
    val connectorId: String,
    val linkId: String? = null,
    val resourceUri: String? = null,
    val appName: String? = null,
    val actionName: String? = null,
)

/**
 * How an MCP app result should be presented.
 *
 * Mirrors `McpAppUi` in `codex-rs/protocol/src/items.rs`; [preferredModelDisplayMode] is `inline` or
 * `fullscreen` and stays a string because a future mode must not be coerced into a wrong one.
 */
data class McpAppUi(
    val resourceUri: String? = null,
    val preferredModelDisplayMode: String? = null,
)

data class DynamicToolCallItem(
    override val id: String,
    val namespace: String? = null,
    val tool: String,
    val arguments: String = "{}",
    val status: DynamicToolCallStatus = DynamicToolCallStatus.InProgress,
    val contentItems: List<DynamicToolOutputContent> = emptyList(),
    val success: Boolean? = null,
    val durationMs: Long? = null,
) : ThreadItem

/**
 * One `DynamicToolCallOutputContentItem` block.
 *
 * Mirrors the tagged union in `codex-rs/app-server-protocol/src/protocol/v2/item.rs`: text is the
 * only variant that carries displayable content, the media variants carry a URL the transcript
 * cannot render, so it only records which kind arrived.
 */
sealed interface DynamicToolOutputContent {
    data class InputText(val text: String) : DynamicToolOutputContent
    data class InputImage(val imageUrl: String) : DynamicToolOutputContent
    data class InputAudio(val audioUrl: String) : DynamicToolOutputContent
}

data class CollabAgentToolCallItem(
    override val id: String,
    val tool: CollabAgentTool,
    val status: CollabAgentToolCallStatus = CollabAgentToolCallStatus.InProgress,
    val senderThreadId: String,
    val receiverThreadIds: List<String> = emptyList(),
    val prompt: String? = null,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val agentsStates: Map<String, CollabAgentState> = emptyMap(),
) : ThreadItem

data class SubAgentActivityItem(
    override val id: String,
    val kind: SubAgentActivityKind,
    val agentThreadId: String,
    val agentPath: String,
) : ThreadItem

data class WebSearchItem(
    override val id: String,
    val query: String,
    val results: List<WebSearchResult> = emptyList(),
    /** What the web tool actually did; `null` on servers that do not model the action yet. */
    val action: WebSearchAction? = null,
) : ThreadItem

/**
 * One element of a web search result list.
 *
 * The wire keeps these as opaque JSON so new result types can pass through without a protocol
 * change (`ext/items/src/web_search.rs`), so only the two fields a result list needs to be useful
 * are projected; an element carrying neither is dropped rather than shown as an empty row.
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String? = null,
    /** The server's own result discriminator (`text_result` today), kept for export fidelity. */
    val type: String? = null,
)

/**
 * The `WebSearchAction` union of `app-server-protocol/schema/json/v2`.
 *
 * Mirrors `codex-rs/ext/items/src/web_search.rs`: the search tool can search, open a page or find
 * text in one, and only the action's own fields name what happened.
 */
sealed interface WebSearchAction {
    data class Search(val query: String?, val queries: List<String>?) : WebSearchAction
    data class OpenPage(val url: String?) : WebSearchAction
    data class FindInPage(val url: String?, val pattern: String?) : WebSearchAction
    data object Other : WebSearchAction
}

data class ImageViewItem(
    override val id: String,
    val path: String,
) : ThreadItem

data class SleepItem(
    override val id: String,
    val durationMs: Long,
) : ThreadItem

/**
 * One image-generation call.
 *
 * Mirrors `ImageGenerationItem` in `codex-rs/ext/items/src/image_generation.rs`. There is no
 * `prompt` field on the wire: the prompt the model actually used is [revisedPrompt], and the
 * original request only survives inside the backend. [status] stays a string because the server
 * owns the vocabulary (`completed` / `failed` today) and a new value must not be coerced into
 * "in progress".
 */
data class ImageGenerationItem(
    override val id: String,
    val status: String = "",
    val revisedPrompt: String? = null,
    /** The generated image: a data URL or a remote URL, depending on the backend. */
    val result: String = "",
    val transparentBackground: Boolean? = null,
    val failure: ImageGenerationFailure? = null,
    /** Where the image was written on the server's filesystem, when it kept one. */
    val savedPath: String? = null,
) : ThreadItem {
    val failed: Boolean get() = status == FailedStatus

    /** The line the transcript narrates this call with, falling back to the item id. */
    val detail: String get() = revisedPrompt?.takeIf { it.isNotBlank() } ?: id

    companion object {
        const val InProgressStatus = "inProgress"
        const val CompletedStatus = "completed"
        const val FailedStatus = "failed"
    }
}

/**
 * Why an image generation failed.
 *
 * Mirrors `ImageGenerationFailure` in `codex-rs/ext/items/src/image_generation.rs`: a tagged union
 * whose only variant today is `usageLimitExceeded`.
 */
sealed interface ImageGenerationFailure {
    data class UsageLimitExceeded(val limitId: String, val resetsAt: Long?) : ImageGenerationFailure
}

data class EnteredReviewModeItem(
    override val id: String,
    val review: String,
) : ThreadItem

data class ExitedReviewModeItem(
    override val id: String,
    val review: String,
) : ThreadItem

data class ContextCompactionItem(
    override val id: String,
) : ThreadItem

/**
 * A client-local divider the transcript shows once a turn finishes.
 *
 * Not a wire item: upstream draws this from the completed `Turn` (`history_cell/separators.rs`),
 * and this transcript is item-based, so [label] is formatted when the separator is inserted and
 * the item is rebuilt from `thread/read` turns on every history refresh.
 */
data class TurnSeparatorItem(
    override val id: String,
    val label: String,
) : ThreadItem

/**
 * A client-local conversation recap cell (`/recap`).
 *
 * `text == null` is the in-flight state ("Generating conversation recap…"); [failed] marks a
 * request that finished without a usable answer. Both exist only on this device.
 */
data class RecapItem(
    override val id: String,
    val text: String?,
    val nextAction: String? = null,
    val failed: Boolean = false,
) : ThreadItem

/** A client-local startup tip, shown once on a fresh conversation (`tui/src/tooltips.rs`). */
data class TipItem(
    override val id: String,
    val text: String,
) : ThreadItem
