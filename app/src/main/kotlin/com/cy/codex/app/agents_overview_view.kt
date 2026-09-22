package com.cy.codex.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.raisedSurface
import com.cy.codex.status.formatTokens
import com.cy.codex.statusDotColor
import com.cy.codex.usageColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One agent of the roster, as every surface that lists agents draws it.
 *
 * The picker and the dashboard used to be two unrelated row composables — 16dp rows with a
 * transparent fill against 20dp cards on `raisedSurface()`, `SheetRowTitle` against `CardTitle`, a
 * check glyph against a "current" badge — for the same object. They are now this one row; the only
 * difference between the two surfaces is how many rows they show and how tall the sheet is.
 *
 * @param tokens usage attributed to the agent. When it is greater than zero the row also draws the
 *   relative usage meter; [busiestTokens] is what the meter is measured against, so the widest bar
 *   in the list belongs to the heaviest agent rather than to an arbitrary absolute value.
 * @param selected marks the agent the transcript is currently showing.
 * @param onClick opens that agent's transcript.
 */
@Composable
internal fun AgentRosterRow(
    entry: AgentRosterEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tokens: Int = 0,
    busiestTokens: Int = 0,
) {
    val colors = MiuixTheme.colorScheme
    // The fill is animated rather than swapped: selecting an agent is a state change inside a list
    // the user is still reading, and a row that repaints on one frame reads as a glitch.
    val container by
        animateColorAsState(
            targetValue =
                when {
                    selected -> colors.primary.copy(alpha = 0.12f)
                    else -> raisedSurface()
                },
            animationSpec = Motion.Tint,
            label = "rosterRowContainer",
        )
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = UiConsts.CornerRow,
        insideMargin = PaddingValues(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        colors = CardDefaults.defaultColors(color = container, contentColor = colors.onSurface),
        showIndication = true,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(tone = entry.tone())
            Spacer(Modifier.width(UiConsts.Space9))
            Text(
                text = entry.name,
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight =
                    if (entry.role == AgentRole.Main) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RoleTag(role = entry.role)
            Spacer(Modifier.width(UiConsts.Space6))
            Text(
                text = entry.statusLabel(),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = statusDotColor(entry.tone()),
                maxLines = 1,
            )
            if (selected) {
                Spacer(Modifier.width(UiConsts.Space6))
                Text(
                    text = stringResource(R.string.agents_overview_current),
                    modifier =
                        Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                            .background(colors.primary.copy(alpha = 0.14f))
                            .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
                    fontSize = UiType.Badge,
                    lineHeight = UiType.BadgeLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
        }
        Text(
            text =
                entry.task?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.agents_overview_no_task),
            modifier = Modifier.padding(start = UiConsts.Space16, top = UiConsts.Space4),
            fontSize = UiType.Detail,
            lineHeight = UiType.DetailLine,
            color =
                if (entry.task.isNullOrBlank()) {
                    colors.onSurfaceVariantSummary
                } else {
                    colors.onSurfaceVariantActions
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta =
            listOfNotNull(
                entry.model,
                entry.effort?.label(),
                entry.itemId,
            )
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                modifier = Modifier.padding(start = UiConsts.Space16, top = UiConsts.Space3),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busiestTokens > 0) {
            Spacer(Modifier.height(UiConsts.Space8))
            TokenMeter(tokens = tokens, busiestTokens = busiestTokens)
        }
    }
}

/**
 * Relative usage of one agent.
 *
 * Per-agent usage is unknown to the item stream, so an agent without usage renders an empty track
 * rather than an invented number.
 */
@Composable
private fun TokenMeter(tokens: Int, busiestTokens: Int) {
    val colors = MiuixTheme.colorScheme
    val fraction = (tokens.toFloat() / busiestTokens.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.padding(start = UiConsts.Space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.weight(1f),
            progress = fraction,
            colors =
                ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = usageColor(fraction),
                    backgroundColor = colors.onSurface.copy(alpha = 0.08f),
                ),
            height = UiConsts.ProgressHeightRow,
        )
        Text(
            text =
                if (tokens > 0) {
                    formatTokens(tokens.toLong())
                } else {
                    stringResource(R.string.agents_overview_tokens_none)
                },
            modifier = Modifier.width(UiConsts.TokenValueWidth),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}
