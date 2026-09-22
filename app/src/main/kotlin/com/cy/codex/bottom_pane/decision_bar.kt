package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared decision controls for the approval dialog.
 *
 * Mirrors the option list `codex-rs/tui/src/bottom_pane/approval_overlay.rs` builds for all four
 * request families: the *same* pill row sits under every dialog, so a command approval and an MCP
 * form cannot disagree about what "允许" looks like.
 *
 * The pills are [CodexButton] and nothing else. They used to be two more hand-rolled controls —
 * one 36dp with an inline scale transform, one 38dp with no horizontal padding at all — sitting
 * next to a third definition in `overlays/`. Three heights and three corner systems inside one
 * dialog is what made the decision row look assembled rather than designed.
 */

/** One button of a [DecisionRow]: the label the protocol supplied plus where it sits in the order. */
data class DecisionAction(
    val label: String,
    val role: ButtonRole = ButtonRole.Secondary,
    val onClick: () -> Unit,
)

/**
 * A wrapping row of pill buttons.
 *
 * Wraps instead of scrolling: four decisions (`允许` / `本会话总是允许` / `拒绝` / `取消本轮`) do not
 * fit on a phone in one line, and a horizontally scrolling decision bar hides the destructive
 * escape hatch off-screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecisionRow(
    decisions: List<DecisionAction>,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        decisions.forEach { action ->
            CodexButton(
                text = action.label,
                onClick = action.onClick,
                role = action.role,
                enabled = !busy,
                minWidth = UiConsts.ButtonMinWidth,
            )
        }
    }
}

/**
 * The remaining-request footer.
 *
 * Reads as a report rather than a warning: the queue is a fact about the session, not a problem
 * with the answer being given, so it takes the muted caption colour and sits under the buttons
 * instead of competing with them.
 */
@Composable
internal fun RemainingQueueLine(remainingQueue: Int) {
    if (remainingQueue <= 0) return
    Spacer(Modifier.height(UiConsts.Space10))
    Text(
        text = stringResource(R.string.decision_bar_remaining_queue, remainingQueue),
        modifier = Modifier.fillMaxWidth(),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
    )
}

/**
 * `取消` + confirm row for the two form dialogs.
 *
 * They cannot use [DecisionRow]: both have an invalid state (an unanswered question, a missing
 * required field) that has to keep the primary action visible but disabled, which a decision list
 * has no notion of.
 *
 * The two buttons split the dialog's width evenly, so the confirm action is always the same size
 * as the escape next to it whichever label happens to be longer.
 */
@Composable
internal fun FormButtons(
    confirmLabel: String,
    enabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CodexButton(
                text = stringResource(R.string.decision_bar_cancel),
                onClick = onCancel,
                role = ButtonRole.Secondary,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CodexButton(
                text = confirmLabel,
                onClick = onConfirm,
                role = ButtonRole.Primary,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A section of a dialog body: an eyebrow and its content.
 *
 * The two form dialogs build their bodies out of these so a question block, a field block and the
 * command block of a command approval all start on the same left edge with the same gap above them.
 */
@Composable
internal fun DialogSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}
