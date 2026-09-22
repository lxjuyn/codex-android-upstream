package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.ActionRow
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexDivider
import com.cy.codex.CodexTextField
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.codeSurface
import com.cy.codex.label
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.EnvironmentInfoResponse
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusKind
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusResponse
import com.cy.codex.protocol.protocol.v2.ProjectEntry
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
import com.cy.codex.ThreadStatusTone
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Saved projects, and the environments a thread can be run on.
 *
 * The TUI has no page for either: it works in whatever directory it was started in and reaches a
 * remote host through `--remote`, so `project/…` and `environment/…` exist in the protocol without
 * a terminal surface. On a phone both are worth a list — a project is a checkout you come back to,
 * and an environment is a machine the thread runs on — so this page is the protocol's own surface
 * rather than a port of anything.
 *
 * One page for both because they answer the same question, and because they are the two halves of
 * the same picker: a new thread needs a directory, and a directory may live on another machine.
 */
@Composable
fun ProjectsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    onOpenEnvironment: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // One project, re-read by id. `project/list` answers with the whole list, so a row that only
    // wants its own thread count would otherwise have to reload every other row to find out.
    var rechecked by remember { mutableStateOf<ProjectEntry?>(null) }
    var editing by remember { mutableStateOf<ProjectEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var addingEnvironment by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.projects_screen_title),
            subtitle = stringResource(R.string.runtime_project_count, catalog.projects.size),
            leading = { SurfaceBackButton(stringResource(R.string.projects_screen_back), onBack) },
            trailing = {
                IconButton(
                    onClick = { creating = true },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.projects_screen_new),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = colors.primary,
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
            ProjectsCard(
                projects = catalog.projects,
                onEvent = onEvent,
                onEdit = { editing = it },
                onImport = { importing = true },
                onOpen = { onOpenProject(it.path) },
                onRecheck = { project ->
                    scope.launch {
                        client.readProject(project.id).onSuccess { rechecked = it }
                    }
                },
            )
            rechecked?.let { fresh ->
                // Only the *count* can move while the page is open — threads get filed under a
                // project from the session list — so the card reports that and nothing else.
                SectionCard(
                    title = stringResource(R.string.projects_screen_rechecked, fresh.name),
                    icon = MiuixIcons.FolderFill,
                ) {
                    ValueRow(
                        label = stringResource(R.string.projects_screen_path_label),
                        value = fresh.path,
                        monospace = true,
                    )
                }
            }
        }
    }

    if (creating) {
        ProjectFormSheet(
            title = stringResource(R.string.projects_screen_new),
            initial = null,
            onDismiss = { creating = false },
            onSubmit = { name, path ->
                onEvent(AppEvent.CreateProject(name, path))
                creating = false
            },
        )
    }
    editing?.let { project ->
        ProjectFormSheet(
            title = stringResource(R.string.projects_screen_edit),
            initial = project,
            onDismiss = { editing = null },
            onSubmit = { name, path ->
                onEvent(AppEvent.UpdateProject(project.id, name = name, path = path))
                editing = null
            },
        )
    }
    if (importing) {
        PathSheet(
            title = stringResource(R.string.projects_screen_import),
            label = stringResource(R.string.projects_screen_path_label),
            confirm = stringResource(R.string.projects_screen_import),
            onDismiss = { importing = false },
            onSubmit = { path ->
                onEvent(AppEvent.ImportProject(path))
                importing = false
            },
        )
    }
    if (addingEnvironment) {
        EnvironmentFormSheet(
            onDismiss = { addingEnvironment = false },
            onSubmit = { id, url ->
                onEvent(AppEvent.AddEnvironment(id, url))
                addingEnvironment = false
            },
        )
    }
}

/**
 * The project list, with its own inline actions.
 *
 * Tapping a row selects it and reveals the actions rather than opening a sheet: the actions are
 * five, they are all one tap deep from here, and a sheet would hide the list the user is deciding
 * between while they decide.
 */
