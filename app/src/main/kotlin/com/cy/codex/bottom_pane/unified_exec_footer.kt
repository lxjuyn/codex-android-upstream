package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.ThreadBackgroundTerminal
import com.cy.codex.raisedSurface
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
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
            client
                .listBackgroundTerminals(threadId)
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
        BasicComponent(
            title = stringResource(R.string.exec_terminals_title),
            summary = stringResource(R.string.exec_terminals_subtitle, terminals.size),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.exec_terminals_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                // Only once there is something to clean, because the action is a category error
                // on an empty list, and a button that does nothing reads as a broken one.
                if (terminals.isNotEmpty()) {
                    Button(
                        onClick = { onEvent(AppEvent.CleanBackgroundTerminals(threadId)) },
                        modifier =
                            Modifier.squircleBorder(
                                UiConsts.OutlineThickness,
                                MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                UiConsts.ButtonHeightCompact / 2,
                            ),
                        colors =
                            ButtonColors(
                                color = Color.Transparent,
                                disabledColor =
                                    MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                                contentColor = MiuixTheme.colorScheme.error,
                                disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                            ),
                        cornerRadius = UiConsts.ButtonHeightCompact / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeightCompact,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.exec_terminals_terminate_all),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.exec_terminals_section),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        Text(
                            text = terminals.size.toString(),
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    },
                    insideMargin = PaddingValues(0.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (terminals.isEmpty()) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    vertical = UiConsts.Space24,
                                    horizontal = UiConsts.Space16,
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(UiConsts.IconBoxLarge)
                                    .squircleBackground(
                                        color = raisedSurface(),
                                        cornerRadius = UiConsts.CornerCard,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconHeader),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(UiConsts.Space12))
                        Text(
                            text = stringResource(R.string.exec_terminals_empty),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(UiConsts.Space4))
                        Text(
                            text = stringResource(R.string.exec_terminals_empty_detail),
                            fontSize = UiType.Meta,
                            lineHeight = UiType.MetaLine,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    terminals.forEachIndexed { index, terminal ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = UiConsts.Space1)
                            )
                        }
                        BackgroundTerminalRow(
                            terminal = terminal,
                            onTerminate = {
                                val event =
                                    AppEvent.TerminateBackgroundTerminal(
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = raisedSurface(),
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.exec_terminals_read_failed),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = failed.orEmpty(),
                        modifier =
                            Modifier.padding(
                                horizontal = UiConsts.Space4,
                                vertical = UiConsts.Space8,
                            ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                    Button(
                        onClick = { read() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.exec_terminals_retry),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    terminal.command.ifEmpty {
                        stringResource(R.string.exec_terminals_command_unknown)
                    },
                modifier =
                    Modifier.fillMaxWidth()
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
                text =
                    stringResource(
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
        Button(
            onClick = onTerminate,
            modifier =
                Modifier.squircleBorder(
                    UiConsts.OutlineThickness,
                    MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                    UiConsts.ButtonHeightCompact / 2,
                ),
            colors =
                ButtonColors(
                    color = Color.Transparent,
                    disabledColor = MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                    contentColor = MiuixTheme.colorScheme.error,
                    disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                ),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.exec_terminals_terminate),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
