package com.cy.codex.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.ModalSheet
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The agent roster and the agent picker.
 *
 * Subagents have no transcript of their own: they only appear as `CollabAgentToolCallItem` /
 * `SubAgentActivityItem` entries in the parent thread's item stream, so the roster is *derived*
 * by [deriveAgentRoster] instead of being read from a per-agent fixture.
 */

/** Whether an entry is the thread the user is looking at, or an agent it fanned work out to. */
enum class AgentRole {
    Main,
    Sub;

    /**
     * Short tag that marks which side of the collab relation the entry sits on.
     *
     * Composable because it is text: the tag is read from the roster's rows, which are composables.
     */
    val tag: String
        @Composable @ReadOnlyComposable get() = when (this) {
            Main -> stringResource(R.string.agents_overview_role_main)
            Sub -> stringResource(R.string.agents_overview_role_sub)
        }
}

/** One agent observed in the thread's item stream. */
data class AgentRosterEntry(
    val threadId: String,
    val name: String,
    val role: AgentRole,
    val status: AgentRunStatus?,
    val activity: SubAgentActivityKind?,
    val task: String?,
    val model: String?,
    val effort: ReasoningEffort?,
    val tokens: Int = 0,
    val itemId: String?,
    /**
     * Server-reported thread status from `thread/list` / `thread/status/changed`.
     *
     * Separate from [status]: that one is the collab tool's view of the agent's run, this one is the
     * thread's own liveness. A subagent that finished its collab run can still have a live thread,
     * and the dashboard prefers the thread's answer when the two disagree.
     */
    val threadStatus: ThreadStatus? = null,
)

/**
 * Fold the transcript into the roster: the main agent plus every agent the collab items mention.
 *
 * The main agent is always first and always present. Subagents follow in first-appearance order,
 * one entry per thread id seen in [CollabAgentToolCallItem.receiverThreadIds] or
 * [SubAgentActivityItem.agentThreadId]; a thread id equal to [mainThreadId] is never added twice.
 * Per agent, later items win: `status` from the newest collab `agentsStates` entry, `activity`
 * from the newest activity item, and `task` / `model` / `effort` from the newest collab item that
 * carries a non-null value for them. The main entry stays a placeholder: the item stream only
 * describes subagents, and session state owns the main thread's own status.
 *
 * Pure and total: any list, including malformed or partial items, yields a well-formed roster.
 *
 * The names it invents are passed in rather than looked up: the fold is a pure function with no
 * composable context, so the two callers resolve [mainLabel] and [subAgentNameFormat] from resources
 * and hand them over. [subAgentNameFormat] is a `%s`-style template, which keeps the naming rule
 * ("Subagent · <leaf>") in one place instead of in a second literal.
 */
fun deriveAgentRoster(
    items: List<ThreadItem>,
    mainThreadId: String,
    mainLabel: String,
    subAgentNameFormat: String,
): List<AgentRosterEntry> {
    val subagents = LinkedHashMap<String, AgentRosterEntry>()
    items.forEach { item ->
        when (item) {
            is CollabAgentToolCallItem -> item.receiverThreadIds.forEach { threadId ->
                if (threadId == mainThreadId) return@forEach
                val previous = subagents[threadId]
                val state = item.agentsStates[threadId]
                subagents[threadId] = AgentRosterEntry(
                    threadId = threadId,
                    name = previous?.name ?: defaultSubAgentName(threadId, subAgentNameFormat),
                    role = AgentRole.Sub,
                    status = state?.status ?: previous?.status,
                    activity = previous?.activity,
                    task = item.prompt ?: previous?.task,
                    model = item.model ?: previous?.model,
                    effort = item.reasoningEffort ?: previous?.effort,
                    tokens = previous?.tokens ?: 0,
                    itemId = item.id,
                )
            }

            is SubAgentActivityItem -> {
                val threadId = item.agentThreadId
                if (threadId != mainThreadId) {
                    val previous = subagents[threadId]
                    subagents[threadId] = AgentRosterEntry(
                        threadId = threadId,
                        name = subAgentName(item.agentPath, subAgentNameFormat) ?: previous?.name
                            ?: defaultSubAgentName(threadId, subAgentNameFormat),
                        role = AgentRole.Sub,
                        status = previous?.status,
                        activity = item.kind,
                        task = previous?.task,
                        model = previous?.model,
                        effort = previous?.effort,
                        tokens = previous?.tokens ?: 0,
                        itemId = item.id,
                    )
                }
            }

            else -> Unit
        }
    }
    val main = AgentRosterEntry(
        threadId = mainThreadId,
        name = mainLabel,
        role = AgentRole.Main,
        status = null,
        activity = null,
        task = null,
        model = null,
        effort = null,
        tokens = 0,
        itemId = null,
    )
    return listOf(main) + subagents.values
}

