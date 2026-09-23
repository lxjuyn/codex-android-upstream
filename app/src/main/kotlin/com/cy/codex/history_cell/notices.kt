package com.cy.codex.history_cell

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.codeSurface
import com.cy.codex.successColor
import com.cy.codex.warningColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Everything the transcript has to say that is not a message or a tool call: warnings, errors,
 * context compaction, review mode and hook prompts.
 *
 * Mirrors `codex-rs/tui/src/history_cell/notices.rs` and `hook_cell.rs`: a notice is one row with a
 * severity, an optional detail line, and a tint of the severity colour instead of a border, so a
 * run of notices never reads as loudly as the conversation itself.
 */

enum class NoticeTone { Info, Warning, Error, Success }

/** Shared notice row; `DiagnosticCell` and the review-mode cells are built on it. */
@Composable
fun NoticeCell(
    message: String,
    detail: String? = null,
    tone: NoticeTone = NoticeTone.Info,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.CornerControl,
    accentBarWidth: Dp = 3.dp,
    startPadding: Dp = 11.dp,
    endPadding: Dp = 12.dp,
    topPadding: Dp = 9.dp,
    bottomPadding: Dp = 10.dp,
    iconTopPadding: Dp = 1.dp,
    iconSize: Dp = 16.dp,
    iconSpacing: Dp = 9.dp,
    contentSpacing: Dp = 3.dp,
    messageFontSize: TextUnit = UiType.Body,
    messageLineHeight: TextUnit = UiType.BodyLine,
    detailFontSize: TextUnit = UiType.RowDetail,
    detailLineHeight: TextUnit = UiType.Composer,
) {
    val colors = MiuixTheme.colorScheme
    val accent = noticeToneColor(tone)
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(accent.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(accentBarWidth)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.8f)),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = startPadding,
                    end = endPadding,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = noticeToneIcon(tone),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = iconTopPadding)
                    .size(iconSize),
                tint = accent,
            )
            Spacer(Modifier.width(iconSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                Text(
                    text = message,
                    fontSize = messageFontSize,
                    lineHeight = messageLineHeight,
                    color = colors.onSurface,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        fontSize = detailFontSize,
                        lineHeight = detailLineHeight,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
fun ContextCompactionCell(item: ContextCompactionItem, modifier: Modifier = Modifier) {
    CompactLine(
        icon = MiuixIcons.Merge,
        text = stringResource(R.string.notices_cell_context_compacted),
        detail = stringResource(R.string.notices_cell_context_compacted_detail),
        modifier = modifier,
        tone = ThreadStatusTone.Done,
    )
}

@Composable
fun ReviewModeCell(item: EnteredReviewModeItem, modifier: Modifier = Modifier) {
    NoticeCell(
        message = stringResource(R.string.notices_cell_entered_review_mode),
        detail = item.review.ifBlank { null },
        tone = NoticeTone.Info,
        modifier = modifier,
    )
}

@Composable
fun ExitedReviewModeCell(item: ExitedReviewModeItem, modifier: Modifier = Modifier) {
    NoticeCell(
        message = stringResource(R.string.notices_cell_exited_review_mode),
        detail = item.review.ifBlank { null },
        tone = NoticeTone.Success,
        modifier = modifier,
    )
}

/** Hook output, credited to the hook that produced it. */
@Composable
fun HookPromptCell(
    item: HookPromptItem,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.RowCorner,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    blockSpacing: Dp = 9.dp,
    iconSize: Dp = 14.dp,
    iconSpacing: Dp = 8.dp,
    headerFontSize: TextUnit = UiType.RowDetail,
    headerLineHeight: TextUnit = UiType.Message,
    fragmentSpacing: Dp = 2.dp,
    fragmentFontSize: TextUnit = UiType.Body,
    fragmentLineHeight: TextUnit = UiType.BodyLine,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(codeSurface())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(blockSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Notes,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(iconSpacing))
            Text(
                text = stringResource(R.string.notices_cell_hook),
                fontSize = headerFontSize,
                lineHeight = headerLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        item.fragments.forEach { fragment ->
            Column(verticalArrangement = Arrangement.spacedBy(fragmentSpacing)) {
                // The wire only carries the hook's opaque run id — `hookRunId` — and no name, and
                // the header above already says the block came from a hook, so there is nothing
                // worth a second line here.
                Text(
                    text = fragment.text,
                    fontSize = fragmentFontSize,
                    lineHeight = fragmentLineHeight,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun noticeToneColor(tone: NoticeTone): Color = when (tone) {
    NoticeTone.Info -> MiuixTheme.colorScheme.primary
    NoticeTone.Warning -> warningColor()
    NoticeTone.Error -> MiuixTheme.colorScheme.error
    NoticeTone.Success -> successColor()
}

private fun noticeToneIcon(tone: NoticeTone): ImageVector = when (tone) {
    NoticeTone.Info -> MiuixIcons.Info
    NoticeTone.Warning -> MiuixIcons.Blocklist
    NoticeTone.Error -> MiuixIcons.Close
    NoticeTone.Success -> MiuixIcons.Ok
}
