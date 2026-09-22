package com.cy.codex.external_agent_config_migration

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.CodexSwitchRow
import com.cy.codex.EmptyState
import com.cy.codex.ImportProgress
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportHistory
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Bringing another agent's configuration into Codex: `externalAgentConfig/…`.
 *
 * Mirrors the `codex-rs/tui/src/external_agent_config_migration/` module, where a migration is a
 * conversation in three steps rather than one command. The phone keeps all three on one page
 * because each step is the input to the next: a user who cannot see what was found cannot decide
 * what to bring over.
 *
 * Detection is a *read* and the import is the only write on the page, which is why the first card
 * says so in as many words. The two buttons sit one tap apart and only one of them changes
 * anything, so the copy has to carry the difference rather than the layout.
 *
 * The page asks for its own data on the way in. Neither the detected items nor the history are in
 * [CatalogState] until something reads them, and a page that opened on an empty list with a
 * "detect" button would read as a page that had already answered.
 *
 * @param catalog the catalog the page reads: what detection found, the import history, and the
 *   progress of an import that is running right now.
 * @param onEvent where the page's events go — [AppEvent.ReloadExternalAgentConfig] on entry,
 *   [AppEvent.DetectExternalAgentConfig] and [AppEvent.ImportExternalAgentConfig].
 * @param onBack closes the page.
 * @param modifier layout modifier for the page frame.
 */
@Composable
fun ExternalAgentImportScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val items = catalog.externalAgentConfig
    val histories = catalog.externalAgentImportHistories
    // The protocol has no per-item id: an import sends the detected items back whole, so selection
    // is keyed by what identifies one item — type, scope and description.
    val selection = remember(items) {
        mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it.selectionKey(), true) } }
    }
    val selected = items.filter { selection[it.selectionKey()] == true }

    // One event covers both reads — detection and the import history — and the app runs them as two
    // independent requests, so a detection that fails still leaves the history on screen.
    LaunchedEffect(Unit) { onEvent(AppEvent.ReloadExternalAgentConfig) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.migration_screen_title),
            subtitle = stringResource(R.string.migration_screen_subtitle, items.size),
            leading = { SurfaceBackButton(stringResource(R.string.migration_screen_back), onBack) },
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
            MigrationDetectCard(onEvent = onEvent)
            MigrationSelectionCard(
                items = items,
                selected = selection,
                selectedCount = selected.size,
                onToggle = { key, checked -> selection[key] = checked },
                onSelectAll = { value -> items.forEach { selection[it.selectionKey()] = value } },
                onImport = { onEvent(AppEvent.ImportExternalAgentConfig(selected)) },
            )
            val progress = catalog.externalAgentImport
            if (progress != null) {
                MigrationProgressCard(progress = progress)
            }
            MigrationHistoryCard(histories = histories)
        }
    }
}

/**
 * The identity of one detected item inside this page.
 *
 * The description is part of the key because one working directory can produce several items of the
 * same type — a `SKILLS` row per skill — and keying on the type alone would make selecting one
 * select all of them.
 */
private fun ExternalAgentConfigMigrationItem.selectionKey(): String =
    "$itemType\u0000${cwd.orEmpty()}\u0000$description"

/**
 * The detect step, and the sentence that makes it safe to press.
 *
 * Its button is [ButtonRole.Secondary] on purpose: detection is not what the page was opened to do,
 * and a read that writes nothing should not compete with the import for the page's one filled pill.
 */
@Composable
private fun MigrationDetectCard(onEvent: (AppEvent) -> Unit) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.migration_detect_section),
        icon = MiuixIcons.ConvertFile,
    ) {
        // The guarantee gets a surface of its own rather than another paragraph: "this changes
        // nothing" is the one thing a user has to believe before pressing a button two rows above
        // the one that does change something.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.Space4)
                .clip(remember { RoundedCornerShape(UiConsts.CornerRow) })
                .background(codeSurface())
                .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space7),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconInline),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(UiConsts.Space7))
            Text(
                text = stringResource(R.string.migration_detect_note),
                modifier = Modifier.weight(1f),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(UiConsts.Space10))
        CodexButton(
            text = stringResource(R.string.migration_detect_action),
            onClick = { onEvent(AppEvent.DetectExternalAgentConfig) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.Space4),
            role = ButtonRole.Secondary,
        )
    }
}