/** `Subagent · <leaf>` for a known agent path, `null` when the path carries no leaf segment. */
private fun subAgentName(agentPath: String, format: String): String? {
    val leaf = agentPath.trim().trimEnd('/').substringAfterLast('/').trim()
    return leaf.takeIf { it.isNotEmpty() }?.let { format.format(it) }
}

/** Fallback label for an agent the activity stream has not named yet. */
private fun defaultSubAgentName(threadId: String, format: String): String =
    threadId.takeLast(4).takeIf { it.isNotEmpty() }?.let { format.format(it) }
        ?: format.substringBefore('%').trim()

/**
 * Modal agent picker: the roster with a filter box on top, mirroring `bottom_pane/agent_picker.rs`.
 * Selecting a row reports the thread id; the caller decides which transcript to render.
 *
 * The picker and the dashboard ([AgentsOverview]) are the same sheet over the same roster. They
 * differ in what they are for — this one is a filter box and a list to choose from, that one is a
 * usage report — and in nothing else: both mount [ModalSheet] and both draw [AgentRosterRow].
 */
@Composable
fun AgentPickerSheet(
    show: Boolean,
    roster: List<AgentRosterEntry>,
    selectedThreadId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(roster, query) {
        val needle = query.trim()
        if (needle.isEmpty()) roster else roster.filter { it.matches(needle) }
    }
    val close = LocalDismissState.current
    ModalSheet(
        show = show,
        // Closing the picker is a decision, not an exit: the chosen thread is opening behind it, so
        // waiting for the sheet's own exit would hold the transcript back for a third of a second.
        onDismiss = {
            onDismiss()
            onDismissFinished()
        },
        onDismissFinished = onDismissFinished,
        title = stringResource(R.string.agent_picker_title),
        subtitle = stringResource(R.string.agent_picker_subtitle, roster.size),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            insideMargin = DpSize(UiConsts.Space12, UiConsts.Space8),
            label = stringResource(R.string.agent_picker_filter_hint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = UiConsts.Space12)
                        .size(UiConsts.IconRow),
                    tint = colors.onSurfaceVariantSummary,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.SearchCleanup,
                            contentDescription = stringResource(R.string.agent_picker_filter_clear),
                            modifier = Modifier.size(UiConsts.IconRow),
                            tint = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(UiConsts.Space8))
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.agent_picker_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = UiConsts.Space20),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        } else {
            filtered.forEach { entry ->
                AgentRosterRow(
                    entry = entry,
                    selected = entry.threadId == selectedThreadId,
                    onClick = {
                        onSelect(entry.threadId)
                        close?.invoke()
                    },
                )
            }
        }
    }
}

internal fun AgentRosterEntry.matches(needle: String): Boolean =
    name.contains(needle, true) || task?.contains(needle, true) == true || threadId.contains(needle, true)

/**
 * The agent roster folded out of the transcript, at most once per [SessionState.itemsRevision].
 *
 * The point of the dedicated type is what is *not* observed: the fold reads the revision and
 * nothing else, so an item write only schedules a recalculation, and the screen that reads the
 * roster is invalidated only when the folded value actually differs. During a turn the transcript
 * is written on every delta and the roster normally does not change at all, so none of those
 * writes recompose the caller.
 */
@Composable
internal fun rememberAgentRoster(
    session: SessionState,
    mainAgentLabel: String,
    subAgentNameFormat: String,
): List<AgentRosterEntry> {
    val state = remember(session, mainAgentLabel, subAgentNameFormat) {
        AgentRosterMemo(session, mainAgentLabel, subAgentNameFormat)
    }
    return state.roster
}

private class AgentRosterMemo(
    private val session: SessionState,
    private val mainAgentLabel: String,
    private val subAgentNameFormat: String,
) {
    private var revision = -1
    private var cached: List<AgentRosterEntry> = emptyList()
    private val state = derivedStateOf {
        val current = session.itemsRevision
        if (current != revision) {
            revision = current
            // The list itself is read without a read observer: the revision above is the memo's
            // only dependency, and the fold runs once per revision rather than once per reader.
            cached = Snapshot.withoutReadObservation {
                deriveAgentRoster(session.items, session.threadId, mainAgentLabel, subAgentNameFormat)
            }
        }
        cached
    }

    val roster: List<AgentRosterEntry> get() = state.value
}
