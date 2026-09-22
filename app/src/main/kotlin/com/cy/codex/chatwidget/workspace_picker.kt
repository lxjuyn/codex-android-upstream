package com.cy.codex.chatwidget

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.cy.codex.runtime.CodexApplication
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.R
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.FileMetadata
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.pressableRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Directory browser behind the workspace picker.
 *
 * Mirrors the `onboarding/directory_trust.rs` + `trust_directory.rs` folder prompt and
 * `additional_dirs.rs`'s "extra writable root" flow: the TUI asks for consent on a folder it
 * already knows, the phone lets the user walk the tree with `fs/readDirectory` before consenting.
 * The screen owns no navigation state of its own beyond [currentPath].
 */
@Composable
fun WorkspacePickerScreen(
    client: AppServerClient,
    initialPath: String,
    onPicked: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val runtime = LocalContext.current.applicationContext as CodexApplication
    var currentPath by remember(initialPath) { mutableStateOf(initialPath.ifBlank { runtime.defaultWorkspace }) }
    var entries by remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Resolved here because the effect below is not a composable; it only needs the text once, when
    // a read fails without a message of its own.
    val readError = stringResource(R.string.workspace_picker_read_error)

    LaunchedEffect(currentPath) {
        loading = true
        error = null
        client.readDirectory(currentPath)
            .onSuccess { listing -> entries = listing }
            .onFailure { failure ->
                entries = emptyList()
                error = failure.message ?: readError
            }
        loading = false
    }

    val directories = remember(entries) {
        entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
    }
    val files = remember(entries) {
        entries.filterNot { it.isDirectory }.sortedBy { it.name.lowercase() }
    }
    val crumbs = remember(currentPath) { workspaceCrumbs(currentPath) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.workspace_picker_title),
            subtitle = currentPath,
            leading = { WorkspaceBackButton(onBack) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(
                        text = "/",
                        fontSize = UiType.Value,
                        lineHeight = UiType.ValueLine,
                        color = colors.disabledOnSurface,
                    )
                }
                val last = index == crumbs.lastIndex
                Text(
                    text = crumb.first,
                    modifier = Modifier
                        .clip(WorkspaceRowShape)
                        .pressableRow(
                            shape = WorkspaceRowShape,
                            container = Color.Transparent,
                            onClick = { currentPath = crumb.second },
                        )
                        .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space4),
                    fontSize = UiType.Value,
                    lineHeight = UiType.ValueLine,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) colors.onSurface else colors.primary,
                    maxLines = 1,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> WorkspaceMessage(
                    text = stringResource(R.string.workspace_picker_reading, currentPath),
                    showProgress = true,
                )

                error != null -> WorkspaceMessage(text = error!!, isError = true)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = UiConsts.ScreenMargin,
                        vertical = UiConsts.Space6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(UiConsts.Space2),
                ) {
                    if (currentPath != "/") {
                        item(key = "..") {
                            WorkspaceEntryRow(
                                name = "..",
                                detail = stringResource(R.string.workspace_picker_parent),
                                isDirectory = true,
                                onClick = { currentPath = workspaceParent(currentPath) },
                            )
                        }
                    }
                    items(directories, key = { it.path }) { entry ->
                        WorkspaceEntryRow(
                            name = entry.name,
                            detail = entry.path,
                            isDirectory = true,
                            onClick = { currentPath = entry.path },
                        )
                    }
                    items(files, key = { it.path }) { entry ->
                        WorkspaceEntryRow(
                            name = entry.name,
                            detail = workspaceFormatSize(entry.size),
                            isDirectory = false,
                            onClick = null,
                        )
                    }
                    if (directories.isEmpty() && files.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.workspace_picker_empty),
                                modifier = Modifier.padding(vertical = UiConsts.Space10),
                                fontSize = UiType.Meta,
                                lineHeight = UiType.MetaLine,
                                color = colors.disabledOnSurface,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.ScreenMargin, vertical = UiConsts.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.workspace_picker_footer),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Text(
                    text = currentPath,
                    fontSize = UiType.Value,
                    lineHeight = UiType.ValueLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(UiConsts.Space12))
            Button(
                onClick = { onPicked(currentPath) },
                colors = ButtonDefaults.buttonColorsPrimary(),
                minWidth = UiConsts.ActionButtonMinWidth,
            ) {
                Icon(
                    imageVector = MiuixIcons.UploadCloud,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconInline),
                )
                Spacer(Modifier.width(UiConsts.Space6))
                Text(
                    text = stringResource(R.string.workspace_picker_confirm),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceEntryRow(
    name: String,
    detail: String,
    isDirectory: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressableRow(WorkspaceRowShape, Color.Transparent, onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDirectory) MiuixIcons.FolderFill else MiuixIcons.ConvertFile,
            contentDescription = null,
            modifier = Modifier.size(UiConsts.IconRow),
            tint = if (isDirectory) colors.primary else colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(UiConsts.Space9))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = if (isDirectory) FontWeight.Medium else FontWeight.Normal,
                color = if (onClick != null) colors.onSurface else colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                fontFamily = FontFamily.Monospace,
                color = colors.disabledOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconChevron),
                tint = colors.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun WorkspaceMessage(text: String, showProgress: Boolean = false, isError: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiConsts.Space16),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(UiConsts.Space10))
        }
        Text(
            text = text,
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = if (isError) colors.error else colors.onSurfaceVariantSummary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WorkspaceBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.workspace_picker_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

private val WorkspaceRowShape = RoundedCornerShape(UiConsts.RowCorner)

/** `label to path` for every ancestor of [path], root first; each crumb jumps to its own path. */
private fun workspaceCrumbs(path: String): List<Pair<String, String>> {
    val normalized = path.trimEnd('/').ifEmpty { "/" }
    if (normalized == "/") return listOf("/" to "/")
    val crumbs = mutableListOf("/" to "/")
    var accumulated = ""
    normalized.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        accumulated = "$accumulated/$segment"
        crumbs += segment to accumulated
    }
    return crumbs
}

/** Parent of [path], clamped at the filesystem root. */
private fun workspaceParent(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    return trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
}

private fun workspaceFormatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576f)
    bytes >= 1_024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_024f)
    else -> "$bytes B"
}