/**
 * The choose step: what detection found, one switch per item, and the import.
 *
 * The draft selection lives here rather than in the catalog because it is exactly that — a draft.
 * Putting it back into [CatalogState] would make a half-made choice look like server state and
 * would be wrong the moment a second detection arrived.
 *
 * The import sits under the list rather than in the progress card below it. The progress card
 * exists only while an import runs, so an action living there would vanish with it — taking the
 * page's only write away at the exact moment it became available again.
 */
@Composable
private fun MigrationSelectionCard(
    items: List<ExternalAgentConfigMigrationItem>,
    selected: Map<String, Boolean>,
    selectedCount: Int,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.migration_selection_section),
        icon = MiuixIcons.Tasks,
        trailing = items.size.toString(),
    ) {
        if (items.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.ConvertFile,
                title = stringResource(R.string.migration_selection_empty),
                detail = stringResource(R.string.migration_selection_empty_detail),
            )
        }
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The two bulk actions are enabled only when they would change something: a pill
                // that cannot do anything is how a list ends up looking broken rather than settled.
                CodexButton(
                    text = stringResource(R.string.migration_selection_select_all),
                    onClick = { onSelectAll(true) },
                    size = CodexButtonSize.Compact,
                    role = ButtonRole.Secondary,
                    enabled = items.any { selected[it.selectionKey()] != true },
                )
                Spacer(Modifier.width(UiConsts.Space6))
                CodexButton(
                    text = stringResource(R.string.migration_selection_select_none),
                    onClick = { onSelectAll(false) },
                    size = CodexButtonSize.Compact,
                    role = ButtonRole.Secondary,
                    enabled = items.any { selected[it.selectionKey()] == true },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.migration_selection_count, selectedCount),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(UiConsts.Space6))
        }
        items.forEachIndexed { index, item ->
            if (index > 0) CodexDivider()
            // The type first because it is the protocol's own word for what the item is, and the
            // description is the server's sentence about it; a row showing only one of the two
            // either repeats the title or says nothing about what would be copied.
            val kind = migrationItemTypeLabel(item.itemType)
            val subtitle = when {
                item.description.isBlank() -> item.cwd ?: kind
                item.cwd == null -> "$kind · ${item.description}"
                else -> "$kind · ${item.description} · ${item.cwd}"
            }
            CodexSwitchRow(
                title = item.description.ifBlank { kind },
                subtitle = subtitle,
                checked = selected[item.selectionKey()] == true,
                onCheckedChange = { onToggle(item.selectionKey(), it) },
            )
        }
        Spacer(Modifier.height(UiConsts.Space10))
        CodexButton(
            text = stringResource(R.string.migration_selection_import),
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.Space4),
            enabled = selectedCount > 0,
        )
    }
}

/** The server's uppercase item type, as a label the page can show. */
@Composable
@ReadOnlyComposable
private fun migrationItemTypeLabel(type: String): String = stringResource(
    when (type) {
        "AGENTS_MD" -> R.string.migration_kind_agents_md
        "CONFIG" -> R.string.migration_kind_config
        "SKILLS" -> R.string.migration_kind_skills
        "PLUGINS" -> R.string.migration_kind_plugins
        "MCP_SERVER_CONFIG" -> R.string.migration_kind_mcp_server_config
        "SUBAGENTS" -> R.string.migration_kind_subagents
        "HOOKS" -> R.string.migration_kind_hooks
        "COMMANDS" -> R.string.migration_kind_commands
        "MEMORY" -> R.string.migration_kind_memory
        "SESSIONS" -> R.string.migration_kind_sessions
        else -> R.string.migration_kind_unknown
    },
)

