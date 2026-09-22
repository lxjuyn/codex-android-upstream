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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.ModalSheet
import com.cy.codex.R
import com.cy.codex.SurfaceHeader
import com.cy.codex.ButtonRole
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.CodexTextField
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.status.formatTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Source kinds a `thread/list` scoped to an agent's descendants should include. */
internal val SUB_AGENT_SOURCE_KINDS = listOf(
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
    val entries = remember(roster, usage, agentThreads, listedThreads) {
        val threadsById = (agentThreads + listedThreads).associateBy { it.id }
        roster.map { agent -> agent.withThreadMetadata(threadsById[agent.threadId], usage[agent.threadId]) }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(entries, query) {
        val needle = query.trim()
        if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
    }
    val busiest = filtered.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
    val totalTokens = entries.sumOf { it.tokens.toLong() }
    var renameTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }

    // `thread/list` is the only source of subagent liveness and cli metadata, and it is a read the
    // page can need at any moment, so it is refreshed once per open rather than streamed.
    LaunchedEffect(session.threadId) { app.onAppEvent(AppEvent.ReloadAgentThreads(session.threadId)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.agents_overview_title),
            subtitle = stringResource(R.string.agents_screen_subtitle, entries.size, formatTokens(totalTokens)),
            leading = { AgentsBackButton(onBack) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            CodexTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.agents_screen_filter),
                singleLine = true,
            )
            Spacer(Modifier.height(UiConsts.Space2))
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(
                        if (entries.isEmpty()) R.string.agents_overview_empty else R.string.agent_picker_empty,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = UiConsts.Space20),
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

    ModalSheet(
        show = renameTarget != null,
        onDismiss = { renameTarget = null },
        title = stringResource(R.string.agents_rename_title),
        subtitle = renameTarget?.name,
    ) {
        CodexTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = stringResource(R.string.agents_rename_label),
            onImeAction = {
                val target = renameTarget
                if (target != null && renameText.isNotBlank()) {
                    app.onAppEvent(AppEvent.RenameThread(target.threadId, renameText.trim()))
                    renameTarget = null
                }
            },
        )
        Spacer(Modifier.height(UiConsts.Space12))
        SheetActions(
            confirm = stringResource(R.string.agents_rename_confirm),
            confirmEnabled = renameText.isNotBlank(),
            onConfirm = {
                renameTarget?.let { app.onAppEvent(AppEvent.RenameThread(it.threadId, renameText.trim())) }
                renameTarget = null
            },
            onCancel = { renameTarget = null },
        )
    }

    ModalSheet(
        show = archiveTarget != null,
        onDismiss = { archiveTarget = null },
        title = stringResource(R.string.agents_archive_title),
        subtitle = archiveTarget?.name,
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
                archiveTarget?.let { app.onAppEvent(AppEvent.ArchiveThread(it.threadId, archived = true)) }
                archiveTarget = null
            },
            onCancel = { archiveTarget = null },
        )
    }
}

/** Stop is offered only while the server calls the thread active; the rest are always available. */
private fun AgentRosterEntry.canStop(): Boolean = when {
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
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = UiConsts.Space16, bottom = UiConsts.Space4),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (agent.canStop()) {
            CodexButton(
                text = stringResource(R.string.agents_action_stop),
                onClick = onStop,
                role = ButtonRole.Secondary,
                size = CodexButtonSize.Compact,
            )
        }
        CodexButton(
            text = stringResource(R.string.agents_action_rename),
            onClick = onRename,
            role = ButtonRole.Secondary,
            size = CodexButtonSize.Compact,
        )
        if (destructive) {
            CodexButton(
                text = stringResource(R.string.agents_action_archive),
                onClick = onArchive,
                role = ButtonRole.Secondary,
                size = CodexButtonSize.Compact,
            )
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
        CodexButton(
            text = stringResource(R.string.agents_cancel),
            onClick = onCancel,
            role = ButtonRole.Secondary,
        )
        CodexButton(
            text = confirm,
            onClick = onConfirm,
            role = if (destructive) ButtonRole.Destructive else ButtonRole.Primary,
            enabled = confirmEnabled,
        )
    }
}

@Composable
private fun AgentsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.agents_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}
