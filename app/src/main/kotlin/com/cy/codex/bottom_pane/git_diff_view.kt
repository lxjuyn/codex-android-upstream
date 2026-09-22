package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.FileDiffRow
import com.cy.codex.GitDiff
import com.cy.codex.GitDiffResult
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.parseTurnDiff
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/diff`: the working tree, tracked and untracked.
 *
 * Mirrors what the TUI's `/diff` prints into the transcript (`codex-rs/tui/src/get_git_diff.rs`
 * plus `slash_dispatch.rs`): the diff is computed on entry through [GitDiff], because the working
 * tree changes without the app hearing about it — an edit in another app, a file the user just
 * saved — and a cached copy would report a state that never was.
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

    val files =
        remember(result) {
            (result as? GitDiffResult.Changes)?.let { parseTurnDiff(it.diff) }.orEmpty()
        }
    // Every file starts collapsed: this page can hold hundreds of files, and a page that opens with
    // each one's first screenful laid out is a page that takes a second to appear.
    val expanded = remember(result) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.git_diff_screen_title),
            summary = cwd,
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.git_diff_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
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
            when (val current = result) {
                null ->
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    vertical = UiConsts.Space24,
                                    horizontal = UiConsts.Space16,
                                ),
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
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconHeader),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(UiConsts.Space12))
                        Text(
                            text = stringResource(R.string.git_diff_screen_loading),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                is GitDiffResult.Clean ->
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    vertical = UiConsts.Space24,
                                    horizontal = UiConsts.Space16,
                                ),
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
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconHeader),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(UiConsts.Space12))
                        Text(
                            text = stringResource(R.string.git_diff_screen_empty),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                is GitDiffResult.NotARepository ->
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    vertical = UiConsts.Space24,
                                    horizontal = UiConsts.Space16,
                                ),
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
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconHeader),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(UiConsts.Space12))
                        Text(
                            text = stringResource(R.string.git_diff_screen_not_repository),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                is GitDiffResult.Failed ->
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    vertical = UiConsts.Space24,
                                    horizontal = UiConsts.Space16,
                                ),
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
                                imageVector = MiuixIcons.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconHeader),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(UiConsts.Space12))
                        Text(
                            text = stringResource(R.string.git_diff_screen_failed, current.message),
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                is GitDiffResult.Changes -> {
                    Text(
                        text =
                            stringResource(
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
