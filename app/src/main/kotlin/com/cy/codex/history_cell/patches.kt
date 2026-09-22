package com.cy.codex.history_cell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import com.cy.codex.FileDiffRow
import com.cy.codex.ToolCard
import com.cy.codex.fileDiffOf
import com.cy.codex.parseUnifiedDiff
import com.cy.codex.ThreadStatusTone
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The patch summary of one turn.
 *
 * Mirrors `codex-rs/tui/src/history_cell/patches.rs`: the card lists every changed file with its
 * per-file diff and only expands the one the reader asked for, so a wide patch cannot swamp the
 * transcript.
 */
@Composable
fun FileChangeCell(
    item: FileChangeItem,
    modifier: Modifier = Modifier,
    cwd: String? = null,
    errorFontSize: TextUnit = UiType.Body,
    errorLineHeight: TextUnit = UiType.SheetTitle,
    blockSpacing: Dp = 6.dp,
) {
    val colors = MiuixTheme.colorScheme
    val tone = patchApplyTone(item.status)
    val diffs = remember(item.id, item.changes) {
        item.changes.map { change -> fileDiffOf(change.path, parseUnifiedDiff(change.diff)) }
    }
    // Keyed by index so two hunks touching the same path keep independent disclosure state.
    val expanded = remember(item.id) {
        mutableStateMapOf<Int, Boolean>().apply {
            if (item.changes.size == 1) put(0, true)
        }
    }

    ToolCard(
        icon = MiuixIcons.ConvertFile,
        title = stringResource(R.string.patches_cell_title),
        subtitle = stringResource(
            R.string.patches_cell_summary,
            item.changes.size,
            patchApplyLabel(item.status),
        ),
        modifier = modifier,
        accent = statusDotColor(tone),
        trailing = { StatusChip(label = patchApplyLabel(item.status), tone = tone) },
    ) {
        if (item.status == PatchApplyStatus.Declined || item.status == PatchApplyStatus.Failed) {
            Text(
                text = if (item.status == PatchApplyStatus.Declined) {
                    stringResource(R.string.patches_cell_declined)
                } else {
                    stringResource(R.string.patches_cell_failed)
                },
                fontSize = errorFontSize,
                lineHeight = errorLineHeight,
                color = colors.error,
            )
            Spacer(Modifier.height(blockSpacing))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            diffs.forEachIndexed { index, diff ->
                FileDiffRow(
                    file = diff,
                    expanded = expanded[index] == true,
                    onToggle = { expanded[index] = expanded[index] != true },
                    cwd = cwd,
                )
            }
        }
    }
}

internal fun patchApplyTone(status: PatchApplyStatus): ThreadStatusTone = when (status) {
    PatchApplyStatus.InProgress -> ThreadStatusTone.Running
    PatchApplyStatus.Completed -> ThreadStatusTone.Done
    PatchApplyStatus.Failed -> ThreadStatusTone.Failed
    PatchApplyStatus.Declined -> ThreadStatusTone.Waiting
}

@Composable
@ReadOnlyComposable
internal fun patchApplyLabel(status: PatchApplyStatus): String = when (status) {
    PatchApplyStatus.InProgress -> stringResource(R.string.patches_cell_status_applying)
    PatchApplyStatus.Completed -> stringResource(R.string.patches_cell_status_applied)
    PatchApplyStatus.Failed -> stringResource(R.string.patches_cell_status_failed)
    PatchApplyStatus.Declined -> stringResource(R.string.patches_cell_status_declined)
}
