package com.cy.codex.history_cell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.rememberExpanded
import com.cy.codex.Motion
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The collapsible "thinking" block.
 *
 * Mirrors `ReasoningSummaryCell` in `codex-rs/tui/src/history_cell/messages.rs`: the reasoning body
 * is dimmed relative to the answer, so it collapses to a single summary line and only expands when
 * the user asks for the detail.
 */
@Composable
fun ReasoningCell(
    item: ReasoningItem,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    defaultExpanded: Boolean = false,
    headerVerticalPadding: Dp = 4.dp,
    chevronSize: Dp = 13.dp,
    chevronSpacing: Dp = 7.dp,
    headerFontSize: TextUnit = UiType.Subtitle,
    headerLineHeight: TextUnit = UiType.SheetTitle,
    bodyStartPadding: Dp = 6.dp,
    bodyTopPadding: Dp = 2.dp,
    quoteBarWidth: Dp = 2.5.dp,
    quoteCorner: Dp = UiConsts.CornerBar,
    quoteSpacing: Dp = 10.dp,
    paragraphSpacing: Dp = 8.dp,
    bodyFontSize: TextUnit = UiType.Body,
    bodyLineHeight: TextUnit = UiType.Title,
) {
    val colors = MiuixTheme.colorScheme
    val (expanded, toggle) = rememberExpanded(defaultExpanded)
    // The same disclosure the section cards use: 0 degrees when closed, 90 when open, over
    // Motion.Disclosure. It used to snap, which made this header the one in the app that did.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = Motion.Disclosure,
        label = "reasoningChevron",
    )
    val paragraphs = remember(item.summary, item.content) { item.summary + item.content }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle)
                .padding(vertical = headerVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (expanded) {
                    stringResource(R.string.reasoning_cell_collapse)
                } else {
                    stringResource(R.string.reasoning_cell_expand)
                },
                modifier = Modifier
                    .size(chevronSize)
                    .rotate(chevronRotation),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(chevronSpacing))
            Text(
                text = stringResource(R.string.reasoning_cell_label),
                fontSize = headerFontSize,
                lineHeight = headerLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
            if (streaming) {
                val shimmer by rememberShimmerAlpha()
                Text(
                    text = stringResource(R.string.reasoning_cell_streaming),
                    fontSize = headerFontSize,
                    lineHeight = headerLineHeight,
                    color = colors.primary.copy(alpha = shimmer),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.reasoning_cell_title) },
                    modifier = Modifier.weight(1f),
                    fontSize = headerFontSize,
                    lineHeight = headerLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = bodyStartPadding, top = bodyTopPadding),
            ) {
                Box(
                    modifier = Modifier
                        .width(quoteBarWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(quoteCorner))
                        .background(colors.primary.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.width(quoteSpacing))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
                ) {
                    if (paragraphs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reasoning_cell_no_details),
                            fontSize = bodyFontSize,
                            lineHeight = bodyLineHeight,
                            color = colors.onSurfaceVariantSummary,
                        )
                    } else {
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                fontSize = bodyFontSize,
                                lineHeight = bodyLineHeight,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fade used by the streaming reasoning label. */
@Composable
private fun rememberShimmerAlpha(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "reasoningShimmer")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.SpinnerPeriodMs.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reasoningShimmerAlpha",
    )
}
