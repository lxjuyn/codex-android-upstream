package com.cy.codex.history_cell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.ImageViewItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.SleepItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.DiagnosticCode
import com.cy.codex.MarkdownStream
import com.cy.codex.SessionDiagnostic

/**
 * The transcript dispatcher.
 *
 * Mirrors the `HistoryCell` trait in `codex-rs/tui/src/history_cell/mod.rs`: one item in, one cell
 * out. Every branch is a cell of its own so the transcript screen never has to know which item
 * kinds exist.
 */

/** Renders one transcript item. This is the only function the transcript screen calls. */
@Composable
fun ThreadItemCell(
    item: ThreadItem,
    modifier: Modifier = Modifier,
    stream: MarkdownStream? = null,
    streaming: Boolean = false,
    assistantLabel: String = stringResource(R.string.transcript_cell_assistant_name),
    cwd: String? = null,
    onOpenAgent: (String) -> Unit = {},
    onOpenAgentInfo: (String) -> Unit = {},
    onAnswerQuestion: (String) -> Unit = {},
) {
    when (item) {
        is UserMessageItem -> UserMessageCell(item, modifier)
        is HookPromptItem -> HookPromptCell(item, modifier)
        is AgentMessageItem ->
            AgentMessageCell(item, modifier, stream, streaming, assistantLabel, cwd = cwd, onAnswerQuestion = onAnswerQuestion)
        is FunctionCallOutputItem -> FunctionCallOutputCell(item, modifier)
        is PlanItem -> PlanItemCell(item, modifier, stream, streaming, cwd = cwd)
        is ReasoningItem -> ReasoningCell(item, modifier, streaming)
        is CommandExecutionItem -> CommandExecutionCell(item, modifier)
        is FileChangeItem -> FileChangeCell(item, modifier, cwd = cwd)
        is McpToolCallItem -> McpToolCallCell(item, modifier)
        is DynamicToolCallItem -> DynamicToolCallCell(item, modifier)
        is CollabAgentToolCallItem ->
            CollabToolCallCell(item, modifier, onOpenAgent = onOpenAgent, onOpenAgentInfo = onOpenAgentInfo)

        is SubAgentActivityItem ->
            SubAgentActivityCell(item, modifier, onOpenAgent = onOpenAgent, onOpenAgentInfo = onOpenAgentInfo)
        is WebSearchItem -> WebSearchCell(item, modifier)
        is ImageViewItem -> ImageViewCell(item, modifier)
        is SleepItem -> SleepCell(item, modifier)
        is ImageGenerationItem -> ImageGenerationCell(item, modifier)
        is EnteredReviewModeItem -> ReviewModeCell(item, modifier)
        is ExitedReviewModeItem -> ExitedReviewModeCell(item, modifier)
        is ContextCompactionItem -> ContextCompactionCell(item, modifier)
        is com.cy.codex.protocol.protocol.item.TurnSeparatorItem -> TurnSeparatorCell(item, modifier)
        is com.cy.codex.protocol.protocol.item.RecapItem -> RecapCell(item, modifier)
        is com.cy.codex.protocol.protocol.item.TipItem -> TooltipCell(item, modifier)
    }
}

/**
 * One warning/error the session produced, rendered like a transcript entry.
 *
 * The reducer names its own notices by [DiagnosticCode] rather than by text — it has no `Context`
 * and no composition — so the wording is resolved here, at the edge, where a string resource can be
 * read. [SessionDiagnostic.message] is kept for text the transport supplied, which is data rather
 * than copy: `error.message` is reported as it arrived.
 */
@Composable
fun DiagnosticCell(
    diagnostic: SessionDiagnostic,
    modifier: Modifier = Modifier,
) {
    val message = diagnostic.message
        ?: diagnostic.code?.let { code -> diagnosticText(code, diagnostic.args) }
        ?: ""
    NoticeCell(
        message = message,
        detail = diagnostic.detail,
        tone = when (diagnostic.severity) {
            DiagnosticSeverity.Info -> NoticeTone.Info
            DiagnosticSeverity.Warning -> NoticeTone.Warning
            DiagnosticSeverity.Error -> NoticeTone.Error
        },
        modifier = modifier,
    )
}

/** The wording of one client-authored notice. */
@Composable
private fun diagnosticText(code: DiagnosticCode, args: List<String>): String = when (code) {
    DiagnosticCode.ThreadLoadFailed -> stringResource(R.string.chatwidget_diagnostic_thread_load_failed)
    DiagnosticCode.NewThreadFailed -> stringResource(R.string.chatwidget_diagnostic_new_thread_failed)
    DiagnosticCode.SendFailed -> stringResource(R.string.chatwidget_diagnostic_send_failed)
    DiagnosticCode.InterruptFailed -> stringResource(R.string.chatwidget_diagnostic_interrupt_failed)
    DiagnosticCode.TurnInterrupted -> stringResource(R.string.chatwidget_diagnostic_turn_interrupted)
    DiagnosticCode.TurnFailed -> stringResource(R.string.chatwidget_diagnostic_turn_failed)
    DiagnosticCode.TurnFinished -> stringResource(
        R.string.chatwidget_diagnostic_turn_finished,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.ModelSwitched -> stringResource(
        R.string.chatwidget_diagnostic_model_switched,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.WorldWritable -> stringResource(
        R.string.chatwidget_diagnostic_world_writable,
        args.firstOrNull().orEmpty(),
        args.getOrNull(1).orEmpty(),
    )

    DiagnosticCode.StrictReviewRequired -> stringResource(R.string.chatwidget_diagnostic_strict_review)

    DiagnosticCode.HookFailed -> stringResource(
        R.string.chatwidget_diagnostic_hook_failed,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.McpLoginFailed -> stringResource(
        R.string.chatwidget_diagnostic_mcp_login_failed,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.SafetyBuffering -> stringResource(R.string.chatwidget_diagnostic_safety_buffering)

    DiagnosticCode.RateLimitWarning -> stringResource(
        R.string.chatwidget_diagnostic_rate_limit_warning,
        args.firstOrNull()?.toIntOrNull() ?: 0,
        args.getOrNull(1).orEmpty(),
    )

    DiagnosticCode.RateLimitReached -> stringResource(
        R.string.chatwidget_diagnostic_rate_limit_reached,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.ImageTooLarge -> stringResource(
        R.string.chatwidget_diagnostic_image_too_large,
        args.firstOrNull().orEmpty(),
    )

    DiagnosticCode.RecapNoHistory -> stringResource(R.string.chatwidget_diagnostic_recap_no_history)
}
