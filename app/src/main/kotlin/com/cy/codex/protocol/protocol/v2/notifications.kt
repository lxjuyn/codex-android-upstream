package com.cy.codex.protocol.protocol.v2

/**
 * Payloads the client reads back out of the 82 notifications.
 *
 * Mirrors `schema/typescript/v2/`. Only fields the UI renders are carried; the rest of the wire
 * message is dropped by the decoder.
 */

/** `item/agentMessage/delta`, `item/plan/delta`, `item/reasoning(3)`: one incremental text chunk. */
data class ItemTextDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
    val summaryIndex: Int = 0,
)

/** `item/commandExecution/outputDelta`: incremental stdout/stderr of a running command. */
data class CommandExecutionOutputDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

/** `item/commandExecution/terminalInteraction`: the user typed into the live terminal. */
data class TerminalInteraction(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val processId: String,
    val stdin: String,
)

/** `item/fileChange/outputDelta`: progress text while a patch is applied. */
data class FileChangeOutputDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

/** `item/mcpToolCall/progress`: free-form progress note from an MCP tool. */
data class McpToolCallProgress(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val message: String,
)

/** `turn/plan/updated`: the whole plan after a revision. */
data class TurnPlanUpdated(
    val threadId: String,
    val turnId: String,
    val plan: List<PlanStep>,
)

data class PlanStep(val step: String, val status: PlanStepStatus)

enum class PlanStepStatus(val wire: String) {
    Pending("pending"),
    InProgress("inProgress"),
    Completed("completed"),
    ;

    companion object {
        fun fromWire(value: String): PlanStepStatus =
            entries.firstOrNull { it.wire == value } ?: Pending
    }
}

/** `turn/diff/updated`: the accumulated unified diff of the whole turn. */
data class TurnDiffUpdated(
    val threadId: String,
    val turnId: String,
    val diff: String,
)

/** `thread/status/changed`. */
data class ThreadStatusChanged(
    val threadId: String,
    val status: ThreadStatus,
)

/** `thread/tokenUsage/updated`. */
data class ThreadTokenUsageUpdated(
    val threadId: String,
    val turnId: String?,
    val usage: ThreadTokenUsage,
)

/** `thread/name/updated`. */
data class ThreadNameUpdated(val threadId: String, val name: String?)

/** `thread/settings/updated`: model, effort, approval policy, reviewer and modes changed server-side. */
data class ThreadSettingsUpdated(
    val threadId: String,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val collaborationMode: CollaborationMode? = null,
    val serviceTier: String? = null,
)

/**
 * `thread/queue/changed`: the server's queue for this thread moved.
 *
 * Carries no payload — it is a poke. The client answers it with `thread/queue/list`; see [QueuedSubmission].
 */
data class ThreadQueueChanged(val threadId: String)

/** `thread/goal/updated` and `thread/goal/cleared`. */
data class ThreadGoalUpdated(
    val threadId: String,
    val objective: String,
    val status: GoalStatus = GoalStatus.Active,
    /** Token ceiling for the goal, or null when the goal is unbounded. */
    val tokenBudget: Long? = null,
    val tokensUsed: Int = 0,
    val timeUsedSeconds: Long = 0L,
)

/**
 * `thread/goal/set`: a patch rather than a replacement.
 *
 * Every field the caller omits is left alone, which is what `thread/goal/set` does upstream
 * (`ThreadGoalSetParams`). [clearTokenBudget] sends the explicit JSON null that removes an existing
 * ceiling, because omitting the field means "keep it".
 */
data class ThreadGoalSetParams(
    val threadId: String,
    val objective: String? = null,
    val status: GoalStatus? = null,
    val tokenBudget: Long? = null,
    val clearTokenBudget: Boolean = false,
)

enum class GoalStatus(val wire: String) {
    Active("active"),
    Paused("paused"),
    Blocked("blocked"),
    UsageLimited("usageLimited"),
    BudgetLimited("budgetLimited"),
    Complete("complete"),
}

/** `thread/reverted`: the transcript was rolled back to an earlier item. */
data class ThreadReverted(val threadId: String, val itemId: String?)

/** `thread/compacted`. */
data class ThreadCompacted(val threadId: String, val summary: String? = null)

// ---------------------------------------------------------------------------------------------
// Diagnostics
//
// These are five *separate* notifications on the wire, with different payloads, so they are five
// separate types here. Collapsing them into one "diagnostic" shape loses `willRetry` and
// `path`/`range` (which locate a bad config key).
// ---------------------------------------------------------------------------------------------

/** `error` — a turn-level failure. */
data class ErrorNotification(
    val error: TurnError,
    val threadId: String,
    val turnId: String,
    /**
     * The server is retrying the turn itself.
     *
     * Kept for wire parity: the transcript has no manual retry to suppress, so nothing reads it.
     */
    val willRetry: Boolean = false,
)

/** `error.error`; `codexErrorInfo` is the snake-free wire tag listed by `CodexErrorInfo`. */
data class TurnError(
    val message: String,
    val additionalDetails: String? = null,
    val codexErrorInfo: String? = null,
)

/** `warning` — a plain advisory with no structured payload. */
data class WarningNotification(
    val threadId: String,
    val message: String,
)

/** `configWarning` — one bad key in a config layer, located by path and line range. */
data class ConfigWarningNotification(
    val summary: String,
    val details: String? = null,
    val path: String? = null,
    val range: TextRange? = null,
)

data class TextRange(val start: Int, val end: Int)

/** `guardianWarning` — the review policy flagged something about a turn. */
data class GuardianWarningNotification(
    val threadId: String,
    val message: String,
)

/** `deprecationNotice` — a config key or flag that will be removed. */
data class DeprecationNoticeNotification(
    val summary: String,
    val details: String? = null,
)

/** `windows/worldWritableWarning` — a scanned directory that is writable by everyone. */
data class WorldWritableWarningNotification(
    val samplePaths: List<String> = emptyList(),
    val extraCount: Int = 0,
    /** The scan itself failed, so `samplePaths` is not exhaustive. */
    val failedScan: Boolean = false,
)

enum class DiagnosticSeverity { Info, Warning, Error }

/**
 * `serverRequest/resolved`: the request is settled and every surface should drop it.
 *
 * Carries the thread rather than the method — the client already knows which method it answered,
 * and the thread is what lets a surface decide whether the card it is showing is the one that just
 * went away.
 */
data class ServerRequestResolved(
    val requestId: String,
    val threadId: String,
)

/** `model/rerouted`: the requested model could not be served and another one took over. */
data class ModelRerouted(
    val threadId: String,
    val fromModel: String,
    val toModel: String,
    val reason: String,
)

/**
 * `account/rateLimits/updated`.
 *
 * A sparse rolling update: fields the server could not supply are absent, not zero, so a reader
 * merges them into the last `account/rateLimits/read` snapshot instead of replacing it.
 */
data class RateLimitsUpdated(val rateLimits: RateLimitSnapshot)

/** `mcpServer/startupStatus/updated`. */
data class McpStartupStatusUpdated(
    val serverName: String,
    val status: McpServerStartupState,
    val error: String? = null,
    /** `reauthenticationRequired` when the failure is an expired credential. */
    val failureReason: String? = null,
)

/** `skills/changed` / `app/list/updated`: a catalog the UI is showing went stale. */
data class CatalogChanged(val reason: String? = null)

/** `fs/changed`: a watched path changed on disk. */
data class FsChanged(val path: String, val kind: String)
