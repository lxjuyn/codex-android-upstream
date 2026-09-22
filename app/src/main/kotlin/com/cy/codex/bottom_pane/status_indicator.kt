package com.cy.codex.bottom_pane

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.DynamicToolCallStatus
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The live turn status row: elapsed time plus whatever the agent is doing right now.
 *
 * Mirrors `status_indicator_widget.rs`: a spinner, "Working" and a compact timer
 * (`0s` / `59s` / `1m 00s` / `1h 00m 00s`), then the current tool detail and the running hook under
 * a `└` prefix. The detail is one ellipsized line on a phone, where the TUI wraps up to three.
 */
@Composable
fun TurnActivityBar(
    running: Boolean,
    startedAtMs: Long?,
    detail: String?,
    hookStatus: String?,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // The timer only needs its own recomposition, and only while a turn runs: a tick a second on a
    // finished session would redraw a row nobody is reading.
    LaunchedEffect(running, startedAtMs) {
        if (!running) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    AnimatedVisibility(
        visible = running,
        enter = fadeIn(tween(Motion.EnterMs, easing = Motion.EnterEasing)) +
            expandVertically(tween(Motion.EnterMs, easing = Motion.EnterEasing), expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(Motion.ExitMs, easing = Motion.ExitEasing)) +
            shrinkVertically(tween(Motion.ExitMs, easing = Motion.ExitEasing), shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val colors = MiuixTheme.colorScheme
        val elapsed = ((now - (startedAtMs ?: now)).coerceAtLeast(0L)) / 1000
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    modifier = Modifier.width(UiConsts.Space24),
                )
                Spacer(Modifier.width(UiConsts.Space8))
                Text(
                    text = stringResource(R.string.status_indicator_working) + " · " + formatElapsedCompact(elapsed),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
            ActivityDetailLine(detail)
            ActivityDetailLine(hookStatus)
        }
    }
}

/** One `└ …` payload line; nothing renders for a blank value. */
@Composable
private fun ActivityDetailLine(text: String?) {
    if (text.isNullOrBlank()) return
    Text(
        text = "  └ $text",
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = UiConsts.Space8, top = UiConsts.Space2),
        fontSize = UiType.Code,
        lineHeight = UiType.CodeLine,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Compact elapsed time, exactly as `fmt_elapsed_compact` formats it.
 *
 * Split out so the boundary values (59s / 60s / 3600s) are testable without a composition.
 */
internal fun formatElapsedCompact(elapsedSeconds: Long): String {
    val seconds = elapsedSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
        else -> "${seconds / 3600}h ${((seconds % 3600) / 60).toString().padStart(2, '0')}m ${(seconds % 60).toString().padStart(2, '0')}s"
    }
}

/**
 * What the agent is doing right now: the last tool item that has not finished.
 *
 * `null` when the only open thing is text streaming, which the transcript already shows.
 */
internal fun activeToolDetail(items: List<ThreadItem>): String? {
    val item = items.lastOrNull { candidate ->
        when (candidate) {
            is CommandExecutionItem -> candidate.status == CommandExecutionStatus.InProgress
            is FileChangeItem -> candidate.status == PatchApplyStatus.InProgress
            is McpToolCallItem -> candidate.status == McpToolCallStatus.InProgress
            is DynamicToolCallItem -> candidate.status == DynamicToolCallStatus.InProgress
            is CollabAgentToolCallItem -> candidate.status == CollabAgentToolCallStatus.InProgress
            is ImageGenerationItem -> candidate.status == DynamicToolCallStatus.InProgress
            else -> false
        }
    } ?: return null
    return when (item) {
        is CommandExecutionItem -> item.command.lineSequence().firstOrNull { it.isNotBlank() }
        is FileChangeItem -> item.changes.firstOrNull()?.path
        is McpToolCallItem -> "${item.server}.${item.tool}"
        is DynamicToolCallItem -> item.namespace?.let { "$it.${item.tool}" } ?: item.tool
        is CollabAgentToolCallItem -> item.tool.wire
        is ImageGenerationItem -> item.prompt.lineSequence().firstOrNull { it.isNotBlank() }
        else -> null
    }
}
