package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.raisedSurface
import com.cy.codex.successColor
import com.cy.codex.warningColor
import java.util.Base64
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `command/exec` and `process/spawn` as one page.
 *
 * Mirrors `codex-rs/tui/src/exec_command.rs`: the TUI reaches both families through the same shell
 * entry point and only the *lifetime* of the process differs — a command runs to completion and
 * answers with its exit code, a spawned pty stays open and is talked to. Two pages would have
 * duplicated the argv editor, the cwd field and the output pane, so they share one page, with the
 * one-shot command at the top and the long-lived terminal below it.
 *
 * The output of a command is never read from its answer: `command/exec` streams stdout and stderr
 * as output-delta notifications and returns only the final summary, so the pane is fed by
 * [AppServerClient.events] while the call is still in flight.
 *
 * That summary is also why correlation is partial. It carries the exit code, the duration and the
 * last of the output, but no process id — the protocol models `command/exec` as one call with one
 * answer, and only `process/spawn` names what it started. So the running command is tracked by what
 * the form was told: an id typed into the form filters deltas strictly, and with the field blank
 * every delta that arrives while a run is in flight belongs to it, because this page runs one
 * command at a time. The response's stdout and stderr are deliberately not appended to the pane:
 * for a streaming command they are the same bytes the deltas already delivered.
 *
 * A pty that outlives the page is exactly what `thread/backgroundTerminals/list` reports, which is
 * what [BackgroundTerminalsScreen] lists.
 */
