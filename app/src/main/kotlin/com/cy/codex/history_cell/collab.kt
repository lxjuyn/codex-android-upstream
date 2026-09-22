package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.CollabAgentState
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.ToolCard
import com.cy.codex.label
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.pressableRow
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Multi-agent work: a collab tool call and the one-line activity feed it produces.
 *
 * Mirrors the collab items the app-server streams (`CollabAgentToolCall`, `SubAgentActivity`):
 * every receiver thread is listed with the last state the server reported for it, and the prompt
 * that was handed to the agents stays visible as a quote.
 *
 * The rows are the way *into* a subagent. A subagent has no thread of its own to open from the
 * session list, so if the transcript — the one place it is mentioned — is not the way in, there is
 * no way in at all.
 */
@Composable
fun CollabToolCallCell(
    item: CollabAgentToolCallItem,
    modifier: Modifier = Modifier,
    agentNameOf: (String) -> String = { it },
    onOpenAgent: (String) -> Unit = {},
    onOpenAgentInfo: (String) -> Unit = {},
    chipSpacing: Dp = 6.dp,
    modelSpacing: Dp = 8.dp,
    emptyFontSize: TextUnit = UiType.Subtitle,
    emptyLineHeight: TextUnit = UiType.SheetTitle,
    agentSpacing: Dp = 7.dp,
    promptSpacing: Dp = 10.dp,
) {
    val colors = MiuixTheme.colorScheme
    val tone = collabCallTone(item.status)
    // Receivers keep the order the server sent; any extra agents in `agentsStates` follow, sorted,
    // so recomposition never reshuffles the list.
    val threadIds = remember(item.receiverThreadIds, item.agentsStates) {
        (item.receiverThreadIds + item.agentsStates.keys.sorted()).distinct()
    }
    val prompt = item.prompt?.trim().orEmpty()

    ToolCard(
        icon = MiuixIcons.Community,
        title = item.tool.label(),
        subtitle = stringResource(R.string.collab_cell_agent_count, threadIds.size),
        modifier = modifier,
        accent = statusDotColor(tone),
        trailing = { StatusChip(label = collabCallLabel(item.status), tone = tone) },
    ) {
        val model = item.model
        if (!model.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
                MetaChip(text = model)
                item.reasoningEffort?.let {
                    MetaChip(
                        text = stringResource(R.string.collab_cell_reasoning_effort, it.label()),
                    )
                }
            }
            Spacer(Modifier.height(modelSpacing))
        }
        if (threadIds.isEmpty()) {
            Text(
                text = stringResource(R.string.collab_cell_no_agents),
                fontSize = emptyFontSize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(agentSpacing)) {
                threadIds.forEach { threadId ->
                    AgentStateRow(
                        name = agentNameOf(threadId),
                        state = item.agentsStates[threadId],
                        onClick = { onOpenAgent(threadId) },
                        onLongClick = { onOpenAgentInfo(threadId) },
                    )
                }
            }
        }
        if (prompt.isNotEmpty()) {
            Spacer(Modifier.height(promptSpacing))
            QuotedBlock(text = prompt)
        }
    }
}