@Composable
private fun ProjectsCard(
    projects: List<ProjectEntry>,
    onEvent: (AppEvent) -> Unit,
    onEdit: (ProjectEntry) -> Unit,
    onImport: () -> Unit,
    onRecheck: (ProjectEntry) -> Unit,
    onOpen: (ProjectEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var selected by remember { mutableStateOf<String?>(null) }

    SectionCard(
        title = stringResource(R.string.projects_screen_projects),
        icon = MiuixIcons.FolderFill,
        trailing = projects.size.toString(),
    ) {
        if (projects.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.FolderFill,
                title = stringResource(R.string.projects_screen_empty),
                detail = stringResource(R.string.projects_screen_empty_detail),
            )
        }
        projects.forEachIndexed { index, project ->
            if (index > 0) CodexDivider()
            ProjectRow(
                project = project,
                expanded = selected == project.id,
                first = index == 0,
                last = index == projects.lastIndex,
                onToggle = { selected = if (selected == project.id) null else project.id },
                onEdit = { onEdit(project) },
                onMove = { delta -> onEvent(AppEvent.MoveProject(project.id, index + delta)) },
                onDelete = { onEvent(AppEvent.DeleteProject(project.id)) },
                onRecheck = { onRecheck(project) },
                onOpen = { onOpen(project) },
            )
        }
        ActionRow(
            title = stringResource(R.string.projects_screen_import),
            subtitle = stringResource(R.string.projects_screen_import_detail),
            icon = MiuixIcons.Link,
            onClick = onImport,
        )
    }
}

@Composable
private fun ProjectRow(
    project: ProjectEntry,
    expanded: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onRecheck: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            title = project.name,
            subtitle = project.path.ifEmpty { null },
            onClick = onToggle,
        )
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = UiConsts.Space4, end = UiConsts.Space4, bottom = UiConsts.Space8),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                CodexButton(
                    text = stringResource(R.string.runtime_new_thread),
                    onClick = onOpen,
                    size = com.cy.codex.CodexButtonSize.Compact,
                )
                CodexButton(
                    text = stringResource(R.string.projects_screen_edit),
                    onClick = onEdit,
                    size = com.cy.codex.CodexButtonSize.Compact,
                    role = com.cy.codex.ButtonRole.Secondary,
                )
                CodexButton(
                    text = stringResource(R.string.projects_screen_move_up),
                    onClick = { onMove(-1) },
                    size = com.cy.codex.CodexButtonSize.Compact,
                    role = com.cy.codex.ButtonRole.Secondary,
                    enabled = !first,
                )
                CodexButton(
                    text = stringResource(R.string.projects_screen_move_down),
                    onClick = { onMove(1) },
                    size = com.cy.codex.CodexButtonSize.Compact,
                    role = com.cy.codex.ButtonRole.Secondary,
                    enabled = !last,
                )
                CodexButton(
                    text = stringResource(R.string.projects_screen_recheck),
                    onClick = onRecheck,
                    size = com.cy.codex.CodexButtonSize.Compact,
                    role = com.cy.codex.ButtonRole.Secondary,
                )
                CodexButton(
                    text = stringResource(R.string.projects_screen_delete),
                    onClick = onDelete,
                    size = com.cy.codex.CodexButtonSize.Compact,
                    role = com.cy.codex.ButtonRole.Destructive,
                )
            }
        }
    }
    if (!expanded) Spacer(Modifier.height(0.dp))
}

/**
 * The environment list.
 *
 * Only the ids this client has been told about, because `environment/info` and `environment/status`
 * are addressed by id and there is no call that enumerates them. An id arrives from
 * `thread/environment/connected` or from an `environment/add` made here, and both are folded into
 * [CatalogState.environments] by the app.
 */
