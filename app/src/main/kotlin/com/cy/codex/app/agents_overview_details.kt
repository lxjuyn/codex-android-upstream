package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.history_cell.collabCallTone
import com.cy.codex.label
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One subagent, as a page.
 *
 * A subagent never gets a thread of its own on the server, so there is nothing to `thread/read` and
 * nothing for the session list to show. Everything on this page is therefore folded back out of the
 * *parent* transcript: the roster entry (task, model, effort, last reported state) and the ordered
 * list of items that mention this agent's thread id. That is the same evidence the roster itself is
 * derived from, so the page can never contradict the row that opened it.
 */
@Composable
fun SubAgentScreen(
    threadId: String,
    items: List<ThreadItem>,
    mainThreadId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val mainAgentLabel = stringResource(R.string.agent_roster_main_label)
    val subAgentNameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    val entry =
        remember(items, threadId, mainThreadId, mainAgentLabel, subAgentNameFormat) {
            deriveAgentRoster(items, mainThreadId, mainAgentLabel, subAgentNameFormat).firstOrNull {
                it.threadId == threadId
            }
        }
    val timeline = remember(items, threadId) { deriveSubAgentTimeline(items, threadId) }
    val name =
        entry?.name ?: stringResource(R.string.sub_agent_screen_fallback_name, threadId.takeLast(4))

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = name,
            summary =
                if (entry == null) stringResource(R.string.sub_agent_screen_missing)
                else entry.statusLabel(),
            startAction = { SubAgentBackButton(onBack) },
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
            SubAgentTaskCard(entry)
            SubAgentRunCard(entry, threadId, mainThreadId)
            SubAgentTimelineCard(timeline)
        }
    }
}

/** The prompt the parent handed this agent. */
@Composable
private fun SubAgentTaskCard(entry: AgentRosterEntry?) {
    val colors = MiuixTheme.colorScheme
    val task = entry?.task?.takeIf { it.isNotBlank() }
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
            title = stringResource(R.string.sub_agent_section_task),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        if (task == null) {
            Text(
                text = stringResource(R.string.sub_agent_screen_task_empty),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier =
                        Modifier.width(UiConsts.TimelineBarWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(UiConsts.Space2))
                            .background(colors.primary.copy(alpha = 0.45f))
                )
                Spacer(Modifier.width(UiConsts.Space10))
                Text(
                    text = task,
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Quote,
                    lineHeight = UiType.QuoteLine,
                    color = colors.onSurface,
                )
            }
        }
    }
}

/** Identity and the last thing the server said about this agent. */
@Composable
private fun SubAgentRunCard(entry: AgentRosterEntry?, threadId: String, mainThreadId: String) {
    val colors = MiuixTheme.colorScheme
    val tone = entry?.tone() ?: ThreadStatusTone.Idle
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
            title = stringResource(R.string.sub_agent_section_run),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MiuixIcons.Community,
                null,
                Modifier.size(UiConsts.IconHeader),
                statusDotColor(tone),
            )
            Spacer(Modifier.width(UiConsts.Space9))
            Text(
                text = entry?.statusLabel() ?: stringResource(R.string.sub_agent_screen_not_found),
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = statusDotColor(tone),
            )
            Text(
                text =
                    stringResource(
                        R.string.sub_agent_screen_role_badge,
                        (entry?.role ?: AgentRole.Sub).tag,
                    ),
                modifier =
                    Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                        .background(colors.primary.copy(alpha = 0.14f))
                        .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
                fontSize = UiType.Badge,
                lineHeight = UiType.BadgeLine,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(UiConsts.Space8))
        SubAgentInfoLine(
            stringResource(R.string.sub_agent_screen_agent_thread),
            threadId,
            mono = true,
        )
        SubAgentInfoLine(
            stringResource(R.string.sub_agent_screen_main_thread),
            mainThreadId,
            mono = true,
        )
        if (entry?.model != null) {
            SubAgentInfoLine(stringResource(R.string.sub_agent_screen_model), entry.model)
        }
        if (entry?.effort != null) {
            SubAgentInfoLine(stringResource(R.string.sub_agent_screen_effort), entry.effort.label())
        }
        if (entry?.itemId != null) {
            SubAgentInfoLine(
                stringResource(R.string.sub_agent_screen_source_item),
                entry.itemId,
                mono = true,
            )
        }
    }
}