/** Compact agent-thread activity line: kind, path, state and the short thread id. */
@Composable
fun SubAgentActivityCell(
    item: SubAgentActivityItem,
    modifier: Modifier = Modifier,
    agentNameOf: (String) -> String = { it },
    onOpenAgent: (String) -> Unit = {},
    onOpenAgentInfo: (String) -> Unit = {},
    horizontalPadding: Dp = 4.dp,
    verticalPadding: Dp = 5.dp,
    statusIconSize: Dp = 13.dp,
    statusIconSpacing: Dp = 7.dp,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.Composer,
    labelSpacing: Dp = 6.dp,
    threadIdFontSize: TextUnit = UiType.Footnote,
    threadIdLineHeight: TextUnit = UiType.CardTitle,
    threadIdSpacing: Dp = 6.dp,
    chevronSpacing: Dp = 4.dp,
    chevronSize: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val tone = subAgentActivityTone(item.kind)
    val icon = when (item.kind) {
        SubAgentActivityKind.Started -> MiuixIcons.Play
        SubAgentActivityKind.Interacted -> MiuixIcons.Messages
        SubAgentActivityKind.Interrupted -> MiuixIcons.Pause
        SubAgentActivityKind.Completed -> MiuixIcons.Ok
    }
    val path = item.agentPath.ifBlank { agentNameOf(item.agentThreadId) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressableRow(
                shape = RoundedCornerShape(UiConsts.RowCorner),
                container = Color.Transparent,
                onClick = { onOpenAgent(item.agentThreadId) },
                onLongClick = { onOpenAgentInfo(item.agentThreadId) },
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(statusIconSize),
            tint = statusDotColor(tone),
        )
        Spacer(Modifier.width(statusIconSpacing))
        Text(
            text = stringResource(R.string.collab_cell_sub_agent_prefix),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Text(
            text = path,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(labelSpacing))
        Text(
            text = subAgentActivityLabel(item.kind),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = statusDotColor(tone),
            maxLines = 1,
        )
        Spacer(Modifier.width(threadIdSpacing))
        Text(
            text = shortThreadId(item.agentThreadId),
            fontSize = threadIdFontSize,
            lineHeight = threadIdLineHeight,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Spacer(Modifier.width(chevronSpacing))
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.collab_cell_open_sub_agent),
            modifier = Modifier.size(chevronSize),
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AgentStateRow(
    name: String,
    state: CollabAgentState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    horizontalPadding: Dp = 4.dp,
    verticalPadding: Dp = 4.dp,
    dotSize: Dp = 7.dp,
    dotSpacing: Dp = 9.dp,
    nameFontSize: TextUnit = UiType.RowTitle,
    nameLineHeight: TextUnit = UiType.RowTitleLine,
    statusSpacing: Dp = 6.dp,
    statusFontSize: TextUnit = UiType.Meta,
    statusLineHeight: TextUnit = UiType.Message,
    chevronSpacing: Dp = 4.dp,
    chevronSize: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val status = state?.status ?: AgentRunStatus.Running
    val tone = agentRunTone(status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = RoundedCornerShape(UiConsts.RowCorner),
                container = Color.Transparent,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(statusDotColor(tone)),
        )
        Spacer(Modifier.width(dotSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    fontSize = nameFontSize,
                    lineHeight = nameLineHeight,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(statusSpacing))
                Text(
                    text = agentRunLabel(status),
                    fontSize = statusFontSize,
                    lineHeight = statusLineHeight,
                    color = statusDotColor(tone),
                    maxLines = 1,
                )
                Spacer(Modifier.width(chevronSpacing))
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = stringResource(R.string.collab_cell_open_sub_agent),
                    modifier = Modifier.size(chevronSize),
                    tint = colors.onSurfaceVariantSummary,
                )
            }
            val message = state?.message
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    fontSize = statusFontSize,
                    lineHeight = statusLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Quote block used for the prompt handed to the agents. */
@Composable
private fun QuotedBlock(
    text: String,
    barWidth: Dp = 2.5.dp,
    corner: Dp = UiConsts.CornerBar,
    spacing: Dp = 10.dp,
    fontSize: TextUnit = UiType.Subtitle,
    lineHeight: TextUnit = UiType.RowTitleLine,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(corner))
                .background(colors.primary.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(spacing))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

internal fun collabCallTone(status: CollabAgentToolCallStatus): ThreadStatusTone = when (status) {
    CollabAgentToolCallStatus.InProgress -> ThreadStatusTone.Running
    CollabAgentToolCallStatus.Completed -> ThreadStatusTone.Done
    CollabAgentToolCallStatus.Failed -> ThreadStatusTone.Failed
    CollabAgentToolCallStatus.Interrupted -> ThreadStatusTone.Waiting
}

@Composable
@ReadOnlyComposable
internal fun collabCallLabel(status: CollabAgentToolCallStatus): String = when (status) {
    CollabAgentToolCallStatus.InProgress -> stringResource(R.string.collab_cell_call_status_running)
    CollabAgentToolCallStatus.Completed ->
        stringResource(R.string.collab_cell_call_status_completed)
    CollabAgentToolCallStatus.Failed -> stringResource(R.string.collab_cell_call_status_failed)
    CollabAgentToolCallStatus.Interrupted ->
        stringResource(R.string.collab_cell_call_status_interrupted)
}

internal fun agentRunTone(status: AgentRunStatus): ThreadStatusTone = when (status) {
    AgentRunStatus.PendingInit -> ThreadStatusTone.Waiting
    AgentRunStatus.Running -> ThreadStatusTone.Running
    AgentRunStatus.Interrupted -> ThreadStatusTone.Waiting
    AgentRunStatus.Completed -> ThreadStatusTone.Done
    AgentRunStatus.Errored -> ThreadStatusTone.Failed
    AgentRunStatus.Shutdown -> ThreadStatusTone.Idle
    AgentRunStatus.NotFound -> ThreadStatusTone.Failed
}

@Composable
@ReadOnlyComposable
internal fun agentRunLabel(status: AgentRunStatus): String = when (status) {
    AgentRunStatus.PendingInit -> stringResource(R.string.collab_cell_agent_status_pending_init)
    AgentRunStatus.Running -> stringResource(R.string.collab_cell_agent_status_running)
    AgentRunStatus.Interrupted -> stringResource(R.string.collab_cell_agent_status_interrupted)
    AgentRunStatus.Completed -> stringResource(R.string.collab_cell_agent_status_completed)
    AgentRunStatus.Errored -> stringResource(R.string.collab_cell_agent_status_errored)
    AgentRunStatus.Shutdown -> stringResource(R.string.collab_cell_agent_status_shutdown)
    AgentRunStatus.NotFound -> stringResource(R.string.collab_cell_agent_status_not_found)
}

private fun subAgentActivityTone(kind: SubAgentActivityKind): ThreadStatusTone = when (kind) {
    SubAgentActivityKind.Started -> ThreadStatusTone.Running
    SubAgentActivityKind.Interacted -> ThreadStatusTone.Running
    SubAgentActivityKind.Interrupted -> ThreadStatusTone.Waiting
    SubAgentActivityKind.Completed -> ThreadStatusTone.Done
}

@Composable
@ReadOnlyComposable
private fun subAgentActivityLabel(kind: SubAgentActivityKind): String = when (kind) {
    SubAgentActivityKind.Started -> stringResource(R.string.collab_cell_activity_started)
    SubAgentActivityKind.Interacted -> stringResource(R.string.collab_cell_activity_interacted)
    SubAgentActivityKind.Interrupted -> stringResource(R.string.collab_cell_activity_interrupted)
    SubAgentActivityKind.Completed -> stringResource(R.string.collab_cell_activity_completed)
}

private fun shortThreadId(threadId: String): String = threadId.take(8)
