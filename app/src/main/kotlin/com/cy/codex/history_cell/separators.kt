package com.cy.codex.history_cell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Id prefix of a [TurnSeparatorItem]; the separator exists once per turn id. */
internal const val TurnSeparatorIdPrefix = "turn-separator-"

// Locale.US on purpose: upstream's chrono formats `%b`/`%p` in English whatever the host locale,
// and a translated month next to the literal "at" would be worse than one language throughout.
private val ClockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val SameYearFormatter = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US)
private val OlderFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US)

/**
 * Completion metadata for the divider after a turn, mirroring `FinalMessageSeparator::label`.
 *
 * The elapsed part is dropped for turns under a minute (upstream `> 60`); the timestamp records
 * when the turn finished, with the date added when it is not today. Every part is absent-tolerant:
 * a turn with neither duration nor completion time gets no divider at all.
 */
internal fun finalMessageSeparatorLabel(
    elapsedSeconds: Long?,
    completedAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val parts = mutableListOf<String>()
    val elapsed = elapsedSeconds?.takeIf { it > 60 }
    if (elapsed != null) {
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        val text = when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
        parts += "Worked for $text"
    }
    if (completedAtMillis != null) {
        val completed = Instant.ofEpochMilli(completedAtMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val formatter = when {
            completed.toLocalDate() == today -> ClockFormatter
            completed.year == today.year -> SameYearFormatter
            else -> OlderFormatter
        }
        parts += formatter.format(completed)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The subtle, indented divider drawn after a turn's last cell. */
@Composable
internal fun TurnSeparatorCell(item: TurnSeparatorItem, modifier: Modifier = Modifier) {
    Text(
        text = item.label,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = UiConsts.Space16, top = UiConsts.Space2),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The transcript of [turns], with a divider inserted after every finished turn.
 *
 * `thread/read` returns turns in order, so this is also the order the transcript was streamed in.
 * In-progress turns get no divider: they have neither a completion time nor a duration yet.
 */
internal fun transcriptWithSeparators(turns: List<Turn>): List<ThreadItem> {
    val items = ArrayList<ThreadItem>(turns.sumOf { it.items.size } + turns.size)
    for (turn in turns) {
        items += turn.items
        if (turn.status == TurnStatus.InProgress) continue
        val elapsed = turn.durationMs?.let { it / 1000 }
            ?: turn.completedAt?.let { completed -> (completed - turn.startedAt).coerceAtLeast(0) / 1000 }
        val label = finalMessageSeparatorLabel(elapsed, turn.completedAt) ?: continue
        items += TurnSeparatorItem(TurnSeparatorIdPrefix + turn.id, label)
    }
    return items
}
