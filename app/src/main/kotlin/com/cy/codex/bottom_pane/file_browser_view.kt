package com.cy.codex.bottom_pane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.codeSurface
import com.cy.codex.fileName
import com.cy.codex.parentPath
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.FileMetadata
import com.cy.codex.raisedSurface
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The `fs/…` family as a page: one directory at a time, the files in it, and the writes the
 * protocol exposes on them.
 *
 * Mirrors the browsing half of `codex-rs/tui/src/bottom_pane/file_search_popup.rs` and the client
 * calls in `app_server_session/fs.rs`. The TUI walks a tree behind `@`-mention and the workspace
 * prompt and prints a hit list; a phone has the room to do what the terminal cannot — open a file,
 * see whether it is text, and make a folder without leaving the app — so this page is the walk plus
 * the preview, and the two share the navigation instead of each having their own.
 *
 * [picking] is the workspace-picker reading of the same page: the rows still navigate and the
 * preview still reads, but every write is hidden and the header offers [onPick] instead. A chooser
 * that can delete is a chooser that will eventually delete the wrong folder.
 *
 * @param path the directory the page opens on; walking below it stays inside this page.
 * @param picking true when the caller wants a directory chosen rather than browsed.
 * @param client the transport every `fs/…` call goes through.
 * @param onBack pops the page, once there is no folder left to ascend to inside it.
 * @param onPick reports the directory currently on screen; only used while [picking].
 */
