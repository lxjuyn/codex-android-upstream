package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.status.formatTokens
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Source kinds a `thread/list` scoped to an agent's descendants should include. */
internal val SUB_AGENT_SOURCE_KINDS =
    listOf(
        "subAgent",
        "subAgentReview",
        "subAgentCompact",
        "subAgentThreadSpawn",
        "subAgentOther",
    )

/**
 * `/agents` and `/subagents` as a page.
 *
 * Mirrors `app/agents_overview.rs` with the parts that make sense on a phone: a filter box, one row
 * per roster entry, and the dashboard actions the terminal binds to keys — stop a running turn,
 * rename and archive. Usage comes from `thread/tokenUsage/updated`, which the app keeps per thread,
 * and liveness from a `thread/list` scoped by `ancestorThreadId`; that is what fills subagent
 * meters and status labels the transcript fold alone cannot know.
 */
@Composable
fun AgentsScreen(
    app: CodexApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val session = app.widget.state
    val mainLabel = stringResource(R.string.agent_roster_main_label)
    val nameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    val roster = rememberAgentRoster(session, mainLabel, nameFormat)
    val usage = app.catalog.threadUsage
    val agentThreads = app.catalog.agentThreads
    val listedThreads = app.threads.threads
    val entries =
        remember(roster, usage, agentThreads, listedThreads) {
            val threadsById = (agentThreads + listedThreads).associateBy { it.id }
            roster.map { agent ->
                agent.withThreadMetadata(threadsById[agent.threadId], usage[agent.threadId])
            }
        }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(entries, query) {
            val needle = query.trim()
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
    val busiest = filtered.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
    val totalTokens = entries.sumOf { it.tokens.toLong() }
    var renameTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }
    val renameAgent = {
        val target = renameTarget
        if (target != null && renameText.isNotBlank()) {
            app.onAppEvent(AppEvent.RenameThread(target.threadId, renameText.trim()))
            renameTarget = null
        }
    }

    // `thread/list` is the only source of subagent liveness and cli metadata, and it is a read the
    // page can need at any moment, so it is refreshed once per open rather than streamed.
    LaunchedEffect(session.threadId) {
        app.onAppEvent(AppEvent.ReloadAgentThreads(session.threadId))
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.agents_overview_title),
            summary =
                stringResource(
                    R.string.agents_screen_subtitle,
                    entries.size,
                    formatTokens(totalTokens),
                ),
            startAction = { AgentsBackButton(onBack) },
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.agents_screen_filter),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(UiConsts.Space2))
            if (filtered.isEmpty()) {
                Text(
                    text =
                        stringResource(
                            if (entries.isEmpty()) R.string.agents_overview_empty
                            else R.string.agent_picker_empty
                        ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space20),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
            } else {
                filtered.forEach { agent ->
                    AgentRosterRow(
                        entry = agent,
                        selected = agent.threadId == session.threadId,
                        onClick = { app.openThread(agent.threadId) },
                        tokens = agent.tokens,
                        busiestTokens = busiest,
                    )
                    AgentActions(
                        agent = agent,
                        onStop = { app.onAppEvent(AppEvent.StopThreadTurn(agent.threadId)) },
                        onRename = {
                            renameTarget = agent
                            renameText = agent.name
                        },
                        onArchive = { archiveTarget = agent },
                    )
                }
            }
        }
    }

    WindowBottomSheet(
        show = renameTarget != null,
        onDismissRequest = { renameTarget = null },
        onDismissFinished = {},
        title = stringResource(R.string.agents_rename_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = renameTarget?.name.orEmpty(),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.agents_rename_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = { renameAgent() },
                            onGo = { renameAgent() },
                            onSend = { renameAgent() },
                        ),
                )
                Spacer(Modifier.height(UiConsts.Space12))
                SheetActions(
                    confirm = stringResource(R.string.agents_rename_confirm),
                    confirmEnabled = renameText.isNotBlank(),
                    onConfirm = renameAgent,
                    onCancel = { renameTarget = null },
                )
            }
        }
    }

    WindowBottomSheet(
        show = archiveTarget != null,
        onDismissRequest = { archiveTarget = null },
        onDismissFinished = {},
        title = stringResource(R.string.agents_archive_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = archiveTarget?.name.orEmpty(),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Text(
                    text = stringResource(R.string.agents_archive_message),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space12))
                SheetActions(
                    confirm = stringResource(R.string.agents_archive_confirm),
                    destructive = true,
                    onConfirm = {
                        archiveTarget?.let {
                            app.onAppEvent(AppEvent.ArchiveThread(it.threadId, archived = true))
                        }
                        archiveTarget = null
                    },
                    onCancel = { archiveTarget = null },
                )
            }
        }
    }
}

/** Stop is offered only while the server calls the thread active; the rest are always available. */
private fun AgentRosterEntry.canStop(): Boolean =
    when {
        threadStatus != null -> threadStatus is ThreadStatus.Active
        else -> status == AgentRunStatus.Running || status == AgentRunStatus.PendingInit
    }

/**
 * The dashboard's per-row actions.
 *
 * The row itself opens the agent, because that is the one thing every tap should do; the less
 * frequent and more destructive actions sit under it, so nothing fires by accident.
 */
@Composable
private fun AgentActions(
    agent: AgentRosterEntry,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    val destructive = agent.role == AgentRole.Sub
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = UiConsts.Space16, bottom = UiConsts.Space4),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (agent.canStop()) {
            Button(
                onClick = onStop,
                modifier = Modifier,
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.agents_action_stop), maxLines = 1)
            }
        }
        Button(
            onClick = onRename,
            modifier = Modifier,
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.agents_action_rename), maxLines = 1)
        }
        if (destructive) {
            Button(
                onClick = onArchive,
                modifier = Modifier,
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.agents_action_archive), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SheetActions(
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8, Alignment.End),
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier,
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.agents_cancel), maxLines = 1)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier,
            enabled = confirmEnabled,
            colors =
                if (destructive) {
                    ButtonDefaults.buttonColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
        ) {
            Text(text = confirm, maxLines = 1)
        }
    }
}

@Composable
private fun AgentsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.agents_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}