@Composable
private fun EnvironmentsCard(
    environments: List<String>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.projects_screen_environments),
        icon = MiuixIcons.Link,
        trailing = environments.size.toString(),
    ) {
        if (environments.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Link,
                title = stringResource(R.string.projects_screen_no_environments),
                detail = stringResource(R.string.projects_screen_no_environments_detail),
            )
        }
        environments.forEach { id ->
            ActionRow(
                title = id,
                subtitle = stringResource(R.string.projects_screen_environment_detail),
                onClick = { onOpen(id) },
            )
        }
        ActionRow(
            title = stringResource(R.string.projects_screen_add_environment),
            subtitle = stringResource(R.string.projects_screen_add_environment_detail),
            icon = MiuixIcons.Add,
            onClick = onAdd,
        )
    }
}

/**
 * One environment, read by id.
 *
 * The two reads are separate calls with very different costs — `environment/info` starts nothing and
 * `environment/status` refuses to recover a connection — so the page runs both and reports them
 * side by side rather than presenting one merged "state".
 */
@Composable
fun EnvironmentDetailScreen(
    environmentId: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<EnvironmentInfoResponse?>(null) }
    var status by remember { mutableStateOf<EnvironmentStatusResponse?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    fun read() {
        scope.launch {
            client.readEnvironmentInfo(environmentId)
                .onSuccess { info = it }
                .onFailure { failed = it.message }
            client.readEnvironmentStatus(environmentId)
                .onSuccess { status = it; failed = null }
                .onFailure { failed = it.message }
        }
    }

    LaunchedEffect(environmentId) { read() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = environmentId,
            subtitle = status?.status?.label() ?: stringResource(R.string.environment_detail_loading),
            leading = { SurfaceBackButton(stringResource(R.string.projects_screen_back), onBack) },
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
                title = stringResource(R.string.environment_detail_state),
                icon = MiuixIcons.Link,
            ) {
                val kind = status?.status
                ValueRow(
                    label = stringResource(R.string.environment_detail_status),
                    value = kind?.label() ?: stringResource(R.string.environment_detail_unknown),
                    tint = kind?.let { statusDotColor(it.tone()) },
                )
                CodexDivider()
                ValueRow(
                    label = stringResource(R.string.environment_detail_error),
                    value = status?.error.orEmpty(),
                )
            }
            SectionCard(
                title = stringResource(R.string.environment_detail_shell),
                icon = MiuixIcons.FolderFill,
            ) {
                ValueRow(
                    label = stringResource(R.string.environment_detail_shell_name),
                    value = info?.shell?.name.orEmpty(),
                )
                CodexDivider()
                ValueRow(
                    label = stringResource(R.string.environment_detail_shell_path),
                    value = info?.shell?.path.orEmpty(),
                    monospace = true,
                )
                CodexDivider()
                ValueRow(
                    label = stringResource(R.string.environment_detail_cwd),
                    value = info?.cwd.orEmpty(),
                    monospace = true,
                )
            }
            if (failed != null) {
                SectionCard(
                    title = stringResource(R.string.environment_detail_unreachable),
                    icon = MiuixIcons.Link,
                ) {
                    Text(
                        text = failed.orEmpty(),
                        modifier = Modifier.padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                }
            }
            CodexButton(
                text = stringResource(R.string.environment_detail_recheck),
                onClick = { read() },
                modifier = Modifier.fillMaxWidth(),
                role = com.cy.codex.ButtonRole.Secondary,
            )
        }
    }
}

/** Colour the status dot the way every other status in the app is coloured. */
private fun EnvironmentStatusKind.tone(): ThreadStatusTone = when (this) {
    EnvironmentStatusKind.Ready -> ThreadStatusTone.Done
    EnvironmentStatusKind.Pending -> ThreadStatusTone.Running
    EnvironmentStatusKind.Disconnected -> ThreadStatusTone.Waiting
    EnvironmentStatusKind.Unknown -> ThreadStatusTone.Failed
}
