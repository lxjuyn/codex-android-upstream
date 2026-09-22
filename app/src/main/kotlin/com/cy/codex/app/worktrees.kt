package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.raisedSurface
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One entry of `git worktree list --porcelain`. */
data class GitWorktree(
    val path: String,
    val branch: String?,
    val detached: Boolean,
    val bare: Boolean,
)

/**
 * Parse `git worktree list --porcelain`.
 *
 * Porcelain output is one block per worktree, blank-line separated, one `key value` per line.
 * Unknown keys are ignored on purpose so a newer git that adds one does not break the list.
 */
fun parseWorktrees(output: String): List<GitWorktree> =
    output.split(Regex("\\n\\s*\\n")).mapNotNull { block ->
        val lines = block.lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()
        val path =
            lines.firstOrNull { it.startsWith("worktree ") }?.removePrefix("worktree ")?.trim()
        if (path.isNullOrEmpty()) return@mapNotNull null
        GitWorktree(
            path = path,
            branch =
                lines
                    .firstOrNull { it.startsWith("branch ") }
                    ?.removePrefix("branch ")
                    ?.trim()
                    ?.removePrefix("refs/heads/"),
            detached = lines.any { it == "detached" },
            bare = lines.any { it == "bare" },
        )
    }

/** Sibling directory `git worktree add` should create for [branch] under [repository]. */
internal fun worktreePathFor(repository: String, branch: String): String {
    val root = repository.trimEnd('/')
    val parent = root.substringBeforeLast('/', "").ifEmpty { "/" }
    val repoName = root.substringAfterLast('/').ifEmpty { "repo" }
    val leaf = branch.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').ifEmpty { "worktree" }
    val prefix = if (parent == "/") "" else parent
    return "$prefix/$repoName-$leaf"
}

/**
 * `/worktree` as a page.
 *
 * The TUI's managed-worktree pool is a client-side concept with no protocol surface, so the GUI
 * equivalent is the VCS feature itself: `git worktree list` through `command/exec`, and a form that
 * adds one. Picking a worktree starts a new thread there, which is what the TUI does when it
 * resumes a worktree session.
 */
@Composable
fun WorktreesScreen(
    client: AppServerClient,
    cwd: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val repository = cwd.ifBlank { "/" }
    var entries by remember(repository) { mutableStateOf<List<GitWorktree>>(emptyList()) }
    var loading by remember(repository) { mutableStateOf(true) }
    var error by remember(repository) { mutableStateOf<String?>(null) }
    var reload by remember(repository) { mutableStateOf(0) }
    var branch by remember(repository) { mutableStateOf("") }
    var creating by remember(repository) { mutableStateOf(false) }
    var createError by remember(repository) { mutableStateOf<String?>(null) }
    val listError = stringResource(R.string.worktrees_error)

    LaunchedEffect(repository, reload) {
        loading = true
        error = null
        client
            .execCommand(
                listOf("git", "worktree", "list", "--porcelain"),
                repository,
                timeoutMs = 15_000,
            )
            .onSuccess { answer -> entries = parseWorktrees(answer.stdout) }
            .onFailure { failure -> error = failure.message ?: listError }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.worktrees_title),
            summary = repository,
            startAction = { WorktreesBackButton(onBack) },
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
                    title = stringResource(R.string.worktrees_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Merge,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                when {
                    loading -> WorktreesNote(stringResource(R.string.worktrees_loading))
                    error != null -> WorktreesNote(error!!, isError = true)
                    entries.isEmpty() -> WorktreesNote(stringResource(R.string.worktrees_empty))
                    else -> entries.forEach { entry -> WorktreeRow(entry, repository, onOpen) }
                }
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
                    title = stringResource(R.string.worktrees_create_group),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.FolderFill,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )
                Column(modifier = Modifier) {
                    Text(
                        text = stringResource(R.string.worktrees_branch_label),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    TextField(
                        value = branch,
                        onValueChange = { branch = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "feature/my-change",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                if (createError != null) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    WorktreesNote(createError!!, isError = true)
                }
                Spacer(Modifier.height(UiConsts.Space10))
                Button(
                    onClick = {
                        val name = branch.trim()
                        if (name.isEmpty() || creating) return@Button
                        creating = true
                        createError = null
                        val path = worktreePathFor(repository, name)
                        scope.launch {
                            // `git worktree add -b` fails clearly when the branch or path already
                            // exists; the page shows git's own message rather than guessing.
                            client
                                .execCommand(
                                    listOf("git", "worktree", "add", path, "-b", name),
                                    repository,
                                    timeoutMs = 60_000,
                                )
                                .onSuccess { answer ->
                                    creating = false
                                    branch = ""
                                    if (answer.exitCode != 0) {
                                        createError = answer.stderr.ifBlank { listError }
                                    } else {
                                        reload++
                                    }
                                }
                                .onFailure { failure ->
                                    creating = false
                                    createError = failure.message ?: listError
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = branch.isNotBlank() && !creating,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text =
                            if (creating) {
                                stringResource(R.string.worktrees_creating)
                            } else {
                                stringResource(R.string.worktrees_create)
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorktreeRow(entry: GitWorktree, current: String, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.RowCorner,
        insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        colors =
            CardDefaults.defaultColors(color = Color.Transparent, contentColor = colors.onSurface),
        showIndication = true,
        onClick = { onOpen(entry.path) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.FolderFill,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconRow),
                tint =
                    if (entry.path == current) colors.primary else colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(UiConsts.Space9))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.path,
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontFamily = FontFamily.Monospace,
                    fontWeight =
                        if (entry.path == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        when {
                            entry.bare -> stringResource(R.string.worktrees_bare)
                            entry.detached -> stringResource(R.string.worktrees_detached)
                            entry.branch != null -> entry.branch
                            else -> ""
                        },
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            if (entry.path == current) {
                Spacer(Modifier.width(UiConsts.Space6))
                Text(
                    text = stringResource(R.string.session_list_current),
                    fontSize = UiType.Chip,
                    lineHeight = UiType.ChipLine,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun WorktreesNote(text: String, isError: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color =
            if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.disabledOnSurface,
    )
}

@Composable
private fun WorktreesBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.worktrees_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}