@Composable
fun FileBrowserScreen(
    path: String,
    picking: Boolean,
    client: AppServerClient,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // The walk lives here rather than in the shell: descending is this page looking at another
    // path, and a pushed page per folder would make back mean two different things one tap apart.
    var current by remember(path) { mutableStateOf(path) }
    var entries by remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    var reading by remember { mutableStateOf(true) }
    var readFailure by remember { mutableStateOf<String?>(null) }
    var writeFailure by remember { mutableStateOf<String?>(null) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<FilePreview?>(null) }
    // `fs/getMetadata` is a separate call from `fs/readFile` on purpose: it answers for a path this
    // client may not be allowed to open, which is exactly the case where the size and the kind are
    // the only things worth knowing.
    var metadata by remember { mutableStateOf<FileMetadata?>(null) }
    var watched by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sheet by remember { mutableStateOf<FileSheet?>(null) }
    // Bumped after every successful write to re-run the read below without moving [current].
    var revision by remember { mutableStateOf(0) }

    // Resolved here because the effects below are not composable: they need the text only as the
    // fallback for a failure the server reported without a message of its own.
    val readFailed = stringResource(R.string.file_browser_read_failed)
    val writeFailed = stringResource(R.string.file_browser_action_failed)

    LaunchedEffect(current, revision) {
        reading = true
        readFailure = null
        // The preview belongs to the listing that produced it, and a re-read may have removed the
        // file: keeping a stale path on screen would show contents of something that is gone.
        previewPath = null
        client
            .readDirectory(current)
            .onSuccess { entries = it }
            .onFailure {
                entries = emptyList()
                readFailure = it.message ?: readFailed
            }
        reading = false
    }

    LaunchedEffect(previewPath) {
        val target = previewPath
        preview = null
        metadata = null
        if (target != null) {
            client.getMetadata(target).onSuccess { metadata = it }
        }
        if (target == null) return@LaunchedEffect
        client
            .readFile(target)
            .onSuccess { preview = fileBrowserPreview(it) }
            .onFailure {
                preview = FilePreview(bytes = 0L, text = null, failure = it.message ?: readFailed)
            }
    }

    // Folders first, then files, each by name: the order the TUI's file list uses, and the only
    // order in which a folder can be read by eye.
    val rows =
        remember(entries) {
            entries.sortedWith(
                compareBy<FileMetadata>({ !it.isDirectory }, { it.name.lowercase() })
            )
        }

    // Every write goes through here so that a refusal is reported in one place and the listing is
    // re-read in one place: a write the page does not re-read is a listing that lies.
    fun mutate(action: suspend () -> Result<Unit>, onSuccess: () -> Unit = {}) {
        scope.launch {
            action()
                .onSuccess {
                    writeFailure = null
                    onSuccess()
                    revision++
                }
                .onFailure { writeFailure = it.message ?: writeFailed }
        }
    }

    // `watchId` is the path itself. `fs/unwatch` only carries the id back, so an id the page can
    // recompute from what it is showing is the only kind that survives a recomposition or a
    // re-entry; a counter would leave watches behind that nothing can name again.
    fun toggleWatch(target: String) {
        val registered = target in watched
        scope.launch {
            val result =
                if (registered) {
                    client.unwatchPath(target)
                } else {
                    client.watchPath(target, target)
                }
            result
                .onSuccess { watched = if (registered) watched - target else watched + target }
                .onFailure { writeFailure = it.message ?: writeFailed }
        }
    }

    // Back inside the walk first, out of the page second: three folders down, a back that threw the
    // page away would lose the path the user was reading.
    val back: () -> Unit = {
        if (current == path) {
            onBack()
        } else {
            current = fileBrowserParent(current)
        }
    }
    BackHandler(enabled = current != path) { current = fileBrowserParent(current) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = fileBrowserName(current),
            summary = parentPath(current).ifEmpty { null },
            startAction = {
                IconButton(
                    onClick = back,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.file_browser_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { revision++ },
                        minWidth = UiConsts.IconButtonSize,
                        minHeight = UiConsts.IconButtonSize,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.file_browser_refresh),
                            modifier = Modifier.size(UiConsts.IconRefresh),
                            tint = colors.primary,
                        )
                    }
                    // The picker's decision sits in the header, next to the path it decides about:
                    // it is what the page was opened for, and must not scroll away with the list.
                    if (picking) {
                        Spacer(Modifier.width(UiConsts.Space6))
                        Button(
                            onClick = { onPick(current) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            cornerRadius = UiConsts.ButtonHeightCompact / 2,
                            minWidth = 0.dp,
                            minHeight = UiConsts.ButtonHeightCompact,
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                    vertical = 0.dp,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.file_browser_use_directory),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
            if (writeFailure != null) {
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
                        title = stringResource(R.string.file_browser_action_failed_title),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    FileBrowserNote(writeFailure.orEmpty(), error = true)
                }
            }
            FileListingCard(
                rows = rows,
                reading = reading,
                failure = readFailure,
                onOpen = { entry ->
                    if (entry.isDirectory) current = entry.path else previewPath = entry.path
                },
            )
            previewPath?.let { open ->
                FilePreviewCard(
                    path = open,
                    preview = preview,
                    metadata = metadata,
                    picking = picking,
                    watched = open in watched,
                    onToggleWatch = { toggleWatch(open) },
                    onRename = { sheet = FileSheet.Rename(open, isDirectory = false) },
                    onCopy = { sheet = FileSheet.Copy(open, isDirectory = false) },
                    onDelete = { sheet = FileSheet.Delete(open, isDirectory = false) },
                )
            }
            if (!picking) {
                FolderActionsCard(
                    watched = current in watched,
                    onNewFolder = { sheet = FileSheet.NewFolder(current) },
                    onNewFile = { sheet = FileSheet.NewFile(current) },
                    onToggleWatch = { toggleWatch(current) },
                    onRename = { sheet = FileSheet.Rename(current, isDirectory = true) },
                    onCopy = { sheet = FileSheet.Copy(current, isDirectory = true) },
                    onDelete = { sheet = FileSheet.Delete(current, isDirectory = true) },
                )
            }
        }
    }

    when (val open = sheet) {
        null -> Unit

        is FileSheet.NewFolder ->
            FormSheet(
                title = stringResource(R.string.file_browser_new_folder),
                subtitle = open.directory,
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            placeholder = stringResource(R.string.file_browser_name_placeholder),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_create),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    sheet = null
                    val destination = fileBrowserJoin(open.directory, name)
                    mutate({ client.createDirectory(destination, recursive = true) })
                },
            )

        is FileSheet.NewFile ->
            FormSheet(
                title = stringResource(R.string.file_browser_new_file),
                subtitle = open.directory,
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            placeholder = stringResource(R.string.file_browser_name_placeholder),
                        ),
                        FormField(
                            key = ContentField,
                            label = stringResource(R.string.file_browser_content),
                            required = false,
                            help = stringResource(R.string.file_browser_content_help),
                        ),
                    ),
                confirmLabel = stringResource(R.string.file_browser_create),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    val bytes = values[ContentField].orEmpty().toByteArray(Charsets.UTF_8)
                    sheet = null
                    mutate({ client.writeFile(fileBrowserJoin(open.directory, name), bytes) })
                },
            )

        is FileSheet.Rename ->
            FormSheet(
                title =
                    stringResource(R.string.file_browser_rename_title, fileBrowserName(open.path)),
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            initial = fileBrowserName(open.path),
                            help = stringResource(R.string.file_browser_rename_help),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_rename),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    val destination = fileBrowserJoin(fileBrowserParent(open.path), name)
                    sheet = null
                    mutate({
                        client.copyPath(open.path, destination, recursive = open.isDirectory)
                    })
                },
            )

        is FileSheet.Copy ->
            FormSheet(
                title =
                    stringResource(R.string.file_browser_copy_title, fileBrowserName(open.path)),
                fields =
                    listOf(
                        FormField(
                            key = DestinationField,
                            label = stringResource(R.string.file_browser_copy_destination),
                            initial =
                                open.path +
                                    stringResource(R.string.file_browser_copy_default_suffix),
                            help = stringResource(R.string.file_browser_copy_help),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_copy),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val destination = values[DestinationField].orEmpty().trim()
                    sheet = null
                    mutate({
                        client.copyPath(open.path, destination, recursive = open.isDirectory)
                    })
                },
            )

        is FileSheet.Delete ->
            WindowBottomSheet(
                show = true,
                onDismissRequest = { sheet = null },
                onDismissFinished = { sheet = null },
                title =
                    stringResource(R.string.file_browser_delete_title, fileBrowserName(open.path)),
                backgroundColor = sheetColor(),
                cornerRadius = UiConsts.SheetCorner,
                sheetMaxWidth = UiConsts.SheetMaxWidth,
                outsideMargin = DpSize(sheetSideMargin(), 0.dp),
                insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
                dragHandleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
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
                        text = stringResource(R.string.file_browser_delete_detail),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = UiConsts.SheetPadding),
                        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                    ) {
                        Text(
                            text = open.path,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(UiConsts.RowCorner))
                                    .background(codeSurface())
                                    .padding(
                                        horizontal = UiConsts.Space8,
                                        vertical = UiConsts.Space7,
                                    ),
                            fontSize = UiType.Code,
                            lineHeight = UiType.CodeLine,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { sheet = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(),
                                cornerRadius = UiConsts.ButtonHeight / 2,
                                minWidth = 0.dp,
                                minHeight = UiConsts.ButtonHeight,
                                insideMargin =
                                    PaddingValues(
                                        horizontal = UiConsts.ButtonPaddingHorizontal,
                                        vertical = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.file_browser_cancel),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = {
                                    // Read out now, so the write hits the path the user confirmed
                                    // even if the
                                    // page has moved on by the time the coroutine runs.
                                    val target = open.path
                                    val recursive = open.isDirectory
                                    sheet = null
                                    mutate(
                                        action = {
                                            client.removePath(target, recursive = recursive)
                                        },
                                        // Deleting the folder on screen would leave the page
                                        // reading a path
                                        // that is gone; the parent is the nearest thing still
                                        // there.
                                        onSuccess = {
                                            if (target == current)
                                                current = fileBrowserParent(target)
                                        },
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                                        .squircleBorder(
                                            UiConsts.OutlineThickness,
                                            MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                            UiConsts.ButtonHeight / 2,
                                        ),
                                colors =
                                    ButtonColors(
                                        color = Color.Transparent,
                                        disabledColor =
                                            MiuixTheme.colorScheme.disabledOnSurface.copy(
                                                alpha = 0.1f
                                            ),
                                        contentColor = MiuixTheme.colorScheme.error,
                                        disabledContentColor =
                                            MiuixTheme.colorScheme.disabledOnSurface,
                                    ),
                                cornerRadius = UiConsts.ButtonHeight / 2,
                                minWidth = 0.dp,
                                minHeight = UiConsts.ButtonHeight,
                                insideMargin =
                                    PaddingValues(
                                        horizontal = UiConsts.ButtonPaddingHorizontal,
                                        vertical = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.file_browser_delete),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
    }
}

/**
 * The directory listing: one row per child, folders before files.
 *
 * A failure is drawn in the same place as an empty folder on purpose. `fs/readDirectory` answers a
 * forbidden directory and an empty one with the same empty list, so a page that showed "nothing
 * here" for both would be telling the user their folder is empty when it is merely unreadable — and
 * only one of those two is worth a retry.
 */
@Composable
private fun FileListingCard(
    rows: List<FileMetadata>,
    reading: Boolean,
    failure: String?,
    onOpen: (FileMetadata) -> Unit,
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
            title = stringResource(R.string.file_browser_contents),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.FolderFill,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                (if (failure == null) rows.size.toString() else null)?.let {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        when {
            failure != null ->
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier.size(UiConsts.IconBoxLarge)
                                .squircleBackground(
                                    color = raisedSurface(),
                                    cornerRadius = UiConsts.CornerCard,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.file_browser_read_failed),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = failure,
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }

            rows.isEmpty() && reading ->
                FileBrowserNote(stringResource(R.string.file_browser_reading))

            rows.isEmpty() ->
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier.size(UiConsts.IconBoxLarge)
                                .squircleBackground(
                                    color = raisedSurface(),
                                    cornerRadius = UiConsts.CornerCard,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.FolderFill,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.file_browser_empty),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = stringResource(R.string.file_browser_empty_detail),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                rows.forEach { entry ->
                    ArrowPreference(
                        title = entry.name,
                        summary =
                            if (entry.isDirectory) {
                                stringResource(R.string.file_browser_folder)
                            } else {
                                fileBrowserSize(entry.size)
                            },
                        startAction = {
                            Icon(
                                imageVector =
                                    if (entry.isDirectory) MiuixIcons.FolderFill
                                    else MiuixIcons.ConvertFile,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconPreference),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { onOpen(entry) },
                    )
                }
        }
    }
}

/**
 * One open file: its text, or why there is none, plus the writes that apply to it.
 *
 * The text is shown on a code surface inside a bounded scroll box rather than in the page's own
 * scroll: a 4000-character file is taller than the phone, and letting it join the page's scroll
 * would push the listing and the actions off the top of the screen.
 */
@Composable
private fun FilePreviewCard(
    path: String,
    preview: FilePreview?,
    metadata: FileMetadata?,
    picking: Boolean,
    watched: Boolean,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val body = preview
    val text = body?.text
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
            title = fileBrowserName(path),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                (body?.let { fileBrowserSize(it.bytes) })?.let {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        // The server's answer about the path itself, which is the only description available for a
        // file this client is not allowed to read.
        if (metadata != null) {
            BasicComponent(
                title = stringResource(R.string.file_browser_meta_kind),
                endActions = {
                    Text(
                        text =
                            (stringResource(
                                    if (metadata.isDirectory) {
                                        R.string.file_browser_meta_directory
                                    } else {
                                        R.string.file_browser_meta_file
                                    }
                                ))
                                .ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.file_browser_meta_size),
                endActions = {
                    Text(
                        text = (fileBrowserSize(metadata.size)).ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            if (metadata.modifiedAt > 0L) {
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.file_browser_meta_modified),
                    endActions = {
                        Text(
                            text =
                                (android.text.format.DateUtils.getRelativeTimeSpanString(
                                            metadata.modifiedAt
                                        )
                                        .toString())
                                    .ifEmpty { "—" },
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = UiType.Detail,
                            lineHeight = UiType.DetailLine,
                            fontFamily = null,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        }
        when {
            body == null -> FileBrowserNote(stringResource(R.string.file_browser_preview_reading))

            body.failure != null -> FileBrowserNote(body.failure.orEmpty(), error = true)

            text == null ->
                FileBrowserNote(
                    stringResource(
                        R.string.file_browser_preview_binary,
                        fileBrowserSize(body.bytes),
                    )
                )

            text.isEmpty() -> FileBrowserNote(stringResource(R.string.file_browser_preview_empty))

            else -> {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = PreviewMaxHeight)
                            .clip(RoundedCornerShape(UiConsts.RowCorner))
                            .background(codeSurface())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space7)
                ) {
                    Text(
                        text = text,
                        fontSize = UiType.Code,
                        lineHeight = UiType.CodeLine,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurface,
                    )
                }
                if (body.clipped) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    FileBrowserNote(
                        stringResource(R.string.file_browser_preview_clipped, PreviewCharLimit)
                    )
                }
            }
        }
        // Reading is not writing: a picker still gets the preview, just none of the buttons.
        if (!picking && body != null && body.failure == null) {
            Spacer(Modifier.height(UiConsts.Space8))
            EntryActions(
                watched = watched,
                onToggleWatch = onToggleWatch,
                onRename = onRename,
                onCopy = onCopy,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * The writes that apply to the folder on screen.
 *
 * New folder and new file are separate rows because they take different forms — a name, and a name
 * plus contents — while rename, copy, delete and watch are [EntryActions], the same set a single
 * file gets. The folder these act on is the one being browsed, which is the only folder the page
 * can be sure the user is looking at.
 */
@Composable
private fun FolderActionsCard(
    watched: Boolean,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
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
            title = stringResource(R.string.file_browser_folder_actions),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        ArrowPreference(
            title = stringResource(R.string.file_browser_new_folder),
            summary = stringResource(R.string.file_browser_new_folder_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.AddFolder,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onNewFolder,
        )
        ArrowPreference(
            title = stringResource(R.string.file_browser_new_file),
            summary = stringResource(R.string.file_browser_new_file_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onNewFile,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        EntryActions(
            watched = watched,
            onToggleWatch = onToggleWatch,
            onRename = onRename,
            onCopy = onCopy,
            onDelete = onDelete,
        )
    }
}

/**
 * The four writes one entry has, as buttons: watch, rename, copy, delete.
 *
 * One composable for a file and for a folder because both need exactly these four, and two copies
 * would drift on which one asks first. Watch only *registers* the watch: `fs/watch` makes the
 * server push `fs/changed` on the client's event stream, and this page does not subscribe to that
 * stream, so the button reports what was asked for rather than promising what will be shown.
 */
@Composable
private fun EntryActions(
    watched: Boolean,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        Button(
            onClick = onToggleWatch,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        if (watched) R.string.file_browser_watch_stop
                        else R.string.file_browser_watch
                    ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onRename,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_rename),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_copy),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onDelete,
                modifier =
                    Modifier.weight(1f)
                        .squircleBorder(
                            UiConsts.OutlineThickness,
                            MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                            UiConsts.ButtonHeightCompact / 2,
                        ),
                colors =
                    ButtonColors(
                        color = Color.Transparent,
                        disabledColor = MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                        contentColor = MiuixTheme.colorScheme.error,
                        disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                    ),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_delete),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One line of a card's state: reading, empty, binary, or a refusal from the server. */
@Composable
private fun FileBrowserNote(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = if (error) colors.error else colors.onSurfaceVariantSummary,
    )
}

/** A byte count in the unit a person reads, one decimal above a kilobyte. */
@Composable
private fun fileBrowserSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> stringResource(R.string.file_browser_size_mb, bytes / 1_048_576f)
        bytes >= 1_024 -> stringResource(R.string.file_browser_size_kb, bytes / 1_024f)
        else -> stringResource(R.string.file_browser_size_bytes, bytes)
    }

/**
 * One file, decoded far enough to show.
 *
 * `fs/readFile` has no ranged variant, so the whole file is already in memory by the time this is
 * built; the cap is what keeps a 40 MB log from becoming 40 MB of text in one composition.
 *
 * @param bytes the file's size on disk, which is all a binary file can be shown as.
 * @param text the decoded text, or `null` when the file is binary.
 * @param failure why the read failed, when it did; [text] is then `null` as well.
 * @param clipped whether [text] is only the beginning of a longer file.
 */
private data class FilePreview(
    val bytes: Long,
    val text: String?,
    val failure: String? = null,
    val clipped: Boolean = false,
)

/**
 * The form the page has open over its listing, if any.
 *
 * One sealed type rather than six flags: exactly one can be open, and a `when` over this is what
 * makes the compiler say so when a seventh write is added to the protocol.
 */
private sealed interface FileSheet {
    /** Create a directory inside [directory]. `recursive` is always true, so one name is enough. */
    data class NewFolder(val directory: String) : FileSheet

    /** Write a new text file into [directory]. */
    data class NewFile(val directory: String) : FileSheet

    /**
     * Rename [path] to a new name in its own directory.
     *
     * The protocol has no move, so this is `fs/copy` to that new name — and the original stays
     * where it was, which the form says out loud. A rename that silently leaves a second copy
     * behind is worse than one that admits it is a copy.
     */
    data class Rename(val path: String, val isDirectory: Boolean) : FileSheet

    /** Copy [path] to an absolute destination, recursively when it is a directory. */
    data class Copy(val path: String, val isDirectory: Boolean) : FileSheet

    /** Delete [path] once the user has confirmed, because `fs/remove` cannot be undone. */
    data class Delete(val path: String, val isDirectory: Boolean) : FileSheet
}

/** Characters of a file the preview shows before it stops; a phone shows far fewer per screen. */
private const val PreviewCharLimit = 4000

/** Bytes scanned for a NUL before a file is called binary, the way the `file` tool decides. */
private const val PreviewByteScan = 512

/** Ceiling on the preview box, so one long file cannot push every other card off the page. */
private val PreviewMaxHeight = 320.dp

/** Field key of every name field; a [FormSheet] reports the typed values under it. */
private const val NameField = "name"

/** Field key of the new-file form's contents. */
private const val ContentField = "content"

/** Field key of the copy form's destination path. */
private const val DestinationField = "destination"

/**
 * Decode [bytes] for the preview card.
 *
 * Binary is decided by a NUL in the first [PreviewByteScan] bytes rather than by UTF-8 validity: a
 * source file in a legacy encoding decodes to replacement characters and is still worth reading,
 * while a PNG has NULs at the front and does not become text by being pushed through a decoder.
 */
private fun fileBrowserPreview(bytes: ByteArray): FilePreview {
    val binary = bytes.take(PreviewByteScan).any { it == 0.toByte() }
    if (binary) return FilePreview(bytes = bytes.size.toLong(), text = null)
    val decoded = bytes.toString(Charsets.UTF_8)
    return FilePreview(
        bytes = bytes.size.toLong(),
        text = decoded.take(PreviewCharLimit),
        clipped = decoded.length > PreviewCharLimit,
    )
}

/** Child of [directory] named [name], with exactly one separator between the two. */
private fun fileBrowserJoin(directory: String, name: String): String =
    directory.trimEnd('/') + "/" + name.trimStart('/')

/** Parent of [path], clamped at the filesystem root where there is nothing above to name. */
private fun fileBrowserParent(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    return trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
}

/** Last segment of [path], or the whole path when it has none — the filesystem root. */
private fun fileBrowserName(path: String): String = fileName(path).ifEmpty { path }
