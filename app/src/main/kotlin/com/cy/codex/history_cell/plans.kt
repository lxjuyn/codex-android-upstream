package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.v2.PlanStep
import com.cy.codex.protocol.protocol.v2.PlanStepStatus
import com.cy.codex.MarkdownStream
import com.cy.codex.MarkdownStreamText
import com.cy.codex.MarkdownText
import com.cy.codex.raisedSurface
import com.cy.codex.successColor
import com.cy.codex.UiType
import com.cy.codex.UiConsts
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The agent's proposed plan and the turn's checklist.
 *
 * Mirrors `codex-rs/tui/src/history_cell/plans.rs`: a plan body is styled differently from an
 * answer (it is a proposal, not a reply), and `Updated Plan` renders as a checkbox list whose
 * markers carry the step status.
 */

@Composable
fun PlanItemCell(
    item: PlanItem,
    modifier: Modifier = Modifier,
    stream: MarkdownStream? = null,
    streaming: Boolean = false,
    cwd: String? = null,
    corner: Dp = UiConsts.CornerRow,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    iconSize: Dp = 14.dp,
    iconSpacing: Dp = 8.dp,
    titleSpacing: Dp = 8.dp,
    fontSize: TextUnit = UiType.Subtitle,
    lineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(raisedSurface())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Tasks,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = colors.primary,
            )
            Spacer(Modifier.width(iconSpacing))
            Text(
                text = stringResource(R.string.plans_cell_title),
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(titleSpacing))
        if (stream != null && stream.hasContent) {
            MarkdownStreamText(stream = stream, streaming = streaming, cwd = cwd)
        } else {
            MarkdownText(
                markdown = item.text.ifBlank { stringResource(R.string.plans_cell_empty) },
                cwd = cwd,
            )
        }
    }
}

/** `○` pending, `◐` in progress, `●` done — the markers the TUI's plan updates use. */
@Composable
fun PlanChecklist(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    emptyFontSize: TextUnit = UiType.Subtitle,
    emptyLineHeight: TextUnit = UiType.SheetTitle,
    markerWidth: Dp = 18.dp,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.BodyLine,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (steps.isEmpty()) {
            Text(
                text = stringResource(R.string.plans_cell_no_steps),
                fontSize = emptyFontSize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        }
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = planStepMarker(step.status),
                    modifier = Modifier.width(markerWidth),
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = planStepColor(step.status),
                )
                Text(
                    text = step.step,
                    modifier = Modifier.weight(1f),
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = if (step.status == PlanStepStatus.Completed) {
                        colors.onSurfaceVariantSummary
                    } else {
                        colors.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun planStepColor(status: PlanStepStatus) = when (status) {
    PlanStepStatus.Pending -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    PlanStepStatus.InProgress -> MiuixTheme.colorScheme.primary
    PlanStepStatus.Completed -> successColor()
}

@Composable
@ReadOnlyComposable
private fun planStepMarker(status: PlanStepStatus): String = when (status) {
    PlanStepStatus.Pending -> stringResource(R.string.plans_cell_step_pending)
    PlanStepStatus.InProgress -> stringResource(R.string.plans_cell_step_in_progress)
    PlanStepStatus.Completed -> stringResource(R.string.plans_cell_step_completed)
}