@Composable
fun ExecCommandScreen(
    threadId: String,
    client: AppServerClient,
    shellPath: String,
    initialCwd: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val arguments =
        remember(shellPath) { mutableStateListOf(shellPath, "--noprofile", "--norc", "-c", "") }
    var cwd by remember { mutableStateOf(initialCwd) }
    var timeoutText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<ExecRunStatus>(ExecRunStatus.Idle) }
    var startedAt by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    // Two outputs, not one: the one-shot command and the pty are different processes, and mixing
    // their bytes would make the page unreadable the moment both are alive.
    var execOutput by remember { mutableStateOf("") }
    var execProcessId by remember { mutableStateOf<String?>(null) }
    var truncated by remember { mutableStateOf(false) }
    var stdinText by remember { mutableStateOf("") }
    var termStart by remember { mutableStateOf("$shellPath --noprofile --norc") }
    var termCwd by remember { mutableStateOf(initialCwd) }
    var terminalId by remember { mutableStateOf<String?>(null) }
    var terminalOutput by remember { mutableStateOf("") }
    var terminalExited by remember { mutableStateOf(false) }
    var rowsText by remember { mutableStateOf("24") }
    var colsText by remember { mutableStateOf("80") }
    var notice by remember { mutableStateOf<String?>(null) }
    // The elapsed readout is the only thing on this page that moves on its own; resolving it into
    // state here is what makes it redraw, because the time mark itself is not observable.
    var elapsedMs by remember { mutableStateOf(0L) }

    LaunchedEffect(client) {
        client.events.collect { event ->
            when (event) {
                is AppServerEvent.CommandExecOutput -> {
                    val delta = event.delta
                    val watching = execProcessId
                    if (watching != null && delta.processId == watching) {
                        // A delta with no bytes is the server acknowledging stdin, not output;
                        // echoing it would print the user's own input back at them.
                        if (delta.capReached) truncated = true
                        if (delta.deltaBase64.isNotEmpty()) {
                            execOutput += decodeOutput(delta.deltaBase64)
                        }
                    }
                }

                is AppServerEvent.ProcessOutputDelta -> {
                    val delta = event.delta
                    if (delta.processHandle == terminalId && delta.deltaBase64.isNotEmpty()) {
                        terminalOutput += decodeOutput(delta.deltaBase64)
                    }
                }

                // An exit notification is trusted only when it names a process this page issued:
                // the one it killed, or the pty. Anything else belongs to a turn, not to this page.
                is AppServerEvent.ProcessExited -> {
                    val delta = event.delta
                    if (delta.processHandle == terminalId) {
                        terminalExited = true
                    }
                    if (delta.processHandle == execProcessId) {
                        execProcessId = null
                    }
                }

                else -> Unit
            }
        }
    }

    LaunchedEffect(status) {
        if (status is ExecRunStatus.Running) {
            while (isActive) {
                elapsedMs = startedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L
                delay(250L)
            }
        } else {
            elapsedMs = startedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.exec_command_title),
            summary = stringResource(R.string.exec_command_subtitle),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.exec_command_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
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
            ExecCommandForm(
                arguments = arguments,
                cwd = cwd,
                onCwdChange = { cwd = it },
                timeoutText = timeoutText,
                onTimeoutChange = { timeoutText = it },
                running = status is ExecRunStatus.Running,
                onRun = {
                    val argv = arguments.toList()
                    if (argv.firstOrNull()?.isNotBlank() == true) {
                        execOutput = ""
                        truncated = false
                        notice = null
                        execProcessId = null
                        startedAt = TimeSource.Monotonic.markNow()
                        status = ExecRunStatus.Running
                        scope.launch {
                            val callStarted = TimeSource.Monotonic.markNow()
                            client
                                .execCommand(
                                    command = argv,
                                    cwd = cwd.trim().ifEmpty { null },
                                    timeoutMs = timeoutText.trim().toLongOrNull(),
                                )
                                .onSuccess { answer ->
                                    if (answer.stdout.isNotEmpty() || answer.stderr.isNotEmpty()) {
                                        execOutput = answer.stdout + answer.stderr
                                    }
                                    execProcessId = null
                                    status =
                                        ExecRunStatus.Finished(
                                            exitCode = answer.exitCode,
                                            durationMs =
                                                callStarted.elapsedNow().inWholeMilliseconds,
                                        )
                                }
                                .onFailure { failure ->
                                    execProcessId = null
                                    status = ExecRunStatus.Failed(failure.message.orEmpty())
                                }
                        }
                    }
                },
            )

            ExecOutputCard(
                status = status,
                elapsedMs = elapsedMs,
                output = execOutput,
                truncated = truncated,
                processId = execProcessId,
                stdinText = stdinText,
                onStdinChange = { stdinText = it },
                rowsText = rowsText,
                onRowsChange = { rowsText = it },
                colsText = colsText,
                onColsChange = { colsText = it },
                notice = notice,
                onWrite = { data, closeStdin ->
                    val id = execProcessId
                    if (id != null) {
                        scope.launch {
                            client
                                .execWrite(processId = id, data = data, closeStdin = closeStdin)
                                .onFailure { notice = it.message }
                        }
                    }
                },
                onResize = {
                    val id = execProcessId
                    val rows = rowsText.trim().toIntOrNull()
                    val cols = colsText.trim().toIntOrNull()
                    if (id != null && rows != null && cols != null) {
                        scope.launch {
                            client.execResize(id, rows, cols).onFailure { notice = it.message }
                        }
                    }
                },
                onTerminate = {
                    val id = execProcessId
                    if (id != null) {
                        scope.launch {
                            client.execTerminate(id).onFailure { notice = it.message }
                        }
                    }
                },
            )

            // The pty half. `process/spawn` names what it starts, so the handle the call returns is
            // what every control below addresses — unlike `command/exec`, whose response carries no
            // id at all.
            TerminalCard(
                start = termStart,
                onStartChange = { termStart = it },
                cwd = termCwd,
                onCwdChange = { termCwd = it },
                processId = terminalId,
                exited = terminalExited,
                output = terminalOutput,
                cwdFallback = initialCwd,
                onSpawn = {
                    val argv = splitCommandLine(termStart)
                    if (argv.isNotEmpty()) {
                        scope.launch {
                            notice = null
                            client
                                .spawnProcess(
                                    command = argv,
                                    cwd = termCwd.trim().ifEmpty { null },
                                    tty = true,
                                )
                                .onSuccess { handle ->
                                    terminalId = handle
                                    terminalOutput = ""
                                    terminalExited = false
                                }
                                .onFailure { failure -> notice = failure.message }
                        }
                    }
                },
                onKill = {
                    val id = terminalId
                    if (id != null) {
                        scope.launch {
                            client.killProcess(id).onFailure { failure -> notice = failure.message }
                        }
                    }
                },
                onWrite = { line ->
                    val id = terminalId
                    if (id != null) {
                        scope.launch {
                            client
                                .writeProcessStdin(id, line.toByteArray(), closeStdin = false)
                                .onFailure { failure -> notice = failure.message }
                        }
                    }
                },
                onCloseStdin = {
                    val id = terminalId
                    if (id != null) {
                        scope.launch {
                            client.writeProcessStdin(id, null, closeStdin = true).onFailure {
                                failure ->
                                notice = failure.message
                            }
                        }
                    }
                },
                onResizePty = { rows, cols ->
                    val id = terminalId
                    if (id != null) {
                        scope.launch {
                            client.resizeProcessPty(id, rows, cols).onFailure { failure ->
                                notice = failure.message
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * What one `command/exec` call is currently doing.
 *
 * The pane reports four different things — nothing yet, the call is in flight, it exited with a
 * code, it was refused — and three independent nullable fields could not say which one is true.
 */
private sealed interface ExecRunStatus {
    /** No command has been run on this page yet. */
    data object Idle : ExecRunStatus

    /** The call is in flight; deltas stream into the pane while this holds. */
    data object Running : ExecRunStatus

    /** The process exited and the response carried its code. */
    data class Finished(val exitCode: Int, val durationMs: Long) : ExecRunStatus

    /** The call itself failed, so nothing was started. */
    data class Failed(val reason: String) : ExecRunStatus
}

/** The argv editor, plus the two facts every command needs: where it runs, and how long it may. */
@Composable
private fun ExecCommandForm(
    arguments: MutableList<String>,
    cwd: String,
    onCwdChange: (String) -> Unit,
    timeoutText: String,
    onTimeoutChange: (String) -> Unit,
    running: Boolean,
    onRun: () -> Unit,
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
            title = stringResource(R.string.exec_command_form_title),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = arguments.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.exec_command_form_note),
            modifier = Modifier.padding(vertical = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        // One field per argument, because the protocol takes argv and never a shell string: a
        // quoted command line would have to be re-split here with rules a filename would break.
        arguments.forEachIndexed { index, argument ->
            if (index > 0) Spacer(Modifier.height(UiConsts.Space6))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.exec_command_arg_label, index + 1),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = argument,
                        onValueChange = { arguments[index] = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.exec_command_arg_placeholder),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                Spacer(Modifier.width(UiConsts.Space8))
                Button(
                    onClick = { if (arguments.size > 1) arguments.removeAt(index) },
                    modifier =
                        Modifier.squircleBorder(
                            UiConsts.OutlineThickness,
                            if (arguments.size > 1) MiuixTheme.colorScheme.error.copy(alpha = 0.5f)
                            else MiuixTheme.colorScheme.outline.copy(alpha = 0.18f),
                            UiConsts.ButtonHeightCompact / 2,
                        ),
                    enabled = arguments.size > 1,
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
                        text = stringResource(R.string.exec_command_arg_remove),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(UiConsts.Space8))
        Button(
            onClick = { arguments.add("") },
            colors = ButtonDefaults.buttonColors(),
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
                text = stringResource(R.string.exec_command_arg_add),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.exec_command_cwd_label),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            TextField(
                value = cwd,
                onValueChange = onCwdChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.exec_command_cwd_placeholder),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
        Spacer(Modifier.height(UiConsts.Space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.exec_command_timeout_label),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = timeoutText,
                    onValueChange = onTimeoutChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.exec_command_timeout_placeholder),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        Spacer(Modifier.height(UiConsts.Space10))
        Button(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
            enabled = !running && arguments.firstOrNull()?.isNotBlank() == true,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.exec_command_run),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The streamed pane, the exit summary, and the controls that exist while a process is alive. */
@Composable
private fun ExecOutputCard(
    status: ExecRunStatus,
    elapsedMs: Long,
    output: String,
    truncated: Boolean,
    processId: String?,
    stdinText: String,
    onStdinChange: (String) -> Unit,
    rowsText: String,
    onRowsChange: (String) -> Unit,
    colsText: String,
    onColsChange: (String) -> Unit,
    notice: String?,
    onWrite: (data: ByteArray, closeStdin: Boolean) -> Unit,
    onResize: () -> Unit,
    onTerminate: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val rows = rowsText.trim().toIntOrNull()
    val cols = colsText.trim().toIntOrNull()
    val stateLabel =
        when (status) {
            ExecRunStatus.Idle -> stringResource(R.string.exec_command_state_idle)
            ExecRunStatus.Running -> stringResource(R.string.exec_command_state_running)
            is ExecRunStatus.Finished -> {
                stringResource(R.string.exec_command_state_finished, status.exitCode)
            }

            is ExecRunStatus.Failed -> stringResource(R.string.exec_command_state_failed)
        }
    val stateTint: Color? =
        when (status) {
            ExecRunStatus.Idle -> null
            ExecRunStatus.Running -> colors.primary
            is ExecRunStatus.Finished -> if (status.exitCode == 0) successColor() else colors.error
            is ExecRunStatus.Failed -> colors.error
        }
    val duration =
        when (status) {
            is ExecRunStatus.Running -> {
                stringResource(R.string.exec_command_duration_running, elapsedMs)
            }

            is ExecRunStatus.Finished -> {
                stringResource(R.string.exec_command_duration_value, status.durationMs)
            }

            else -> ""
        }
    val exitCode = (status as? ExecRunStatus.Finished)?.exitCode?.toString().orEmpty()
    val processRow =
        when {
            processId != null -> processId
            status is ExecRunStatus.Running ->
                stringResource(R.string.exec_command_process_attached)
            status is ExecRunStatus.Idle -> ""
            else -> stringResource(R.string.exec_command_process_done)
        }

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
            title = stringResource(R.string.exec_command_output_title),
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
        BasicComponent(
            title = stringResource(R.string.exec_command_state_label),
            endActions = {
                Text(
                    text = (stateLabel).ifEmpty { "—" },
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.Detail,
                    lineHeight = UiType.DetailLine,
                    fontFamily = null,
                    color = stateTint ?: MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.exec_command_exit_label),
            endActions = {
                Text(
                    text = (exitCode).ifEmpty { "—" },
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.Detail,
                    lineHeight = UiType.DetailLine,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.exec_command_duration_label),
            endActions = {
                Text(
                    text = (duration).ifEmpty { "—" },
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.Detail,
                    lineHeight = UiType.DetailLine,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.exec_command_process_row_label),
            endActions = {
                Text(
                    text = (processRow).ifEmpty { "—" },
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.Detail,
                    lineHeight = UiType.DetailLine,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        )
        if (status is ExecRunStatus.Failed) {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            Text(
                text = status.reason,
                modifier =
                    Modifier.padding(
                        horizontal = UiConsts.Space4,
                        vertical = UiConsts.Space8,
                    ),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.error,
            )
        }
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(UiConsts.Space8))
            MonospacePane(text = output)
        }
        if (truncated) {
            Spacer(Modifier.height(UiConsts.Space6))
            Text(
                text = stringResource(R.string.exec_command_cap_reached),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = warningColor(),
            )
        }
        if (processId != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.exec_command_stdin_label),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = stdinText,
                    onValueChange = onStdinChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.exec_command_stdin_placeholder),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Button(
                    onClick = { onWrite(stdinText.toByteArray(), false) },
                    enabled = stdinText.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.exec_command_stdin_send),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onWrite(ByteArray(0), true) },
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.exec_command_stdin_close),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        text = stringResource(R.string.exec_command_terminate),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.exec_command_rows_label),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = rowsText,
                        onValueChange = onRowsChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = "",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Spacer(Modifier.width(UiConsts.Space8))
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.exec_command_cols_label),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = colsText,
                        onValueChange = onColsChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = "",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Spacer(Modifier.width(UiConsts.Space8))
                Button(
                    onClick = onResize,
                    enabled = rows != null && cols != null,
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.exec_command_resize),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (notice != null) {
            Spacer(Modifier.height(UiConsts.Space6))
            Text(
                text = notice,
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.error,
            )
        }
    }
}

/**
 * The pty half of the page: `process/spawn` and the three calls that keep it talking.
 *
 * The command here is one line rather than an argv row because a pty is where a shell line makes
 * sense — it is the thing that will interpret the pipes and the quotes — and [splitCommandLine] is
 * the one place that turns it back into argv.
 *
 * The pty has its own stdin and resize controls, and they are `process/writeStdin` and
 * `process/resizePty` rather than the `command/exec` pair above. The two families are separate on
 * the wire: a one-shot command is addressed by the id the caller supplied to `command/exec`, while
 * a spawned process is addressed by the id `process/spawn` *returned*. Sending a spawned process's
 * bytes through `command/exec/write` would name a request the server never saw, so the controls
 * cannot be shared however alike they look.
 */
@Composable
private fun TerminalCard(
    start: String,
    onStartChange: (String) -> Unit,
    cwd: String,
    onCwdChange: (String) -> Unit,
    processId: String?,
    exited: Boolean,
    output: String,
    cwdFallback: String,
    onSpawn: () -> Unit,
    onKill: () -> Unit,
    onWrite: (String) -> Unit,
    onCloseStdin: () -> Unit,
    onResizePty: (Int, Int) -> Unit,
) {
    var line by remember(processId) { mutableStateOf("") }
    var rows by remember(processId) { mutableStateOf("24") }
    var cols by remember(processId) { mutableStateOf("80") }
    val colors = MiuixTheme.colorScheme
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
            title = stringResource(R.string.exec_command_terminal_title),
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
            text = stringResource(R.string.exec_command_terminal_note),
            modifier = Modifier.padding(vertical = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.exec_command_terminal_start_label),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            TextField(
                value = start,
                onValueChange = onStartChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.exec_command_terminal_start_placeholder),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
        Spacer(Modifier.height(UiConsts.Space8))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.exec_command_terminal_cwd_label),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            TextField(
                value = cwd,
                onValueChange = onCwdChange,
                modifier = Modifier.fillMaxWidth(),
                label = cwdFallback,
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
        Spacer(Modifier.height(UiConsts.Space10))
        Button(
            onClick = onSpawn,
            modifier = Modifier.fillMaxWidth(),
            enabled = start.isNotBlank(),
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.exec_command_terminal_spawn),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.exec_command_terminal_process_label),
            endActions = {
                Text(
                    text = (processId.orEmpty()).ifEmpty { "—" },
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.Detail,
                    lineHeight = UiType.DetailLine,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            },
            insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        )
        if (processId != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Text(
                    text =
                        stringResource(
                            if (exited) {
                                R.string.exec_command_terminal_exited
                            } else {
                                R.string.exec_command_terminal_live
                            }
                        ),
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = if (exited) colors.onSurfaceVariantSummary else successColor(),
                )
                Button(
                    onClick = onKill,
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
                        text = stringResource(R.string.exec_command_terminal_kill),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (processId != null && !exited) {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.exec_command_terminal_stdin_label),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = line,
                    onValueChange = { line = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.exec_command_terminal_stdin_placeholder),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (line.isNotEmpty()) {
                                    onWrite(line + "\n")
                                    line = ""
                                }
                            },
                            onGo = {
                                if (line.isNotEmpty()) {
                                    onWrite(line + "\n")
                                    line = ""
                                }
                            },
                            onSend = {
                                if (line.isNotEmpty()) {
                                    onWrite(line + "\n")
                                    line = ""
                                }
                            },
                        ),
                )
            }
            Spacer(Modifier.height(UiConsts.Space6))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (line.isNotEmpty()) {
                            onWrite(line + "\n")
                            line = ""
                        }
                    },
                    enabled = line.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
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
                        text = stringResource(R.string.exec_command_terminal_stdin_send),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onCloseStdin,
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.exec_command_terminal_stdin_eof),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.exec_command_terminal_rows),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = rows,
                        onValueChange = { rows = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.exec_command_terminal_cols),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = cols,
                        onValueChange = { cols = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        val r = rows.toIntOrNull()
                        val c = cols.toIntOrNull()
                        // A pty with no size is a request the server can only reject, so a blank or
                        // zero field disables the button rather than sending one.
                        if (r != null && c != null && r > 0 && c > 0) onResizePty(r, c)
                    },
                    enabled = (rows.toIntOrNull() ?: 0) > 0 && (cols.toIntOrNull() ?: 0) > 0,
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.exec_command_terminal_resize),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(UiConsts.Space8))
            MonospacePane(text = output)
        }
        Spacer(Modifier.height(UiConsts.Space6))
        Text(
            text = stringResource(R.string.exec_command_terminal_hint),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * A read-only, monospace, scrollable pane of captured output.
 *
 * Both the command's stdout and the pty's stream go through it, so the two halves of the page
 * cannot end up with different leading, corner or gutter. It follows the tail as text arrives —
 * output that grows below the fold is output the user has to chase — and it scrolls sideways rather
 * than wrapping, because a wrapped log line stops being readable as a table.
 */
@Composable
private fun MonospacePane(text: String) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.CornerControl) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    // The pane is one frame behind the text that feeds it, so the scroll is issued from inside the
    // next frame and the extent is read in the snapshot that produced it. Reading `maxValue` here
    // would chase a value that is still the previous frame's, leaving the last line off screen.
    LaunchedEffect(vertical) {
        snapshotFlow { text }
            .collect {
                withFrameNanos {}
                val extent = Snapshot.withoutReadObservation { vertical.maxValue }
                if (extent > 0) vertical.scrollTo(extent)
            }
    }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = UiConsts.Space24, max = 240.dp)
                .clip(shape)
                .background(codeSurface())
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal)
                    .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space7)
        ) {
            Text(
                text = text,
                fontSize = UiType.Code,
                lineHeight = UiType.CodeLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                softWrap = false,
            )
        }
    }
}