/** Everything the parent transcript ever said about this agent, in stream order. */
@Composable
private fun SubAgentTimelineCard(events: List<SubAgentEvent>) {
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
            title = stringResource(R.string.sub_agent_section_activity),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Tasks,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = stringResource(R.string.sub_agent_screen_activity_count, events.size),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.sub_agent_screen_activity_empty),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
            )
            return@Card
        }
        Column(verticalArrangement = Arrangement.spacedBy(UiConsts.Space9)) {
            events.forEach { event ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        modifier =
                            Modifier.width(UiConsts.TimelineBarWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(UiConsts.Space2))
                                .background(statusDotColor(event.tone).copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(UiConsts.Space10))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.title(),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        event.detail?.let { detail ->
                            Text(
                                text = detail,
                                modifier = Modifier.padding(top = UiConsts.Space2),
                                fontSize = UiType.Detail,
                                lineHeight = UiType.DetailLine,
                                color = colors.onSurfaceVariantSummary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = event.id,
                            modifier = Modifier.padding(top = UiConsts.Space2),
                            fontSize = UiType.Caption,
                            lineHeight = UiType.CaptionLine,
                            fontFamily = FontFamily.Monospace,
                            color = colors.disabledOnSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubAgentInfoLine(
    label: String,
    value: String,
    mono: Boolean = false,
    labelWidth: Dp = 78.dp,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = UiConsts.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(UiConsts.RowCorner))
                    .background(codeSurface())
                    .padding(horizontal = UiConsts.Space7, vertical = UiConsts.Space3),
            fontSize = UiType.Value,
            lineHeight = UiType.ValueLine,
            fontFamily = if (mono) FontFamily.Monospace else null,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubAgentBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            MiuixIcons.ChevronBackward,
            stringResource(R.string.sub_agent_screen_back),
            Modifier.size(UiConsts.IconHeader),
            MiuixTheme.colorScheme.primary,
        )
    }
}

/**
 * One line of a subagent's history, folded out of the parent transcript.
 *
 * The row title is not stored here: a collab call and an activity item each name the enum they came
 * from, and [title] resolves the text where resources are available.
 */
data class SubAgentEvent(
    val id: String,
    val tool: CollabAgentTool?,
    val activity: SubAgentActivityKind?,
    val detail: String?,
    val tone: ThreadStatusTone,
)

/** The row's title: the collab tool that was called, or the activity kind that was reported. */
@Composable
@ReadOnlyComposable
private fun SubAgentEvent.title(): String =
    when {
        tool != null -> stringResource(R.string.sub_agent_screen_collab_call, tool.label())
        activity != null -> activity.timelineLabel()
        // A fold that names neither is a bug, not a state; the id keeps the row readable either
        // way.
        else -> id
    }

/**
 * Fold the parent transcript down to everything that names [threadId].
 *
 * Two item kinds can: a `CollabAgentToolCallItem` that lists the agent as a receiver (the request,
 * the prompt and the last state the server reported for it), and a `SubAgentActivityItem` addressed
 * to it (started / interacted / interrupted / completed). Order is stream order, which is the order
 * the parent saw them in; the id is the item id, so the list is stable across recompositions.
 *
 * Pure and total: an unknown agent simply yields an empty list, which is what the page shows when
 * the roster no longer mentions it.
 */
fun deriveSubAgentTimeline(items: List<ThreadItem>, threadId: String): List<SubAgentEvent> =
    items.mapNotNull { item ->
        when (item) {
            is CollabAgentToolCallItem -> {
                if (threadId !in item.receiverThreadIds && threadId !in item.agentsStates.keys) {
                    null
                } else {
                    val state = item.agentsStates[threadId]
                    SubAgentEvent(
                        id = item.id,
                        tool = item.tool,
                        activity = null,
                        detail =
                            state?.message?.takeIf { it.isNotBlank() }
                                ?: item.prompt?.takeIf { it.isNotBlank() },
                        tone = collabCallTone(item.status),
                    )
                }
            }

            is SubAgentActivityItem -> {
                if (item.agentThreadId != threadId) {
                    null
                } else {
                    SubAgentEvent(
                        id = item.id,
                        tool = null,
                        activity = item.kind,
                        detail = item.agentPath.takeIf { it.isNotBlank() },
                        tone = item.kind.timelineTone(),
                    )
                }
            }

            else -> null
        }
    }

@Composable
@ReadOnlyComposable
private fun SubAgentActivityKind.timelineLabel(): String =
    stringResource(
        when (this) {
            SubAgentActivityKind.Started -> R.string.sub_agent_screen_started
            SubAgentActivityKind.Interacted -> R.string.sub_agent_screen_interacted
            SubAgentActivityKind.Interrupted -> R.string.sub_agent_screen_interrupted
            SubAgentActivityKind.Completed -> R.string.sub_agent_screen_completed
        }
    )

private fun SubAgentActivityKind.timelineTone(): ThreadStatusTone =
    when (this) {
        SubAgentActivityKind.Started -> ThreadStatusTone.Running
        SubAgentActivityKind.Interacted -> ThreadStatusTone.Running
        SubAgentActivityKind.Interrupted -> ThreadStatusTone.Waiting
        SubAgentActivityKind.Completed -> ThreadStatusTone.Done
    }