/**
 * What a running import is doing.
 *
 * Composed only while [CatalogState.externalAgentImport] is non-null, so the card's presence *is*
 * the "an import is running" state — there is no second boolean that could disagree with it and
 * leave a finished bar on screen.
 */
@Composable
private fun MigrationProgressCard(progress: ImportProgress) {
    val colors = MiuixTheme.colorScheme
    // A total of zero is a server reporting progress before it knew the size. Clamping rather than
    // dividing keeps the bar empty instead of drawing NaN across the card.
    val fraction = if (progress.total > 0) {
        (progress.imported.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    SectionCard(
        title = stringResource(R.string.migration_progress_section),
        icon = MiuixIcons.Stopwatch,
        trailing = stringResource(
            R.string.migration_progress_count,
            progress.imported,
            progress.total,
        ),
    ) {
        Text(
            text = progress.label,
            modifier = Modifier.padding(horizontal = UiConsts.Space4),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.Space4),
            progress = fraction,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = colors.primary,
                backgroundColor = colors.onSurface.copy(alpha = 0.08f),
            ),
            height = UiConsts.ProgressHeight,
        )
    }
}

/**
 * The audit trail: every import the server has recorded, newest first.
 *
 * Read-only: a history row is the server's record of a completed import, and the page that would
 * add one by hand is not this one — `externalAgentConfig/import/recordHistory` takes per-item-type
 * results, not a free-form note.
 */
@Composable
private fun MigrationHistoryCard(histories: List<ExternalAgentConfigImportHistory>) {
    val ordered = remember(histories) { histories.sortedByDescending { it.completedAtMs } }
    SectionCard(
        title = stringResource(R.string.migration_history_section),
        icon = MiuixIcons.Notes,
        trailing = histories.size.toString(),
    ) {
        if (ordered.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Notes,
                title = stringResource(R.string.migration_history_empty),
                detail = stringResource(R.string.migration_history_empty_detail),
            )
        }
        ordered.forEachIndexed { index, history ->
            if (index > 0) CodexDivider()
            MigrationHistoryRow(history = history)
        }
    }
}

/**
 * One recorded import.
 *
 * A read-only row rather than an ActionRow: there is nothing behind a history entry to open, and a
 * chevron would promise one. The timestamp is the only ordering key the row has, so it is rendered
 * in the user's locale and time zone instead of as the epoch milliseconds it arrives as.
 */
@Composable
private fun MigrationHistoryRow(history: ExternalAgentConfigImportHistory) {
    val colors = MiuixTheme.colorScheme
    val stamp = migrationTimeLabel(history.completedAtMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
    ) {
        Text(
            text = history.providerId ?: history.importId,
            fontSize = UiType.RowTitle,
            lineHeight = UiType.RowTitleLine,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(UiConsts.Space2))
        Text(
            text = stringResource(
                R.string.migration_history_counts,
                history.successes.size,
                history.failures.size,
            ),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        if (stamp != null) {
            Spacer(Modifier.height(UiConsts.Space2))
            Text(
                text = stamp,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

/**
 * A history row's timestamp, or `null` when the server sent none.
 *
 * `0` is the field's default on the wire, and formatting it would print 1970 under a row whose own
 * contents say it happened much later; a missing stamp is better than a wrong one.
 *
 * [ReadOnlyComposable] because the helper emits nothing of its own — it resolves a pattern resource
 * and formats a date with it — so it needs no composition group of its own.
 */
@Composable
@ReadOnlyComposable
private fun migrationTimeLabel(at: Long): String? = if (at <= 0L) {
    null
} else {
    SimpleDateFormat(
        stringResource(R.string.migration_history_time_format),
        Locale.getDefault(),
    ).format(Date(at))
}
