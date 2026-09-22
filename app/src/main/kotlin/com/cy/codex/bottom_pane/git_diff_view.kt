package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.EmptyState
import com.cy.codex.FileDiffRow
import com.cy.codex.GitDiff
import com.cy.codex.GitDiffResult
import com.cy.codex.R
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.parseTurnDiff
import com.cy.codex.protocol.AppServerClient
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/diff`: the working tree, tracked and untracked.
 *
 * Mirrors what the TUI's `/diff` prints into the transcript
 * (`codex-rs/tui/src/get_git_diff.rs` plus `slash_dispatch.rs`): the diff is computed on entry
 * through [GitDiff], because the working tree changes without the app hearing about it — an edit in
 * another app, a file the user just saved — and a cached copy would report a state that never was.
 *
 * The transcript's patch cells show one turn's changes. This page is deliberately a second view
 * rather than another cell: `turn/diff/updated` never mentions untracked files, and a page is the
 * only shape here that can carry a reload.
 */
@Composable
fun GitDiffScreen(
    cwd: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var result by remember(cwd) { mutableStateOf<GitDiffResult?>(null) }
    var reloadToken by remember(cwd) { mutableIntStateOf(0) }
    // Keyed on the token as well as the directory so the refresh button re-runs the read. Leaving
    // it set keeps the previous answer on screen while the new one is computed, which is what makes
    // the reload feel like a refresh rather than a blank page.
    LaunchedEffect(cwd, reloadToken) { result = GitDiff.load(client, cwd) }

    val files = remember(result) {
        (result as? GitDiffResult.Changes)?.let { parseTurnDiff(it.diff) }.orEmpty()
    }
    // Every file starts collapsed: this page can hold hundreds of files, and a page that opens with
    // each one's first screenful laid out is a page that takes a second to appear.
    val expanded = remember(result) { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.git_diff_screen_title),
            subtitle = cwd,
            leading = {
                SurfaceBackButton(stringResource(R.string.git_diff_screen_back), onBack)
            },
            trailing = {
                IconButton(
                    onClick = { reloadToken++ },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.git_diff_screen_reload),
                        modifier = Modifier.size(UiConsts.IconRefresh),
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
            when (val current = result) {
                null -> EmptyState(
                    icon = MiuixIcons.Notes,
                    title = stringResource(R.string.git_diff_screen_loading),
                )

                is GitDiffResult.Clean -> EmptyState(
                    icon = MiuixIcons.Notes,
                    title = stringResource(R.string.git_diff_screen_empty),
                )

                is GitDiffResult.NotARepository -> EmptyState(
                    icon = MiuixIcons.Notes,
                    title = stringResource(R.string.git_diff_screen_not_repository),
                )

                is GitDiffResult.Failed -> EmptyState(
                    icon = MiuixIcons.Notes,
                    title = stringResource(R.string.git_diff_screen_failed, current.message),
                )

                is GitDiffResult.Changes -> {
                    Text(
                        text = stringResource(
                            R.string.git_diff_screen_summary,
                            files.size,
                            files.sumOf { it.additions },
                            files.sumOf { it.removals },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                    files.forEach { file ->
                        FileDiffRow(
                            file = file,
                            expanded = expanded[file.path] == true,
                            onToggle = { expanded[file.path] = expanded[file.path] != true },
                            cwd = cwd,
                            corner = UiConsts.CornerControl,
                        )
                    }
                }
            }
        }
    }
}
