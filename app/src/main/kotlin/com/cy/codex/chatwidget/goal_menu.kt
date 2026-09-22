package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.GoalStatus
import com.cy.codex.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.status.formatTokens
import com.cy.codex.successColor
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * Goal mode sheet.
 *
 * Mirrors `chatwidget/goal_menu.rs` and `chatwidget/goal_status.rs`: with no goal the sheet is a
 * single objective field that starts one, with a goal it reports progress — the same `tokensUsed` /
 * `timeUsedSeconds` numbers the status line shows — and offers to clear it.
 */
@Composable
fun GoalSheet(
    goal: ThreadGoalUpdated?,
    onSet: (String) -> Unit,
    onSetStatus: (GoalStatus) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var objective by
        remember(goal?.threadId, goal?.objective) { mutableStateOf(goal?.objective.orEmpty()) }
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.goal_sheet_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            Text(
                text =
                    if (goal == null) {
                        stringResource(R.string.goal_sheet_subtitle_idle)
                    } else {
                        goal.status.label()
                    },
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (goal == null) {
                TextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = DpSize(UiConsts.Space12, UiConsts.Space10),
                    label = stringResource(R.string.goal_sheet_objective_hint),
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    text = stringResource(R.string.goal_sheet_objective_example),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space2,
                        ),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Button(
                    onClick = {
                        onSet(objective.trim())
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = UiConsts.Space4),
                    enabled = objective.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.goal_sheet_start),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val usage =
                    stringResource(
                        R.string.goal_sheet_usage,
                        formatTokens(goal.tokensUsed.toLong()),
                        formatGoalDuration(goal.timeUsedSeconds),
                    )
                Row(
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GoalStatusChip(status = goal.status)
                    Spacer(Modifier.width(UiConsts.Space8))
                    Text(
                        text = usage,
                        fontSize = UiType.RowDetail,
                        lineHeight = UiType.RowDetailLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                if (goal.tokenBudget != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.goal_sheet_budget,
                                formatTokens(goal.tokenBudget),
                            ),
                        modifier =
                            Modifier.padding(
                                horizontal = UiConsts.Space4,
                                vertical = UiConsts.Space3,
                            ),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                // Editing keeps the status and the budget: `UpdateExisting` upstream carries both
                // through `goal_menu.rs`, so a paused goal stays paused when its objective changes.
                TextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space4),
                    insideMargin = DpSize(UiConsts.Space12, UiConsts.Space10),
                    label = stringResource(R.string.goal_sheet_objective_hint),
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    text = stringResource(R.string.goal_sheet_progress_hint),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space2,
                        ),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Button(
                    onClick = {
                        onSet(objective.trim())
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = UiConsts.Space4),
                    enabled = objective.isNotBlank() && objective.trim() != goal.objective,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.goal_sheet_save),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (goal.status) {
                    GoalStatus.Active ->
                        Button(
                            onClick = {
                                onSetStatus(GoalStatus.Paused)
                                onDismiss()
                            },
                            modifier = Modifier.padding(top = UiConsts.Space4),
                            colors = ButtonDefaults.buttonColors(),
                            cornerRadius = UiConsts.ButtonHeight / 2,
                            minHeight = UiConsts.ButtonHeight,
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.ButtonPaddingHorizontal,
                                    vertical = 0.dp,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.goal_sheet_pause),
                                fontSize = UiType.Action,
                                lineHeight = UiType.ActionLine,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                    GoalStatus.Paused,
                    GoalStatus.Blocked,
                    GoalStatus.UsageLimited ->
                        Button(
                            onClick = {
                                onSetStatus(GoalStatus.Active)
                                onDismiss()
                            },
                            modifier = Modifier.padding(top = UiConsts.Space4),
                            colors = ButtonDefaults.buttonColors(),
                            cornerRadius = UiConsts.ButtonHeight / 2,
                            minHeight = UiConsts.ButtonHeight,
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.ButtonPaddingHorizontal,
                                    vertical = 0.dp,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.goal_sheet_resume),
                                fontSize = UiType.Action,
                                lineHeight = UiType.ActionLine,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                    // A budget-limited or finished goal has nothing left to run; upstream offers
                    // only
                    // edit and clear for these two, so no pause/resume button is drawn.
                    GoalStatus.BudgetLimited,
                    GoalStatus.Complete -> Unit
                }
                Button(
                    onClick = {
                        onClear()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = UiConsts.Space4),
                    colors = ButtonDefaults.buttonColors(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.goal_sheet_clear),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Status an edit should carry over, mirroring `goal_menu.rs:edited_goal_status`.
 *
 * A goal that stopped for a terminal reason (budget limited, complete) restarts active when its
 * objective is edited; paused / blocked / usage-limited goals stay in that state.
 */
internal fun editedGoalStatus(status: GoalStatus): GoalStatus =
    when (status) {
        GoalStatus.Active -> GoalStatus.Active
        GoalStatus.Paused,
        GoalStatus.Blocked,
        GoalStatus.UsageLimited -> status

        GoalStatus.BudgetLimited,
        GoalStatus.Complete -> GoalStatus.Active
    }

/** Status pill of a running goal, coloured by how the run ended or whether it is still going. */
@Composable
internal fun GoalStatusChip(status: GoalStatus) {
    val accent = goalStatusColor(status)
    Text(
        text = status.label(),
        modifier =
            Modifier.clip(RoundedCornerShape(UiConsts.PillCorner))
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space3),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 1,
    )
}

/**
 * Active goals run, paused ones wait, budget-limited ones need a decision, complete ones are done.
 */
@Composable
internal fun goalStatusColor(status: GoalStatus): Color =
    when (status) {
        GoalStatus.Active -> MiuixTheme.colorScheme.primary
        GoalStatus.Paused -> warningColor()
        GoalStatus.Blocked -> warningColor()
        GoalStatus.UsageLimited -> MiuixTheme.colorScheme.error
        GoalStatus.BudgetLimited -> MiuixTheme.colorScheme.error
        GoalStatus.Complete -> successColor()
    }

/**
 * Elapsed goal time in *seconds*: `200` becomes `3 分 20 秒`, `3900` becomes `1 小时 5 分`.
 *
 * Named for its unit because `chatwidget/cell/ExecCell.kt` has a formatter for the same shape of
 * value measured in milliseconds. Both used to be called `formatDuration`, which is a 1000x error
 * waiting for whoever imports the wrong one.
 */
@Composable
@ReadOnlyComposable
internal fun formatGoalDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remaining = total % 60
    return when {
        hours > 0 ->
            if (minutes > 0) {
                stringResource(R.string.goal_sheet_duration_hours_minutes, hours, minutes)
            } else {
                stringResource(R.string.goal_sheet_duration_hours, hours)
            }

        minutes > 0 ->
            if (remaining > 0) {
                stringResource(R.string.goal_sheet_duration_minutes_seconds, minutes, remaining)
            } else {
                stringResource(R.string.goal_sheet_duration_minutes, minutes)
            }

        else -> stringResource(R.string.goal_sheet_duration_seconds, remaining)
    }
}
