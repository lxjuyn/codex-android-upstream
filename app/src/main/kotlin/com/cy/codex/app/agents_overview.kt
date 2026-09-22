package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.status.formatTokens
import com.cy.codex.statusDotColor
import com.cy.codex.tone
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * Full-screen agent dashboard, plus the roster presentation helpers the picker shares with it.
 *
 * Mirrors `app/agents_overview.rs`: one card per entry of the derived roster, so the screen shows
 * exactly the agents the thread's collab items mention — never a fixture list. Tapping a card
 * selects that agent and closes the dashboard.
 *
 * Token bars are relative: the widest bar is the busiest agent, and the main agent's own usage is
 * the thread total the caller passes in. Per-agent usage is unknown to the item stream, so an agent
 * without usage renders an empty track instead of an invented number.
 */
@Composable
fun AgentsOverview(
    show: Boolean,
    roster: List<AgentRosterEntry>,
    activeThreadId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    totalTokens: Long = 0L,
) {
    val colors = MiuixTheme.colorScheme
    val busiest = remember(roster) { roster.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1 }
    WindowBottomSheet(
        show = show,
        onDismissRequest = {
            onDismiss()
            onDismissFinished()
        },
        onDismissFinished = onDismissFinished,
        title = stringResource(R.string.agents_overview_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        val close = LocalDismissState.current
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFractionTall
                    )
        ) {
            Text(
                text =
                    stringResource(
                        R.string.agents_overview_subtitle,
                        roster.size,
                        formatTokens(totalTokens),
                    ),
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
                if (roster.isEmpty()) {
                    Text(
                        text = stringResource(R.string.agents_overview_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space20),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                } else {
                    roster.forEach { agent ->
                        AgentRosterRow(
                            entry = agent,
                            selected = agent.threadId == activeThreadId,
                            onClick = {
                                onSelect(agent.threadId)
                                close?.invoke()
                            },
                            tokens = agent.tokens,
                            busiestTokens = busiest,
                        )
                    }
                }
            }
        }
    }
}

/** Label for a server agent state, in the wording the TUI dashboard uses. */
@Composable
@ReadOnlyComposable
internal fun AgentRunStatus.label(): String =
    when (this) {
        AgentRunStatus.PendingInit -> stringResource(R.string.agents_overview_status_pending_init)
        AgentRunStatus.Running -> stringResource(R.string.agents_overview_status_running)
        AgentRunStatus.Interrupted -> stringResource(R.string.agents_overview_status_interrupted)
        AgentRunStatus.Completed -> stringResource(R.string.agents_overview_status_completed)
        AgentRunStatus.Errored -> stringResource(R.string.agents_overview_status_errored)
        AgentRunStatus.Shutdown -> stringResource(R.string.agents_overview_status_shutdown)
        AgentRunStatus.NotFound -> stringResource(R.string.agents_overview_status_not_found)
    }

/** Label for a subagent activity event. */
@Composable
@ReadOnlyComposable
internal fun SubAgentActivityKind.label(): String =
    when (this) {
        SubAgentActivityKind.Started -> stringResource(R.string.agents_overview_activity_started)
        SubAgentActivityKind.Interacted ->
            stringResource(R.string.agents_overview_activity_interacted)
        SubAgentActivityKind.Interrupted ->
            stringResource(R.string.agents_overview_activity_interrupted)
        SubAgentActivityKind.Completed ->
            stringResource(R.string.agents_overview_activity_completed)
    }

/**
 * What the row should say. The collab `agentsStates` map is the server's own last word on an agent,
 * so it wins over the coarser activity event; activity only fills the gap before the first collab
 * update arrives. A thread status from `thread/list` is preferred over activity too: it is the
 * thread's own liveness, not a one-off progress event.
 */
@Composable
@ReadOnlyComposable
internal fun AgentRosterEntry.statusLabel(): String =
    when {
        status != null -> status.label()
        threadStatus != null -> threadStatus.label()
        activity != null -> activity.label()
        role == AgentRole.Main -> stringResource(R.string.agents_overview_status_main_thread)
        else -> stringResource(R.string.agents_overview_status_idle)
    }

/** Colour tone of an agent, used for its status dot and label. */
internal fun AgentRosterEntry.tone(): ThreadStatusTone =
    when {
        activity == SubAgentActivityKind.Interrupted -> ThreadStatusTone.Failed
        status == AgentRunStatus.Errored || status == AgentRunStatus.NotFound ->
            ThreadStatusTone.Failed
        status == AgentRunStatus.Completed -> ThreadStatusTone.Done
        status == AgentRunStatus.Running -> ThreadStatusTone.Running
        status == AgentRunStatus.PendingInit -> ThreadStatusTone.Waiting
        status == AgentRunStatus.Interrupted -> ThreadStatusTone.Waiting
        status == AgentRunStatus.Shutdown -> ThreadStatusTone.Idle
        threadStatus != null -> threadStatus.tone()
        activity == SubAgentActivityKind.Completed -> ThreadStatusTone.Done
        activity != null -> ThreadStatusTone.Running
        else -> ThreadStatusTone.Idle
    }

/**
 * Fold server-side per-thread facts into one roster row.
 *
 * The transcript fold only sees collab items; `thread/list` (scoped by `ancestorThreadId`) and
 * `thread/tokenUsage/updated` are the only sources for an agent's liveness and lifetime usage, so
 * every overview surface applies them here rather than inventing numbers in the fold itself.
 */
internal fun AgentRosterEntry.withThreadMetadata(
    thread: Thread?,
    usage: ThreadTokenUsage?,
): AgentRosterEntry =
    copy(
        threadStatus = thread?.status ?: threadStatus,
        tokens = (usage?.total?.totalTokens ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    )

/** Small round agent/environment status dot shared by the overview and the picker. */
@Composable
internal fun StatusDot(tone: ThreadStatusTone, size: Dp = UiConsts.DotSize) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(statusDotColor(tone)))
}

/** "主" / "子" tag that marks which side of the collab relation an entry sits on. */
@Composable
internal fun RoleTag(role: AgentRole) {
    val colors = MiuixTheme.colorScheme
    val accent = if (role == AgentRole.Main) colors.primary else colors.onSurfaceVariantSummary
    Text(
        text = role.tag,
        modifier =
            Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
        fontSize = UiType.Badge,
        lineHeight = UiType.BadgeLine,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 1,
    )
}
