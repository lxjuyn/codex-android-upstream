package com.cy.codex.chatwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.v2.PlanStep
import com.cy.codex.protocol.protocol.v2.PlanStepStatus
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.raisedSurface
import com.cy.codex.successColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The plan timeline.
 *
 * Mirrors `chatwidget/plan_implementation.rs` and the plan section of `status/card.rs`: the whole
 * plan arrives from `turn/plan/updated`, so the timeline is a pure function of [steps] — one node
 * per step, the rail between them carrying the same colour as the step that owns it.
 */

/** Vertical plan timeline: hollow node for pending, half-filled for running, check for done. */
@Composable
fun PlanTimeline(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val done = steps.count { it.status == PlanStepStatus.Completed }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.plan_timeline_summary, steps.size, done),
            modifier = Modifier.padding(bottom = UiConsts.Space6),
            fontSize = UiType.Footnote,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        steps.forEachIndexed { index, step ->
            PlanStepRow(
                step = step,
                first = index == 0,
                last = index == steps.lastIndex,
            )
        }
    }
}

/** Compact "3/5 steps" pill with a thin bar, for the status card header. */
@Composable
fun PlanProgressChip(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val done = steps.count { it.status == PlanStepStatus.Completed }
    val accent = if (done == steps.size) successColor() else colors.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = UiConsts.PillCorner))
            .background(raisedSurface())
            .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.plan_timeline_chip, done, steps.size),
            fontSize = UiType.Footnote,
            lineHeight = UiType.MetaLine,
            fontWeight = FontWeight.Medium,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.width(UiConsts.Space7))
        LinearProgressIndicator(
            modifier = Modifier.width(UiConsts.ProgressWidthCompact),
            progress = (done.toFloat() / steps.size.toFloat()).coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = accent,
                backgroundColor = colors.onSurface.copy(alpha = 0.1f),
            ),
            height = UiConsts.Space3,
        )
    }
}

@Composable
private fun PlanStepRow(
    step: PlanStep,
    first: Boolean,
    last: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val accent = planStepColor(step.status)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .width(UiConsts.Space20)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(UiConsts.Space2)
                    .height(UiConsts.Space4)
                    .background(if (first) Color.Transparent else planRailColor(step.status)),
            )
            PlanNode(status = step.status)
            if (!last) {
                Box(
                    modifier = Modifier
                        .width(UiConsts.Space2)
                        .weight(1f)
                        .background(planRailColor(step.status)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = UiConsts.Space6, bottom = if (last) UiConsts.Space0 else UiConsts.Space10),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space1),
        ) {
            Text(
                text = step.step,
                fontSize = UiType.Subtitle,
                lineHeight = UiType.SubtitleLine,
                color = if (step.status == PlanStepStatus.Completed) {
                    colors.onSurfaceVariantSummary
                } else {
                    colors.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = step.status.label(),
                fontSize = UiType.Footnote,
                lineHeight = UiType.MetaLine,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlanNode(status: PlanStepStatus) {
    val colors = MiuixTheme.colorScheme
    val accent = planStepColor(status)
    Box(modifier = Modifier.size(UiConsts.PlanNodeSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(UiConsts.PlanNodeSize)) {
            val stroke = UiConsts.PlanNodeStroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            when (status) {
                PlanStepStatus.Completed -> drawCircle(color = accent, radius = radius)

                PlanStepStatus.InProgress -> {
                    drawArc(
                        color = accent,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                    )
                    drawCircle(color = accent, radius = radius, style = Stroke(width = stroke))
                }

                PlanStepStatus.Pending -> drawCircle(
                    color = accent,
                    radius = radius,
                    style = Stroke(width = stroke),
                )
            }
        }
        if (status == PlanStepStatus.Completed) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.Space9),
                tint = colors.background,
            )
        }
    }
}

/** Completed steps read as done, running ones as active, pending ones as a quiet outline. */
@Composable
private fun planStepColor(status: PlanStepStatus): Color = when (status) {
    PlanStepStatus.Completed -> successColor()
    PlanStepStatus.InProgress -> MiuixTheme.colorScheme.primary
    PlanStepStatus.Pending -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

/** The rail under a node takes the colour of the step it leaves behind. */
@Composable
private fun planRailColor(status: PlanStepStatus): Color =
    if (status == PlanStepStatus.Completed) {
        successColor().copy(alpha = 0.5f)
    } else {
        MiuixTheme.colorScheme.dividerLine
    }

/** Display label of a plan step. */
@Composable
@ReadOnlyComposable
internal fun PlanStepStatus.label(): String = stringResource(
    when (this) {
        PlanStepStatus.Pending -> R.string.plan_timeline_pending
        PlanStepStatus.InProgress -> R.string.plan_timeline_in_progress
        PlanStepStatus.Completed -> R.string.plan_timeline_completed
    },
)
