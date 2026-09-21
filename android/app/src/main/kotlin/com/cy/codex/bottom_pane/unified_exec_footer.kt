package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SessionState
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.ThreadBackgroundTerminal
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The background terminals one thread has left running.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/unified_exec_footer.rs`: the TUI prints this as a footer
 * strip under the composer, one line per process, because a long-running command started by a turn
 * keeps going after the turn's output has scrolled away. A phone has no footer that can hold a
 * command, a directory and an age and still be tappable, so the same three facts are rows.
 *
 * The list is the server's: it is read with `thread/backgroundTerminals/list` and mirrored into
 * [SessionState.backgroundTerminals], which the rest of the app already reads. Terminating is an
 * event rather than a call because a row only names a process — the reducer owns the cleanup that
 * has to follow the call in [SessionState].
 */
@Composable
fun BackgroundTerminalsScreen(
    threadId: String,
    client: AppServerClient,
    session: SessionState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<String?>(null) }
    val terminals = session.backgroundTerminals

    fun read() {
        scope.launch {
            loading = true
            failed = null
            client.listBackgroundTerminals(threadId)
                .onSuccess { listed ->
                    // The read is the whole list, so it replaces rather than merges: a terminal
                    // gone from the answer is gone from the thread, and leaving it on screen would
                    // offer a kill button for a process that no longer exists.
                    session.backgroundTerminals.clear()
                    session.backgroundTerminals.addAll(listed)
                }
                .onFailure { failed = it.message }
            loading = false
        }
    }

    LaunchedEffect(threadId) { read() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.exec_terminals_title),
            subtitle = stringResource(R.string.exec_terminals_subtitle, terminals.size),
            leading = { SurfaceBackButton(stringResource(R.string.exec_terminals_back), onBack) },
            trailing = {
                // Only once there is something to clean, because the action is a category error
                // on an empty list, and a button that does nothing reads as a broken one.
                if (terminals.isNotEmpty()) {
                    CodexButton(
                        text = stringResource(R.string.exec_terminals_terminate_all),
                        onClick = { onEvent(AppEvent.CleanBackgroundTerminals(threadId)) },
                        size = CodexButtonSize.Compact,
                        role = ButtonRole.Destructive,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            SectionCard(
                title = stringResource(R.string.exec_terminals_section),
                icon = MiuixIcons.Notes,
                trailing = terminals.size.toString(),
            ) {
                if (terminals.isEmpty()) {
                    EmptyState(
                        icon = MiuixIcons.Notes,
                        title = stringResource(R.string.exec_terminals_empty),
                        detail = stringResource(R.string.exec_terminals_empty_detail),
                    )
                } else {
                    terminals.forEachIndexed { index, terminal ->
                        if (index > 0) BackgroundTerminalDivider()
                        BackgroundTerminalRow(
                            terminal = terminal,
                            onTerminate = {
                                val event = AppEvent.TerminateBackgroundTerminal(
                                    threadId = threadId,
                                    processId = terminal.processId,
                                )
                                onEvent(event)
                            },
                        )
                    }
                }
            }
            if (failed != null && terminals.isEmpty()) {
                SectionCard(
                    title = stringResource(R.string.exec_terminals_read_failed),
                    icon = MiuixIcons.Notes,
                ) {
                    Text(
                        text = failed.orEmpty(),
                        modifier = Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space8,
                        ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                    CodexButton(
                        text = stringResource(R.string.exec_terminals_retry),
                        onClick = { read() },
                        modifier = Modifier.fillMaxWidth(),
                        role = ButtonRole.Secondary,
                    )
                }
            } else if (loading) {
                // A read that has not answered yet is not the same as a thread with no terminals,
                // and the footer is exactly the surface where the difference matters.
                Text(
                    text = stringResource(R.string.exec_terminals_reading),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * One background terminal: what is running, where, since when, and the one action that ends it.
 *
 * The command is the row's title rather than a field in a detail page, because the question this
 * list answers is "which of these is the one I need to stop" — and that question is answered by the
 * command line, not by the process id.
 */
@Composable
private fun BackgroundTerminalRow(
    terminal: ThreadBackgroundTerminal,
    onTerminate: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = terminal.command.ifEmpty {
                    stringResource(R.string.exec_terminals_command_unknown)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(codeSurface())
                    .padding(horizontal = UiConsts.Space7, vertical = UiConsts.Space5),
                fontSize = UiType.Code,
                lineHeight = UiType.CodeLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(UiConsts.Space5))
            Text(
                text = terminal.cwd.ifEmpty { stringResource(R.string.exec_terminals_cwd_unknown) },
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(UiConsts.Space2))
            Text(
                text = stringResource(
                    R.string.exec_terminals_row_meta,
                    terminal.processId,
                    terminal.osPid?.let { stringResource(R.string.exec_terminals_os_pid, it) }
                        ?: stringResource(R.string.exec_terminals_os_pid_unknown),
                ),
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(UiConsts.Space10))
        CodexButton(
            text = stringResource(R.string.exec_terminals_terminate),
            onClick = onTerminate,
            size = CodexButtonSize.Compact,
            role = ButtonRole.Destructive,
        )
    }
}

/** Hairline between two terminal rows, matching the dividers the rest of the app's cards use. */
@Composable
private fun BackgroundTerminalDivider() = CodexDivider()


