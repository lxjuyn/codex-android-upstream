package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.v2.CommandAction
import com.cy.codex.protocol.protocol.v2.CommandExecutionSource
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.CodeBlock
import com.cy.codex.ToolCard
import com.cy.codex.ThreadStatusTone
import com.cy.codex.render.highlightShellCommand
import com.cy.codex.statusDotColor
import com.cy.codex.statusPillSurface
import com.cy.codex.render.syntaxPalette
import com.cy.codex.UiType
import com.cy.codex.UiConsts
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Trim
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * Command executions with their live output.
 *
 * Mirrors the exec cells in `codex-rs/tui/src/history_cell/exec.rs` and
 * `exec_cell/render.rs`: the command is the headline, the parsed command actions are labels above
 * the body, and the aggregated output is shown tail-first because a running command's interesting
 * lines are the last ones.
 */
@Composable
fun CommandExecutionCell(
    item: CommandExecutionItem,
    modifier: Modifier = Modifier,
    metaFontSize: TextUnit = UiType.Chip,
    metaLineHeight: TextUnit = UiType.CardTitle,
    bodyFontSize: TextUnit = UiType.Body,
    bodyLineHeight: TextUnit = UiType.SheetTitle,
    chipSpacing: Dp = 6.dp,
    blockSpacing: Dp = 8.dp,
    progressHeight: Dp = 2.dp,
) {
    val colors = MiuixTheme.colorScheme
    val tone = commandExecutionTone(item.status)
    val output = item.aggregatedOutput?.trimEnd().orEmpty()
    val palette = syntaxPalette()
    val command = item.command.trim().ifEmpty { stringResource(R.string.exec_cell_command_title) }
    val styledCommand = remember(command, palette) { highlightShellCommand(command, palette) }
    val meta = listOfNotNull(
        item.exitCode?.let { stringResource(R.string.exec_cell_exit_code, it) },
        item.durationMs?.let { formatToolDuration(it) },
    ).joinToString(stringResource(R.string.exec_cell_meta_separator))

    ToolCard(
        icon = MiuixIcons.Trim,
        title = command,
        titleStyled = styledCommand,
        subtitle = item.cwd.ifBlank { null },
        modifier = modifier,
        accent = statusDotColor(tone),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = metaFontSize,
                        lineHeight = metaLineHeight,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(chipSpacing))
                }
                StatusChip(label = commandExecutionLabel(item.status), tone = tone)
            }
        },
    ) {
        if (item.status == CommandExecutionStatus.InProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                height = progressHeight,
            )
            Spacer(Modifier.height(blockSpacing))
        }
        if (item.commandActions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
            ) {
                item.commandActions.forEach { action -> MetaChip(text = commandActionLabel(action)) }
            }
            Spacer(Modifier.height(blockSpacing))
        }
        when {
            output.isNotEmpty() -> LimitedCodeBlock(text = output, language = "shell", maxLines = 10)
            item.status == CommandExecutionStatus.InProgress -> Text(
                text = stringResource(R.string.exec_cell_waiting_for_output),
                fontSize = bodyFontSize,
                lineHeight = bodyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
            item.status == CommandExecutionStatus.Declined -> Text(
                text = stringResource(R.string.exec_cell_command_not_approved),
                fontSize = bodyFontSize,
                lineHeight = bodyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** The TUI's `Read` / `List` / `Search` / `Run` labels for one parsed command action. */
@Composable
@ReadOnlyComposable
internal fun commandActionLabel(action: CommandAction): String = when (action) {
    is CommandAction.Read -> stringResource(
        R.string.exec_cell_action_read,
        action.name.ifEmpty { action.path },
    )
    is CommandAction.ListFiles -> stringResource(
        R.string.exec_cell_action_list,
        action.path ?: action.command,
    )
    is CommandAction.Search -> when {
        action.query.isNullOrBlank() ->
            stringResource(R.string.exec_cell_action_search, action.command)
        action.path.isNullOrBlank() ->
            stringResource(R.string.exec_cell_action_search, action.query)
        else -> stringResource(R.string.exec_cell_action_search_in, action.path, action.query)
    }
    is CommandAction.Unknown -> stringResource(R.string.exec_cell_action_run, action.command)
}

internal fun commandExecutionTone(status: CommandExecutionStatus): ThreadStatusTone = when (status) {
    CommandExecutionStatus.InProgress -> ThreadStatusTone.Running
    CommandExecutionStatus.Completed -> ThreadStatusTone.Done
    CommandExecutionStatus.Failed -> ThreadStatusTone.Failed
    CommandExecutionStatus.Declined -> ThreadStatusTone.Waiting
}

@Composable
@ReadOnlyComposable
internal fun commandExecutionLabel(status: CommandExecutionStatus): String = when (status) {
    CommandExecutionStatus.InProgress -> stringResource(R.string.exec_cell_status_running)
    CommandExecutionStatus.Completed -> stringResource(R.string.exec_cell_status_completed)
    CommandExecutionStatus.Failed -> stringResource(R.string.exec_cell_status_failed)
    CommandExecutionStatus.Declined -> stringResource(R.string.exec_cell_status_declined)
}

// ---------------------------------------------------------------------------------------------
// Atoms shared by every tool cell in the transcript.
// ---------------------------------------------------------------------------------------------

/** Lines of tool output shown before the cell offers its "expand all" control. */
internal const val OutputPreviewLines = 20

/**
 * Small state pill used by every tool card. Mirrors the coloured status words the TUI prints
 * next to a tool call.
 */
@Composable
internal fun StatusChip(
    label: String,
    tone: ThreadStatusTone,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 2.dp,
    fontSize: TextUnit = UiType.Chip,
    lineHeight: TextUnit = UiType.CardTitle,
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(statusPillSurface(tone))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = statusDotColor(tone),
        maxLines = 1,
    )
}

/** Monospace label chip: parsed command actions, file paths, tool names. */
@Composable
internal fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp,
    fontSize: TextUnit = UiType.Chip,
    lineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(colors.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = colors.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Tool output in a code block, collapsed to a head and a tail with a `… +N lines` marker between
 * them, as `codex-rs/tui/src/exec_cell/render.rs` previews a command. The expander below the block
 * still reveals the whole body.
 */
@Composable
internal fun LimitedCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    maxLines: Int = OutputPreviewLines,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 3.dp,
    fontSize: TextUnit = UiType.Code,
    lineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    val body = text.trimEnd('\n')
    val lines = remember(body) { body.split('\n') }
    var expanded by remember(body) { mutableStateOf(false) }
    val headCount = (maxLines / 2).coerceAtLeast(1)
    val tailCount = (maxLines - headCount).coerceAtLeast(0)
    val omitted = (lines.size - headCount - tailCount).coerceAtLeast(0)
    val truncated = lines.size > maxLines
    val marker = stringResource(R.string.exec_cell_omitted_lines, omitted)
    val shownBody = when {
        !truncated || expanded -> body
        tailCount == 0 -> (lines.take(headCount) + marker).joinToString("\n")
        else -> (lines.take(headCount) + marker + lines.takeLast(tailCount)).joinToString("\n")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CodeBlock(code = shownBody, language = language)
        if (truncated) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.exec_cell_collapse)
                } else {
                    stringResource(R.string.exec_cell_more_lines, lines.size - headCount)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(corner))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = colors.primary,
                maxLines = 1,
            )
        }
    }
}

/** `412ms` / `6.1s` / `2m3s`, the durations the TUI prints for a finished tool call. */
@Composable
@ReadOnlyComposable
internal fun formatToolDuration(durationMs: Long): String {
    val value = durationMs.coerceAtLeast(0L)
    return when {
        value < 1_000L -> stringResource(R.string.exec_cell_duration_ms, value)
        value < 60_000L -> String.format(
            Locale.US,
            stringResource(R.string.exec_cell_duration_seconds),
            value / 1000.0,
        )
        else -> stringResource(
            R.string.exec_cell_duration_minutes,
            value / 60_000L,
            (value % 60_000L) / 1000L,
        )
    }
}

/**
 * Whether one command is "exploring" rather than "running".
 *
 * Mirrors `ExecCell::is_exploring_call` in `codex-rs/tui/src/exec_cell/model.rs`: reads, listings
 * and searches the agent runs to understand the workspace group under an `Explored` heading instead
 * of one card each.
 */
internal fun CommandExecutionItem.isExploringCall(): Boolean =
    source == CommandExecutionSource.Agent &&
        commandActions.isNotEmpty() &&
        commandActions.all { action ->
            action is CommandAction.Read || action is CommandAction.ListFiles || action is CommandAction.Search
        }