/**
 * Decode one base64 output chunk, tolerating a codec that is not what the wire said.
 *
 * A delta is base64 on the wire (`deltaBase64`), but a transport that already put text in the field
 * can occur in terminal output, so it must not hide the rest of the response.
 */
private fun decodeOutput(base64: String): String = runCatching {
    Base64.getDecoder().decode(base64).decodeToString()
}
    .getOrElse { base64 }

/**
 * Split a shell-ish command line into argv for `process/spawn`.
 *
 * Quotes and backslash escapes are honoured because a pty command usually starts a shell, and a
 * path with a space in it has to survive the trip. This is not a shell: no expansion, no operators.
 */
private fun splitCommandLine(line: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false

    for (symbol in line) {
        when {
            escaped -> {
                current.append(symbol)
                escaped = false
                started = true
            }

            symbol == '\\' -> {
                escaped = true
                started = true
            }

            quote != null -> if (symbol == quote) quote = null else current.append(symbol)

            symbol == '"' || symbol == '\'' -> {
                quote = symbol
                started = true
            }

            symbol.isWhitespace() -> {
                if (started) {
                    arguments += current.toString()
                    current.clear()
                    started = false
                }
            }

            else -> {
                current.append(symbol)
                started = true
            }
        }
    }
    if (started) arguments += current.toString()
    return arguments
}
