package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.glassTint
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Messages queued behind the running turn.
 *
 * The queue is the server's, not the client's: `thread/queue/changed` is only a poke, and the rows
 * below are whatever the last `thread/queue/list` returned. Every action here is therefore a
 * request — [onStart], [onMove] and [onRemove] address a queued submission by id, and the list is
 * re-read when the server confirms. Reordering locally would produce an order the server never saw.
 *
 * It sits inline above the composer rather than behind a scrim: queueing is normal while a turn
 * runs, not a modal decision.
 */
@Composable
fun QueuedMessages(
    messages: List<QueuedSubmission>,
    onStart: (QueuedSubmission) -> Unit,
    onMove: (QueuedSubmission, Int) -> Unit,
    onRemove: (QueuedSubmission) -> Unit,
    onEdit: (QueuedSubmission, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    // The entry being edited, held as the entry and not as its text: an edit has to write back the
    // *whole* input list, and the text alone would drop a queued attachment on the way through.
    var editing by remember { mutableStateOf<QueuedSubmission?>(null) }
    val shape = remember { RoundedCornerShape(UiConsts.PanelCorner) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = glassTint(0.94f),
        shadowElevation = UiConsts.PanelElevation,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = UiConsts.Space12,
                    end = UiConsts.Space10,
                    top = UiConsts.Space8,
                    bottom = UiConsts.Space10,
                ),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space7),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowUpDown,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconChevron),
                    tint = colors.primary,
                )
                Spacer(Modifier.width(UiConsts.Space7))
                Text(
                    text = stringResource(R.string.queued_messages_header, messages.size),
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.SubtitleLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                // Clearing is the one bulk action; it is only offered while more than one row is
                // shown,
                // because a single row already has its own remove chip one line below.
                if (messages.size > 1) {
                    QueuedChip(
                        text = stringResource(R.string.queued_messages_clear),
                        tint = colors.onSurfaceVariantSummary,
                        onClick = onClear,
                    )
                    Spacer(Modifier.width(UiConsts.Space6))
                }
                IconButton(
                    onClick = onDismiss,
                    minWidth = UiConsts.IconButtonCompact,
                    minHeight = UiConsts.IconButtonCompact,
                    backgroundColor = colors.onSurface.copy(alpha = 0.06f),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.queued_messages_collapse),
                        modifier = Modifier.size(UiConsts.IconChevron),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
            messages.forEachIndexed { index, entry ->
                QueuedMessageRow(
                    index = index,
                    entry = entry,
                    first = index == 0,
                    last = index == messages.lastIndex,
                    onStart = { onStart(entry) },
                    onMoveUp = { onMove(entry, -1) },
                    onMoveDown = { onMove(entry, 1) },
                    onRemove = { onRemove(entry) },
                    onEdit = { editing = entry },
                )
            }
        }
    }

    editing?.let { entry ->
        FormSheet(
            title = stringResource(R.string.queued_messages_edit),
            subtitle = stringResource(R.string.queued_messages_edit_detail),
            fields =
                listOf(
                    FormField(
                        key = "body",
                        label = stringResource(R.string.queued_messages_edit_label),
                        initial = entry.preview,
                    )
                ),
            confirmLabel = stringResource(R.string.queued_messages_edit_save),
            onDismiss = { editing = null },
            onSubmit = { values ->
                onEdit(entry, values["body"].orEmpty())
                editing = null
            },
        )
    }
}

@Composable
private fun QueuedMessageRow(
    index: Int,
    entry: QueuedSubmission,
    first: Boolean,
    last: Boolean,
    onStart: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(colors.onSurface.copy(alpha = 0.045f))
                .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space7),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(UiConsts.QueueIndexSize)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    fontSize = UiType.Badge,
                    lineHeight = UiType.Subtitle,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(UiConsts.Space8))
            Text(
                text = entry.preview.ifBlank { stringResource(R.string.queued_messages_empty) },
                modifier = Modifier.weight(1f),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                color = colors.onSurfaceVariantActions,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The action row is right-aligned under the message so the text keeps the full width of the
        // card on the line that matters, and the four chips stay reachable with a thumb.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueuedChip(
                text = stringResource(R.string.queued_messages_move_up),
                tint = colors.onSurfaceVariantSummary,
                onClick = onMoveUp,
                enabled = !first,
            )
            QueuedChip(
                text = stringResource(R.string.queued_messages_move_down),
                tint = colors.onSurfaceVariantSummary,
                onClick = onMoveDown,
                enabled = !last,
            )
            QueuedChip(
                text = stringResource(R.string.queued_messages_edit),
                tint = colors.onSurfaceVariantSummary,
                onClick = onEdit,
            )
            QueuedChip(
                text = stringResource(R.string.queued_messages_remove),
                tint = colors.error,
                onClick = onRemove,
            )
            QueuedChip(
                text = stringResource(R.string.queued_messages_start),
                tint = colors.primary,
                onClick = onStart,
            )
        }
    }
}

/**
 * A small text pill, the same one the composer's inline actions use.
 *
 * Shared by the row actions and the header's clear button so a disabled chip looks the same
 * everywhere; [enabled] only dims the label rather than removing the chip, because a row whose
 * "move up" disappeared would reflow every time the order changed.
 */
@Composable
private fun QueuedChip(
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val alpha = if (enabled) 0.14f else 0.06f
    Text(
        text = text,
        modifier =
            Modifier.background(
                    tint.copy(alpha = alpha),
                    RoundedCornerShape(percent = UiConsts.PillCorner),
                )
                .clip(RoundedCornerShape(percent = UiConsts.PillCorner))
                .combinedClickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space4),
        fontSize = UiType.Action,
        lineHeight = UiType.ActionLine,
        fontWeight = FontWeight.Medium,
        color = if (enabled) tint else colors.disabledOnSurface,
        maxLines = 1,
    )
}
